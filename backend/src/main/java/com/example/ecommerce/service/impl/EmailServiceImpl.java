package com.example.ecommerce.service.impl;

import com.example.ecommerce.entity.Order;
import com.example.ecommerce.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Sends transactional emails. When app.mail.enabled=false (default for local
 * dev without SMTP credentials), emails are logged instead of sent so the
 * order flow never breaks due to missing mail configuration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled}")
    private boolean mailEnabled;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Override
    public void sendOrderConfirmation(Order order) {
        String subject = "Order Confirmation - Order #" + order.getId();
        String body = buildOrderConfirmationBody(order);
        send(order.getUser().getEmail(), subject, body);
    }

    @Override
    public void sendOrderStatusUpdate(Order order) {
        String subject = "Order #" + order.getId() + " status updated to " + order.getStatus();
        String body = "<p>Hi " + order.getUser().getName() + ",</p>"
                + "<p>Your order #" + order.getId() + " is now <b>" + order.getStatus() + "</b>.</p>";
        send(order.getUser().getEmail(), subject, body);
    }

    private void send(String to, String subject, String htmlBody) {
        if (!mailEnabled) {
            log.info("[MAIL DISABLED] Would send email to={} subject='{}'", to, subject);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("Failed to build email to {}: {}", to, e.getMessage());
        } catch (Exception e) {
            // Never let a mail failure (e.g. bad SMTP creds) break the order flow
            log.error("Unexpected error sending email to {}: {}", to, e.getMessage());
        }
    }

    private String buildOrderConfirmationBody(Order order) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p>Hi ").append(order.getUser().getName()).append(",</p>");
        sb.append("<p>Thank you for your order! Here are your order details:</p>");
        sb.append("<ul>");
        order.getItems().forEach(item -> sb.append("<li>")
                .append(item.getProduct().getName())
                .append(" x ").append(item.getQuantity())
                .append(" - ").append(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .append("</li>"));
        sb.append("</ul>");
        sb.append("<p><b>Total: ").append(order.getTotalAmount()).append("</b></p>");
        sb.append("<p>Shipping to: ").append(order.getShippingAddress()).append("</p>");
        return sb.toString();
    }
}
