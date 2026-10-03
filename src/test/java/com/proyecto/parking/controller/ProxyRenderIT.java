package com.proyecto.parking.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La app detrás de un proxy como el de Render, con un Tomcat real (MockMvc no
 * pasa por el RemoteIpValve de Tomcat, que es justo lo que se prueba aquí).
 * Las peticiones llegan desde 127.0.0.1, que Tomcat trata como proxy de
 * confianza, igual que la red interna de Render.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.data.mongodb.uri=mongodb://localhost:27017/parking-test-no-se-usa",
                "spring.data.mongodb.database=parking-test",
                "brevo.api.key=test-key-no-se-usa",
                "de.flapdoodle.mongodb.embedded.version=7.0.5",
                "server.forward-headers-strategy=native"
        })
class ProxyRenderIT {

    @LocalServerPort
    private int puerto;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private HttpResponse<String> enviar(HttpRequest.Builder peticion) throws Exception {
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void googleRecibeLaDireccionHttpsPublicaAunqueLaAppHableHttp() throws Exception {
        HttpResponse<String> respuesta = enviar(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + puerto + "/oauth2/authorization/google"))
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "parking.onrender.com")
                .header("X-Forwarded-For", "8.8.4.4"));

        String destino = URLDecoder.decode(respuesta.headers().firstValue("Location").orElseThrow(),
                StandardCharsets.UTF_8);
        assertTrue(destino.contains("redirect_uri=https://parking.onrender.com/login/oauth2/code/google"),
                "Google exige la misma URI registrada, con https y el dominio público: " + destino);
    }

    /**
     * El atacante pone una IP inventada distinta en cada intento; el proxy
     * agrega al final su IP real. Antes se tomaba la primera (la inventada) y
     * el bloqueo nunca llegaba.
     */
    @Test
    void unaIpFalsaEnXForwardedForNoEvitaElBloqueoPorIntentos() throws Exception {
        String ipReal = "203.0.113.7";

        for (int intento = 1; intento <= 4; intento++) {
            assertTrue(intentarLogin("198.51.100." + intento + ", " + ipReal).endsWith("/login?error=credenciales"),
                    "Intento " + intento);
        }
        assertTrue(intentarLogin("198.51.100.99, " + ipReal).endsWith("/login?error=bloqueado"),
                "Al quinto fallo desde la misma IP real se bloquea, aunque la primera IP cambie");
    }

    /** Login fallido como lo haría un navegador: pide el formulario, toma el CSRF y envía. */
    private String intentarLogin(String xForwardedFor) throws Exception {
        HttpResponse<String> formulario = enviar(HttpRequest.newBuilder(
                URI.create("http://localhost:" + puerto + "/login")));

        String cookieCsrf = formulario.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.substring(0, c.indexOf(';')))
                .findFirst().orElseThrow();
        Matcher campo = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(formulario.body());
        assertTrue(campo.find(), "El formulario de login debe traer el token CSRF");

        String cuerpo = "email=" + URLEncoder.encode("nadie@correo.com", StandardCharsets.UTF_8)
                + "&password=incorrecta&_csrf=" + URLEncoder.encode(campo.group(1), StandardCharsets.UTF_8);

        HttpResponse<String> respuesta = enviar(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + puerto + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", cookieCsrf)
                .header("X-Forwarded-For", xForwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo)));

        assertEquals(302, respuesta.statusCode());
        return respuesta.headers().firstValue("Location").orElseThrow();
    }
}
