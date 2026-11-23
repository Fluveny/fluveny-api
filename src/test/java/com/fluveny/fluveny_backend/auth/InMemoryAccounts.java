package com.fluveny.fluveny_backend.auth;

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

/**
 * {@code users}, {@code sessions}, {@code events}, {@code one_time_tokens} e {@code mail_outbox} em
 * memória (e {@code login_throttle}, história 2.3), com as mesmas regras de unicidade e atomicidade
 * do Mongo (e-mail único, inclusive em cadastros simultâneos; consumo do token de uso único), para
 * os testes de rota sem banco (histórias 2.1, 2.2 e 2.3, esta com {@code login_throttle}). Os
 * testes contra o Mongo ficam nos {@code *IT}.
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

    /** {@code mail_outbox}. */
    public final InMemoryOutbox outbox = new InMemoryOutbox();

    /** Quando ligado, o {@code EventStore} lança, como um Mongo fora do ar. */
    public volatile boolean failEvents;

    public void clear() {
        this.users.clear();
        this.idsByEmail.clear();
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
            public boolean updatePronounSet(String id, PronounSet pronounSet) {
                User user = InMemoryAccounts.this.users.get(id);
                if (user == null) {
                    return false;
                }
                InMemoryAccounts.this.users.put(id, withPronouns(user, pronounSet));
                return true;
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
            public void recordFailure(String keyHash, String kind, Instant now, Instant expiresAt) {
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
                user.deletionRequestedAt());
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
                user.deletionRequestedAt());
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
                user.deletionRequestedAt());
    }
}
