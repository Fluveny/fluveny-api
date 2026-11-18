package com.fluveny.fluveny_backend.account.local;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fluveny.fluveny_backend.account.Passwords;
import com.fluveny.fluveny_backend.account.infrastructure.AccountIndexes;
import com.fluveny.fluveny_backend.account.infrastructure.UserStore;
import com.fluveny.fluveny_backend.platform.local.LocalSeedPassword;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Sem {@code LOCAL_SEED_PASSWORD}, ou com ela fora da regra de senha, o carregador de {@code
 * account} pula os Alunos sem lançar: o {@code mise run up} sobe do mesmo jeito (história 2.1). Sem
 * Mongo.
 */
class AccountLocalSeederTest {

    private final AccountIndexes indexes = mock(AccountIndexes.class);

    private final UserStore users = mock(UserStore.class);

    private final LocalSeedPassword password = mock(LocalSeedPassword.class);

    private final AccountLocalSeeder seeder =
            new AccountLocalSeeder(
                    this.indexes, this.users, new Passwords(), this.password, Clock.systemUTC());

    @Test
    void withoutThePasswordNothingIsStoredAndNothingIsThrown() {
        when(this.password.value())
                .thenThrow(new IllegalStateException("LOCAL_SEED_PASSWORD vazia"));
        assertThatNoException().isThrownBy(this.seeder::seed);
        verify(this.indexes).ensure();
        verifyNoInteractions(this.users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"curta1", "semnumeros", "12345678"})
    void withAPasswordOutsideTheRuleNothingIsStoredAndNothingIsThrown(String value) {
        when(this.password.value()).thenReturn(value);
        assertThatNoException().isThrownBy(this.seeder::seed);
        verify(this.indexes).ensure();
        verifyNoInteractions(this.users);
    }
}
