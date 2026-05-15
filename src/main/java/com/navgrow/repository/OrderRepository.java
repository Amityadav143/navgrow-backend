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
    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);
    long countByStatus(OrderStatus status);
    long countByPaymentStatus(PaymentStatus paymentStatus);

    @Query("SELECT COALESCE(SUM(o.grandTotal), 0) FROM Order o WHERE o.paymentStatus = com.navgrow.enums.PaymentStatus.PAID AND o.createdAt >= :from")
    BigDecimal sumRevenueFrom(@Param("from") LocalDateTime from);

    @Query("SELECT o FROM Order o WHERE o.createdAt >= :from ORDER BY o.createdAt DESC")
    List<Order> findRecentOrders(@Param("from") LocalDateTime from);
}