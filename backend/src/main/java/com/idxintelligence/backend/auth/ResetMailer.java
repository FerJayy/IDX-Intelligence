package com.idxintelligence.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends the password-reset email through Gmail's SMTP server (MAIL_USERNAME / MAIL_PASSWORD = the site's
 * Gmail address and its app password). Without them nothing is sent; the link is never logged.
 */
@Component
public class ResetMailer {

    private static final Logger log = LoggerFactory.getLogger(ResetMailer.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final String from;

    public ResetMailer(ObjectProvider<JavaMailSender> sender, @Value("${spring.mail.username:}") String from) {
        this.sender = sender;
        this.from = from;
    }

    public boolean configured() {
        return !from.isBlank() && sender.getIfAvailable() != null;
    }

    /** @param link the full reset link, valid for {@link AuthService#RESET_VALID_FOR} */
    public void sendReset(String to, String link, boolean indonesian) {
        if (!configured()) {
            log.warn("Password reset requested but email isn't configured (MAIL_USERNAME/MAIL_PASSWORD); nothing sent");
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("IDX Intelligence <" + from + ">");
        message.setTo(to);
        if (indonesian) {
            message.setSubject("Atur ulang kata sandi IDX Intelligence");
            message.setText("""
                    Halo,

                    Ada permintaan untuk mengatur ulang kata sandi akun IDX Intelligence dengan email ini.
                    Buka tautan berikut dalam 30 menit untuk membuat kata sandi baru:

                    %s

                    Tautan hanya bisa dipakai sekali. Jika kamu tidak memintanya, abaikan email ini; kata sandimu tidak berubah.
                    """.formatted(link));
        } else {
            message.setSubject("Reset your IDX Intelligence password");
            message.setText("""
                    Hi,

                    Someone asked to reset the password of the IDX Intelligence account with this email.
                    Open this link within 30 minutes to choose a new password:

                    %s

                    The link works once. If you didn't ask for this, ignore this email; your password stays the same.
                    """.formatted(link));
        }
        sender.getObject().send(message);
    }
}
