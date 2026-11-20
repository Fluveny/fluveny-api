package com.fluveny.fluveny_backend.mail.local;

import com.fluveny.fluveny_backend.mail.MailPriority;
import com.fluveny.fluveny_backend.mail.infrastructure.MailStatus;
import com.fluveny.fluveny_backend.mail.infrastructure.OutboxMail;
import com.fluveny.fluveny_backend.mail.infrastructure.OutboxStore;
import com.fluveny.fluveny_backend.platform.security.PublicRoute;
import com.fluveny.fluveny_backend.platform.web.ApiResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /local/mail} (história 2.2): os últimos e-mails do outbox, com o texto, para abrir o
 * link de confirmação sem SMTP. Só no perfil {@code local}, fora de {@code /api/v1} e do contrato;
 * é a única saída do texto (e do token do link) fora do e-mail.
 */
@RestController
@Profile("local")
@PublicRoute
@RequestMapping("/local")
class MailLocalController {

    static final int LIMIT = 20;

    private final OutboxStore store;

    MailLocalController(OutboxStore store) {
        this.store = store;
    }

    @GetMapping("/mail")
    ApiResponse<List<LocalMail>> latest() {
        return ApiResponse.of(
                this.store.latest(LIMIT).stream().map(MailLocalController::toLocal).toList());
    }

    private static LocalMail toLocal(OutboxMail mail) {
        return new LocalMail(
                mail.id(),
                mail.to(),
                mail.subject(),
                mail.text(),
                mail.priority(),
                mail.status(),
                mail.attempts(),
                mail.createdAt(),
                mail.sentAt());
    }

    record LocalMail(
            String id,
            String to,
            String subject,
            String text,
            MailPriority priority,
            MailStatus status,
            int attempts,
            Instant createdAt,
            Instant sentAt) {}
}
