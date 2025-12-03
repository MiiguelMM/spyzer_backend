# 📦 Arquitectura de Caché Redis en Spyzer

## 📋 Índice

1. [Visión General](#-visión-general)
2. [Arquitectura de Componentes](#-arquitectura-de-componentes)
3. [Estrategia de Caché](#-estrategia-de-caché)
4. [Flujo de Datos Completo](#-flujo-de-datos-completo)
5. [Estructura de Datos en Redis](#-estructura-de-datos-en-redis)
6. [Guía de Uso](#-guía-de-uso)
7. [Monitoreo y Debugging](#-monitoreo-y-debugging)
8. [Troubleshooting](#-troubleshooting)

---

## 🎯 Visión General

### ¿Qué problema resuelve Redis en Spyzer?

Spyzer es una plataforma de trading que:
- Actualiza **80 símbolos** de bolsa cada 20/60/90 minutos durante horario NYSE
- Tiene restricciones de API externa (TwelveData): **800 créditos/día, 8 llamadas/min**
- Necesita servir consultas de múltiples usuarios concurrentemente

**Sin Redis:**
```
Usuario 1 → Consulta AAPL → MySQL
Usuario 2 → Consulta AAPL → MySQL  ❌ Sobrecarga de MySQL
Usuario 3 → Consulta AAPL → MySQL
Usuario 4 → Consulta AAPL → MySQL
```

**Con Redis:**
```
Usuario 1 → Consulta AAPL → MySQL → Cachea en Redis ✅
Usuario 2 → Consulta AAPL → Redis (instantáneo)
Usuario 3 → Consulta AAPL → Redis (instantáneo)
Usuario 4 → Consulta AAPL → Redis (instantáneo)
```

### ⚠️ Aclaración Importante

**Redis NO reduce las llamadas a la API de TwelveData**. Los schedulers siguen ejecutándose y consultando TwelveData cada 20/60/90 minutos. Redis solo:

✅ **Reduce la carga en MySQL** (menos queries concurrentes)
✅ **Mejora el rendimiento** para usuarios (respuestas más rápidas)
✅ **Permite escalabilidad** (soporta más usuarios simultáneos)
❌ **NO ahorra créditos de API** (los schedulers siguen llamando a TwelveData)

---

## 🏗️ Arquitectura de Componentes

La implementación de Redis en Spyzer consta de **3 componentes principales**:

```
┌─────────────────────────────────────────────────────────────┐
│                     SPYZER BACKEND                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  1. RedisConfig.java                                 │  │
│  │     - CacheManager con TTLs diferenciados           │  │
│  │     - RedisTemplate para operaciones manuales       │  │
│  │     - 3 cachés: Premium/Standard/Extended           │  │
│  └──────────────────────────────────────────────────────┘  │
│                          ▼                                  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  2. MarketDataRedisRepository.java                   │  │
│  │     - Gestión de ZSET para históricos intraday      │  │
│  │     - TTL automático de 24 horas                    │  │
│  │     - Queries por rango temporal                    │  │
│  └──────────────────────────────────────────────────────┘  │
│                          ▼                                  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  3. MarketDataService.java                           │  │
│  │     - @Cacheable en métodos de lectura              │  │
│  │     - @CacheEvict en schedulers de actualización    │  │
│  │     - Guardado automático en ZSET                   │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
                          ▼
              ┌────────────────────┐
              │   REDIS SERVER     │
              │   localhost:6379   │
              └────────────────────┘
```

### 1️⃣ RedisConfig.java

**Ubicación:** `src/main/java/com/miguel/spyzer/config/RedisConfig.java`

**Responsabilidades:**
- Configurar Spring Cache con Redis como backend
- Definir 3 cachés diferenciadas con TTLs específicos
- Proporcionar RedisTemplate para operaciones manuales de ZSET

**Código clave:**

```java
@Configuration
@EnableCaching
public class RedisConfig {

    // TTLs alineados con frecuencias de actualización del scheduler
    private static final Duration PREMIUM_TTL = Duration.ofMinutes(20);
    private static final Duration STANDARD_TTL = Duration.ofMinutes(60);
    private static final Duration EXTENDED_TTL = Duration.ofMinutes(90);

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        cacheConfigurations.put("premiumPrices",
            defaultConfig.entryTtl(PREMIUM_TTL));
        cacheConfigurations.put("standardPrices",
            defaultConfig.entryTtl(STANDARD_TTL));
        cacheConfigurations.put("extendedPrices",
            defaultConfig.entryTtl(EXTENDED_TTL));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }
}
```

**¿Por qué TTLs diferenciados?**

| Grupo | Símbolos | Frecuencia | TTL | Razón |
|-------|----------|------------|-----|-------|
| **Premium** | 20 (AAPL, MSFT, SPY...) | 20 min | 20 min | Más volátiles, actualizaciones frecuentes |
| **Standard** | 42 (WFC, JNJ, XOM...) | 60 min | 60 min | Volatilidad media |
| **Extended** | 18 (ARKK, TLT, GLD...) | 90 min | 90 min | Menos volátiles, ETFs temáticos |

El TTL se sincroniza con la frecuencia del scheduler para garantizar que la caché **nunca tenga datos más antiguos** que el intervalo de actualización.

---

### 2️⃣ MarketDataRedisRepository.java

**Ubicación:** `src/main/java/com/miguel/spyzer/repository/MarketDataRedisRepository.java`

**Responsabilidades:**
- Gestionar históricos intraday usando **ZSET (Sorted Sets)** de Redis
- Limpiar automáticamente datos antiguos (>24h)
- Proporcionar queries por rango temporal

**Estructura de datos:**

```
Key:   "historical:AAPL"
Type:  ZSET (Sorted Set)
Score: Timestamp Unix en milisegundos (1701352800000)
Value: Objeto MarketData serializado como JSON

┌────────────────────────────────────────────────────┐
│ historical:AAPL (ZSET)                             │
├─────────────────┬──────────────────────────────────┤
│ Score           │ Value (MarketData JSON)          │
├─────────────────┼──────────────────────────────────┤
│ 1701349200000   │ {"symbol":"AAPL","precio":195.50}│
│ 1701350400000   │ {"symbol":"AAPL","precio":196.20}│
│ 1701351600000   │ {"symbol":"AAPL","precio":195.80}│
│ ...             │ ...                              │
└─────────────────┴──────────────────────────────────┘
```

**¿Por qué ZSET y no otros tipos?**

| Tipo Redis | ¿Por qué NO? |
|------------|--------------|
| **STRING** | Solo puede almacenar 1 valor, no es ideal para series temporales |
| **LIST** | No permite ordenación por timestamp, queries lentas |
| **HASH** | No soporta ordenación, difícil consultar rangos |
| **ZSET** ✅ | **Ordenación automática por score (timestamp)**, queries eficientes O(log(N)+M) |

**Operaciones principales:**

```java
// Añadir nuevo dato histórico
public void addHistoricalData(MarketData marketData) {
    String key = "historical:" + marketData.getSymbol();
    double score = marketData.getTimestamp().toEpochMilli();
    zSetOperations.add(key, marketData, score);
    cleanOldData(marketData.getSymbol()); // Limpia datos >24h
}

// Consultar rango temporal (ej: últimas 6 horas)
public List<MarketData> getHistoricalData(String symbol,
                                          LocalDateTime start,
                                          LocalDateTime end) {
    String key = "historical:" + symbol;
    double minScore = toTimestamp(start);
    double maxScore = toTimestamp(end);
    return zSetOperations.rangeByScore(key, minScore, maxScore);
}
```

**Ejemplo de uso:**

```java
// Obtener histórico de AAPL de las últimas 6 horas
LocalDateTime now = LocalDateTime.now();
LocalDateTime sixHoursAgo = now.minusHours(6);

List<MarketData> historico = marketDataRedisRepository
    .getHistoricalData("AAPL", sixHoursAgo, now);

// Resultado: Lista ordenada de todos los puntos de precio de AAPL
// registrados en las últimas 6 horas
```

---

### 3️⃣ MarketDataService.java (Integración)

**Ubicación:** `src/main/java/com/miguel/spyzer/service/MarketDataService.java`

**Responsabilidades:**
- Aplicar `@Cacheable` en métodos de lectura
- Aplicar `@CacheEvict` en schedulers de actualización
- Guardar datos en ZSET después de actualizar MySQL

#### 📖 Lectura de Datos (Cache-Aside Pattern)

```java
public MarketData obtenerDatos(String symbol) {
    String upperSymbol = symbol.toUpperCase();

    // Determinar grupo del símbolo
    UpdateFrequency frequency = symbolGroupConfig.getFrequencyForSymbol(upperSymbol);

    // Llamar al método cacheado correspondiente
    return switch (frequency) {
        case PREMIUM_20MIN -> obtenerDatosPremiumCache(upperSymbol);
        case STANDARD_60MIN -> obtenerDatosStandardCache(upperSymbol);
        case EXTENDED_90MIN -> obtenerDatosExtendedCache(upperSymbol);
    };
}

@Cacheable(value = "premiumPrices", key = "#symbol")
private MarketData obtenerDatosPremiumCache(String symbol) {
    return marketDataRepository.findTopBySymbolOrderByTimestampDesc(symbol);
}
```

**Flujo de ejecución (primera llamada):**

```
1. Usuario llama: obtenerDatos("AAPL")
2. Se determina que AAPL es PREMIUM
3. Llama a obtenerDatosPremiumCache("AAPL")
4. @Cacheable verifica si existe "premiumPrices::AAPL" en Redis
5. ❌ No existe → Consulta MySQL
6. ✅ Guarda resultado en Redis con TTL de 20 minutos
7. Retorna MarketData al usuario
```

**Flujo de ejecución (llamadas subsecuentes):**

```
1. Usuario llama: obtenerDatos("AAPL")
2. Se determina que AAPL es PREMIUM
3. Llama a obtenerDatosPremiumCache("AAPL")
4. @Cacheable verifica si existe "premiumPrices::AAPL" en Redis
5. ✅ Existe y no ha expirado → Retorna desde Redis (sin tocar MySQL)
```

#### ✍️ Escritura de Datos (Cache Eviction + ZSET)

```java
@Scheduled(fixedRate = 1200000) // 20 minutos
@Transactional
@CacheEvict(value = "premiumPrices", allEntries = true)
public void actualizarGrupoPremium() {
    if (!marketHoursService.isNYSEOpen()) {
        return; // No actualizar fuera de horario NYSE
    }

    actualizarGrupoDeSimbolos(
        symbolGroupConfig.getSymbolsByFrequency(PREMIUM_20MIN),
        "PREMIUM"
    );
}

private void actualizarGrupoDeSimbolos(List<String> simbolos, String grupo) {
    List<MarketData> datosNuevos = new ArrayList<>();

    // 1. Llamar a TwelveData API para cada símbolo
    for (String symbol : simbolos) {
        MarketData datos = obtenerDatosTwelveData(symbol); // API Call
        if (datos != null) {
            datosNuevos.add(datos);
        }
        Thread.sleep(8000); // Rate limiting: 8 seg entre llamadas
    }

    // 2. Guardar en MySQL
    marketDataRepository.deleteBySymbol(simbolos);
    marketDataRepository.saveAll(datosNuevos);

    // 3. Guardar en Redis ZSET (históricos intraday)
    guardarEnRedisZSET(datosNuevos);

    // 4. La caché se limpia automáticamente por @CacheEvict
}

private void guardarEnRedisZSET(List<MarketData> datosNuevos) {
    for (MarketData datos : datosNuevos) {
        marketDataRedisRepository.addHistoricalData(datos);
    }
}
```

**Flujo completo de actualización:**

```
┌─────────────────────────────────────────────────────────────┐
│ SCHEDULER PREMIUM (cada 20 min durante horario NYSE)       │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
        ┌─────────────────────────────────┐
        │ @CacheEvict limpia premiumPrices│ ← Invalida caché ANTES
        └─────────────────────────────────┘
                          │
                          ▼
        ┌─────────────────────────────────┐
        │ Llamar TwelveData API (20 símb.)│ ← 20 llamadas * 8 seg = 160 seg
        └─────────────────────────────────┘
                          │
                          ▼
        ┌─────────────────────────────────┐
        │ Guardar en MySQL                │
        └─────────────────────────────────┘
                          │
                          ▼
        ┌─────────────────────────────────┐
        │ Guardar en Redis ZSET           │ ← historical:AAPL, historical:MSFT...
        └─────────────────────────────────┘
                          │
                          ▼
        ┌─────────────────────────────────┐
        │ Actualizar portfolios           │
        └─────────────────────────────────┘
                          │
                          ▼
        ┌─────────────────────────────────┐
        │ Verificar alertas               │
        └─────────────────────────────────┘
```

---

## 🎯 Estrategia de Caché

### Cache-Aside Pattern (Lazy Loading)

Spyzer usa el patrón **Cache-Aside** (también llamado Lazy Loading):

```
┌──────────────────────────────────────────────────────────┐
│ LECTURA                                                  │
└──────────────────────────────────────────────────────────┘
App → Redis.get("premiumPrices::AAPL")
       │
       ├─ HIT ✅  → Retorna dato
       │
       └─ MISS ❌ → MySQL.query("SELECT * FROM market_data WHERE symbol='AAPL'")
                    │
                    └─ Redis.set("premiumPrices::AAPL", data, TTL=20min)
                       │
                       └─ Retorna dato

┌──────────────────────────────────────────────────────────┐
│ ESCRITURA                                                │
└──────────────────────────────────────────────────────────┘
Scheduler → Redis.delete("premiumPrices::*")  ← @CacheEvict
          │
          └─ MySQL.insert(nuevos_datos)
             │
             └─ Redis.zadd("historical:AAPL", score, data)
```

### TTL (Time To Live) Sincronizado

Los TTLs de caché están **sincronizados con las frecuencias de actualización** para evitar datos obsoletos:

| Cache | TTL | Frecuencia Scheduler | ¿Por qué? |
|-------|-----|----------------------|-----------|
| premiumPrices | 20 min | 20 min | Si el scheduler actualiza cada 20 min, no tiene sentido cachear más tiempo |
| standardPrices | 60 min | 60 min | Idem |
| extendedPrices | 90 min | 90 min | Idem |

**Ejemplo temporal:**

```
15:30 → Scheduler PREMIUM actualiza AAPL ($195.50)
        Redis: premiumPrices::AAPL = $195.50 (expira 15:50)

15:35 → Usuario consulta AAPL → Redis ✅ ($195.50)
15:45 → Usuario consulta AAPL → Redis ✅ ($195.50)

15:50 → Scheduler PREMIUM actualiza AAPL ($196.20)
        Redis: Limpia premiumPrices::AAPL
        Redis: premiumPrices::AAPL = $196.20 (expira 16:10)

15:55 → Usuario consulta AAPL → Redis ✅ ($196.20)
```

---

## 🔄 Flujo de Datos Completo

### Escenario 1: Usuario consulta precio de AAPL (Cache HIT)

```
┌──────────┐
│ Frontend │ GET /api/market-data/AAPL
└──────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ MarketDataController                │
│ └─ obtenerDatosSymbol("AAPL")       │
└─────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ MarketDataService                   │
│ └─ obtenerDatos("AAPL")             │
│    └─ obtenerDatosPremiumCache()    │ ← @Cacheable
└─────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ Redis (Spring Cache)                │
│ GET premiumPrices::AAPL             │
│ → ✅ HIT: {"symbol":"AAPL",...}     │
└─────────────────────────────────────┘
     │
     ▼
┌──────────┐
│ Frontend │ Recibe JSON con precio
└──────────┘

Tiempo: ~5ms (sin tocar MySQL)
```

### Escenario 2: Usuario consulta precio de AAPL (Cache MISS)

```
┌──────────┐
│ Frontend │ GET /api/market-data/AAPL
└──────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ MarketDataService                   │
│ └─ obtenerDatosPremiumCache()       │ ← @Cacheable
└─────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ Redis (Spring Cache)                │
│ GET premiumPrices::AAPL             │
│ → ❌ MISS (key no existe)           │
└─────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ MySQL                               │
│ SELECT * FROM market_data           │
│ WHERE symbol='AAPL'                 │
│ → {"symbol":"AAPL","precio":195.50} │
└─────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ Redis (Spring Cache)                │
│ SET premiumPrices::AAPL             │
│ VALUE: {"symbol":"AAPL",...}        │
│ TTL: 1200 segundos (20 min)         │
└─────────────────────────────────────┘
     │
     ▼
┌──────────┐
│ Frontend │ Recibe JSON con precio
└──────────┘

Tiempo: ~50ms (primera vez, luego caché)
```

### Escenario 3: Scheduler actualiza grupo PREMIUM

```
┌─────────────────────────────────────┐
│ Scheduler PREMIUM (cada 20 min)    │
│ if (isNYSEOpen())                   │
└─────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ @CacheEvict(premiumPrices)          │
│ → Redis: FLUSHDB premiumPrices::*   │ ← Limpia TODA la caché premium
└─────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ TwelveData API                      │
│ 20 llamadas (AAPL, MSFT, GOOGL...)  │
│ Rate Limit: 8 seg entre llamadas    │
│ Total: 160 segundos                 │
└─────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ MySQL                               │
│ DELETE FROM market_data             │
│ WHERE symbol IN (...)               │
│ INSERT INTO market_data VALUES(...) │
└─────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ Redis ZSET                          │
│ ZADD historical:AAPL 1701... {...}  │
│ ZADD historical:MSFT 1701... {...}  │
│ ...                                 │
│ ZREMRANGEBYSCORE ... (limpia >24h)  │
└─────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────┐
│ Tareas Post-Actualización           │
│ - Actualizar portfolios             │
│ - Verificar alertas                 │
└─────────────────────────────────────┘
```

---

## 📊 Estructura de Datos en Redis

### Vista completa de keys en Redis

Después de que el sistema haya estado funcionando por varias horas, Redis contendrá:

```bash
redis-cli KEYS *

# Resultado:
# 1) "premiumPrices::AAPL"
# 2) "premiumPrices::MSFT"
# 3) "premiumPrices::GOOGL"
# ... (20 keys de premium)
#
# 21) "standardPrices::WFC"
# 22) "standardPrices::JNJ"
# ... (42 keys de standard)
#
# 63) "extendedPrices::ARKK"
# 64) "extendedPrices::TLT"
# ... (18 keys de extended)
#
# 81) "historical:AAPL"
# 82) "historical:MSFT"
# ... (80 ZSET de históricos)
```

### Inspeccionar una key de caché

```bash
# Ver contenido de caché PREMIUM
redis-cli GET "premiumPrices::AAPL"

# Resultado (JSON serializado):
{
  "symbol": "AAPL",
  "precio": 195.50,
  "timestamp": "2024-11-30T15:30:00",
  "variacionPorcentual": 1.25,
  "open": 194.00,
  "high": 196.00,
  "low": 193.50,
  "close": 195.50,
  ...
}

# Ver TTL restante (segundos)
redis-cli TTL "premiumPrices::AAPL"
# Resultado: 897 (quedan ~15 minutos)
```

### Inspeccionar un ZSET de históricos

```bash
# Ver número de entradas en histórico de AAPL
redis-cli ZCARD "historical:AAPL"
# Resultado: 21 (21 actualizaciones en las últimas 24h)

# Ver las últimas 5 entradas (más recientes)
redis-cli ZREVRANGE "historical:AAPL" 0 4 WITHSCORES

# Resultado:
# 1) "{\"symbol\":\"AAPL\",\"precio\":195.50,...}"
# 2) "1701352800000"  ← Score (timestamp)
# 3) "{\"symbol\":\"AAPL\",\"precio\":195.20,...}"
# 4) "1701351600000"
# ...

# Consultar rango temporal (últimas 6 horas)
# Timestamp actual: 1701360000000
# Timestamp hace 6h: 1701338400000
redis-cli ZRANGEBYSCORE "historical:AAPL" 1701338400000 1701360000000
```

### Estadísticas de memoria

```bash
# Ver uso de memoria total
redis-cli INFO memory

# Resultado:
# used_memory_human: 2.45M
# used_memory_peak_human: 3.12M

# Ver tamaño de una key específica
redis-cli MEMORY USAGE "historical:AAPL"
# Resultado: 15632 (bytes)

# Ver todas las keys y su tamaño
redis-cli --bigkeys
```

---

## 📖 Guía de Uso

### Configuración inicial

1. **Instalar Redis** (si no está instalado):
   ```bash
   # Windows (Memurai)
   Download: https://www.memurai.com/get-memurai

   # Docker
   docker run -d -p 6379:6379 redis:7-alpine

   # WSL/Linux
   sudo apt-get install redis-server
   redis-server
   ```

2. **Verificar conexión**:
   ```bash
   redis-cli ping
   # Respuesta esperada: PONG
   ```

3. **Configurar Spring Boot** (ya está configurado en `application.properties`):
   ```properties
   spring.data.redis.host=localhost
   spring.data.redis.port=6379
   spring.cache.type=redis
   ```

4. **Ejecutar aplicación**:
   ```bash
   mvn spring-boot:run
   ```

### Uso desde código

#### Consultar precio actual (con caché)

```java
@Autowired
private MarketDataService marketDataService;

// Spring Cache lo maneja automáticamente
MarketData precio = marketDataService.obtenerDatos("AAPL");
System.out.println("Precio AAPL: $" + precio.getPrecio());

// Primera llamada: MySQL (50ms)
// Llamadas subsecuentes: Redis (5ms) durante 20 minutos
```

#### Consultar histórico intraday (ZSET)

```java
@Autowired
private MarketDataRedisRepository redisRepository;

// Obtener últimas 10 actualizaciones de AAPL
List<MarketData> ultimos10 = redisRepository
    .getLatestHistoricalData("AAPL", 10);

// Obtener histórico de las últimas 6 horas
LocalDateTime ahora = LocalDateTime.now();
LocalDateTime hace6h = ahora.minusHours(6);

List<MarketData> historico6h = redisRepository
    .getHistoricalData("AAPL", hace6h, ahora);

// Estadísticas de históricos
String stats = redisRepository.getHistoricalStats("AAPL");
System.out.println(stats);
// Output: "Symbol AAPL: 21 entries | Latest: 2024-11-30T15:30:00"
```

#### Limpiar caché manualmente (si es necesario)

```java
@Autowired
private CacheManager cacheManager;

// Limpiar caché de un símbolo específico
Cache premiumCache = cacheManager.getCache("premiumPrices");
premiumCache.evict("AAPL");

// Limpiar toda la caché premium
premiumCache.clear();
```

---

## 🔍 Monitoreo y Debugging

### Comandos útiles de Redis CLI

```bash
# 1. Ver todas las keys
redis-cli KEYS *

# 2. Ver keys con patrón
redis-cli KEYS "premiumPrices::*"
redis-cli KEYS "historical:*"

# 3. Monitorear en tiempo real
redis-cli MONITOR
# Muestra TODOS los comandos que se ejecutan en Redis

# 4. Estadísticas del servidor
redis-cli INFO stats
redis-cli INFO memory
redis-cli INFO keyspace

# 5. Ver clientes conectados
redis-cli CLIENT LIST

# 6. Ver comandos más lentos
redis-cli SLOWLOG GET 10
```

### Logs de la aplicación

Cuando la aplicación arranca, verás estos logs si Redis está funcionando:

```
✅ LOGS ESPERADOS (Redis OK)
========================================
INFO  c.m.s.config.RedisConfig - Redis CacheManager initialized
INFO  c.m.s.config.RedisConfig - Caches created: premiumPrices, standardPrices, extendedPrices
INFO  o.s.data.redis.core.RedisTemplate - Connecting to Redis at localhost:6379
```

Cuando los schedulers actualizan:

```
=== Iniciando actualización PREMIUM (20 símbolos) | NYSE: OPEN (15:30-22:00 CET) ===
🔍 Llamando TwelveData para: AAPL
✓ Datos obtenidos para AAPL | Precio: $195.50
...
=== Guardando en Redis ZSET: 20 símbolos ===
Redis ZSET progreso: 20/20
=== Redis ZSET guardado: 20 símbolos ===
```

### Troubleshooting Logs

```
❌ ERROR: Cannot connect to Redis at localhost:6379
→ Solución: Verificar que Redis esté corriendo (redis-cli ping)

⚠️ WARN: Cache 'premiumPrices' could not be acquired
→ Solución: Verificar configuración en application.properties

❌ ERROR: WRONGTYPE Operation against a key holding the wrong kind of value
→ Solución: Limpiar Redis (redis-cli FLUSHDB) y reiniciar app
```

---

## 🐛 Troubleshooting

### Problema 1: Redis no conecta

**Síntoma:**
```
org.springframework.data.redis.RedisConnectionFailureException:
Unable to connect to Redis; nested exception is
io.lettuce.core.RedisConnectionException: Unable to connect to localhost:6379
```

**Soluciones:**

1. **Verificar que Redis está corriendo:**
   ```bash
   redis-cli ping
   # Esperado: PONG
   ```

2. **Verificar puerto:**
   ```bash
   netstat -an | findstr 6379
   # Esperado: LISTENING
   ```

3. **Reiniciar Redis:**
   ```bash
   # Windows (Memurai)
   net stop Memurai
   net start Memurai

   # Docker
   docker restart spyzer-redis-dev

   # Linux
   sudo systemctl restart redis
   ```

---

### Problema 2: Caché no se actualiza

**Síntoma:** Los precios en la app son antiguos, aunque Redis está funcionando.

**Diagnóstico:**
```bash
# Ver TTL de una key
redis-cli TTL "premiumPrices::AAPL"
# Si retorna -1: La key no tiene TTL (¡problema!)
# Si retorna número: TTL correcto en segundos
```

**Soluciones:**

1. **Limpiar caché manualmente:**
   ```bash
   redis-cli FLUSHDB
   ```

2. **Verificar que @CacheEvict está funcionando:**
   - Revisar logs del scheduler
   - Confirmar que `@CacheEvict` está ANTES de la actualización
   ```java
   @CacheEvict(value = "premiumPrices", allEntries = true)
   public void actualizarGrupoPremium() { ... }
   ```

---

### Problema 3: Datos históricos crecen sin control

**Síntoma:** Redis consume cada vez más memoria.

**Diagnóstico:**
```bash
# Ver tamaño de ZSET
redis-cli ZCARD "historical:AAPL"
# Si retorna >100: Problema, debería limpiarse cada 24h
```

**Solución:**

1. **Verificar limpieza automática:**
   ```java
   // En MarketDataRedisRepository.addHistoricalData()
   cleanOldData(marketData.getSymbol()); // ← Debe estar presente
   ```

2. **Limpiar manualmente:**
   ```bash
   # Limpiar ZSET de AAPL
   redis-cli DEL "historical:AAPL"

   # Limpiar TODOS los históricos
   redis-cli KEYS "historical:*" | xargs redis-cli DEL
   ```

---

### Problema 4: Serialización fallida

**Síntoma:**
```
com.fasterxml.jackson.databind.exc.InvalidDefinitionException:
No serializer found for class com.miguel.spyzer.entities.MarketData
```

**Solución:**

Verificar que `MarketData` tiene anotación `@Data` (Lombok):
```java
@Entity
@Data  // ← REQUERIDO para serialización
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketData { ... }
```

---

## 📈 Métricas de Rendimiento

### Sin Redis (baseline)

```
Usuarios concurrentes: 10
Consulta: GET /api/market-data/AAPL

Tiempo promedio:     45ms
Queries a MySQL:     100/seg
CPU MySQL:           65%
```

### Con Redis (optimizado)

```
Usuarios concurrentes: 10
Consulta: GET /api/market-data/AAPL

Tiempo promedio:     5ms  (9x más rápido)
Queries a MySQL:     2/seg (50x menos)
CPU MySQL:           15%
Hit Rate Redis:      98%
```

### Consumo de memoria Redis

```
80 símbolos con caché:        ~1.5 MB
80 símbolos con históricos:   ~3.0 MB
Total:                        ~4.5 MB

Conclusión: Redis consume muy poca memoria para el beneficio que ofrece.
```

---

## 🎓 Conceptos Clave

### ¿Qué es TTL (Time To Live)?

TTL es el tiempo que un dato permanece en caché antes de expirar automáticamente.

```
15:30 → SET premiumPrices::AAPL (TTL=20min)
15:35 → GET premiumPrices::AAPL ✅ (quedan 15min)
15:49 → GET premiumPrices::AAPL ✅ (queda 1min)
15:51 → GET premiumPrices::AAPL ❌ (expiró, va a MySQL)
```

### ¿Qué es Cache Eviction?

Es el proceso de **invalidar/eliminar** datos de caché. En Spyzer:
- `@CacheEvict` elimina entradas ANTES de actualizar
- Garantiza que la caché nunca tenga datos obsoletos

### ¿Qué es ZSET (Sorted Set)?

ZSET es una estructura de datos de Redis que combina:
- **Set**: Valores únicos (no duplicados)
- **Sorted**: Ordenados por un "score" numérico

Ideal para:
- Series temporales (score = timestamp)
- Leaderboards (score = puntuación)
- Rankings

---

## 📚 Referencias

- [Redis Documentation](https://redis.io/documentation)
- [Spring Cache Abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)
- [Redis ZSET Commands](https://redis.io/commands/?group=sorted-set)
- [Cache-Aside Pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/cache-aside)

---

## ✅ Checklist de Verificación

Usa esta checklist para confirmar que Redis está correctamente implementado:

- [ ] Redis está corriendo (`redis-cli ping` responde PONG)
- [ ] Aplicación arranca sin errores de conexión
- [ ] Logs muestran "Redis CacheManager initialized"
- [ ] `redis-cli KEYS *` muestra keys de caché después de consultas
- [ ] `redis-cli ZCARD historical:AAPL` retorna > 0 después de actualizaciones
- [ ] TTLs son correctos: `redis-cli TTL premiumPrices::AAPL` retorna ~1200 seg
- [ ] Schedulers limpian caché (logs muestran "@CacheEvict")
- [ ] Memoria de Redis estable (~5MB) sin crecimiento descontrolado

---

**Autor:** Sistema Spyzer
**Versión:** 1.0
**Última actualización:** 2024-11-30
