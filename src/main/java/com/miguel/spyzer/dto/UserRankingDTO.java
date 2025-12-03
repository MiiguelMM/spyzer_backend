package com.miguel.spyzer.dto;

import java.math.BigDecimal;

public class UserRankingDTO {
    private Long id;
    private String name;
    private String avatar;
    private BigDecimal balanceActual;
    private Integer posicion; // Posición en el ranking
    
    // Constructor vacío
    public UserRankingDTO() {}
    
    // Constructor completo
    public UserRankingDTO(Long id, String name, String avatar, BigDecimal balanceActual, Integer posicion) {
        this.id = id;
        this.name = name;
        this.avatar = avatar;
        this.balanceActual = balanceActual;
        this.posicion = posicion;
    }
    
    // Getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    
    public BigDecimal getBalanceActual() { return balanceActual; }
    public void setBalanceActual(BigDecimal balanceActual) { this.balanceActual = balanceActual; }
    
    public Integer getPosicion() { return posicion; }
    public void setPosicion(Integer posicion) { this.posicion = posicion; }
}
