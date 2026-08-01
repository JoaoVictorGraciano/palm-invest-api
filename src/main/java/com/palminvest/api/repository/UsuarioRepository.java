package com.palminvest.api.repository;

import com.palminvest.api.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.userdetails.UserDetails;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    
    // O Spring Security vai usar esse método para procurar o usuário na hora do login!
    UserDetails findByEmail(String email);
}