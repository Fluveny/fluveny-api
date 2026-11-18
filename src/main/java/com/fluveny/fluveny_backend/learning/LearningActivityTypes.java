package com.fluveny.fluveny_backend.learning;

import com.fluveny.fluveny_backend.content.activity.CardsActivity;
import com.fluveny.fluveny_backend.content.activity.ChoiceActivity;
import com.fluveny.fluveny_backend.content.activity.DialogueActivity;
import com.fluveny.fluveny_backend.content.activity.ExpressionActivity;
import com.fluveny.fluveny_backend.content.activity.FreeActivity;
import com.fluveny.fluveny_backend.content.activity.GapsActivity;
import com.fluveny.fluveny_backend.content.activity.GrammarActivity;
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
 * pode ser concluído sem correção. O {@code sinceAppVersion} é a tag do App que renderiza o Tipo
 * (nulo enquanto ela não existe). A publicação fora do perfil local recusa o Tipo sem ela ou acima
 * do {@code FLUVENY_MIN_APP_VERSION} do ambiente ({@code 422 UNSUPPORTED_ACTIVITY_TYPE}).
 */
@Component
public class LearningActivityTypes implements ActivityTypeSource {

    /**
     * A tag {@code v0.1.0} do {@code fluveny-app} (história 1.20): a primeira versão do App que
     * renderiza os 9 Tipos do Estágio 1.
     */
    static final String APP_0_1_0 = "0.1.0";

    /**
     * As conclusões ({@code completions}) que cada Tipo aceita (histórias 1.13 a 1.19). Os
     * expositivos (Diálogo, Cartões de expressões, Expressão Útil destacada, Grammar Time) só são
     * vistos; a Escolha, o Associar e as Lacunas não se concluem sem Tentativa; a Fala pode ser
     * pulada ("Não posso falar agora"); a Resposta Livre só é vista (ENVIAR, sem mandar o texto), e
     * o Extra Help dela é Tentativa. Tipo fora do mapa não aceita nenhuma.
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
                    Set.of(Outcome.VIEWED),
                    GrammarActivity.TYPE,
                    Set.of(Outcome.VIEWED));

    @Override
    public List<ActivityTypeSupport> activityTypes() {
        return List.of(
                new ActivityTypeSupport(ChoiceActivity.TYPE, APP_0_1_0),
                new ActivityTypeSupport(DialogueActivity.TYPE, APP_0_1_0),
                new ActivityTypeSupport(CardsActivity.TYPE, APP_0_1_0),
                new ActivityTypeSupport(ExpressionActivity.TYPE, APP_0_1_0),
                new ActivityTypeSupport(SpeechActivity.TYPE, APP_0_1_0),
                new ActivityTypeSupport(MatchActivity.TYPE, APP_0_1_0),
                new ActivityTypeSupport(GapsActivity.TYPE, APP_0_1_0),
                new ActivityTypeSupport(FreeActivity.TYPE, APP_0_1_0),
                new ActivityTypeSupport(GrammarActivity.TYPE, APP_0_1_0));
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
