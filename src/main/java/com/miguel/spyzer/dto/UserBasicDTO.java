package com.miguel.spyzer.dto;

public class UserBasicDTO {
    private Long id;
    private String name;
    private String avatar;
    
    // Constructor vacío
    public UserBasicDTO() {}
    
    // Constructor completo
    public UserBasicDTO(Long id, String name, String avatar) {
        this.id = id;
        this.name = name;
        this.avatar = avatar;
    }
    
    // Getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
}