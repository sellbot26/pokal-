package com.shop.repo;

import com.shop.model.Giveaway;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface GiveawayRepo extends JpaRepository<Giveaway, Long> {

    /** Gewinnspiele eines Servers, neueste zuerst — für die Dashboard-Liste. */
    List<Giveaway> findByGuildIdOrderByCreatedAtDesc(String guildId);

    /** Abgelaufene, noch laufende Gewinnspiele — für den Auslos-Scheduler. */
    List<Giveaway> findByStatusAndEndsAtBefore(Giveaway.Status status, Instant cutoff);
}
