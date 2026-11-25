package com.fluveny.fluveny_backend.account;

import com.fluveny.fluveny_backend.account.infrastructure.OneTimeTokenStore;
import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.mail.MailPriority;
import com.fluveny.fluveny_backend.mail.MailSender;
import com.fluveny.fluveny_backend.platform.events.AccountDeletionRequested;
import com.fluveny.fluveny_backend.platform.events.AfterCommit;
import com.fluveny.fluveny_backend.platform.events.TransientTransactions;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.fluveny.fluveny_backend.platform.web.ErrorCode;
import io.sentry.Sentry;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A exclusão da conta pelo próprio Aluno (história 2.10, FR-6, NFR7, AD-13), na hora, sem job nem
 * período de carência. Confere a senha atual ({@link AccountPassword}, com o mesmo limite de erros
 * da troca de senha) e, numa só transação Mongo: (1) marca o {@code deletionRequestedAt}, só se
 * ainda é nulo; (2) publica o {@link AccountDeletionRequested}, que {@code auth}, {@code mail} e
 * {@code learning} tratam na mesma transação, apagando os próprios dados; (3) apaga os tokens de
 * uso único; (4) apaga o documento de {@code users}, o único elo entre o {@code metricsId} e a
 * pessoa. Os documentos de {@code events} ficam, agregados e sem dado pessoal.
 *
 * <p>Qualquer falha desfaz tudo e dá {@code 503 ACCOUNT_DELETION_FAILED}: a conta continua intacta
 * e o Aluno tenta de novo; se a conta já não existe depois da falha (commit de resultado
 * desconhecido), vale como excluída. A conta que sumiu, já marcada ou fora de Aluno ativo entre a
 * conferência da senha e a transação dá {@code 404 NOT_FOUND}. O e-mail pendente que já é de outra
 * conta não vai no evento. Um erro {@code TransientTransactionError} refaz a transação, até {@link
 * TransientTransactions#ATTEMPTS} vezes. Depois do commit, a confirmação vai para o e-mail da
 * conta, sem link, e some do outbox assim que sai. Nada de id, e-mail nem {@code metricsId} no log.
 */
@Service
public class AccountDeletion {

    static final String SUBJECT = "Sua conta do Fluveny foi excluída";

    static final String TEXT =
            "Olá!\n\n"
                    + "Sua conta do Fluveny foi excluída, como você pediu. Apagamos o seu"
                    + " progresso e os seus dados pessoais.\n\n"
                    + "Se quiser voltar a estudar, é só criar uma conta nova.\n\n"
                    + "Equipe Fluveny\n";

    private static final Logger log = LoggerFactory.getLogger(AccountDeletion.class);

    private final AccountPassword password;

    private final UserStore users;

    private final OneTimeTokenStore tokens;

    private final MailSender mail;

    private final ApplicationEventPublisher events;

    private final TransactionTemplate transaction;

    private final Clock clock;

    AccountDeletion(
            AccountPassword password,
            UserStore users,
            OneTimeTokenStore tokens,
            MailSender mail,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.password = password;
        this.users = users;
        this.tokens = tokens;
        this.mail = mail;
        this.events = events;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Exclui a conta, com a senha atual.
     *
     * @throws ApiException {@code 400 CURRENT_PASSWORD_INVALID}, {@code 429 TOO_MANY_REQUESTS},
     *     {@code 404 NOT_FOUND} (a conta sumiu ou não é um Aluno ativo) ou {@code 503
     *     ACCOUNT_DELETION_FAILED}; em todos, nada muda
     */
    public void delete(String userId, String currentPassword) {
        User user = this.password.verifyCurrent(userId, currentPassword);
        try {
            TransientTransactions.run(
                    "Exclusão",
                    () -> this.transaction.execute(status -> deleteInTransaction(userId)));
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // Commit de resultado desconhecido (ou outra falha depois dele): se a conta sumiu, a
            // exclusão valeu.
            if (!accountGone(userId)) {
                // Só a classe: a mensagem do driver pode trazer o e-mail.
                log.error("Falha ao excluir a conta ({})", ex.getClass().getName());
                Sentry.captureException(sanitized("Falha ao excluir a conta", ex));
                throw failed();
            }
        }
        String email = user.email();
        AfterCommit.run(() -> confirm(email));
    }

    private boolean accountGone(String userId) {
        try {
            return this.users.findById(userId).isEmpty();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** Os quatro passos. */
    private Void deleteInTransaction(String userId) {
        User user = this.users.findById(userId).orElseThrow(AccountDeletion::gone);
        if (!this.users.markDeletionRequested(user.id(), now())) {
            // Sumiu, já está marcada ou deixou de ser um Aluno ativo.
            throw gone();
        }
        String pendingEmail = user.pendingEmail();
        if (pendingEmail != null && this.users.findByEmail(pendingEmail).isPresent()) {
            // O e-mail pendente já é de outra conta: nada dele é desta.
            pendingEmail = null;
        }
        this.events.publishEvent(
                new AccountDeletionRequested(
                        user.id(), user.metricsId(), user.email(), pendingEmail));
        this.tokens.deleteAllFor(user.id());
        this.users.delete(user.id());
        return null;
    }

    private void confirm(String email) {
        try {
            this.mail.enqueuePurgedOnSend(email, SUBJECT, TEXT, MailPriority.HIGH);
        } catch (RuntimeException ex) {
            // A conta já foi; a falha vai para o log e o Sentry, sem o endereço.
            log.error(
                    "Falha ao enfileirar a confirmação da exclusão ({})", ex.getClass().getName());
            Sentry.captureException(sanitized("Falha ao enfileirar a confirmação da exclusão", ex));
        }
    }

    private static IllegalStateException sanitized(String message, RuntimeException ex) {
        IllegalStateException copy =
                new IllegalStateException(message + " (" + ex.getClass().getName() + ")");
        copy.setStackTrace(ex.getStackTrace());
        return copy;
    }

    private Instant now() {
        return this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static ApiException gone() {
        return new ApiException(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.detail());
    }

    private static ApiException failed() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "ACCOUNT_DELETION_FAILED",
                "Não conseguimos excluir sua conta agora. Tente de novo.");
    }
}
