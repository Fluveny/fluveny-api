package com.fluveny.fluveny_backend.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga os {@code @Scheduled} da API (o despacho do outbox de {@code mail}, história 2.2). Fica
 * aqui, sem condição, para que desligar uma tarefa (por exemplo {@code
 * fluveny.mail.dispatch.enabled}) não desligue as outras.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {}
