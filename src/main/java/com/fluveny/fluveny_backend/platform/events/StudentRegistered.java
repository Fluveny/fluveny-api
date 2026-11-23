package com.fluveny.fluveny_backend.platform.events;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma conta de Aluno acabou de ser gravada (história 2.6, AD-3): publicado por {@code account} logo
 * depois de gravar {@code users}, dentro da transação do cadastro. {@code learning} trata e migra o
 * Progresso do Visitante na mesma transação. Sem e-mail, nome, país nem data de nascimento.
 *
 * @param userId o id da conta
 * @param metricsId o identificador de {@code events} da conta
 * @param visitor os recibos do Visitante; nulo sem o convite (aí ninguém migra nada)
 */
public record StudentRegistered(String userId, UUID metricsId, Visitor visitor) {

    public StudentRegistered {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(metricsId, "metricsId");
    }

    /** Sem o {@code userId} nem os recibos. */
    @Override
    public String toString() {
        return "StudentRegistered[fromVisitor=" + (this.visitor != null) + "]";
    }

    /**
     * O Progresso do Visitante que vem com o cadastro.
     *
     * @param deviceId o aparelho do Visitante
     * @param receipts os recibos {@code v1.<payload>.<hmac>} das rotas do Visitante (história 1.12)
     */
    public record Visitor(UUID deviceId, List<String> receipts) {

        public Visitor {
            Objects.requireNonNull(deviceId, "deviceId");
            receipts = List.copyOf(receipts);
        }

        /** Sem o {@code deviceId} nem os recibos. */
        @Override
        public String toString() {
            return "Visitor[receipts=" + this.receipts.size() + "]";
        }
    }
}
