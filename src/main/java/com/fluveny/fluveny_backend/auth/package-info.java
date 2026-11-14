/** Módulo `auth`: login, sessões, throttling e redefinição de senha (AD-3, AD-12). */
@ApplicationModule(allowedDependencies = {"account", "mail", "platform :: *"})
package com.fluveny.fluveny_backend.auth;

import org.springframework.modulith.ApplicationModule;
