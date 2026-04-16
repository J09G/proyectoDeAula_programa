package com.proyecto.parking.service.impl;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.model.Zona;
import com.proyecto.parking.repository.ParqueaderoRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.repository.ZonaRepository;
import com.proyecto.parking.service.ParqueaderoService;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
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

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public Parqueadero registrarParqueadero(String nombre, String direccion, String horario,
                                            double tarifa, int espaciosTotales, int espaciosDisponibles,
                                            String idZona, String registradoPor, String urlMaps, String telefono) {

        Zona zona = zonaRepository.findById(idZona)
                .or(() -> zonaRepository.findAll().stream().filter(z -> idZona.equals(z.getId())).findFirst())
                .orElseThrow(() -> new RuntimeException("Zona no encontrada."));

        Usuario superadmin = usuarioRepository.findById(registradoPor)
                .or(() -> usuarioRepository.findAll().stream().filter(u -> registradoPor.equals(u.getId())).findFirst())
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
                .or(() -> zonaRepository.findAll().stream().filter(z -> idZona.equals(z.getId())).findFirst())
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
        return parqueaderoRepository.findAll().stream()
                .filter(p -> p.getAdministrador() != null && idUsuario.equals(p.getAdministrador().getId()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void eliminarParqueaderoPorAdministrador(String idUsuario) {
        Parqueadero parqueadero = parqueaderoRepository.findByAdministrador_Id(idUsuario);
        if (parqueadero != null) {
            parqueaderoRepository.delete(parqueadero);
        }
    }

    @Override
    public List<Parqueadero> obtenerParqueaderosPorZona(String idZona) {
        Zona zona = zonaRepository.findById(idZona)
                .or(() -> zonaRepository.findAll().stream().filter(z -> idZona.equals(z.getId())).findFirst())
                .orElse(null);
        if (zona == null) return java.util.Collections.emptyList();
        return parqueaderoRepository.findByZona_NombreZonaAndHabilitado(zona.getNombreZona(), true);
    }

    @Override
    public Parqueadero obtenerParqueaderoPorId(String idParqueadero) {
        return parqueaderoRepository.findById(idParqueadero)
                .or(() -> parqueaderoRepository.findAll().stream()
                        .filter(p -> idParqueadero.equals(p.getId()))
                        .findFirst())
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
        java.util.List<Document> orConditions = new java.util.ArrayList<>();
        orConditions.add(new Document("_id", idParqueadero));
        try {
            orConditions.add(new Document("_id", new ObjectId(idParqueadero)));
        } catch (IllegalArgumentException ignored) {}

        Document filter = new Document("$or", orConditions);
        Document updateDoc = new Document("$set", new Document("habilitado", habilitado));
        mongoTemplate.getDb().getCollection("parqueaderos").updateOne(filter, updateDoc);
    }

    @Override
    public Parqueadero guardarParqueadero(Parqueadero parqueadero) {
        java.util.List<Document> orConditions = new java.util.ArrayList<>();
        orConditions.add(new Document("_id", parqueadero.getId()));
        try {
            orConditions.add(new Document("_id", new ObjectId(parqueadero.getId())));
        } catch (IllegalArgumentException ignored) {}

        Document filter = new Document("$or", orConditions);
        Document updateDoc = new Document("$set", new Document("espaciosDisponibles", parqueadero.getEspaciosDisponibles()));
        mongoTemplate.getDb().getCollection("parqueaderos").updateOne(filter, updateDoc);
        return parqueadero;
    }

    @Override
    public List<Parqueadero> buscarPorNombre(String nombre) {
        return parqueaderoRepository.findByNombreContainingIgnoreCase(nombre);
    }

    @Override
    public List<Parqueadero> buscarPorZona(String idZona) {
        Zona zona = zonaRepository.findById(idZona)
                .or(() -> zonaRepository.findAll().stream().filter(z -> idZona.equals(z.getId())).findFirst())
                .orElse(null);
        if (zona == null) return java.util.Collections.emptyList();
        return parqueaderoRepository.findByZona_NombreZona(zona.getNombreZona());
    }

    @Override
    public List<Parqueadero> buscarPorCedulaAdmin(String cedula) {
        return parqueaderoRepository.findByAdministrador_CedulaContainingIgnoreCase(cedula);
    }
}
