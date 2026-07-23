/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 */
package com.navgrow.service;

import com.navgrow.entity.Order;
import com.navgrow.entity.OrderItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

/**
 * InvoiceService — generates a GST-compliant tax invoice as self-contained,
 * print-ready HTML. Browsers convert this to a perfect PDF via the print
 * dialog ("Save as PDF"), so no heavyweight PDF library is required.
 *
 * Handles the CGST/SGST vs IGST split based on place of supply:
 *  - Intra-state (seller state == buyer state)  → CGST + SGST (half each)
 *  - Inter-state (different states)             → IGST (full rate)
 */
@Service
@Slf4j
public class InvoiceService {

    // ── Seller (Navgrow) constants ────────────────────────────────────────────
    private static final String SELLER_NAME   = "Navgrow Engineering Service Pvt. Ltd.";
    private static final String SELLER_CIN     = "U74999WB2022PTC256012";
    private static final String SELLER_ADDR    = "Ward No-47, Old Matigara Road, Pati Colony, Siliguri, West Bengal – 734001";
    private static final String SELLER_STATE   = "West Bengal";
    private static final String SELLER_GSTIN   = "19AABCN0000A1Z5"; // configure your real GSTIN
    private static final String SELLER_EMAIL   = "info@navgrow.org";
    private static final String SELLER_PHONE   = "+91 89270 70972";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public String generateInvoiceHtml(Order o) {
        boolean intraState = SELLER_STATE.equalsIgnoreCase(o.getState());

        StringBuilder rows = new StringBuilder();
        int idx = 1;
        BigDecimal totalTaxable = BigDecimal.ZERO;
        BigDecimal totalTax     = BigDecimal.ZERO;

        for (OrderItem it : o.getItems()) {
            BigDecimal qty   = BigDecimal.valueOf(it.getQuantity());
            BigDecimal taxable = it.getUnitPrice().multiply(qty);
            BigDecimal gstRate = it.getGstRate() != null ? it.getGstRate() : BigDecimal.valueOf(18);
            BigDecimal taxAmt  = taxable.multiply(gstRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            totalTaxable = totalTaxable.add(taxable);
            totalTax     = totalTax.add(taxAmt);

            String taxCols;
            if (intraState) {
                BigDecimal half = gstRate.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                BigDecimal halfAmt = taxAmt.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                taxCols = td(pct(half) + "<br/>" + money(halfAmt)) + td(pct(half) + "<br/>" + money(halfAmt));
            } else {
                taxCols = td(pct(gstRate) + "<br/>" + money(taxAmt));
            }

            rows.append("<tr>")
                .append(td(String.valueOf(idx++)))
                .append(tdLeft(esc(it.getProductName())))
                // HSN/SAC is a statutory column on a GST tax invoice.
                .append(td(it.getHsnCode() != null && !it.getHsnCode().isBlank()
                           ? esc(it.getHsnCode()) : "&mdash;"))
                .append(td(it.getQuantity().toString()))
                .append(td(money(it.getUnitPrice())))
                .append(td(money(taxable)))
                .append(taxCols)
                .append(td("<strong>" + money(taxable.add(taxAmt)) + "</strong>"))
                .append("</tr>");
        }

        BigDecimal shipping = o.getShippingCharge() != null ? o.getShippingCharge() : BigDecimal.ZERO;
        BigDecimal discount = o.getDiscountAmount() != null ? o.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal grand    = o.getGrandTotal();

        String taxHeaders = intraState
            ? "<th>CGST</th><th>SGST</th>"
            : "<th>IGST</th>";

        String invoiceNo = o.getInvoiceNumber() != null ? o.getInvoiceNumber()
                          : ("INV-" + o.getOrderNumber());

        return """
            <!DOCTYPE html><html><head><meta charset="utf-8"/>
            <title>Tax Invoice %s</title>
            <style>
              * { box-sizing: border-box; }
              body { font-family: 'Segoe UI', Arial, sans-serif; color:#1f2937; margin:0; padding:32px; font-size:13px; }
              .inv { max-width:820px; margin:auto; border:1px solid #e5e7eb; }
              .hd { background:linear-gradient(135deg,#1e3a8a,#2563eb); color:#fff; padding:24px 28px; display:flex; justify-content:space-between; align-items:flex-start; }
              .hd h1 { margin:0; font-size:22px; letter-spacing:.5px; }
              .hd .tag { font-size:11px; opacity:.85; margin-top:4px; }
              .inv-title { text-align:right; }
              .inv-title h2 { margin:0; font-size:20px; font-weight:800; }
              .meta { display:flex; justify-content:space-between; padding:20px 28px; border-bottom:1px solid #eee; gap:24px; }
              .meta .box { font-size:12.5px; line-height:1.6; }
              .meta .box b { color:#1e3a8a; display:block; margin-bottom:4px; font-size:11px; text-transform:uppercase; letter-spacing:.5px; }
              table { width:100%%; border-collapse:collapse; }
              thead th { background:#f1f5f9; color:#334155; font-size:11px; text-transform:uppercase; letter-spacing:.4px; padding:10px 8px; border-bottom:2px solid #cbd5e1; }
              tbody td { padding:9px 8px; border-bottom:1px solid #eee; text-align:center; }
              tbody td.l { text-align:left; }
              .totals { padding:18px 28px; display:flex; justify-content:flex-end; }
              .totals table { width:320px; }
              .totals td { padding:6px 4px; font-size:13px; }
              .totals .grand td { font-size:17px; font-weight:800; color:#1e3a8a; border-top:2px solid #1e3a8a; padding-top:10px; }
              .ft { padding:20px 28px; border-top:1px solid #eee; font-size:11px; color:#6b7280; }
              .badge { display:inline-block; background:#dcfce7; color:#15803d; padding:3px 10px; border-radius:99px; font-size:11px; font-weight:700; }
              .pay { color:%s; font-weight:700; }
              @media print { body { padding:0; } .inv { border:none; } .noprint { display:none; } }
            </style></head><body>
            <div class="inv">
              <div class="hd">
                <div>
                  <h1>%s</h1>
                  <div class="tag">CIN: %s · GSTIN: %s</div>
                  <div class="tag">%s</div>
                  <div class="tag">%s · %s</div>
                </div>
                <div class="inv-title">
                  <h2>TAX INVOICE</h2>
                  <div class="tag">%s</div>
                </div>
              </div>

              <div class="meta">
                <div class="box">
                  <b>Bill To</b>
                  %s<br/>
                  %s%s<br/>
                  %s, %s – %s<br/>
                  %s
                  %s
                </div>
                <div class="box" style="text-align:right">
                  <b>Invoice Details</b>
                  Invoice No: <strong>%s</strong><br/>
                  Order No: %s<br/>
                  Date: %s<br/>
                  Payment: <span class="pay">%s</span><br/>
                  Place of Supply: %s
                </div>
              </div>

              <table>
                <thead><tr>
                  <th>#</th><th style="text-align:left">Description</th><th>HSN/SAC</th><th>Qty</th>
                  <th>Rate</th><th>Taxable</th>%s<th>Amount</th>
                </tr></thead>
                <tbody>%s</tbody>
              </table>

              <div class="totals">
                <table>
                  <tr><td>Taxable Value</td><td style="text-align:right">%s</td></tr>
                  <tr><td>Total GST</td><td style="text-align:right">%s</td></tr>
                  %s
                  %s
                  <tr class="grand"><td>Grand Total</td><td style="text-align:right">%s</td></tr>
                </table>
              </div>

              <div class="ft">
                <span class="badge">✓ GST Compliant Tax Invoice</span>
                &nbsp; This is a computer-generated invoice and does not require a physical signature.<br/><br/>
                <strong>%s</strong> · %s · %s<br/>
                Thank you for your business. For queries, contact %s.
              </div>
            </div>
            <div class="noprint" style="text-align:center;margin:24px">
              <button onclick="window.print()" style="background:#2563eb;color:#fff;border:none;padding:12px 28px;border-radius:8px;font-weight:700;font-size:14px;cursor:pointer">
                Download / Print Invoice
              </button>
            </div>
            </body></html>
            """.formatted(
                esc(invoiceNo),
                "DELIVERED".equals(String.valueOf(o.getStatus())) ? "#15803d" : "#b45309",
                SELLER_NAME, SELLER_CIN, SELLER_GSTIN, SELLER_ADDR, SELLER_EMAIL, SELLER_PHONE,
                esc(invoiceNo),
                esc(o.getCustomerName()),
                esc(o.getAddressLine1()),
                o.getAddressLine2() != null ? "<br/>" + esc(o.getAddressLine2()) : "",
                esc(o.getCity()), esc(o.getState()), esc(o.getPincode()),
                esc(o.getCustomerPhone()),
                o.getGstin() != null ? "<br/>GSTIN: <strong>" + esc(o.getGstin()) + "</strong>" : "",
                esc(invoiceNo), esc(o.getOrderNumber()),
                o.getCreatedAt() != null ? o.getCreatedAt().format(DATE) : "",
                String.valueOf(o.getPaymentStatus()), esc(o.getState()),
                taxHeaders, rows.toString(),
                money(totalTaxable), money(totalTax),
                discount.signum() > 0 ? "<tr><td>Discount</td><td style='text-align:right'>− " + money(discount) + "</td></tr>" : "",
                shipping.signum() > 0 ? "<tr><td>Shipping</td><td style='text-align:right'>" + money(shipping) + "</td></tr>" : "",
                money(grand),
                SELLER_NAME, SELLER_ADDR, "GSTIN: " + SELLER_GSTIN, SELLER_EMAIL
            );
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private String money(BigDecimal v) { return "₹" + (v == null ? "0.00" : v.setScale(2, RoundingMode.HALF_UP).toPlainString()); }
    private String pct(BigDecimal v)   { return v.stripTrailingZeros().toPlainString() + "%"; }
    private String td(String c)        { return "<td>" + c + "</td>"; }
    private String tdLeft(String c)    { return "<td class='l'>" + c + "</td>"; }
    private String esc(String s)       { return s == null ? "" : s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); }
}
