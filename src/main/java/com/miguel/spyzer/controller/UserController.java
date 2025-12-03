package com.miguel.spyzer.controller;

import com.miguel.spyzer.config.JwtService;
import com.miguel.spyzer.dto.UserResponseDTO;
import com.miguel.spyzer.dto.UserRankingDTO;
import com.miguel.spyzer.service.UserService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;

    /**
     * Obtener perfil del usuario actual
     * GET /api/users/{userId}/perfil
     */
    @GetMapping("/{userId}/perfil")
    public ResponseEntity<UserResponseDTO> obtenerPerfil(@PathVariable Long userId) {
        log.info("GET /api/users/{}/perfil - Obteniendo perfil del usuario", userId);
        UserResponseDTO perfil = userService.obtenerPerfil(userId);
        return ResponseEntity.ok(perfil);
    }

    /**
     * Obtener ranking completo de usuarios
     * GET /api/users/ranking
     */
    @GetMapping("/ranking")
    public ResponseEntity<List<UserRankingDTO>> obtenerRanking() {
        log.info("GET /api/users/ranking - Obteniendo ranking de usuarios");
        return ResponseEntity.ok(userService.obtenerRanking());
    }

    /**
     * Obtener top del ranking (primeros N usuarios)
     * GET /api/users/ranking/top/{limite}
     */
    @GetMapping("/ranking/top/{limite}")
    public ResponseEntity<List<UserRankingDTO>> obtenerTopRanking(@PathVariable int limite) {
        log.info("GET /api/users/ranking/top/{} - Obteniendo top del ranking", limite);
        if (limite <= 0 || limite > 150) {
            throw new IllegalArgumentException("El límite debe estar entre 1 y 150");
        }

        List<UserRankingDTO> rankingCompleto = userService.obtenerRanking();
        List<UserRankingDTO> topRanking = rankingCompleto.stream()
                .limit(limite)
                .toList();

        return ResponseEntity.ok(topRanking);
    }

    /**
     * Buscar posición de un usuario en el ranking
     * GET /api/users/{userId}/posicion-ranking
     */
    @GetMapping("/{userId}/posicion-ranking")
    public ResponseEntity<?> obtenerPosicionRanking(@PathVariable Long userId) {
        log.info("GET /api/users/{}/posicion-ranking - Buscando posición en ranking", userId);

        List<UserRankingDTO> ranking = userService.obtenerRanking();

        UserRankingDTO usuarioEnRanking = ranking.stream()
                .filter(user -> user.getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado en el ranking"));

        return ResponseEntity.ok(Map.of(
                "posicion", usuarioEnRanking.getPosicion(),
                "totalUsuarios", ranking.size(),
                "usuario", usuarioEnRanking));
    }

    /**
     * Obtener balance actual del usuario
     * GET /api/users/{userId}/balance
     */
    @GetMapping("/{userId}/balance")
    public ResponseEntity<?> obtenerBalance(@PathVariable Long userId) {
        log.info("GET /api/users/{}/balance - Obteniendo balance actual", userId);
        BigDecimal balance = userService.obtenerBalanceActual(userId);
        return ResponseEntity.ok(Map.of("balanceActual", balance));
    }

    /**
     * Verificar si el usuario tiene suficiente balance
     * POST /api/users/{userId}/verificar-balance
     */
    @PostMapping("/{userId}/verificar-balance")
    public ResponseEntity<?> verificarBalance(@PathVariable Long userId,
            @Valid @RequestBody VerificarBalanceRequest request) {
        log.info("POST /api/users/{}/verificar-balance - Verificando balance para {}", userId, request.getCantidad());

        boolean tieneSuficiente = userService.tieneSuficienteBalance(userId, request.getCantidad());
        BigDecimal balanceActual = userService.obtenerBalanceActual(userId);

        return ResponseEntity.ok(Map.of(
                "tieneSuficienteBalance", tieneSuficiente,
                "balanceActual", balanceActual,
                "cantidadSolicitada", request.getCantidad(),
                "diferencia", balanceActual.subtract(request.getCantidad())));
    }

    /**
     * Obtener estadísticas generales del usuario
     * GET /api/users/{userId}/estadisticas
     */
    @GetMapping("/{userId}/estadisticas")
    public ResponseEntity<?> obtenerEstadisticasGenerales(@PathVariable Long userId) {
        log.info("GET /api/users/{}/estadisticas - Obteniendo estadísticas generales", userId);

        UserResponseDTO perfil = userService.obtenerPerfil(userId);

        // Calcular estadísticas adicionales
        BigDecimal balanceActual = userService.obtenerBalanceActual(userId);
        BigDecimal gananciaPerdida = balanceActual.subtract(perfil.getBalanceActual());
        BigDecimal porcentajeGanancia = BigDecimal.ZERO;

        if (perfil.getBalanceActual().compareTo(BigDecimal.ZERO) > 0) {
            porcentajeGanancia = gananciaPerdida
                    .divide(perfil.getBalanceActual(), 4, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        // Buscar posición en ranking
        List<UserRankingDTO> ranking = userService.obtenerRanking();
        Integer posicionRanking = null;

        for (UserRankingDTO user : ranking) {
            if (user.getId().equals(userId)) {
                posicionRanking = user.getPosicion();
                break;
            }
        }

        EstadisticasGenerales estadisticas = new EstadisticasGenerales(
                perfil,
                gananciaPerdida,
                porcentajeGanancia,
                posicionRanking,
                ranking.size());

        return ResponseEntity.ok(estadisticas);
    }

    /**
     * Eliminar cuenta de usuario (con validación de seguridad)
     * DELETE /api/users/{userId}
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<?> eliminarUsuario(@PathVariable Long userId, HttpServletRequest request) {
        log.info("DELETE /api/users/{} - Solicitud de eliminación de cuenta", userId);

        try {
            // Extraer y validar el token JWT
            String token = extractTokenFromRequest(request);
            if (token == null) {
                log.warn("Intento de eliminación sin token de autenticación");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "No authentication token provided"
                ));
            }

            // Obtener el userId del token
            Long authenticatedUserId = extractUserIdFromToken(token);
            if (authenticatedUserId == null) {
                log.warn("Token inválido o sin userId");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "Invalid authentication token"
                ));
            }

            // SEGURIDAD: Verificar que el usuario autenticado es el mismo que se quiere eliminar
            if (!authenticatedUserId.equals(userId)) {
                log.warn("Usuario {} intentó eliminar la cuenta del usuario {}", authenticatedUserId, userId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "You can only delete your own account"
                ));
            }

            // Proceder con la eliminación
            userService.eliminarUsuario(userId);
            log.info("Usuario {} eliminado exitosamente", userId);

            return ResponseEntity.ok(Map.of(
                "message", "User account deleted successfully",
                "userId", userId
            ));
        } catch (RuntimeException e) {
            log.error("Error al eliminar usuario {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Error deleting user account: " + e.getMessage()
            ));
        }
    }

    /**
     * Extraer token JWT del header Authorization
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * Extraer userId del token JWT
     */
    private Long extractUserIdFromToken(String token) {
        try {
            Claims claims = jwtService.extractClaim(token, claims1 -> claims1);
            Object userIdObj = claims.get("userId");

            if (userIdObj instanceof Integer) {
                return ((Integer) userIdObj).longValue();
            } else if (userIdObj instanceof Long) {
                return (Long) userIdObj;
            }
            return null;
        } catch (Exception e) {
            log.error("Error extrayendo userId del token: {}", e.getMessage());
            return null;
        }
    }

    // DTOs para requests y responses

    public static class VerificarBalanceRequest {
        @NotNull(message = "La cantidad es requerida")
        @DecimalMin(value = "0.01", message = "La cantidad debe ser mayor a 0")
        private BigDecimal cantidad;

        public BigDecimal getCantidad() {
            return cantidad;
        }

        public void setCantidad(BigDecimal cantidad) {
            this.cantidad = cantidad;
        }
    }

    public static class EstadisticasGenerales {
        private final UserResponseDTO perfil;
        private final BigDecimal gananciaPerdida;
        private final BigDecimal porcentajeGanancia;
        private final Integer posicionRanking;
        private final int totalUsuarios;

        public EstadisticasGenerales(UserResponseDTO perfil, BigDecimal gananciaPerdida,
                BigDecimal porcentajeGanancia, Integer posicionRanking,
                int totalUsuarios) {
            this.perfil = perfil;
            this.gananciaPerdida = gananciaPerdida;
            this.porcentajeGanancia = porcentajeGanancia;
            this.posicionRanking = posicionRanking;
            this.totalUsuarios = totalUsuarios;
        }

        // Getters
        public UserResponseDTO getPerfil() {
            return perfil;
        }

        public BigDecimal getGananciaPerdida() {
            return gananciaPerdida;
        }

        public BigDecimal getPorcentajeGanancia() {
            return porcentajeGanancia;
        }

        public Integer getPosicionRanking() {
            return posicionRanking;
        }

        public int getTotalUsuarios() {
            return totalUsuarios;
        }
    }
}