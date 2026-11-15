package com.fluveny.fluveny_backend.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.SearchTerms;
import java.util.regex.Pattern;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.query.Criteria;

/** Termo de busca é sempre texto literal (NFR5). */
class SearchTermsTest {

    @Test
    void operatorIsLiteralText() {
        String term = "{\"$ne\":1}";
        Pattern pattern = Pattern.compile(SearchTerms.literal(term));

        assertThat(pattern.matcher("busca " + term).find()).isTrue();
        assertThat(pattern.matcher("qualquer coisa").find()).isFalse();
    }

    @Test
    void regexMetacharactersAreLiteral() {
        Pattern pattern = Pattern.compile(SearchTerms.literal(".*"));

        assertThat(pattern.matcher("abc").find()).isFalse();
        assertThat(pattern.matcher("a.*b").find()).isTrue();
        assertThat(Pattern.compile(SearchTerms.literal("a\\E.*")).matcher("xyz").find()).isFalse();
    }

    @Test
    void staysAValueInTheCriteria() {
        Document query =
                Criteria.where("name")
                        .regex(SearchTerms.literal("{\"$ne\":1}"), "i")
                        .getCriteriaObject();

        Object value = query.get("name");
        assertThat(value).isNotInstanceOf(Document.class);
        assertThat(value.toString()).contains("$ne");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void nullOrBlankTermMatchesEverything(String term) {
        String literal = SearchTerms.literal(term);

        assertThat(literal).isEqualTo(Pattern.quote(""));
        assertThat(Pattern.compile(literal).matcher("qualquer coisa").find()).isTrue();
    }

    @Test
    void rejectsTermsOverTheMaximum() {
        assertThat(SearchTerms.literal(" alex ")).isEqualTo(Pattern.quote("alex"));
        assertThatThrownBy(() -> SearchTerms.literal("a".repeat(SearchTerms.MAX_LENGTH + 1)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_FAILED");
    }
}
