package com.miguel.spyzer.config;

import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 * Configuración centralizada de grupos de símbolos para actualización de Market Data.
 *
 * Organiza los 85 símbolos en 3 grupos con diferentes frecuencias de actualización:
 * - PREMIUM: 20 símbolos, actualizados cada 20 minutos (los más importantes/volátiles)
 * - STANDARD: 42 símbolos, actualizados cada 60 minutos (importantes pero menos volátiles)
 * - EXTENDED: 23 símbolos, actualizados cada 90 minutos (símbolos adicionales)
 */
@Configuration
public class SymbolGroupConfig {

    public enum UpdateFrequency {
        PREMIUM_20MIN,
        STANDARD_60MIN,
        EXTENDED_90MIN
    }

    // GRUPO PREMIUM (20 símbolos) - Cada 20 minutos
    // Índices principales + Mega caps tech + Top financieros
    private static final List<String> PREMIUM_SYMBOLS = Arrays.asList(
        // Índices principales (4)
        "SPY", "QQQ", "DAX",

        // Mega Tech más negociadas (11)
        "AAPL", "MSFT", "GOOGL", "AMZN", "META", "TSLA", "NVDA", "NFLX", "AMD",

        // Top Financieros (3)
        "JPM", "V", "MA",

        // International Tech (3)
        "BABA", "TSM", "ADBE",

        // Enterprise Software (2)
        "ORCL", "CRM"
    );

    // GRUPO ESTÁNDAR (42 símbolos) - Cada 60 minutos
    // Resto de blue chips y ETFs sectoriales
    private static final List<String> STANDARD_SYMBOLS = Arrays.asList(
        // ETF China
        "FXI",

        // Financieros adicionales (2)
        "WFC", "GS",

        // Salud (5)
        "JNJ", "PFE", "UNH", "ABT", "TMO",

        // Consumo (7)
        "WMT", "HD", "MCD", "NKE", "SBUX", "KO", "PG",

        // Energía (3)
        "XOM", "CVX", "COP",

        // Media (2)
        "DIS", "CMCSA",

        // Tech adicional (3)
        "CSCO", "INTC", "QCOM",

        // ETFs (8)
        "IWM", "DIA", "VTI", "XLF", "XLK", "XLE",

        // European ADRs (2)
        "NVO", "ASML",

        // Semiconductors adicionales (2)
        "SMH", "SOXX",

        // Fintech (3)
        "PYPL", "SQ", "COIN",

        // E-commerce/Gig (3)
        "SHOP", "UBER", "LYFT"
    );

    // GRUPO EXTENDIDO (18 símbolos) - Cada 90 minutos
    // ETFs temáticos y sectores específicos (reducido para optimizar uso de API)
    private static final List<String> EXTENDED_SYMBOLS = Arrays.asList(
        // ETFs de Innovación/Growth (1)
        "ARKK",

        // Commodities & Treasuries (3) - Removido USO (muy volátil)
        "TLT", "GLD", "SLV",

        // Biotech (2)
        "XBI", "IBB",

        // Clean Energy (2) - Removido LIT (muy nicho)
        "TAN", "ICLN",

        // Retail (1)
        "XRT",

        // Homebuilders (2)
        "XHB", "ITB",

        // Regional Banks (1)
        "KRE",

        // Streaming/Entertainment (2) - Removido DKNG (muy especulativo)
        "SPOT", "ROKU",

        // Cloud/Cybersecurity (3)
        "NET", "CRWD", "ZS"

        // Removidos: RIVN, LCID (EV startups muy especulativos)
        // Removidos: JETS (airlines, sector inestable)
    );

    /**
     * Obtiene todos los símbolos de un grupo específico.
     *
     * @param frequency Frecuencia de actualización del grupo
     * @return Lista de símbolos en ese grupo
     */
    public List<String> getSymbolsByFrequency(UpdateFrequency frequency) {
        return switch (frequency) {
            case PREMIUM_20MIN -> new ArrayList<>(PREMIUM_SYMBOLS);
            case STANDARD_60MIN -> new ArrayList<>(STANDARD_SYMBOLS);
            case EXTENDED_90MIN -> new ArrayList<>(EXTENDED_SYMBOLS);
        };
    }

    /**
     * Obtiene la frecuencia de actualización para un símbolo específico.
     *
     * @param symbol Símbolo a consultar
     * @return Frecuencia de actualización del símbolo, o null si no está en ningún grupo
     */
    public UpdateFrequency getFrequencyForSymbol(String symbol) {
        String upperSymbol = symbol.toUpperCase();

        if (PREMIUM_SYMBOLS.contains(upperSymbol)) {
            return UpdateFrequency.PREMIUM_20MIN;
        }
        if (STANDARD_SYMBOLS.contains(upperSymbol)) {
            return UpdateFrequency.STANDARD_60MIN;
        }
        if (EXTENDED_SYMBOLS.contains(upperSymbol)) {
            return UpdateFrequency.EXTENDED_90MIN;
        }

        return null;
    }

    /**
     * Obtiene todos los símbolos configurados (85 total).
     *
     * @return Lista con todos los símbolos de los 3 grupos
     */
    public List<String> getAllSymbols() {
        List<String> allSymbols = new ArrayList<>();
        allSymbols.addAll(PREMIUM_SYMBOLS);
        allSymbols.addAll(STANDARD_SYMBOLS);
        allSymbols.addAll(EXTENDED_SYMBOLS);
        return allSymbols;
    }

    /**
     * Obtiene estadísticas de la configuración.
     *
     * @return String con información de grupos y totales
     */
    public String getConfigInfo() {
        return String.format(
            "Symbol Groups - Premium: %d (20min) | Standard: %d (60min) | Extended: %d (90min) | Total: %d",
            PREMIUM_SYMBOLS.size(),
            STANDARD_SYMBOLS.size(),
            EXTENDED_SYMBOLS.size(),
            getAllSymbols().size()
        );
    }
}
