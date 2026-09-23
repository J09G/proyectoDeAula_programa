package com.proyecto.parking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

// Misma configuracion que AuthControllerIT a proposito: asi Spring reutiliza el mismo
// ApplicationContext (y el mismo Mongo embebido) en vez de levantar dos instancias a la vez.
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.MOCK,
		properties = {
				"spring.data.mongodb.uri=mongodb://localhost:27017/parking-test-no-se-usa",
				"spring.data.mongodb.database=parking-test",
				"brevo.api.key=test-key-no-se-usa",
				"de.flapdoodle.mongodb.embedded.version=7.0.5"
		})
@AutoConfigureMockMvc
class ParkingApplicationTests {

	@Test
	void contextLoads() {
	}

}
