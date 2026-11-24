package com.fluveny.fluveny_backend.account;

import com.fluveny.fluveny_backend.account.infrastructure.GuardianConsentRecord;
import com.fluveny.fluveny_backend.account.infrastructure.GuardianConsentStore;
import com.fluveny.fluveny_backend.account.infrastructure.OneTimeToken;
import com.fluveny.fluveny_backend.account.infrastructure.OneTimeTokenStore;
import com.fluveny.fluveny_backend.account.infrastructure.TokenPurpose;
import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.mail.MailPriority;
import com.fluveny.fluveny_backend.mail.MailSender;
import com.fluveny.fluveny_backend.platform.events.GuardianConsentConfirmed;
import com.fluveny.fluveny_backend.platform.events.TransientTransactions;
import com.fluveny.fluveny_backend.platform.security.OpaqueTokens;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.ErrorCode;
import com.fluveny.fluveny_backend.platform.web.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O consentimento do responsável pelo adolescente de 12 a 17 anos (história 2.11, FR-2, AD-10,
 * AD-15) {@code [ASSUMPTION: mecanismo a validar com a coordenação]}: envia o link de uso único ao
 * responsável, reenvia com o limite da confirmação de e-mail e confirma.
 *
 * <p>A confirmação roda numa transação Mongo, com a retentativa dos erros transitórios: consome o
 * token, ativa a conta (atualização condicional, que apaga o {@code users.guardianEmail}), grava
 * {@code guardian_consents} só com hashes (pseudonimizados) e as versões dos documentos legais em
 * vigor ({@code fluveny.legal.*}) e publica o {@link GuardianConsentConfirmed}. O e-mail do
 * responsável sai do outbox assim que é enviado ({@code purgeOnSend}): depois da confirmação, só o
 * hash fica.
 *
 * <p>O e-mail, o token e o link nunca vão para o log, o Sentry, o evento nem a resposta.
 */
@Service
public class GuardianConsent {

    /** Quanto vale o link, como o da confirmação de e-mail. */
    static final Duration VALIDITY = EmailConfirmation.VALIDITY;

    // REVISÃO JURÍDICA (NFR24): assunto e texto do e-mail ao responsável.
    static final String SUBJECT = "Autorize o cadastro no Fluveny";

    static final String PATH = "/consentimento?token=";

    static final String TERMS_PATH = "/termos";

    /**
     * O prefixo dos hashes de {@code guardian_consents}: o mesmo sha256 dos tokens, separado. É
     * pseudonimização, não anonimização: sem segredo, quem tem um e-mail ou IP candidato recalcula
     * o hash. Os documentos são dado pessoal e saem na exclusão.
     */
    static final String EMAIL_HASH_PREFIX = "fluveny-guardian-email\n";

    static final String IP_HASH_PREFIX = "fluveny-guardian-ip\n";

    private final UserStore users;

    private final OneTimeTokenStore tokens;

    private final GuardianConsentStore consents;

    private final MailSender mail;

    private final ApplicationEventPublisher events;

    private final TransactionTemplate transaction;

    private final Clock clock;

    private final String appUrl;

    private final String termsVersion;

    private final String privacyVersion;

    private final RateLimiter perMinute;

    private final RateLimiter perDay;

    GuardianConsent(
            UserStore users,
            OneTimeTokenStore tokens,
            GuardianConsentStore consents,
            MailSender mail,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${fluveny.app.url:}") String appUrl,
            @Value("${fluveny.legal.terms-version}") String termsVersion,
            @Value("${fluveny.legal.privacy-version}") String privacyVersion) {
        this.termsVersion = termsVersion;
        this.privacyVersion = privacyVersion;
        this.users = users;
        this.tokens = tokens;
        this.consents = consents;
        this.mail = mail;
        this.events = events;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.appUrl = EmailConfirmation.stripTrailingSlashes(appUrl.strip());
        this.perMinute =
                new RateLimiter(EmailConfirmation.RESENDS_PER_MINUTE, Duration.ofMinutes(1), clock);
        this.perDay = new RateLimiter(EmailConfirmation.RESENDS_PER_DAY, Duration.ofDays(1), clock);
    }

    /**
     * Cria o token e enfileira o e-mail ao responsável, com prioridade alta (o cadastro chama logo
     * depois de gravar a conta).
     *
     * @param name o nome do adolescente; nulo ou fora de {@link EmailConfirmation#isPlainName} fica
     *     de fora
     */
    void issue(String userId, String guardianEmail, String name) {
        Instant now = now();
        String token = OpaqueTokens.generate();
        this.tokens.insert(
                new OneTimeToken(
                        OpaqueTokens.hash(token),
                        userId,
                        TokenPurpose.GUARDIAN_CONSENT,
                        guardianEmail,
                        now,
                        now.plus(VALIDITY)));
        this.mail.enqueuePurgedOnSend(guardianEmail, SUBJECT, text(name, token), MailPriority.HIGH);
    }

    /**
     * Apaga o link anterior, cria outro e enfileira o e-mail ao responsável. Com {@code
     * newGuardianEmail}, troca antes o e-mail do responsável (o adolescente digitou errado).
     *
     * @param newGuardianEmail o e-mail novo do responsável, como digitado; nulo mantém o atual
     * @throws ApiException {@code 409 GUARDIAN_CONSENT_NOT_PENDING} se a conta não espera o
     *     responsável; {@code 400 VALIDATION_FAILED} no {@code guardianEmail} se ele é o do próprio
     *     adolescente ou se falta (a conta pendente sem responsável gravado); {@code 429
     *     TOO_MANY_REQUESTS} (com {@code Retry-After}) acima de 1 por minuto ou 5 por dia
     */
    public void resend(String userId, String newGuardianEmail) {
        User user =
                this.users
                        .findById(userId)
                        .orElseThrow(() ->
                                new ApiException(
                                        ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.detail()));
        if (user.status() != AccountStatus.PENDING_GUARDIAN_CONSENT) {
            throw notPending();
        }
        String guardianEmail;
        if (newGuardianEmail != null && !newGuardianEmail.isBlank()) {
            guardianEmail = RegisterStudent.normalizeEmail(newGuardianEmail);
            if (guardianEmail.equals(user.email())) {
                throw RegisterStudent.invalid("guardianEmail", "Use o e-mail do seu responsável.");
            }
        } else {
            guardianEmail =
                    this.users
                            .findGuardianEmail(user.id())
                            .orElseThrow(() ->
                                    RegisterStudent.invalid(
                                            "guardianEmail",
                                            "Informe o e-mail do seu responsável."));
        }
        this.perMinute.acquire(userId);
        this.perDay.acquire(userId);
        if (!this.users.setGuardianEmail(user.id(), guardianEmail)) {
            // Confirmada (ou excluída) entre a leitura e aqui.
            throw notPending();
        }
        this.tokens.deleteByUser(userId, TokenPurpose.GUARDIAN_CONSENT);
        issue(user.id(), guardianEmail, user.name());
    }

    /**
     * Confirma o consentimento com o token do link.
     *
     * @param ip o IP de quem confirmou; só o hash é gravado
     * @throws ApiException {@code 400 GUARDIAN_CONSENT_TOKEN_INVALID} se o token é desconhecido,
     *     venceu ou já foi usado, ou se a conta não espera mais esse responsável; nada muda
     */
    public void confirm(String token, String ip) {
        if (token == null || token.isBlank()) {
            throw tokenInvalid();
        }
        String tokenHash = OpaqueTokens.hash(token);
        TransientTransactions.run(
                "Consentimento do responsável",
                () -> this.transaction.execute(status -> confirmInTransaction(tokenHash, ip)));
    }

    private Void confirmInTransaction(String tokenHash, String ip) {
        Instant now = now();
        OneTimeToken found =
                this.tokens
                        .consume(tokenHash, TokenPurpose.GUARDIAN_CONSENT, now)
                        .orElseThrow(GuardianConsent::tokenInvalid);
        // Lança e desfaz o consumo: a conta sumiu, já foi ativada ou mudou de responsável.
        User user = this.users.findById(found.userId()).orElseThrow(GuardianConsent::tokenInvalid);
        if (!this.users.activateGuardianConsent(user.id(), found.email())) {
            throw tokenInvalid();
        }
        this.consents.insert(
                new GuardianConsentRecord(
                        user.id(),
                        OpaqueTokens.hash(EMAIL_HASH_PREFIX + found.email()),
                        now,
                        this.termsVersion,
                        this.privacyVersion,
                        (ip == null) ? null : OpaqueTokens.hash(IP_HASH_PREFIX + ip)));
        this.events.publishEvent(new GuardianConsentConfirmed(user.id(), user.metricsId()));
        return null;
    }

    // REVISÃO JURÍDICA (NFR24): o texto do e-mail ao responsável.
    private String text(String name, String token) {
        String who =
                EmailConfirmation.isPlainName(name)
                        ? name.strip() + " quer"
                        : "Um adolescente que indicou você como responsável quer";
        return "Olá!\n\n"
                + who
                + " estudar inglês no Fluveny e precisa da sua autorização.\n\n"
                + "O que o Fluveny guarda: o e-mail e a senha (cifrada) da conta, a data de"
                + " nascimento, o nome, o país e os pronomes usados nas histórias, se informados,"
                + " e o progresso nas atividades. Enquanto você não autoriza, a conta já existe e"
                + " guarda os dados de perfil, as trocas de senha e de e-mail e este seu e-mail;"
                + " o progresso fica só no aparelho. Ao autorizar, guardamos a data, as versões"
                + " dos termos e uma forma cifrada (pseudonimizada) do seu e-mail e do IP de quem"
                + " autorizou, e o seu e-mail em texto é apagado. Esses dados servem só para o"
                + " estudo e nunca são vendidos.\n\n"
                + "Termos de uso e política de privacidade: "
                + this.appUrl
                + TERMS_PATH
                + "\n\n"
                + "Para autorizar, abra o link abaixo:\n\n"
                + this.appUrl
                + PATH
                + token
                + "\n\n"
                + "O link vale por "
                + VALIDITY.toDays()
                + " dias. Se você não conhece este pedido, ignore este e-mail:"
                + " o progresso não é salvo sem a sua autorização.\n\n"
                + "Equipe Fluveny\n";
    }

    private static ApiException notPending() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "GUARDIAN_CONSENT_NOT_PENDING",
                "Sua conta já foi autorizada pelo responsável.");
    }

    private static ApiException tokenInvalid() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "GUARDIAN_CONSENT_TOKEN_INVALID",
                "Este link de autorização não vale mais. Peça outro ao adolescente.");
    }

    private Instant now() {
        return this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
