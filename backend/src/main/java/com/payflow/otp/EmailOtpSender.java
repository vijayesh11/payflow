package com.payflow.otp;

import com.payflow.model.OtpPurpose;
import com.payflow.model.User;
import com.payflow.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Real delivery by email (e.g. Gmail SMTP with an app password, or Brevo).
 * Enable with OTP_DELIVERY=email plus SPRING_MAIL_HOST / SPRING_MAIL_USERNAME / SPRING_MAIL_PASSWORD.
 */
@Component
@ConditionalOnProperty(name = "app.otp.delivery", havingValue = "email")
public class EmailOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(EmailOtpSender.class);

    private final JavaMailSender mail;
    private final String from;
    private final int expiryMinutes;

    public EmailOtpSender(JavaMailSender mail, @Value("${app.mail.from}") String from,
                          @Value("${app.otp.expiry-minutes}") int expiryMinutes) {
        this.mail = mail;
        this.from = from;
        this.expiryMinutes = expiryMinutes;
    }

    @Override
    public void send(User user, OtpPurpose purpose, String code, String context) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(user.getEmail());
        msg.setSubject(code + " is your PayFlow verification code");
        msg.setText("Hi " + user.getFullName() + ",\n\n"
                + context + "\n\n"
                + "Your one-time code is: " + code + "\n\n"
                + "It expires in " + expiryMinutes + " minutes. PayFlow will never ask you to share this code "
                + "— if you didn't request it, change your password.\n\n— PayFlow (sandbox)");
        try {
            mail.send(msg);
        } catch (MailException e) {
            log.error("Failed to send OTP email to user {}: {}", user.getId(), e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Couldn't send the verification code. Please try again.");
        }
    }

    @Override
    public String channel() { return "email"; }
}
