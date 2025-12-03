package com.miguel.spyzer.controller;

import com.miguel.spyzer.entities.TradingBot;
import com.miguel.spyzer.service.TradingBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trading-bots")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class TradingBotController {
    
    private final TradingBotService tradingBotService;
    
    /**
     * Crear un nuevo bot de trading
     * POST /api/trading-bots/{userId}
     */
    @PostMapping("/{userId}")
    public ResponseEntity<?> crearBot(@PathVariable Long userId, 
                                     @RequestBody CrearBotRequest request) {
        log.info("POST /api/trading-bots/{} - Creando bot: {} - Estrategia: {} - Budget: ${}", 
                userId, request.getNombre(), request.getEstrategia(), request.getBudget());
        
        try {
            // Validaciones básicas
            if (request.getNombre() == null || request.getNombre().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El nombre del bot es requerido"));
            }
            
            if (request.getEstrategia() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "La estrategia es requerida"));
            }
            
            if (request.getBudget() == null || request.getBudget().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "El budget debe ser mayor a 0"));
            }
            
            if (request.getStopLoss() == null || request.getStopLoss().compareTo(BigDecimal.ZERO) <= 0 || 
                request.getStopLoss().compareTo(new BigDecimal("100")) >= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "El stop loss debe estar entre 0% y 100%"));
            }
            
            TradingBot nuevoBot = tradingBotService.crearBot(
                    userId,
                    request.getNombre(),
                    request.getEstrategia(),
                    request.getParametros() != null ? request.getParametros() : Map.of(),
                    request.getBudget(),
                    request.getStopLoss()
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(nuevoBot);
            
        } catch (IllegalArgumentException e) {
            log.warn("Error de validación creando bot: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creando bot para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Obtener todos los bots de un usuario
     * GET /api/trading-bots/{userId}
     */
    @GetMapping("/{userId}")
    public ResponseEntity<List<TradingBot>> obtenerBots(@PathVariable Long userId) {
        log.info("GET /api/trading-bots/{} - Obteniendo bots del usuario", userId);
        
        try {
            List<TradingBot> bots = tradingBotService.obtenerBotsPorUsuario(userId);
            return ResponseEntity.ok(bots);
        } catch (Exception e) {
            log.error("Error obteniendo bots del usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Obtener solo los bots activos de un usuario
     * GET /api/trading-bots/{userId}/activos
     */
    @GetMapping("/{userId}/activos")
    public ResponseEntity<List<TradingBot>> obtenerBotsActivos(@PathVariable Long userId) {
        log.info("GET /api/trading-bots/{}/activos - Obteniendo bots activos", userId);
        
        try {
            List<TradingBot> botsActivos = tradingBotService.obtenerBotsActivosPorUsuario(userId);
            return ResponseEntity.ok(botsActivos);
        } catch (Exception e) {
            log.error("Error obteniendo bots activos del usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Obtener un bot específico
     * GET /api/trading-bots/{userId}/{botId}
     */
    @GetMapping("/{userId}/{botId}")
    public ResponseEntity<?> obtenerBot(@PathVariable Long userId, @PathVariable Long botId) {
        log.info("GET /api/trading-bots/{}/{} - Obteniendo bot específico", userId, botId);
        
        try {
            return tradingBotService.buscarBotPorId(botId, userId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error obteniendo bot {} del usuario {}: {}", botId, userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Activar/Desactivar un bot
     * PUT /api/trading-bots/{userId}/{botId}/toggle
     */
    @PutMapping("/{userId}/{botId}/toggle")
    public ResponseEntity<?> toggleBot(@PathVariable Long userId, @PathVariable Long botId) {
        log.info("PUT /api/trading-bots/{}/{}/toggle - Cambiando estado del bot", userId, botId);
        
        try {
            TradingBot bot = tradingBotService.toggleBot(botId, userId);
            return ResponseEntity.ok(bot);
            
        } catch (IllegalArgumentException e) {
            log.warn("Bot no encontrado: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.warn("Error de estado del bot: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error cambiando estado del bot {} para usuario {}: {}", botId, userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Actualizar configuración de un bot
     * PUT /api/trading-bots/{userId}/{botId}
     */
    @PutMapping("/{userId}/{botId}")
    public ResponseEntity<?> actualizarBot(@PathVariable Long userId, 
                                          @PathVariable Long botId,
                                          @RequestBody ActualizarBotRequest request) {
        log.info("PUT /api/trading-bots/{}/{} - Actualizando configuración", userId, botId);
        
        try {
            // Validaciones básicas
            if (request.getNombre() == null || request.getNombre().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El nombre del bot es requerido"));
            }
            
            if (request.getBudget() == null || request.getBudget().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "El budget debe ser mayor a 0"));
            }
            
            if (request.getStopLoss() == null || request.getStopLoss().compareTo(BigDecimal.ZERO) <= 0 || 
                request.getStopLoss().compareTo(new BigDecimal("100")) >= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "El stop loss debe estar entre 0% y 100%"));
            }
            
            TradingBot botActualizado = tradingBotService.actualizarConfiguracion(
                    botId,
                    userId,
                    request.getNombre(),
                    request.getParametros() != null ? request.getParametros() : Map.of(),
                    request.getBudget(),
                    request.getStopLoss()
            );
            
            return ResponseEntity.ok(botActualizado);
            
        } catch (IllegalArgumentException e) {
            log.warn("Error de validación actualizando bot: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("Error de estado actualizando bot: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error actualizando bot {} para usuario {}: {}", botId, userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Eliminar un bot
     * DELETE /api/trading-bots/{userId}/{botId}
     */
    @DeleteMapping("/{userId}/{botId}")
    public ResponseEntity<?> eliminarBot(@PathVariable Long userId, @PathVariable Long botId) {
        log.info("DELETE /api/trading-bots/{}/{} - Eliminando bot", userId, botId);
        
        try {
            tradingBotService.eliminarBot(botId, userId);
            return ResponseEntity.ok(Map.of("mensaje", "Bot eliminado correctamente"));
            
        } catch (IllegalArgumentException e) {
            log.warn("Bot no encontrado: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.warn("Error de estado eliminando bot: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error eliminando bot {} para usuario {}: {}", botId, userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Ejecutar trade manualmente (para testing)
     * POST /api/trading-bots/{botId}/ejecutar-trade
     */
    @PostMapping("/{botId}/ejecutar-trade")
    public ResponseEntity<?> ejecutarTrade(@PathVariable Long botId, 
                                          @RequestBody EjecutarTradeRequest request) {
        log.info("POST /api/trading-bots/{}/ejecutar-trade - Ejecutando trade: ${}", botId, request.getGananciaOPerdida());
        
        try {
            if (request.getGananciaOPerdida() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "La ganancia/pérdida es requerida"));
            }
            
            TradingBot bot = tradingBotService.ejecutarTrade(botId, request.getGananciaOPerdida());
            return ResponseEntity.ok(bot);
            
        } catch (IllegalArgumentException e) {
            log.warn("Bot no encontrado: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.warn("Error ejecutando trade: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error ejecutando trade para bot {}: {}", botId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Obtener estadísticas de bots del usuario
     * GET /api/trading-bots/{userId}/estadisticas
     */
    @GetMapping("/{userId}/estadisticas")
    public ResponseEntity<TradingBotService.BotStats> obtenerEstadisticas(@PathVariable Long userId) {
        log.info("GET /api/trading-bots/{}/estadisticas - Obteniendo estadísticas", userId);
        
        try {
            TradingBotService.BotStats stats = tradingBotService.obtenerEstadisticasBots(userId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error obteniendo estadísticas para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Obtener ranking de bots por performance
     * GET /api/trading-bots/{userId}/ranking
     */
    @GetMapping("/{userId}/ranking")
    public ResponseEntity<List<TradingBot>> obtenerRanking(@PathVariable Long userId,
                                                          @RequestParam(defaultValue = "10") int limite) {
        log.info("GET /api/trading-bots/{}/ranking - Límite: {}", userId, limite);
        
        try {
            if (limite <= 0 || limite > 50) {
                limite = 10; // Valor por defecto
            }
            
            List<TradingBot> ranking = tradingBotService.obtenerRankingPorPerformance(userId, limite);
            return ResponseEntity.ok(ranking);
        } catch (Exception e) {
            log.error("Error obteniendo ranking para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Contar bots activos de un usuario
     * GET /api/trading-bots/{userId}/count-activos
     */
    @GetMapping("/{userId}/count-activos")
    public ResponseEntity<Map<String, Long>> contarBotsActivos(@PathVariable Long userId) {
        log.info("GET /api/trading-bots/{}/count-activos - Contando bots activos", userId);
        
        try {
            long count = tradingBotService.contarBotsActivos(userId);
            return ResponseEntity.ok(Map.of("botsActivos", count));
        } catch (Exception e) {
            log.error("Error contando bots activos para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Contar total de bots de un usuario
     * GET /api/trading-bots/{userId}/count-total
     */
    @GetMapping("/{userId}/count-total")
    public ResponseEntity<Map<String, Long>> contarTotalBots(@PathVariable Long userId) {
        log.info("GET /api/trading-bots/{}/count-total - Contando total de bots", userId);
        
        try {
            long count = tradingBotService.contarTotalBots(userId);
            return ResponseEntity.ok(Map.of("totalBots", count));
        } catch (Exception e) {
            log.error("Error contando total de bots para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Pausar todos los bots de un usuario (emergencia)
     * POST /api/trading-bots/{userId}/pausar-todos
     */
    @PostMapping("/{userId}/pausar-todos")
    public ResponseEntity<Map<String, String>> pausarTodosLosBots(@PathVariable Long userId) {
        log.warn("POST /api/trading-bots/{}/pausar-todos - PAUSANDO TODOS LOS BOTS", userId);
        
        try {
            tradingBotService.pausarTodosLosBots(userId);
            return ResponseEntity.ok(Map.of("mensaje", "Todos los bots han sido pausados exitosamente"));
        } catch (Exception e) {
            log.error("Error pausando todos los bots para usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Resetear contadores diarios (administración)
     * POST /api/trading-bots/admin/reset-contadores
     */
    @PostMapping("/admin/reset-contadores")
    public ResponseEntity<Map<String, String>> resetearContadores() {
        log.info("POST /api/trading-bots/admin/reset-contadores - Reseteando contadores diarios");
        
        try {
            tradingBotService.resetearContadoresDiarios();
            return ResponseEntity.ok(Map.of("mensaje", "Contadores diarios reseteados exitosamente"));
        } catch (Exception e) {
            log.error("Error reseteando contadores diarios: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    // DTOs para requests
    
    public static class CrearBotRequest {
        private String nombre;
        private TradingBot.Estrategia estrategia;
        private Map<String, Object> parametros;
        private BigDecimal budget;
        private BigDecimal stopLoss;
        
        // Getters y setters
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        
        public TradingBot.Estrategia getEstrategia() { return estrategia; }
        public void setEstrategia(TradingBot.Estrategia estrategia) { this.estrategia = estrategia; }
        
        public Map<String, Object> getParametros() { return parametros; }
        public void setParametros(Map<String, Object> parametros) { this.parametros = parametros; }
        
        public BigDecimal getBudget() { return budget; }
        public void setBudget(BigDecimal budget) { this.budget = budget; }
        
        public BigDecimal getStopLoss() { return stopLoss; }
        public void setStopLoss(BigDecimal stopLoss) { this.stopLoss = stopLoss; }
    }
    
    public static class ActualizarBotRequest {
        private String nombre;
        private Map<String, Object> parametros;
        private BigDecimal budget;
        private BigDecimal stopLoss;
        
        // Getters y setters
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        
        public Map<String, Object> getParametros() { return parametros; }
        public void setParametros(Map<String, Object> parametros) { this.parametros = parametros; }
        
        public BigDecimal getBudget() { return budget; }
        public void setBudget(BigDecimal budget) { this.budget = budget; }
        
        public BigDecimal getStopLoss() { return stopLoss; }
        public void setStopLoss(BigDecimal stopLoss) { this.stopLoss = stopLoss; }
    }
    
    public static class EjecutarTradeRequest {
        private BigDecimal gananciaOPerdida;
        
        public BigDecimal getGananciaOPerdida() { return gananciaOPerdida; }
        public void setGananciaOPerdida(BigDecimal gananciaOPerdida) { this.gananciaOPerdida = gananciaOPerdida; }
    }
}