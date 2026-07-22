package com.shop.repo;

import com.shop.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepo extends JpaRepository<Review, Long> {

    Optional<Review> findByUserIdAndProductId(String userId, Long productId);

    /** Alle Reviews eines Servers, neueste zuerst — für die Storefront. */
    List<Review> findByGuildIdOrderByCreatedAtDesc(String guildId);
}
