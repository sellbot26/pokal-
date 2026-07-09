package com.shop.repo;

import com.shop.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewRepo extends JpaRepository<Review, Long> {

    Optional<Review> findByUserIdAndProductId(String userId, Long productId);
}
