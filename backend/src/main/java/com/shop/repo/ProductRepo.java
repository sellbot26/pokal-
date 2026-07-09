package com.shop.repo;

import com.shop.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRepo extends JpaRepository<Product, Long> {

    List<Product> findByActiveTrueOrderByCategoryAscNameAsc();

    List<Product> findByActiveTrueAndCategoryOrderByNameAsc(String category);

    Optional<Product> findByNameIgnoreCase(String name);

    List<Product> findTop25ByActiveTrueAndNameContainingIgnoreCase(String name);

    @Query("select distinct p.category from Product p where p.active = true and p.category is not null order by p.category")
    List<String> findActiveCategories();

    // ===== Pro Discord-Server getrennt (Multi-Server-Support) =====

    List<Product> findByGuildIdOrderByCategoryAscNameAsc(String guildId);

    List<Product> findByGuildIdAndActiveTrueOrderByCategoryAscNameAsc(String guildId);

    List<Product> findByGuildIdAndActiveTrueAndCategoryOrderByNameAsc(String guildId, String category);

    Optional<Product> findByGuildIdAndNameIgnoreCase(String guildId, String name);

    List<Product> findTop25ByGuildIdAndActiveTrueAndNameContainingIgnoreCase(String guildId, String name);
}
