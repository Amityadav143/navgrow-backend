package com.navgrow.entity;
import com.navgrow.enums.OrderStatus;
import com.navgrow.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity @Table(name = "orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_number", unique = true, nullable = false)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "customer_name",  nullable = false) private String customerName;
    @Column(name = "customer_email", nullable = false) private String customerEmail;
    @Column(name = "customer_phone", nullable = false) private String customerPhone;
    @Column(name = "company_name")   private String companyName;

    @Column(name = "address_line1", nullable = false, columnDefinition = "TEXT") private String addressLine1;
    @Column(name = "address_line2", columnDefinition = "TEXT") private String addressLine2;
    @Column(nullable = false)  private String city;
    @Column(nullable = false)  private String state;
    @Column(nullable = false)  private String pincode;

    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal subtotal;
    @Column(name = "gst_amount",      precision = 12, scale = 2) private BigDecimal gstAmount    = BigDecimal.ZERO;
    @Column(name = "shipping_charge", precision = 12, scale = 2) private BigDecimal shippingCharge = BigDecimal.ZERO;
    @Column(name = "discount_amount", precision = 12, scale = 2) private BigDecimal discountAmount = BigDecimal.ZERO;
    @Column(name = "grand_total",     precision = 12, scale = 2) private BigDecimal grandTotal;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING) @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "razorpay_order_id")   private String razorpayOrderId;
    @Column(name = "razorpay_payment_id") private String razorpayPaymentId;
    @Column(name = "razorpay_signature")  private String razorpaySignature;

    @Column(name = "tracking_number") private String trackingNumber;
    @Column(name = "courier_name")    private String courierName;

    @Column(columnDefinition = "TEXT") private String notes;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at") private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
