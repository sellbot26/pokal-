package com.shop.repo;

import com.shop.model.ShopSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopSettingRepo extends JpaRepository<ShopSetting, String> {
}
