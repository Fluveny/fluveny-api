package com.fluveny.fluveny_backend.auth;

import com.fluveny.fluveny_backend.account.PronounSet;
import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.auth.infrastructure.Session;
import com.fluveny.fluveny_backend.auth.infrastructure.SessionStore;
import com.fluveny.fluveny_backend.metrics.infrastructure.Event;
import com.fluveny.fluveny_backend.metrics.infrastructure.EventStore;
import java.time.Instant;
import java.util.ArrayList;
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
 * {@code users}, {@code sessions} e {@code events} em memória, com as mesmas regras de unicidade do
 * Mongo (e-mail único, inclusive em cadastros simultâneos), para os testes de rota sem banco
 * (história 2.1). Os testes contra o Mongo ficam nos {@code *IT}.
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

    /** Quando ligado, o {@code EventStore} lança, como um Mongo fora do ar. */
    public volatile boolean failEvents;

    public void clear() {
        this.users.clear();
        this.idsByEmail.clear();
        this.sessions.clear();
        this.events.clear();
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
            public boolean updatePronounSet(String id, PronounSet pronounSet) {
                User user = InMemoryAccounts.this.users.get(id);
                if (user == null) {
                    return false;
                }
                InMemoryAccounts.this.users.put(id, withPronouns(user, pronounSet));
                return true;
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
        };
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
}
