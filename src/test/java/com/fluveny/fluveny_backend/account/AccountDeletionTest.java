package com.fluveny.fluveny_backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fluveny.fluveny_backend.account.infrastructure.OneTimeTokenStore;
import com.fluveny.fluveny_backend.account.infrastructure.User;
import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.mail.MailPriority;
import com.fluveny.fluveny_backend.mail.MailSender;
import com.fluveny.fluveny_backend.platform.events.TransientTransactions;
import com.fluveny.fluveny_backend.platform.security.Role;
import com.fluveny.fluveny_backend.platform.web.ApiException;
import com.mongodb.MongoException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * A retentativa da exclusão (história 2.10): o erro {@code TransientTransactionError} refaz a
 * transação, até 3 vezes; depois disso, {@code 503 ACCOUNT_DELETION_FAILED}. Se a conta sumiu mesmo
 * com a falha (commit de resultado desconhecido), vale como excluída.
 */
class AccountDeletionTest {

    private final AccountPassword password = mock(AccountPassword.class);

    private final UserStore users = mock(UserStore.class);

    private final MailSender mail = mock(MailSender.class);

    private final AccountDeletion deletion =
            new AccountDeletion(
                    this.password,
                    this.users,
                    mock(OneTimeTokenStore.class),
                    this.mail,
                    mock(ApplicationEventPublisher.class),
                    mock(PlatformTransactionManager.class),
                    Clock.fixed(Instant.parse("2026-11-24T12:00:00Z"), ZoneOffset.UTC));

    private final User user =
            new User(
                    "u1",
                    "ana@example.com",
                    "hash",
                    LocalDate.of(1995, 5, 20),
                    Role.STUDENT,
                    AccountStatus.ACTIVE,
                    UUID.randomUUID(),
                    null,
                    null,
                    "0.1",
                    "0.2",
                    Instant.EPOCH,
                    Instant.EPOCH,
                    null,
                    null,
                    null);

    @BeforeEach
    void prepare() {
        when(this.password.verifyCurrent("u1", "senha123")).thenReturn(this.user);
        when(this.users.findById("u1")).thenReturn(Optional.of(this.user));
    }

    private static RuntimeException transientError() {
        MongoException mongo = new MongoException(112, "WriteConflict");
        mongo.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
        return new DataIntegrityViolationException("WriteConflict", mongo);
    }

    @Test
    void aTransientErrorRunsTheTransactionAgainAndDeletes() {
        when(this.users.markDeletionRequested(eq("u1"), any()))
                .thenThrow(transientError())
                .thenReturn(true);

        this.deletion.delete("u1", "senha123");

        verify(this.users, times(2)).markDeletionRequested(eq("u1"), any());
        verify(this.users).delete("u1");
        verify(this.mail)
                .enqueuePurgedOnSend(
                        eq("ana@example.com"),
                        eq(AccountDeletion.SUBJECT),
                        any(),
                        eq(MailPriority.HIGH));
    }

    @Test
    void alwaysTransientIs503AfterThreeAttempts() {
        when(this.users.markDeletionRequested(eq("u1"), any())).thenThrow(transientError());

        assertThatThrownBy(() -> this.deletion.delete("u1", "senha123"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("ACCOUNT_DELETION_FAILED"));
        verify(this.users, times(TransientTransactions.ATTEMPTS))
                .markDeletionRequested(eq("u1"), any());
        verify(this.users, never()).delete("u1");
        verify(this.mail, never()).enqueuePurgedOnSend(any(), any(), any(), any());
    }

    @Test
    void aFailureWithTheAccountGoneCountsAsDeleted() {
        when(this.users.markDeletionRequested(eq("u1"), any())).thenReturn(true);
        doThrow(new IllegalStateException("commit desconhecido")).when(this.users).delete("u1");
        when(this.users.findById("u1"))
                .thenReturn(Optional.of(this.user))
                .thenReturn(Optional.empty());

        this.deletion.delete("u1", "senha123");

        verify(this.mail).enqueuePurgedOnSend(any(), any(), any(), any());
    }

    @Test
    void anAccountAlreadyMarkedIs404() {
        when(this.users.markDeletionRequested(eq("u1"), any())).thenReturn(false);

        assertThatThrownBy(() -> this.deletion.delete("u1", "senha123"))
                .isInstanceOfSatisfying(
                        ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("NOT_FOUND"));
    }
}
