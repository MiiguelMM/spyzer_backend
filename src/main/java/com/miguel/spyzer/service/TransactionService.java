package com.miguel.spyzer.service;

import com.miguel.spyzer.entities.Transaction;
import com.miguel.spyzer.entities.User;
import com.miguel.spyzer.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TransactionService {
    
    private final TransactionRepository transactionRepository;
    
    /**
     * Registrar una nueva transacción de compra
     */
    public Transaction registrarCompra(Long userId, String symbol, BigDecimal cantidad, 
                                     BigDecimal precio, Transaction.EjecutadaPor ejecutadaPor) {
        log.info("Registrando compra para usuario {} - {} shares de {} a ${} - ejecutada por: {}", 
                userId, cantidad, symbol, precio, ejecutadaPor);
        
        return crearTransaccion(userId, symbol, Transaction.TipoTransaccion.BUY, 
                              cantidad, precio, ejecutadaPor);
    }
    
    /**
     * Registrar una nueva transacción de venta
     */
    public Transaction registrarVenta(Long userId, String symbol, BigDecimal cantidad, 
                                    BigDecimal precio, Transaction.EjecutadaPor ejecutadaPor) {
        log.info("Registrando venta para usuario {} - {} shares de {} a ${} - ejecutada por: {}", 
                userId, cantidad, symbol, precio, ejecutadaPor);
        
        return crearTransaccion(userId, symbol, Transaction.TipoTransaccion.SELL, 
                              cantidad, precio, ejecutadaPor);
    }
    
    /**
     * Obtener historial completo de transacciones de un usuario
     */
    @Transactional(readOnly = true)
    public List<Transaction> obtenerHistorialCompleto(Long userId) {
        log.info("Obteniendo historial completo de transacciones para usuario: {}", userId);
        return transactionRepository.findByUserIdOrderByTimestampDesc(userId);
    }
    
    /**
     * Obtener transacciones recientes de un usuario (últimas 10)
     */
    @Transactional(readOnly = true)
    public List<Transaction> obtenerTransaccionesRecientes(Long userId) {
        log.info("Obteniendo transacciones recientes para usuario: {}", userId);
        return transactionRepository.findTop10ByUserIdOrderByTimestampDesc(userId);
    }
    
    /**
     * Obtener transacciones por tipo (BUY o SELL)
     */
    @Transactional(readOnly = true)
    public List<Transaction> obtenerTransaccionesPorTipo(Long userId, Transaction.TipoTransaccion tipo) {
        log.info("Obteniendo transacciones de tipo {} para usuario: {}", tipo, userId);
        return transactionRepository.findByUserIdAndTipoOrderByTimestampDesc(userId, tipo);
    }
    
    /**
     * Obtener transacciones de un símbolo específico para un usuario
     */
    @Transactional(readOnly = true)
    public List<Transaction> obtenerTransaccionesPorSimbolo(Long userId, String symbol) {
        log.info("Obteniendo transacciones de {} para usuario: {}", symbol, userId);
        return transactionRepository.findByUserIdAndSymbolOrderByTimestampDesc(userId, symbol.toUpperCase());
    }
    
    /**
     * Contar total de transacciones de un usuario
     */
    @Transactional(readOnly = true)
    public long contarTransacciones(Long userId) {
        return transactionRepository.countByUserId(userId);
    }
    
    /**
     * Contar transacciones por tipo
     */
    @Transactional(readOnly = true)
    public long contarTransaccionesPorTipo(Long userId, Transaction.TipoTransaccion tipo) {
        return transactionRepository.countByUserIdAndTipo(userId, tipo);
    }
    
    /**
     * Calcular volumen total operado por un usuario
     */
    @Transactional(readOnly = true)
    public BigDecimal calcularVolumenTotalOperado(Long userId) {
        List<Transaction> transacciones = obtenerHistorialCompleto(userId);
        
        return transacciones.stream()
                .map(Transaction::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * Calcular volumen operado por símbolo
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> calcularVolumenPorSimbolo(Long userId) {
        List<Transaction> transacciones = obtenerHistorialCompleto(userId);
        
        return transacciones.stream()
                .collect(Collectors.groupingBy(
                        Transaction::getSymbol,
                        Collectors.reducing(BigDecimal.ZERO, 
                                          Transaction::getValorTotal, 
                                          BigDecimal::add)
                ));
    }
    
    /**
     * Obtener estadísticas detalladas de trading de un usuario
     */
    @Transactional(readOnly = true)
    public TradingStats obtenerEstadisticasTrading(Long userId) {
        log.info("Calculando estadísticas de trading para usuario: {}", userId);
        
        List<Transaction> transacciones = obtenerHistorialCompleto(userId);
        
        long totalTransacciones = transacciones.size();
        long compras = contarTransaccionesPorTipo(userId, Transaction.TipoTransaccion.BUY);
        long ventas = contarTransaccionesPorTipo(userId, Transaction.TipoTransaccion.SELL);
        
        BigDecimal volumenTotal = calcularVolumenTotalOperado(userId);
        
        long transaccionesManual = transacciones.stream()
                .mapToLong(t -> t.getEjecutadaPor() == Transaction.EjecutadaPor.MANUAL ? 1 : 0)
                .sum();
        
        long transaccionesBot = totalTransacciones - transaccionesManual;
        
        // Calcular símbolos únicos operados
        long simbolosUnicos = transacciones.stream()
                .map(Transaction::getSymbol)
                .distinct()
                .count();
        
        // Calcular promedio de valor por transacción
        BigDecimal promedioValorTransaccion = totalTransacciones > 0 
                ? volumenTotal.divide(BigDecimal.valueOf(totalTransacciones), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        
        return new TradingStats(
                totalTransacciones,
                compras,
                ventas,
                volumenTotal,
                transaccionesManual,
                transaccionesBot,
                simbolosUnicos,
                promedioValorTransaccion
        );
    }
    
    /**
     * Obtener actividad de trading por período (para gráficos)
     */
    @Transactional(readOnly = true)
    public List<ActividadDiaria> obtenerActividadDiaria(Long userId, LocalDateTime desde, LocalDateTime hasta) {
        log.info("Obteniendo actividad diaria para usuario {} desde {} hasta {}", userId, desde, hasta);
        
        List<Transaction> transacciones = obtenerHistorialCompleto(userId);
        
        return transacciones.stream()
                .filter(t -> t.getTimestamp().isAfter(desde) && t.getTimestamp().isBefore(hasta))
                .collect(Collectors.groupingBy(
                        t -> t.getTimestamp().toLocalDate(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                lista -> new ActividadDiaria(
                                        lista.get(0).getTimestamp().toLocalDate(),
                                        lista.size(),
                                        lista.stream()
                                                .map(Transaction::getValorTotal)
                                                .reduce(BigDecimal.ZERO, BigDecimal::add),
                                        lista.stream()
                                                .mapToLong(t -> t.getTipo() == Transaction.TipoTransaccion.BUY ? 1 : 0)
                                                .sum(),
                                        lista.stream()
                                                .mapToLong(t -> t.getTipo() == Transaction.TipoTransaccion.SELL ? 1 : 0)
                                                .sum()
                                )
                        )
                ))
                .values()
                .stream()
                .sorted((a, b) -> a.fecha().compareTo(b.fecha()))
                .toList();
    }
    
    /**
     * Obtener top símbolos más operados por un usuario
     */
    @Transactional(readOnly = true)
    public List<SimboloActividad> obtenerTopSimbolosOperados(Long userId, int limite) {
        List<Transaction> transacciones = obtenerHistorialCompleto(userId);
        
        return transacciones.stream()
                .collect(Collectors.groupingBy(
                        Transaction::getSymbol,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                lista -> new SimboloActividad(
                                        lista.get(0).getSymbol(),
                                        lista.size(),
                                        lista.stream()
                                                .map(Transaction::getValorTotal)
                                                .reduce(BigDecimal.ZERO, BigDecimal::add),
                                        lista.stream()
                                                .mapToLong(t -> t.getTipo() == Transaction.TipoTransaccion.BUY ? 1 : 0)
                                                .sum(),
                                        lista.stream()
                                                .mapToLong(t -> t.getTipo() == Transaction.TipoTransaccion.SELL ? 1 : 0)
                                                .sum()
                                )
                        )
                ))
                .values()
                .stream()
                .sorted((a, b) -> b.volumenTotal().compareTo(a.volumenTotal()))
                .limit(limite)
                .toList();
    }
    
    /**
     * Verificar si un usuario ha operado un símbolo específico
     */
    @Transactional(readOnly = true)
    public boolean haOperadoSimbolo(Long userId, String symbol) {
        return !obtenerTransaccionesPorSimbolo(userId, symbol).isEmpty();
    }
    
    // Método auxiliar privado
    
    private Transaction crearTransaccion(Long userId, String symbol, Transaction.TipoTransaccion tipo,
                                       BigDecimal cantidad, BigDecimal precio, Transaction.EjecutadaPor ejecutadaPor) {
        User user = new User();
        user.setId(userId);
        
        Transaction transaccion = Transaction.builder()
                .user(user)
                .symbol(symbol.toUpperCase())
                .tipo(tipo)
                .cantidad(cantidad)
                .precio(precio)
                .ejecutadaPor(ejecutadaPor)
                .timestamp(LocalDateTime.now())
                .build();
        
        Transaction transaccionGuardada = transactionRepository.save(transaccion);
        
        log.info("Transacción registrada con ID: {} - Valor total: ${}", 
                transaccionGuardada.getId(), transaccionGuardada.getValorTotal());
        
        return transaccionGuardada;
    }
    
    // Records para estadísticas y datos de retorno
    
    public record TradingStats(
            long totalTransacciones,
            long compras,
            long ventas,
            BigDecimal volumenTotal,
            long transaccionesManual,
            long transaccionesBot,
            long simbolosUnicos,
            BigDecimal promedioValorTransaccion
    ) {}
    
    public record ActividadDiaria(
            java.time.LocalDate fecha,
            int transacciones,
            BigDecimal volumen,
            long compras,
            long ventas
    ) {}
    
    public record SimboloActividad(
            String symbol,
            int transacciones,
            BigDecimal volumenTotal,
            long compras,
            long ventas
    ) {}
}