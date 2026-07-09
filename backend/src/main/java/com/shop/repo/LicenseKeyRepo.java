package com.shop.repo;

import com.shop.model.LicenseKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LicenseKeyRepo extends JpaRepository<LicenseKey, Long> {

    Optional<LicenseKey> findFirstByProductIdAndUsedFalse(Long productId);

    long countByProductIdAndUsedFalse(Long productId);

    java.util.List<LicenseKey> findByOrderId(Long orderId);
}
