package com.proyecto.parking.service.impl;

import com.proyecto.parking.config.ParkingProperties;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Pago;
import com.proyecto.parking.service.PasarelaPago.Aviso;
import com.proyecto.parking.service.PasarelaPago.Transaccion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** La API de Mercado Pago se simula: estas pruebas no salen a internet. */
@DisplayName("Pasarela Mercado Pago")
class MercadoPagoPasarelaTest {

    private static final String API = "https://api.mp.test";
    private static final String SECRETO_WEBHOOK = "secreto-de-prueba";

    private ParkingProperties properties;
    private MercadoPagoPasarela pasarela;
    private MockRestServiceServer servidor;

    @BeforeEach
    void preparar() {
        properties = new ParkingProperties();
        properties.setUrlPublica("https://parking.test");
        properties.getPagos().getMercadoPago().setAccessToken("TEST-token");
        properties.getPagos().getMercadoPago().setSecretoWebhook(SECRETO_WEBHOOK);
        properties.getPagos().getMercadoPago().setUrlApi(API);

        MockServerRestTemplateCustomizer simulador = new MockServerRestTemplateCustomizer();
        pasarela = new MercadoPagoPasarela(new RestTemplateBuilder(simulador), properties);
        servidor = simulador.getServer();
    }

    private static Pago pago() {
        Pago pago = new Pago();
        pago.setReferencia("PKG-ABC123-00FF");
        pago.setMontoCentavos(500_000);
        return pago;
    }

    // ── Crear el pago ────────────────────────────────────────────────────────

    @Test
    @DisplayName("crea la preferencia con el importe, la referencia y el retorno, y devuelve la página de pago")
    void creaLaPreferencia() {
        servidor.expect(requestTo(API + "/checkout/preferences"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TEST-token"))
                .andExpect(jsonPath("$.external_reference").value("PKG-ABC123-00FF"))
                .andExpect(jsonPath("$.items[0].unit_price").value(5000.0))
                .andExpect(jsonPath("$.items[0].currency_id").value("COP"))
                .andExpect(jsonPath("$.back_urls.success").value("https://parking.test/pagos/retorno"))
                .andExpect(jsonPath("$.auto_return").value("approved"))
                .andExpect(jsonPath("$.notification_url").value("https://parking.test/pagos/eventos"))
                .andRespond(withSuccess("{\"id\":\"pref-1\",\"init_point\":\"https://mp.test/pagar?pref_id=pref-1\"}",
                        MediaType.APPLICATION_JSON));

        String url = pasarela.urlDePago(pago(), "https://parking.test/pagos/retorno");

        assertThat(url).isEqualTo("https://mp.test/pagar?pref_id=pref-1");
        servidor.verify();
    }

    @Test
    @DisplayName("en local (http) no pide volver solo ni enviar avisos: Mercado Pago solo los acepta con HTTPS")
    void enLocalSinHttps() {
        properties.setUrlPublica("http://lvh.me:8081");
        servidor.expect(requestTo(API + "/checkout/preferences"))
                .andExpect(jsonPath("$.auto_return").doesNotExist())
                .andExpect(jsonPath("$.notification_url").doesNotExist())
                .andRespond(withSuccess("{\"init_point\":\"https://mp.test/pagar\"}", MediaType.APPLICATION_JSON));

        assertThat(pasarela.urlDePago(pago(), "http://lvh.me:8081/pagos/retorno")).isEqualTo("https://mp.test/pagar");
        servidor.verify();
    }

    @Test
    @DisplayName("si Mercado Pago no responde, el cliente ve un mensaje claro en vez de un error")
    void sinRespuesta() {
        servidor.expect(requestTo(API + "/checkout/preferences")).andRespond(withServerError());

        assertThatThrownBy(() -> pasarela.urlDePago(pago(), "https://parking.test/pagos/retorno"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Mercado Pago");
    }

    // ── Consultar ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("lee el pago de la API y lo traduce al formato de la aplicación")
    void consultaElPago() {
        servidor.expect(requestTo(API + "/v1/payments/123"))
                .andExpect(header("Authorization", "Bearer TEST-token"))
                .andRespond(withSuccess("{\"id\":123,\"status\":\"approved\",\"transaction_amount\":5000,"
                        + "\"currency_id\":\"COP\",\"external_reference\":\"PKG-1\",\"payment_method_id\":\"pse\"}",
                        MediaType.APPLICATION_JSON));

        Transaccion transaccion = pasarela.consultarTransaccion("123").orElseThrow();

        assertThat(transaccion).isEqualTo(new Transaccion("123", "PKG-1", "APPROVED", 500_000, "COP", "PSE"));
    }

    @Test
    @DisplayName("no consulta ids que no sean números: podrían alterar la URL de la API")
    void idSospechoso() {
        assertThat(pasarela.consultarTransaccion("../../otra-ruta")).isEmpty();
        assertThat(pasarela.consultarTransaccion(null)).isEmpty();
        servidor.verify(); // ninguna petición
    }

    @Test
    @DisplayName("traduce los estados de Mercado Pago a aprobado, en curso o no aprobado")
    void estados() {
        assertThat(MercadoPagoPasarela.estadoComun("approved")).isEqualTo("APPROVED");
        assertThat(MercadoPagoPasarela.estadoComun("pending")).isEqualTo("PENDING");
        assertThat(MercadoPagoPasarela.estadoComun("in_process")).isEqualTo("PENDING");
        assertThat(MercadoPagoPasarela.estadoComun("rejected")).isEqualTo("DECLINED");
        assertThat(MercadoPagoPasarela.estadoComun("cancelled")).isEqualTo("DECLINED");
        assertThat(MercadoPagoPasarela.estadoComun(null)).isEqualTo("DECLINED");
    }

    // ── Devolver ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("devuelve el pago completo, con una clave que evita devolverlo dos veces")
    void devuelve() {
        servidor.expect(requestTo(API + "/v1/payments/123/refunds"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Idempotency-Key", "devolucion-123"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .body("{\"id\":1,\"status\":\"approved\"}").contentType(MediaType.APPLICATION_JSON));

        assertThat(pasarela.devolver("123")).isTrue();
    }

    @Test
    @DisplayName("si Mercado Pago no puede devolverlo, queda para hacerlo a mano")
    void devolucionFallida() {
        servidor.expect(requestTo(API + "/v1/payments/123/refunds"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(pasarela.devolver("123")).isFalse();
    }

    // ── Avisos ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("acepta un aviso firmado con la plantilla id;request-id;ts y la clave secreta")
    void avisoAutentico() throws Exception {
        String firma = hmac("id:123;request-id:req-1;ts:1704908010;");

        assertThat(pasarela.transaccionDelAviso(aviso("123", "req-1", "ts=1704908010,v1=" + firma)))
                .contains("123");
    }

    @Test
    @DisplayName("rechaza un aviso con la firma equivocada, de otro pago o sin clave configurada")
    void avisoFalso() throws Exception {
        String firma = hmac("id:123;request-id:req-1;ts:1704908010;");

        assertThat(pasarela.transaccionDelAviso(aviso("123", "req-1", "ts=1704908010,v1=0000"))).isEmpty();
        // La firma es del pago 123: no sirve para "aprobar" el 999.
        assertThat(pasarela.transaccionDelAviso(aviso("999", "req-1", "ts=1704908010,v1=" + firma))).isEmpty();
        assertThat(pasarela.transaccionDelAviso(aviso("123", "req-1", null))).isEmpty();
        assertThat(pasarela.transaccionDelAviso(null)).isEmpty();

        properties.getPagos().getMercadoPago().setSecretoWebhook("");
        assertThat(pasarela.transaccionDelAviso(aviso("123", "req-1", "ts=1704908010,v1=" + firma))).isEmpty();
    }

    @Test
    @DisplayName("ignora los avisos que no son de pagos")
    void otroTipoDeAviso() throws Exception {
        String firma = hmac("id:123;request-id:req-1;ts:1704908010;");
        Aviso deOrden = new Aviso(Map.of(),
                Map.of("x-signature", "ts=1704908010,v1=" + firma, "x-request-id", "req-1"),
                Map.of("type", "merchant_order", "data.id", "123"));

        assertThat(pasarela.transaccionDelAviso(deOrden)).isEmpty();
    }

    /** Aviso con la forma que envía Mercado Pago: tipo e id en la URL, firma en las cabeceras. */
    private static Aviso aviso(String idPago, String idPeticion, String firma) {
        Map<String, String> cabeceras = firma == null
                ? Map.of("x-request-id", idPeticion)
                : Map.of("x-request-id", idPeticion, "x-signature", firma);
        return new Aviso(Map.of("type", "payment", "data", Map.of("id", idPago)),
                cabeceras, Map.of("type", "payment", "data.id", idPago));
    }

    /** HMAC-SHA256 calculado aparte, para no comprobar la clase consigo misma. */
    private static String hmac(String plantilla) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRETO_WEBHOOK.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(plantilla.getBytes(StandardCharsets.UTF_8)));
    }
}
