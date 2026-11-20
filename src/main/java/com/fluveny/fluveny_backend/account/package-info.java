/**
 * Módulo `account`: usuários, perfil, status, consentimento, exclusão e tokens de uso único (AD-3,
 * AD-13). Usa a porta {@code MailSender} pela named interface {@code mail :: sender} (história
 * 2.2), a mesma seta account → mail da AD-3.
 */
@ApplicationModule(allowedDependencies = {"mail", "mail :: sender", "metrics", "platform :: *"})
package com.fluveny.fluveny_backend.account;

import org.springframework.modulith.ApplicationModule;
