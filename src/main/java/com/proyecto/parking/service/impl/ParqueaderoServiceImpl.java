package com.proyecto.parking.service.impl;

import com.proyecto.parking.dto.EditarParqueaderoForm;
import com.proyecto.parking.dto.ParqueaderoForm;
import com.proyecto.parking.exception.AccesoDenegadoException;
import com.proyecto.parking.exception.RecursoNoEncontradoException;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.model.Zona;
import com.proyecto.parking.repository.ParqueaderoRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.repository.ZonaRepository;
import com.proyecto.parking.service.ParqueaderoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ParqueaderoServiceImpl implements ParqueaderoService {

    private static final Logger log = LoggerFactory.getLogger(ParqueaderoServiceImpl.class);

    private final ParqueaderoRepository parqueaderoRepository;
    private final ZonaRepository zonaRepository;
    private final UsuarioRepository usuarioRepository;

    public ParqueaderoServiceImpl(ParqueaderoRepository parqueaderoRepository,
                                  ZonaRepository zonaRepository,
                                  UsuarioRepository usuarioRepository) {
        this.parqueaderoRepository = parqueaderoRepository;
        this.zonaRepository = zonaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public Parqueadero registrarParqueadero(ParqueaderoForm form, String idAdministrador) {
        Zona zona = buscarZona(form.getIdZona());

        Usuario administrador = usuarioRepository.findById(idAdministrador)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Administrador", idAdministrador));

        Parqueadero parqueadero = new Parqueadero();
        parqueadero.setNombre(form.getNombre().trim());
        parqueadero.setDireccion(form.getDireccion().trim());
        parqueadero.setHorario(form.getHorario().trim());
        parqueadero.setTarifaHora(form.getTarifa());
        parqueadero.setEspaciosTotales(form.getEspaciosTotales());
        parqueadero.setEspaciosDisponibles(form.getEspaciosDisponibles());
        parqueadero.setZona(zona);
        parqueadero.setTelefono(form.getTelefono());
        parqueadero.setUrlMaps(form.getUrlMaps());
        // El administrador y el estado se fijan aquí mismo: antes eran tres
        // escrituras seguidas y un fallo intermedio dejaba un parqueadero
        // huérfano y deshabilitado.
        parqueadero.setAdministrador(administrador);
        parqueadero.setHabilitado(true);

        Parqueadero guardado = parqueaderoRepository.save(parqueadero);
        log.info("Parqueadero {} creado por el administrador {}.", guardado.getId(), idAdministrador);
        return guardado;
    }

    @Override
    public Parqueadero obtenerParqueaderoDeAdministrador(String idParqueadero, String idAdministrador) {
        Parqueadero parqueadero = obtenerParqueaderoPorId(idParqueadero);

        if (!parqueadero.perteneceA(idAdministrador)) {
            log.warn("El administrador {} intentó operar sobre el parqueadero {}, que no le pertenece.",
                    idAdministrador, idParqueadero);
            throw new AccesoDenegadoException("Este parqueadero no te pertenece.");
        }
        return parqueadero;
    }

    @Override
    public Parqueadero obtenerParqueaderoPorId(String idParqueadero) {
        return parqueaderoRepository.findById(idParqueadero)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Parqueadero", idParqueadero));
    }

    @Override
    public List<Parqueadero> obtenerParqueaderosPorAdministrador(String idAdministrador) {
        return parqueaderoRepository.findByAdministrador_Id(idAdministrador);
    }

    @Override
    public List<Parqueadero> obtenerParqueaderosPorZona(String idZona) {
        return parqueaderoRepository.findByZona_IdAndHabilitado(idZona, true);
    }

    @Override
    public Page<Parqueadero> listarParqueaderos(Pageable pageable) {
        return parqueaderoRepository.findAll(pageable);
    }

    @Override
    public Page<Parqueadero> buscarPorNombre(String nombre, Pageable pageable) {
        return parqueaderoRepository.findByNombreContainingIgnoreCase(nombre.trim(), pageable);
    }

    @Override
    public Page<Parqueadero> buscarPorZona(String idZona, Pageable pageable) {
        return parqueaderoRepository.findByZona_Id(idZona, pageable);
    }

    @Override
    public Page<Parqueadero> buscarPorCedulaAdmin(String cedula, Pageable pageable) {
        List<String> idsAdmins = usuarioRepository.findByCedulaContaining(cedula.trim())
                .stream()
                .map(Usuario::getId)
                .toList();

        if (idsAdmins.isEmpty()) {
            return Page.empty(pageable);
        }
        return parqueaderoRepository.findByAdministrador_IdIn(idsAdmins, pageable);
    }

    @Override
    public void actualizarDatos(String idParqueadero, String idAdministrador, EditarParqueaderoForm form) {
        Parqueadero parqueadero = obtenerParqueaderoDeAdministrador(idParqueadero, idAdministrador);

        parqueadero.setNombre(form.getNombre().trim());
        parqueadero.setDireccion(form.getDireccion().trim());
        parqueadero.setHorario(form.getHorario().trim());
        parqueadero.setTarifaHora(form.getTarifa());
        parqueadero.setUrlMaps(form.getUrlMaps());

        parqueaderoRepository.save(parqueadero);
        log.info("Datos del parqueadero {} actualizados.", idParqueadero);
    }

    @Override
    public void actualizarCapacidad(String idParqueadero, String idAdministrador,
                                    int espaciosTotales, int espaciosDisponibles) {
        Parqueadero parqueadero = obtenerParqueaderoDeAdministrador(idParqueadero, idAdministrador);

        if (espaciosDisponibles > espaciosTotales) {
            throw new ReglaNegocioException("Los espacios disponibles no pueden superar los totales.");
        }

        int ocupados = parqueadero.getEspaciosTotales() - parqueadero.getEspaciosDisponibles();
        if (espaciosTotales < ocupados) {
            throw new ReglaNegocioException(
                    "No puedes reducir el total a " + espaciosTotales
                    + ": ahora mismo hay " + ocupados + " espacios ocupados.");
        }

        parqueadero.setEspaciosTotales(espaciosTotales);
        parqueadero.setEspaciosDisponibles(espaciosDisponibles);
        parqueaderoRepository.save(parqueadero);

        log.info("Capacidad del parqueadero {} fijada en {}/{}.",
                idParqueadero, espaciosDisponibles, espaciosTotales);
    }

    @Override
    public void cambiarEstado(String idParqueadero, String idAdministrador, boolean habilitado) {
        Parqueadero parqueadero = obtenerParqueaderoDeAdministrador(idParqueadero, idAdministrador);
        guardarEstado(parqueadero, habilitado);
    }

    @Override
    public void cambiarEstadoComoSuperadmin(String idParqueadero, boolean habilitado) {
        guardarEstado(obtenerParqueaderoPorId(idParqueadero), habilitado);
    }

    @Override
    public void ocuparEspacio(String idParqueadero) {
        // $inc condicionado en una sola operación: si dos entradas coinciden en
        // el tiempo, sólo una de ellas encuentra el documento con cupo.
        long modificados = parqueaderoRepository.reservarEspaciosSiHayDisponibles(idParqueadero, 1, -1);

        if (modificados == 0) {
            throw new ReglaNegocioException("No hay espacios disponibles en este parqueadero.");
        }
    }

    @Override
    public void liberarEspacio(String idParqueadero) {
        parqueaderoRepository.ajustarEspaciosDisponibles(idParqueadero, 1);
    }

    private void guardarEstado(Parqueadero parqueadero, boolean habilitado) {
        parqueadero.setHabilitado(habilitado);
        parqueaderoRepository.save(parqueadero);
        log.info("Parqueadero {} {}.", parqueadero.getId(), habilitado ? "habilitado" : "deshabilitado");
    }

    private Zona buscarZona(String idZona) {
        return zonaRepository.findById(idZona)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Zona", idZona));
    }
}
