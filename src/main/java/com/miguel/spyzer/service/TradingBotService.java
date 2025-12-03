package com.miguel.spyzer.service;

import com.miguel.spyzer.entities.TradingBot;
import com.miguel.spyzer.entities.User;
import com.miguel.spyzer.repository.TradingBotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TradingBotService {
    
    private final TradingBotRepository tradingBotRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * Crear un nuevo bot de trading
     */
    public TradingBot crearBot(Long userId, String nombre, TradingBot.Estrategia estrategia,
                             Map<String, Object> parametros, BigDecimal budget, BigDecimal stopLoss) {
        log.info("Creando nuevo bot para usuario {} - Nombre: {} - Estrategia: {} - Budget: ${}", 
                userId, nombre, estrategia, budget);
        
        // Verificar que no existe un bot con el mismo nombre para el usuario
        if (existeBotConNombre(userId, nombre)) {
            throw new IllegalArgumentException("Ya existe un bot con el nombre: " + nombre);
        }
        
        // Validaciones de negocio
        validarParametrosBot(budget, stopLoss);
        
        User user = new User();
        user.setId(userId);
        
        TradingBot nuevoBot = TradingBot.builder()
                .user(user)
                .nombre(nombre)
                .estrategia(estrategia)
                .parametros(convertirParametrosAJson(parametros))
                .budget(budget)
                .stopLoss(stopLoss)
                .activo(false) // Los bots se crean desactivados por seguridad
                .performance(BigDecimal.ZERO)
                .tradesHoy(0)
                .build();
        
        TradingBot botGuardado = tradingBotRepository.save(nuevoBot);
        log.info("Bot creado exitosamente con ID: {}", botGuardado.getId());
        
        return botGuardado;
    }
    
    /**
     * Obtener todos los bots de un usuario
     */
    @Transactional(readOnly = true)
    public List<TradingBot> obtenerBotsPorUsuario(Long userId) {
        log.info("Obteniendo todos los bots del usuario: {}", userId);
        return tradingBotRepository.findByUserId(userId);
    }
    
    /**
     * Obtener solo los bots activos de un usuario
     */
    @Transactional(readOnly = true)
    public List<TradingBot> obtenerBotsActivosPorUsuario(Long userId) {
        log.info("Obteniendo bots activos del usuario: {}", userId);
        return tradingBotRepository.findByUserIdAndActivoTrue(userId);
    }
    
    /**
     * Obtener todos los bots activos del sistema (para job automático)
     */
    @Transactional(readOnly = true)
    public List<TradingBot> obtenerTodosLosBotsActivos() {
        return tradingBotRepository.findByActivoTrue();
    }
    
    /**
     * Buscar bot por ID y verificar que pertenece al usuario
     */
    @Transactional(readOnly = true)
    public Optional<TradingBot> buscarBotPorId(Long botId, Long userId) {
        return tradingBotRepository.findById(botId)
                .filter(bot -> bot.getUser().getId().equals(userId));
    }
    
    /**
     * Activar/Desactivar un bot
     */
    public TradingBot toggleBot(Long botId, Long userId) {
        log.info("Cambiando estado del bot {} para usuario {}", botId, userId);
        
        TradingBot bot = buscarBotPorId(botId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Bot no encontrado"));
        
        boolean nuevoEstado = !bot.getActivo();
        
        // Validaciones antes de activar
        if (nuevoEstado) {
            validarActivacionBot(bot);
        }
        
        bot.setActivo(nuevoEstado);
        
        TradingBot botActualizado = tradingBotRepository.save(bot);
        log.info("Bot {} ahora está: {}", botId, botActualizado.getActivo() ? "ACTIVO" : "INACTIVO");
        
        return botActualizado;
    }
    
    /**
     * Actualizar configuración de un bot
     */
    public TradingBot actualizarConfiguracion(Long botId, Long userId, String nuevoNombre,
                                            Map<String, Object> nuevosParametros, BigDecimal nuevoBudget,
                                            BigDecimal nuevoStopLoss) {
        log.info("Actualizando configuración del bot {} para usuario {}", botId, userId);
        
        TradingBot bot = buscarBotPorId(botId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Bot no encontrado"));
        
        // No permitir actualizar si está activo (por seguridad)
        if (bot.getActivo()) {
            throw new IllegalStateException("No se puede actualizar un bot activo. Desactívalo primero.");
        }
        
        // Validar nuevo nombre si cambió
        if (!bot.getNombre().equals(nuevoNombre) && existeBotConNombre(userId, nuevoNombre)) {
            throw new IllegalArgumentException("Ya existe un bot con el nombre: " + nuevoNombre);
        }
        
        // Validaciones de negocio
        validarParametrosBot(nuevoBudget, nuevoStopLoss);
        
        // Actualizar valores
        bot.setNombre(nuevoNombre);
        bot.setParametros(convertirParametrosAJson(nuevosParametros));
        bot.setBudget(nuevoBudget);
        bot.setStopLoss(nuevoStopLoss);
        
        return tradingBotRepository.save(bot);
    }
    
    /**
     * Eliminar un bot
     */
    public void eliminarBot(Long botId, Long userId) {
        log.info("Eliminando bot {} del usuario {}", botId, userId);
        
        TradingBot bot = buscarBotPorId(botId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Bot no encontrado"));
        
        // No permitir eliminar si está activo
        if (bot.getActivo()) {
            throw new IllegalStateException("No se puede eliminar un bot activo. Desactívalo primero.");
        }
        
        tradingBotRepository.delete(bot);
        log.info("Bot {} eliminado exitosamente", botId);
    }
    
    /**
     * Ejecutar trade y actualizar performance del bot
     */
    public TradingBot ejecutarTrade(Long botId, BigDecimal gananciaOPerdida) {
        log.info("Ejecutando trade para bot {} - Resultado: ${}", botId, gananciaOPerdida);
        
        TradingBot bot = tradingBotRepository.findById(botId)
                .orElseThrow(() -> new IllegalArgumentException("Bot no encontrado"));
        
        // Verificar que el bot puede ejecutar trades
        if (!bot.puedeEjecutar()) {
            throw new IllegalStateException("El bot no puede ejecutar trades (inactivo o sin budget)");
        }
        
        // Actualizar performance y contador de trades
        bot.actualizarPerformance(gananciaOPerdida);
        bot.incrementarTradesHoy();
        
        // Verificar stop loss
        verificarStopLoss(bot);
        
        TradingBot botActualizado = tradingBotRepository.save(bot);
        log.info("Trade ejecutado. Nueva performance: ${}", botActualizado.getPerformance());
        
        return botActualizado;
    }
    
    /**
     * Resetear contadores diarios de todos los bots (llamar cada día)
     */
    public void resetearContadoresDiarios() {
        log.info("Reseteando contadores diarios de todos los bots");
        
        List<TradingBot> todosLosBots = tradingBotRepository.findAll();
        
        todosLosBots.forEach(TradingBot::resetearTradesDiarios);
        tradingBotRepository.saveAll(todosLosBots);
        
        log.info("Contadores diarios reseteados para {} bots", todosLosBots.size());
    }
    
    /**
     * Contar bots activos de un usuario
     */
    @Transactional(readOnly = true)
    public long contarBotsActivos(Long userId) {
        return tradingBotRepository.countByUserIdAndActivoTrue(userId);
    }
    
    /**
     * Contar total de bots de un usuario
     */
    @Transactional(readOnly = true)
    public long contarTotalBots(Long userId) {
        return tradingBotRepository.countByUserId(userId);
    }
    
    /**
     * Obtener estadísticas de bots de un usuario
     */
    @Transactional(readOnly = true)
    public BotStats obtenerEstadisticasBots(Long userId) {
        List<TradingBot> bots = obtenerBotsPorUsuario(userId);
        
        long totalBots = bots.size();
        long botsActivos = bots.stream().mapToLong(b -> b.getActivo() ? 1 : 0).sum();
        long botsInactivos = totalBots - botsActivos;
        
        BigDecimal performanceTotal = bots.stream()
                .map(TradingBot::getPerformance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal budgetTotal = bots.stream()
                .map(TradingBot::getBudget)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        int tradesHoyTotal = bots.stream()
                .mapToInt(TradingBot::getTradesHoy)
                .sum();
        
        // Obtener estadísticas por estrategia
        Map<TradingBot.Estrategia, Long> botsPorEstrategia = bots.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        TradingBot::getEstrategia,
                        java.util.stream.Collectors.counting()
                ));
        
        return new BotStats(
                totalBots,
                botsActivos,
                botsInactivos,
                performanceTotal,
                budgetTotal,
                tradesHoyTotal,
                botsPorEstrategia
        );
    }
    
    /**
     * Obtener ranking de bots por performance
     */
    @Transactional(readOnly = true)
    public List<TradingBot> obtenerRankingPorPerformance(Long userId, int limite) {
        return obtenerBotsPorUsuario(userId).stream()
                .sorted((a, b) -> b.getPerformance().compareTo(a.getPerformance()))
                .limit(limite)
                .toList();
    }
    
    /**
     * Pausar todos los bots de un usuario (emergencia)
     */
    public void pausarTodosLosBots(Long userId) {
        log.warn("PAUSANDO TODOS LOS BOTS del usuario: {}", userId);
        
        List<TradingBot> botsActivos = obtenerBotsActivosPorUsuario(userId);
        
        botsActivos.forEach(bot -> bot.setActivo(false));
        tradingBotRepository.saveAll(botsActivos);
        
        log.warn("Se pausaron {} bots del usuario {}", botsActivos.size(), userId);
    }
    
    // Métodos auxiliares privados
    
    private boolean existeBotConNombre(Long userId, String nombre) {
        return tradingBotRepository.findByUserIdAndNombre(userId, nombre).isPresent();
    }
    
    private void validarParametrosBot(BigDecimal budget, BigDecimal stopLoss) {
        if (budget.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El budget debe ser mayor a 0");
        }
        
        if (stopLoss.compareTo(BigDecimal.ZERO) <= 0 || stopLoss.compareTo(new BigDecimal("100")) >= 0) {
            throw new IllegalArgumentException("El stop loss debe estar entre 0% y 100%");
        }
    }
    
    private void validarActivacionBot(TradingBot bot) {
        if (bot.getBudget().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("No se puede activar un bot sin budget");
        }
        
        if (bot.getParametros() == null || bot.getParametros().trim().isEmpty()) {
            throw new IllegalStateException("No se puede activar un bot sin parámetros configurados");
        }
    }
    
    private String convertirParametrosAJson(Map<String, Object> parametros) {
        try {
            return objectMapper.writeValueAsString(parametros);
        } catch (JsonProcessingException e) {
            log.error("Error al convertir parámetros a JSON", e);
            throw new IllegalArgumentException("Parámetros inválidos");
        }
    }
    
    private void verificarStopLoss(TradingBot bot) {
        // Calcular pérdida porcentual basada en el budget inicial
        BigDecimal perdidaPorcentual = bot.getPerformance()
                .divide(bot.getBudget(), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .abs();
        
        // Si la pérdida supera el stop loss, desactivar el bot
        if (bot.getPerformance().compareTo(BigDecimal.ZERO) < 0 && 
            perdidaPorcentual.compareTo(bot.getStopLoss()) >= 0) {
            
            log.warn("STOP LOSS ACTIVADO para bot {} - Pérdida: {}% (Límite: {}%)", 
                    bot.getId(), perdidaPorcentual, bot.getStopLoss());
            
            bot.setActivo(false);
        }
    }
    
    // Record para estadísticas
    public record BotStats(
            long totalBots,
            long botsActivos,
            long botsInactivos,
            BigDecimal performanceTotal,
            BigDecimal budgetTotal,
            int tradesHoyTotal,
            Map<TradingBot.Estrategia, Long> botsPorEstrategia
    ) {}
}