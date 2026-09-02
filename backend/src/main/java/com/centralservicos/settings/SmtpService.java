package com.centralservicos.settings;

import com.centralservicos.identity.PasswordResetRequested;
import com.centralservicos.shared.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Properties;

@Service
class SmtpService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpService.class);

    private final SettingsService settings;
    private final String publicBaseUrl;

    SmtpService(SettingsService settings, @Value("${app.public-base-url}") String publicBaseUrl) {
        this.settings = settings;
        this.publicBaseUrl = publicBaseUrl;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendResetEmail(PasswordResetRequested event) {
        SettingsService.SmtpSnapshot snapshot;
        try {
            snapshot = settings.smtpSnapshot();
        } catch (DomainException exception) {
            return;
        }
        var message = new SimpleMailMessage();
        message.setTo(event.recipientEmail());
        message.setFrom(snapshot.fromAddress());
        message.setSubject("Redefinição de senha");
        message.setText("Olá, " + event.recipientName() + ".\n\nAcesse "
                + publicBaseUrl + "/reset-password?token=" + event.rawToken()
                + " para redefinir sua senha. O link expira em 15 minutos.");
        try {
            mailSender(snapshot).send(message);
        } catch (Exception exception) {
            LOGGER.warn("Não foi possível enviar e-mail de recuperação de senha.");
        }
    }

    void testConnection() {
        try {
            mailSender(settings.smtpSnapshot()).testConnection();
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DomainException(HttpStatus.BAD_GATEWAY, "Não foi possível conectar ao servidor SMTP.");
        }
    }

    private JavaMailSenderImpl mailSender(SettingsService.SmtpSnapshot snapshot) {
        var sender = new JavaMailSenderImpl();
        sender.setHost(snapshot.host());
        sender.setPort(snapshot.port());
        sender.setUsername(snapshot.username());
        sender.setPassword(snapshot.password());
        var properties = new Properties();
        properties.put("mail.smtp.auth", String.valueOf(snapshot.username() != null && !snapshot.username().isBlank()));
        properties.put("mail.smtp.starttls.enable", String.valueOf(snapshot.tls()));
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "5000");
        properties.put("mail.smtp.writetimeout", "5000");
        sender.setJavaMailProperties(properties);
        return sender;
    }
}
