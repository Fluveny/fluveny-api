/**
 * Módulo `account`: usuários, perfil, status, consentimento, exclusão e tokens de uso único (AD-3, AD-13).
 */
@ApplicationModule(allowedDependencies = { "mail", "metrics", "platform" })
package com.fluveny.fluveny_backend.account;

import org.springframework.modulith.ApplicationModule;
