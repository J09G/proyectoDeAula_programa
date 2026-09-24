package com.proyecto.parking.security;

import com.proyecto.parking.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Puente entre Spring Security y la colección {@code usuarios} de MongoDB. */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public CustomUserDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        return usuarioRepository.findByCorreo(correo)
                .map(UsuarioPrincipal::new)
                // Mensaje deliberadamente genérico: no revelamos si el correo existe.
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas."));
    }
}
