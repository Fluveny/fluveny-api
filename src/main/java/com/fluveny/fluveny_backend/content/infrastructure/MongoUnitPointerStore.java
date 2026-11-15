package com.fluveny.fluveny_backend.content.infrastructure;

import com.fluveny.fluveny_backend.content.publication.UnitPointer;
import com.fluveny.fluveny_backend.content.publication.UnitPointerStore;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/** {@code unit_pointers}: um documento por Unidade, com {@code _id} igual ao {@code unitKey}. */
@Repository
class MongoUnitPointerStore implements UnitPointerStore {

    static final String COLLECTION = "unit_pointers";

    private final MongoTemplate mongo;

    MongoUnitPointerStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void move(String unitKey, String versionId, Instant now) {
        this.mongo.upsert(
                Query.query(Criteria.where("_id").is(unitKey)),
                new Update().set("publishedVersionId", versionId).set("updatedAt", Date.from(now)),
                COLLECTION);
    }

    @Override
    public Optional<UnitPointer> findById(String unitKey) {
        return Optional.ofNullable(
                        this.mongo.findOne(
                                Query.query(Criteria.where("_id").is(unitKey)),
                                Document.class,
                                COLLECTION))
                .map(MongoUnitPointerStore::toPointer);
    }

    @Override
    public List<UnitPointer> findAll() {
        return this.mongo.findAll(Document.class, COLLECTION).stream()
                .map(MongoUnitPointerStore::toPointer)
                .toList();
    }

    private static UnitPointer toPointer(Document document) {
        return new UnitPointer(
                document.getString("_id"),
                document.getString("publishedVersionId"),
                MongoUnitVersionStore.toInstant(document.getDate("updatedAt")));
    }
}
