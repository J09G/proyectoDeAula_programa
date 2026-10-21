package com.proyecto.parking.service.impl;

import com.proyecto.parking.config.ParkingProperties;
import com.proyecto.parking.model.Pago;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pasarela Wompi")
class WompiPasarelaTest {

    private static final String SECRETO_EVENTOS = "test_events_secreto";

    private ParkingProperties properties;
    private WompiPasarela pasarela;

    @BeforeEach
    void preparar() {
        properties = new ParkingProperties();
        properties.getPagos().getWompi().setLlavePublica("pub_test_abc");
        properties.getPagos().getWompi().setSecretoIntegridad("prod_integrity_Z5mMke9x0k8gpErbDqwrJXMqsI6SFli6");
        properties.getPagos().getWompi().setSecretoEventos(SECRETO_EVENTOS);
        pasarela = new WompiPasarela(new RestTemplateBuilder(), properties);
    }

    @Test
    @DisplayName("la firma de integridad coincide con el ejemplo de la documentación de Wompi")
    void firmaDeIntegridad() {
        assertThat(pasarela.firmaDeIntegridad("sk8-438k4-xmxm392-sn2m", 2_490_000, "COP"))
                .isEqualTo("37c8407747e595535433ef8f6a811d853cd943046624a0ec04662b17bbf33bf5");
    }

    @Test
    @DisplayName("la URL de pago lleva la llave pública, el importe, la referencia, la firma y el retorno")
    void urlDePago() {
        Pago pago = new Pago();
        pago.setReferencia("PKG-ABC123-00FF");
        pago.setMontoCentavos(500_000);

        String url = pasarela.urlDePago(pago, "http://localhost:8081/pagos/wompi/retorno");

        assertThat(url)
                .startsWith("https://checkout.wompi.co/p/?")
                .contains("public-key=pub_test_abc")
                .contains("currency=COP")
                .contains("amount-in-cents=500000")
                .contains("reference=PKG-ABC123-00FF")
                .contains("signature:integrity=" + pasarela.firmaDeIntegridad("PKG-ABC123-00FF", 500_000, "COP"))
                .contains("redirect-url=http://localhost:8081/pagos/wompi/retorno");
        // El secreto firma la URL pero nunca viaja en ella.
        assertThat(url).doesNotContain("prod_integrity");
    }

    @Test
    @DisplayName("acepta un aviso firmado por Wompi")
    void avisoAutentico() {
        assertThat(pasarela.avisoEsAutentico(
                aviso("177a142e25beca66d59f857f08b24ca96fb01b5cb39911a05940ea0cb8144a5d", "APPROVED"))).isTrue();
    }

    @Test
    @DisplayName("rechaza un aviso con la firma equivocada o con los datos cambiados")
    void avisoFalso() {
        assertThat(pasarela.avisoEsAutentico(aviso("0000", "APPROVED"))).isFalse();
        // Misma firma, pero alguien cambió DECLINED por APPROVED... o al revés.
        assertThat(pasarela.avisoEsAutentico(
                aviso("177a142e25beca66d59f857f08b24ca96fb01b5cb39911a05940ea0cb8144a5d", "DECLINED"))).isFalse();
        assertThat(pasarela.avisoEsAutentico(Map.of("event", "transaction.updated"))).isFalse();
        assertThat(pasarela.avisoEsAutentico(null)).isFalse();
    }

    @Test
    @DisplayName("sin secreto de eventos configurado no se puede dar por bueno ningún aviso")
    void sinSecretoNoHayAvisosValidos() {
        properties.getPagos().getWompi().setSecretoEventos("");
        assertThat(pasarela.avisoEsAutentico(
                aviso("177a142e25beca66d59f857f08b24ca96fb01b5cb39911a05940ea0cb8144a5d", "APPROVED"))).isFalse();
    }

    @Test
    @DisplayName("sin llave privada, las devoluciones y la búsqueda por referencia quedan para hacerse a mano")
    void sinLlavePrivada() {
        assertThat(pasarela.devolver("txn-1")).isFalse();
        assertThat(pasarela.consultarPorReferencia("PKG-1")).isEmpty();
    }

    @Test
    @DisplayName("no consulta ids de transacción con caracteres que podrían alterar la URL")
    void idDeTransaccionSospechoso() {
        assertThat(pasarela.consultarTransaccion("../../otra-ruta")).isEmpty();
        assertThat(pasarela.consultarTransaccion(null)).isEmpty();
    }

    /** Aviso con la forma que envía Wompi: indica qué campos firmó, más la marca de tiempo. */
    private static Map<String, Object> aviso(String checksum, String estado) {
        Map<String, Object> transaccion = new HashMap<>();
        transaccion.put("id", "1234-1610641025-49201");
        transaccion.put("status", estado);
        transaccion.put("amount_in_cents", 4_490_000);

        Map<String, Object> aviso = new HashMap<>();
        aviso.put("event", "transaction.updated");
        aviso.put("data", Map.of("transaction", transaccion));
        aviso.put("timestamp", 1_530_291_411);
        aviso.put("signature", Map.of(
                "properties", List.of("transaction.id", "transaction.status", "transaction.amount_in_cents"),
                "checksum", checksum));
        return aviso;
    }
}
