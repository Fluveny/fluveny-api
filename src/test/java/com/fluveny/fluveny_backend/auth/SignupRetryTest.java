package com.fluveny.fluveny_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fluveny.fluveny_backend.account.NewStudent;
import com.fluveny.fluveny_backend.account.PreparedStudent;
import com.fluveny.fluveny_backend.account.RegisterStudent;
import com.fluveny.fluveny_backend.account.RegisteredStudent;
import com.fluveny.fluveny_backend.platform.security.Role;
import com.mongodb.MongoException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * A retentativa do cadastro (história 2.6): o erro com o rótulo {@code TransientTransactionError}
 * (a corrida de dois cadastros com os mesmos recibos) refaz a transação inteira, até 3 vezes; os
 * outros erros sobem na hora. O BCrypt ({@code prepare}) roda uma vez, fora das tentativas.
 */
class SignupRetryTest {

    private final RegisterStudent register = mock(RegisterStudent.class);

    private final Sessions sessions = mock(Sessions.class);

    private final Signup signup =
            new Signup(this.register, this.sessions, new InMemoryAccounts.NoOpTransactionManager());

    private static final NewStudent STUDENT =
            new NewStudent(
                    "ana@example.com", "senha123", LocalDate.of(1995, 5, 20), null, "0.1", "0.2");

    private final PreparedStudent prepared = mock(PreparedStudent.class);

    @BeforeEach
    void prepare() {
        when(this.register.prepare(STUDENT)).thenReturn(this.prepared);
    }

    private static RuntimeException transientError() {
        MongoException mongo = new MongoException(112, "WriteConflict");
        mongo.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
        // Como o Spring traduz: a exceção do driver vem como causa.
        return new DataIntegrityViolationException("WriteConflict", mongo);
    }

    private static RegisteredStudent registered() {
        return new RegisteredStudent("u1", Role.STUDENT, null);
    }

    @Test
    void aTransientErrorRunsTheTransactionAgain() {
        when(this.register.register(this.prepared))
                .thenThrow(transientError())
                .thenThrow(transientError())
                .thenReturn(registered());
        when(this.sessions.create("u1", Role.STUDENT))
                .thenReturn(new Sessions.NewSession("token", Instant.EPOCH));

        Signup.Registered result = this.signup.signup(STUDENT);

        assertThat(result.student().userId()).isEqualTo("u1");
        verify(this.register, times(3)).register(this.prepared);
        verify(this.register, times(1)).prepare(STUDENT);
        verify(this.sessions, times(1)).create("u1", Role.STUDENT);
    }

    @Test
    void afterThreeTransientErrorsTheErrorGoesUp() {
        when(this.register.register(this.prepared)).thenThrow(transientError());

        assertThatThrownBy(() -> this.signup.signup(STUDENT))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(this.register, times(Signup.ATTEMPTS)).register(this.prepared);
    }

    @Test
    void anyOtherErrorGoesUpAtOnce() {
        when(this.register.register(this.prepared)).thenThrow(new IllegalStateException("falha"));

        assertThatThrownBy(() -> this.signup.signup(STUDENT))
                .isInstanceOf(IllegalStateException.class);
        verify(this.register, times(1)).register(this.prepared);
        assertThat(Signup.isTransient(new MongoException("sem rótulo"))).isFalse();
    }

    @Test
    void aCycleInTheCauseChainEnds() {
        RuntimeException first = new RuntimeException("a");
        RuntimeException second = new RuntimeException("b", first);
        first.initCause(second);

        assertThat(Signup.isTransient(first)).isFalse();
    }
}
