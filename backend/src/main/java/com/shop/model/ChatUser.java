package com.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Ein Konto der SecureChat-Desktop-App (getrennt von den Discord-Shop-Nutzern).
 * Registrierung/Login laufen über {@code /api/chat/**} mit Username + Passwort.
 *
 * Nachrichten werden Ende-zu-Ende verschlüsselt: der Client lädt hier nur seinen
 * <b>öffentlichen</b> ECDH-Schlüssel hoch ({@link #publicKey}); der private Schlüssel
 * verlässt nie das Gerät. Der Server sieht also niemals Klartext.
 */
@Entity
@Table(name = "chat_users")
@Getter
@Setter
@NoArgsConstructor
public class ChatUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Eindeutiger Anzeigename, mit dem man sich einloggt. */
    @Column(unique = true, nullable = false, length = 32)
    private String username;

    /** BCrypt-Hash des Passworts — nie im Klartext gespeichert. */
    @Column(nullable = false)
    private String passwordHash;

    /** Öffentlicher ECDH-Schlüssel (Base64) für die Ende-zu-Ende-Verschlüsselung. */
    @Column(columnDefinition = "text")
    private String publicKey;

    /** Aktuelles Sitzungs-Token (nach Login); im Header X-Chat-Token mitgeschickt. */
    @Column(length = 64)
    private String token;

    private Instant createdAt = Instant.now();
    private Instant lastSeen;
}
