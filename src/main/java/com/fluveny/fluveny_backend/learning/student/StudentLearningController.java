package com.fluveny.fluveny_backend.learning.student;

import com.fluveny.fluveny_backend.learning.visitor.AttemptResult;
import com.fluveny.fluveny_backend.learning.visitor.CompletionResult;
import com.fluveny.fluveny_backend.platform.security.RequiresRole;
import com.fluveny.fluveny_backend.platform.security.Role;
import com.fluveny.fluveny_backend.platform.security.SessionPrincipal;
import com.fluveny.fluveny_backend.platform.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tentativa, "Ver resposta", conclusão, eventos, Progresso e painel do Aluno da sessão (histórias
 * 2.4 e 2.5). O dono vem só da sessão; não há id na rota nem no corpo. As escritas contam num
 * limite por Aluno ({@code 429 TOO_MANY_REQUESTS} com {@code Retry-After}) e, com a sessão do
 * navegador (cookie), exigem {@code X-Fluveny-Client} ({@code 403 CLIENT_HEADER_REQUIRED}).
 */
@RestController
@Tag(name = "students")
@RequiresRole(Role.STUDENT)
@RequestMapping("/api/v1/students/me")
class StudentLearningController {

    /** O que vale para toda rota: sem sessão válida, {@code 401}. */
    private static final String SESSION =
            " Sem sessão, com sessão vencida ou de conta fora de ACTIVE: 401 com"
                    + " WWW-Authenticate.";

    /** O que vale para as quatro escritas, além do {@link #SESSION}. */
    private static final String WRITE =
            SESSION
                    + " Com a sessão do navegador (cookie), exige X-Fluveny-Client, senão 403"
                    + " CLIENT_HEADER_REQUIRED. Mais de 60 escritas por minuto do Aluno, somando"
                    + " attempts, attempts/reveal, completions e events: 429 TOO_MANY_REQUESTS com"
                    + " Retry-After.";

    /** A sequência, nas três escritas de Progresso (história 2.5). */
    private static final String LOCK =
            " Estágio bloqueado na versão publicada (o anterior não está concluído): 409"
                    + " STAGE_LOCKED, sem gravar nada (na Tentativa e no Ver resposta, antes de"
                    + " corrigir). O Estágio é o da Atividade na versão publicada, mesmo com"
                    + " versionId antigo; Atividade ou Estágio fora da versão publicada não"
                    + " trava.";

    private final StudentAttempts attempts;

    private final StudentEvents events;

    private final StudentDashboards dashboards;

    StudentLearningController(
            StudentAttempts attempts, StudentEvents events, StudentDashboards dashboards) {
        this.attempts = attempts;
        this.events = events;
        this.dashboards = dashboards;
    }

    @Operation(
            operationId = "submitStudentAttempt",
            description =
                    "Corrige a Tentativa do Aluno e grava a primeira na Atividade, com o XP dela."
                            + " expected e as justificativas só voltam no Acerto (100%). Reenviar o"
                            + " mesmo attemptId com o mesmo corpo devolve o XP gravado; outro"
                            + " attemptId (Tentar de novo, refazer) devolve o Feedback com xpAwarded"
                            + " 0, sem gravar. Tipo divergente ou expositivo: 400 INVALID_ANSWER;"
                            + " versão ou Atividade inexistente: 404. Campo desconhecido dá 400."
                            + LOCK
                            + WRITE)
    @PostMapping("/attempts")
    ApiResponse<AttemptResult> attempt(@Valid @RequestBody StudentAttempt attempt) {
        return ApiResponse.of(this.attempts.attempt(userId(), attempt));
    }

    @Operation(
            operationId = "revealStudentAttempt",
            description =
                    "Ver resposta: corrige a mesma Tentativa e devolve o Resultado com expected e"
                            + " as justificativas, sem XP e sem gravar. Exige o registro do Aluno"
                            + " na Atividade (na Resposta Livre, uma Tentativa do Extra Help), senão"
                            + " 409 ATTEMPT_REQUIRED. Tipo divergente ou expositivo: 400"
                            + " INVALID_ANSWER; versão ou Atividade inexistente: 404. Campo"
                            + " desconhecido dá 400."
                            + LOCK
                            + WRITE)
    @PostMapping("/attempts/reveal")
    ApiResponse<AttemptResult> reveal(@Valid @RequestBody StudentAttempt attempt) {
        return ApiResponse.of(this.attempts.reveal(userId(), attempt));
    }

    @Operation(
            operationId = "submitStudentCompletion",
            description =
                    "Conclui uma Atividade sem correção, sem XP, e grava a primeira na Atividade:"
                            + " os expositivos dialogue, cards, expression, grammar e free com"
                            + " VIEWED, e a speech pulada, com SKIPPED. Fora disso, 409"
                            + " COMPLETION_NOT_ALLOWED. A conclusão VIEWED de um free devolve a"
                            + " modelAnswer. Versão ou Atividade inexistente: 404. Campo"
                            + " desconhecido dá 400."
                            + LOCK
                            + WRITE)
    @PostMapping("/completions")
    ApiResponse<CompletionResult> complete(@Valid @RequestBody StudentCompletion completion) {
        return ApiResponse.of(this.attempts.complete(userId(), completion));
    }

    @Operation(
            operationId = "recordStudentEvent",
            description =
                    "Grava um evento do Aluno, sem PII, com a hora do servidor e o metricsId da"
                            + " conta. ACTIVITY_STARTED a cada abertura de Atividade, com versionId e"
                            + " activityKey; Atividade inexistente dá 404. Campo desconhecido dá"
                            + " 400. Responde 202 sem corpo. Se a gravação do evento falhar, 500."
                            + WRITE)
    @PostMapping("/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void record(@Valid @RequestBody StudentEvent event) {
        this.events.record(userId(), event);
    }

    @Operation(
            operationId = "getMyProgress",
            description =
                    "O Progresso do Aluno da sessão: o XP total (soma de todos os registros,"
                            + " inclusive de Atividades removidas) e a primeira conclusão de cada"
                            + " Atividade. resultPct só no GRADED."
                            + SESSION)
    @GetMapping("/progress")
    ApiResponse<StudentProgress> progress() {
        return ApiResponse.of(this.attempts.progress(userId()));
    }

    @Operation(
            operationId = "getMyDashboard",
            description =
                    "O painel do Aluno da sessão, calculado a cada leitura sobre a versão publicada"
                            + " de cada Unidade: XP total (o mesmo do /progress), Badges, Estágios"
                            + " concluídos de total, Resultado médio, o estado de cada Estágio"
                            + " (COMPLETED, CURRENT, LOCKED) e onde retomar (next). Atividade"
                            + " removida do conteúdo só conta no XP. name ausente se o Aluno não"
                            + " informou. A leitura reconcilia as conclusões: um Estágio coberto e"
                            + " ainda não gravado (por exemplo, porque uma versão nova removeu a"
                            + " Atividade pendente) é gravado nela, com completedAt na hora da"
                            + " leitura, e publica o STAGE_COMPLETED."
                            + SESSION)
    @GetMapping("/dashboard")
    ApiResponse<StudentDashboard> dashboard() {
        return ApiResponse.of(this.dashboards.dashboard(userId()));
    }

    private static String userId() {
        return SessionPrincipal.current().userId();
    }
}
