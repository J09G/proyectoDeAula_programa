package com.proyecto.parking.service.impl;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.RegistroParqueo;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import com.proyecto.parking.prediccion.CalendarioColombia;
import com.proyecto.parking.prediccion.ModeloOcupacion;
import com.proyecto.parking.prediccion.Prediccion;
import com.proyecto.parking.repository.RegistroParqueoRepository;
import com.proyecto.parking.repository.ReservaRepository;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.PrediccionService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Arma, con los datos de la aplicación, las mismas variables con las que se
 * entrenó el modelo y le pide la ocupación estimada.
 */
@Service
public class PrediccionServiceImpl implements PrediccionService {

    private final ModeloOcupacion modelo;
    private final ParqueaderoService parqueaderoService;
    private final ReservaRepository reservaRepository;
    private final RegistroParqueoRepository registroParqueoRepository;

    public PrediccionServiceImpl(ModeloOcupacion modelo,
                                 ParqueaderoService parqueaderoService,
                                 ReservaRepository reservaRepository,
                                 RegistroParqueoRepository registroParqueoRepository) {
        this.modelo = modelo;
        this.parqueaderoService = parqueaderoService;
        this.reservaRepository = reservaRepository;
        this.registroParqueoRepository = registroParqueoRepository;
    }

    @Override
    public List<Prediccion> predecirDia(String idParqueadero, LocalDate dia) {
        Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorId(idParqueadero);
        int capacidad = Math.max(1, parqueadero.getEspaciosTotales() == null ? 1 : parqueadero.getEspaciosTotales());
        double tarifa = parqueadero.getTarifaHora() == null ? 0 : parqueadero.getTarifaHora();
        int zona = modelo.indiceDeZona(parqueadero.getZona() == null ? null : parqueadero.getZona().getNombreZona());

        boolean finDeSemana = CalendarioColombia.esFinDeSemana(dia);
        boolean festivo = CalendarioColombia.esFestivo(dia);
        boolean temporadaAlta = CalendarioColombia.esTemporadaAlta(dia);

        List<Reserva> aceptadas = reservaRepository.findByParqueadero_IdAndEstado(idParqueadero, EstadoReserva.ACEPTADA)
                .stream()
                .filter(r -> r.getFechaReserva() != null && r.getFechaReserva().toLocalDate().equals(dia))
                .toList();

        // Vehículos que entraron el mismo día de la semana anterior.
        LocalDate hace7 = dia.minusDays(7);
        List<RegistroParqueo> semanaAnterior = registroParqueoRepository
                .findByParqueadero_IdAndHoraEntradaBetween(idParqueadero, hace7.atStartOfDay(), hace7.plusDays(1).atStartOfDay());
        boolean conHistorial = !semanaAnterior.isEmpty();

        List<Prediccion> predicciones = new ArrayList<>();
        for (int hora = modelo.horaInicial(); hora <= modelo.horaFinal(); hora++) {
            int h = hora;
            long reservas = aceptadas.stream().filter(r -> r.getFechaReserva().getHour() == h).count();

            double[] x = new double[modelo.numeroDeVariables()];
            x[ModeloOcupacion.HORA] = hora;
            x[ModeloOcupacion.DIA_SEMANA] = dia.getDayOfWeek().getValue() - 1;   // 0 = lunes
            x[ModeloOcupacion.FIN_DE_SEMANA] = finDeSemana ? 1 : 0;
            x[ModeloOcupacion.FESTIVO] = festivo ? 1 : 0;
            x[ModeloOcupacion.TEMPORADA_ALTA] = temporadaAlta ? 1 : 0;
            x[ModeloOcupacion.CAPACIDAD] = capacidad;
            x[ModeloOcupacion.TARIFA] = tarifa;
            x[ModeloOcupacion.RESERVAS_PCT] = 100.0 * reservas / capacidad;
            x[ModeloOcupacion.SEMANA_ANTERIOR] = conHistorial
                    ? ocupacionEn(semanaAnterior, hace7.atTime(hora, 0), capacidad)
                    : modelo.ocupacionTipica(zona, finDeSemana || festivo, hora);
            x[ModeloOcupacion.PRIMERA_ZONA + zona] = 1;

            double porcentaje = modelo.predecir(x);
            predicciones.add(new Prediccion(dia.atTime(hora, 0), hora, (int) Math.round(porcentaje),
                    Prediccion.Nivel.de(porcentaje), conHistorial));
        }
        return predicciones;
    }

    @Override
    public Prediccion predecir(String idParqueadero, LocalDateTime momento) {
        int hora = Math.max(modelo.horaInicial(), Math.min(modelo.horaFinal(), momento.getHour()));
        return predecirDia(idParqueadero, momento.toLocalDate()).get(hora - modelo.horaInicial());
    }

    /** Porcentaje de cubículos que tenían un vehículo dentro durante la franja de una hora. */
    private static double ocupacionEn(List<RegistroParqueo> registros, LocalDateTime inicio, int capacidad) {
        LocalDateTime fin = inicio.plusHours(1);
        long dentro = registros.stream()
                .filter(r -> r.getHoraEntrada().isBefore(fin))
                .filter(r -> r.getHoraSalida() == null || r.getHoraSalida().isAfter(inicio))
                .count();
        return Math.min(100.0, 100.0 * dentro / capacidad);
    }
}
