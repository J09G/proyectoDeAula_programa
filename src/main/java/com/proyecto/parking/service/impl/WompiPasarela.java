package com.proyecto.parking.service.impl;

import com.proyecto.parking.config.ParkingProperties;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pasarela Wompi (Bancolombia). Usa su "Web Checkout": al cliente se le envía a
 * una página de Wompi con el importe firmado, y al terminar Wompi lo devuelve a
 * la aplicación con el id de la transacción.
 *
 * <p>Nunca se da por bueno lo que llegue en la URL de retorno o en un aviso: el
 * estado, el importe y la referencia se leen siempre de la API de Wompi.</p>
 */
@Service
public class WompiPasarela implements PasarelaPago {

    private static final Logger log = LoggerFactory.getLogger(WompiPasarela.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAPA =
            new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate;
    private final ParkingProperties.Wompi config;

    public WompiPasarela(RestTemplateBuilder builder, ParkingProperties properties) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
        this.config = properties.getPagos().getWompi();
    }

    @Override
    public String urlDePago(Pago pago, String urlRetorno) {
        return UriComponentsBuilder.fromUriString(config.getUrlCheckout())
                .queryParam("public-key", config.getLlavePublica())
                .queryParam("currency", pago.getMoneda())
                .queryParam("amount-in-cents", pago.getMontoCentavos())
                .queryParam("reference", pago.getReferencia())
                .queryParam("signature:integrity",
                        firmaDeIntegridad(pago.getReferencia(), pago.getMontoCentavos(), pago.getMoneda()))
                .queryParam("redirect-url", urlRetorno)
                .encode()
                .toUriString();
    }

    /**
     * Firma que impide alterar el importe o la referencia en la URL de pago:
     * SHA-256 de referencia + importe + moneda + secreto de integridad.
     */
    String firmaDeIntegridad(String referencia, long montoCentavos, String moneda) {
        return sha256(referencia + montoCentavos + moneda + config.getSecretoIntegridad());
    }

    @Override
    public Optional<Transaccion> consultarTransaccion(String idTransaccion) {
        if (idTransaccion == null || !idTransaccion.matches("[A-Za-z0-9_-]{1,80}")) {
            return Optional.empty();
        }
        try {
            ResponseEntity<Map<String, Object>> respuesta = restTemplate.exchange(
                    config.getUrlApi() + "/transactions/" + idTransaccion,
                    HttpMethod.GET, HttpEntity.EMPTY, MAPA);
            return Optional.ofNullable(respuesta.getBody())
                    .map(cuerpo -> cuerpo.get("data"))
                    .flatMap(WompiPasarela::aTransaccion);
        } catch (RestClientException e) {
            log.warn("Wompi no respondió al consultar la transacción {}: {}", idTransaccion, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<Transaccion> consultarPorReferencia(String referencia) {
        if (sinTexto(config.getLlavePrivada())) {
            return Optional.empty();
        }
        try {
            String url = UriComponentsBuilder.fromUriString(config.getUrlApi() + "/transactions")
                    .queryParam("reference", referencia)
                    .encode().toUriString();
            ResponseEntity<Map<String, Object>> respuesta = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(cabecerasPrivadas()), MAPA);
            Object datos = respuesta.getBody() != null ? respuesta.getBody().get("data") : null;
            if (!(datos instanceof List<?> lista)) {
                return Optional.empty();
            }
            // Puede haber varios intentos con la misma referencia: manda el aprobado.
            List<Transaccion> transacciones = lista.stream()
                    .map(WompiPasarela::aTransaccion).flatMap(Optional::stream).toList();
            return transacciones.stream().filter(Transaccion::aprobada).findFirst()
                    .or(() -> transacciones.stream().findFirst());
        } catch (RestClientException e) {
            log.warn("Wompi no respondió al buscar la referencia {}: {}", referencia, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean devolver(String idTransaccion) {
        if (sinTexto(config.getLlavePrivada()) || idTransaccion == null) {
            return false;
        }
        try {
            // "Void" anula el cobro. Wompi sólo lo permite en pagos con tarjeta; con
            // otros medios responde con error y la devolución queda para hacerla a mano.
            ResponseEntity<Map<String, Object>> respuesta = restTemplate.exchange(
                    config.getUrlApi() + "/transactions/" + idTransaccion + "/void",
                    HttpMethod.POST, new HttpEntity<>(Map.of(), cabecerasPrivadas()), MAPA);
            return respuesta.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.warn("Wompi no pudo anular la transacción {}: {}", idTransaccion, e.getMessage());
            return false;
        }
    }

    /**
     * Wompi firma cada aviso: indica qué campos de la transacción concatenar, y
     * a eso se le suman la marca de tiempo y el secreto de eventos. Sin ese
     * secreto configurado no se puede comprobar nada y el aviso se descarta.
     */
    @Override
    public boolean avisoEsAutentico(Map<String, Object> aviso) {
        if (sinTexto(config.getSecretoEventos()) || aviso == null) {
            return false;
        }
        if (!(aviso.get("signature") instanceof Map<?, ?> firma)
                || !(firma.get("properties") instanceof List<?> propiedades)
                || !(firma.get("checksum") instanceof String checksum)
                || !(aviso.get("data") instanceof Map<?, ?> datos)
                || aviso.get("timestamp") == null) {
            return false;
        }
        StringBuilder texto = new StringBuilder();
        for (Object propiedad : propiedades) {
            Object valor = leerRuta(datos, String.valueOf(propiedad));
            if (valor == null) {
                return false;
            }
            texto.append(valor);
        }
        texto.append(aviso.get("timestamp")).append(config.getSecretoEventos());
        return MessageDigest.isEqual(
                sha256(texto.toString()).getBytes(StandardCharsets.UTF_8),
                checksum.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    /** Lee "transaction.amount_in_cents" dentro de un mapa anidado. */
    private static Object leerRuta(Map<?, ?> origen, String ruta) {
        Object actual = origen;
        for (String parte : ruta.split("\\.")) {
            if (!(actual instanceof Map<?, ?> mapa)) {
                return null;
            }
            actual = mapa.get(parte);
        }
        return actual;
    }

    private static Optional<Transaccion> aTransaccion(Object dato) {
        if (!(dato instanceof Map<?, ?> t) || t.get("id") == null) {
            return Optional.empty();
        }
        long monto = t.get("amount_in_cents") instanceof Number n ? n.longValue() : -1;
        return Optional.of(new Transaccion(
                String.valueOf(t.get("id")),
                texto(t.get("reference")),
                texto(t.get("status")),
                monto,
                texto(t.get("currency")),
                texto(t.get("payment_method_type"))));
    }

    private HttpHeaders cabecerasPrivadas() {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(config.getLlavePrivada());
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        return cabeceras;
    }

    private static String texto(Object valor) {
        return valor != null ? valor.toString() : null;
    }

    private static boolean sinTexto(String valor) {
        return valor == null || valor.isBlank();
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
