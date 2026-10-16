/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.repository;
import com.navgrow.entity.Order;
import com.navgrow.enums.OrderStatus;
import com.navgrow.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByOrderNumber(String orderNumber);
    Optional<Order> findByRazorpayOrderId(String razorpayOrderId);
    Page<Order> findByCustomerEmailOrderByCreatedAtDesc(String email, Pageable pageable);

    /** "My orders": placed while signed in to this account OR with its email. */
    Page<Order> findByUser_IdOrCustomerEmailIgnoreCaseOrderByCreatedAtDesc(UUID userId, String email, Pageable pageable);
    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    /** Admin search: order number, customer name, email or phone (status bound as a parameter). */
    @Query("SELECT o FROM Order o WHERE LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :q, '%')) "
         + "OR LOWER(o.customerEmail) LIKE LOWER(CONCAT('%', :q, '%')) "
         + "OR LOWER(o.customerName) LIKE LOWER(CONCAT('%', :q, '%')) "
         + "OR o.customerPhone LIKE CONCAT('%', :q, '%') ORDER BY o.createdAt DESC")
    Page<Order> searchAll(@Param("q") String q, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.status = :status AND (LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :q, '%')) "
         + "OR LOWER(o.customerEmail) LIKE LOWER(CONCAT('%', :q, '%')) "
         + "OR LOWER(o.customerName) LIKE LOWER(CONCAT('%', :q, '%')) "
         + "OR o.customerPhone LIKE CONCAT('%', :q, '%')) ORDER BY o.createdAt DESC")
    Page<Order> searchByStatus(@Param("q") String q, @Param("status") OrderStatus status, Pageable pageable);
    long countByStatus(OrderStatus status);
    long countByPaymentStatus(PaymentStatus paymentStatus);

    /**
     * Marks the order PAID only if it isn't already, in one statement. Two
     * confirmations of the same payment (double submit / retry) can both pass a
     * plain "is it paid yet?" check; this lets exactly one of them run the paid
     * flow (stock, coupon, emails). Status is bound as a parameter (see below).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Order o SET o.paymentStatus = :paid WHERE o.id = :id "
         + "AND (o.paymentStatus IS NULL OR o.paymentStatus <> :paid)")
    int markPaidIfNotAlready(@Param("id") UUID id, @Param("paid") PaymentStatus paid);

    /**
     * NOTE: the payment status MUST be bound as a query parameter, never written
     * as a JPQL enum literal. With Postgres native enums (SqlTypes.NAMED_ENUM),
     * Hibernate renders the literal `com.navgrow.enums.PaymentStatus.PAID` as
     * `'PAID'::PaymentStatus` (the Java class name) instead of the real DB type
     * `payment_status`, which fails with:
     *   "operator does not exist: payment_status = paymentstatus".
     * Parameter binding goes through the enum JdbcType and casts correctly.
     */
    @Query("SELECT COALESCE(SUM(o.grandTotal), 0) FROM Order o WHERE o.paymentStatus = :status AND o.createdAt >= :from")
    BigDecimal sumRevenueByStatusFrom(@Param("status") PaymentStatus status, @Param("from") LocalDateTime from);

    default BigDecimal sumRevenueFrom(LocalDateTime from) {
        return sumRevenueByStatusFrom(PaymentStatus.PAID, from);
    }

    @Query("SELECT o FROM Order o WHERE o.createdAt >= :from ORDER BY o.createdAt DESC")
    List<Order> findRecentOrders(@Param("from") LocalDateTime from);
}