package com.proyecto.parking.scheduler;

import com.proyecto.parking.service.ReservaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Libera los cubículos de las reservas aceptadas a las que el cliente nunca
 * llegó.
 *
 * <p>Sin este job, cada cliente que no se presentaba dejaba un cubículo
 * bloqueado de forma permanente: el parqueadero acababa reportando cero plazas
 * disponibles con el aparcamiento vacío.</p>
 *
 * <p>Con varias instancias de la aplicación, todas ejecutarían el job a la vez.
 * Las operaciones son idempotentes (una reserva ya expirada deja de aparecer en
 * la consulta), pero para un despliegue con réplicas conviene añadir un bloqueo
 * distribuido tipo ShedLock.</p>
 */
@Component
public class ExpiracionReservasJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiracionReservasJob.class);

    private final ReservaService reservaService;

    public ExpiracionReservasJob(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    @Scheduled(
            fixedRateString = "${parking.reservas.intervalo-expiracion-ms}",
            initialDelayString = "${parking.reservas.intervalo-expiracion-ms}")
    public void expirarReservas() {
        try {
            int expiradas = reservaService.expirarReservasVencidas();
            if (expiradas > 0) {
                log.info("Job de expiración: {} reservas liberadas.", expiradas);
            }
        } catch (RuntimeException e) {
            // Un fallo puntual (por ejemplo, la base de datos caída) no debe
            // matar el scheduler: se reintenta en la siguiente ejecución.
            log.error("El job de expiración de reservas falló: {}", e.getMessage(), e);
        }
    }
}
