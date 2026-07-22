package com.shop.repo;

import com.shop.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepo extends JpaRepository<ChatMessage, Long> {

    /** Neue Nachrichten an einen Empfänger seit einer bestimmten ID (fürs Polling). */
    List<ChatMessage> findByToUserAndIdGreaterThanOrderByIdAsc(String toUser, long sinceId);

    /** Kompletter Verlauf zwischen zwei Nutzern (beide Richtungen), chronologisch. */
    List<ChatMessage> findByFromUserInAndToUserInOrderByIdAsc(List<String> from, List<String> to);
}
