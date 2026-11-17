package com.fluveny.fluveny_backend.content.validation;

/** Regras do {@link ContentValidator} (FR-29). O nome é o {@code RULE_CODE} do relatório. */
public enum ContentRule {

    /** Arquivo obrigatório ausente ({@code unit.json}, {@code media.json}). */
    MISSING_FILE,

    /** O arquivo não é um JSON válido (ou repete uma chave de objeto). */
    INVALID_JSON,

    /** Violação do JSON Schema de {@code content/schema/}. */
    SCHEMA_VIOLATION,

    /** O nome do arquivo ou da pasta difere da chave ({@code unitKey}, {@code stageKey}). */
    FILE_NAME_MISMATCH,

    /**
     * Arquivo fora do lugar: em {@code stages/}, o que não for {@code <stageKey>.json} (outra
     * extensão, {@code .JSON}, subpasta); em {@code units/}, arquivo solto. Entradas que começam
     * com {@code .} são ignoradas.
     */
    UNEXPECTED_FILE,

    /** Estágio listado em {@code unit.json} sem arquivo, ou arquivo fora da lista. */
    STAGE_FILE_MISMATCH,

    /** Chave repetida onde deveria ser única (inclusive o {@code order} de duas Unidades). */
    DUPLICATE_KEY,

    /**
     * Atividade avaliada sem resposta aceita (na Escolha, {@code correctOptionKey} fora das opções;
     * no Associar, {@code correctResponseKey} fora de {@code responses} ou repetido entre itens).
     */
    MISSING_ACCEPTED_ANSWER,

    /**
     * O mesmo {@code ask} ({@code name} ou {@code country}) em mais de uma fala do Diálogo: cada
     * pergunta aparece no máximo uma vez por Atividade (história 1.13).
     */
    DUPLICATE_ASK,

    /**
     * Frase da Expressão Útil destacada sem nenhum termo {@code [[texto|expressionKey]]} (história
     * 1.14). Com variantes de pronome, vale para cada uma.
     */
    MISSING_EXPRESSION_TERM,

    /**
     * Frase-alvo ou alternativa da Fala sem nenhuma palavra fora dos placeholders (história 1.15):
     * os placeholders não contam no critério, então a frase não teria o que conferir. Com variantes
     * de pronome, vale para cada uma.
     */
    MISSING_SPEECH_WORD,

    /** {@code mediaKey} fora do manifesto {@code content/media.json}. */
    MEDIA_NOT_FOUND,

    /** Texto com variantes de pronome sem uma das três ({@code he}, {@code she}, {@code they}). */
    PRONOUN_VARIANTS_MISSING,

    /**
     * Placeholder fora da gramática fechada ({@code {name}}, {@code {country}}, {@code {avatar.x}},
     * {@code {likes.x}}).
     */
    UNKNOWN_PLACEHOLDER,

    /**
     * Formatação fora da lista fechada, {@code **} sem par ou {@code [[texto|expressionKey]]}
     * malformado ou com chave inexistente.
     */
    INVALID_MARKUP,

    /**
     * Pacote de publicação fora do formato {@code {unit, stages[], media{}}}: campo a mais, {@code
     * stages} que não é lista, Estágio repetido ou fora da ordem de {@code unit.stages} (história
     * 1.7).
     */
    INVALID_BUNDLE,

    /** Entrada de {@code media} do pacote que a Unidade não referencia (história 1.7). */
    UNREFERENCED_MEDIA
}
