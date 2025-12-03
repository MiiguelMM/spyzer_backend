package com.miguel.spyzer.repository;

import com.miguel.spyzer.entities.MarketData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketDataRepository extends JpaRepository<MarketData, Long> {
    
    // Buscar el dato más reciente de un símbolo específico
    MarketData findTopBySymbolOrderByTimestampDesc(String symbol);
    
    // Buscar todos los datos de un símbolo ordenados por timestamp
    List<MarketData> findBySymbolOrderByTimestampDesc(String symbol);
    
    // Buscar datos por símbolo y tipo de dato
    List<MarketData> findBySymbolAndDataTypeOrderByTimestampDesc(
        String symbol, MarketData.DataType dataType);
    
    // Buscar múltiples símbolos
    @Query("SELECT m FROM MarketData m WHERE m.symbol IN :symbols ORDER BY m.symbol, m.timestamp DESC")
    List<MarketData> findBySymbolInOrderBySymbolAndTimestampDesc(@Param("symbols") List<String> symbols);
    
    // Obtener los datos más recientes de cada símbolo (para dashboard)
    @Query("SELECT m FROM MarketData m WHERE m.timestamp = " +
           "(SELECT MAX(m2.timestamp) FROM MarketData m2 WHERE m2.symbol = m.symbol)")
    List<MarketData> findLatestDataForAllSymbols();
    
    // Buscar datos por rango de tiempo
    List<MarketData> findByTimestampBetweenOrderByTimestampDesc(
        LocalDateTime start, LocalDateTime end);
    
    // Buscar datos de un símbolo en un rango de tiempo
    List<MarketData> findBySymbolAndTimestampBetweenOrderByTimestampDesc(
        String symbol, LocalDateTime start, LocalDateTime end);
    
    // Verificar si existe un símbolo
    boolean existsBySymbol(String symbol);
    
    // Contar registros por símbolo
    long countBySymbol(String symbol);
    
    // Obtener todos los símbolos únicos disponibles
    @Query("SELECT DISTINCT m.symbol FROM MarketData m ORDER BY m.symbol")
    List<String> findAllDistinctSymbols();
    
    // === MÉTODOS DE LIMPIEZA/MANTENIMIENTO ===
    
    // Borrar todos los datos de un símbolo específico
    @Modifying
    @Transactional
    void deleteBySymbol(String symbol);
    
    // Borrar datos anteriores a una fecha específica
    @Modifying
    @Transactional
    int deleteByTimestampBefore(LocalDateTime timestamp);
    
    // Borrar datos de un símbolo anteriores a una fecha
    @Modifying
    @Transactional
    int deleteBySymbolAndTimestampBefore(String symbol, LocalDateTime timestamp);
    
    // Borrar datos por tipo de dato
    @Modifying
    @Transactional
    void deleteByDataType(MarketData.DataType dataType);
    
    // === CONSULTAS DE ESTADÍSTICAS/ANÁLISIS ===
    
    // Buscar símbolos con datos obsoletos
    @Query("SELECT DISTINCT m.symbol FROM MarketData m WHERE m.timestamp < :cutoffTime")
    List<String> findSymbolsWithDataOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    // Contar total de registros
    @Query("SELECT COUNT(m) FROM MarketData m")
    long countTotalRecords();
    
    // Obtener último timestamp de actualización por símbolo
    @Query("SELECT m.symbol, MAX(m.timestamp) FROM MarketData m GROUP BY m.symbol")
    List<Object[]> findLastUpdateTimePerSymbol();
    
    // Buscar símbolos con tendencia alcista (variación positiva)
    @Query("SELECT m FROM MarketData m WHERE m.variacionPorcentual > 0 AND " +
           "m.timestamp = (SELECT MAX(m2.timestamp) FROM MarketData m2 WHERE m2.symbol = m.symbol)")
    List<MarketData> findSymbolsWithPositiveTrend();
    
    // Buscar símbolos con tendencia bajista (variación negativa)
    @Query("SELECT m FROM MarketData m WHERE m.variacionPorcentual < 0 AND " +
           "m.timestamp = (SELECT MAX(m2.timestamp) FROM MarketData m2 WHERE m2.symbol = m.symbol)")
    List<MarketData> findSymbolsWithNegativeTrend();
    
    // Top gainers (mayores subidas porcentuales)
    @Query("SELECT m FROM MarketData m WHERE m.timestamp = " +
           "(SELECT MAX(m2.timestamp) FROM MarketData m2 WHERE m2.symbol = m.symbol) " +
           "AND m.variacionPorcentual IS NOT NULL " +
           "ORDER BY m.variacionPorcentual DESC")
    List<MarketData> findTopGainers();
    
    // Top losers (mayores bajadas porcentuales)
    @Query("SELECT m FROM MarketData m WHERE m.timestamp = " +
           "(SELECT MAX(m2.timestamp) FROM MarketData m2 WHERE m2.symbol = m.symbol) " +
           "AND m.variacionPorcentual IS NOT NULL " +
           "ORDER BY m.variacionPorcentual ASC")
    List<MarketData> findTopLosers();
    
    // Buscar por rango de precios
    @Query("SELECT m FROM MarketData m WHERE m.precio BETWEEN :minPrice AND :maxPrice " +
           "AND m.timestamp = (SELECT MAX(m2.timestamp) FROM MarketData m2 WHERE m2.symbol = m.symbol)")
    List<MarketData> findByPriceRange(@Param("minPrice") java.math.BigDecimal minPrice, 
                                     @Param("maxPrice") java.math.BigDecimal maxPrice);
    
    // === CONSULTAS ESPECÍFICAS PARA EL SERVICE ===
    
    // Verificar si todos los símbolos prioritarios tienen datos recientes
    @Query("SELECT COUNT(DISTINCT m.symbol) FROM MarketData m WHERE m.symbol IN :symbols " +
           "AND m.timestamp > :cutoffTime")
    long countSymbolsWithRecentData(@Param("symbols") List<String> symbols, 
                                   @Param("cutoffTime") LocalDateTime cutoffTime);
    
    // Obtener resumen de estado de la BD para monitoring
    @Query("SELECT " +
           "COUNT(DISTINCT m.symbol) as uniqueSymbols, " +
           "COUNT(m) as totalRecords, " +
           "MAX(m.timestamp) as lastUpdate " +
           "FROM MarketData m")
    Object[] getDatabaseSummary();
}