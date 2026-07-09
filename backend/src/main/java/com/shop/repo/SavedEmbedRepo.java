package com.shop.repo;

import com.shop.model.SavedEmbed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedEmbedRepo extends JpaRepository<SavedEmbed, Long> {

    Optional<SavedEmbed> findByNameIgnoreCase(String name);

    List<SavedEmbed> findAllByOrderByNameAsc();

    Optional<SavedEmbed> findByOwnerIdAndNameIgnoreCase(String ownerId, String name);

    List<SavedEmbed> findByOwnerIdOrderByNameAsc(String ownerId);

    long countByOwnerId(String ownerId);
}
