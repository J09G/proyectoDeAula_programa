package com.proyecto.parking.service.impl;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.RegistroParqueo;
import com.proyecto.parking.model.RegistroParqueo.EstadoRegistro;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.ParqueaderoRepository;
import com.proyecto.parking.repository.RegistroParqueoRepository;
import com.proyecto.parking.repository.ReservaRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.RegistroParqueoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class RegistroParqueoServiceImpl implements RegistroParqueoService {

    @Autowired
    private RegistroParqueoRepository registroRepository;

    @Autowired
    private ParqueaderoRepository parqueaderoRepository;

    @Autowired
    private ParqueaderoService parqueaderoService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    @Override
    public RegistroParqueo registrarEntrada(String placa, String cedula, String idParqueadero) {
        Parqueadero parqueadero = parqueaderoRepository.findById(idParqueadero)
                .orElseThrow(() -> new RuntimeException("Parqueadero no encontrado."));

        registroRepository.findByPlacaAndEstado(placa.toUpperCase(), EstadoRegistro.ACTIVO)
                .ifPresent(r -> { throw new RuntimeException("Ya existe un registro activo para la placa " + placa); });

        RegistroParqueo registro = new RegistroParqueo();
        registro.setPlaca(placa.toUpperCase());
        registro.setParqueadero(parqueadero);
        registro.setHoraEntrada(LocalDateTime.now());
        registro.setEstado(EstadoRegistro.ACTIVO);

        boolean tieneReserva = false;
        Usuario usuario = null;

        if (cedula != null && !cedula.trim().isEmpty()) {
            usuario = usuarioRepository.findByCedula(cedula.trim());
            if (usuario == null) {
                throw new RuntimeException("No se encontró ningún usuario con la cédula: " + cedula);
            }
        } else {
            // Sin cédula: buscar usuario por placa para detectar reserva
            usuario = usuarioRepository.findByPlaca(placa.toUpperCase());
        }

        if (usuario != null) {
            registro.setUsuario(usuario);
            List<Reserva> reservasAceptadas = reservaRepository
                    .findByCliente_IdAndParqueadero_IdAndEstado(usuario.getId(), parqueadero.getId(), EstadoReserva.ACEPTADA);
            if (!reservasAceptadas.isEmpty()) {
                Reserva reserva = reservasAceptadas.get(0);
                reserva.setEstado(Reserva.EstadoReserva.UTILIZADA);
                reservaRepository.save(reserva);
                registro.setReserva(reserva);
                tieneReserva = true;
            }
        }

        if (!tieneReserva) {
            if (parqueadero.getEspaciosDisponibles() <= 0) {
                throw new RuntimeException("No hay espacios disponibles en el parqueadero.");
            }
            parqueadero.setEspaciosDisponibles(parqueadero.getEspaciosDisponibles() - 1);
            parqueaderoService.guardarParqueadero(parqueadero);
        }

        return registroRepository.save(registro);
    }

    @Override
    public RegistroParqueo registrarSalida(String idRegistro) {
        RegistroParqueo registro = registroRepository.findById(idRegistro)
                .orElseThrow(() -> new RuntimeException("Registro no encontrado."));

        if (registro.getEstado() == EstadoRegistro.FINALIZADO) {
            throw new RuntimeException("Este registro ya fue finalizado.");
        }

        LocalDateTime ahora = LocalDateTime.now();
        long minutos = ChronoUnit.MINUTES.between(registro.getHoraEntrada(), ahora);

        double horas = Math.max(minutos / 60.0, 1.0);
        double valor = horas * registro.getParqueadero().getTarifaHora();

        registro.setHoraSalida(ahora);
        registro.setTiempoMinutos(minutos);
        registro.setValorPagado(Math.round(valor * 100.0) / 100.0);
        registro.setEstado(EstadoRegistro.FINALIZADO);

        Parqueadero parqueadero = parqueaderoRepository.findAll().stream()
                .filter(p -> registro.getParqueadero().getId().equals(p.getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Parqueadero no encontrado."));
        parqueadero.setEspaciosDisponibles(parqueadero.getEspaciosDisponibles() + 1);
        parqueaderoService.guardarParqueadero(parqueadero);

        return registroRepository.save(registro);
    }

    @Override
    public List<RegistroParqueo> listarActivosPorParqueadero(String idParqueadero) {
        return registroRepository.findByParqueadero_IdAndEstado(idParqueadero, EstadoRegistro.ACTIVO);
    }

    @Override
    public List<RegistroParqueo> listarTodosPorParqueadero(String idParqueadero) {
        return registroRepository.findByParqueadero_Id(idParqueadero);
    }

    @Override
    public RegistroParqueo obtenerPorId(String idRegistro) {
        return registroRepository.findById(idRegistro)
                .orElseThrow(() -> new RuntimeException("Registro no encontrado."));
    }
}
