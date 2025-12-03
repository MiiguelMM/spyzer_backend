package com.miguel.spyzer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuración de Redis para caché de precios y datos históricos.
 *
 * Estrategia de caché:
 * - PRECIOS ACTUALES: Caché con TTLs diferenciados por grupo de símbolos
 * · Premium: 20 minutos (sincronizado con frecuencia de actualización)
 * · Standard: 60 minutos
 * · Extended: 90 minutos
 *
 * - HISTÓRICOS: ZSET (Sorted Set) para series temporales
 * · Key: "historical:{symbol}"
 * · Score: timestamp
 * · Value: JSON del precio
 * · TTL: 24 horas (rolling window)
 *
 * IMPORTANTE: Redis NO reduce llamadas a TwelveData API, solo reduce carga en
 * MySQL.
 * Las llamadas a la API externa siguen siendo necesarias para obtener datos
 * actualizados.
 */
@Configuration
@EnableCaching
public class RedisConfig {

    // Nombres de las cachés (deben coincidir con @Cacheable)
    public static final String CACHE_PREMIUM_PRICES = "premiumPrices";
    public static final String CACHE_STANDARD_PRICES = "standardPrices";
    public static final String CACHE_EXTENDED_PRICES = "extendedPrices";
    public static final String CACHE_RANKINGS = "rankings";

    // TTLs alineados con frecuencias de actualización del scheduler
    private static final Duration PREMIUM_TTL = Duration.ofMinutes(20);
    private static final Duration STANDARD_TTL = Duration.ofMinutes(60);
    private static final Duration EXTENDED_TTL = Duration.ofMinutes(90);
    private static final Duration RANKINGS_TTL = Duration.ofMinutes(5); // Rankings se actualizan frecuentemente

    /**
     * ObjectMapper configurado para Redis con soporte para LocalDateTime y otros tipos de Java 8 Time.
     *
     * Registra el módulo JavaTimeModule para serializar/deserializar:
     * - LocalDateTime
     * - LocalDate
     * - LocalTime
     * - ZonedDateTime
     * - etc.
     */
    @Bean
    public ObjectMapper redisObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    /**
     * RedisTemplate genérico para operaciones manuales de caché.
     *
     * Se usa para:
     * - Gestión de ZSETs de históricos (zadd, zrangeByScore, zremrangeByScore)
     * - Operaciones que no son soportadas por @Cacheable
     *
     * Serializadores:
     * - Keys: StringRedisSerializer (claves legibles en Redis CLI)
     * - Values: GenericJackson2JsonRedisSerializer (JSON automático para POJOs)
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper redisObjectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Serialización de claves como String
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Serialización de valores como JSON con soporte para LocalDateTime
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * CacheManager con configuraciones diferenciadas por grupo de símbolos.
     *
     * Cada caché tiene su propio TTL alineado con la frecuencia de actualización
     * del scheduler correspondiente. Esto garantiza que:
     *
     * 1. Los datos en caché nunca están más desactualizados que el intervalo de
     * actualización
     * 2. Se minimiza el impacto en MySQL durante horarios de mercado abierto
     * 3. Los schedulers siempre obtienen datos frescos de TwelveData API
     *
     * @param connectionFactory Factory de conexiones Redis (auto-configurado por
     *                          Spring Boot)
     * @param redisObjectMapper ObjectMapper configurado con soporte para LocalDateTime
     * @return CacheManager configurado con TTLs diferenciados
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper redisObjectMapper) {
        // Crear un ObjectMapper específico para caché con default typing habilitado
        ObjectMapper cacheObjectMapper = redisObjectMapper.copy();
        cacheObjectMapper.activateDefaultTyping(
                cacheObjectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
        );

        // Configuración por defecto (usada como base)
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer(cacheObjectMapper)))
                .disableCachingNullValues(); // No cachear valores null

        // Configuraciones específicas por caché
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Premium: 20 minutos (símbolos más volátiles)
        cacheConfigurations.put(
                CACHE_PREMIUM_PRICES,
                defaultConfig.entryTtl(PREMIUM_TTL));

        // Standard: 60 minutos (símbolos moderadamente volátiles)
        cacheConfigurations.put(
                CACHE_STANDARD_PRICES,
                defaultConfig.entryTtl(STANDARD_TTL));

        // Extended: 90 minutos (símbolos menos volátiles)
        cacheConfigurations.put(
                CACHE_EXTENDED_PRICES,
                defaultConfig.entryTtl(EXTENDED_TTL));

        // Rankings: 5 minutos (se actualiza con transacciones)
        cacheConfigurations.put(
                CACHE_RANKINGS,
                defaultConfig.entryTtl(RANKINGS_TTL));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware() // Sincronizar con transacciones de Spring
                .build();
    }
}
