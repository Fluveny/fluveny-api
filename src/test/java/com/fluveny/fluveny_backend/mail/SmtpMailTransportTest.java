package com.fluveny.fluveny_backend.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * O transporte SMTP (história 2.2), com um {@link JavaMailSender} falso: só envia com o sender do
 * Boot ({@code spring.mail.host}) e o remetente ({@code FLUVENY_MAIL_FROM}), e a mensagem leva
 * remetente, destinatário, assunto e texto.
 */
class SmtpMailTransportTest {

    private final JavaMailSender sender = mock(JavaMailSender.class);

    private static ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (sender != null) {
            factory.addBean("mailSender", sender);
        }
        return factory.getBeanProvider(JavaMailSender.class);
    }

    @Test
    void withoutTheSenderItIsNotConfiguredAndSendsNothing() {
        SmtpMailTransport transport = new SmtpMailTransport(provider(null), "Fluveny <a@b.c>");
        assertThat(transport.configured()).isFalse();
        assertThatThrownBy(() -> transport.send("ana@example.com", "s", "t"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void withoutTheFromItIsNotConfiguredAndSendsNothing() {
        SmtpMailTransport transport = new SmtpMailTransport(provider(this.sender), "  ");
        assertThat(transport.configured()).isFalse();
        assertThatThrownBy(() -> transport.send("ana@example.com", "s", "t"))
                .isInstanceOf(IllegalStateException.class);
        verify(this.sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void withBothItSendsTheMessageFields() {
        SmtpMailTransport transport =
                new SmtpMailTransport(
                        provider(this.sender), " Fluveny <nao-responda@fluveny.test> ");
        assertThat(transport.configured()).isTrue();

        transport.send("ana@example.com", "Confirme seu e-mail no Fluveny", "Olá!\n\nlink");

        ArgumentCaptor<SimpleMailMessage> message =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(this.sender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("Fluveny <nao-responda@fluveny.test>");
        assertThat(message.getValue().getTo()).containsExactly("ana@example.com");
        assertThat(message.getValue().getSubject()).isEqualTo("Confirme seu e-mail no Fluveny");
        assertThat(message.getValue().getText()).isEqualTo("Olá!\n\nlink");
    }
}
