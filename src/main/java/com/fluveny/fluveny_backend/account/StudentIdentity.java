package com.fluveny.fluveny_backend.account;

import java.util.UUID;

/**
 * O que {@code learning} lê da conta do Aluno (história 2.4): o identificador de métricas e o
 * pronome do corretor. Sem e-mail, nome, data de nascimento nem o id da conta.
 *
 * @param metricsId o identificador de {@code events}; não é o id da conta
 * @param pronounSet os pronomes; nulo vale como they/them
 */
public record StudentIdentity(UUID metricsId, PronounSet pronounSet) {}
