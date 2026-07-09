package com.shop.repo;

import com.shop.model.DiscountCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiscountCodeRepo extends JpaRepository<DiscountCode, Long> {

    Optional<DiscountCode> findByCodeIgnoreCase(String code);

    Optional<DiscountCode> findByGuildIdAndCodeIgnoreCase(String guildId, String code);

    List<DiscountCode> findByGuildIdOrderByCreatedAtDesc(String guildId);
}
