package com.miguel.spyzer.repository;

import com.miguel.spyzer.entities.Transaction;
import com.miguel.spyzer.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
    // Obtener todas las transacciones de un usuario (historial completo)
    List<Transaction> findByUserIdOrderByTimestampDesc(Long userId);
    
    // Obtener transacciones de un usuario por tipo (BUY o SELL)
    List<Transaction> findByUserIdAndTipoOrderByTimestampDesc(Long userId, Transaction.TipoTransaccion tipo);
    
    // Obtener transacciones de un usuario para un símbolo específico
    List<Transaction> findByUserIdAndSymbolOrderByTimestampDesc(Long userId, String symbol);
    
    // Contar total de transacciones de un usuario
    long countByUserId(Long userId);
    
    // Obtener transacciones recientes de un usuario (últimas N)
    List<Transaction> findTop10ByUserIdOrderByTimestampDesc(Long userId);
    
    // Para calcular estadísticas de trading
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.user.id = :userId AND t.tipo = :tipo")
    long countByUserIdAndTipo(@Param("userId") Long userId, @Param("tipo") Transaction.TipoTransaccion tipo);

    // Eliminar todas las transacciones de un usuario
    void deleteByUserId(Long userId);
}