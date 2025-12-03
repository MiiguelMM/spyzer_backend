package com.miguel.spyzer.service;

import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Rate Limiter global para llamadas API.
 *
 * Garantiza que NUNCA se supere el límite de 8 llamadas por minuto,
 * sin importar cuántos schedulers estén activos simultáneamente.
 *
 * Usa un sistema de ventanas deslizantes (sliding window) para rastrear
 * las llamadas realizadas en los últimos 60 segundos.
 */
@Service
public class ApiRateLimiter {

    private static final int MAX_LLAMADAS_POR_MINUTO = 8;
    private static final long VENTANA_TIEMPO_MS = 60_000; // 60 segundos

    // Cola thread-safe que almacena los timestamps de las últimas llamadas
    private final ConcurrentLinkedQueue<Long> llamadasRecientes = new ConcurrentLinkedQueue<>();

    // Lock para sincronizar el acceso crítico
    private final Object lock = new Object();

    /**
     * Espera hasta que sea seguro hacer una llamada API respetando el rate limit.
     *
     * Este método:
     * 1. Limpia llamadas antiguas (fuera de la ventana de 60s)
     * 2. Si hay menos de 8 llamadas en los últimos 60s, permite la llamada inmediatamente
     * 3. Si ya hay 8 llamadas, espera hasta que la más antigua salga de la ventana
     * 4. Registra la nueva llamada
     *
     * @throws InterruptedException si el thread es interrumpido durante la espera
     */
    public void esperarSiEsNecesario() throws InterruptedException {
        synchronized (lock) {
            long ahora = Instant.now().toEpochMilli();

            // 1. Limpiar llamadas antiguas (fuera de la ventana de 60 segundos)
            limpiarLlamadasAntiguas(ahora);

            // 2. Si ya hay 8 llamadas en la ventana, esperar
            while (llamadasRecientes.size() >= MAX_LLAMADAS_POR_MINUTO) {
                // Calcular cuánto tiempo esperar hasta que la llamada más antigua expire
                Long llamadaMasAntigua = llamadasRecientes.peek();
                if (llamadaMasAntigua == null) {
                    break; // No debería pasar, pero por seguridad
                }

                long tiempoExpiracion = llamadaMasAntigua + VENTANA_TIEMPO_MS;
                long tiempoEspera = tiempoExpiracion - ahora;

                if (tiempoEspera > 0) {
                    System.out.println("⏳ Rate limit alcanzado (" + MAX_LLAMADAS_POR_MINUTO
                            + " llamadas/min). Esperando " + (tiempoEspera / 1000) + "s...");
                    Thread.sleep(tiempoEspera + 100); // +100ms de margen de seguridad
                    ahora = Instant.now().toEpochMilli();
                    limpiarLlamadasAntiguas(ahora);
                } else {
                    // La llamada más antigua ya expiró, limpiar y continuar
                    llamadasRecientes.poll();
                }
            }

            // 3. Registrar esta nueva llamada
            llamadasRecientes.offer(ahora);

            int llamadasEnVentana = llamadasRecientes.size();
            System.out.println("✅ Llamada API permitida (" + llamadasEnVentana + "/"
                    + MAX_LLAMADAS_POR_MINUTO + " en ventana de 60s)");
        }
    }

    /**
     * Limpia las llamadas que están fuera de la ventana de tiempo (más de 60s atrás)
     */
    private void limpiarLlamadasAntiguas(long ahora) {
        long limiteVentana = ahora - VENTANA_TIEMPO_MS;

        // Eliminar todas las llamadas más antiguas que el límite
        while (!llamadasRecientes.isEmpty()) {
            Long primeraLlamada = llamadasRecientes.peek();
            if (primeraLlamada != null && primeraLlamada < limiteVentana) {
                llamadasRecientes.poll();
            } else {
                break; // La primera llamada aún está dentro de la ventana
            }
        }
    }

    /**
     * Obtiene el número de llamadas realizadas en la ventana actual (últimos 60s)
     */
    public int getLlamadasEnVentana() {
        synchronized (lock) {
            limpiarLlamadasAntiguas(Instant.now().toEpochMilli());
            return llamadasRecientes.size();
        }
    }

    /**
     * Resetea el rate limiter (útil para testing)
     */
    public void reset() {
        synchronized (lock) {
            llamadasRecientes.clear();
        }
    }
}
