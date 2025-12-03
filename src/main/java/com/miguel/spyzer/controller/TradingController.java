package com.miguel.spyzer.controller;

import com.miguel.spyzer.entities.Transaction;
import com.miguel.spyzer.service.TradingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/trading")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class TradingController {
    
    private final TradingService tradingService;
    
    /**
     * Comprar acciones
     * POST /api/trading/{userId}/comprar
     */
    @PostMapping("/{userId}/comprar")
    public ResponseEntity<?> comprarAccion(@PathVariable Long userId, 
                                          @RequestBody ComprarAccionRequest request) {
        log.info("POST /api/trading/{}/comprar - Comprando {} shares de {} - Ejecutado por: {}", 
                userId, request.getCantidad(), request.getSymbol(), request.getEjecutadaPor());
        
        try {
            // Validaciones básicas
            if (request.getSymbol() == null || request.getSymbol().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El símbolo es requerido"));
            }
            
            if (request.getCantidad() == null || request.getCantidad().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "La cantidad debe ser mayor a 0"));
            }
            
            Transaction transaccion = tradingService.comprarAccion(
                    userId,
                    request.getSymbol(),
                    request.getCantidad(),
                    request.getEjecutadaPor() != null ? request.getEjecutadaPor() : Transaction.EjecutadaPor.MANUAL
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "mensaje", "Compra ejecutada exitosamente",
                    "transaccion", transaccion,
                    "valorTotal", transaccion.getValorTotal()
            ));
            
        } catch (RuntimeException e) {
            log.warn("Error de negocio comprando acción: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error interno comprando acción para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Vender acciones
     * POST /api/trading/{userId}/vender
     */
    @PostMapping("/{userId}/vender")
    public ResponseEntity<?> venderAccion(@PathVariable Long userId, 
                                         @RequestBody VenderAccionRequest request) {
        log.info("POST /api/trading/{}/vender - Vendiendo {} shares de {} - Ejecutado por: {}", 
                userId, request.getCantidad(), request.getSymbol(), request.getEjecutadaPor());
        
        try {
            // Validaciones básicas
            if (request.getSymbol() == null || request.getSymbol().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El símbolo es requerido"));
            }
            
            if (request.getCantidad() == null || request.getCantidad().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "La cantidad debe ser mayor a 0"));
            }
            
            Transaction transaccion = tradingService.venderAccion(
                    userId,
                    request.getSymbol(),
                    request.getCantidad(),
                    request.getEjecutadaPor() != null ? request.getEjecutadaPor() : Transaction.EjecutadaPor.MANUAL
            );
            
            return ResponseEntity.ok(Map.of(
                    "mensaje", "Venta ejecutada exitosamente",
                    "transaccion", transaccion,
                    "valorTotal", transaccion.getValorTotal()
            ));
            
        } catch (RuntimeException e) {
            log.warn("Error de negocio vendiendo acción: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error interno vendiendo acción para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Obtener cotización para una operación (sin ejecutar)
     * POST /api/trading/cotizacion
     */
    @PostMapping("/cotizacion")
    public ResponseEntity<?> obtenerCotizacion(@RequestBody CotizacionRequest request) {
        log.info("POST /api/trading/cotizacion - Cotizando {} shares de {} para {}", 
                request.getCantidad(), request.getSymbol(), request.isEsCompra() ? "COMPRA" : "VENTA");
        
        try {
            // Validaciones básicas
            if (request.getSymbol() == null || request.getSymbol().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El símbolo es requerido"));
            }
            
            if (request.getCantidad() == null || request.getCantidad().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "La cantidad debe ser mayor a 0"));
            }
            
            BigDecimal cotizacion = tradingService.obtenerCotizacion(
                    request.getSymbol(),
                    request.getCantidad(),
                    request.isEsCompra()
            );
            
            return ResponseEntity.ok(Map.of(
                    "symbol", request.getSymbol().toUpperCase(),
                    "cantidad", request.getCantidad(),
                    "tipoOperacion", request.isEsCompra() ? "COMPRA" : "VENTA",
                    "cotizacion", cotizacion,
                    "timestamp", java.time.LocalDateTime.now()
            ));
            
        } catch (RuntimeException e) {
            log.warn("Error obteniendo cotización: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error interno obteniendo cotización: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Verificar si puede comprar una cantidad específica
     * POST /api/trading/{userId}/puede-comprar
     */
    @PostMapping("/{userId}/puede-comprar")
    public ResponseEntity<?> puedeComprar(@PathVariable Long userId, 
                                         @RequestBody ValidarOperacionRequest request) {
        log.info("POST /api/trading/{}/puede-comprar - Validando compra de {} shares de {}", 
                userId, request.getCantidad(), request.getSymbol());
        
        try {
            // Validaciones básicas
            if (request.getSymbol() == null || request.getSymbol().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El símbolo es requerido"));
            }
            
            if (request.getCantidad() == null || request.getCantidad().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "La cantidad debe ser mayor a 0"));
            }
            
            boolean puedeComprar = tradingService.puedeComprar(userId, request.getSymbol(), request.getCantidad());
            
            // Obtener cotización para información adicional
            BigDecimal cotizacion = null;
            try {
                cotizacion = tradingService.obtenerCotizacion(request.getSymbol(), request.getCantidad(), true);
            } catch (Exception e) {
                log.warn("No se pudo obtener cotización: {}", e.getMessage());
            }
            
            return ResponseEntity.ok(Map.of(
                    "puedeComprar", puedeComprar,
                    "symbol", request.getSymbol().toUpperCase(),
                    "cantidad", request.getCantidad(),
                    "cotizacion", cotizacion,
                    "timestamp", java.time.LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Error validando compra para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Verificar si puede vender una cantidad específica
     * POST /api/trading/{userId}/puede-vender
     */
    @PostMapping("/{userId}/puede-vender")
    public ResponseEntity<?> puedeVender(@PathVariable Long userId, 
                                        @RequestBody ValidarOperacionRequest request) {
        log.info("POST /api/trading/{}/puede-vender - Validando venta de {} shares de {}", 
                userId, request.getCantidad(), request.getSymbol());
        
        try {
            // Validaciones básicas
            if (request.getSymbol() == null || request.getSymbol().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El símbolo es requerido"));
            }
            
            if (request.getCantidad() == null || request.getCantidad().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "La cantidad debe ser mayor a 0"));
            }
            
            boolean puedeVender = tradingService.puedeVender(userId, request.getSymbol(), request.getCantidad());
            
            // Obtener cotización para información adicional
            BigDecimal cotizacion = null;
            try {
                cotizacion = tradingService.obtenerCotizacion(request.getSymbol(), request.getCantidad(), false);
            } catch (Exception e) {
                log.warn("No se pudo obtener cotización: {}", e.getMessage());
            }
            
            return ResponseEntity.ok(Map.of(
                    "puedeVender", puedeVender,
                    "symbol", request.getSymbol().toUpperCase(),
                    "cantidad", request.getCantidad(),
                    "cotizacion", cotizacion,
                    "timestamp", java.time.LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Error validando venta para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Validar múltiples operaciones de una vez
     * POST /api/trading/{userId}/validar-operaciones
     */
    @PostMapping("/{userId}/validar-operaciones")
    public ResponseEntity<?> validarOperaciones(@PathVariable Long userId, 
                                               @RequestBody ValidarMultiplesOperacionesRequest request) {
        log.info("POST /api/trading/{}/validar-operaciones - Validando {} operaciones", 
                userId, request.getOperaciones() != null ? request.getOperaciones().length : 0);
        
        try {
            if (request.getOperaciones() == null || request.getOperaciones().length == 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Se requiere al menos una operación"));
            }
            
            if (request.getOperaciones().length > 20) {
                return ResponseEntity.badRequest().body(Map.of("error", "Máximo 20 operaciones por solicitud"));
            }
            
            java.util.List<Map<String, Object>> resultados = new java.util.ArrayList<>();
            
            for (ValidarOperacionRequest operacion : request.getOperaciones()) {
                try {
                    boolean esValida = operacion.getTipoOperacion().equals("COMPRA") ?
                            tradingService.puedeComprar(userId, operacion.getSymbol(), operacion.getCantidad()) :
                            tradingService.puedeVender(userId, operacion.getSymbol(), operacion.getCantidad());
                    
                    BigDecimal cotizacion = tradingService.obtenerCotizacion(
                            operacion.getSymbol(), 
                            operacion.getCantidad(), 
                            operacion.getTipoOperacion().equals("COMPRA")
                    );
                    
                    resultados.add(Map.of(
                            "symbol", operacion.getSymbol().toUpperCase(),
                            "cantidad", operacion.getCantidad(),
                            "tipoOperacion", operacion.getTipoOperacion(),
                            "esValida", esValida,
                            "cotizacion", cotizacion
                    ));
                    
                } catch (Exception e) {
                    resultados.add(Map.of(
                            "symbol", operacion.getSymbol().toUpperCase(),
                            "cantidad", operacion.getCantidad(),
                            "tipoOperacion", operacion.getTipoOperacion(),
                            "esValida", false,
                            "error", e.getMessage()
                    ));
                }
            }
            
            long operacionesValidas = resultados.stream()
                    .mapToLong(r -> (Boolean) r.get("esValida") ? 1 : 0)
                    .sum();
            
            return ResponseEntity.ok(Map.of(
                    "totalOperaciones", resultados.size(),
                    "operacionesValidas", operacionesValidas,
                    "operacionesInvalidas", resultados.size() - operacionesValidas,
                    "resultados", resultados,
                    "timestamp", java.time.LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Error validando múltiples operaciones para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    // DTOs para requests
    
    public static class ComprarAccionRequest {
        private String symbol;
        private BigDecimal cantidad;
        private Transaction.EjecutadaPor ejecutadaPor;
        
        // Getters y setters
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        
        public BigDecimal getCantidad() { return cantidad; }
        public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
        
        public Transaction.EjecutadaPor getEjecutadaPor() { return ejecutadaPor; }
        public void setEjecutadaPor(Transaction.EjecutadaPor ejecutadaPor) { this.ejecutadaPor = ejecutadaPor; }
    }
    
    public static class VenderAccionRequest {
        private String symbol;
        private BigDecimal cantidad;
        private Transaction.EjecutadaPor ejecutadaPor;
        
        // Getters y setters
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        
        public BigDecimal getCantidad() { return cantidad; }
        public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
        
        public Transaction.EjecutadaPor getEjecutadaPor() { return ejecutadaPor; }
        public void setEjecutadaPor(Transaction.EjecutadaPor ejecutadaPor) { this.ejecutadaPor = ejecutadaPor; }
    }
    
    public static class CotizacionRequest {
        private String symbol;
        private BigDecimal cantidad;
        private boolean esCompra;
        
        // Getters y setters
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        
        public BigDecimal getCantidad() { return cantidad; }
        public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
        
        public boolean isEsCompra() { return esCompra; }
        public void setEsCompra(boolean esCompra) { this.esCompra = esCompra; }
    }
    
    public static class ValidarOperacionRequest {
        private String symbol;
        private BigDecimal cantidad;
        private String tipoOperacion; // "COMPRA" o "VENTA"
        
        // Getters y setters
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        
        public BigDecimal getCantidad() { return cantidad; }
        public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
        
        public String getTipoOperacion() { return tipoOperacion; }
        public void setTipoOperacion(String tipoOperacion) { this.tipoOperacion = tipoOperacion; }
    }
    
    public static class ValidarMultiplesOperacionesRequest {
        private ValidarOperacionRequest[] operaciones;
        
        public ValidarOperacionRequest[] getOperaciones() { return operaciones; }
        public void setOperaciones(ValidarOperacionRequest[] operaciones) { this.operaciones = operaciones; }
    }
}