package com.miguel.spyzer.controller;

import com.miguel.spyzer.entities.MarketData;
import com.miguel.spyzer.entities.HistoricalDataPoint;
import com.miguel.spyzer.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market-data")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class MarketDataController {
    
    private final MarketDataService marketDataService;
    
    /**
     * Obtener datos de un símbolo específico
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<?> obtenerDatos(@PathVariable String symbol) {
        log.info("GET /api/market-data/{}", symbol.toUpperCase());
        
        try {
            if (symbol == null || symbol.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El símbolo es requerido"));
            }
            
            MarketData datos = marketDataService.obtenerDatos(symbol);
            
            if (datos != null) {
                return ResponseEntity.ok(datos);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Error obteniendo datos para {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Obtener datos de múltiples símbolos
     */
    @PostMapping("/multiple")
    public ResponseEntity<?> obtenerMultiplesDatos(@RequestBody MultipleSymbolsRequest request) {
        log.info("POST /api/market-data/multiple - {} símbolos", 
                request.getSymbols() != null ? request.getSymbols().length : 0);
        
        try {
            if (request.getSymbols() == null || request.getSymbols().length == 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Se requiere al menos un símbolo"));
            }
            
            if (request.getSymbols().length > 200) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Máximo 50 símbolos por solicitud"));
            }
            
            Map<String, MarketData> resultados = marketDataService.obtenerMultiplesDatos(request.getSymbols());
            
            return ResponseEntity.ok(Map.of(
                    "total", resultados.size(),
                    "solicitados", request.getSymbols().length,
                    "datos", resultados
            ));
            
        } catch (Exception e) {
            log.error("Error obteniendo múltiples datos: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Obtener todos los índices principales
     */
    @GetMapping("/indices")
    public ResponseEntity<Map<String, MarketData>> obtenerIndicesPrincipales() {
        log.info("GET /api/market-data/indices");
        
        try {
            Map<String, MarketData> indices = marketDataService.obtenerIndicesPrincipales();
            return ResponseEntity.ok(indices);
        } catch (Exception e) {
            log.error("Error obteniendo índices principales: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Obtener datos históricos de un símbolo
     */
    @GetMapping("/{symbol}/historical")
    public ResponseEntity<?> obtenerHistorico(@PathVariable String symbol,
                                               @RequestParam(defaultValue = "730") int days) {
        log.info("GET /api/market-data/{}/historical?days={}", symbol.toUpperCase(), days);
        
        try {
            List<HistoricalDataPoint> historical = marketDataService.obtenerHistorico(symbol, days);
            
            return ResponseEntity.ok(Map.of(
                    "symbol", symbol.toUpperCase(),
                    "days", days,
                    "data", historical
            ));
            
        } catch (Exception e) {
            log.error("Error obteniendo histórico de {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Obtener cotización rápida (solo precio)
     */
    @GetMapping("/{symbol}/precio")
    public ResponseEntity<?> obtenerPrecio(@PathVariable String symbol) {
        log.info("GET /api/market-data/{}/precio", symbol.toUpperCase());
        
        try {
            MarketData datos = marketDataService.obtenerDatos(symbol);
            
            if (datos != null) {
                return ResponseEntity.ok(Map.of("symbol", datos.getSymbol(),
                        "precio", datos.getPrecio(),
                        "variacionAbsoluta", datos.getVariacionAbsoluta(),
                        "variacionPorcentual", datos.getVariacionPorcentual(),
                        "timestamp", datos.getTimestamp()
                ));
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Error obteniendo precio de {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Verificar si un símbolo está disponible
     */
    @GetMapping("/disponible/{symbol}")
    public ResponseEntity<Map<String, Object>> verificarDisponibilidad(@PathVariable String symbol) {
        log.info("GET /api/market-data/disponible/{}", symbol.toUpperCase());
        
        try {
            boolean disponible = marketDataService.estaDisponible(symbol);
            return ResponseEntity.ok(Map.of(
                    "symbol", symbol.toUpperCase(),
                    "disponible", disponible
            ));
        } catch (Exception e) {
            log.error("Error verificando disponibilidad de {}: {}", symbol, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Obtener lista de todos los símbolos disponibles
     */
    @GetMapping("/simbolos-disponibles")
    public ResponseEntity<?> obtenerSimbolosDisponibles(@RequestParam(defaultValue = "all") String tipo) {
        log.info("GET /api/market-data/simbolos-disponibles?tipo={}", tipo);
        
        try {
            List<String> simbolos = marketDataService.obtenerSimbolosDisponibles();
            
            List<String> simbolosFiltrados = switch (tipo.toLowerCase()) {
                case "indices" -> simbolos.stream()
                        .filter(s -> List.of("SPX", "IXIC", "VIX", "IBEX").contains(s))
                        .toList();
                case "stocks" -> simbolos.stream()
                        .filter(s -> !List.of("SPX", "IXIC", "VIX", "IBEX").contains(s))
                        .toList();
                case "etfs" -> simbolos.stream()
                        .filter(s -> List.of("SPY", "QQQ", "IWM", "DIA", "VTI", "XLF", "XLK", "XLE", "XLV", "XLI").contains(s))
                        .toList();
                default -> simbolos;
            };
            
            return ResponseEntity.ok(Map.of(
                    "total", simbolosFiltrados.size(),
                    "tipo", tipo,
                    "simbolos", simbolosFiltrados
            ));
            
        } catch (Exception e) {
            log.error("Error obteniendo símbolos disponibles: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Búsqueda de símbolos por texto
     */
    @GetMapping("/buscar")
    public ResponseEntity<?> buscarSimbolos(@RequestParam String q, 
                                           @RequestParam(defaultValue = "10") int limite) {
        log.info("GET /api/market-data/buscar?q={}&limite={}", q, limite);
        
        try {
            if (q == null || q.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El parámetro 'q' es requerido"));
            }
            
            if (limite <= 0 || limite > 50) {
                limite = 10;
            }
            
            List<String> todosLosSimbolos = marketDataService.obtenerSimbolosDisponibles();
            String query = q.toUpperCase().trim();
            
            List<String> resultados = todosLosSimbolos.stream()
                    .filter(symbol -> symbol.contains(query))
                    .limit(limite)
                    .toList();
            
            return ResponseEntity.ok(Map.of(
                    "query", query,
                    "total", resultados.size(),
                    "limite", limite,
                    "resultados", resultados
            ));
            
        } catch (Exception e) {
            log.error("Error buscando símbolos: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Obtener resumen del mercado (índices principales)
     */
    @GetMapping("/resumen-mercado")
    public ResponseEntity<?> obtenerResumenMercado() {
        log.info("GET /api/market-data/resumen-mercado");
        
        try {
            Map<String, MarketData> indices = marketDataService.obtenerIndicesPrincipales();
            
            return ResponseEntity.ok(Map.of(
                    "mercado", "US Markets + IBEX",
                    "indices", indices,
                    "total", indices.size(),
                    "ultimaActualizacion", java.time.LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Error obteniendo resumen del mercado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Obtener estadísticas de disponibilidad de datos
     */
    @GetMapping("/estadisticas")
    public ResponseEntity<?> obtenerEstadisticas() {
        log.info("GET /api/market-data/estadisticas");
        
        try {
            List<String> simbolosDisponibles = marketDataService.obtenerSimbolosDisponibles();
            
            long datosDisponibles = 0;
            for (String symbol : simbolosDisponibles) {
                if (marketDataService.obtenerDatos(symbol) != null) {
                    datosDisponibles++;
                }
            }
            
            double porcentajeCobertura = simbolosDisponibles.isEmpty() ? 0.0 : 
                    (datosDisponibles * 100.0) / simbolosDisponibles.size();
            
            return ResponseEntity.ok(Map.of(
                    "simbolosTotales", simbolosDisponibles.size(),
                    "datosDisponibles", datosDisponibles,
                    "porcentajeCobertura", Math.round(porcentajeCobertura * 100.0) / 100.0,
                    "indices", 4,
                    "acciones", simbolosDisponibles.size() - 4,
                    "ultimaVerificacion", java.time.LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("Error obteniendo estadísticas: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Verificar el estado de salud del servicio
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> verificarSalud() {
        log.info("GET /api/market-data/health");
        
        try {
            MarketData spx = marketDataService.obtenerDatos("SPX");
            MarketData aapl = marketDataService.obtenerDatos("AAPL");
            
            boolean healthy = spx != null && aapl != null;
            
            return ResponseEntity.ok(Map.of(
                    "status", healthy ? "UP" : "DOWN",
                    "timestamp", java.time.LocalDateTime.now(),
                    "checks", Map.of(
                            "indicesDisponibles", spx != null,
                            "accionesDisponibles", aapl != null
                    )
            ));
            
        } catch (Exception e) {
            log.error("Error verificando salud del servicio: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "status", "DOWN",
                            "error", e.getMessage(),
                            "timestamp", java.time.LocalDateTime.now()
                    ));
        }
    }
    
    // DTO para request
    public static class MultipleSymbolsRequest {
        private String[] symbols;
        
        public String[] getSymbols() { return symbols; }
        public void setSymbols(String[] symbols) { this.symbols = symbols; }
    }
}