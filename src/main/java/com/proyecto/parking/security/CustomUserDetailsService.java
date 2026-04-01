package com.proyecto.parking.security;

import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

// Le dice a Spring Security cómo buscar un usuario en nuestra BD (RF-23)
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByCorreo(correo);

        if (usuario == null) {
            throw new UsernameNotFoundException("Usuario no encontrado: " + correo);
        }

        // Ejemplo: "Administrador" → "ROLE_ADMINISTRADOR"
        String authority = "ROLE_" + usuario.getRol().getNombre().toUpperCase();

        return User.builder()
                .username(usuario.getCorreo())
                .password(usuario.getContrasena())
                .authorities(Collections.singletonList(new SimpleGrantedAuthority(authority)))
                .disabled(!usuario.isHabilitado()) // bloquea si la cuenta está deshabilitada
                .build();
    }
}
