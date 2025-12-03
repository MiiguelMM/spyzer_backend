package com.miguel.spyzer.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "portfolio")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Portfolio {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false, length = 10)
    private String symbol; // AAPL, TSLA, ^GSPC
    
    @Column(precision = 15, scale = 6, nullable = false)
    private BigDecimal cantidad; // 10.5 shares
    
    @Column(name = "precio_promedio", precision = 15, scale = 2, nullable = false)
    private BigDecimal precioPromedio; // $150.30
    
    @Column(name = "precio_actual", precision = 15, scale = 2, nullable = false)
    private BigDecimal precioActual; // $155.20
    
    @Column(name = "valor_mercado", precision = 15, scale = 2, nullable = false)
    private BigDecimal valorMercado; // cantidad * precio_actual
    
    @Column(name = "ganancia_perdida", precision = 15, scale = 2, nullable = false)
    private BigDecimal gananciaPerdida; // valor_mercado - (cantidad * precio_promedio)
    
    // Método para calcular automáticamente el valor de mercado
    public void calcularValorMercado() {
        if (cantidad != null && precioActual != null) {
            this.valorMercado = cantidad.multiply(precioActual).setScale(2, RoundingMode.HALF_UP);
        }
    }
    
    // Método para calcular automáticamente ganancia/pérdida
    public void calcularGananciaPerdida() {
        if (cantidad != null && precioPromedio != null && valorMercado != null) {
            BigDecimal costoTotal = cantidad.multiply(precioPromedio);
            this.gananciaPerdida = valorMercado.subtract(costoTotal).setScale(2, RoundingMode.HALF_UP);
        }
    }
    
    @PrePersist
    @PreUpdate
    protected void calcularValores() {
        calcularValorMercado();
        calcularGananciaPerdida();
    }
}