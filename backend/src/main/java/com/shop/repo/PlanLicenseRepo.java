package com.shop.repo;

import com.shop.model.PlanLicense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanLicenseRepo extends JpaRepository<PlanLicense, Long> {

    Optional<PlanLicense> findByCodeIgnoreCase(String code);

    List<PlanLicense> findAllByOrderByCreatedAtDesc();
}
