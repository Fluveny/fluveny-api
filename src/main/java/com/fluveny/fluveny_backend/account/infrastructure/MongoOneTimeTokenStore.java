package com.fluveny.fluveny_backend.account.infrastructure;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 * {@code one_time_tokens} no Mongo, com o índice único do {@code tokenHash} e o TTL do {@code
 * expiresAt} ({@link AccountIndexes}). O {@code userId} fica como {@code ObjectId}, o mesmo {@code
 * _id} de {@code users}. O consumo é um {@code findAndRemove}: valida e apaga numa operação só, sem
 * transação.
 */
@Repository
class MongoOneTimeTokenStore implements OneTimeTokenStore {

    static final String COLLECTION = "one_time_tokens";

    private final MongoTemplate mongo;

    MongoOneTimeTokenStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void insert(OneTimeToken token) {
        this.mongo.insert(
                new Document("tokenHash", token.tokenHash())
                        .append("userId", new ObjectId(token.userId()))
                        .append("purpose", token.purpose().name())
                        .append("email", token.email())
                        .append("createdAt", Date.from(token.createdAt()))
                        .append("expiresAt", Date.from(token.expiresAt())),
                COLLECTION);
    }

    @Override
    public Optional<OneTimeToken> consume(String tokenHash, TokenPurpose purpose, Instant now) {
        Query query =
                Query.query(
                        Criteria.where("tokenHash")
                                .is(tokenHash)
                                .and("purpose")
                                .is(purpose.name())
                                .and("expiresAt")
                                .gt(Date.from(now)));
        return Optional.ofNullable(this.mongo.findAndRemove(query, Document.class, COLLECTION))
                .map(document ->
                        new OneTimeToken(
                                document.getString("tokenHash"),
                                document.getObjectId("userId").toHexString(),
                                TokenPurpose.valueOf(document.getString("purpose")),
                                document.getString("email"),
                                document.getDate("createdAt").toInstant(),
                                document.getDate("expiresAt").toInstant()));
    }

    @Override
    public void deleteByUser(String userId, TokenPurpose purpose) {
        if (userId == null || !ObjectId.isValid(userId)) {
            return;
        }
        this.mongo.remove(
                Query.query(
                        Criteria.where("userId")
                                .is(new ObjectId(userId))
                                .and("purpose")
                                .is(purpose.name())),
                COLLECTION);
    }
}
