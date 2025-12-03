package com.miguel.spyzer.mapper;

import com.miguel.spyzer.entities.User;
import com.miguel.spyzer.dto.UserResponseDTO;
import com.miguel.spyzer.dto.UserRankingDTO;
import com.miguel.spyzer.dto.UserBasicDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {
    
    // Entity → UserResponseDTO (perfil completo del usuario)
    UserResponseDTO toUserResponseDTO(User user);
    
    // Entity → UserBasicDTO (solo datos básicos)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "avatar", source = "avatar")
    UserBasicDTO toUserBasicDTO(User user);
    
    // Entity → UserRankingDTO (para ranking, sin posición aún)
    @Mapping(target = "posicion", ignore = true) // Se asigna manualmente después
    UserRankingDTO toUserRankingDTO(User user);
    
    // Lista de usuarios → Lista de DTOs para ranking
    List<UserRankingDTO> toUserRankingDTOList(List<User> users);
    
    // Lista de usuarios → Lista de DTOs básicos
    List<UserBasicDTO> toUserBasicDTOList(List<User> users);
}