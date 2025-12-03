package com.miguel.spyzer.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "market_data",
       indexes = {
           @Index(name = "idx_symbol_timestamp", columnList = "symbol, timestamp"),
           @Index(name = "idx_data_type", columnList = "data_type"),
           @Index(name = "idx_timestamp", columnList = "timestamp")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketData {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 10)
    private String symbol; // AAPL, ^GSPC, ^VIX, etc.
    
    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal precio; // Precio actual ($155.20)
    
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now(); // Última actualización
    
    // Datos OHLC para gráficos
    @Column(precision = 15, scale = 2)
    private BigDecimal open; // Precio apertura
    
    @Column(precision = 15, scale = 2)
    private BigDecimal high; // Precio máximo
    
    @Column(precision = 15, scale = 2)
    private BigDecimal low; // Precio mínimo
    
    @Column(precision = 15, scale = 2)
    private BigDecimal close; // Precio cierre
    
    @Column(precision = 20, scale = 0)
    private Long volumen; // Volumen de transacciones del día
    
    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    private DataType dataType; // "REALTIME", "DAILY", "HISTORICAL"
    
    // Datos adicionales útiles
    @Column(name = "precio_anterior", precision = 15, scale = 2)
    private BigDecimal precioAnterior; // Para calcular variación
    
    @Column(name = "variacion_absoluta", precision = 15, scale = 2)
    private BigDecimal variacionAbsoluta; // Cambio en $
    
    @Column(name = "variacion_porcentual", precision = 5, scale = 2)
    private BigDecimal variacionPorcentual; // % de cambio
    
    @Column(name = "market_cap", precision = 20, scale = 0)
    private Long marketCap; // Capitalización de mercado
    
    @Column(name = "week_52_high", precision = 15, scale = 2)
    private BigDecimal week52High; // Máximo 52 semanas
    
    @Column(name = "week_52_low", precision = 15, scale = 2)
    private BigDecimal week52Low; // Mínimo 52 semanas
    
    @Column(name = "pe_ratio", precision = 8, scale = 2)
    private BigDecimal peRatio; // Relación precio/ganancia
    
    // Método para calcular variación porcentual y absoluta
    public void calcularVariaciones() {
        if (precioAnterior != null && precioAnterior.compareTo(BigDecimal.ZERO) > 0) {
            // Variación absoluta
            this.variacionAbsoluta = precio.subtract(precioAnterior);
            
            // Variación porcentual
            this.variacionPorcentual = variacionAbsoluta
                .divide(precioAnterior, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        }
    }
    
    // Sistema de caché simple - los datos siempre están frescos por updates programados
    public boolean sonDatosRecientes() {
        return timestamp.isAfter(LocalDateTime.now().minusHours(1));
    }
    
    // Método para verificar si es dato en tiempo real
    public boolean esEnTiempoReal() {
        return dataType == DataType.REALTIME;
    }
    
    // Método para obtener precio con variación formateado
    public String getPrecioConVariacion() {
        if (variacionPorcentual != null && variacionAbsoluta != null) {
            String signo = variacionAbsoluta.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
            return String.format("$%.2f (%s$%.2f / %s%.2f%%)", 
                precio, signo, variacionAbsoluta, signo, variacionPorcentual);
        }
        return String.format("$%.2f", precio);
    }
    
    // Método para verificar si está en tendencia alcista
    public boolean esTendenciaAlcista() {
        return variacionPorcentual != null && variacionPorcentual.compareTo(BigDecimal.ZERO) > 0;
    }
    
    // Método para verificar si está en tendencia bajista
    public boolean esTendenciaBajista() {
        return variacionPorcentual != null && variacionPorcentual.compareTo(BigDecimal.ZERO) < 0;
    }
    
    // Método para obtener color de tendencia (para frontend)
    public String getColorTendencia() {
        if (esTendenciaAlcista()) return "green";
        if (esTendenciaBajista()) return "red";
        return "gray";
    }
    
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        calcularVariaciones();
    }
    
    @PreUpdate
    protected void onUpdate() {
        calcularVariaciones();
    }
    
    // Enum para tipos de datos
    public enum DataType {
        REALTIME("Tiempo Real", 5), // Expira en 5 minutos
        DAILY("Diario", 1440), // Expira en 24 horas (1440 minutos)
        HISTORICAL("Histórico", Integer.MAX_VALUE); // No expira
        
        private final String descripcion;
        private final int minutosExpiracion;
        
        DataType(String descripcion, int minutosExpiracion) {
            this.descripcion = descripcion;
            this.minutosExpiracion = minutosExpiracion;
        }
        
        public String getDescripcion() {
            return descripcion;
        }
        
        public int getMinutosExpiracion() {
            return minutosExpiracion;
        }
    }
}