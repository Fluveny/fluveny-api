package com.fluveny.fluveny_backend.account.infrastructure;

import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/** {@code guardian_consents} no Mongo (história 2.11), com índice no {@code userId}. */
@Repository
class MongoGuardianConsentStore implements GuardianConsentStore {

    static final String COLLECTION = "guardian_consents";

    private final MongoTemplate mongo;

    MongoGuardianConsentStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void insert(GuardianConsentRecord consent) {
        this.mongo.insert(
                new Document("userId", consent.userId())
                        .append("guardianEmailHash", consent.guardianEmailHash())
                        .append("confirmedAt", Date.from(consent.confirmedAt()))
                        .append("termsVersion", consent.termsVersion())
                        .append("privacyVersion", consent.privacyVersion())
                        .append("ipHash", consent.ipHash()),
                COLLECTION);
    }

    @Override
    public List<GuardianConsentRecord> findByUser(String userId) {
        return this.mongo
                .find(Query.query(Criteria.where("userId").is(userId)), Document.class, COLLECTION)
                .stream()
                .map(document ->
                        new GuardianConsentRecord(
                                document.getString("userId"),
                                document.getString("guardianEmailHash"),
                                document.getDate("confirmedAt").toInstant(),
                                document.getString("termsVersion"),
                                document.getString("privacyVersion"),
                                document.getString("ipHash")))
                .toList();
    }

    @Override
    public void deleteAllFor(String userId) {
        if (userId == null) {
            return;
        }
        this.mongo.remove(Query.query(Criteria.where("userId").is(userId)), COLLECTION);
    }
}
