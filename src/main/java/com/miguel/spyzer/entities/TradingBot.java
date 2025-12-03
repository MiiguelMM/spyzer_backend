package com.miguel.spyzer.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.math.BigDecimal;

@Entity
@Table(name = "trading_bots")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradingBot {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false, length = 100)
    private String nombre; // "Mi Bot Conservative"
    
    @Builder.Default
    private Boolean activo = false; // Encendido/Apagado desde React
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estrategia estrategia; // "buy_low_sell_high", "moving_average"
    
    @Column(columnDefinition = "JSON")
    private String parametros; // {"buy_drop": 5%, "sell_gain": 10%}
    
    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal budget; // Máximo dinero que puede usar ($1,000)
    
    @Column(name = "stop_loss", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal stopLoss = new BigDecimal("15.00"); // Límite de pérdida por operación (15%)
    
    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal performance = BigDecimal.ZERO; // Ganancia/pérdida total del bot (+$234.50)
    
    @Column(name = "trades_hoy")
    @Builder.Default
    private Integer tradesHoy = 0; // Operaciones ejecutadas hoy (3)
    
    // Método para verificar si el bot puede ejecutar trades
    public boolean puedeEjecutar() {
        return activo && budget.compareTo(BigDecimal.ZERO) > 0;
    }
    
    // Método para actualizar performance
    public void actualizarPerformance(BigDecimal gananciaOPerdida) {
        if (gananciaOPerdida != null) {
            this.performance = this.performance.add(gananciaOPerdida);
        }
    }
    
    // Método para incrementar trades del día
    public void incrementarTradesHoy() {
        this.tradesHoy++;
    }
    
    // Método para resetear trades diarios (llamar cada día)
    public void resetearTradesDiarios() {
        this.tradesHoy = 0;
    }
    
    // Enum para las estrategias del bot
    public enum Estrategia {
        BUY_LOW_SELL_HIGH("Comprar Bajo, Vender Alto"),
        MOVING_AVERAGE("Media Móvil"),
        MOMENTUM("Momentum Trading"),
        SCALPING("Scalping"),
        SWING_TRADING("Swing Trading");
        
        private final String descripcion;
        
        Estrategia(String descripcion) {
            this.descripcion = descripcion;
        }
        
        public String getDescripcion() {
            return descripcion;
        }
    }
}