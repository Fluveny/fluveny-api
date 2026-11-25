package com.fluveny.fluveny_backend.account;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * O inventário de dados pessoais por coleção Mongo (história 2.10, FR-6, NFR7, LGPD): o dono de
 * cada coleção, os campos que identificam a pessoa e o que a exclusão da conta faz com eles. O
 * {@link AccountDeletionIT} varre todas as coleções do banco e falha se aparece uma fora daqui.
 *
 * <p><b>Regra:</b> história que cria coleção nova acrescenta a coleção aqui. Se ela guarda dado
 * pessoal, o módulo dono trata o {@code AccountDeletionRequested} num {@code @EventListener}
 * síncrono, apaga o que é da conta, e o {@link AccountDeletionIT} ganha dados nela antes da
 * exclusão.
 */
final class AccountDeletionInventory {

    /** O que a exclusão faz com os documentos da conta. */
    enum Treatment {
        /** Apagados na transação da exclusão. */
        DELETED,
        /** Ficam: não guardam dado pessoal nem nada que leve à pessoa. */
        KEPT_WITHOUT_PII
    }

    /**
     * Uma coleção.
     *
     * @param personalFields os campos que identificam a pessoa (vazio se não há)
     */
    record Entry(
            String collection, String owner, List<String> personalFields, Treatment treatment) {}

    static final List<Entry> ENTRIES =
            List.of(
                    new Entry(
                            "users",
                            "account",
                            List.of(
                                    "_id",
                                    "email",
                                    "pendingEmail",
                                    "passwordHash",
                                    "birthDate",
                                    "name",
                                    "country",
                                    "pronounSet",
                                    "metricsId"),
                            Treatment.DELETED),
                    new Entry(
                            "one_time_tokens",
                            "account",
                            List.of("userId", "email", "tokenHash"),
                            Treatment.DELETED),
                    new Entry(
                            "sessions", "auth", List.of("userId", "tokenHash"), Treatment.DELETED),
                    new Entry(
                            "login_throttle",
                            "auth",
                            List.of("keyHash (account, account_ip)", "accountKeyHash"),
                            Treatment.DELETED),
                    new Entry(
                            "mail_outbox",
                            "mail",
                            List.of("to", "text"),
                            // A confirmação da exclusão entra depois e some no envio.
                            Treatment.DELETED),
                    new Entry("activity_results", "learning", List.of("userId"), Treatment.DELETED),
                    new Entry("unit_progress", "learning", List.of("userId"), Treatment.DELETED),
                    // Depois da migração (2.6), nada liga o deviceId à conta.
                    new Entry(
                            "visitor_attempts", "learning", List.of(), Treatment.KEPT_WITHOUT_PII),
                    // Agregados com o metricsId, que perde o vínculo com a pessoa (FR-6).
                    new Entry("events", "metrics", List.of(), Treatment.KEPT_WITHOUT_PII),
                    new Entry("unit_versions", "content", List.of(), Treatment.KEPT_WITHOUT_PII),
                    new Entry("unit_pointers", "content", List.of(), Treatment.KEPT_WITHOUT_PII));

    static final Map<String, Entry> BY_COLLECTION =
            ENTRIES.stream().collect(Collectors.toMap(Entry::collection, Function.identity()));

    private AccountDeletionInventory() {}
}
