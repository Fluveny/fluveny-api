package com.fluveny.fluveny_backend.auth.infrastructure;

import com.fluveny.fluveny_backend.platform.security.Role;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * {@code sessions} no Mongo, com o índice único do {@code tokenHash} e o TTL do {@code expiresAt}
 * ({@link AuthIndexes}). O {@code userId} fica como {@code ObjectId}, o mesmo {@code _id} de {@code
 * users}, com índice (a redefinição de senha apaga as sessões da conta, história 2.8).
 */
@Repository
class MongoSessionStore implements SessionStore {

    static final String COLLECTION = "sessions";

    private final MongoTemplate mongo;

    MongoSessionStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void insert(Session session) {
        this.mongo.insert(
                new Document("tokenHash", session.tokenHash())
                        .append("userId", new ObjectId(session.userId()))
                        .append("role", session.role().name())
                        .append("createdAt", Date.from(session.createdAt()))
                        .append("expiresAt", Date.from(session.expiresAt())),
                COLLECTION);
    }

    @Override
    public Optional<Session> findByTokenHash(String tokenHash) {
        return Optional.ofNullable(
                        this.mongo.findOne(byHash(tokenHash), Document.class, COLLECTION))
                .map(document ->
                        new Session(
                                document.getString("tokenHash"),
                                document.getObjectId("userId").toHexString(),
                                Role.valueOf(document.getString("role")),
                                instant(document.getDate("createdAt")),
                                instant(document.getDate("expiresAt"))));
    }

    @Override
    public void renew(String tokenHash, Instant expiresAt) {
        this.mongo.updateFirst(
                byHash(tokenHash), Update.update("expiresAt", Date.from(expiresAt)), COLLECTION);
    }

    @Override
    public void deleteByTokenHash(String tokenHash) {
        this.mongo.remove(byHash(tokenHash), COLLECTION);
    }

    @Override
    public void deleteByUser(String userId) {
        if (userId == null || !ObjectId.isValid(userId)) {
            return;
        }
        this.mongo.remove(
                Query.query(Criteria.where("userId").is(new ObjectId(userId))), COLLECTION);
    }

    @Override
    public void deleteByUserExcept(String userId, String keptTokenHash) {
        if (userId == null || !ObjectId.isValid(userId) || keptTokenHash == null) {
            return;
        }
        this.mongo.remove(
                Query.query(
                        Criteria.where("userId")
                                .is(new ObjectId(userId))
                                .and("tokenHash")
                                .ne(keptTokenHash)),
                COLLECTION);
    }

    private static Query byHash(String tokenHash) {
        return Query.query(Criteria.where("tokenHash").is(tokenHash));
    }

    private static Instant instant(Date date) {
        return (date == null) ? null : date.toInstant();
    }
}
