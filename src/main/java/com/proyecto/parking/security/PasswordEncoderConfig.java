package com.proyecto.parking.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    /**
     * Delegating encoder: los hashes nuevos se guardan con el prefijo
     * {@code {bcrypt}}, pero sigue verificando los antiguos sin prefijo gracias a
     * {@code setDefaultPasswordEncoderForMatches}. Así las contraseñas ya
     * existentes en la base de datos siguen funcionando y, a la vez, queda la
     * puerta abierta a migrar a otro algoritmo sin romper nada.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        var delegating = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        ((org.springframework.security.crypto.password.DelegatingPasswordEncoder) delegating)
                .setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        return delegating;
    }
}
