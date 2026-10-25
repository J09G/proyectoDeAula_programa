package com.proyecto.parking.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Saldo a pagar al salir, con anticipo")
class SaldoConAnticipoTest {

    private static RegistroParqueo registro(Double valor, Double anticipo) {
        RegistroParqueo registro = new RegistroParqueo();
        registro.setValorPagado(valor);
        registro.setAnticipo(anticipo);
        return registro;
    }

    @Test
    @DisplayName("sin anticipo se paga el valor completo del servicio")
    void sinAnticipo() {
        assertThat(registro(12_000d, null).getSaldoAPagar()).isEqualTo(12_000d);
        assertThat(registro(12_000d, null).anticipoAplicado()).isZero();
    }

    @Test
    @DisplayName("el anticipo se descuenta del valor del servicio")
    void descuentaElAnticipo() {
        assertThat(registro(12_000d, 5_000d).getSaldoAPagar()).isEqualTo(7_000d);
    }

    @Test
    @DisplayName("una estancia de una hora queda saldada con el anticipo")
    void unaHoraQuedaSaldada() {
        assertThat(registro(5_000d, 5_000d).getSaldoAPagar()).isZero();
    }

    @Test
    @DisplayName("el saldo nunca es negativo, aunque el anticipo supere al servicio")
    void nuncaNegativo() {
        assertThat(registro(4_000d, 5_000d).getSaldoAPagar()).isZero();
    }
}
