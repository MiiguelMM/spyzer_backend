package com.miguel.spyzer.service;

import com.miguel.spyzer.entities.Portfolio;
import com.miguel.spyzer.entities.User;
import com.miguel.spyzer.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PortfolioService {
    
    private final PortfolioRepository portfolioRepository;
    
    /**
     * Obtener todas las posiciones de un usuario
     */
    @Transactional(readOnly = true)
    public List<Portfolio> obtenerPortfolioPorUsuario(Long userId) {
        log.info("Obteniendo portfolio del usuario: {}", userId);
        return portfolioRepository.findByUserId(userId);
    }
    
    /**
     * Obtener todas las posiciones de un usuario ordenadas por valor de mercado
     */
    @Transactional(readOnly = true)
    public List<Portfolio> obtenerPortfolioOrdenadoPorValor(Long userId) {
        log.info("Obteniendo portfolio ordenado por valor para usuario: {}", userId);
        return portfolioRepository.findByUserIdOrderByValorMercadoDesc(userId);
    }
    
    /**
     * Buscar una posición específica de un usuario por símbolo
     */
    @Transactional(readOnly = true)
    public Optional<Portfolio> buscarPosicionPorSimbolo(Long userId, String symbol) {
        log.info("Buscando posición de {} para usuario: {}", symbol, userId);
        return portfolioRepository.findByUserIdAndSymbol(userId, symbol);
    }
    
    /**
     * Verificar si un usuario tiene una posición en un símbolo específico
     */
    @Transactional(readOnly = true)
    public boolean tieneposicionEnSimbolo(Long userId, String symbol) {
        return portfolioRepository.existsByUserIdAndSymbol(userId, symbol);
    }
    
    /**
     * Agregar shares a una posición existente o crear nueva posición
     */
    public Portfolio comprarShares(Long userId, String symbol, BigDecimal cantidad, BigDecimal precio, BigDecimal precioActual) {
        log.info("Comprando {} shares de {} a ${} para usuario: {}", cantidad, symbol, precio, userId);
        
        Optional<Portfolio> posicionExistente = portfolioRepository.findByUserIdAndSymbol(userId, symbol);
        
        if (posicionExistente.isPresent()) {
            // Actualizar posición existente
            Portfolio portfolio = posicionExistente.get();
            return actualizarPosicionCompra(portfolio, cantidad, precio, precioActual);
        } else {
            // Crear nueva posición
            return crearNuevaPosicion(userId, symbol, cantidad, precio, precioActual);
        }
    }
    
    /**
     * Vender shares de una posición existente
     */
    public Portfolio venderShares(Long userId, String symbol, BigDecimal cantidadVender, BigDecimal precioVenta, BigDecimal precioActual) {
        log.info("Vendiendo {} shares de {} a ${} para usuario: {}", cantidadVender, symbol, precioVenta, userId);
        
        Optional<Portfolio> posicionOpt = portfolioRepository.findByUserIdAndSymbol(userId, symbol);
        
        if (posicionOpt.isEmpty()) {
            throw new IllegalArgumentException("No se encontró posición para el símbolo: " + symbol);
        }
        
        Portfolio portfolio = posicionOpt.get();
        
        if (portfolio.getCantidad().compareTo(cantidadVender) < 0) {
            throw new IllegalArgumentException("Cantidad a vender excede las shares disponibles");
        }
        
        return actualizarPosicionVenta(portfolio, cantidadVender, precioActual);
    }
    
    /**
     * Actualizar precio actual de todas las posiciones de un usuario
     */
    public void actualizarPreciosActuales(Long userId, String symbol, BigDecimal nuevoPrecio) {
        log.info("Actualizando precio de {} a ${} para usuario: {}", symbol, nuevoPrecio, userId);
        
        Optional<Portfolio> posicionOpt = portfolioRepository.findByUserIdAndSymbol(userId, symbol);
        
        if (posicionOpt.isPresent()) {
            Portfolio portfolio = posicionOpt.get();
            portfolio.setPrecioActual(nuevoPrecio);
            portfolio.calcularValorMercado();
            portfolio.calcularGananciaPerdida();
            portfolioRepository.save(portfolio);
        }
    }
    
    /**
     * Obtener valor total del portfolio de un usuario
     */
    @Transactional(readOnly = true)
    public BigDecimal calcularValorTotalPortfolio(Long userId) {
        List<Portfolio> posiciones = portfolioRepository.findByUserId(userId);
        
        return posiciones.stream()
                .map(Portfolio::getValorMercado)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * Obtener ganancia/pérdida total del portfolio
     */
    @Transactional(readOnly = true)
    public BigDecimal calcularGananciaPerdidaTotal(Long userId) {
        List<Portfolio> posiciones = portfolioRepository.findByUserId(userId);
        
        return posiciones.stream()
                .map(Portfolio::getGananciaPerdida)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * Contar número de posiciones diferentes de un usuario
     */
    @Transactional(readOnly = true)
    public long contarPosiciones(Long userId) {
        return portfolioRepository.countByUserId(userId);
    }
    
    /**
     * Eliminar una posición completamente
     */
    public void eliminarPosicion(Long userId, String symbol) {
        log.info("Eliminando posición de {} para usuario: {}", symbol, userId);
        
        Optional<Portfolio> posicionOpt = portfolioRepository.findByUserIdAndSymbol(userId, symbol);
        
        if (posicionOpt.isPresent()) {
            portfolioRepository.delete(posicionOpt.get());
            log.info("Posición eliminada exitosamente");
        }
    }
    
    // Métodos privados auxiliares
    
    private Portfolio actualizarPosicionCompra(Portfolio portfolio, BigDecimal cantidad, BigDecimal precio, BigDecimal precioActual) {
        // Calcular nuevo precio promedio ponderado
        BigDecimal cantidadTotal = portfolio.getCantidad().add(cantidad);
        BigDecimal costoActual = portfolio.getCantidad().multiply(portfolio.getPrecioPromedio());
        BigDecimal costoNuevo = cantidad.multiply(precio);
        BigDecimal costoTotal = costoActual.add(costoNuevo);
        BigDecimal nuevoPrecioPromedio = costoTotal.divide(cantidadTotal, 2, RoundingMode.HALF_UP);
        
        // Actualizar valores
        portfolio.setCantidad(cantidadTotal);
        portfolio.setPrecioPromedio(nuevoPrecioPromedio);
        portfolio.setPrecioActual(precioActual);
        portfolio.calcularValorMercado();
        portfolio.calcularGananciaPerdida();
        
        return portfolioRepository.save(portfolio);
    }
    
    private Portfolio actualizarPosicionVenta(Portfolio portfolio, BigDecimal cantidadVender, BigDecimal precioActual) {
        BigDecimal nuevaCantidad = portfolio.getCantidad().subtract(cantidadVender);
        
        portfolio.setCantidad(nuevaCantidad);
        portfolio.setPrecioActual(precioActual);
        portfolio.calcularValorMercado();
        portfolio.calcularGananciaPerdida();
        
        // Si se vendieron todas las shares, eliminar la posición
        if (nuevaCantidad.compareTo(BigDecimal.ZERO) == 0) {
            portfolioRepository.delete(portfolio);
            return null;
        }
        
        return portfolioRepository.save(portfolio);
    }
    
    private Portfolio crearNuevaPosicion(Long userId, String symbol, BigDecimal cantidad, BigDecimal precio, BigDecimal precioActual) {
        User user = new User();
        user.setId(userId);
        
        Portfolio nuevaPos = Portfolio.builder()
                .user(user)
                .symbol(symbol)
                .cantidad(cantidad)
                .precioPromedio(precio)
                .precioActual(precioActual)
                .build();
        
        nuevaPos.calcularValorMercado();
        nuevaPos.calcularGananciaPerdida();
        
        return portfolioRepository.save(nuevaPos);
    }
}