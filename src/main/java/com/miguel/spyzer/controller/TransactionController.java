package com.miguel.spyzer.controller;

import com.miguel.spyzer.entities.Transaction;
import com.miguel.spyzer.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class TransactionController {
    
    private final TransactionService transactionService;
    
    /**
     * Registrar una nueva transacción de compra
     * POST /api/transactions/{userId}/compra
     */
    @PostMapping("/{userId}/compra")
    public ResponseEntity<?> registrarCompra(@PathVariable Long userId, 
                                           @RequestBody RegistrarTransaccionRequest request) {
        log.info("POST /api/transactions/{}/compra - Registrando compra de {} shares de {} a ${}", 
                userId, request.getCantidad(), request.getSymbol(), request.getPrecio());
        
        try {
            // Validaciones básicas
            if (request.getSymbol() == null || request.getSymbol().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El símbolo es requerido"));
            }
            
            if (request.getCantidad() == null || request.getCantidad().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "La cantidad debe ser mayor a 0"));
            }
            
            if (request.getPrecio() == null || request.getPrecio().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "El precio debe ser mayor a 0"));
            }
            
            Transaction transaccion = transactionService.registrarCompra(
                    userId,
                    request.getSymbol(),
                    request.getCantidad(),
                    request.getPrecio(),
                    request.getEjecutadaPor() != null ? request.getEjecutadaPor() : Transaction.EjecutadaPor.MANUAL
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(transaccion);
            
        } catch (Exception e) {
            log.error("Error registrando compra para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Registrar una nueva transacción de venta
     * POST /api/transactions/{userId}/venta
     */
    @PostMapping("/{userId}/venta")
    public ResponseEntity<?> registrarVenta(@PathVariable Long userId, 
                                          @RequestBody RegistrarTransaccionRequest request) {
        log.info("POST /api/transactions/{}/venta - Registrando venta de {} shares de {} a ${}", 
                userId, request.getCantidad(), request.getSymbol(), request.getPrecio());
        
        try {
            // Validaciones básicas
            if (request.getSymbol() == null || request.getSymbol().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El símbolo es requerido"));
            }
            
            if (request.getCantidad() == null || request.getCantidad().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "La cantidad debe ser mayor a 0"));
            }
            
            if (request.getPrecio() == null || request.getPrecio().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "El precio debe ser mayor a 0"));
            }
            
            Transaction transaccion = transactionService.registrarVenta(
                    userId,
                    request.getSymbol(),
                    request.getCantidad(),
                    request.getPrecio(),
                    request.getEjecutadaPor() != null ? request.getEjecutadaPor() : Transaction.EjecutadaPor.MANUAL
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(transaccion);
            
        } catch (Exception e) {
            log.error("Error registrando venta para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Obtener historial completo de transacciones
     * GET /api/transactions/{userId}
     */
    @GetMapping("/{userId}")
    public ResponseEntity<List<Transaction>> obtenerHistorial(@PathVariable Long userId) {
        log.info("GET /api/transactions/{} - Obteniendo historial completo", userId);
        
        try {
            List<Transaction> transacciones = transactionService.obtenerHistorialCompleto(userId);
            return ResponseEntity.ok(transacciones);
        } catch (Exception e) {
            log.error("Error obteniendo historial para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Obtener transacciones recientes (últimas 10)
     * GET /api/transactions/{userId}/recientes
     */
    @GetMapping("/{userId}/recientes")
    public ResponseEntity<List<Transaction>> obtenerTransaccionesRecientes(@PathVariable Long userId) {
        log.info("GET /api/transactions/{}/recientes - Obteniendo transacciones recientes", userId);
        
        try {
            List<Transaction> transacciones = transactionService.obtenerTransaccionesRecientes(userId);
            return ResponseEntity.ok(transacciones);
        } catch (Exception e) {
            log.error("Error obteniendo transacciones recientes para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Obtener transacciones por tipo (BUY o SELL)
     * GET /api/transactions/{userId}/tipo/{tipo}
     */
    @GetMapping("/{userId}/tipo/{tipo}")
    public ResponseEntity<?> obtenerTransaccionesPorTipo(@PathVariable Long userId, 
                                                        @PathVariable String tipo) {
        log.info("GET /api/transactions/{}/tipo/{} - Obteniendo transacciones por tipo", userId, tipo);
        
        try {
            Transaction.TipoTransaccion tipoTransaccion;
            try {
                tipoTransaccion = Transaction.TipoTransaccion.valueOf(tipo.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Tipo de transacción inválido. Valores válidos: BUY, SELL"));
            }
            
            List<Transaction> transacciones = transactionService.obtenerTransaccionesPorTipo(userId, tipoTransaccion);
            return ResponseEntity.ok(transacciones);
            
        } catch (Exception e) {
            log.error("Error obteniendo transacciones por tipo para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Obtener transacciones de un símbolo específico
     * GET /api/transactions/{userId}/symbol/{symbol}
     */
    @GetMapping("/{userId}/symbol/{symbol}")
    public ResponseEntity<List<Transaction>> obtenerTransaccionesPorSimbolo(@PathVariable Long userId, 
                                                                           @PathVariable String symbol) {
        log.info("GET /api/transactions/{}/symbol/{} - Obteniendo transacciones por símbolo", userId, symbol);
        
        try {
            List<Transaction> transacciones = transactionService.obtenerTransaccionesPorSimbolo(userId, symbol);
            return ResponseEntity.ok(transacciones);
        } catch (Exception e) {
            log.error("Error obteniendo transacciones de {} para usuario {}: {}", symbol, userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Obtener estadísticas de trading del usuario
     * GET /api/transactions/{userId}/estadisticas
     */
    @GetMapping("/{userId}/estadisticas")
    public ResponseEntity<TransactionService.TradingStats> obtenerEstadisticas(@PathVariable Long userId) {
        log.info("GET /api/transactions/{}/estadisticas - Obteniendo estadísticas de trading", userId);
        
        try {
            TransactionService.TradingStats stats = transactionService.obtenerEstadisticasTrading(userId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error obteniendo estadísticas para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Obtener volumen total operado
     * GET /api/transactions/{userId}/volumen-total
     */
    @GetMapping("/{userId}/volumen-total")
    public ResponseEntity<Map<String, BigDecimal>> obtenerVolumenTotal(@PathVariable Long userId) {
        log.info("GET /api/transactions/{}/volumen-total - Calculando volumen total", userId);
        
        try {
            BigDecimal volumenTotal = transactionService.calcularVolumenTotalOperado(userId);
            return ResponseEntity.ok(Map.of("volumenTotal", volumenTotal));
        } catch (Exception e) {
            log.error("Error calculando volumen total para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Obtener volumen operado por símbolo
     * GET /api/transactions/{userId}/volumen-por-simbolo
     */
    @GetMapping("/{userId}/volumen-por-simbolo")
    public ResponseEntity<Map<String, BigDecimal>> obtenerVolumenPorSimbolo(@PathVariable Long userId) {
        log.info("GET /api/transactions/{}/volumen-por-simbolo - Calculando volumen por símbolo", userId);
        
        try {
            Map<String, BigDecimal> volumenPorSimbolo = transactionService.calcularVolumenPorSimbolo(userId);
            return ResponseEntity.ok(volumenPorSimbolo);
        } catch (Exception e) {
            log.error("Error calculando volumen por símbolo para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Obtener actividad de trading por período
     * GET /api/transactions/{userId}/actividad-diaria
     */
    @GetMapping("/{userId}/actividad-diaria")
    public ResponseEntity<?> obtenerActividadDiaria(@PathVariable Long userId,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        log.info("GET /api/transactions/{}/actividad-diaria - Desde: {} Hasta: {}", userId, desde, hasta);
        
        try {
            if (desde.isAfter(hasta)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "La fecha 'desde' no puede ser posterior a 'hasta'"));
            }
            
            List<TransactionService.ActividadDiaria> actividad = 
                    transactionService.obtenerActividadDiaria(userId, desde, hasta);
            
            return ResponseEntity.ok(actividad);
            
        } catch (Exception e) {
            log.error("Error obteniendo actividad diaria para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Obtener top símbolos más operados
     * GET /api/transactions/{userId}/top-simbolos
     */
    @GetMapping("/{userId}/top-simbolos")
    public ResponseEntity<List<TransactionService.SimboloActividad>> obtenerTopSimbolos(@PathVariable Long userId,
                                                                                        @RequestParam(defaultValue = "10") int limite) {
        log.info("GET /api/transactions/{}/top-simbolos - Límite: {}", userId, limite);
        
        try {
            if (limite <= 0 || limite > 50) {
                limite = 10; // Valor por defecto si es inválido
            }
            
            List<TransactionService.SimboloActividad> topSimbolos = 
                    transactionService.obtenerTopSimbolosOperados(userId, limite);
            
            return ResponseEntity.ok(topSimbolos);
            
        } catch (Exception e) {
            log.error("Error obteniendo top símbolos para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Contar total de transacciones
     * GET /api/transactions/{userId}/count
     */
    @GetMapping("/{userId}/count")
    public ResponseEntity<Map<String, Long>> contarTransacciones(@PathVariable Long userId) {
        log.info("GET /api/transactions/{}/count - Contando transacciones", userId);
        
        try {
            long totalTransacciones = transactionService.contarTransacciones(userId);
            return ResponseEntity.ok(Map.of("totalTransacciones", totalTransacciones));
        } catch (Exception e) {
            log.error("Error contando transacciones para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Contar transacciones por tipo
     * GET /api/transactions/{userId}/count/{tipo}
     */
    @GetMapping("/{userId}/count/{tipo}")
    public ResponseEntity<?> contarTransaccionesPorTipo(@PathVariable Long userId, 
                                                       @PathVariable String tipo) {
        log.info("GET /api/transactions/{}/count/{} - Contando por tipo", userId, tipo);
        
        try {
            Transaction.TipoTransaccion tipoTransaccion;
            try {
                tipoTransaccion = Transaction.TipoTransaccion.valueOf(tipo.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Tipo de transacción inválido. Valores válidos: BUY, SELL"));
            }
            
            long count = transactionService.contarTransaccionesPorTipo(userId, tipoTransaccion);
            return ResponseEntity.ok(Map.of("count", count, "tipo", tipo.toUpperCase()));
            
        } catch (Exception e) {
            log.error("Error contando transacciones por tipo para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Verificar si ha operado un símbolo específico
     * GET /api/transactions/{userId}/ha-operado/{symbol}
     */
    @GetMapping("/{userId}/ha-operado/{symbol}")
    public ResponseEntity<Map<String, Object>> haOperadoSimbolo(@PathVariable Long userId, 
                                                                @PathVariable String symbol) {
        log.info("GET /api/transactions/{}/ha-operado/{} - Verificando operaciones", userId, symbol);
        
        try {
            boolean haOperado = transactionService.haOperadoSimbolo(userId, symbol);
            return ResponseEntity.ok(Map.of("haOperado", haOperado, "symbol", symbol.toUpperCase()));
        } catch (Exception e) {
            log.error("Error verificando operaciones de {} para usuario {}: {}", symbol, userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // DTO para requests
    
    public static class RegistrarTransaccionRequest {
        private String symbol;
        private BigDecimal cantidad;
        private BigDecimal precio;
        private Transaction.EjecutadaPor ejecutadaPor;
        
        // Getters y setters
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        
        public BigDecimal getCantidad() { return cantidad; }
        public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
        
        public BigDecimal getPrecio() { return precio; }
        public void setPrecio(BigDecimal precio) { this.precio = precio; }
        
        public Transaction.EjecutadaPor getEjecutadaPor() { return ejecutadaPor; }
        public void setEjecutadaPor(Transaction.EjecutadaPor ejecutadaPor) { this.ejecutadaPor = ejecutadaPor; }
    }
}