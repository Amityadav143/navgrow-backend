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
    // ObjectProvider prevents a circular/early-init dependency at startup.
    private final org.springframework.beans.factory.ObjectProvider<NotificationSettingsService> settingsProvider;

    @Value("${app.frontend-url:http://navgrow.org}")
    private String frontendUrl;

    /** Recruitment inbox fallback. Admin panel value (if set) takes precedence. */
    @Value("${app.careers-email:careers@navgrow.org}")
    private String careersEmailDefault;

    @Value("${app.contact-email:info@navgrow.org}")
    private String contactEmailDefault;

    @Value("${app.name:Navgrow Engineering}")
    private String appName;

    /** The "From" address fallback — same as SMTP username unless admin overrides. */
    @Value("${spring.mail.username:info@navgrow.org}")
    private String fromEmailDefault;

    // ── Effective, admin-configurable recipients (DB row wins over env) ────────
    private NotificationSettingsService settings() { return settingsProvider.getIfAvailable(); }
    private boolean emailEnabled() { var s = settings(); return s == null || s.emailEnabled(); }
    private String fromEmail()    { var s = settings(); return s == null ? fromEmailDefault    : s.fromEmail(); }
    private String contactEmail() { var s = settings(); return s == null ? contactEmailDefault : s.contactEmail(); }
    private String careersEmail() { var s = settings(); return s == null ? careersEmailDefault : s.careersEmail(); }
    private String ordersEmail()  { var s = settings(); return s == null ? contactEmailDefault : s.ordersEmail(); }
    private String quotesEmail()  { var s = settings(); return s == null ? contactEmailDefault : s.quotesEmail(); }
    private String supportEmail() { var s = settings(); return s == null ? contactEmailDefault : s.supportEmail(); }

    // ── Contact notification ──────────────────────────────────────────────────
    @Async
    public void sendContactNotification(String fromName, String senderEmail,
                                        String subject, String message) {
        if (!emailEnabled()) { log.info("[EMAIL] Disabled by admin settings — contact notice not sent."); return; }
        try {
            var mime   = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromEmail());
            helper.setTo(contactEmail());
            if (senderEmail != null && !senderEmail.isBlank()) helper.setReplyTo(senderEmail);
            helper.setSubject("[Website Enquiry] " + subject);
            helper.setText(buildContactHtml(fromName, senderEmail, subject, message), true);
            mailSender.send(mime);
            log.info("Contact notification sent to {} for: {}", contactEmail(), senderEmail);
        } catch (Exception e) {
            log.error("Failed to send contact notification: {}", e.getMessage());
        }
    }

    // ── Order confirmation ────────────────────────────────────────────────────
    @Async
    public void sendOrderConfirmation(String toEmail, String toName,
                                      String orderNumber, String total) {
        if (!emailEnabled()) { log.info("[EMAIL] Disabled — order confirmation not sent."); return; }
        try {
            var mime   = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromEmail());
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
        if (!emailEnabled()) { log.info("[EMAIL] Disabled — quote acknowledgement not sent."); return; }
        try {
            var mime   = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromEmail());
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
        if (!emailEnabled()) { log.info("[EMAIL] Disabled — quote notice not sent."); return; }
        try {
            var mime   = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromEmail());
            helper.setTo(quotesEmail());
            if (q.getEmail() != null && !q.getEmail().isBlank()) helper.setReplyTo(q.getEmail());
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

    /** Careers inbox gets the application; the candidate gets an acknowledgement. */
    @Async
    public void sendJobApplicationReceived(com.navgrow.entity.JobApplication app) {
        if (!emailEnabled()) { log.info("[EMAIL] Disabled — job application receipt not sent."); return; }
        try {
            var mime   = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromEmail());
            helper.setTo(careersEmail());
            helper.setReplyTo(app.getEmail());
            helper.setSubject("[Application] " + app.getJobTitle() + " \u2014 " + app.getName());
            String html = "<div style=\"font-family:Arial,sans-serif;max-width:560px\">"
                + "<h2 style=\"color:#1e3a8a\">New application: "
                + org.springframework.web.util.HtmlUtils.htmlEscape(app.getJobTitle()) + "</h2>"
                + "<table style=\"border-collapse:collapse;width:100%;font-size:14px\">"
                + row("Name", app.getName()) + row("Email", app.getEmail())
                + row("Phone", app.getPhone()) + row("Experience", app.getExperience())
                + row("CV", app.getResumeUrl() == null ? "Not attached" : app.getResumeUrl())
                + row("Cover note", app.getCoverNote())
                + "</table>"
                + "<p style=\"margin-top:16px;font-size:13px;color:#64748b\">"
                + "Reply directly to this email to reach the candidate.</p></div>";
            helper.setText(html, true);
            mailSender.send(mime);
        } catch (Exception e) {
            log.error("Careers notification failed: {}", e.getMessage());
        }

        try {
            var mime2   = mailSender.createMimeMessage();
            var helper2 = new MimeMessageHelper(mime2, true, "UTF-8");
            helper2.setFrom(fromEmail());
            helper2.setTo(app.getEmail());
            helper2.setReplyTo(careersEmail());
            helper2.setSubject("We\u2019ve received your application \u2014 " + app.getJobTitle());
            String html2 = "<div style=\"font-family:Arial,sans-serif;max-width:560px\">"
                + "<h2 style=\"color:#1e3a8a\">Thank you, "
                + org.springframework.web.util.HtmlUtils.htmlEscape(app.getName()) + "</h2>"
                + "<p style=\"font-size:14px;color:#334155\">We have received your application for <strong>"
                + org.springframework.web.util.HtmlUtils.htmlEscape(app.getJobTitle())
                + "</strong> at Navgrow Engineering Service Pvt. Ltd. Our team reviews every "
                + "application and will get back to you within 5 working days if your profile matches.</p>"
                + "<p style=\"font-size:13px;color:#64748b\">Questions? Just reply to this email and it "
                + "will reach our recruitment team.</p>"
                + "<p style=\"font-size:12px;color:#94a3b8;margin-top:24px\">Navgrow Engineering Service Pvt. Ltd.<br/>"
                + "Railway \u00b7 Industrial \u00b7 Civil \u00b7 Sustainability</p></div>";
            helper2.setText(html2, true);
            mailSender.send(mime2);
        } catch (Exception e) {
            log.error("Applicant acknowledgement failed: {}", e.getMessage());
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
        if (!emailEnabled()) { log.info("[EMAIL] Disabled — catalogue not sent."); return; }
        try {
            var mime   = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromEmail());
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
                    frontendUrl, contactEmail(), contactEmail());
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
        if (!emailEnabled()) { log.info("[EMAIL] Disabled — catalogue lead notice not sent."); return; }
        try {
            var mime   = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromEmail());
            helper.setTo(supportEmail());
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
        if (!emailEnabled()) { log.info("[EMAIL] Disabled — password reset email not sent."); return; }
        try {
            var mime   = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromEmail());
            helper.setTo(toEmail);
            helper.setSubject("Reset Your Password — Navgrow Engineering");
            String base = frontendUrl == null ? "" : frontendUrl.replaceAll("/+$", "");
            String resetUrl = base + "/reset-password?token=" + token;
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
        if (!emailEnabled()) { log.info("[EMAIL] Disabled — reply email not sent."); return; }
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(toEmail);
            mail.setFrom(fromEmail());
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
    @Async
    public void sendRfqAcknowledgement(String toEmail, String toName, String rfqNumber, int itemCount) {
        if (!emailEnabled()) { log.info("[EMAIL] Disabled — RFQ acknowledgement not sent."); return; }
        try {
            var mime = mailSender.createMimeMessage();
            var helper = new org.springframework.mail.javamail.MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromEmail());
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
    @Async
    public void sendRfqQuoted(String toEmail, String toName, String rfqNumber,
                              String total, String validUntil) {
        if (!emailEnabled()) { log.info("[EMAIL] Disabled — RFQ quote not sent."); return; }
        try {
            var mime = mailSender.createMimeMessage();
            var helper = new org.springframework.mail.javamail.MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromEmail());
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
        if (!emailEnabled()) { log.info("[EMAIL] Disabled — RFQ decision notice not sent."); return; }
        try {
            var mime = mailSender.createMimeMessage();
            var helper = new org.springframework.mail.javamail.MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromEmail());
            helper.setTo(quotesEmail());
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

    // ── Order status update (shipped / delivered / cancelled / processing) ────
    /**
     * Tells the customer their order has moved — the transactional email people
     * most expect from a shop. SHIPPED carries the tracking number and courier so
     * the buyer can follow the parcel; CANCELLED and DELIVERED get their own copy.
     * Best-effort: a mail failure never blocks the admin's status change.
     */
    @Async
    public void sendOrderStatusUpdate(com.navgrow.entity.Order order) {
        if (!emailEnabled()) { log.info("[EMAIL] Disabled — order status update not sent."); return; }
        try {
            String status = order.getStatus() == null ? "" : order.getStatus().name();
            String heading, intro, accent = "#2563eb";
            switch (status) {
                case "SHIPPED"   -> { heading = "Your order is on its way";  intro = "Good news — your order has been dispatched."; accent = "#2563eb"; }
                case "DELIVERED" -> { heading = "Your order has been delivered"; intro = "Your order has been delivered. We hope everything arrived in good order."; accent = "#059669"; }
                case "CANCELLED" -> { heading = "Your order has been cancelled"; intro = "Your order has been cancelled. Any amount paid online will be refunded to the original payment method."; accent = "#dc2626"; }
                case "PROCESSING"-> { heading = "Your order is being prepared"; intro = "Your order is now being processed and will be dispatched shortly."; accent = "#2563eb"; }
                case "REFUNDED"  -> { heading = "Your order has been refunded"; intro = "A refund has been issued for your order to the original payment method."; accent = "#059669"; }
                default          -> { heading = "Order update"; intro = "There is an update on your order."; }
            }
            var mime   = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromEmail());
            helper.setTo(order.getCustomerEmail());
            helper.setSubject(heading + " – " + order.getOrderNumber() + " | Navgrow Engineering");
            StringBuilder html = new StringBuilder()
                .append("<div style='font-family:Inter,Arial,sans-serif;max-width:560px;margin:auto'>")
                .append("<h2 style='color:").append(accent).append("'>").append(heading).append("</h2>")
                .append("<p>Dear ").append(safe(order.getCustomerName())).append(",</p>")
                .append("<p>").append(intro).append("</p>")
                .append("<table style='width:100%;border-collapse:collapse;margin:12px 0'>")
                .append("<tr><td style='padding:6px;color:#64748b'>Order</td><td style='padding:6px;text-align:right;font-weight:600'>")
                .append(safe(order.getOrderNumber())).append("</td></tr>")
                .append("<tr><td style='padding:6px;color:#64748b'>Status</td><td style='padding:6px;text-align:right;font-weight:600'>")
                .append(safe(status)).append("</td></tr>");
            if ("SHIPPED".equals(status)) {
                if (order.getCourierName() != null && !order.getCourierName().isBlank())
                    html.append("<tr><td style='padding:6px;color:#64748b'>Courier</td><td style='padding:6px;text-align:right'>")
                        .append(safe(order.getCourierName())).append("</td></tr>");
                if (order.getTrackingNumber() != null && !order.getTrackingNumber().isBlank())
                    html.append("<tr><td style='padding:6px;color:#64748b'>Tracking No.</td><td style='padding:6px;text-align:right;font-weight:600'>")
                        .append(safe(order.getTrackingNumber())).append("</td></tr>");
            }
            html.append("</table>")
                .append("<p><a href='").append(frontendUrl).append("/track/").append(safe(order.getOrderNumber()))
                .append("' style='color:#2563eb'>Track your order</a></p>")
                .append("<p style='color:#64748b;font-size:13px;margin-top:20px'>Questions? ")
                .append("<a href='mailto:").append(contactEmail()).append("'>").append(contactEmail()).append("</a> | +91 89270 70972</p>")
                .append("<p>Thank you for choosing Navgrow Engineering.</p></div>");
            helper.setText(html.toString(), true);
            mailSender.send(mime);
            log.info("Order status ({}) email sent: {}", status, order.getOrderNumber());
        } catch (Exception e) {
            log.warn("Failed to send order status update for {}: {}",
                order.getOrderNumber(), e.getMessage());
        }
    }

    // ── New order → office/admin notification ─────────────────────────────────
    /**
     * Alerts the office inbox the moment an order is confirmed (COD at placement,
     * online at successful payment), so fulfilment doesn't depend on someone
     * happening to open the admin dashboard. Reply-To is the customer.
     */
    @Async
    public void sendNewOrderAdminNotification(com.navgrow.entity.Order order) {
        if (!emailEnabled()) { log.info("[EMAIL] Disabled — new-order notice not sent."); return; }
        try {
            var mime   = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromEmail());
            helper.setTo(ordersEmail());
            if (order.getCustomerEmail() != null && !order.getCustomerEmail().isBlank())
                helper.setReplyTo(order.getCustomerEmail());
            helper.setSubject("New order " + order.getOrderNumber() + " — ₹"
                + (order.getGrandTotal() == null ? "" : order.getGrandTotal().toPlainString())
                + " (" + safe(order.getPaymentMethod()) + ")");
            StringBuilder html = new StringBuilder()
                .append("<div style='font-family:Inter,Arial,sans-serif;max-width:600px;margin:auto'>")
                .append("<h2 style='color:#2563eb'>New order received</h2>")
                .append("<table style='width:100%;border-collapse:collapse;margin:12px 0'>")
                .append(row("Order", order.getOrderNumber()))
                .append(row("Customer", order.getCustomerName()))
                .append(row("Email", order.getCustomerEmail()))
                .append(row("Phone", order.getCustomerPhone()))
                .append(row("Payment", order.getPaymentMethod()))
                .append(row("Delivery", order.getDeliverySpeed()))
                .append(row("Total", order.getGrandTotal() == null ? "" : "₹" + order.getGrandTotal().toPlainString()));
            if (order.getCouponCode() != null && !order.getCouponCode().isBlank())
                html.append(row("Coupon", order.getCouponCode()));
            html.append("</table>");
            if (order.getItems() != null && !order.getItems().isEmpty()) {
                html.append("<h3 style='margin:16px 0 6px'>Items</h3>")
                    .append("<table style='width:100%;border-collapse:collapse'>");
                for (var it : order.getItems()) {
                    html.append("<tr><td style='padding:5px;border-bottom:1px solid #eee'>")
                        .append(safe(it.getProductName())).append("</td>")
                        .append("<td style='padding:5px;border-bottom:1px solid #eee;text-align:right'>× ")
                        .append(it.getQuantity()).append("</td></tr>");
                }
                html.append("</table>");
            }
            html.append("<p style='margin-top:16px'><a href='").append(frontendUrl)
                .append("/admin/orders' style='color:#2563eb'>Open in admin dashboard</a></p>")
                .append("<p style='color:#64748b;font-size:13px'>Navgrow Engineering — admin notification</p></div>");
            helper.setText(html.toString(), true);
            mailSender.send(mime);
            log.info("New-order admin notification sent: {}", order.getOrderNumber());
        } catch (Exception e) {
            log.warn("Failed to send new-order admin notification for {}: {}",
                order.getOrderNumber(), e.getMessage());
        }
    }

    // ── Welcome email (new account) ───────────────────────────────────────────
    /** A short welcome after registration. Skipped for phone-only synthetic emails by the caller. */
    @Async
    public void sendWelcomeEmail(String toEmail, String toName) {
        if (!emailEnabled()) { log.info("[EMAIL] Disabled — welcome email not sent."); return; }
        try {
            var mime   = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromEmail());
            helper.setTo(toEmail);
            helper.setSubject("Welcome to " + appName);
            String html = "<div style='font-family:Inter,Arial,sans-serif;max-width:560px;margin:auto'>"
                + "<h2 style='color:#2563eb'>Welcome to Navgrow Engineering</h2>"
                + "<p>Dear " + safe(toName) + ",</p>"
                + "<p>Thank you for creating an account. You can now track orders, request quotes (RFQ) "
                + "for bulk requirements, and download GST invoices from your dashboard.</p>"
                + "<p><a href='" + frontendUrl + "/shop' style='background:#2563eb;color:#fff;padding:10px 18px;"
                + "border-radius:8px;text-decoration:none;display:inline-block'>Browse the shop</a></p>"
                + "<p style='color:#64748b;font-size:13px;margin-top:20px'>Need help? "
                + "<a href='mailto:" + contactEmail() + "'>" + contactEmail() + "</a> | +91 89270 70972</p></div>";
            helper.setText(html, true);
            mailSender.send(mime);
            log.info("Welcome email sent to {}", toEmail);
        } catch (Exception e) {
            log.warn("Failed to send welcome email to {}: {}", toEmail, e.getMessage());
        }
    }

    /**
     * Sends a plain test email to an arbitrary address so an admin can confirm
     * SMTP is actually working from the settings screen. Throws on failure so the
     * controller can report the real reason (unlike the fire-and-forget notices).
     */
    public void sendTestEmail(String toEmail) throws Exception {
        var mime   = mailSender.createMimeMessage();
        var helper = new MimeMessageHelper(mime, true, "UTF-8");
        helper.setFrom(fromEmail());
        helper.setTo(toEmail);
        helper.setSubject("Navgrow test email — configuration OK");
        helper.setText(
            "<div style='font-family:Arial,sans-serif'>"
            + "<h2 style='color:#2563eb'>It works ✅</h2>"
            + "<p>This is a test email from your Navgrow admin panel. If you're reading it, "
            + "outgoing email is configured correctly.</p>"
            + "<p style='color:#64748b;font-size:13px'>Sent from " + fromEmail() + "</p></div>", true);
        mailSender.send(mime);
        log.info("Test email sent to {}", toEmail);
    }

    private String safe(String s) { return s == null ? "" : s.replaceAll("[<>]", ""); }

}