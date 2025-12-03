package com.miguel.spyzer.controller;

import com.miguel.spyzer.entities.Alert;
import com.miguel.spyzer.service.AlertService;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.EntityNotFoundException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@Slf4j
public class AlertController {
    
    private final AlertService alertService;
    
    /**
     * Crear una nueva alerta
     * POST /api/alerts/{userId}
     */
    @PostMapping("/{userId}")
    public ResponseEntity<?> crearAlerta(
            @PathVariable Long userId, 
            @RequestBody CrearAlertaRequest request) {
        
        log.info("POST /api/alerts/{} - Creando alerta {} para {} a ${}", 
                userId, request.getTipo(), request.getSymbol(), request.getValorTrigger());
        
        try {
            Alert nuevaAlerta = alertService.crearAlerta(
                    userId, 
                    request.getSymbol(), 
                    request.getTipo(), 
                    request.getValorTrigger(), 
                    request.getMensajePersonalizado()
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(AlertDTO.fromEntity(nuevaAlerta));
            
        } catch (EntityNotFoundException e) {
            log.warn("Usuario no encontrado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Validación fallida: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creando alerta para usuario {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Obtener todas las alertas de un usuario
     * GET /api/alerts/{userId}
     */
    @GetMapping("/{userId}")
    public ResponseEntity<?> obtenerAlertas(@PathVariable Long userId) {
        log.info("GET /api/alerts/{} - Obteniendo alertas del usuario", userId);
        
        try {
            List<Alert> alertas = alertService.obtenerAlertasPorUsuario(userId);
            List<AlertDTO> alertasDTO = alertas.stream()
                    .map(AlertDTO::fromEntity)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(alertasDTO);
            
        } catch (EntityNotFoundException e) {
            log.warn("Usuario no encontrado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error obteniendo alertas del usuario {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Obtener alertas de un usuario con paginación
     * GET /api/alerts/{userId}/paginadas?page=0&size=10
     */
    @GetMapping("/{userId}/paginadas")
    public ResponseEntity<?> obtenerAlertasPaginadas(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        
        log.info("GET /api/alerts/{}/paginadas - página {}, tamaño {}", userId, page, size);
        
        try {
            Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC") 
                    ? Sort.Direction.ASC 
                    : Sort.Direction.DESC;
            
            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
            Page<Alert> alertas = alertService.obtenerAlertasPorUsuario(userId, pageable);
            Page<AlertDTO> alertasDTO = alertas.map(AlertDTO::fromEntity);
            
            return ResponseEntity.ok(alertasDTO);
            
        } catch (EntityNotFoundException e) {
            log.warn("Usuario no encontrado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error obteniendo alertas paginadas del usuario {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Obtener solo las alertas activas de un usuario
     * GET /api/alerts/{userId}/activas
     */
    @GetMapping("/{userId}/activas")
    public ResponseEntity<?> obtenerAlertasActivas(@PathVariable Long userId) {
        log.info("GET /api/alerts/{}/activas - Obteniendo alertas activas", userId);
        
        try {
            List<Alert> alertasActivas = alertService.obtenerAlertasActivasPorUsuario(userId);
            List<AlertDTO> alertasDTO = alertasActivas.stream()
                    .map(AlertDTO::fromEntity)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(alertasDTO);
            
        } catch (EntityNotFoundException e) {
            log.warn("Usuario no encontrado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error obteniendo alertas activas del usuario {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Obtener una alerta específica
     * GET /api/alerts/{userId}/{alertaId}
     */
    @GetMapping("/{userId}/{alertaId}")
    public ResponseEntity<?> obtenerAlerta(
            @PathVariable Long userId, 
            @PathVariable Long alertaId) {
        
        log.info("GET /api/alerts/{}/{} - Obteniendo alerta", userId, alertaId);
        
        try {
            Alert alerta = alertService.obtenerAlertaPorId(alertaId, userId);
            return ResponseEntity.ok(AlertDTO.fromEntity(alerta));
            
        } catch (EntityNotFoundException e) {
            log.warn("Alerta no encontrada: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            log.warn("Acceso no autorizado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error obteniendo alerta {} para usuario {}: {}", alertaId, userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Activar/Desactivar una alerta
     * PATCH /api/alerts/{userId}/{alertaId}/toggle
     */
    @PatchMapping("/{userId}/{alertaId}/toggle")
    public ResponseEntity<?> toggleAlerta(
            @PathVariable Long userId, 
            @PathVariable Long alertaId) {
        
        log.info("PATCH /api/alerts/{}/{}/toggle - Cambiando estado de alerta", userId, alertaId);
        
        try {
            Alert alerta = alertService.toggleAlerta(alertaId, userId);
            return ResponseEntity.ok(AlertDTO.fromEntity(alerta));
            
        } catch (EntityNotFoundException e) {
            log.warn("Alerta no encontrada: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            log.warn("Acceso no autorizado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error cambiando estado de alerta {} para usuario {}: {}", alertaId, userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Reactivar una alerta ya disparada
     * PATCH /api/alerts/{userId}/{alertaId}/reactivar
     */
    @PatchMapping("/{userId}/{alertaId}/reactivar")
    public ResponseEntity<?> reactivarAlerta(
            @PathVariable Long userId, 
            @PathVariable Long alertaId) {
        
        log.info("PATCH /api/alerts/{}/{}/reactivar - Reactivando alerta", userId, alertaId);
        
        try {
            Alert alerta = alertService.reactivarAlerta(alertaId, userId);
            return ResponseEntity.ok(AlertDTO.fromEntity(alerta));
            
        } catch (EntityNotFoundException e) {
            log.warn("Alerta no encontrada: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            log.warn("Acceso no autorizado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("Estado inválido: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error reactivando alerta {} para usuario {}: {}", alertaId, userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Eliminar una alerta
     * DELETE /api/alerts/{userId}/{alertaId}
     */
    @DeleteMapping("/{userId}/{alertaId}")
    public ResponseEntity<?> eliminarAlerta(
            @PathVariable Long userId, 
            @PathVariable Long alertaId) {
        
        log.info("DELETE /api/alerts/{}/{} - Eliminando alerta", userId, alertaId);
        
        try {
            alertService.eliminarAlerta(alertaId, userId);
            return ResponseEntity.ok(Map.of("mensaje", "Alerta eliminada correctamente"));
            
        } catch (EntityNotFoundException e) {
            log.warn("Alerta no encontrada: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            log.warn("Acceso no autorizado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error eliminando alerta {} para usuario {}: {}", alertaId, userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Actualizar una alerta completa
     * PUT /api/alerts/{userId}/{alertaId}
     */
    @PutMapping("/{userId}/{alertaId}")
    public ResponseEntity<?> actualizarAlerta(
            @PathVariable Long userId,
            @PathVariable Long alertaId,
            @RequestBody ActualizarAlertaRequest request) {
        
        log.info("PUT /api/alerts/{}/{} - Actualizando alerta", userId, alertaId);
        
        try {
            Alert alerta = alertService.actualizarAlerta(
                    alertaId, 
                    userId, 
                    request.getTipo(), 
                    request.getValorTrigger(), 
                    request.getMensajePersonalizado()
            );
            
            return ResponseEntity.ok(AlertDTO.fromEntity(alerta));
            
        } catch (EntityNotFoundException e) {
            log.warn("Alerta no encontrada: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            log.warn("Acceso no autorizado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Validación fallida: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error actualizando alerta {} para usuario {}: {}", alertaId, userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Actualizar mensaje personalizado de una alerta
     * PATCH /api/alerts/{userId}/{alertaId}/mensaje
     */
    @PatchMapping("/{userId}/{alertaId}/mensaje")
    public ResponseEntity<?> actualizarMensaje(
            @PathVariable Long userId, 
            @PathVariable Long alertaId,
            @RequestBody ActualizarMensajeRequest request) {
        
        log.info("PATCH /api/alerts/{}/{}/mensaje - Actualizando mensaje", userId, alertaId);
        
        try {
            Alert alerta = alertService.actualizarMensajeAlerta(alertaId, userId, request.getNuevoMensaje());
            return ResponseEntity.ok(AlertDTO.fromEntity(alerta));
            
        } catch (EntityNotFoundException e) {
            log.warn("Alerta no encontrada: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            log.warn("Acceso no autorizado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error actualizando mensaje de alerta {} para usuario {}: {}", alertaId, userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Actualizar valor trigger de una alerta
     * PATCH /api/alerts/{userId}/{alertaId}/trigger
     */
    @PatchMapping("/{userId}/{alertaId}/trigger")
    public ResponseEntity<?> actualizarTrigger(
            @PathVariable Long userId, 
            @PathVariable Long alertaId,
            @RequestBody ActualizarTriggerRequest request) {
        
        log.info("PATCH /api/alerts/{}/{}/trigger - Actualizando trigger a ${}", userId, alertaId, request.getNuevoValor());
        
        try {
            Alert alerta = alertService.actualizarValorTrigger(alertaId, userId, request.getNuevoValor());
            return ResponseEntity.ok(AlertDTO.fromEntity(alerta));
            
        } catch (EntityNotFoundException e) {
            log.warn("Alerta no encontrada: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            log.warn("Acceso no autorizado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Validación fallida: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error actualizando trigger de alerta {} para usuario {}: {}", alertaId, userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Obtener estadísticas de alertas del usuario
     * GET /api/alerts/{userId}/estadisticas
     */
    @GetMapping("/{userId}/estadisticas")
    public ResponseEntity<?> obtenerEstadisticas(@PathVariable Long userId) {
        log.info("GET /api/alerts/{}/estadisticas - Obteniendo estadísticas", userId);
        
        try {
            AlertService.AlertStats stats = alertService.obtenerEstadisticasUsuario(userId);
            return ResponseEntity.ok(stats);
            
        } catch (EntityNotFoundException e) {
            log.warn("Usuario no encontrado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error obteniendo estadísticas para usuario {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Contar alertas activas de un usuario
     * GET /api/alerts/{userId}/count-activas
     */
    @GetMapping("/{userId}/count-activas")
    public ResponseEntity<?> contarAlertasActivas(@PathVariable Long userId) {
        log.info("GET /api/alerts/{}/count-activas - Contando alertas activas", userId);
        
        try {
            long count = alertService.contarAlertasActivas(userId);
            return ResponseEntity.ok(Map.of("alertasActivas", count));
            
        } catch (EntityNotFoundException e) {
            log.warn("Usuario no encontrado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error contando alertas activas para usuario {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Verificar alertas manualmente para un símbolo (endpoint de utilidad)
     * POST /api/alerts/verificar/{symbol}
     */
    @PostMapping("/verificar/{symbol}")
    public ResponseEntity<?> verificarAlertas(
            @PathVariable String symbol, 
            @RequestBody VerificarAlertasRequest request) {
        
        log.info("POST /api/alerts/verificar/{} - Verificando alertas con precio ${}", symbol, request.getPrecioActual());
        
        try {
            List<Alert> alertasDisparadas = alertService.verificarYDispararAlertas(symbol, request.getPrecioActual());
            List<AlertDTO> alertasDTO = alertasDisparadas.stream()
                    .map(AlertDTO::fromEntity)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(Map.of(
                "alertasDisparadas", alertasDTO.size(),
                "alertas", alertasDTO
            ));
            
        } catch (IllegalArgumentException e) {
            log.warn("Validación fallida: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error verificando alertas para {}: {}", symbol, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Limpiar alertas disparadas antiguas (endpoint de mantenimiento)
     * DELETE /api/alerts/mantenimiento/limpiar?dias=30
     */
    @DeleteMapping("/mantenimiento/limpiar")
    public ResponseEntity<?> limpiarAlertasAntiguas(
            @RequestParam(defaultValue = "30") int dias) {
        
        log.info("DELETE /api/alerts/mantenimiento/limpiar - Limpiando alertas de más de {} días", dias);
        
        try {
            int cantidadEliminada = alertService.limpiarAlertasDisparadasAntiguas(dias);
            
            return ResponseEntity.ok(Map.of(
                "mensaje", "Limpieza completada",
                "alertasEliminadas", cantidadEliminada
            ));
            
        } catch (IllegalArgumentException e) {
            log.warn("Validación fallida: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error limpiando alertas antiguas: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    /**
     * Limpiar alertas disparadas de un usuario específico
     * DELETE /api/alerts/{userId}/disparadas
     */
    @DeleteMapping("/{userId}/disparadas")
    public ResponseEntity<?> limpiarAlertasDisparadasUsuario(@PathVariable Long userId) {
        log.info("DELETE /api/alerts/{}/disparadas - Limpiando alertas disparadas del usuario", userId);
        
        try {
            int cantidadEliminada = alertService.limpiarAlertasDisparadasDeUsuario(userId);
            
            return ResponseEntity.ok(Map.of(
                "mensaje", "Alertas disparadas eliminadas",
                "alertasEliminadas", cantidadEliminada
            ));
            
        } catch (EntityNotFoundException e) {
            log.warn("Usuario no encontrado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error limpiando alertas disparadas del usuario {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error interno del servidor"));
        }
    }
    
    // ========== DTO PARA RESPONSE ==========
    
    @Data
    public static class AlertDTO {
        private Long id;
        private String symbol;
        @JsonProperty("tipo")
        private String tipoAlerta;
        private BigDecimal valorTrigger;
        private String mensajePersonalizado;
        private Boolean activa;
        private Boolean disparada;
        private LocalDateTime createdAt;
        private LocalDateTime triggeredAt;
        
        public static AlertDTO fromEntity(Alert alert) {
            AlertDTO dto = new AlertDTO();
            dto.setId(alert.getId());
            dto.setSymbol(alert.getSymbol());
            dto.setTipoAlerta(alert.getTipo().name());
            dto.setValorTrigger(alert.getValorTrigger());
            dto.setMensajePersonalizado(alert.getMensajePersonalizado());
            dto.setActiva(alert.getActiva());
            dto.setDisparada(alert.getDisparada());
            dto.setCreatedAt(alert.getCreatedAt());
            dto.setTriggeredAt(alert.getTriggeredAt());
            return dto;
        }
    }
    
    // ========== DTOs PARA REQUESTS ==========
    
    @Data
    public static class CrearAlertaRequest {
        private String symbol;
        private Alert.TipoAlerta tipo;
        private BigDecimal valorTrigger;
        private String mensajePersonalizado;
    }
    
    @Data
    public static class ActualizarAlertaRequest {
        private Alert.TipoAlerta tipo;
        private BigDecimal valorTrigger;
        private String mensajePersonalizado;
    }
    
    @Data
    public static class ActualizarMensajeRequest {
        private String nuevoMensaje;
    }
    
    @Data
    public static class ActualizarTriggerRequest {
        private BigDecimal nuevoValor;
    }
    
    @Data
    public static class VerificarAlertasRequest {
        private BigDecimal precioActual;
    }
}