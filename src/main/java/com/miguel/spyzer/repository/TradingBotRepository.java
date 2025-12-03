package com.miguel.spyzer.repository;

import com.miguel.spyzer.entities.TradingBot;
import com.miguel.spyzer.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradingBotRepository extends JpaRepository<TradingBot, Long> {
    
    // Obtener todos los bots de un usuario
    List<TradingBot> findByUserId(Long userId);
    
    // Obtener solo los bots activos de un usuario
    List<TradingBot> findByUserIdAndActivoTrue(Long userId);
    
    // Obtener todos los bots activos del sistema (para el job que los ejecuta)
    List<TradingBot> findByActivoTrue();
    
    // Buscar bot por usuario y nombre (para evitar duplicados)
    Optional<TradingBot> findByUserIdAndNombre(Long userId, String nombre);
    
    // Contar bots activos de un usuario
    long countByUserIdAndActivoTrue(Long userId);
    
    // Contar total de bots de un usuario
    long countByUserId(Long userId);

    // Eliminar todos los bots de un usuario
    void deleteByUserId(Long userId);
}