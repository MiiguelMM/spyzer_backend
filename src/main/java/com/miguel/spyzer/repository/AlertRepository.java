package com.miguel.spyzer.repository;

import com.miguel.spyzer.entities.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    
    // ========== MÉTODOS BÁSICOS ==========
    
    /**
     * Obtener todas las alertas de un usuario
     */
    List<Alert> findByUserId(Long userId);
    
    /**
     * Obtener todas las alertas de un usuario con paginación
     */
    Page<Alert> findByUserId(Long userId, Pageable pageable);
    
    /**
     * Obtener solo las alertas activas de un usuario
     */
    List<Alert> findByUserIdAndActivaTrue(Long userId);
    
    /**
     * Buscar alerta por ID y userId (para validación de propiedad)
     */
    Optional<Alert> findByIdAndUserId(Long id, Long userId);
    
    // ========== MÉTODOS PARA VERIFICACIÓN DE ALERTAS ==========
    
    /**
     * Obtener alertas activas por símbolo (para verificar cuando lleguen datos nuevos)
     */
    List<Alert> findBySymbolAndActivaTrueAndDisparadaFalse(String symbol);
    
    /**
     * Obtener todas las alertas activas del sistema (para el job que las verifica)
     */
    List<Alert> findByActivaTrueAndDisparadaFalse();
    
    // ========== MÉTODOS DE ESTADÍSTICAS Y CONTEO ==========
    
    /**
     * Contar alertas activas de un usuario
     */
    long countByUserIdAndActivaTrue(Long userId);
    
    // ========== MÉTODOS PARA LIMPIEZA Y MANTENIMIENTO ==========
    
    /**
     * Buscar alertas disparadas de un usuario
     */
    List<Alert> findByUserIdAndDisparadaTrue(Long userId);
    
    /**
     * Buscar alertas disparadas antes de una fecha (para limpieza)
     */
    List<Alert> findByDisparadaTrueAndTriggeredAtBefore(LocalDateTime fecha);
    
    /**
     * Eliminar todas las alertas ya disparadas (para mantenimiento)
     */
    void deleteByDisparadaTrue();

    /**
     * Eliminar todas las alertas de un usuario
     */
    void deleteByUserId(Long userId);
}