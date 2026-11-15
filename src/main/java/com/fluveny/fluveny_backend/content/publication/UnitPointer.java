package com.fluveny.fluveny_backend.content.publication;

import java.time.Instant;

/**
 * O ponteiro {@code published} de uma Unidade no ambiente (AD-4): a versão que o aluno vê.
 *
 * @param unitKey a Unidade
 * @param publishedVersionId o id da {@link UnitVersion} publicada
 * @param updatedAt quando o ponteiro mudou pela última vez
 */
public record UnitPointer(String unitKey, String publishedVersionId, Instant updatedAt) {}
