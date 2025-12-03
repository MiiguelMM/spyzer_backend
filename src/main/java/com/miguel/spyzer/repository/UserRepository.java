package com.miguel.spyzer.repository;

import com.miguel.spyzer.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Para OAuth Google - autenticación
    Optional<User> findByGoogleId(String googleId);
    Optional<User> findByEmail(String email);
    boolean existsByGoogleId(String googleId);
    
    // Para ranking - top 150 usuarios con más dinero actual
    List<User> findTop150ByOrderByBalanceActualDesc();
}