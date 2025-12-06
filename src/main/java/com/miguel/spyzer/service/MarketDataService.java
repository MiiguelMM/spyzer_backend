package com.miguel.spyzer.service;

import com.miguel.spyzer.entities.Alert;
import com.miguel.spyzer.entities.MarketData;
import com.miguel.spyzer.entities.HistoricalDataPoint;
import com.miguel.spyzer.entities.Portfolio;
import com.miguel.spyzer.repository.MarketDataRepository;
import com.miguel.spyzer.repository.HistoricalDataRepository;
import com.miguel.spyzer.repository.PortfolioRepository;
import com.miguel.spyzer.repository.MarketDataRedisRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import static com.miguel.spyzer.entities.MarketData.DataType.REALTIME;

@Service
public class MarketDataService {

    @Value("${twelvedata.api.key:demo}")
    private String twelveDataApiKey;

    private static final String TWELVE_DATA_URL = "https://api.twelvedata.com";

    private final Object historicalLock = new Object();

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private MarketDataRepository marketDataRepository;

    @Autowired
    private HistoricalDataRepository historicalDataRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private AlertService alertService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private MarketHoursService marketHoursService;

    @Autowired
    private com.miguel.spyzer.config.SymbolGroupConfig symbolGroupConfig;

    @Autowired
    private MarketDataRedisRepository marketDataRedisRepository;

    @Autowired
    private ApiRateLimiter apiRateLimiter;

    // Self-injection para acceder al proxy de Spring y hacer que @Cacheable funcione
    // @Lazy rompe la referencia circular permitiendo que Spring termine de crear el bean primero
    @Lazy
    @Autowired
    private MarketDataService self;

    // Lista maestra de TODOS los símbolos (80 total)
    // Usada para validaciones y como referencia completa
    // Los símbolos se actualizan en 3 grupos según SymbolGroupConfig:
    // - PREMIUM (20): cada 20 min
    // - STANDARD (42): cada 60 min
    // - EXTENDED (18): cada 90 min
    private static final List<String> TODOS_LOS_SIMBOLOS = Arrays.asList(
            // === GRUPO PREMIUM (20 símbolos) ===
            // Índices principales
            "SPY", "QQQ", "DAX",
            // Mega Tech
            "AAPL", "MSFT", "GOOGL", "AMZN", "META", "TSLA", "NVDA", "NFLX", "AMD",
            // Top Financieros
            "JPM", "V", "MA",
            // International Tech
            "BABA", "TSM", "ADBE",
            // Enterprise Software
            "ORCL", "CRM",

            // === GRUPO STANDARD (42 símbolos) ===
            // ETF China
            "FXI",
            // Financieros adicionales
            "WFC", "GS",
            // Salud
            "JNJ", "PFE", "UNH", "ABT", "TMO",
            // Consumo
            "WMT", "HD", "MCD", "NKE", "SBUX", "KO", "PG",
            // Energía
            "XOM", "CVX", "COP",
            // Media
            "DIS", "CMCSA",
            // Tech adicional
            "CSCO", "INTC", "QCOM",
            // ETFs
            "IWM", "DIA", "VTI", "XLF", "XLK", "XLE",
            // European ADRs
            "NVO", "ASML",
            // Semiconductors adicionales
            "SMH", "SOXX",
            // Fintech
            "PYPL", "SQ", "COIN",
            // E-commerce/Gig
            "SHOP", "UBER", "LYFT",

            // === GRUPO EXTENDED (18 símbolos) ===
            // ETFs de Innovación/Growth
            "ARKK",
            // Commodities & Treasuries
            "TLT", "GLD", "SLV",
            // Biotech
            "XBI", "IBB",
            // Clean Energy
            "TAN", "ICLN",
            // Retail
            "XRT",
            // Homebuilders
            "XHB", "ITB",
            // Regional Banks
            "KRE",
            // Streaming/Entertainment
            "SPOT", "ROKU",
            // Cloud/Cybersecurity
            "NET", "CRWD", "ZS");

    // Solo los 4 índices para históricos
    private static final List<String> INDICES = Arrays.asList("SPY", "QQQ", "DAX", "FXI");

    // ==================== ACTUALIZACIÓN PRECIOS ACTUALES (CADA 100 MINUTOS)
    // ====================

    // ==================== MÉTODO REFACTORIZADO PARA ACTUALIZACIÓN POR GRUPOS
    // ====================

    /**
     * Actualiza un grupo específico de símbolos.
     * Este método reemplaza la lógica de actualizarTodosLosDatos() permitiendo
     * actualizaciones parciales de símbolos según su grupo.
     *
     * @param simbolos    Lista de símbolos a actualizar
     * @param grupoNombre Nombre del grupo para logging (ej: "PREMIUM", "STANDARD")
     */
    @Transactional
    private void actualizarGrupoDeSimbolos(List<String> simbolos, String grupoNombre) {
        System.out.println(
                "=== Iniciando actualización " + grupoNombre + " (" + simbolos.size() + " símbolos) | "
                        + marketHoursService.getMarketStatusInfo() + " ===");

        List<MarketData> datosNuevos = new ArrayList<>();
        List<String> simbolosExitosos = new ArrayList<>();
        List<String> simbolosFallidos = new ArrayList<>();
        int llamadasExitosas = 0;

        for (String symbol : simbolos) {
            try {
                MarketData datos = obtenerDatosTwelveData(symbol);
                if (datos != null) {
                    datosNuevos.add(datos);
                    simbolosExitosos.add(symbol);
                    llamadasExitosas++;

                    if (llamadasExitosas % 10 == 0) {
                        System.out.println("Progreso " + grupoNombre + ": " + llamadasExitosas + "/" + simbolos.size());
                    }
                } else {
                    simbolosFallidos.add(symbol + " (respuesta vacía)");
                }
                // El rate limiting ahora se maneja dentro de obtenerDatosTwelveData()

            } catch (Exception e) {
                simbolosFallidos.add(symbol + " (" + e.getMessage() + ")");
                System.err.println("Error obteniendo " + symbol + ": " + e.getMessage());
            }
        }

        if (llamadasExitosas > 0) {
            // Guardar nuevos datos en BD
            for (String symbol : simbolos) {
                marketDataRepository.deleteBySymbol(symbol);
            }
            marketDataRepository.saveAll(datosNuevos);

            // RESUMEN DETALLADO
            System.out.println("\n========================================");
            System.out.println("RESUMEN ACTUALIZACIÓN " + grupoNombre);
            System.out.println("========================================");
            System.out.println("✅ EXITOSOS (" + simbolosExitosos.size() + "/" + simbolos.size() + "):");
            System.out.println("   " + String.join(", ", simbolosExitosos));

            if (!simbolosFallidos.isEmpty()) {
                System.out.println("\n❌ FALLIDOS (" + simbolosFallidos.size() + "):");
                for (String fallo : simbolosFallidos) {
                    System.out.println("   - " + fallo);
                }
            }
            System.out.println("========================================\n");

            // 1. GUARDAR EN REDIS ZSET (históricos de todos los símbolos actualizados)
            guardarEnRedisZSET(datosNuevos);

            // 2. GUARDAR PUNTOS HISTÓRICOS DE LOS 4 ÍNDICES PRINCIPALES
            guardarPuntosHistoricosIndices(datosNuevos);

            // 3. VERIFICAR Y DISPARAR ALERTAS
            verificarAlertasConNuevosPrecios(datosNuevos);
        } else {
            System.err.println("\n========================================");
            System.err.println("❌ ACTUALIZACIÓN " + grupoNombre + " COMPLETAMENTE FALLIDA");
            System.err.println("Símbolos fallidos: " + String.join(", ", simbolosFallidos));
            System.err.println("========================================\n");
        }
    }

    /**
     * Guarda datos de mercado en Redis ZSET para históricos intraday.
     *
     * Cada símbolo tiene su propio ZSET con:
     * - Key: "historical:{symbol}"
     * - Score: timestamp (para ordenación temporal)
     * - Value: objeto MarketData
     * - TTL: 24 horas (rolling window automático)
     *
     * Esto permite análisis de tendencias intraday sin golpear MySQL.
     */
    private void guardarEnRedisZSET(List<MarketData> datosNuevos) {
        try {
            System.out.println("=== Guardando en Redis ZSET: " + datosNuevos.size() + " símbolos ===");
            int guardados = 0;

            for (MarketData datos : datosNuevos) {
                try {
                    marketDataRedisRepository.addHistoricalData(datos);
                    guardados++;

                    if (guardados % 20 == 0) {
                        System.out.println("Redis ZSET progreso: " + guardados + "/" + datosNuevos.size());
                    }
                } catch (Exception e) {
                    System.err.println("Error guardando en Redis ZSET " + datos.getSymbol() + ": " + e.getMessage());
                }
            }

            System.out.println("=== Redis ZSET guardado: " + guardados + " símbolos ===");
        } catch (Exception e) {
            System.err.println("Error general guardando en Redis ZSET: " + e.getMessage());
            // No lanzar excepción - Redis es opcional, el sistema debe funcionar sin él
        }
    }

    // ==================== SCHEDULERS POR GRUPO (SOLO DURANTE HORARIO NYSE)
    // ====================

    /**
     * Actualiza símbolos PREMIUM cada 20 minutos (solo durante horario NYSE).
     * Grupo: 20 símbolos de mayor importancia/volatilidad.
     *
     * Cache Eviction: Limpia caché de premiumPrices después de actualizar.
     */
    @Scheduled(fixedRate = 1200000) // 20 minutos
    @Transactional
    @CacheEvict(value = "premiumPrices", allEntries = true, beforeInvocation = true)
    public void actualizarGrupoPremium() {
      
        if (!marketHoursService.isNYSEOpen()) {
        System.out.println("⏸️ Scheduler PREMIUM pausado - Mercado NYSE cerrado | "
        + marketHoursService.getMarketStatusInfo());
        return;
        }

        actualizarGrupoDeSimbolos(
                symbolGroupConfig.getSymbolsByFrequency(
                        com.miguel.spyzer.config.SymbolGroupConfig.UpdateFrequency.PREMIUM_20MIN),
                "PREMIUM");

        // ACTUALIZAR TODOS LOS PORTFOLIOS (solo aquí, cada 20 min)
        actualizarPreciosEnPortfolios();
    }

    /**
     * Actualiza símbolos ESTÁNDAR cada 60 minutos (solo durante horario NYSE).
     * Grupo: 42 símbolos de importancia media.
     *
     * Cache Eviction: Limpia caché de standardPrices después de actualizar.
     */
    @Scheduled(fixedRate = 3600000) // 60 minutos
    @Transactional
    @CacheEvict(value = "standardPrices", allEntries = true, beforeInvocation = true)
    public void actualizarGrupoEstandar() {
     
        if (!marketHoursService.isNYSEOpen()) {
        System.out.println("⏸️ Scheduler ESTÁNDAR pausado - Mercado NYSE cerrado | "
        + marketHoursService.getMarketStatusInfo());
        return;
        }

        actualizarGrupoDeSimbolos(
                symbolGroupConfig.getSymbolsByFrequency(
                        com.miguel.spyzer.config.SymbolGroupConfig.UpdateFrequency.STANDARD_60MIN),
                "ESTÁNDAR");
    }

    /**
     * Actualiza símbolos EXTENDIDOS cada 90 minutos (solo durante horario NYSE).
     * Grupo: 18 símbolos adicionales (ETFs temáticos, sectores específicos).
     *
     * Cache Eviction: Limpia caché de extendedPrices después de actualizar.
     */
    @Scheduled(fixedRate = 5400000) // 90 minutos
    @Transactional
    @CacheEvict(value = "extendedPrices", allEntries = true, beforeInvocation = true)
    public void actualizarGrupoExtendido() {
        
        if (!marketHoursService.isNYSEOpen()) {
        System.out.println("⏸️ Scheduler EXTENDIDO pausado - Mercado NYSE cerrado | "
        + marketHoursService.getMarketStatusInfo());
        return;
        }

        actualizarGrupoDeSimbolos(
                symbolGroupConfig.getSymbolsByFrequency(
                        com.miguel.spyzer.config.SymbolGroupConfig.UpdateFrequency.EXTENDED_90MIN),
                "EXTENDIDO");
    }

    /**
     * Actualiza los 4 índices principales 1 hora después del cierre del mercado.
     * Se ejecuta a las 5:00 PM ET (23:00 hora España) de lunes a viernes.
     *
     * Esto asegura capturar los precios de cierre definitivos del día,
     * ya que a veces hay ajustes post-cierre que no se reflejan durante el trading.
     *
     * Coste: 4 llamadas/día (SPY, QQQ, DAX, FXI)
     */
    @Scheduled(cron = "0 0 17 * * MON-FRI", zone = "America/New_York")
    @Transactional
    public void actualizarIndicesPostCierre() {
        System.out.println("\n========================================");
        System.out.println("=== ACTUALIZACIÓN POST-CIERRE DE ÍNDICES ===");
        System.out.println("Hora: " + marketHoursService.getMarketStatusInfo());
        System.out.println("========================================\n");

        // Actualizar solo los 4 índices principales
        actualizarGrupoDeSimbolos(INDICES, "POST-CIERRE ÍNDICES");

        System.out.println("\n=== Actualización post-cierre completada ===\n");
    }

    /**
     * Guardar puntos históricos de los 4 índices principales cada vez que se
     * actualiza
     */
    private void guardarPuntosHistoricosIndices(List<MarketData> datosNuevos) {
        System.out.println("=== Guardando puntos históricos de índices principales ===");
        System.out.println("Total de datos nuevos recibidos: " + datosNuevos.size());
        System.out.println("Índices a buscar: " + INDICES);

        List<HistoricalDataPoint> puntosHistoricos = new ArrayList<>();
        int guardados = 0;

        for (MarketData datos : datosNuevos) {
            System.out.println("Procesando símbolo: " + datos.getSymbol() + " | Es índice: "
                    + INDICES.contains(datos.getSymbol()));

            // Solo guardar si es uno de los 4 índices principales
            if (INDICES.contains(datos.getSymbol())) {
                try {
                    HistoricalDataPoint punto = HistoricalDataPoint.builder()
                            .symbol(datos.getSymbol())
                            .date(java.time.Instant.now().toString())
                            .open(datos.getOpen())
                            .high(datos.getHigh())
                            .low(datos.getLow())
                            .close(datos.getClose())
                            .volume(datos.getVolumen())
                            .build();

                    puntosHistoricos.add(punto);
                    guardados++;
                    System.out.println("✓ Punto histórico creado para " + datos.getSymbol() +
                            " | Close: " + datos.getClose());
                } catch (Exception e) {
                    System.err.println(
                            "✗ Error creando punto histórico para " + datos.getSymbol() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        if (!puntosHistoricos.isEmpty()) {
            historicalDataRepository.saveAll(puntosHistoricos);
            System.out.println("=== Puntos históricos guardados en BD: " + guardados + " índices ===");
        } else {
            System.err.println("⚠️ ADVERTENCIA: No se guardaron puntos históricos. ¿No hay índices en datosNuevos?");
        }
    }

    /**
     * Actualizar precios actuales en todos los portfolios después de actualizar
     * MarketData
     */
    private void actualizarPreciosEnPortfolios() {
        System.out.println("=== Actualizando precios en Portfolios ===");

        List<Portfolio> todasLasPosiciones = portfolioRepository.findAll();
        int actualizadas = 0;
        int noEncontradas = 0;

        for (Portfolio posicion : todasLasPosiciones) {
            try {
                MarketData datos = obtenerDatos(posicion.getSymbol());

                if (datos != null && datos.getPrecio() != null) {
                    posicion.setPrecioActual(datos.getPrecio());
                    posicion.calcularValorMercado();
                    posicion.calcularGananciaPerdida();
                    portfolioRepository.save(posicion);
                    actualizadas++;

                    System.out.println("Portfolio actualizado: " + posicion.getSymbol() +
                            " | Precio: $" + datos.getPrecio() +
                            " | G/P: $" + posicion.getGananciaPerdida());
                } else {
                    noEncontradas++;
                    System.err.println("No se encontraron datos para " + posicion.getSymbol());
                }
            } catch (Exception e) {
                System.err.println("Error actualizando portfolio para " + posicion.getSymbol() + ": " + e.getMessage());
            }
        }

        System.out.println("=== Portfolios actualizados: " + actualizadas + "/" + todasLasPosiciones.size() +
                " (sin datos: " + noEncontradas + ") ===");
    }

    /**
     * Verificar todas las alertas activas con los nuevos precios obtenidos
     */
    private void verificarAlertasConNuevosPrecios(List<MarketData> datosNuevos) {
        System.out.println("=== Verificando alertas con nuevos precios ===");

        try {
            // Crear mapa de precios actuales: symbol -> precio
            Map<String, BigDecimal> preciosActuales = new HashMap<>();
            for (MarketData datos : datosNuevos) {
                if (datos != null && datos.getSymbol() != null && datos.getPrecio() != null) {
                    preciosActuales.put(datos.getSymbol(), datos.getPrecio());
                }
            }

            if (preciosActuales.isEmpty()) {
                System.out.println("No hay precios disponibles para verificar alertas");
                return;
            }

            // Verificar todas las alertas activas del sistema
            List<Alert> alertasDisparadas = alertService.verificarTodasLasAlertas(preciosActuales);

            if (!alertasDisparadas.isEmpty()) {
                System.out.println("=== ¡ALERTAS DISPARADAS! ===");
                for (Alert alerta : alertasDisparadas) {

                    System.out.println("🔔 ALERTA: " + alerta.getSymbol() +
                            " | Tipo: " + alerta.getTipo() +
                            " | Trigger: $" + alerta.getValorTrigger() +
                            " | Usuario: " + alerta.getUser().getId() +
                            " | Mensaje: " + alerta.getMensajeCompleto());

                    try {
                        notificationService.enviarNotificacionAlerta(alerta);
                        System.out.println("✅ Notificación enviada para alerta " + alerta.getId());
                    } catch (Exception e) {
                        System.err.println(
                                "❌ Error enviando notificación para alerta " + alerta.getId() + ": " + e.getMessage());
                    }

                }
                System.out.println("=== Total alertas disparadas: " + alertasDisparadas.size() + " ===");
            } else {
                System.out.println("No se dispararon alertas en esta actualización");
            }

        } catch (Exception e) {
            System.err.println("Error verificando alertas: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== RESETEO Y RECARGA HISTÓRICOS (CADA MES)
    // ====================

    @Scheduled(fixedRate = 2592000000L) // 30 días (30 * 24 * 60 * 60 * 1000)
    @Transactional
    public void resetearYRecargarHistoricos() {
        System.out.println("=== Iniciando reseteo y recarga de históricos (" + INDICES.size() + " símbolos) ===");

        // 1. ELIMINAR TODOS LOS DATOS HISTÓRICOS
        System.out.println("Eliminando todos los datos históricos...");
        historicalDataRepository.deleteAll();
        System.out.println("Datos históricos eliminados ✓");

        int actualizados = 0;

        // 2. RECARGAR HISTÓRICOS DE LOS ÚLTIMOS 2 AÑOS
        for (String symbol : INDICES) {
            synchronized (historicalLock) {
                try {
                    System.out.println("Cargando históricos de " + symbol + " (2 años)...");
                    // El rate limiting se maneja dentro de obtenerHistoricoDesdeAPI()

                    List<HistoricalDataPoint> datos = obtenerHistoricoDesdeAPI(symbol, 730); // 730 días = ~2 años

                    if (!datos.isEmpty()) {
                        // Guardar nuevos
                        historicalDataRepository.saveAll(datos);

                        actualizados++;
                        System.out.println("Históricos de " + symbol + " cargados: " + datos.size() + " puntos");
                    }

                } catch (Exception e) {
                    System.err.println("Error cargando históricos de " + symbol + ": " + e.getMessage());
                }
            }
        }

        System.out.println("=== Reseteo y recarga completada: " + actualizados + "/" + INDICES.size() + " ===");
    }

    // ==================== DATOS HISTÓRICOS - LEER DESDE BD ====================

    public List<HistoricalDataPoint> obtenerHistorico(String symbol, int days) {
        // Leer directamente de BD
        List<HistoricalDataPoint> historicos = historicalDataRepository
                .findBySymbolOrderByDateDesc(symbol.toUpperCase(), days);

        if (historicos.isEmpty()) {
            System.out.println("No hay históricos en BD para " + symbol + ", obteniendo de API...");
            return obtenerHistoricoDesdeAPI(symbol, days);
        }

        return historicos;
    }

    private List<HistoricalDataPoint> obtenerHistoricoDesdeAPI(String symbol, int days) {
        try {
            // RATE LIMITING GLOBAL: Esperar si es necesario para respetar límite de 8
            // llamadas/min
            apiRateLimiter.esperarSiEsNecesario();

            String interval = "1day";
            String outputsize = String.valueOf(days);

            String url = String.format("%s/time_series?symbol=%s&interval=%s&outputsize=%s&apikey=%s",
                    TWELVE_DATA_URL, symbol, interval, outputsize, twelveDataApiKey);

            System.out.println("Llamando a TwelveData histórico: " + symbol);

            TwelveDataTimeSeriesResponse response = restTemplate.getForObject(url,
                    TwelveDataTimeSeriesResponse.class);

            if (response != null && response.getValues() != null && !response.getValues().isEmpty()) {
                System.out.println(
                        "Históricos obtenidos de API: " + symbol + " (" + response.getValues().size() + " puntos)");
                return parsearHistoricoTwelveData(response.getValues(), symbol);
            } else {
                System.err.println("Respuesta vacía para históricos de " + symbol);
            }

        } catch (Exception e) {
            System.err.println("Error obteniendo históricos de " + symbol + ": " + e.getMessage());
        }

        return new ArrayList<>();
    }

    // ==================== MÉTODOS PÚBLICOS ====================

    /**
     * Obtiene datos de mercado para un símbolo.
     *
     * Cache Strategy:
     * - Premium symbols: cached 20 min
     * - Standard symbols: cached 60 min
     * - Extended symbols: cached 90 min
     *
     * El método determina automáticamente qué caché usar basándose en el grupo del
     * símbolo.
     */
    public MarketData obtenerDatos(String symbol) {
        String upperSymbol = symbol.toUpperCase();

        // Determinar el grupo del símbolo para usar la caché correcta
        com.miguel.spyzer.config.SymbolGroupConfig.UpdateFrequency frequency = symbolGroupConfig
                .getFrequencyForSymbol(upperSymbol);

        if (frequency == null) {
            // Símbolo no está en ningún grupo, no cachear
            return marketDataRepository.findTopBySymbolOrderByTimestampDesc(upperSymbol);
        }

        // Llamar a través del proxy (self) para que @Cacheable funcione correctamente
        // Si llamamos directamente (this.metodo), Spring AOP no puede interceptar la llamada
        return switch (frequency) {
            case PREMIUM_20MIN -> self.obtenerDatosPremiumCache(upperSymbol);
            case STANDARD_60MIN -> self.obtenerDatosStandardCache(upperSymbol);
            case EXTENDED_90MIN -> self.obtenerDatosExtendedCache(upperSymbol);
        };
    }

    @Cacheable(value = "premiumPrices", key = "#symbol")
    public MarketData obtenerDatosPremiumCache(String symbol) {
        return marketDataRepository.findTopBySymbolOrderByTimestampDesc(symbol);
    }

    @Cacheable(value = "standardPrices", key = "#symbol")
    public MarketData obtenerDatosStandardCache(String symbol) {
        return marketDataRepository.findTopBySymbolOrderByTimestampDesc(symbol);
    }

    @Cacheable(value = "extendedPrices", key = "#symbol")
    public MarketData obtenerDatosExtendedCache(String symbol) {
        return marketDataRepository.findTopBySymbolOrderByTimestampDesc(symbol);
    }

    public Map<String, MarketData> obtenerMultiplesDatos(String... symbols) {
        Map<String, MarketData> resultados = new HashMap<>();

        for (String symbol : symbols) {
            MarketData data = obtenerDatos(symbol);
            if (data != null) {
                resultados.put(symbol.toUpperCase(), data);
            }
        }

        return resultados;
    }

    public Map<String, MarketData> obtenerIndicesPrincipales() {
        Map<String, MarketData> indices = new HashMap<>();

        for (String symbol : INDICES) {
            MarketData data = obtenerDatos(symbol);
            if (data != null) {
                indices.put(symbol, data);
            }
        }

        return indices;
    }

    public boolean estaDisponible(String symbol) {
        return TODOS_LOS_SIMBOLOS.contains(symbol.toUpperCase());
    }

    public List<String> obtenerSimbolosDisponibles() {
        return new ArrayList<>(TODOS_LOS_SIMBOLOS);
    }

    // ==================== PARSERS ====================

    private MarketData obtenerDatosTwelveData(String symbol) {
        try {
            // RATE LIMITING GLOBAL: Esperar si es necesario para respetar límite de 8
            // llamadas/min
            apiRateLimiter.esperarSiEsNecesario();

            String url = String.format("%s/quote?symbol=%s&apikey=%s",
                    TWELVE_DATA_URL, symbol, twelveDataApiKey);

            System.out.println("🔍 Llamando TwelveData para: " + symbol);
            TwelveDataQuoteResponse response = restTemplate.getForObject(url, TwelveDataQuoteResponse.class);

            if (response != null && response.getClose() != null && response.getPreviousClose() != null) {
                BigDecimal price = new BigDecimal(response.getClose());
                BigDecimal previousClose = new BigDecimal(response.getPreviousClose());
                BigDecimal change = price.subtract(previousClose);
                BigDecimal changePercent = previousClose.compareTo(BigDecimal.ZERO) != 0 ? change
                        .divide(previousClose, 4, java.math.RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                        : BigDecimal.ZERO;

                System.out.println("✓ Datos obtenidos para " + symbol + " | Precio: $" + price);

                return MarketData.builder()
                        .symbol(symbol.toUpperCase())
                        .precio(price)
                        .open(response.getOpen() != null ? new BigDecimal(response.getOpen()) : price)
                        .high(response.getHigh() != null ? new BigDecimal(response.getHigh()) : price)
                        .low(response.getLow() != null ? new BigDecimal(response.getLow()) : price)
                        .close(price)
                        .volumen(response.getVolume() != null && !response.getVolume().isEmpty()
                                ? Long.parseLong(response.getVolume())
                                : null)
                        .precioAnterior(previousClose)
                        .variacionAbsoluta(change)
                        .variacionPorcentual(changePercent)
                        .dataType(REALTIME)
                        .timestamp(LocalDateTime.now())
                        .build();
            } else {
                System.err.println("✗ Respuesta vacía o incompleta para " + symbol);
                if (response != null) {
                    System.err.println("  - Close: " + response.getClose());
                    System.err.println("  - PreviousClose: " + response.getPreviousClose());
                }
            }

        } catch (Exception e) {
            System.err.println("✗ Error obteniendo datos de " + symbol + ": " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    private List<HistoricalDataPoint> parsearHistoricoTwelveData(List<TwelveDataValue> values, String symbol) {
        List<HistoricalDataPoint> result = new ArrayList<>();

        for (TwelveDataValue value : values) {
            try {
                result.add(HistoricalDataPoint.builder()
                        .symbol(symbol.toUpperCase())
                        .date(value.getDatetime())
                        .open(value.getOpen() != null ? new BigDecimal(value.getOpen()) : null)
                        .high(value.getHigh() != null ? new BigDecimal(value.getHigh()) : null)
                        .low(value.getLow() != null ? new BigDecimal(value.getLow()) : null)
                        .close(value.getClose() != null ? new BigDecimal(value.getClose()) : null)
                        .volume(value.getVolume() != null && !value.getVolume().isEmpty()
                                ? Long.parseLong(value.getVolume())
                                : null)
                        .build());
            } catch (Exception e) {
                System.err.println("Error parseando valor histórico: " + e.getMessage());
            }
        }

        return result;
    }

    // ==================== DTOs ====================

    public static class TwelveDataQuoteResponse {
        private String symbol;
        private String open;
        private String high;
        private String low;
        private String close;
        private String volume;
        @JsonProperty("previous_close")
        private String previousClose;

        public String getSymbol() {
            return symbol;
        }

        public String getOpen() {
            return open;
        }

        public String getHigh() {
            return high;
        }

        public String getLow() {
            return low;
        }

        public String getClose() {
            return close;
        }

        public String getVolume() {
            return volume;
        }

        public String getPreviousClose() {
            return previousClose;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public void setOpen(String open) {
            this.open = open;
        }

        public void setHigh(String high) {
            this.high = high;
        }

        public void setLow(String low) {
            this.low = low;
        }

        public void setClose(String close) {
            this.close = close;
        }

        public void setVolume(String volume) {
            this.volume = volume;
        }

        public void setPreviousClose(String previousClose) {
            this.previousClose = previousClose;
        }
    }

    public static class TwelveDataTimeSeriesResponse {
        private List<TwelveDataValue> values;

        public List<TwelveDataValue> getValues() {
            return values;
        }

        public void setValues(List<TwelveDataValue> values) {
            this.values = values;
        }
    }

    public static class TwelveDataValue {
        private String datetime;
        private String open;
        private String high;
        private String low;
        private String close;
        private String volume;

        public String getDatetime() {
            return datetime;
        }

        public String getOpen() {
            return open;
        }

        public String getHigh() {
            return high;
        }

        public String getLow() {
            return low;
        }

        public String getClose() {
            return close;
        }

        public String getVolume() {
            return volume;
        }

        public void setDatetime(String datetime) {
            this.datetime = datetime;
        }

        public void setOpen(String open) {
            this.open = open;
        }

        public void setHigh(String high) {
            this.high = high;
        }

        public void setLow(String low) {
            this.low = low;
        }

        public void setClose(String close) {
            this.close = close;
        }

        public void setVolume(String volume) {
            this.volume = volume;
        }
    }
}