package com.miguel.spyzer.service;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.ZoneId;

/**
 * Servicio para determinar si el mercado NYSE está abierto.
 *
 * Horario NYSE: Lunes a Viernes, 9:30 AM - 4:00 PM ET (cierre oficial)
 * Período de gracia: +15 minutos post-cierre (hasta 4:15 PM ET)
 * En hora de España (CET/CEST): 15:30 - 22:15 (incluye gracia)
 *
 * El período de gracia permite capturar actualizaciones finales durante after-hours.
 * Con 80 símbolos y gracia de 15 min: ~770 llamadas/día (96.2% del límite de 800).
 */
@Service
public class MarketHoursService {

    private static final ZoneId NYSE_ZONE = ZoneId.of("America/New_York");
    private static final ZoneId SPAIN_ZONE = ZoneId.of("Europe/Madrid");

    // Horario NYSE en hora local (ET)
    private static final int MARKET_OPEN_HOUR = 9;
    private static final int MARKET_OPEN_MINUTE = 30;
    private static final int MARKET_CLOSE_HOUR = 16;  // 4:00 PM ET (cierre oficial)
    private static final int MARKET_CLOSE_MINUTE = 0;

    // Período de gracia post-cierre (15 minutos) para capturar últimas actualizaciones
    // Permite actualizaciones hasta 4:15 PM ET (22:15 España)
    // Suficiente para que todos los grupos (Premium/Standard/Extended) capturen precios de cierre
    private static final int GRACE_PERIOD_MINUTES = 15;

    /**
     * Verifica si el mercado NYSE está actualmente abierto.
     *
     * @return true si es lunes-viernes y la hora está entre 9:30 AM - 4:00 PM ET
     */
    public boolean isNYSEOpen() {
        return isNYSEOpen(ZonedDateTime.now(NYSE_ZONE));
    }

    /**
     * Verifica si el mercado NYSE está abierto en un momento específico.
     *
     * @param time Momento a verificar (en cualquier timezone, se convertirá a ET)
     * @return true si es día laboral y está dentro del horario de trading
     */
    public boolean isNYSEOpen(ZonedDateTime time) {
        // Convertir a timezone de NYSE
        ZonedDateTime nyseTime = time.withZoneSameInstant(NYSE_ZONE);

        // Verificar día de la semana (lunes a viernes)
        DayOfWeek day = nyseTime.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }

        // Verificar horario (9:30 AM - 4:00 PM ET)
        int hour = nyseTime.getHour();
        int minute = nyseTime.getMinute();

        // Antes de 9:30 AM
        if (hour < MARKET_OPEN_HOUR) {
            return false;
        }
        if (hour == MARKET_OPEN_HOUR && minute < MARKET_OPEN_MINUTE) {
            return false;
        }

        // Después de 4:30 PM (4:00 PM + 30 min de gracia)
        int closeHourWithGrace = MARKET_CLOSE_HOUR;
        int closeMinuteWithGrace = MARKET_CLOSE_MINUTE + GRACE_PERIOD_MINUTES;

        // Ajustar si los minutos pasan de 60
        if (closeMinuteWithGrace >= 60) {
            closeHourWithGrace += closeMinuteWithGrace / 60;
            closeMinuteWithGrace = closeMinuteWithGrace % 60;
        }

        if (hour > closeHourWithGrace) {
            return false;
        }
        if (hour == closeHourWithGrace && minute >= closeMinuteWithGrace) {
            return false;
        }

        return true;
    }

    /**
     * Obtiene la próxima apertura del mercado NYSE.
     * Útil para logging y debugging.
     *
     * @return ZonedDateTime con la próxima apertura del mercado
     */
    public ZonedDateTime getNextMarketOpen() {
        ZonedDateTime now = ZonedDateTime.now(NYSE_ZONE);
        ZonedDateTime nextOpen = now.withHour(MARKET_OPEN_HOUR)
                                     .withMinute(MARKET_OPEN_MINUTE)
                                     .withSecond(0)
                                     .withNano(0);

        // Si ya pasó la hora de apertura hoy, ir al siguiente día laboral
        if (now.isAfter(nextOpen) || now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY) {
            nextOpen = nextOpen.plusDays(1);

            // Saltar fin de semana si es necesario
            while (nextOpen.getDayOfWeek() == DayOfWeek.SATURDAY || nextOpen.getDayOfWeek() == DayOfWeek.SUNDAY) {
                nextOpen = nextOpen.plusDays(1);
            }
        }

        return nextOpen;
    }

    /**
     * Obtiene la hora actual en formato para logging.
     * Muestra tanto hora NYSE como hora local de España.
     *
     * @return String con información de hora para debugging
     */
    public String getMarketStatusInfo() {
        ZonedDateTime nyseNow = ZonedDateTime.now(NYSE_ZONE);
        ZonedDateTime spainNow = ZonedDateTime.now(SPAIN_ZONE);
        boolean isOpen = isNYSEOpen();

        return String.format("NYSE: %s | España: %s | Mercado: %s",
            nyseNow.toLocalTime(),
            spainNow.toLocalTime(),
            isOpen ? "ABIERTO" : "CERRADO (próxima apertura: " + getNextMarketOpen().toLocalTime() + " ET)"
        );
    }
}
