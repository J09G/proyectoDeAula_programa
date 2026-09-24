package com.proyecto.parking.service.impl;

import com.proyecto.parking.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Envío de correo a través de la API HTTP de Brevo.
 *
 * <p>El cuerpo se construye con un {@code Map} y lo serializa Jackson, en lugar
 * de concatenar JSON a mano: así el escapado de comillas, saltos de línea y
 * acentos deja de ser responsabilidad nuestra.</p>
 *
 * <p>Si no hay API key configurada, el servicio no falla: registra en el log el
 * correo que habría enviado. Eso permite trabajar en local sin credenciales.</p>
 */
@Service
public class BrevoEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailService.class);

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String apiUrl;
    private final String senderEmail;
    private final String senderName;

    public BrevoEmailService(RestTemplateBuilder builder,
                             @Value("${brevo.api.key:}") String apiKey,
                             @Value("${brevo.api.url}") String apiUrl,
                             @Value("${brevo.sender.email}") String senderEmail,
                             @Value("${brevo.sender.name}") String senderName) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.senderEmail = senderEmail;
        this.senderName = senderName;
    }

    @Override
    @Async
    public void enviarCorreo(String destinatario, String asunto, String cuerpo) {
        if (destinatario == null || destinatario.isBlank()) {
            log.warn("Se pidió enviar un correo sin destinatario. Se omite.");
            return;
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.info("BREVO_API_KEY no configurada. Correo NO enviado a {} (asunto: {}).",
                    destinatario, asunto);
            return;
        }

        Map<String, Object> payload = Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", destinatario)),
                "subject", asunto,
                "textContent", cuerpo);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);

        try {
            restTemplate.postForEntity(apiUrl, new HttpEntity<>(payload, headers), String.class);
            log.debug("Correo enviado a {}.", destinatario);
        } catch (RestClientException e) {
            // Un fallo de correo no debe tumbar la operación de negocio que lo disparó.
            log.error("No se pudo enviar el correo a {}: {}", destinatario, e.getMessage());
        }
    }
}
