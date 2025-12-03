package com.miguel.spyzer.repository;

import com.miguel.spyzer.entities.Portfolio;
import com.miguel.spyzer.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    
    // Obtener todas las posiciones de un usuario
    List<Portfolio> findByUser(User user);
    
    // Obtener todas las posiciones de un usuario por ID
    List<Portfolio> findByUserId(Long userId);
    
    // Buscar una posición específica de un usuario y símbolo
    Optional<Portfolio> findByUserAndSymbol(User user, String symbol);
    Optional<Portfolio> findByUserIdAndSymbol(Long userId, String symbol);
    
    // Verificar si un usuario tiene una posición en un símbolo específico
    boolean existsByUserAndSymbol(User user, String symbol);
    boolean existsByUserIdAndSymbol(Long userId, String symbol);
    
    // Obtener posiciones ordenadas por valor de mercado (mayores primero)
    List<Portfolio> findByUserOrderByValorMercadoDesc(User user);
    List<Portfolio> findByUserIdOrderByValorMercadoDesc(Long userId);
    
    // Contar cuántas posiciones diferentes tiene un usuario
    long countByUser(User user);
    long countByUserId(Long userId);

    // Eliminar todas las posiciones de un usuario
    void deleteByUserId(Long userId);
}