package com.miguel.spyzer.service;

import com.miguel.spyzer.entities.Alert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    @Autowired
    private final JavaMailSender mailSender;
    
    @Value("${spring.mail.username}")
    private String fromEmail;
    
    @Value("${app.notification.enabled:true}")
    private boolean notificationsEnabled;
    
    /**
     * Enviar notificación de alerta disparada
     */
    @Async
    public void enviarNotificacionAlerta(Alert alerta) {
        if (!notificationsEnabled) {
            log.info("Notificaciones deshabilitadas. Alerta no enviada: {}", alerta.getId());
            return;
        }
        
        try {
            String emailUsuario = alerta.getUser().getEmail();
            
            if (emailUsuario == null || emailUsuario.isBlank()) {
                log.warn("Usuario {} no tiene email configurado", alerta.getUser().getId());
                return;
            }
            
            enviarEmailAlerta(alerta, emailUsuario);
            log.info("Notificación enviada exitosamente para alerta {} a {}", alerta.getId(), emailUsuario);
            
        } catch (Exception e) {
            log.error("Error enviando notificación para alerta {}: {}", alerta.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Enviar email simple (texto plano)
     */
    private void enviarEmailSimple(Alert alerta, String destinatario) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(destinatario);
            message.setSubject("🔔 Alerta de Precio: " + alerta.getSymbol());
            message.setText(construirMensajeTexto(alerta));
            
            mailSender.send(message);
            
        } catch (Exception e) {
            log.error("Error enviando email simple: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * Enviar email HTML (con formato bonito)
     */
    private void enviarEmailAlerta(Alert alerta, String destinatario) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(destinatario);
            helper.setSubject("🔔 Alerta de Precio: " + alerta.getSymbol());
            helper.setText(construirMensajeHTML(alerta), true);
            
            mailSender.send(mimeMessage);
            
        } catch (Exception e) {
            log.error("Error enviando email HTML: {}", e.getMessage());
            // Fallback a email simple si falla el HTML
            enviarEmailSimple(alerta, destinatario);
        }
    }
    
    /**
     * Construir mensaje de texto plano
     */
    private String construirMensajeTexto(Alert alerta) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        
        return String.format("""
                ¡Tu alerta ha sido disparada!
                
                Símbolo: %s
                Tipo: %s
                Precio objetivo: $%.2f
                Fecha: %s
                
                %s
                
                ---
                Este es un mensaje automático de Spyzer.
                """,
                alerta.getSymbol(),
                alerta.getTipo().getDescripcion(),
                alerta.getValorTrigger(),
                alerta.getTriggeredAt().format(formatter),
                alerta.getMensajePersonalizado() != null ? alerta.getMensajePersonalizado() : ""
        );
    }
    
    /**
     * Construir mensaje HTML con diseño profesional
     */
    private String construirMensajeHTML(Alert alerta) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        
        String color = switch (alerta.getTipo()) {
            case PRICE_UP -> "#10b981"; // Verde
            case PRICE_DOWN -> "#ef4444"; // Rojo
            case PRICE_EQUAL -> "#3b82f6"; // Azul
            default -> "#6b7280"; // Gris
        };
        
        String emoji = switch (alerta.getTipo()) {
            case PRICE_UP -> "📈";
            case PRICE_DOWN -> "📉";
            case PRICE_EQUAL -> "🎯";
            default -> "🔔";
        };
        
        return String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                            line-height: 1.6;
                            color: #333;
                            margin: 0;
                            padding: 0;
                            background-color: #f3f4f6;
                        }
                        .container {
                            max-width: 600px;
                            margin: 40px auto;
                            background: white;
                            border-radius: 12px;
                            overflow: hidden;
                            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
                        }
                        .header {
                            background: linear-gradient(135deg, %s 0%%, %s 100%%);
                            color: white;
                            padding: 30px;
                            text-align: center;
                        }
                        .header h1 {
                            margin: 0;
                            font-size: 28px;
                            font-weight: 600;
                        }
                        .content {
                            padding: 30px;
                        }
                        .alert-box {
                            background: #f9fafb;
                            border-left: 4px solid %s;
                            padding: 20px;
                            margin: 20px 0;
                            border-radius: 8px;
                        }
                        .detail-row {
                            display: flex;
                            justify-content: space-between;
                            padding: 12px 0;
                            border-bottom: 1px solid #e5e7eb;
                        }
                        .detail-row:last-child {
                            border-bottom: none;
                        }
                        .label {
                            font-weight: 600;
                            color: #6b7280;
                        }
                        .value {
                            color: #111827;
                            font-weight: 500;
                        }
                        .price {
                            font-size: 32px;
                            font-weight: 700;
                            color: %s;
                            text-align: center;
                            margin: 20px 0;
                        }
                        .message-box {
                            background: #fffbeb;
                            border: 1px solid #fcd34d;
                            padding: 15px;
                            border-radius: 8px;
                            margin: 20px 0;
                        }
                        .footer {
                            background: #f9fafb;
                            padding: 20px;
                            text-align: center;
                            color: #6b7280;
                            font-size: 12px;
                        }
                        .button {
                            display: inline-block;
                            background: %s;
                            color: white;
                            padding: 12px 30px;
                            border-radius: 6px;
                            text-decoration: none;
                            font-weight: 600;
                            margin: 20px 0;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>%s ¡Alerta Disparada!</h1>
                        </div>
                        
                        <div class="content">
                            <div class="alert-box">
                                <div class="detail-row">
                                    <span class="label">Símbolo</span>
                                    <span class="value">%s</span>
                                </div>
                                <div class="detail-row">
                                    <span class="label">Tipo de Alerta</span>
                                    <span class="value">%s</span>
                                </div>
                                <div class="detail-row">
                                    <span class="label">Disparada</span>
                                    <span class="value">%s</span>
                                </div>
                            </div>
                            
                            <div class="price">$%.2f</div>
                            
                            %s
                            
                            <center>
                                <a href="http://localhost:3000/alerts" class="button">
                                    Ver en Spyzer
                                </a>
                            </center>
                        </div>
                        
                        <div class="footer">
                            <p>Este es un mensaje automático de <strong>Spyzer</strong></p>
                            <p>No respondas a este correo</p>
                        </div>
                    </div>
                </body>
                </html>
                """,
                color, color, // Gradient header
                color, // Border color
                color, // Price color
                color, // Button color
                emoji, // Emoji
                alerta.getSymbol(),
                alerta.getTipo().getDescripcion(),
                alerta.getTriggeredAt().format(formatter),
                alerta.getValorTrigger(),
                alerta.getMensajePersonalizado() != null && !alerta.getMensajePersonalizado().isBlank()
                    ? "<div class=\"message-box\"><strong>💬 Tu mensaje:</strong><br>" + alerta.getMensajePersonalizado() + "</div>"
                    : ""
        );
    }
    
    /**
     * Enviar notificación de múltiples alertas (resumen diario)
     */
    @Async
    public void enviarResumenAlertas(String emailUsuario, java.util.List<Alert> alertas) {
        if (!notificationsEnabled || alertas.isEmpty()) {
            return;
        }
        
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(emailUsuario);
            helper.setSubject("📊 Resumen de Alertas - " + alertas.size() + " alertas disparadas");
            helper.setText(construirResumenHTML(alertas), true);
            
            mailSender.send(mimeMessage);
            log.info("Resumen de alertas enviado a {}: {} alertas", emailUsuario, alertas.size());
            
        } catch (Exception e) {
            log.error("Error enviando resumen de alertas: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Construir HTML para resumen de múltiples alertas
     */
    private String construirResumenHTML(java.util.List<Alert> alertas) {
        StringBuilder alertasHtml = new StringBuilder();
        
        for (Alert alerta : alertas) {
            String color = switch (alerta.getTipo()) {
                case PRICE_UP -> "#10b981";
                case PRICE_DOWN -> "#ef4444";
                case PRICE_EQUAL -> "#3b82f6";
                default -> "#6b7280";
            };
            
            alertasHtml.append(String.format("""
                <div style="border-left: 4px solid %s; padding: 15px; margin: 10px 0; background: #f9fafb; border-radius: 4px;">
                    <strong>%s</strong> - %s<br>
                    <span style="font-size: 24px; color: %s;">$%.2f</span>
                </div>
                """,
                color,
                alerta.getSymbol(),
                alerta.getTipo().getDescripcion(),
                color,
                alerta.getValorTrigger()
            ));
        }
        
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="font-family: Arial, sans-serif; padding: 20px;">
                <h2>📊 Resumen de tus alertas</h2>
                <p>Se dispararon <strong>%d alertas</strong> hoy:</p>
                %s
                <p style="margin-top: 30px; color: #666;">
                    <small>Este es un mensaje automático de Spyzer</small>
                </p>
            </body>
            </html>
            """,
            alertas.size(),
            alertasHtml.toString()
        );
    }
    
    /**
     * Enviar email de prueba
     */
    public void enviarEmailPrueba(String destinatario) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(destinatario);
            message.setSubject("🔔 Email de Prueba - Spyzer");
            message.setText("Este es un email de prueba del sistema de notificaciones de Spyzer.\n\nSi recibes este mensaje, el sistema está funcionando correctamente.");
            
            mailSender.send(message);
            log.info("Email de prueba enviado a: {}", destinatario);
            
        } catch (Exception e) {
            log.error("Error enviando email de prueba: {}", e.getMessage(), e);
            throw e;
        }
    }
}