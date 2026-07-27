/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 */
package com.navgrow.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Admin-editable notification configuration — a single row.
 *
 * WHY A DB ROW (not just application.yml): the office wanted to change *where*
 * each kind of message goes (careers → one inbox, orders → another, quotes → a
 * third) without a redeploy. Credentials still default to environment variables
 * so nothing secret has to live in the database; but if an admin fills them in
 * here, these take precedence. This keeps the common "change the careers inbox"
 * task a two-click job while leaving a secure env-only setup possible.
 */
@Entity
@Table(name = "notification_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationSettings {

    // Single-row table; the id is always 1.
    @Id
    private Integer id;

    // ── Per-event recipient inboxes (where NOTICES about an event are sent) ────
    // Customer-facing confirmations always go to the customer; these are the
    // INTERNAL inboxes that should hear about each event.
    @Column(name = "careers_email")   private String careersEmail;   // job applications
    @Column(name = "orders_email")    private String ordersEmail;    // new orders
    @Column(name = "quotes_email")    private String quotesEmail;    // quote/RFQ requests
    @Column(name = "contact_email")   private String contactEmail;   // contact form
    @Column(name = "support_email")   private String supportEmail;   // catch-all / support
    @Column(name = "from_email")      private String fromEmail;      // the "From:" address

    // ── Master switches ───────────────────────────────────────────────────────
    @Builder.Default @Column(name = "email_enabled") private boolean emailEnabled = true;
    @Builder.Default @Column(name = "sms_enabled")   private boolean smsEnabled   = true;

    // ── SMS provider (optional DB override of env config) ──────────────────────
    // provider: log | msg91 | twilio  (blank/null = use application.yml/env)
    @Column(name = "sms_provider")        private String smsProvider;
    @Column(name = "sms_sender_id")       private String smsSenderId;
    @Column(name = "msg91_auth_key")      private String msg91AuthKey;
    @Column(name = "msg91_template_id")   private String msg91TemplateId;
    @Column(name = "twilio_account_sid")  private String twilioAccountSid;
    @Column(name = "twilio_auth_token")   private String twilioAuthToken;
    @Column(name = "twilio_from_number")  private String twilioFromNumber;

    @Builder.Default @Column(name = "updated_at") private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist @PreUpdate
    public void touch() { this.updatedAt = LocalDateTime.now(); }
}
