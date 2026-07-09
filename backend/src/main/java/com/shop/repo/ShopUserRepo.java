package com.shop.repo;

import com.shop.model.ShopUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopUserRepo extends JpaRepository<ShopUser, String> {
}
