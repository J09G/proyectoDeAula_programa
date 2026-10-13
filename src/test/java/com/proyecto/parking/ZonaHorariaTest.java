package com.proyecto.parking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Zona horaria de la aplicación")
class ZonaHorariaTest {

    @Test
    @DisplayName("sin configurar se trabaja con la hora de Colombia, no con la del servidor")
    void porDefectoColombia() {
        assertThat(ParkingApplication.zonaHoraria(null)).isEqualTo("America/Bogota");
        assertThat(ParkingApplication.zonaHoraria("  ")).isEqualTo("America/Bogota");
    }

    @Test
    @DisplayName("se puede cambiar con ZONA_HORARIA")
    void configurable() {
        assertThat(ParkingApplication.zonaHoraria(" America/Lima ")).isEqualTo("America/Lima");
    }
}
