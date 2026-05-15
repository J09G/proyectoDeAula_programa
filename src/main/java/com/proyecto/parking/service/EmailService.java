package com.proyecto.parking.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class EmailService {

    @Value("${brevo.api.key}")
    private String apiKey;

    @Value("${brevo.sender.email}")
    private String senderEmail;

    private final RestTemplate restTemplate = new RestTemplate();

    @Async
    public void enviarCorreo(String destinatario, String asunto, String cuerpo) {
        try {
            String body = "{"
                + "\"sender\":{\"name\":\"ParkingApp\",\"email\":\"" + senderEmail + "\"},"
                + "\"to\":[{\"email\":\"" + destinatario + "\"}],"
                + "\"subject\":\"" + escapeJson(asunto) + "\","
                + "\"textContent\":\"" + escapeJson(cuerpo) + "\""
                + "}";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", apiKey);

            HttpEntity<String> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity("https://api.brevo.com/v3/smtp/email", request, String.class);
        } catch (Exception e) {
            System.err.println("Error al enviar correo al cliente: " + e.getMessage());
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "");
    }
}
