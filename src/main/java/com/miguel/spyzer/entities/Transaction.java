package com.miguel.spyzer.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User user;
    
    @Column(nullable = false, length = 10)
    private String symbol; // AAPL, TSLA, etc.
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoTransaccion tipo; // BUY, SELL
    
    @Column(precision = 15, scale = 6, nullable = false)
    private BigDecimal cantidad; // 5.0 shares
    
    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal precio; // $155.50 por acción
    
    @Enumerated(EnumType.STRING)
    @Column(name = "ejecutada_por", nullable = false)
    private EjecutadaPor ejecutadaPor; // MANUAL, BOT
    
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    // Método para calcular el valor total de la transacción
    public BigDecimal getValorTotal() {
        if (cantidad != null && precio != null) {
            return cantidad.multiply(precio);
        }
        return BigDecimal.ZERO;
    }
    
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
    
    // Enums internos
    public enum TipoTransaccion {
        BUY("Compra"),
        SELL("Venta");
        
        private final String descripcion;
        
        TipoTransaccion(String descripcion) {
            this.descripcion = descripcion;
        }
        
        public String getDescripcion() {
            return descripcion;
        }
    }
    
    public enum EjecutadaPor {
        MANUAL("Manual - Usuario"),
        BOT("Automático - Bot");
        
        private final String descripcion;
        
        EjecutadaPor(String descripcion) {
            this.descripcion = descripcion;
        }
        
        public String getDescripcion() {
            return descripcion;
        }
    }
}