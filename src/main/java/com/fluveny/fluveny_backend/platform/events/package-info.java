/**
 * Os eventos de domínio entre módulos no sentido inverso do grafo da AD-3 (história 2.6): o tipo
 * fica aqui, na {@code platform}, e não no módulo que publica, para que nenhum evento feche um
 * ciclo (o {@code AccountDeletionRequested} da 2.10 iria de {@code account} para {@code mail}, que
 * {@code account} já usa). Os eventos são síncronos, na mesma thread e na mesma transação de quem
 * publica; quem trata usa {@code @EventListener}, nunca um listener assíncrono.
 */
@NamedInterface("events")
package com.fluveny.fluveny_backend.platform.events;

import org.springframework.modulith.NamedInterface;
