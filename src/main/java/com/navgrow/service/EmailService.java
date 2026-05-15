package com.navgrow.service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender mailSender;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.contact-email}")
    private String contactEmail;

    @Value("${app.name}")
    private String appName;

    @Async
    public void sendContactNotification(String fromName, String fromEmail, String subject, String message) {
        try {
            var mime = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setTo(contactEmail);
            helper.setSubject("[Website Enquiry] " + subject);
            helper.setText(buildContactHtml(fromName, fromEmail, subject, message), true);
            mailSender.send(mime);
            log.info("Contact notification sent for: {}", fromEmail);
        } catch (Exception e) {
            log.error("Failed to send contact notification: {}", e.getMessage());
        }
    }

    @Async
    public void sendOrderConfirmation(String toEmail, String toName, String orderNumber, String total) {
        try {
            var mime = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("Order Confirmed – " + orderNumber + " | Navgrow Engineering");
            helper.setText(buildOrderHtml(toName, orderNumber, total), true);
            mailSender.send(mime);
            log.info("Order confirmation sent: {}", orderNumber);
        } catch (Exception e) {
            log.error("Failed to send order confirmation: {}", e.getMessage());
        }
    }

    @Async
    public void sendQuoteAcknowledgement(String toEmail, String toName, String serviceType) {
        try {
            var mime = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("Quote Request Received – Navgrow Engineering");
            helper.setText(buildQuoteHtml(toName, serviceType), true);
            mailSender.send(mime);
        } catch (Exception e) {
            log.error("Failed to send quote acknowledgement: {}", e.getMessage());
        }
    }

    private String buildContactHtml(String name, String email, String subject, String message) {
        return "<html><body style='font-family:sans-serif'>" +
               "<h2 style='color:#2563eb'>New Website Enquiry</h2>" +
               "<p><b>From:</b> " + name + " (" + email + ")</p>" +
               "<p><b>Subject:</b> " + subject + "</p>" +
               "<hr/><p>" + message + "</p><hr/>" +
               "<p style='color:#666;font-size:12px'>Sent via navgrow.org contact form</p></body></html>";
    }

    private String buildOrderHtml(String name, String orderNo, String total) {
        return "<html><body style='font-family:sans-serif'>" +
               "<h2 style='color:#2563eb'>Order Confirmed!</h2>" +
               "<p>Dear " + name + ",</p>" +
               "<p>Your order <b>" + orderNo + "</b> has been confirmed.</p>" +
               "<p><b>Total:</b> ₹" + total + " (incl. GST)</p>" +
               "<p>Your order will be dispatched within 1–2 business days. Tracking details will be shared once shipped.</p>" +
               "<p>For queries: <a href='mailto:info@navgrow.org'>info@navgrow.org</a> | +91 89270 70972</p>" +
               "<br/><p>Thank you for choosing Navgrow Engineering!</p></body></html>";
    }

    private String buildQuoteHtml(String name, String serviceType) {
        return "<html><body style='font-family:sans-serif'>" +
               "<h2 style='color:#2563eb'>Quote Request Received</h2>" +
               "<p>Dear " + name + ",</p>" +
               "<p>We've received your quote request for <b>" + serviceType + "</b>.</p>" +
               "<p>Our team will review and send a formal quotation within <b>24 business hours</b>.</p>" +
               "<p>For urgent queries: <a href='https://wa.me/918927070972'>WhatsApp us</a> or call +91 89270 70972.</p>" +
               "<br/><p>— Navgrow Engineering Service Pvt. Ltd.</p></body></html>";
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String toName, String token) {
        try {
            var mime = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("Reset Your Password — Navgrow Engineering");
            String resetUrl = frontendUrl + "/reset-password?token=" + token;
            helper.setText(
                "<html><body style='font-family:sans-serif'>" +
                "<h2 style='color:#2563eb'>Reset Your Password</h2>" +
                "<p>Dear " + toName + ",</p>" +
                "<p>Click the button below to reset your password. This link expires in <b>1 hour</b>.</p>" +
                "<p style='text-align:center;margin:28px 0'>" +
                "<a href='" + resetUrl + "' style='background:#2563eb;color:#fff;padding:12px 28px;border-radius:8px;text-decoration:none;font-weight:bold'>Reset Password</a>" +
                "</p>" +
                "<p>If you didn't request this, please ignore this email.</p>" +
                "<p style='color:#666;font-size:12px'>Navgrow Engineering Service Pvt. Ltd.</p>" +
                "</body></html>", true);
            mailSender.send(mime);
            log.info("Password reset email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email: {}", e.getMessage());
        }
    }

}