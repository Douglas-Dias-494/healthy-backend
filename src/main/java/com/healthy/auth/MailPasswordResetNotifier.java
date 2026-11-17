package com.healthy.auth;

import com.healthy.usuario.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Profile("prod")
public class MailPasswordResetNotifier implements PasswordResetNotifier {

    private final JavaMailSender mailSender;
    private final String from;

    public MailPasswordResetNotifier(JavaMailSender mailSender, @Value("${healthy.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Async
    @Override
    public void sendResetLink(Usuario user, String resetLink, Duration validFor) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("Healthy: redefinição de senha");
        message.setText("""
                Olá, %s.

                Recebemos um pedido para redefinir a senha da sua conta no Healthy.
                Para escolher uma nova senha, acesse o link abaixo (vale por %d minutos e só pode ser usado uma vez):

                %s

                Se você não fez este pedido, ignore este e-mail: sua senha continua a mesma.
                """.formatted(user.getNome(), validFor.toMinutes(), resetLink));
        mailSender.send(message);
    }
}
