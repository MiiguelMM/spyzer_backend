package com.miguel.spyzer.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "google_id", unique = true, nullable = false)
    private String googleId;
    
    @Column(nullable = false)
    private String email;
    
    @Column(nullable = false)
    private String name;
    
    private String avatar;
    
    @Column(name = "balance_inicial", precision = 15, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal balanceInicial = new BigDecimal("50000.00");
    
    @Column(name = "balance_actual", precision = 15, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal balanceActual = new BigDecimal("50000.00");
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (balanceInicial == null) {
            balanceInicial = new BigDecimal("50000.00");
        }
        if (balanceActual == null) {
            balanceActual = balanceInicial;
        }
    }
}