package com.shop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Kundenbewertung nach einem verifizierten Kauf — gepostet über /review. */
@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String guildId;
    private Long productId;
    private String productName;

    private String userId;
    private String username;

    /** 1-5 Sterne */
    private int stars;

    @Column(columnDefinition = "text")
    private String text;

    private Instant createdAt = Instant.now();
}
