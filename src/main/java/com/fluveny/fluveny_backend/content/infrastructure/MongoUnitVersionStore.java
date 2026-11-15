package com.fluveny.fluveny_backend.content.infrastructure;

import com.fluveny.fluveny_backend.content.publication.UnitVersion;
import com.fluveny.fluveny_backend.content.publication.UnitVersionStore;
import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

/**
 * {@code unit_versions}: {@code _id} é o hash, e o documento só é escrito na primeira gravação
 * ({@code $setOnInsert}). Nada aqui apaga nem reescreve uma versão (AD-4).
 */
@Repository
class MongoUnitVersionStore implements UnitVersionStore {

    static final String COLLECTION = "unit_versions";

    private final MongoTemplate mongo;

    MongoUnitVersionStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean insertIfAbsent(UnitVersion version) {
        Update update =
                new Update()
                        .setOnInsert("unitKey", version.unitKey())
                        .setOnInsert("schemaVersion", version.schemaVersion())
                        .setOnInsert("order", version.order())
                        .setOnInsert("canonicalJson", version.canonicalJson())
                        .setOnInsert("createdAt", Date.from(version.createdAt()));
        try {
            UpdateResult result =
                    this.mongo.upsert(
                            Query.query(Criteria.where("_id").is(version.id())),
                            update,
                            COLLECTION);
            return result.getUpsertedId() != null;
        } catch (DuplicateKeyException ex) {
            // Duas publicações iguais ao mesmo tempo: a outra gravou primeiro.
            return false;
        }
    }

    @Override
    public Optional<UnitVersion> findById(String id) {
        return Optional.ofNullable(
                        this.mongo.findOne(
                                Query.query(Criteria.where("_id").is(id)),
                                Document.class,
                                COLLECTION))
                .map(MongoUnitVersionStore::toVersion);
    }

    @Override
    public List<UnitVersion> findAllById(Collection<String> ids) {
        return this.mongo
                .find(Query.query(Criteria.where("_id").in(ids)), Document.class, COLLECTION)
                .stream()
                .map(MongoUnitVersionStore::toVersion)
                .toList();
    }

    private static UnitVersion toVersion(Document document) {
        return new UnitVersion(
                document.getString("_id"),
                document.getString("unitKey"),
                document.getInteger("schemaVersion"),
                document.getInteger("order"),
                document.getString("canonicalJson"),
                toInstant(document.getDate("createdAt")));
    }

    static Instant toInstant(Date date) {
        return (date == null) ? null : date.toInstant();
    }
}
