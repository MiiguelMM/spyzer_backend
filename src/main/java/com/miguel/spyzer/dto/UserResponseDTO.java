
package com.miguel.spyzer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// DTO para respuestas privadas del usuario (su propio perfil)
public class UserResponseDTO {
    private Long id;
    private String email;
    private String name;
    private String avatar;
    private BigDecimal balanceActual;
    private LocalDateTime createdAt;
    
    // Constructor vacío
    public UserResponseDTO() {}
    
    // Constructor completo
    public UserResponseDTO(Long id, String email, String name, String avatar, 
                          BigDecimal balanceActual, LocalDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.avatar = avatar;
        this.balanceActual = balanceActual;
        this.createdAt = createdAt;
    }
    
    // Getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    
    public BigDecimal getBalanceActual() { return balanceActual; }
    public void setBalanceActual(BigDecimal balanceActual) { this.balanceActual = balanceActual; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}