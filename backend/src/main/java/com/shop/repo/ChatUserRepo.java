package com.shop.repo;

import com.shop.model.ChatUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatUserRepo extends JpaRepository<ChatUser, Long> {

    Optional<ChatUser> findByUsernameIgnoreCase(String username);

    Optional<ChatUser> findByToken(String token);

    boolean existsByUsernameIgnoreCase(String username);

    List<ChatUser> findAllByOrderByUsernameAsc();
}
