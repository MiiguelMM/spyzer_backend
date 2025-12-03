package com.miguel.spyzer.service;

import com.miguel.spyzer.entities.User;
import com.miguel.spyzer.entities.Portfolio;
import com.miguel.spyzer.entities.Transaction;
import com.miguel.spyzer.entities.MarketData;
import com.miguel.spyzer.repository.PortfolioRepository;
import com.miguel.spyzer.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TradingService {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private MarketDataService marketDataService;
    
    @Autowired
    private PortfolioRepository portfolioRepository;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    /**
     * Comprar acciones
     *
     * Invalida la caché de rankings porque el balance del usuario cambia.
     */
    @Transactional
    @CacheEvict(value = "rankings", allEntries = true)
    public Transaction comprarAccion(Long userId, String symbol, BigDecimal cantidad, Transaction.EjecutadaPor ejecutadaPor) {
        // 1. Verificar que el símbolo esté disponible
        if (!marketDataService.estaDisponible(symbol)) {
            throw new RuntimeException("Símbolo no disponible: " + symbol);
        }
        
        // 2. Obtener precio actual
        MarketData marketData = marketDataService.obtenerDatos(symbol);
        if (marketData == null) {
            throw new RuntimeException("No se pudieron obtener datos de mercado para: " + symbol);
        }
        
        BigDecimal precioActual = marketData.getPrecio();
        BigDecimal costoTotal = cantidad.multiply(precioActual);
        
        // 3. Verificar balance suficiente
        if (!userService.tieneSuficienteBalance(userId, costoTotal)) {
            throw new RuntimeException("Balance insuficiente. Costo: $" + costoTotal + 
                ", Balance disponible: $" + userService.obtenerBalanceActual(userId));
        }
        
        // 4. Debitar balance del usuario
        User usuario = userService.debitarBalance(userId, costoTotal);
        
        // 5. Actualizar o crear posición en portfolio
        Optional<Portfolio> posicionExistente = portfolioRepository.findByUserIdAndSymbol(userId, symbol);
        
        if (posicionExistente.isPresent()) {
            // Actualizar posición existente
            Portfolio posicion = posicionExistente.get();
            BigDecimal cantidadTotal = posicion.getCantidad().add(cantidad);
            BigDecimal valorTotal = posicion.getCantidad().multiply(posicion.getPrecioPromedio())
                                   .add(costoTotal);
            BigDecimal nuevoPrecioPromedio = valorTotal.divide(cantidadTotal, 6, RoundingMode.HALF_UP);
            
            posicion.setCantidad(cantidadTotal);
            posicion.setPrecioPromedio(nuevoPrecioPromedio);
            posicion.setPrecioActual(precioActual);
            posicion.calcularValorMercado();
            posicion.calcularGananciaPerdida();
            
            portfolioRepository.save(posicion);
        } else {
            // Crear nueva posición
            Portfolio nuevaPosicion = new Portfolio();
            nuevaPosicion.setUser(usuario);
            nuevaPosicion.setSymbol(symbol.toUpperCase());
            nuevaPosicion.setCantidad(cantidad);
            nuevaPosicion.setPrecioPromedio(precioActual);
            nuevaPosicion.setPrecioActual(precioActual);
            nuevaPosicion.calcularValorMercado();
            nuevaPosicion.calcularGananciaPerdida();
            
            portfolioRepository.save(nuevaPosicion);
        }
        
        // 6. Registrar transacción
        Transaction transaccion = new Transaction();
        transaccion.setUser(usuario);
        transaccion.setSymbol(symbol.toUpperCase());
        transaccion.setTipo(Transaction.TipoTransaccion.BUY);
        transaccion.setCantidad(cantidad);
        transaccion.setPrecio(precioActual);
        transaccion.setEjecutadaPor(ejecutadaPor);
        transaccion.setTimestamp(LocalDateTime.now());
        
        return transactionRepository.save(transaccion);
    }
    
    /**
     * Vender acciones
     *
     * Invalida la caché de rankings porque el balance del usuario cambia.
     */
    @Transactional
    @CacheEvict(value = "rankings", allEntries = true)
    public Transaction venderAccion(Long userId, String symbol, BigDecimal cantidad, Transaction.EjecutadaPor ejecutadaPor) {
        // 1. Verificar que el usuario tiene la posición
        Optional<Portfolio> posicion = portfolioRepository.findByUserIdAndSymbol(userId, symbol);
        if (posicion.isEmpty()) {
            throw new RuntimeException("No tienes posición en: " + symbol);
        }
        
        Portfolio portfolio = posicion.get();
        
        // 2. Verificar que tiene suficientes acciones
        if (portfolio.getCantidad().compareTo(cantidad) < 0) {
            throw new RuntimeException("Cantidad insuficiente. Tienes: " + portfolio.getCantidad() + 
                ", quieres vender: " + cantidad);
        }
        
        // 3. Obtener precio actual
        MarketData marketData = marketDataService.obtenerDatos(symbol);
        if (marketData == null) {
            throw new RuntimeException("No se pudieron obtener datos de mercado para: " + symbol);
        }
        
        BigDecimal precioActual = marketData.getPrecio();
        BigDecimal ingresoTotal = cantidad.multiply(precioActual);
        
        // 4. Acreditar balance al usuario
        User usuario = userService.acreditarBalance(userId, ingresoTotal);
        
        // 5. Actualizar posición en portfolio
        BigDecimal nuevaCantidad = portfolio.getCantidad().subtract(cantidad);
        
        if (nuevaCantidad.compareTo(BigDecimal.ZERO) == 0) {
            // Eliminar posición si se vendió todo
            portfolioRepository.delete(portfolio);
        } else {
            // Actualizar cantidad restante
            portfolio.setCantidad(nuevaCantidad);
            portfolio.setPrecioActual(precioActual);
            portfolio.calcularValorMercado();
            portfolio.calcularGananciaPerdida();
            
            portfolioRepository.save(portfolio);
        }
        
        // 6. Registrar transacción
        Transaction transaccion = new Transaction();
        transaccion.setUser(usuario);
        transaccion.setSymbol(symbol.toUpperCase());
        transaccion.setTipo(Transaction.TipoTransaccion.SELL);
        transaccion.setCantidad(cantidad);
        transaccion.setPrecio(precioActual);
        transaccion.setEjecutadaPor(ejecutadaPor);
        transaccion.setTimestamp(LocalDateTime.now());
        
        return transactionRepository.save(transaccion);
    }
    
    // Obtener cotización para una operación (sin ejecutar)
    public BigDecimal obtenerCotizacion(String symbol, BigDecimal cantidad, boolean esCompra) {
        if (!marketDataService.estaDisponible(symbol)) {
            throw new RuntimeException("Símbolo no disponible: " + symbol);
        }
        
        MarketData marketData = marketDataService.obtenerDatos(symbol);
        if (marketData == null) {
            throw new RuntimeException("No se pudieron obtener datos de mercado para: " + symbol);
        }
        
        return cantidad.multiply(marketData.getPrecio());
    }
    
    // Validar si una operación es posible (sin ejecutar)
    public boolean puedeComprar(Long userId, String symbol, BigDecimal cantidad) {
        try {
            BigDecimal costoTotal = obtenerCotizacion(symbol, cantidad, true);
            return userService.tieneSuficienteBalance(userId, costoTotal);
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean puedeVender(Long userId, String symbol, BigDecimal cantidad) {
        Optional<Portfolio> posicion = portfolioRepository.findByUserIdAndSymbol(userId, symbol);
        
        if (posicion.isEmpty()) {
            return false;
        }
        
        return posicion.get().getCantidad().compareTo(cantidad) >= 0;
    }
}