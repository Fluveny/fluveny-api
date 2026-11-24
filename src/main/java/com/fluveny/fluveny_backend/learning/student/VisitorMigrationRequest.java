package com.fluveny.fluveny_backend.learning.student;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * O Progresso do Visitante migrado depois do cadastro (história 2.11): o mesmo bloco {@code
 * visitor} do cadastro (2.6). Corpo fechado.
 *
 * @param deviceId o aparelho do Visitante
 * @param receipts os recibos {@code v1.<payload>.<hmac>}, de 1 a 200, cada um até 1024 caracteres
 */
@Schema(
        name = "VisitorMigrationRequest",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record VisitorMigrationRequest(
        @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull UUID deviceId,
        @ArraySchema(
                        schema =
                                @Schema(
                                        maxLength = MAX_RECEIPT_LENGTH,
                                        description = "Um recibo v1.<payload>.<hmac>."),
                        minItems = 1,
                        maxItems = MAX_RECEIPTS,
                        arraySchema = @Schema(requiredMode = RequiredMode.REQUIRED))
                @NotNull
                @Size(min = 1, max = MAX_RECEIPTS)
                List<@NotNull @Size(max = MAX_RECEIPT_LENGTH) String> receipts) {

    static final int MAX_RECEIPTS = 200;

    static final int MAX_RECEIPT_LENGTH = 1024;

    /** Campo desconhecido: a leitura falha, e a rota responde {@code 400 VALIDATION_FAILED}. */
    @JsonAnySetter
    void rejectUnknown(String field, Object value) {
        throw new IllegalArgumentException("Campo desconhecido.");
    }

    /** Sem o {@code deviceId} nem os recibos. */
    @Override
    public String toString() {
        return "VisitorMigrationRequest[***]";
    }
}
