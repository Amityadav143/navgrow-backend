/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend-url:http://navgrow.org}")
    private String frontendUrl;

    @Value("${app.contact-email:info@navgrow.org}")
    private String contactEmail;

    @Value("${app.name:Navgrow Engineering}")
    private String appName;

    /** The "From" address — same as SMTP username */
    @Value("${spring.mail.username:info@navgrow.org}")
    private String fromEmail;

    // ── Contact notification ──────────────────────────────────────────────────
    @Async
    public void sendContactNotification(String fromName, String fromEmail,
                                        String subject, String message) {
        try {
            var mime   = mailSender.createMimeMessage();
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

    // ── Order confirmation ────────────────────────────────────────────────────
    @Async
    public void sendOrderConfirmation(String toEmail, String toName,
                                      String orderNumber, String total) {
        try {
            var mime   = mailSender.createMimeMessage();
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

    // ── Quote acknowledgement ─────────────────────────────────────────────────
    @Async
    public void sendQuoteAcknowledgement(String toEmail, String toName, String serviceType) {
        try {
            var mime   = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("Quote Request Received – Navgrow Engineering");
            helper.setText(buildQuoteHtml(toName, serviceType), true);
            mailSender.send(mime);
        } catch (Exception e) {
            log.error("Failed to send quote acknowledgement: {}", e.getMessage());
        }
    }

    // ── Quote request → admin notification ────────────────────────────────────
    /**
     * Emails the office inbox whenever a calculator estimate is submitted, so the
     * team is alerted even before opening the admin dashboard.
     */
    @Async
    public void sendQuoteAdminNotification(com.navgrow.entity.QuoteRequest q) {
        try {
            var mime   = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setTo(contactEmail);
            helper.setSubject("[New Quote Request] " + q.getServiceType() + " — " + q.getName());
            String est = q.getEstLow() != null
                ? "₹" + q.getEstLow().toPlainString() + " – ₹" + (q.getEstHigh() != null ? q.getEstHigh().toPlainString() : "?")
                : "—";
            String addons = q.getAddons() != null && !q.getAddons().isEmpty() ? String.join(", ", q.getAddons()) : "None";
            String html = """
                <div style="font-family:Arial,sans-serif;max-width:560px">
                  <h2 style="color:#1e3a8a">New Quote Request</h2>
                  <table style="border-collapse:collapse;width:100%%;font-size:14px">
                    %s
                  </table>
                  <p style="margin-top:16px">
                    <a href="%s/admin/quotes" style="background:#1e3a8a;color:#fff;padding:10px 18px;
                       border-radius:8px;text-decoration:none;font-weight:bold">Open in Admin Dashboard</a>
                  </p>
                </div>""".formatted(
                    row("Name", q.getName()) + row("Email", q.getEmail()) + row("Phone", q.getPhone())
                    + row("Company", q.getCompany()) + row("Industry", q.getIndustry()) + row("City", q.getCity())
                    + row("Service", q.getServiceType()) + row("Scope", q.getScope()) + row("Duration", q.getDuration())
                    + row("Add-ons", addons) + row("Urgency", q.getUrgency())
                    + row("Estimate", est) + row("Notes", q.getNotes()),
                    frontendUrl);
            helper.setText(html, true);
            mailSender.send(mime);
            log.info("Quote admin notification sent for {}", q.getEmail());
        } catch (Exception e) {
            log.error("Failed to send quote admin notification: {}", e.getMessage());
        }
    }

    private String row(String label, String value) {
        if (value == null || value.isBlank()) return "";
        return "<tr><td style=\"padding:6px 10px;border:1px solid #e5e7eb;font-weight:bold;width:130px\">"
             + label + "</td><td style=\"padding:6px 10px;border:1px solid #e5e7eb\">"
             + org.springframework.web.util.HtmlUtils.htmlEscape(value) + "</td></tr>";
    }

    // ── Catalogue download → visitor + admin ──────────────────────────────────
    /** Sends the requester a friendly note with a link back to the catalogue. */
    @Async
    public void sendCatalogueToLead(String toEmail, String toName) {
        try {
            var mime   = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("Your Navgrow Company Profile & Catalogue");
            String html = """
                <div style="font-family:Arial,sans-serif;max-width:560px">
                  <h2 style="color:#1e3a8a">Thank you, %s!</h2>
                  <p style="font-size:14px;color:#334155">
                    Thanks for your interest in Navgrow Engineering Service Pvt. Ltd. Your download
                    should have started automatically. If you need it again, you can download the
                    latest Company Profile &amp; Capabilities catalogue any time using the button below.
                  </p>
                  <p style="margin:20px 0">
                    <a href="%s/catalogue" style="background:#1e3a8a;color:#fff;padding:11px 20px;
                       border-radius:8px;text-decoration:none;font-weight:bold">Download the Catalogue</a>
                  </p>
                  <p style="font-size:13px;color:#64748b">
                    Have a project in mind? Reply to this email or reach us at
                    <a href="mailto:%s">%s</a> and our team will get back within 24 hours.
                  </p>
                  <p style="font-size:12px;color:#94a3b8;margin-top:24px">
                    Navgrow Engineering Service Pvt. Ltd.<br/>
                    Railway · Industrial · Civil · Sustainability
                  </p>
                </div>""".formatted(
                    org.springframework.web.util.HtmlUtils.htmlEscape(toName),
                    frontendUrl, contactEmail, contactEmail);
            helper.setText(html, true);
            mailSender.send(mime);
            log.info("Catalogue email sent to lead {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send catalogue email: {}", e.getMessage());
        }
    }

    /** Alerts the office inbox that a new catalogue lead was captured. */
    @Async
    public void sendCatalogueLeadNotification(com.navgrow.entity.CatalogueLead lead) {
        try {
            var mime   = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setTo(contactEmail);
            helper.setSubject("[New Catalogue Lead] " + lead.getName());
            String html = """
                <div style="font-family:Arial,sans-serif;max-width:560px">
                  <h2 style="color:#1e3a8a">New Catalogue Download Lead</h2>
                  <table style="border-collapse:collapse;width:100%%;font-size:14px">
                    %s
                  </table>
                  <p style="margin-top:16px">
                    <a href="%s/admin/catalogue-leads" style="background:#1e3a8a;color:#fff;padding:10px 18px;
                       border-radius:8px;text-decoration:none;font-weight:bold">Open in Admin Dashboard</a>
                  </p>
                </div>""".formatted(
                    row("Name", lead.getName()) + row("Mobile", lead.getMobile())
                    + row("Email", lead.getEmail()) + row("Company", lead.getCompany())
                    + row("City", lead.getCity()) + row("Requirement", lead.getRequirement()),
                    frontendUrl);
            helper.setText(html, true);
            mailSender.send(mime);
            log.info("Catalogue lead notification sent for {}", lead.getEmail());
        } catch (Exception e) {
            log.error("Failed to send catalogue lead notification: {}", e.getMessage());
        }
    }

    // ── Password reset ────────────────────────────────────────────────────────
    @Async
    public void sendPasswordResetEmail(String toEmail, String toName, String token) {
        try {
            var mime   = mailSender.createMimeMessage();
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
                "<a href='" + resetUrl + "' style='background:#2563eb;color:#fff;padding:12px 28px;" +
                "border-radius:8px;text-decoration:none;font-weight:bold'>Reset Password</a></p>" +
                "<p>If you didn't request this, please ignore this email.</p>" +
                "<p style='color:#666;font-size:12px'>Navgrow Engineering Service Pvt. Ltd.</p>" +
                "</body></html>", true);
            mailSender.send(mime);
            log.info("Password reset email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email: {}", e.getMessage());
        }
    }

    // ── Admin contact reply ───────────────────────────────────────────────────
    @Async
    public void sendReplyEmail(String toEmail, String toName, String subject, String replyText) {
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(toEmail);
            mail.setFrom(fromEmail);
            mail.setSubject("Re: " + subject + " — Navgrow Engineering");
            mail.setText(
                "Dear " + toName + ",\n\n" +
                replyText + "\n\n" +
                "---\n" +
                "Best regards,\n" +
                "Navgrow Engineering Team\n" +
                "info@navgrow.org | +91 89270 70972\n" +
                "navgrow.org"
            );
            mailSender.send(mail);
            log.info("Reply email sent to: {}", toEmail);
        } catch (Exception e) {
            log.warn("Failed to send reply email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ── HTML builders ─────────────────────────────────────────────────────────
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
               "<p>Your order will be dispatched within 1–2 business days.</p>" +
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

    // ── RFQ acknowledgement (buyer submitted) ─────────────────────────────────
    public void sendRfqAcknowledgement(String toEmail, String toName, String rfqNumber, int itemCount) {
        try {
            var mime = mailSender.createMimeMessage();
            var helper = new org.springframework.mail.javamail.MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("RFQ Received: " + rfqNumber + " — Navgrow Engineering");
            helper.setText(
                "<div style='font-family:Inter,Arial,sans-serif;max-width:560px;margin:auto'>" +
                "<h2 style='color:#1e3a8a'>Request for Quote Received</h2>" +
                "<p>Dear " + safe(toName) + ",</p>" +
                "<p>Thank you for your enquiry. We have received your Request for Quote " +
                "<strong>" + rfqNumber + "</strong> with <strong>" + itemCount + " item(s)</strong>.</p>" +
                "<p>Our procurement team will review your requirement and send you a formal, " +
                "GST-compliant quotation within <strong>1 business day</strong>.</p>" +
                "<p style='background:#eff6ff;padding:12px 16px;border-radius:8px;color:#1e40af'>" +
                "Reference: <strong>" + rfqNumber + "</strong></p>" +
                "<p>For urgent requirements, call us at <strong>+91 89270 70972</strong>.</p>" +
                "<p style='color:#64748b;font-size:13px;margin-top:24px'>Navgrow Engineering Service Pvt. Ltd.<br/>" +
                "DPIIT Recognised · MSME Registered · navgrow.org</p>" +
                "</div>", true);
            mailSender.send(mime);
        } catch (Exception e) {
            log.warn("Failed to send RFQ acknowledgement: {}", e.getMessage());
        }
    }

    // ── RFQ quote ready (admin priced it) ─────────────────────────────────────
    public void sendRfqQuoted(String toEmail, String toName, String rfqNumber,
                              String total, String validUntil) {
        try {
            var mime = mailSender.createMimeMessage();
            var helper = new org.springframework.mail.javamail.MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Your Quotation is Ready: " + rfqNumber + " — Navgrow Engineering");
            helper.setText(
                "<div style='font-family:Inter,Arial,sans-serif;max-width:560px;margin:auto'>" +
                "<h2 style='color:#1e3a8a'>Your Quotation is Ready</h2>" +
                "<p>Dear " + safe(toName) + ",</p>" +
                "<p>We have prepared a formal quotation for your request <strong>" + rfqNumber + "</strong>.</p>" +
                "<table style='width:100%;border-collapse:collapse;margin:16px 0'>" +
                "<tr><td style='padding:8px;color:#64748b'>Total (incl. GST)</td>" +
                "<td style='padding:8px;text-align:right;font-size:20px;font-weight:800;color:#1e3a8a'>₹" + total + "</td></tr>" +
                "<tr><td style='padding:8px;color:#64748b'>Valid Until</td>" +
                "<td style='padding:8px;text-align:right;font-weight:600'>" + validUntil + "</td></tr>" +
                "</table>" +
                "<p>Please log in to your account or visit the link in our message to review the full " +
                "line-item breakdown and accept the quote.</p>" +
                "<p><a href='https://navgrow.org/account' style='display:inline-block;background:#2563eb;" +
                "color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:700'>" +
                "View & Accept Quote</a></p>" +
                "<p style='color:#64748b;font-size:13px;margin-top:24px'>Navgrow Engineering Service Pvt. Ltd.<br/>" +
                "navgrow.org · +91 89270 70972</p>" +
                "</div>", true);
            mailSender.send(mime);
        } catch (Exception e) {
            log.warn("Failed to send RFQ quote email: {}", e.getMessage());
        }
    }

    /**
     * Notify the Navgrow team when a buyer accepts or rejects a quote, so the
     * team knows to follow up (accept) or can capture the lost-deal reason (reject).
     * Best-effort: never throws.
     */
    @Async
    public void sendRfqDecisionToTeam(String rfqNumber, String buyerName, String buyerEmail,
                                      String buyerPhone, boolean accepted, String total, String reason) {
        try {
            var mime = mailSender.createMimeMessage();
            var helper = new org.springframework.mail.javamail.MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(contactEmail);
            if (buyerEmail != null && !buyerEmail.isBlank()) helper.setReplyTo(buyerEmail);
            String verb = accepted ? "ACCEPTED" : "REJECTED";
            String accent = accepted ? "#059669" : "#dc2626";
            helper.setSubject("Quote " + verb + ": " + rfqNumber + (accepted ? " — action needed" : ""));
            StringBuilder html = new StringBuilder()
                .append("<div style='font-family:Inter,Arial,sans-serif;max-width:560px;margin:auto'>")
                .append("<h2 style='color:").append(accent).append("'>Quote ").append(verb).append("</h2>")
                .append("<p>RFQ <strong>").append(safe(rfqNumber)).append("</strong> has been ")
                .append(accepted ? "accepted by the buyer." : "rejected by the buyer.").append("</p>")
                .append("<table style='width:100%;border-collapse:collapse;margin:12px 0'>")
                .append("<tr><td style='padding:6px;color:#64748b'>Buyer</td><td style='padding:6px;text-align:right;font-weight:600'>")
                .append(safe(buyerName)).append("</td></tr>")
                .append("<tr><td style='padding:6px;color:#64748b'>Email</td><td style='padding:6px;text-align:right'>")
                .append(safe(buyerEmail)).append("</td></tr>")
                .append("<tr><td style='padding:6px;color:#64748b'>Phone</td><td style='padding:6px;text-align:right'>")
                .append(safe(buyerPhone)).append("</td></tr>")
                .append("<tr><td style='padding:6px;color:#64748b'>Quote Total</td><td style='padding:6px;text-align:right;font-weight:700'>₹")
                .append(safe(total)).append("</td></tr>");
            if (!accepted && reason != null && !reason.isBlank()) {
                html.append("<tr><td style='padding:6px;color:#64748b'>Reason</td><td style='padding:6px;text-align:right'>")
                    .append(safe(reason)).append("</td></tr>");
            }
            html.append("</table>");
            if (accepted) {
                html.append("<p style='font-weight:600'>Please reach out to the buyer to finalise the order.</p>");
            }
            html.append("<p style='color:#64748b;font-size:13px;margin-top:20px'>Navgrow Engineering — admin notification</p></div>");
            helper.setText(html.toString(), true);
            mailSender.send(mime);
        } catch (Exception e) {
            log.warn("Failed to send RFQ decision notification: {}", e.getMessage());
        }
    }

    private String safe(String s) { return s == null ? "" : s.replaceAll("[<>]", ""); }

}