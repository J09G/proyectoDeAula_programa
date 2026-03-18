package com.proyecto.parking.service.impl;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.model.Zona;
import com.proyecto.parking.repository.ParqueaderoRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.repository.ZonaRepository;
import com.proyecto.parking.service.ParqueaderoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ParqueaderoServiceImpl implements ParqueaderoService {

    @Autowired
    private ParqueaderoRepository parqueaderoRepository;

    @Autowired
    private ZonaRepository zonaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public Parqueadero registrarParqueadero(String nombre, String direccion, String horario,
                                            double tarifa, int espaciosTotales, int espaciosDisponibles,
                                            String idZona, String registradoPor, String urlMaps, String telefono) {

        Zona zona = zonaRepository.findById(idZona)
                .orElseThrow(() -> new RuntimeException("Zona no encontrada."));

        Usuario superadmin = usuarioRepository.findById(registradoPor)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        Parqueadero parqueadero = new Parqueadero();
        parqueadero.setNombre(nombre);
        parqueadero.setDireccion(direccion);
        parqueadero.setHorario(horario);
        parqueadero.setTarifaHora(tarifa);
        parqueadero.setEspaciosTotales(espaciosTotales);
        parqueadero.setEspaciosDisponibles(espaciosDisponibles);
        parqueadero.setZona(zona);
        parqueadero.setRegistradoPor(superadmin);
        parqueadero.setAdministrador(null);
        parqueadero.setTelefono((telefono != null && !telefono.trim().isEmpty()) ? telefono.trim() : null);
        parqueadero.setUrlMaps((urlMaps != null && !urlMaps.trim().isEmpty()) ? urlMaps.trim() : null);

        return parqueaderoRepository.save(parqueadero);
    }

    @Override
    public Parqueadero actualizarParqueadero(String idParqueadero, String nombre, String direccion,
                                             String horario, double tarifa, int espaciosTotales,
                                             int espaciosDisponibles, String idZona, String urlMaps) {

        Parqueadero parqueadero = obtenerParqueaderoPorId(idParqueadero);
        Zona zona = zonaRepository.findById(idZona)
                .orElseThrow(() -> new RuntimeException("Zona no encontrada."));

        parqueadero.setNombre(nombre);
        parqueadero.setDireccion(direccion);
        parqueadero.setHorario(horario);
        parqueadero.setTarifaHora(tarifa);
        parqueadero.setEspaciosTotales(espaciosTotales);
        parqueadero.setEspaciosDisponibles(espaciosDisponibles);
        parqueadero.setZona(zona);
        parqueadero.setUrlMaps((urlMaps != null && !urlMaps.trim().isEmpty()) ? urlMaps.trim() : null);

        return parqueaderoRepository.save(parqueadero);
    }

    @Override
    public Parqueadero obtenerParqueaderoPorAdministrador(String idUsuario) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));
        return parqueaderoRepository.findByAdministrador(usuario);
    }

    @Override
    public void eliminarParqueaderoPorAdministrador(String idUsuario) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        Parqueadero parqueadero = parqueaderoRepository.findByAdministrador(usuario);
        if (parqueadero != null) {
            parqueaderoRepository.delete(parqueadero);
        }
    }

    @Override
    public List<Parqueadero> obtenerParqueaderosPorZona(String idZona) {
        return parqueaderoRepository.findByZona_Id(idZona);
    }

    @Override
    public Parqueadero obtenerParqueaderoPorId(String idParqueadero) {
        return parqueaderoRepository.findById(idParqueadero)
                .orElseThrow(() -> new RuntimeException("Parqueadero no encontrado."));
    }

    @Override
    public List<Parqueadero> listarParqueaderos() {
        return parqueaderoRepository.findAll();
    }

    @Override
    public void asignarAdministrador(String idParqueadero, String idAdministrador) {
        Parqueadero parqueadero = obtenerParqueaderoPorId(idParqueadero);
        Usuario administrador = usuarioRepository.findById(idAdministrador)
                .orElseThrow(() -> new RuntimeException("Administrador no encontrado."));

        parqueadero.setAdministrador(administrador);
        parqueaderoRepository.save(parqueadero);
    }

    @Override
    public void cambiarEstado(String idParqueadero, boolean habilitado) {
        Parqueadero parqueadero = parqueaderoRepository.findById(idParqueadero)
                .orElseThrow(() -> new RuntimeException("Parqueadero no encontrado"));
        parqueadero.setHabilitado(habilitado);
        parqueaderoRepository.save(parqueadero);
    }

    @Override
    public Parqueadero guardarParqueadero(Parqueadero parqueadero) {
        return parqueaderoRepository.save(parqueadero);
    }

    @Override
    public List<Parqueadero> buscarPorNombre(String nombre) {
        return parqueaderoRepository.findByNombreContainingIgnoreCase(nombre);
    }

    @Override
    public List<Parqueadero> buscarPorZona(String idZona) {
        return parqueaderoRepository.findByZona_Id(idZona);
    }

    @Override
    public List<Parqueadero> buscarPorCedulaAdmin(String cedula) {
        return parqueaderoRepository.findByAdministrador_CedulaContainingIgnoreCase(cedula);
    }
}
