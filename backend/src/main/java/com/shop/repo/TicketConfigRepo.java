package com.shop.repo;

import com.shop.model.TicketConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketConfigRepo extends JpaRepository<TicketConfig, String> {
}
