package com.miguel.spyzer.controller;

import com.miguel.spyzer.service.NotificationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {
    
    private final NotificationService notificationService;
    
    /**
     * Endpoint para probar el envío de emails
     * POST /api/notifications/test
     */
    @PostMapping("/test")
    public ResponseEntity<?> enviarEmailPrueba(@RequestBody TestEmailRequest request) {
        log.info("POST /api/notifications/test - Enviando email de prueba a {}", request.getEmail());
        
        try {
            notificationService.enviarEmailPrueba(request.getEmail());
            
            return ResponseEntity.ok(Map.of(
                "mensaje", "Email de prueba enviado correctamente",
                "destinatario", request.getEmail()
            ));
            
        } catch (Exception e) {
            log.error("Error enviando email de prueba: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error enviando email: " + e.getMessage()));
        }
    }
    
    @Data
    public static class TestEmailRequest {
        private String email;
    }
}