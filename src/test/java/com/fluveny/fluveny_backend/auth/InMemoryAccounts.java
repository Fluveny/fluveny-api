package com.fluveny.fluveny_backend.auth;

import com.fluveny.fluveny_backend.account.AccountStatus;
import com.fluveny.fluveny_backend.account.ProfileChange;
import com.fluveny.fluveny_backend.account.PronounSet;
import com.fluveny.fluveny_backend.account.infrastructure.OneTimeToken;
import com.fluveny.fluveny_backend.account.infrastructure.OneTimeTokenStore;
import com.fluveny.fluveny_backend.account.infrastructure.TokenPurpose;
import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.auth.infrastructure.LoginThrottleStore;
import com.fluveny.fluveny_backend.auth.infrastructure.Session;
import com.fluveny.fluveny_backend.auth.infrastructure.SessionStore;
import com.fluveny.fluveny_backend.auth.infrastructure.ThrottleEntry;
import com.fluveny.fluveny_backend.mail.InMemoryOutbox;
import com.fluveny.fluveny_backend.mail.infrastructure.OutboxStore;
import com.fluveny.fluveny_backend.metrics.infrastructure.Event;
import com.fluveny.fluveny_backend.metrics.infrastructure.EventStore;
import com.fluveny.fluveny_backend.platform.security.Role;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.bson.types.ObjectId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@code users}, {@code sessions}, {@code events}, {@code one_time_tokens} e {@code mail_outbox} em
 * memória (e {@code login_throttle}, história 2.3), com as mesmas regras de unicidade e atomicidade
 * do Mongo (e-mail único, inclusive em cadastros simultâneos; consumo do token de uso único), para
 * os testes de rota sem banco (histórias 2.1, 2.2, 2.3, esta com {@code login_throttle}, e 2.8, com
 * a gravação condicional da senha e as sessões apagadas por conta). Os testes contra o Mongo ficam
 * nos {@code *IT}.
 *
 * <p>Sem Mongo não há transação (história 2.6): um gerenciador sem efeito no lugar do {@code
 * MongoTransactionManager} abre, junta e fecha a transação e roda os {@code afterCommit}, mas não
 * desfaz nada no rollback. A atomicidade fica nos {@code *IT}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class InMemoryAccounts {

    /** {@code users} por id. */
    public final Map<String, User> users = new ConcurrentHashMap<>();

    private final Map<String, String> idsByEmail = new ConcurrentHashMap<>();

    /** {@code sessions} por {@code tokenHash}. */
    public final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /** {@code events}, na ordem de gravação. */
    public final List<Event> events = Collections.synchronizedList(new ArrayList<>());

    /** {@code one_time_tokens} por {@code tokenHash}. */
    public final Map<String, OneTimeToken> tokens = new ConcurrentHashMap<>();

    /** {@code login_throttle} por {@code keyHash} (história 2.3). */
    public final Map<String, ThrottleEntry> throttle = new ConcurrentHashMap<>();

    /** O {@code accountKeyHash} de cada chave do throttle, como no Mongo (história 2.10). */
    public final Map<String, String> throttleAccounts = new ConcurrentHashMap<>();

    /** {@code mail_outbox}. */
    public final InMemoryOutbox outbox = new InMemoryOutbox();

    /** Quando ligado, o {@code EventStore} lança, como um Mongo fora do ar. */
    public volatile boolean failEvents;

    public void clear() {
        this.users.clear();
        this.idsByEmail.clear();
        this.throttleAccounts.clear();
        this.sessions.clear();
        this.throttle.clear();
        this.events.clear();
        this.tokens.clear();
        this.outbox.clear();
        this.failEvents = false;
    }

    /** Troca a conta gravada (por exemplo, o {@code status}). */
    public void replace(User user) {
        this.users.put(user.id(), user);
    }

    @Bean
    @Primary
    UserStore inMemoryUsers() {
        return new UserStore() {
            @Override
            public Optional<String> insert(User user) {
                String id = new ObjectId().toHexString();
                if (InMemoryAccounts.this.idsByEmail.putIfAbsent(user.email(), id) != null) {
                    return Optional.empty();
                }
                InMemoryAccounts.this.users.put(id, withId(user, id));
                return Optional.of(id);
            }

            @Override
            public Optional<User> findById(String id) {
                return (id == null)
                        ? Optional.empty()
                        : Optional.ofNullable(InMemoryAccounts.this.users.get(id));
            }

            @Override
            public Optional<User> findByEmail(String email) {
                String id = (email == null) ? null : InMemoryAccounts.this.idsByEmail.get(email);
                return (id == null)
                        ? Optional.empty()
                        : Optional.ofNullable(InMemoryAccounts.this.users.get(id));
            }

            @Override
            public boolean markEmailVerified(String id, String email, Instant verifiedAt) {
                User user =
                        InMemoryAccounts.this.users.computeIfPresent(
                                id,
                                (key, current) ->
                                        (current.email().equals(email)
                                                        && current.emailVerifiedAt() == null)
                                                ? withEmailVerifiedAt(current, verifiedAt)
                                                : current);
                return user != null && user.email().equals(email) && user.emailVerifiedAt() != null;
            }

            @Override
            public boolean updatePasswordHash(String id, String email, String passwordHash) {
                boolean[] updated = new boolean[1];
                if (id == null) {
                    return false;
                }
                InMemoryAccounts.this.users.computeIfPresent(
                        id,
                        (key, current) -> {
                            if (current.email().equals(email)
                                    && current.status() == AccountStatus.ACTIVE
                                    && current.emailVerifiedAt() != null) {
                                updated[0] = true;
                                return withPasswordHash(current, passwordHash);
                            }
                            return current;
                        });
                return updated[0];
            }

            @Override
            public boolean updateProfile(String id, ProfileChange change) {
                if (id == null) {
                    return false;
                }
                User found = InMemoryAccounts.this.users.get(id);
                if (found == null
                        || found.status() != AccountStatus.ACTIVE
                        || found.role() != Role.STUDENT) {
                    return false;
                }
                return InMemoryAccounts.this.users.computeIfPresent(
                                id,
                                (key, u) ->
                                        with(
                                                u,
                                                u.email(),
                                                u.passwordHash(),
                                                (change.name() == null)
                                                        ? u.name()
                                                        : change.name().value(),
                                                (change.pronounSet() == null)
                                                        ? u.pronounSet()
                                                        : change.pronounSet().value(),
                                                u.emailVerifiedAt(),
                                                u.pendingEmail(),
                                                (change.country() == null)
                                                        ? u.country()
                                                        : change.country().value()))
                        != null;
            }

            @Override
            public boolean replacePasswordHash(String id, String currentHash, String newHash) {
                boolean[] updated = new boolean[1];
                if (id == null) {
                    return false;
                }
                InMemoryAccounts.this.users.computeIfPresent(
                        id,
                        (key, current) -> {
                            if (current.status() == AccountStatus.ACTIVE
                                    && current.passwordHash().equals(currentHash)) {
                                updated[0] = true;
                                return withPasswordHash(current, newHash);
                            }
                            return current;
                        });
                return updated[0];
            }

            @Override
            public boolean setPendingEmail(String id, String pendingEmail) {
                boolean[] updated = new boolean[1];
                if (id == null) {
                    return false;
                }
                InMemoryAccounts.this.users.computeIfPresent(
                        id,
                        (key, u) -> {
                            if (u.status() != AccountStatus.ACTIVE) {
                                return u;
                            }
                            updated[0] = true;
                            return with(
                                    u,
                                    u.email(),
                                    u.passwordHash(),
                                    u.name(),
                                    u.pronounSet(),
                                    u.emailVerifiedAt(),
                                    pendingEmail,
                                    u.country());
                        });
                return updated[0];
            }

            @Override
            public synchronized PromotionResult promotePendingEmail(
                    String id, String pendingEmail, Instant verifiedAt) {
                User u = (id == null) ? null : InMemoryAccounts.this.users.get(id);
                if (u == null
                        || u.status() != AccountStatus.ACTIVE
                        || !pendingEmail.equals(u.pendingEmail())) {
                    return PromotionResult.NOT_PENDING;
                }
                if (InMemoryAccounts.this.idsByEmail.putIfAbsent(pendingEmail, id) != null) {
                    return PromotionResult.TAKEN;
                }
                InMemoryAccounts.this.idsByEmail.remove(u.email());
                InMemoryAccounts.this.users.put(
                        id,
                        with(
                                u,
                                pendingEmail,
                                u.passwordHash(),
                                u.name(),
                                u.pronounSet(),
                                verifiedAt,
                                null,
                                u.country()));
                return PromotionResult.PROMOTED;
            }

            @Override
            public boolean markDeletionRequested(String id, Instant requestedAt) {
                if (id == null) {
                    return false;
                }
                boolean[] marked = {false};
                InMemoryAccounts.this.users.computeIfPresent(
                        id,
                        (key, u) -> {
                            if (u.deletionRequestedAt() != null
                                    || u.status() != AccountStatus.ACTIVE
                                    || u.role() != Role.STUDENT) {
                                return u;
                            }
                            marked[0] = true;
                            return new User(
                                    u.id(),
                                    u.email(),
                                    u.passwordHash(),
                                    u.birthDate(),
                                    u.role(),
                                    u.status(),
                                    u.metricsId(),
                                    u.name(),
                                    u.pronounSet(),
                                    u.termsVersion(),
                                    u.privacyVersion(),
                                    u.termsAcceptedAt(),
                                    u.createdAt(),
                                    u.emailVerifiedAt(),
                                    u.pendingEmail(),
                                    requestedAt,
                                    u.country());
                        });
                return marked[0];
            }

            @Override
            public void delete(String id) {
                User removed = InMemoryAccounts.this.users.remove(id);
                if (removed != null) {
                    InMemoryAccounts.this.idsByEmail.remove(removed.email());
                }
            }

            @Override
            public void clearPendingEmail(String id, String pendingEmail) {
                if (id == null) {
                    return;
                }
                InMemoryAccounts.this.users.computeIfPresent(
                        id,
                        (key, u) ->
                                pendingEmail.equals(u.pendingEmail())
                                        ? with(
                                                u,
                                                u.email(),
                                                u.passwordHash(),
                                                u.name(),
                                                u.pronounSet(),
                                                u.emailVerifiedAt(),
                                                null,
                                                u.country())
                                        : u);
            }

            @Override
            public void upsertByEmail(User user) {
                String id =
                        InMemoryAccounts.this.idsByEmail.computeIfAbsent(
                                user.email(), email -> new ObjectId().toHexString());
                InMemoryAccounts.this.users.put(id, withId(user, id));
            }
        };
    }

    @Bean
    @Primary
    SessionStore inMemorySessions() {
        return new SessionStore() {
            @Override
            public void insert(Session session) {
                InMemoryAccounts.this.sessions.put(session.tokenHash(), session);
            }

            @Override
            public Optional<Session> findByTokenHash(String tokenHash) {
                return Optional.ofNullable(InMemoryAccounts.this.sessions.get(tokenHash));
            }

            @Override
            public void renew(String tokenHash, Instant expiresAt) {
                InMemoryAccounts.this.sessions.computeIfPresent(
                        tokenHash,
                        (hash, session) ->
                                new Session(
                                        hash,
                                        session.userId(),
                                        session.role(),
                                        session.createdAt(),
                                        expiresAt));
            }

            @Override
            public void deleteByTokenHash(String tokenHash) {
                InMemoryAccounts.this.sessions.remove(tokenHash);
            }

            @Override
            public void deleteByUser(String userId) {
                InMemoryAccounts.this
                        .sessions
                        .values()
                        .removeIf(session -> session.userId().equals(userId));
            }

            @Override
            public void deleteByUserExcept(String userId, String keptTokenHash) {
                InMemoryAccounts.this
                        .sessions
                        .values()
                        .removeIf(session ->
                                session.userId().equals(userId)
                                        && !session.tokenHash().equals(keptTokenHash));
            }
        };
    }

    @Bean
    @Primary
    LoginThrottleStore inMemoryThrottle() {
        return new LoginThrottleStore() {
            @Override
            public List<ThrottleEntry> find(Collection<String> keyHashes) {
                return keyHashes.stream()
                        .map(InMemoryAccounts.this.throttle::get)
                        .filter(java.util.Objects::nonNull)
                        .toList();
            }

            @Override
            public void recordFailure(
                    String keyHash,
                    String kind,
                    String accountKeyHash,
                    Instant now,
                    Instant expiresAt) {
                if (accountKeyHash != null) {
                    InMemoryAccounts.this.throttleAccounts.put(keyHash, accountKeyHash);
                }
                InMemoryAccounts.this.throttle.compute(
                        keyHash,
                        (hash, current) ->
                                new ThrottleEntry(
                                        hash,
                                        (current == null || !current.expiresAt().isAfter(now))
                                                ? 1
                                                : current.failures() + 1,
                                        now,
                                        expiresAt));
            }

            @Override
            public void delete(Collection<String> keyHashes) {
                keyHashes.forEach(InMemoryAccounts.this.throttle::remove);
                keyHashes.forEach(InMemoryAccounts.this.throttleAccounts::remove);
            }

            @Override
            public void deleteByAccount(Collection<String> accountKeyHashes) {
                InMemoryAccounts.this
                        .throttle
                        .keySet()
                        .removeIf(key ->
                                accountKeyHashes.contains(key)
                                        || accountKeyHashes.contains(
                                                InMemoryAccounts.this.throttleAccounts.get(key)));
                InMemoryAccounts.this
                        .throttleAccounts
                        .keySet()
                        .removeIf(key -> !InMemoryAccounts.this.throttle.containsKey(key));
            }
        };
    }

    @Bean
    @Primary
    OneTimeTokenStore inMemoryTokens() {
        return new OneTimeTokenStore() {
            @Override
            public void insert(OneTimeToken token) {
                if (InMemoryAccounts.this.tokens.putIfAbsent(token.tokenHash(), token) != null) {
                    throw new IllegalStateException("tokenHash repetido");
                }
            }

            @Override
            public Optional<OneTimeToken> consume(
                    String tokenHash, TokenPurpose purpose, Instant now) {
                OneTimeToken[] consumed = new OneTimeToken[1];
                InMemoryAccounts.this.tokens.computeIfPresent(
                        tokenHash,
                        (hash, token) -> {
                            if (token.purpose() == purpose && token.expiresAt().isAfter(now)) {
                                consumed[0] = token;
                                return null;
                            }
                            return token;
                        });
                return Optional.ofNullable(consumed[0]);
            }

            @Override
            public void deleteAllFor(String userId) {
                InMemoryAccounts.this
                        .tokens
                        .values()
                        .removeIf(token -> token.userId().equals(userId));
            }

            @Override
            public void deleteByUser(String userId, TokenPurpose purpose) {
                InMemoryAccounts.this
                        .tokens
                        .values()
                        .removeIf(token ->
                                token.userId().equals(userId) && token.purpose() == purpose);
            }
        };
    }

    @Bean
    @Primary
    PlatformTransactionManager inMemoryTransactions() {
        return new NoOpTransactionManager();
    }

    @Bean
    @Primary
    OutboxStore inMemoryOutbox() {
        return this.outbox;
    }

    @Bean
    @Primary
    EventStore inMemoryAccountEvents() {
        return event -> {
            if (InMemoryAccounts.this.failEvents) {
                throw new IllegalStateException("events fora do ar");
            }
            InMemoryAccounts.this.events.add(event);
            return true;
        };
    }

    /**
     * Abre, junta ({@code REQUIRED}, {@code MANDATORY}) e fecha a transação, com as sincronizações
     * ({@code afterCommit}), sem tocar em banco nenhum.
     */
    static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}

        @Override
        protected void doSetRollbackOnly(DefaultTransactionStatus status) {}
    }

    static User withId(User user, String id) {
        return new User(
                id,
                user.email(),
                user.passwordHash(),
                user.birthDate(),
                user.role(),
                user.status(),
                user.metricsId(),
                user.name(),
                user.pronounSet(),
                user.termsVersion(),
                user.privacyVersion(),
                user.termsAcceptedAt(),
                user.createdAt(),
                user.emailVerifiedAt(),
                user.pendingEmail(),
                user.deletionRequestedAt(),
                user.country());
    }

    static User withPronouns(User user, PronounSet pronounSet) {
        return new User(
                user.id(),
                user.email(),
                user.passwordHash(),
                user.birthDate(),
                user.role(),
                user.status(),
                user.metricsId(),
                user.name(),
                pronounSet,
                user.termsVersion(),
                user.privacyVersion(),
                user.termsAcceptedAt(),
                user.createdAt(),
                user.emailVerifiedAt(),
                user.pendingEmail(),
                user.deletionRequestedAt(),
                user.country());
    }

    static User withPasswordHash(User user, String passwordHash) {
        return new User(
                user.id(),
                user.email(),
                passwordHash,
                user.birthDate(),
                user.role(),
                user.status(),
                user.metricsId(),
                user.name(),
                user.pronounSet(),
                user.termsVersion(),
                user.privacyVersion(),
                user.termsAcceptedAt(),
                user.createdAt(),
                user.emailVerifiedAt(),
                user.pendingEmail(),
                user.deletionRequestedAt(),
                user.country());
    }

    static User withEmailVerifiedAt(User user, Instant verifiedAt) {
        return new User(
                user.id(),
                user.email(),
                user.passwordHash(),
                user.birthDate(),
                user.role(),
                user.status(),
                user.metricsId(),
                user.name(),
                user.pronounSet(),
                user.termsVersion(),
                user.privacyVersion(),
                user.termsAcceptedAt(),
                user.createdAt(),
                verifiedAt,
                user.pendingEmail(),
                user.deletionRequestedAt(),
                user.country());
    }

    static User with(
            User user,
            String email,
            String passwordHash,
            String name,
            PronounSet pronounSet,
            Instant emailVerifiedAt,
            String pendingEmail,
            String country) {
        return new User(
                user.id(),
                email,
                passwordHash,
                user.birthDate(),
                user.role(),
                user.status(),
                user.metricsId(),
                name,
                pronounSet,
                user.termsVersion(),
                user.privacyVersion(),
                user.termsAcceptedAt(),
                user.createdAt(),
                emailVerifiedAt,
                pendingEmail,
                user.deletionRequestedAt(),
                country);
    }
}
