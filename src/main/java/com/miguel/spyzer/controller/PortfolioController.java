package com.miguel.spyzer.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.miguel.spyzer.entities.Portfolio;
import com.miguel.spyzer.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class PortfolioController {
    
    private final PortfolioService portfolioService;
    
    /**
     * Obtener portfolio completo del usuario
     * GET /api/portfolio/{userId}
     */
    @GetMapping("/{userId}")
    public ResponseEntity<List<PortfolioDTO>> obtenerPortfolio(@PathVariable Long userId) {
        log.info("GET /api/portfolio/{} - Obteniendo portfolio del usuario", userId);
        
        try {
            List<Portfolio> portfolio = portfolioService.obtenerPortfolioPorUsuario(userId);
            List<PortfolioDTO> dtos = portfolio.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            log.error("Error obteniendo portfolio del usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Obtener portfolio ordenado por valor de mercado
     * GET /api/portfolio/{userId}/ordenado
     */
    @GetMapping("/{userId}/ordenado")
    public ResponseEntity<List<PortfolioDTO>> obtenerPortfolioOrdenado(@PathVariable Long userId) {
        log.info("GET /api/portfolio/{}/ordenado - Obteniendo portfolio ordenado", userId);
        
        try {
            List<Portfolio> portfolio = portfolioService.obtenerPortfolioOrdenadoPorValor(userId);
            List<PortfolioDTO> dtos = portfolio.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            log.error("Error obteniendo portfolio ordenado del usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Buscar posición específica por símbolo
     * GET /api/portfolio/{userId}/posicion/{symbol}
     */
    @GetMapping("/{userId}/posicion/{symbol}")
    public ResponseEntity<PortfolioDTO> obtenerPosicion(@PathVariable Long userId, @PathVariable String symbol) {
        log.info("GET /api/portfolio/{}/posicion/{} - Buscando posición", userId, symbol);
        
        try {
            Optional<Portfolio> posicion = portfolioService.buscarPosicionPorSimbolo(userId, symbol);
            
            if (posicion.isPresent()) {
                return ResponseEntity.ok(convertToDTO(posicion.get()));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error buscando posición {} del usuario {}: {}", symbol, userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Verificar si tiene posición en un símbolo
     * GET /api/portfolio/{userId}/tiene/{symbol}
     */
    @GetMapping("/{userId}/tiene/{symbol}")
    public ResponseEntity<Map<String, Boolean>> tienePosicion(@PathVariable Long userId, @PathVariable String symbol) {
        log.info("GET /api/portfolio/{}/tiene/{} - Verificando posición", userId, symbol);
        
        try {
            boolean tieneposicion = portfolioService.tieneposicionEnSimbolo(userId, symbol);
            return ResponseEntity.ok(Map.of("tienePosicion", tieneposicion));
        } catch (Exception e) {
            log.error("Error verificando posición {} del usuario {}: {}", symbol, userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Comprar shares - Agregar a posición existente o crear nueva
     * POST /api/portfolio/{userId}/comprar
     */
    @PostMapping("/{userId}/comprar")
    public ResponseEntity<PortfolioDTO> comprarShares(@PathVariable Long userId, 
                                                  @RequestBody ComprarSharesRequest request) {
        log.info("POST /api/portfolio/{}/comprar - Comprando {} shares de {} a ${}", 
                userId, request.getCantidad(), request.getSymbol(), request.getPrecio());
        
        try {
            if (request.getCantidad().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().build();
            }
            
            if (request.getPrecio().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().build();
            }
            
            Portfolio portfolio = portfolioService.comprarShares(
                    userId, 
                    request.getSymbol(), 
                    request.getCantidad(), 
                    request.getPrecio(), 
                    request.getPrecioActual()
            );
            
            return ResponseEntity.ok(convertToDTO(portfolio));
            
        } catch (Exception e) {
            log.error("Error comprando shares para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Vender shares - Reducir posición existente
     * POST /api/portfolio/{userId}/vender
     */
    @PostMapping("/{userId}/vender")
    public ResponseEntity<?> venderShares(@PathVariable Long userId, 
                                         @RequestBody VenderSharesRequest request) {
        log.info("POST /api/portfolio/{}/vender - Vendiendo {} shares de {} a ${}", 
                userId, request.getCantidad(), request.getSymbol(), request.getPrecioVenta());
        
        try {
            if (request.getCantidad().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "La cantidad debe ser mayor a 0"));
            }
            
            if (request.getPrecioVenta().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "El precio debe ser mayor a 0"));
            }
            
            Portfolio portfolio = portfolioService.venderShares(
                    userId, 
                    request.getSymbol(), 
                    request.getCantidad(), 
                    request.getPrecioVenta(), 
                    request.getPrecioActual()
            );
            
            if (portfolio == null) {
                return ResponseEntity.ok(Map.of("mensaje", "Posición vendida completamente"));
            }
            
            return ResponseEntity.ok(convertToDTO(portfolio));
            
        } catch (IllegalArgumentException e) {
            log.warn("Error de validación vendiendo shares: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error vendiendo shares para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Actualizar precio actual de una posición
     * PUT /api/portfolio/{userId}/actualizar-precio
     */
    @PutMapping("/{userId}/actualizar-precio")
    public ResponseEntity<Map<String, String>> actualizarPrecio(@PathVariable Long userId, 
                                                               @RequestBody ActualizarPrecioRequest request) {
        log.info("PUT /api/portfolio/{}/actualizar-precio - Actualizando {} a ${}", 
                userId, request.getSymbol(), request.getNuevoPrecio());
        
        try {
            if (request.getNuevoPrecio().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "El precio debe ser mayor a 0"));
            }
            
            portfolioService.actualizarPreciosActuales(userId, request.getSymbol(), request.getNuevoPrecio());
            return ResponseEntity.ok(Map.of("mensaje", "Precio actualizado correctamente"));
            
        } catch (Exception e) {
            log.error("Error actualizando precio para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Obtener valor total del portfolio
     * GET /api/portfolio/{userId}/valor-total
     */
    @GetMapping("/{userId}/valor-total")
    public ResponseEntity<Map<String, BigDecimal>> obtenerValorTotal(@PathVariable Long userId) {
        log.info("GET /api/portfolio/{}/valor-total - Calculando valor total", userId);
        
        try {
            BigDecimal valorTotal = portfolioService.calcularValorTotalPortfolio(userId);
            return ResponseEntity.ok(Map.of("valorTotal", valorTotal));
        } catch (Exception e) {
            log.error("Error calculando valor total para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Obtener ganancia/pérdida total del portfolio
     * GET /api/portfolio/{userId}/ganancia-perdida
     */
    @GetMapping("/{userId}/ganancia-perdida")
    public ResponseEntity<Map<String, BigDecimal>> obtenerGananciaPerdida(@PathVariable Long userId) {
        log.info("GET /api/portfolio/{}/ganancia-perdida - Calculando ganancia/pérdida", userId);
        
        try {
            BigDecimal gananciaPerdida = portfolioService.calcularGananciaPerdidaTotal(userId);
            return ResponseEntity.ok(Map.of("gananciaPerdida", gananciaPerdida));
        } catch (Exception e) {
            log.error("Error calculando ganancia/pérdida para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Obtener resumen completo del portfolio
     * GET /api/portfolio/{userId}/resumen
     */
    @GetMapping("/{userId}/resumen")
    public ResponseEntity<PortfolioResumen> obtenerResumen(@PathVariable Long userId) {
        log.info("GET /api/portfolio/{}/resumen - Obteniendo resumen completo", userId);
        
        try {
            BigDecimal valorTotal = portfolioService.calcularValorTotalPortfolio(userId);
            BigDecimal gananciaPerdida = portfolioService.calcularGananciaPerdidaTotal(userId);
            long numeroPosiciones = portfolioService.contarPosiciones(userId);
            List<Portfolio> posiciones = portfolioService.obtenerPortfolioOrdenadoPorValor(userId);
            
            List<PortfolioDTO> posicionesDTO = posiciones.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            
            PortfolioResumen resumen = new PortfolioResumen(
                    valorTotal, 
                    gananciaPerdida, 
                    numeroPosiciones, 
                    posicionesDTO
            );
            
            return ResponseEntity.ok(resumen);
        } catch (Exception e) {
            log.error("Error obteniendo resumen para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Eliminar una posición completamente
     * DELETE /api/portfolio/{userId}/eliminar/{symbol}
     */
    @DeleteMapping("/{userId}/eliminar/{symbol}")
    public ResponseEntity<Map<String, String>> eliminarPosicion(@PathVariable Long userId, 
                                                               @PathVariable String symbol) {
        log.info("DELETE /api/portfolio/{}/eliminar/{} - Eliminando posición", userId, symbol);
        
        try {
            portfolioService.eliminarPosicion(userId, symbol);
            return ResponseEntity.ok(Map.of("mensaje", "Posición eliminada correctamente"));
        } catch (Exception e) {
            log.error("Error eliminando posición {} para usuario {}: {}", symbol, userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // Método de conversión a DTO
    private PortfolioDTO convertToDTO(Portfolio portfolio) {
        return new PortfolioDTO(
                portfolio.getId(),
                portfolio.getSymbol(),
                portfolio.getCantidad(),
                portfolio.getPrecioPromedio(),
                portfolio.getPrecioActual(),
                portfolio.getValorMercado(),
                portfolio.getGananciaPerdida()
        );
    }
    
    // DTOs
    
    public static class PortfolioDTO {
        private final Long id;
        private final String symbol;
        private final BigDecimal cantidad;
        private final BigDecimal precioPromedio;
        private final BigDecimal precioActual;
        private final BigDecimal valorMercado;
        private final BigDecimal gananciaPerdida;
        
        public PortfolioDTO(Long id, String symbol, BigDecimal cantidad, BigDecimal precioPromedio,
                           BigDecimal precioActual, BigDecimal valorMercado, BigDecimal gananciaPerdida) {
            this.id = id;
            this.symbol = symbol;
            this.cantidad = cantidad;
            this.precioPromedio = precioPromedio;
            this.precioActual = precioActual;
            this.valorMercado = valorMercado;
            this.gananciaPerdida = gananciaPerdida;
        }
        
        public Long getId() { return id; }
        public String getSymbol() { return symbol; }
        public BigDecimal getCantidad() { return cantidad; }
        public BigDecimal getPrecioPromedio() { return precioPromedio; }
        public BigDecimal getPrecioActual() { return precioActual; }
        public BigDecimal getValorMercado() { return valorMercado; }
        public BigDecimal getGananciaPerdida() { return gananciaPerdida; }
    }
    
    public static class ComprarSharesRequest {
        private String symbol;
        private BigDecimal cantidad;
        private BigDecimal precio;
        private BigDecimal precioActual;
        
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        
        public BigDecimal getCantidad() { return cantidad; }
        public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
        
        public BigDecimal getPrecio() { return precio; }
        public void setPrecio(BigDecimal precio) { this.precio = precio; }
        
        public BigDecimal getPrecioActual() { return precioActual; }
        public void setPrecioActual(BigDecimal precioActual) { this.precioActual = precioActual; }
    }
    
    public static class VenderSharesRequest {
        private String symbol;
        private BigDecimal cantidad;
        private BigDecimal precioVenta;
        private BigDecimal precioActual;
        
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        
        public BigDecimal getCantidad() { return cantidad; }
        public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
        
        public BigDecimal getPrecioVenta() { return precioVenta; }
        public void setPrecioVenta(BigDecimal precioVenta) { this.precioVenta = precioVenta; }
        
        public BigDecimal getPrecioActual() { return precioActual; }
        public void setPrecioActual(BigDecimal precioActual) { this.precioActual = precioActual; }
    }
    
    public static class ActualizarPrecioRequest {
        private String symbol;
        private BigDecimal nuevoPrecio;
        
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        
        public BigDecimal getNuevoPrecio() { return nuevoPrecio; }
        public void setNuevoPrecio(BigDecimal nuevoPrecio) { this.nuevoPrecio = nuevoPrecio; }
    }
    
    public static class PortfolioResumen {
        private final BigDecimal valorTotal;
        private final BigDecimal gananciaPerdida;
        private final long numeroPosiciones;
        private final List<PortfolioDTO> posiciones;
        
        public PortfolioResumen(BigDecimal valorTotal, BigDecimal gananciaPerdida, 
                               long numeroPosiciones, List<PortfolioDTO> posiciones) {
            this.valorTotal = valorTotal;
            this.gananciaPerdida = gananciaPerdida;
            this.numeroPosiciones = numeroPosiciones;
            this.posiciones = posiciones;
        }
        
        public BigDecimal getValorTotal() { return valorTotal; }
        public BigDecimal getGananciaPerdida() { return gananciaPerdida; }
        public long getNumeroPosiciones() { return numeroPosiciones; }
        public List<PortfolioDTO> getPosiciones() { return posiciones; }
    }
}