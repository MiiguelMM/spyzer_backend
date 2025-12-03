package com.miguel.spyzer.repository;

import com.miguel.spyzer.entities.MarketData;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Repositorio Redis para gestionar datos históricos de precios usando ZSET (Sorted Sets).
 *
 * Estructura de datos:
 * - Key: "historical:{symbol}" (ej. "historical:AAPL")
 * - Score: Timestamp Unix en milisegundos (permite ordenación temporal)
 * - Value: Objeto MarketData serializado como JSON
 *
 * Ventajas de usar ZSET:
 * - Ordenación automática por timestamp
 * - Queries eficientes por rango de tiempo (O(log(N) + M))
 * - Eliminación automática de datos antiguos
 * - Soporte nativo para time-series
 *
 * TTL Management:
 * - Los datos históricos se mantienen 24 horas (rolling window)
 * - Limpieza automática de datos antiguos en cada escritura
 * - Permite análisis intraday sin sobrecargar MySQL
 */
@Repository
public class MarketDataRedisRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ZSetOperations<String, Object> zSetOperations;

    // Prefijo para keys de históricos
    private static final String HISTORICAL_KEY_PREFIX = "historical:";

    // TTL para datos históricos (24 horas en milisegundos)
    private static final long HISTORICAL_TTL_MS = 24 * 60 * 60 * 1000L;

    public MarketDataRedisRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.zSetOperations = redisTemplate.opsForZSet();
    }

    /**
     * Genera la key de Redis para históricos de un símbolo.
     *
     * @param symbol Símbolo del activo
     * @return Key formateada (ej. "historical:AAPL")
     */
    private String getHistoricalKey(String symbol) {
        return HISTORICAL_KEY_PREFIX + symbol.toUpperCase();
    }

    /**
     * Convierte LocalDateTime a timestamp Unix en milisegundos.
     *
     * @param dateTime Fecha/hora a convertir
     * @return Timestamp Unix en milisegundos (usado como score en ZSET)
     */
    private double toTimestamp(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * Convierte timestamp Unix (score) a LocalDateTime.
     *
     * @param timestamp Timestamp en milisegundos
     * @return LocalDateTime correspondiente
     */
    private LocalDateTime fromTimestamp(double timestamp) {
        return LocalDateTime.ofInstant(
            Instant.ofEpochMilli((long) timestamp),
            ZoneId.systemDefault()
        );
    }

    /**
     * Añade un nuevo dato de precio al histórico.
     *
     * Usa el timestamp del objeto MarketData como score en el ZSET.
     * Automáticamente limpia datos antiguos (>24h) después de añadir.
     *
     * @param marketData Dato de mercado a almacenar
     */
    public void addHistoricalData(MarketData marketData) {
        String key = getHistoricalKey(marketData.getSymbol());
        double score = toTimestamp(marketData.getTimestamp());

        // Añadir al ZSET
        zSetOperations.add(key, marketData, score);

        // Limpiar datos antiguos (más de 24 horas)
        cleanOldData(marketData.getSymbol());
    }

    /**
     * Obtiene el histórico de precios de un símbolo en un rango de tiempo.
     *
     * @param symbol Símbolo del activo
     * @param startTime Inicio del rango (inclusive)
     * @param endTime Fin del rango (inclusive)
     * @return Lista ordenada de datos de mercado en el rango temporal
     */
    public List<MarketData> getHistoricalData(String symbol, LocalDateTime startTime, LocalDateTime endTime) {
        String key = getHistoricalKey(symbol);
        double minScore = toTimestamp(startTime);
        double maxScore = toTimestamp(endTime);

        // ZRANGEBYSCORE: Obtener elementos con score entre minScore y maxScore
        Set<Object> results = zSetOperations.rangeByScore(key, minScore, maxScore);

        if (results == null || results.isEmpty()) {
            return List.of();
        }

        // Convertir y retornar
        return results.stream()
            .filter(obj -> obj instanceof MarketData)
            .map(obj -> (MarketData) obj)
            .collect(Collectors.toList());
    }

    /**
     * Obtiene las últimas N entradas de histórico para un símbolo.
     *
     * @param symbol Símbolo del activo
     * @param count Número de entradas a obtener
     * @return Lista de los últimos N datos ordenados por timestamp (más reciente primero)
     */
    public List<MarketData> getLatestHistoricalData(String symbol, int count) {
        String key = getHistoricalKey(symbol);

        // ZREVRANGE: Obtener últimos N elementos en orden descendente (más reciente primero)
        Set<Object> results = zSetOperations.reverseRange(key, 0, count - 1);

        if (results == null || results.isEmpty()) {
            return List.of();
        }

        return results.stream()
            .filter(obj -> obj instanceof MarketData)
            .map(obj -> (MarketData) obj)
            .collect(Collectors.toList());
    }

    /**
     * Obtiene todos los datos históricos de un símbolo (últimas 24h).
     *
     * @param symbol Símbolo del activo
     * @return Lista completa de datos históricos ordenados por timestamp
     */
    public List<MarketData> getAllHistoricalData(String symbol) {
        String key = getHistoricalKey(symbol);

        // ZRANGE 0 -1: Obtener todos los elementos
        Set<Object> results = zSetOperations.range(key, 0, -1);

        if (results == null || results.isEmpty()) {
            return List.of();
        }

        return results.stream()
            .filter(obj -> obj instanceof MarketData)
            .map(obj -> (MarketData) obj)
            .collect(Collectors.toList());
    }

    /**
     * Limpia datos históricos más antiguos que 24 horas.
     *
     * Se ejecuta automáticamente en addHistoricalData() para mantener
     * un rolling window de 24 horas.
     *
     * @param symbol Símbolo a limpiar
     * @return Número de entradas eliminadas
     */
    public Long cleanOldData(String symbol) {
        String key = getHistoricalKey(symbol);
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);
        double maxScore = toTimestamp(cutoffTime);

        // ZREMRANGEBYSCORE: Eliminar elementos con score < cutoffTime
        return zSetOperations.removeRangeByScore(key, 0, maxScore);
    }

    /**
     * Elimina todos los datos históricos de un símbolo.
     *
     * @param symbol Símbolo a limpiar completamente
     * @return true si se eliminó la key, false si no existía
     */
    public Boolean deleteAllHistoricalData(String symbol) {
        String key = getHistoricalKey(symbol);
        return redisTemplate.delete(key);
    }

    /**
     * Obtiene el número de entradas históricas almacenadas para un símbolo.
     *
     * @param symbol Símbolo a consultar
     * @return Número de entradas en el ZSET
     */
    public Long getHistoricalDataCount(String symbol) {
        String key = getHistoricalKey(symbol);
        return zSetOperations.size(key);
    }

    /**
     * Verifica si existen datos históricos para un símbolo.
     *
     * @param symbol Símbolo a verificar
     * @return true si hay datos, false si no
     */
    public boolean hasHistoricalData(String symbol) {
        Long count = getHistoricalDataCount(symbol);
        return count != null && count > 0;
    }

    /**
     * Obtiene el dato más reciente de un símbolo.
     *
     * @param symbol Símbolo a consultar
     * @return Último MarketData registrado, o null si no hay datos
     */
    public MarketData getLatestData(String symbol) {
        List<MarketData> latest = getLatestHistoricalData(symbol, 1);
        return latest.isEmpty() ? null : latest.get(0);
    }

    /**
     * Obtiene estadísticas del histórico almacenado.
     *
     * @param symbol Símbolo a analizar
     * @return String con información de debug
     */
    public String getHistoricalStats(String symbol) {
        Long count = getHistoricalDataCount(symbol);
        MarketData latest = getLatestData(symbol);

        if (count == null || count == 0) {
            return String.format("Symbol %s: No historical data", symbol);
        }

        String latestTime = latest != null ? latest.getTimestamp().toString() : "N/A";
        return String.format("Symbol %s: %d entries | Latest: %s", symbol, count, latestTime);
    }
}
