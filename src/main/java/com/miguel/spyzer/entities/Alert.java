package com.miguel.spyzer.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false, length = 10)
    private String symbol; // TSLA, AAPL, etc.
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoAlerta tipo; // PRICE_UP, PRICE_DOWN, VOLUME_HIGH
    
    @Column(name = "valor_trigger", precision = 15, scale = 2, nullable = false)
    private BigDecimal valorTrigger; // Precio que dispara la alerta ($200.00)
    
    @Builder.Default
    private Boolean activa = true; // Si está funcionando o pausada
    
    @Column(name = "mensaje_personalizado", length = 500)
    private String mensajePersonalizado; // "¡Tesla llegó a $200! Hora de vender"
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "triggered_at")
    private LocalDateTime triggeredAt; // Cuándo se disparó la alerta
    
    @Builder.Default
    private Boolean disparada = false; // Si ya se activó la alerta
    
    // Método para disparar la alerta
    public void disparar() {
        this.disparada = true;
        this.triggeredAt = LocalDateTime.now();
        this.activa = false; // Desactivar después de disparar
    }
    
    // Método para reactivar la alerta
    public void reactivar() {
        this.disparada = false;
        this.triggeredAt = null;
        this.activa = true;
    }
    
    // Método para verificar si debe dispararse
    public boolean debeDispararseConPrecio(BigDecimal precioActual) {
        if (!activa || disparada || precioActual == null) {
            return false;
        }
        
        return switch (tipo) {
            case PRICE_UP -> precioActual.compareTo(valorTrigger) >= 0;
            case PRICE_DOWN -> precioActual.compareTo(valorTrigger) <= 0;
            case PRICE_EQUAL -> precioActual.compareTo(valorTrigger) == 0;
            default -> false;
        };
    }
    
    // Método para obtener mensaje completo
    public String getMensajeCompleto() {
        String mensajeBase = String.format("Alerta de %s para %s: %s $%.2f", 
            tipo.getDescripcion(), symbol, tipo.getAccion(), valorTrigger);
        
        if (mensajePersonalizado != null && !mensajePersonalizado.trim().isEmpty()) {
            return mensajeBase + " - " + mensajePersonalizado;
        }
        
        return mensajeBase;
    }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
    
    // Enum para tipos de alerta
    public enum TipoAlerta {
        PRICE_UP("Precio Subió", "alcanzó"),
        PRICE_DOWN("Precio Bajó", "descendió a"),
        PRICE_EQUAL("Precio Exacto", "llegó exactamente a"),
        VOLUME_HIGH("Volumen Alto", "volumen superó");
        
        private final String descripcion;
        private final String accion;
        
        TipoAlerta(String descripcion, String accion) {
            this.descripcion = descripcion;
            this.accion = accion;
        }
        
        public String getDescripcion() {
            return descripcion;
        }
        
        public String getAccion() {
            return accion;
        }
    }
}