package com.fluveny.fluveny_backend.account.infrastructure;

import com.fluveny.fluveny_backend.account.AccountStatus;
import com.fluveny.fluveny_backend.account.ProfileChange;
import com.fluveny.fluveny_backend.account.PronounSet;
import com.fluveny.fluveny_backend.platform.security.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * {@code users} no Mongo. O {@code email} e o {@code metricsId} têm índice único ({@link
 * AccountIndexes}). A {@link DuplicateKeyException} do e-mail repetido vira um {@code Optional}
 * vazio aqui mesmo: a mensagem do driver traz o e-mail e nunca vai para o log, o Sentry ou a
 * resposta. A data de nascimento fica como texto ISO ({@code yyyy-MM-dd}), sem fuso; o {@code
 * metricsId}, como texto. O {@code country} (história 2.6) só existe quando o Aluno informou.
 */
@Repository
class MongoUserStore implements UserStore {

    static final String COLLECTION = "users";

    private final MongoTemplate mongo;

    MongoUserStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Optional<String> insert(User user) {
        ObjectId id = new ObjectId();
        Document document = toDocument(user).append("_id", id);
        try {
            this.mongo.insert(document, COLLECTION);
            return Optional.of(id.toHexString());
        } catch (DuplicateKeyException ex) {
            // Sem a exceção: a mensagem do driver traz o e-mail (dado pessoal).
            return Optional.empty();
        }
    }

    @Override
    public Optional<User> findById(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                        this.mongo.findById(new ObjectId(id), Document.class, COLLECTION))
                .map(MongoUserStore::toUser);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                        this.mongo.findOne(
                                Query.query(Criteria.where("email").is(email)),
                                Document.class,
                                COLLECTION))
                .map(MongoUserStore::toUser);
    }

    @Override
    public boolean markEmailVerified(String id, String email, Instant verifiedAt) {
        if (id == null || !ObjectId.isValid(id)) {
            return false;
        }
        Criteria sameEmail = Criteria.where("_id").is(new ObjectId(id)).and("email").is(email);
        this.mongo.updateFirst(
                Query.query(
                        Criteria.where("_id")
                                .is(new ObjectId(id))
                                .and("email")
                                .is(email)
                                .and("emailVerifiedAt")
                                .is(null)),
                Update.update("emailVerifiedAt", Date.from(verifiedAt)),
                COLLECTION);
        return this.mongo.exists(
                Query.query(sameEmail.and("emailVerifiedAt").ne(null)), COLLECTION);
    }

    @Override
    public boolean updatePasswordHash(String id, String email, String passwordHash) {
        if (id == null || !ObjectId.isValid(id) || email == null) {
            return false;
        }
        return this.mongo
                        .updateFirst(
                                Query.query(
                                        Criteria.where("_id")
                                                .is(new ObjectId(id))
                                                .and("email")
                                                .is(email)
                                                .and("status")
                                                .is(AccountStatus.ACTIVE.name())
                                                .and("emailVerifiedAt")
                                                .ne(null)),
                                Update.update("passwordHash", passwordHash),
                                COLLECTION)
                        .getMatchedCount()
                > 0;
    }

    @Override
    public boolean updateProfile(String id, ProfileChange change) {
        if (id == null || !ObjectId.isValid(id) || change == null || change.isEmpty()) {
            return false;
        }
        Update update = new Update();
        if (change.name() != null) {
            if (change.name().value() == null) {
                update.unset("name");
            } else {
                update.set("name", change.name().value());
            }
        }
        if (change.country() != null) {
            if (change.country().value() == null) {
                update.unset("country");
            } else {
                update.set("country", change.country().value());
            }
        }
        if (change.pronounSet() != null) {
            update.set("pronounSet", name(change.pronounSet().value()));
        }
        Query activeStudent =
                Query.query(
                        Criteria.where("_id")
                                .is(new ObjectId(id))
                                .and("status")
                                .is(AccountStatus.ACTIVE.name())
                                .and("role")
                                .is(Role.STUDENT.name()));
        return this.mongo.updateFirst(activeStudent, update, COLLECTION).getMatchedCount() > 0;
    }

    @Override
    public boolean replacePasswordHash(String id, String currentHash, String newHash) {
        if (id == null || !ObjectId.isValid(id) || currentHash == null) {
            return false;
        }
        return this.mongo
                        .updateFirst(
                                Query.query(
                                        Criteria.where("_id")
                                                .is(new ObjectId(id))
                                                .and("status")
                                                .is(AccountStatus.ACTIVE.name())
                                                .and("passwordHash")
                                                .is(currentHash)),
                                Update.update("passwordHash", newHash),
                                COLLECTION)
                        .getMatchedCount()
                > 0;
    }

    @Override
    public boolean setPendingEmail(String id, String pendingEmail) {
        if (id == null || !ObjectId.isValid(id)) {
            return false;
        }
        return this.mongo
                        .updateFirst(
                                Query.query(
                                        Criteria.where("_id")
                                                .is(new ObjectId(id))
                                                .and("status")
                                                .is(AccountStatus.ACTIVE.name())),
                                Update.update("pendingEmail", pendingEmail),
                                COLLECTION)
                        .getMatchedCount()
                > 0;
    }

    @Override
    public PromotionResult promotePendingEmail(String id, String pendingEmail, Instant verifiedAt) {
        if (id == null || !ObjectId.isValid(id) || pendingEmail == null) {
            return PromotionResult.NOT_PENDING;
        }
        try {
            long matched =
                    this.mongo
                            .updateFirst(
                                    Query.query(
                                            Criteria.where("_id")
                                                    .is(new ObjectId(id))
                                                    .and("pendingEmail")
                                                    .is(pendingEmail)
                                                    .and("status")
                                                    .is(AccountStatus.ACTIVE.name())),
                                    new Update()
                                            .set("email", pendingEmail)
                                            .set("pendingEmail", null)
                                            .set("emailVerifiedAt", Date.from(verifiedAt)),
                                    COLLECTION)
                            .getMatchedCount();
            return (matched > 0) ? PromotionResult.PROMOTED : PromotionResult.NOT_PENDING;
        } catch (DuplicateKeyException ex) {
            // Sem a exceção: a mensagem do driver traz o e-mail (dado pessoal).
            return PromotionResult.TAKEN;
        }
    }

    @Override
    public void clearPendingEmail(String id, String pendingEmail) {
        if (id == null || !ObjectId.isValid(id) || pendingEmail == null) {
            return;
        }
        this.mongo.updateFirst(
                Query.query(
                        Criteria.where("_id")
                                .is(new ObjectId(id))
                                .and("pendingEmail")
                                .is(pendingEmail)),
                Update.update("pendingEmail", null),
                COLLECTION);
    }

    @Override
    public boolean markDeletionRequested(String id, Instant requestedAt) {
        if (id == null || !ObjectId.isValid(id)) {
            return false;
        }
        return this.mongo
                        .updateFirst(
                                Query.query(
                                        Criteria.where("_id")
                                                .is(new ObjectId(id))
                                                .and("deletionRequestedAt")
                                                .is(null)
                                                .and("status")
                                                .is(AccountStatus.ACTIVE.name())
                                                .and("role")
                                                .is(Role.STUDENT.name())),
                                Update.update("deletionRequestedAt", Date.from(requestedAt)),
                                COLLECTION)
                        .getMatchedCount()
                > 0;
    }

    @Override
    public void delete(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            return;
        }
        this.mongo.remove(Query.query(Criteria.where("_id").is(new ObjectId(id))), COLLECTION);
    }

    @Override
    public void upsertByEmail(User user) {
        Update update =
                new Update()
                        .set("passwordHash", user.passwordHash())
                        .set("birthDate", user.birthDate().toString())
                        .set("role", user.role().name())
                        .set("status", user.status().name())
                        .set("name", user.name())
                        .set("pronounSet", name(user.pronounSet()))
                        .set("termsVersion", user.termsVersion())
                        .set("privacyVersion", user.privacyVersion())
                        .set("emailVerifiedAt", date(user.emailVerifiedAt()))
                        .set("pendingEmail", null)
                        .setOnInsert("metricsId", user.metricsId().toString())
                        .setOnInsert("termsAcceptedAt", date(user.termsAcceptedAt()))
                        .setOnInsert("createdAt", date(user.createdAt()))
                        .setOnInsert("deletionRequestedAt", null);
        if (user.country() == null) {
            update.unset("country");
        } else {
            update.set("country", user.country());
        }
        this.mongo.upsert(
                Query.query(Criteria.where("email").is(user.email())), update, COLLECTION);
    }

    private static Document toDocument(User user) {
        Document document =
                new Document("email", user.email())
                        .append("passwordHash", user.passwordHash())
                        .append("birthDate", user.birthDate().toString())
                        .append("role", user.role().name())
                        .append("status", user.status().name())
                        .append("metricsId", user.metricsId().toString())
                        .append("name", user.name())
                        .append("pronounSet", name(user.pronounSet()))
                        .append("termsVersion", user.termsVersion())
                        .append("privacyVersion", user.privacyVersion())
                        .append("termsAcceptedAt", date(user.termsAcceptedAt()))
                        .append("createdAt", date(user.createdAt()))
                        .append("emailVerifiedAt", date(user.emailVerifiedAt()))
                        .append("pendingEmail", user.pendingEmail())
                        .append("deletionRequestedAt", date(user.deletionRequestedAt()));
        if (user.country() != null) {
            document.append("country", user.country());
        }
        return document;
    }

    private static User toUser(Document document) {
        String pronounSet = document.getString("pronounSet");
        return new User(
                document.getObjectId("_id").toHexString(),
                document.getString("email"),
                document.getString("passwordHash"),
                LocalDate.parse(document.getString("birthDate")),
                Role.valueOf(document.getString("role")),
                AccountStatus.valueOf(document.getString("status")),
                UUID.fromString(document.getString("metricsId")),
                document.getString("name"),
                (pronounSet == null) ? null : PronounSet.valueOf(pronounSet),
                document.getString("termsVersion"),
                document.getString("privacyVersion"),
                instant(document.getDate("termsAcceptedAt")),
                instant(document.getDate("createdAt")),
                instant(document.getDate("emailVerifiedAt")),
                document.getString("pendingEmail"),
                instant(document.getDate("deletionRequestedAt")),
                document.getString("country"));
    }

    private static String name(Enum<?> value) {
        return (value == null) ? null : value.name();
    }

    private static Date date(Instant instant) {
        return (instant == null) ? null : Date.from(instant);
    }

    private static Instant instant(Date date) {
        return (date == null) ? null : date.toInstant();
    }
}
