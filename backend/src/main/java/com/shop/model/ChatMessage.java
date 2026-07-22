package com.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Eine verschlüsselte Nachricht zwischen zwei SecureChat-Nutzern.
 * Der Server speichert und verteilt nur den <b>Ciphertext</b> (Base64 des
 * versiegelten Blobs: Nonce + Tag + AES-256-GCM-Daten) — er kann den Inhalt
 * nicht lesen.
 */
@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_to", columnList = "toUser,id")
})
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Absender-Username. */
    @Column(nullable = false, length = 32)
    private String fromUser;

    /** Empfänger-Username. */
    @Column(nullable = false, length = 32)
    private String toUser;

    /** Versiegelte Nachricht (Base64). Klartext existiert nur auf den Geräten. */
    @Column(columnDefinition = "text", nullable = false)
    private String ciphertext;

    private Instant createdAt = Instant.now();
}
