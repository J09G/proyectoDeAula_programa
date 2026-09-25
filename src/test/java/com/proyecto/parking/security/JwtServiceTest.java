package com.proyecto.parking.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    @Test
    void noArrancaEnProduccionConElSecretoDeDesarrollo() {
        assertThrows(IllegalStateException.class, () ->
                new JwtService("dev-only-secret-cambiar-en-produccion-32chars", 900000, "prod"));
    }

    @Test
    void arrancaEnProduccionSiElSecretoFueSobrescrito() {
        JwtService service = new JwtService("un-secreto-real-de-produccion-32-caracteres", 900000, "prod");
        assertEquals("cliente@correo.com",
                service.validarYObtenerClaims(service.generarToken("cliente@correo.com", "Cliente")).getSubject());
    }

    @Test
    void generaYValidaUnTokenValido() {
        JwtService service = new JwtService("dev-only-secret-cambiar-en-produccion-32chars", 900000);

        String token = service.generarToken("cliente@correo.com", "Cliente");
        Claims claims = service.validarYObtenerClaims(token);

        assertEquals("cliente@correo.com", claims.getSubject());
        assertEquals("Cliente", claims.get("rol", String.class));
    }

    @Test
    void rechazaUnTokenExpirado() throws InterruptedException {
        JwtService service = new JwtService("dev-only-secret-cambiar-en-produccion-32chars", 1);

        String token = service.generarToken("cliente@correo.com", "Cliente");
        Thread.sleep(5);

        assertThrows(ExpiredJwtException.class, () -> service.validarYObtenerClaims(token));
    }

    @Test
    void rechazaUnTokenFirmadoConOtraClave() {
        JwtService firmante = new JwtService("dev-only-secret-cambiar-en-produccion-32chars", 900000);
        JwtService verificador = new JwtService("otra-clave-distinta-de-32-caracteres!!", 900000);

        String token = firmante.generarToken("cliente@correo.com", "Cliente");

        assertThrows(JwtException.class, () -> verificador.validarYObtenerClaims(token));
    }
}
