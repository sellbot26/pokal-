package com.shop.repo;

import com.shop.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface OrderRepo extends JpaRepository<Order, Long> {

    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Order> findAllByOrderByCreatedAtDesc();

    List<Order> findByStatusOrderByCreatedAtDesc(Order.Status status);

    List<Order> findByStatusAndCreatedAtBefore(Order.Status status, Instant before);

    List<Order> findByStatusInAndPaidAtAfter(Collection<Order.Status> statuses, Instant after);

    long countByStatus(Order.Status status);

    long countByCreatedAtAfter(Instant after);

    @Query("select coalesce(sum(o.totalPrice), 0) from Order o where o.status in :statuses")
    BigDecimal revenueTotal(@Param("statuses") Collection<Order.Status> statuses);

    @Query("select coalesce(sum(o.totalPrice), 0) from Order o where o.status in :statuses and o.paidAt >= :since")
    BigDecimal revenueSince(@Param("statuses") Collection<Order.Status> statuses, @Param("since") Instant since);

    @Query("select o.productName, sum(o.quantity), sum(o.totalPrice) from Order o " +
            "where o.status in :statuses group by o.productName order by sum(o.totalPrice) desc")
    List<Object[]> topProducts(@Param("statuses") Collection<Order.Status> statuses);

    @Query("select count(distinct o.userId) from Order o where o.createdAt >= :since")
    long activeCustomersSince(@Param("since") Instant since);
}
