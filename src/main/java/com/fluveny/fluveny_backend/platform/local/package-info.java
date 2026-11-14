/**
 * Ambiente local (AD-18, história 1.4): só existe no perfil {@code local}, na máquina do dev. API
 * pública para os módulos: o SPI {@code LocalDataSeeder}, que cada módulo implementa com os dados
 * mínimos para testar as próprias telas, e a {@code LocalSeedPassword}. O resto ({@code
 * LocalDataLoader}, {@code LocalProfileGuard} e {@code LocalDataController}) é interno.
 */
@NamedInterface("local")
package com.fluveny.fluveny_backend.platform.local;

import org.springframework.modulith.NamedInterface;
