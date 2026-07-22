package com.shop.repo;

import com.shop.model.StorefrontConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StorefrontConfigRepo extends JpaRepository<StorefrontConfig, String> {

    Optional<StorefrontConfig> findBySlug(String slug);
}
