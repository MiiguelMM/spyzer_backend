package com.miguel.spyzer.service;

import com.miguel.spyzer.entities.Alert;
import com.miguel.spyzer.entities.User;
import jakarta.persistence.EntityNotFoundException;
import com.miguel.spyzer.repository.AlertRepository;
import com.miguel.spyzer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AlertService {
    
    private final AlertRepository alertRepository;
    private final UserRepository userRepository;
    
    /**
     * Crear una nueva alerta
     */
    public Alert crearAlerta(Long userId, String symbol, Alert.TipoAlerta tipo, 
                           BigDecimal valorTrigger, String mensajePersonalizado) {
        
        // Validaciones de parámetros
        validarParametrosAlerta(symbol, valorTrigger, tipo);
        
        // Obtener usuario real desde la base de datos
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con ID: " + userId));
        
        log.info("Creando nueva alerta para usuario {} - {} {} ${}", 
                userId, symbol, tipo, valorTrigger);
        
        Alert nuevaAlerta = Alert.builder()
                .user(user)
                .symbol(symbol.toUpperCase().trim())
                .tipo(tipo)
                .valorTrigger(valorTrigger)
                .mensajePersonalizado(mensajePersonalizado)
                .activa(true)
                .disparada(false)
                .createdAt(LocalDateTime.now())
                .build();
        
        Alert alertaGuardada = alertRepository.save(nuevaAlerta);
        log.info("Alerta creada con ID: {}", alertaGuardada.getId());
        
        return alertaGuardada;
    }
    
    /**
     * Obtener todas las alertas de un usuario (con paginación)
     */
    @Transactional(readOnly = true)
    public Page<Alert> obtenerAlertasPorUsuario(Long userId, Pageable pageable) {
        log.info("Obteniendo alertas del usuario: {} (página {})", userId, pageable.getPageNumber());
        validarUsuarioExiste(userId);
        return alertRepository.findByUserId(userId, pageable);
    }
    
    /**
     * Obtener todas las alertas de un usuario (sin paginación)
     */
    @Transactional(readOnly = true)
    public List<Alert> obtenerAlertasPorUsuario(Long userId) {
        log.info("Obteniendo todas las alertas del usuario: {}", userId);
        validarUsuarioExiste(userId);
        return alertRepository.findByUserId(userId);
    }
    
    /**
     * Obtener solo las alertas activas de un usuario
     */
    @Transactional(readOnly = true)
    public List<Alert> obtenerAlertasActivasPorUsuario(Long userId) {
        log.info("Obteniendo alertas activas del usuario: {}", userId);
        validarUsuarioExiste(userId);
        return alertRepository.findByUserIdAndActivaTrue(userId);
    }
    
    /**
     * Obtener alertas activas por símbolo (para verificaciones de precio)
     */
    @Transactional(readOnly = true)
    public List<Alert> obtenerAlertasActivasPorSimbolo(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("El símbolo no puede estar vacío");
        }
        return alertRepository.findBySymbolAndActivaTrueAndDisparadaFalse(symbol.toUpperCase().trim());
    }
    
    /**
     * Obtener todas las alertas activas del sistema (para job automático)
     */
    @Transactional(readOnly = true)
    public List<Alert> obtenerTodasLasAlertasActivas() {
        return alertRepository.findByActivaTrueAndDisparadaFalse();
    }
    
    /**
     * Activar/Desactivar una alerta
     */
    public Alert toggleAlerta(Long alertaId, Long userId) {
        log.info("Cambiando estado de alerta {} para usuario {}", alertaId, userId);
        
        Alert alerta = buscarAlertaPorIdYUsuario(alertaId, userId);
        alerta.setActiva(!alerta.getActiva());
        
        Alert alertaActualizada = alertRepository.save(alerta);
        log.info("Alerta {} ahora está: {}", alertaId, alertaActualizada.getActiva() ? "ACTIVA" : "INACTIVA");
        
        return alertaActualizada;
    }
    
    /**
     * Reactivar una alerta ya disparada
     */
    public Alert reactivarAlerta(Long alertaId, Long userId) {
        log.info("Reactivando alerta {} para usuario {}", alertaId, userId);
        
        Alert alerta = buscarAlertaPorIdYUsuario(alertaId, userId);
        
        if (!alerta.getDisparada()) {
            throw new IllegalStateException("La alerta no está disparada, no se puede reactivar");
        }
        
        alerta.reactivar();
        
        Alert alertaReactivada = alertRepository.save(alerta);
        log.info("Alerta {} reactivada exitosamente", alertaId);
        
        return alertaReactivada;
    }
    
    /**
     * Eliminar una alerta
     */
    public void eliminarAlerta(Long alertaId, Long userId) {
        log.info("Eliminando alerta {} del usuario {}", alertaId, userId);
        
        Alert alerta = buscarAlertaPorIdYUsuario(alertaId, userId);
        alertRepository.delete(alerta);
        
        log.info("Alerta {} eliminada exitosamente", alertaId);
    }
    
    /**
     * Verificar si alguna alerta debe dispararse con el precio actual
     * Este método es llamado cuando llegan nuevos datos de precios
     */
    public List<Alert> verificarYDispararAlertas(String symbol, BigDecimal precioActual) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("El símbolo no puede estar vacío");
        }
        if (precioActual == null || precioActual.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El precio actual debe ser positivo");
        }
        
        log.debug("Verificando alertas para {} a precio ${}", symbol, precioActual);
        
        List<Alert> alertasActivas = obtenerAlertasActivasPorSimbolo(symbol);
        List<Alert> alertasDisparadas = alertasActivas.stream()
                .filter(alerta -> alerta.debeDispararseConPrecio(precioActual))
                .map(alerta -> {
                    alerta.disparar();
                    return alertRepository.save(alerta);
                })
                .toList();
        
        if (!alertasDisparadas.isEmpty()) {
            log.info("Se dispararon {} alertas para {}", alertasDisparadas.size(), symbol);
        }
        
        return alertasDisparadas;
    }
    
    /**
     * Verificar todas las alertas activas del sistema (para job programado)
     * Requiere un mapa de precios actuales: symbol -> precio
     */
    public List<Alert> verificarTodasLasAlertas(java.util.Map<String, BigDecimal> preciosActuales) {
        if (preciosActuales == null || preciosActuales.isEmpty()) {
            log.warn("No hay precios disponibles para verificar alertas");
            return List.of();
        }
        
        log.info("Ejecutando verificación masiva de alertas. Precios disponibles: {}", 
                preciosActuales.size());
        
        List<Alert> todasLasAlertas = obtenerTodasLasAlertasActivas();
        
        List<Alert> alertasDisparadas = todasLasAlertas.stream()
                .filter(alerta -> {
                    BigDecimal precioActual = preciosActuales.get(alerta.getSymbol());
                    return precioActual != null && alerta.debeDispararseConPrecio(precioActual);
                })
                .map(alerta -> {
                    alerta.disparar();
                    return alertRepository.save(alerta);
                })
                .toList();
        
        log.info("Verificación masiva completada. Alertas disparadas: {}/{}", 
                alertasDisparadas.size(), todasLasAlertas.size());
        
        return alertasDisparadas;
    }
    
    /**
     * Contar alertas activas de un usuario
     */
    @Transactional(readOnly = true)
    public long contarAlertasActivas(Long userId) {
        validarUsuarioExiste(userId);
        return alertRepository.countByUserIdAndActivaTrue(userId);
    }
    
    /**
     * Limpiar alertas disparadas antiguas (mantenimiento)
     * Solo elimina alertas disparadas hace más de X días
     */
    public int limpiarAlertasDisparadasAntiguas(int diasAntiguedad) {
        if (diasAntiguedad < 1) {
            throw new IllegalArgumentException("Los días de antigüedad deben ser al menos 1");
        }
        
        LocalDateTime fechaLimite = LocalDateTime.now().minusDays(diasAntiguedad);
        log.info("Limpiando alertas disparadas antes de: {}", fechaLimite);
        
        List<Alert> alertasAntiguas = alertRepository.findByDisparadaTrueAndTriggeredAtBefore(fechaLimite);
        int cantidadEliminada = alertasAntiguas.size();
        
        if (cantidadEliminada > 0) {
            alertRepository.deleteAll(alertasAntiguas);
            log.info("Limpieza completada. Alertas eliminadas: {}", cantidadEliminada);
        } else {
            log.info("No hay alertas antiguas para limpiar");
        }
        
        return cantidadEliminada;
    }
    
    /**
     * Limpiar todas las alertas disparadas de un usuario específico
     */
    public int limpiarAlertasDisparadasDeUsuario(Long userId) {
        validarUsuarioExiste(userId);
        log.info("Limpiando alertas disparadas del usuario: {}", userId);
        
        List<Alert> alertasDisparadas = alertRepository.findByUserIdAndDisparadaTrue(userId);
        int cantidadEliminada = alertasDisparadas.size();
        
        if (cantidadEliminada > 0) {
            alertRepository.deleteAll(alertasDisparadas);
            log.info("Alertas disparadas del usuario {} eliminadas: {}", userId, cantidadEliminada);
        }
        
        return cantidadEliminada;
    }
    
    /**
     * Actualizar mensaje personalizado de una alerta
     */
    public Alert actualizarMensajeAlerta(Long alertaId, Long userId, String nuevoMensaje) {
        log.info("Actualizando mensaje de alerta {} para usuario {}", alertaId, userId);
        
        Alert alerta = buscarAlertaPorIdYUsuario(alertaId, userId);
        alerta.setMensajePersonalizado(nuevoMensaje);
        
        return alertRepository.save(alerta);
    }
    
    /**
     * Actualizar valor trigger de una alerta
     */
    public Alert actualizarValorTrigger(Long alertaId, Long userId, BigDecimal nuevoValor) {
        if (nuevoValor == null || nuevoValor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El valor trigger debe ser positivo");
        }
        
        log.info("Actualizando valor trigger de alerta {} para usuario {} - nuevo valor: ${}", 
                alertaId, userId, nuevoValor);
        
        Alert alerta = buscarAlertaPorIdYUsuario(alertaId, userId);
        alerta.setValorTrigger(nuevoValor);
        
        // Si la alerta estaba disparada, la reactivamos al cambiar el trigger
        if (alerta.getDisparada()) {
            log.info("Alerta {} estaba disparada, reactivando automáticamente", alertaId);
            alerta.reactivar();
        }
        
        return alertRepository.save(alerta);
    }
    
    /**
     * Actualizar una alerta completa (tipo y valor trigger)
     */
    public Alert actualizarAlerta(Long alertaId, Long userId, Alert.TipoAlerta nuevoTipo, 
                                 BigDecimal nuevoValor, String nuevoMensaje) {
        if (nuevoTipo == null) {
            throw new IllegalArgumentException("El tipo de alerta no puede ser nulo");
        }
        if (nuevoValor == null || nuevoValor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El valor trigger debe ser positivo");
        }
        
        log.info("Actualizando alerta {} para usuario {}", alertaId, userId);
        
        Alert alerta = buscarAlertaPorIdYUsuario(alertaId, userId);
        alerta.setTipo(nuevoTipo);
        alerta.setValorTrigger(nuevoValor);
        alerta.setMensajePersonalizado(nuevoMensaje);
        
        // Si la alerta estaba disparada, la reactivamos al cambiar los parámetros
        if (alerta.getDisparada()) {
            log.info("Alerta {} estaba disparada, reactivando automáticamente", alertaId);
            alerta.reactivar();
        }
        
        return alertRepository.save(alerta);
    }
    
    /**
     * Obtener una alerta específica por ID (validando pertenencia al usuario)
     */
    @Transactional(readOnly = true)
    public Alert obtenerAlertaPorId(Long alertaId, Long userId) {
        return buscarAlertaPorIdYUsuario(alertaId, userId);
    }
    
    /**
     * Obtener estadísticas de alertas de un usuario
     */
    @Transactional(readOnly = true)
    public AlertStats obtenerEstadisticasUsuario(Long userId) {
        validarUsuarioExiste(userId);
        
        List<Alert> todasLasAlertas = obtenerAlertasPorUsuario(userId);
        
        long activas = todasLasAlertas.stream().filter(Alert::getActiva).count();
        long disparadas = todasLasAlertas.stream().filter(Alert::getDisparada).count();
        long inactivas = todasLasAlertas.size() - activas;
        
        return new AlertStats(todasLasAlertas.size(), activas, inactivas, disparadas);
    }
    
    // ========== MÉTODOS AUXILIARES PRIVADOS ==========
    
    /**
     * Buscar alerta por ID y validar que pertenece al usuario
     */
    @Transactional(readOnly = true)
    private Alert buscarAlertaPorIdYUsuario(Long alertaId, Long userId) {
        Alert alerta = alertRepository.findById(alertaId)
                .orElseThrow(() -> new EntityNotFoundException("Alerta no encontrada con ID: " + alertaId));
        
        if (!alerta.getUser().getId().equals(userId)) {
            throw new SecurityException("El usuario " + userId + " no tiene acceso a la alerta " + alertaId);
        }
        
        return alerta;
    }
    
    /**
     * Validar parámetros de creación de alerta
     */
    private void validarParametrosAlerta(String symbol, BigDecimal valorTrigger, Alert.TipoAlerta tipo) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("El símbolo no puede estar vacío");
        }
        if (valorTrigger == null || valorTrigger.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El valor trigger debe ser positivo");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("El tipo de alerta no puede ser nulo");
        }
    }
    
    /**
     * Validar que un usuario existe en la base de datos
     */
    private void validarUsuarioExiste(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("Usuario no encontrado con ID: " + userId);
        }
    }
    
    // ========== CLASE INTERNA PARA ESTADÍSTICAS ==========
    
    public record AlertStats(
            long total,
            long activas,
            long inactivas,
            long disparadas
    ) {}
}