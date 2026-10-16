/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.entity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name = "order_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @JsonIgnore
    private Product product;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /** HSN/SAC snapshot taken at the time of sale, so a reissued invoice always
     *  shows the classification that actually applied to this transaction. */
    @Column(name = "hsn_code", length = 12)
    private String hsnCode;

    @Column(name = "gst_rate", precision = 5, scale = 2)
    private BigDecimal gstRate;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    /**
     * Whether this line's units were actually taken from inventory. False only
     * when the payment cleared but the stock had already gone (a STOCK
     * SHORTFALL) — cancelling such an order must not put back units that were
     * never taken, or the shop would show stock it doesn't have.
     */
    @Column(name = "stock_committed")
    @Builder.Default
    private Boolean stockCommitted = Boolean.TRUE;

    @PrePersist
    void defaultStockCommitted() {
        if (stockCommitted == null) stockCommitted = Boolean.TRUE;
    }

    /**
     * The product's id for the client (the product itself is not serialised) —
     * "Reorder" and the account page need it. Reading the id of a lazy proxy
     * does not hit the database.
     */
    @Transient
    @com.fasterxml.jackson.annotation.JsonProperty("productId")
    public UUID getProductId() {
        return product == null ? null : product.getId();
    }

    /** Product page slug for links from "My orders" (null if the product is gone). */
    @Transient
    @com.fasterxml.jackson.annotation.JsonProperty("productSlug")
    public String getProductSlug() {
        try { return product == null ? null : product.getSlug(); }
        catch (RuntimeException e) { return null; }   // proxy no longer loadable — not worth failing the response
    }
}
