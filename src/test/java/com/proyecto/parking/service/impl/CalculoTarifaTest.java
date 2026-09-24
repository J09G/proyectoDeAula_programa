package com.proyecto.parking.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas de cobro: se factura por horas empezadas con un mínimo de una hora.
 * Es la parte del sistema donde un error cuesta dinero real, así que conviene
 * tenerla clavada con ejemplos concretos.
 */
class CalculoTarifaTest {

    @ParameterizedTest(name = "{0} minutos a ${1}/h -> ${2}")
    @CsvSource({
            // Por debajo de una hora se cobra la hora mínima.
            "0,   3000, 3000.00",
            "1,   3000, 3000.00",
            "59,  3000, 3000.00",
            "60,  3000, 3000.00",
            // A partir de ahí, proporcional a los minutos.
            "90,  3000, 4500.00",
            "120, 3000, 6000.00",
            "150, 2000, 5000.00",
            // Un día completo.
            "1440, 1500, 36000.00"
    })
    @DisplayName("cobra el proporcional con un mínimo de una hora")
    void calculaElValorSegunLosMinutos(long minutos, double tarifaHora, double esperado) {
        assertThat(RegistroParqueoServiceImpl.calcularValor(minutos, tarifaHora))
                .isEqualTo(esperado);
    }

    @Test
    @DisplayName("devuelve siempre un importe con dos decimales exactos")
    void redondeaADosDecimales() {
        // 100 minutos = 1,6667 h (redondeado a 4 decimales). A 3333/h son 5555,1111.
        double valor = RegistroParqueoServiceImpl.calcularValor(100, 3333);

        assertThat(valor).isEqualTo(5555.11);

        // Lo importante: nunca sale un 5555.109999999999 como con aritmética de double.
        assertThat(BigDecimal.valueOf(valor).scale()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("una tarifa de cero no genera cobro")
    void tarifaCero() {
        assertThat(RegistroParqueoServiceImpl.calcularValor(300, 0)).isZero();
    }
}
