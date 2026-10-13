package com.proyecto.parking;

import com.proyecto.parking.config.ParkingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(ParkingProperties.class)
public class ParkingApplication {

	/** Hora con la que trabaja la aplicación si no se indica otra en ZONA_HORARIA. */
	static final String ZONA_HORARIA_POR_DEFECTO = "America/Bogota";

	public static void main(String[] args) {
		// Los servidores en la nube trabajan en UTC, cinco horas por delante de
		// Colombia: sin esto, a las 7 p. m. una reserva para las 8 p. m. se
		// rechazaba por "ya pasada" y las fechas se mostraban corridas.
		TimeZone.setDefault(TimeZone.getTimeZone(zonaHoraria(System.getenv("ZONA_HORARIA"))));
		SpringApplication.run(ParkingApplication.class, args);
	}

	static String zonaHoraria(String configurada) {
		return configurada == null || configurada.isBlank() ? ZONA_HORARIA_POR_DEFECTO : configurada.trim();
	}
}
