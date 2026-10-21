package com.proyecto.parking.service.impl;

import com.proyecto.parking.config.ParkingProperties;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Pago;
import com.proyecto.parking.service.PasarelaPago;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Pasarela Mercado Pago, con su "Checkout Pro": la aplicación crea una
 * preferencia de pago con el importe, Mercado Pago responde con la dirección de
 * su página de pago, el cliente paga allí y vuelve a la aplicación con el id del
 * pago.
 *
 * <p>Nunca se da por bueno lo que llegue en la URL de retorno o en un aviso: el
 * estado, el importe y la referencia se leen siempre de la API de Mercado Pago.</p>
 */
@Service
public class MercadoPagoPasarela implements PasarelaPago {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoPasarela.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAPA =
            new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate;
    private final ParkingProperties properties;
    private final ParkingProperties.MercadoPago config;

    public MercadoPagoPasarela(RestTemplateBuilder builder, ParkingProperties properties) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
        this.properties = properties;
        this.config = properties.getPagos().getMercadoPago();
    }

    @Override
    public String nombre() {
        return "Mercado Pago";
    }

    /**
     * Crea la preferencia de pago. El importe viaja de servidor a servidor con
     * el access token, así que el cliente no tiene cómo cambiarlo.
     */
    @Override
    public String urlDePago(Pago pago, String urlRetorno) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", pago.getReferencia());
        item.put("title", "Anticipo de reserva - ParkingApp");
        item.put("quantity", 1);
        item.put("currency_id", pago.getMoneda());
        item.put("unit_price", BigDecimal.valueOf(pago.getMontoCentavos(), 2));

        Map<String, Object> preferencia = new LinkedHashMap<>();
        preferencia.put("items", List.of(item));
        // Así se reconoce de qué pago de ParkingApp es cada pago de Mercado Pago.
        preferencia.put("external_reference", pago.getReferencia());
        preferencia.put("back_urls", Map.of("success", urlRetorno, "failure", urlRetorno, "pending", urlRetorno));
        // Mercado Pago solo devuelve automáticamente al cliente y solo envía
        // avisos a direcciones HTTPS: en local (http) el cliente vuelve con el
        // botón "Volver al sitio" y el pago se confirma en ese momento.
        if (esHttps(urlRetorno)) {
            preferencia.put("auto_return", "approved");
        }
        String urlAvisos = properties.getUrlPublica() + "/pagos/eventos";
        if (esHttps(urlAvisos)) {
            preferencia.put("notification_url", urlAvisos);
        }

        try {
            ResponseEntity<Map<String, Object>> respuesta = restTemplate.exchange(
                    config.getUrlApi() + "/checkout/preferences",
                    HttpMethod.POST, new HttpEntity<>(preferencia, cabeceras()), MAPA);
            if (respuesta.getBody() != null && respuesta.getBody().get("init_point") instanceof String url
                    && !url.isBlank()) {
                return url;
            }
            log.warn("Mercado Pago creó la preferencia de {} pero no devolvió init_point.", pago.getReferencia());
        } catch (RestClientException e) {
            log.warn("Mercado Pago no pudo crear la preferencia de {}: {}", pago.getReferencia(), e.getMessage());
        }
        throw new ReglaNegocioException("No pudimos conectar con Mercado Pago. Intenta de nuevo en un momento.");
    }

    @Override
    public Optional<Transaccion> consultarTransaccion(String idTransaccion) {
        // Los ids de pago de Mercado Pago son números: cualquier otra cosa no se
        // pega a la URL de la API.
        if (sinTexto(config.getAccessToken()) || idTransaccion == null || !idTransaccion.matches("\\d{1,20}")) {
            return Optional.empty();
        }
        try {
            ResponseEntity<Map<String, Object>> respuesta = restTemplate.exchange(
                    config.getUrlApi() + "/v1/payments/" + idTransaccion,
                    HttpMethod.GET, new HttpEntity<>(cabeceras()), MAPA);
            return Optional.ofNullable(respuesta.getBody()).flatMap(MercadoPagoPasarela::aTransaccion);
        } catch (RestClientException e) {
            log.warn("Mercado Pago no respondió al consultar el pago {}: {}", idTransaccion, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<Transaccion> consultarPorReferencia(String referencia) {
        if (sinTexto(config.getAccessToken())) {
            return Optional.empty();
        }
        try {
            String url = UriComponentsBuilder.fromUriString(config.getUrlApi() + "/v1/payments/search")
                    .queryParam("external_reference", referencia)
                    .encode().toUriString();
            ResponseEntity<Map<String, Object>> respuesta = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(cabeceras()), MAPA);
            Object resultados = respuesta.getBody() != null ? respuesta.getBody().get("results") : null;
            if (!(resultados instanceof List<?> lista)) {
                return Optional.empty();
            }
            // Puede haber varios intentos con la misma referencia: manda el aprobado.
            List<Transaccion> transacciones = lista.stream()
                    .map(MercadoPagoPasarela::aTransaccion).flatMap(Optional::stream).toList();
            return transacciones.stream().filter(Transaccion::aprobada).findFirst()
                    .or(() -> transacciones.stream().findFirst());
        } catch (RestClientException e) {
            log.warn("Mercado Pago no respondió al buscar la referencia {}: {}", referencia, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Devolución total. A diferencia de otras pasarelas, Mercado Pago devuelve
     * por API cualquier medio de pago (tarjeta, PSE, saldo...), siempre que la
     * cuenta tenga saldo suficiente.
     */
    @Override
    public boolean devolver(String idTransaccion) {
        if (sinTexto(config.getAccessToken()) || idTransaccion == null || !idTransaccion.matches("\\d{1,20}")) {
            return false;
        }
        HttpHeaders cabeceras = cabeceras();
        // La misma clave en cada reintento: Mercado Pago no devuelve dos veces el mismo pago.
        cabeceras.set("X-Idempotency-Key", "devolucion-" + idTransaccion);
        try {
            ResponseEntity<Map<String, Object>> respuesta = restTemplate.exchange(
                    config.getUrlApi() + "/v1/payments/" + idTransaccion + "/refunds",
                    HttpMethod.POST, new HttpEntity<>(Map.of(), cabeceras), MAPA);
            return respuesta.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.warn("Mercado Pago no pudo devolver el pago {}: {}", idTransaccion, e.getMessage());
            return false;
        }
    }

    /**
     * Mercado Pago firma cada aviso en la cabecera {@code x-signature}
     * ({@code ts=...,v1=...}): v1 es un HMAC-SHA256 de
     * {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;} con la clave
     * secreta de los webhooks. Sin esa clave no se puede comprobar nada y el
     * aviso se descarta.
     */
    @Override
    public Optional<String> transaccionDelAviso(Aviso aviso) {
        if (sinTexto(config.getSecretoWebhook()) || aviso == null) {
            return Optional.empty();
        }
        Map<String, String> parametros = aviso.parametros() != null ? aviso.parametros() : Map.of();
        Map<String, String> cabeceras = aviso.cabeceras() != null ? aviso.cabeceras() : Map.of();
        Map<String, Object> cuerpo = aviso.cuerpo() != null ? aviso.cuerpo() : Map.of();

        // Solo interesan los avisos de pagos (también llegan de otros tipos).
        String tipo = primeroConTexto(parametros.get("type"), parametros.get("topic"), texto(cuerpo.get("type")));
        String idPago = primeroConTexto(parametros.get("data.id"),
                cuerpo.get("data") instanceof Map<?, ?> datos ? texto(datos.get("id")) : null);
        if (!"payment".equals(tipo) || sinTexto(idPago)) {
            return Optional.empty();
        }

        String ts = null;
        String v1 = null;
        for (String parte : String.valueOf(cabeceras.get("x-signature")).split(",")) {
            String[] claveValor = parte.trim().split("=", 2);
            if (claveValor.length == 2 && "ts".equals(claveValor[0])) {
                ts = claveValor[1];
            } else if (claveValor.length == 2 && "v1".equals(claveValor[0])) {
                v1 = claveValor[1];
            }
        }
        if (ts == null || v1 == null) {
            return Optional.empty();
        }

        String esperada = firmaDelAviso(idPago, cabeceras.get("x-request-id"), ts);
        boolean valida = MessageDigest.isEqual(
                esperada.getBytes(StandardCharsets.UTF_8),
                v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        return valida ? Optional.of(idPago) : Optional.empty();
    }

    /** HMAC-SHA256 en hexadecimal; las partes que no llegaron se omiten, como indica Mercado Pago. */
    String firmaDelAviso(String idPago, String idPeticion, String ts) {
        StringBuilder plantilla = new StringBuilder();
        if (!sinTexto(idPago)) {
            plantilla.append("id:").append(idPago.toLowerCase(Locale.ROOT)).append(';');
        }
        if (!sinTexto(idPeticion)) {
            plantilla.append("request-id:").append(idPeticion).append(';');
        }
        if (!sinTexto(ts)) {
            plantilla.append("ts:").append(ts).append(';');
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.getSecretoWebhook().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(plantilla.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 no disponible", e);
        }
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    /** Convierte un pago de Mercado Pago al formato común de la aplicación. */
    static Optional<Transaccion> aTransaccion(Object dato) {
        if (!(dato instanceof Map<?, ?> p) || p.get("id") == null) {
            return Optional.empty();
        }
        long centavos = p.get("transaction_amount") instanceof Number n
                ? new BigDecimal(n.toString()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
                : -1;
        String metodo = texto(p.get("payment_method_id"));
        return Optional.of(new Transaccion(
                texto(p.get("id")),
                texto(p.get("external_reference")),
                estadoComun(texto(p.get("status"))),
                centavos,
                texto(p.get("currency_id")),
                metodo != null ? metodo.toUpperCase(Locale.ROOT) : null));
    }

    /**
     * Traduce los estados de Mercado Pago a los tres que entiende la aplicación:
     * aprobado, todavía en curso, o no aprobado.
     */
    static String estadoComun(String estadoMercadoPago) {
        if (estadoMercadoPago == null) {
            return "DECLINED";
        }
        return switch (estadoMercadoPago) {
            case "approved" -> "APPROVED";
            case "pending", "in_process", "authorized", "in_mediation" -> "PENDING";
            default -> "DECLINED"; // rejected, cancelled, refunded, charged_back
        };
    }

    private HttpHeaders cabeceras() {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(config.getAccessToken());
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        return cabeceras;
    }

    private static boolean esHttps(String url) {
        return url != null && url.startsWith("https://");
    }

    private static String primeroConTexto(String... valores) {
        for (String valor : valores) {
            if (!sinTexto(valor)) {
                return valor;
            }
        }
        return null;
    }

    private static String texto(Object valor) {
        return valor != null ? valor.toString() : null;
    }

    private static boolean sinTexto(String valor) {
        return valor == null || valor.isBlank();
    }
}
