package com.shop.repo;

import com.shop.model.ManualPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ManualPaymentRepo extends JpaRepository<ManualPayment, Long> {

    List<ManualPayment> findAllByOrderByPaymentDateDesc();

    List<ManualPayment> findByStatusAndPaymentDateAfter(ManualPayment.Status status, Instant after);

    List<ManualPayment> findByOwnerIdOrderByPaymentDateDesc(String ownerId);
}
