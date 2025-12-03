package com.miguel.spyzer.service;

import com.miguel.spyzer.entities.User;
import com.miguel.spyzer.repository.UserRepository;
import com.miguel.spyzer.repository.PortfolioRepository;
import com.miguel.spyzer.repository.TransactionRepository;
import com.miguel.spyzer.repository.AlertRepository;
import com.miguel.spyzer.repository.TradingBotRepository;
import com.miguel.spyzer.dto.UserResponseDTO;
import com.miguel.spyzer.dto.UserRankingDTO;
import com.miguel.spyzer.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private TradingBotRepository tradingBotRepository;
    
    // === MÉTODOS PÚBLICOS (devuelven DTOs) ===

    // Obtener perfil del usuario (sus propios datos)
    public UserResponseDTO obtenerPerfil(Long userId) {
        Optional<User> usuario = userRepository.findById(userId);
        return usuario.map(userMapper::toUserResponseDTO).orElse(null);
    }

    /**
     * Obtener ranking completo (top 150)
     *
     * Cachea el resultado durante 5 minutos para evitar consultas repetidas.
     * La caché se invalida automáticamente al hacer transacciones.
     */
    @Cacheable(value = "rankings", key = "'all'")
    public List<UserRankingDTO> obtenerRanking() {
        List<User> usuarios = userRepository.findTop150ByOrderByBalanceActualDesc();
        List<UserRankingDTO> ranking = userMapper.toUserRankingDTOList(usuarios);

        // Asignar posiciones manualmente
        for (int i = 0; i < ranking.size(); i++) {
            ranking.get(i).setPosicion(i + 1);
        }

        return ranking;
    }
    
    // === MÉTODOS INTERNOS (para uso entre services, devuelven entidades) ===
    
    // Crear o encontrar usuario por Google OAuth
    public User crearOEncontrarUsuario(String googleId, String email, String name, String avatar) {
        // Buscar si ya existe
        Optional<User> usuarioExistente = userRepository.findByGoogleId(googleId);
        
        if (usuarioExistente.isPresent()) {
            return usuarioExistente.get();
        }
        
        // Crear nuevo usuario
        User nuevoUsuario = new User();
        nuevoUsuario.setGoogleId(googleId);
        nuevoUsuario.setEmail(email);
        nuevoUsuario.setName(name);
        nuevoUsuario.setAvatar(avatar);
        nuevoUsuario.setBalanceInicial(new BigDecimal("50000.00"));
        nuevoUsuario.setBalanceActual(new BigDecimal("50000.00"));
        nuevoUsuario.setCreatedAt(LocalDateTime.now());
        
        return userRepository.save(nuevoUsuario);
    }
    
    // Obtener usuario por ID (interno)
    public Optional<User> obtenerUsuarioInterno(Long userId) {
        return userRepository.findById(userId);
    }
    
    // Verificar si el usuario tiene suficiente balance
    public boolean tieneSuficienteBalance(Long userId, BigDecimal cantidad) {
        Optional<User> usuario = userRepository.findById(userId);
        
        if (usuario.isPresent()) {
            return usuario.get().getBalanceActual().compareTo(cantidad) >= 0;
        }
        
        return false;
    }
    
    // Debitar balance (para compras)
    public User debitarBalance(Long userId, BigDecimal cantidad) {
        Optional<User> usuario = userRepository.findById(userId);
        
        if (usuario.isPresent()) {
            User user = usuario.get();
            BigDecimal nuevoBalance = user.getBalanceActual().subtract(cantidad);
            
            if (nuevoBalance.compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Balance insuficiente");
            }
            
            user.setBalanceActual(nuevoBalance);
            return userRepository.save(user);
        }
        
        throw new RuntimeException("Usuario no encontrado con ID: " + userId);
    }
    
    // Acreditar balance (para ventas)
    public User acreditarBalance(Long userId, BigDecimal cantidad) {
        Optional<User> usuario = userRepository.findById(userId);
        
        if (usuario.isPresent()) {
            User user = usuario.get();
            BigDecimal nuevoBalance = user.getBalanceActual().add(cantidad);
            user.setBalanceActual(nuevoBalance);
            return userRepository.save(user);
        }
        
        throw new RuntimeException("Usuario no encontrado con ID: " + userId);
    }
    
    // Obtener balance actual de un usuario
    public BigDecimal obtenerBalanceActual(Long userId) {
        Optional<User> usuario = userRepository.findById(userId);

        if (usuario.isPresent()) {
            return usuario.get().getBalanceActual();
        }

        throw new RuntimeException("Usuario no encontrado con ID: " + userId);
    }

    // Eliminar usuario y todos sus datos relacionados
    @Transactional
    @CacheEvict(value = "rankings", allEntries = true)
    public void eliminarUsuario(Long userId) {
        log.info("Iniciando eliminación completa del usuario con ID: {}", userId);

        // Verificar que el usuario existe
        Optional<User> usuario = userRepository.findById(userId);

        if (usuario.isEmpty()) {
            throw new RuntimeException("Usuario no encontrado con ID: " + userId);
        }

        try {
            // Eliminar todos los datos relacionados del usuario en orden
            log.info("Eliminando trading bots del usuario {}", userId);
            tradingBotRepository.deleteByUserId(userId);

            log.info("Eliminando alertas del usuario {}", userId);
            alertRepository.deleteByUserId(userId);

            log.info("Eliminando portfolio del usuario {}", userId);
            portfolioRepository.deleteByUserId(userId);

            log.info("Eliminando transacciones del usuario {}", userId);
            transactionRepository.deleteByUserId(userId);

            // Finalmente, eliminar el usuario
            log.info("Eliminando usuario {}", userId);
            userRepository.deleteById(userId);

            log.info("Usuario {} y todos sus datos han sido eliminados exitosamente", userId);
        } catch (Exception e) {
            log.error("Error al eliminar usuario {} y sus datos: {}", userId, e.getMessage());
            throw new RuntimeException("Error al eliminar usuario: " + e.getMessage());
        }
    }
}