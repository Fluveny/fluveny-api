package com.fluveny.fluveny_backend.learning;

import com.fluveny.fluveny_backend.content.activity.CardsActivity;
import com.fluveny.fluveny_backend.content.activity.ChoiceActivity;
import com.fluveny.fluveny_backend.content.activity.DialogueActivity;
import com.fluveny.fluveny_backend.content.activity.ExpressionActivity;
import com.fluveny.fluveny_backend.content.activity.FreeActivity;
import com.fluveny.fluveny_backend.content.activity.GapsActivity;
import com.fluveny.fluveny_backend.content.activity.MatchActivity;
import com.fluveny.fluveny_backend.content.activity.SpeechActivity;
import com.fluveny.fluveny_backend.learning.grading.Outcome;
import com.fluveny.fluveny_backend.platform.meta.ActivityTypeSource;
import com.fluveny.fluveny_backend.platform.meta.ActivityTypeSupport;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Os Tipos que o {@code learning} atende, em {@code GET /api/v1/meta} (AD-6, AD-11), e como cada um
 * pode ser concluído sem correção. O {@code sinceAppVersion} fica nulo até existir a tag do App que
 * renderiza o Tipo; até lá a publicação fora do perfil local recusa o Tipo ({@code 422
 * UNSUPPORTED_ACTIVITY_TYPE}).
 */
@Component
public class LearningActivityTypes implements ActivityTypeSource {

    /**
     * As conclusões ({@code completions}) que cada Tipo aceita (histórias 1.13 a 1.18). Os
     * expositivos (Diálogo, Cartões de expressões, Expressão Útil destacada) só são vistos; a
     * Escolha, o Associar e as Lacunas não se concluem sem Tentativa; a Fala pode ser pulada ("Não
     * posso falar agora"); a Resposta Livre só é vista (ENVIAR, sem mandar o texto), e o Extra Help
     * dela é Tentativa. Tipo fora do mapa não aceita nenhuma.
     */
    private static final Map<String, Set<Outcome>> COMPLETIONS =
            Map.of(
                    DialogueActivity.TYPE,
                    Set.of(Outcome.VIEWED),
                    CardsActivity.TYPE,
                    Set.of(Outcome.VIEWED),
                    ExpressionActivity.TYPE,
                    Set.of(Outcome.VIEWED),
                    ChoiceActivity.TYPE,
                    Set.of(),
                    SpeechActivity.TYPE,
                    Set.of(Outcome.SKIPPED),
                    MatchActivity.TYPE,
                    Set.of(),
                    GapsActivity.TYPE,
                    Set.of(),
                    FreeActivity.TYPE,
                    Set.of(Outcome.VIEWED));

    @Override
    public List<ActivityTypeSupport> activityTypes() {
        return List.of(
                new ActivityTypeSupport(ChoiceActivity.TYPE, null),
                new ActivityTypeSupport(DialogueActivity.TYPE, null),
                new ActivityTypeSupport(CardsActivity.TYPE, null),
                new ActivityTypeSupport(ExpressionActivity.TYPE, null),
                new ActivityTypeSupport(SpeechActivity.TYPE, null),
                new ActivityTypeSupport(MatchActivity.TYPE, null),
                new ActivityTypeSupport(GapsActivity.TYPE, null),
                new ActivityTypeSupport(FreeActivity.TYPE, null));
    }

    /** Os Tipos com regra de conclusão; o teste confere que batem com {@link #activityTypes()}. */
    static Set<String> completionTypes() {
        return COMPLETIONS.keySet();
    }

    /** Se uma Atividade do Tipo pode ser concluída com {@code outcome}, sem correção. */
    public static boolean allowsCompletion(String type, Outcome outcome) {
        return COMPLETIONS.getOrDefault(type, Set.of()).contains(outcome);
    }
}
