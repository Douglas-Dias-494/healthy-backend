package com.healthy.auth;

import com.healthy.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MailPasswordResetNotifierTest {

    @Test
    void sendsTheLinkToTheUserWithoutAnythingSensitiveElse() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MailPasswordResetNotifier notifier = new MailPasswordResetNotifier(sender, "nao-responda@healthy.com");
        Usuario user = new Usuario();
        user.setNome("Maria");
        user.setEmail("maria@exemplo.com");

        notifier.sendResetLink(user, "https://app.healthy.com/redefinir-senha?token=abc123", Duration.ofMinutes(30));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        assertThat(message.getFrom()).isEqualTo("nao-responda@healthy.com");
        assertThat(message.getTo()).containsExactly("maria@exemplo.com");
        assertThat(message.getText())
                .contains("https://app.healthy.com/redefinir-senha?token=abc123")
                .contains("30 minutos")
                .contains("Maria");
    }
}
