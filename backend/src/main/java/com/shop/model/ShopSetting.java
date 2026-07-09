package com.shop.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Key/Value-Einstellung, über das Dashboard editierbar (Shop-Name, Farben, Wallets, …). */
@Entity
@Table(name = "shop_settings")
@Getter
@Setter
@NoArgsConstructor
public class ShopSetting {

    @Id
    @Column(name = "setting_key", length = 64)
    private String key;

    @Column(name = "setting_value", columnDefinition = "text")
    private String value;

    public ShopSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
