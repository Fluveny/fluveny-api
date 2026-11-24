package com.fluveny.fluveny_backend.learning.student;

import com.fluveny.fluveny_backend.account.ProfileChange;
import com.fluveny.fluveny_backend.account.StudentProfiles;
import com.fluveny.fluveny_backend.content.ContentActivities;
import com.fluveny.fluveny_backend.content.activity.DialogueActivity;
import com.fluveny.fluveny_backend.content.activity.DialogueLine;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * As respostas do Aluno ao Diálogo (história 2.9, FR-13, AD-8): o nome e o país respondidos numa
 * fala {@code ask} são gravados pela porta única de {@code account} ({@link
 * StudentProfiles#updateProfile}). Só aceita o campo que o Diálogo daquela Versão pergunta. Conta
 * no limite de escritas do Aluno.
 */
@Service
class DialogueAnswers {

    private final ContentActivities activities;

    private final StudentProfiles profiles;

    private final StudentWriteLimit limit;

    DialogueAnswers(
            ContentActivities activities, StudentProfiles profiles, StudentWriteLimit limit) {
        this.activities = activities;
        this.profiles = profiles;
        this.limit = limit;
    }

    /**
     * @throws ApiException {@code 400 VALIDATION_FAILED} se a Atividade não existe na Versão, não é
     *     um Diálogo, não pergunta o campo enviado, nenhum campo veio, ou o valor está fora das
     *     regras do perfil; nada é gravado
     */
    void answer(String userId, DialogueAnswer answer) {
        this.limit.acquire(userId);
        String name = blankToNull(answer.name());
        String country = blankToNull(answer.country());
        if (name == null && country == null) {
            throw invalid("name", "Envie o nome ou o país respondido no Diálogo.");
        }
        DialogueActivity dialogue = dialogue(answer.versionId(), answer.activityKey());
        if (name != null && !asks(dialogue, DialogueLine.ASK_NAME)) {
            throw invalid("name", "Este Diálogo não pergunta o nome.");
        }
        if (country != null && !asks(dialogue, DialogueLine.ASK_COUNTRY)) {
            throw invalid("country", "Este Diálogo não pergunta o país.");
        }
        this.profiles.updateProfile(
                userId,
                new ProfileChange(
                        (name == null) ? null : ProfileChange.Value.of(name),
                        (country == null) ? null : ProfileChange.Value.of(country),
                        null));
    }

    private DialogueActivity dialogue(String versionId, String activityKey) {
        ContentActivities.Found found;
        try {
            found = this.activities.find(versionId, activityKey);
        } catch (ApiException ex) {
            if (ex.getStatus() == HttpStatus.NOT_FOUND) {
                throw invalid("activityKey", "A Atividade não existe nesta versão.");
            }
            throw ex;
        }
        if (found.activity() instanceof DialogueActivity dialogue) {
            return dialogue;
        }
        throw invalid("activityKey", "A Atividade não é um Diálogo.");
    }

    private static boolean asks(DialogueActivity dialogue, String field) {
        return dialogue.lines().stream().anyMatch(line -> field.equals(line.ask()));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(
                ErrorCode.VALIDATION_FAILED.status(),
                ErrorCode.VALIDATION_FAILED.name(),
                ErrorCode.VALIDATION_FAILED.detail(),
                Map.of("errors", List.of(Map.of("field", field, "message", message))));
    }
}
