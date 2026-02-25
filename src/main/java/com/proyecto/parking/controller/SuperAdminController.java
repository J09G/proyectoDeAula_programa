package com.proyecto.parking.controller;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.UsuarioService;
import com.proyecto.parking.service.ZonaService;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/superadmin")
public class SuperAdminController {

    @Autowired
    private ParqueaderoService parqueaderoService;

    @Autowired
    private ZonaService zonaService;

    @Autowired
    private UsuarioService usuarioService;


    @GetMapping("")
    public String mostrarPanelSuperadmin(Model model, HttpSession session) {
        Usuario superadmin = (Usuario) session.getAttribute("usuario");

        if (superadmin == null || !superadmin.getRol().getNombre().equalsIgnoreCase("SuperAdmin")) {
            model.addAttribute("error", "No tienes permiso para acceder a esta página.");
            return "redirect:/login";
        }

        try {
            model.addAttribute("zonas", zonaService.obtenerZonas());
            model.addAttribute("parqueaderos", parqueaderoService.listarParqueaderos());
        } catch (Exception e) {
            model.addAttribute("error", "Error al cargar el panel del SuperAdmin: " + e.getMessage());
        }

        return "superadmin/index";
    }

    @PostMapping("/registrarParqueadero")
    public String registrarParqueadero(@RequestParam String nombre,
                                    @RequestParam String direccion,
                                    @RequestParam String horario,
                                    @RequestParam double tarifa,
                                    @RequestParam("espacios_totales") int espaciosTotales,
                                    @RequestParam("espacios_disponibles") int espaciosDisponibles,
                                    @RequestParam("id_zona") int idZona,
                                    @RequestParam("url_maps") String urlMaps,
                                    @RequestParam String telefono,  
                                    Model model) {
        try {
            int registradoPor = 1;
            parqueaderoService.registrarParqueadero(nombre, direccion, horario, tarifa,
                    espaciosTotales, espaciosDisponibles, idZona, registradoPor, urlMaps, telefono);
            model.addAttribute("mensaje", "Parqueadero registrado correctamente.");
        } catch (Exception e) {
            model.addAttribute("error", "Error al registrar el parqueadero: " + e.getMessage());
        }

        model.addAttribute("zonas", zonaService.obtenerZonas());
        model.addAttribute("parqueaderos", parqueaderoService.listarParqueaderos());
        return "superadmin/index";
    }

    @PostMapping("/asignarAdmin")
    public String asignarAdministrador(@RequestParam String correo,
                                    @RequestParam String cedula,
                                    @RequestParam String contrasena,
                                    @RequestParam("id_parqueadero") int idParqueadero,
                                    Model model) {
        try {
            Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorId(idParqueadero);

            if (parqueadero.getAdministrador() != null) {
                model.addAttribute("error", "Este parqueadero ya tiene un administrador asignado.");
            } else {
                String nombreAdmin = "Administrador " + idParqueadero;

                usuarioService.registrarUsuario(nombreAdmin, cedula, correo, contrasena, 2);
                Usuario nuevoAdmin = usuarioService.obtenerUsuarioPorCorreo(correo);

                parqueaderoService.asignarAdministrador(idParqueadero, nuevoAdmin.getIdUsuario());
                model.addAttribute("mensaje", "Administrador asignado correctamente.");
            }
        } catch (Exception e) {
            model.addAttribute("error", "Error al asignar administrador: " + e.getMessage());
        }

        model.addAttribute("zonas", zonaService.obtenerZonas());
        model.addAttribute("parqueaderos", parqueaderoService.listarParqueaderos());
        return "superadmin/index";
    }

    @GetMapping("/usuarios")
    public String listarUsuarios(Model model) {
        model.addAttribute("usuarios", usuarioService.obtenerTodosLosUsuarios());
        return "superadmin/usuarios";
    }

    @GetMapping("/editar/{id}")
    public String mostrarFormularioEdicion(@PathVariable int id, Model model) {
        Usuario usuario = usuarioService.obtenerUsuarioPorId(id);
        model.addAttribute("usuario", usuario);

        model.addAttribute("roles", List.of(
                new Object[]{"SuperAdmin", 1},
                new Object[]{"Administrador", 2},
                new Object[]{"Cliente", 3}
        ));

        return "superadmin/editar_usuario";
    }

    @PostMapping("/actualizar")
    public String actualizarUsuario(@RequestParam int idUsuario,
                                    @RequestParam String nombre,
                                    @RequestParam String correo,
                                    @RequestParam String cedula,
                                    RedirectAttributes redirectAttributes) {
        try {
            usuarioService.actualizarUsuario(idUsuario, nombre, correo, cedula);
            redirectAttributes.addFlashAttribute("mensaje", "Usuario actualizado correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al actualizar el usuario: " + e.getMessage());
        }
        return "redirect:/superadmin/usuarios";
    }

    @GetMapping("/eliminar/{id}")
    public String eliminarUsuario(@PathVariable int id, RedirectAttributes redirectAttributes) {
        try {
            Usuario usuario = usuarioService.obtenerUsuarioPorId(id);

            if (usuario.getRol().getNombre().equalsIgnoreCase("SuperAdmin")) {
                redirectAttributes.addFlashAttribute("error", " No se puede eliminar al SuperAdministrador.");
                return "redirect:/superadmin/usuarios";
            }

            parqueaderoService.eliminarParqueaderoPorAdministrador(id);
            usuarioService.eliminarUsuarioPorId(id);

            redirectAttributes.addFlashAttribute("mensaje", " Usuario y su parqueadero eliminados correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al eliminar usuario o parqueadero: " + e.getMessage());
            e.printStackTrace();
        }

        return "redirect:/superadmin/usuarios";
    }

    @GetMapping("/parqueaderos")
    public String verParqueaderos(Model model) {
        try {
            List<Parqueadero> parqueaderos = parqueaderoService.listarParqueaderos();
            model.addAttribute("parqueaderos", parqueaderos);
            model.addAttribute("zonas", zonaService.obtenerZonas()); 
            model.addAttribute("mensaje", parqueaderos.isEmpty() ? "No hay parqueaderos registrados." : null);
        } catch (Exception e) {
            model.addAttribute("error", "Error al cargar los parqueaderos: " + e.getMessage());
        }
        return "superadmin/parqueaderos";
    }

    @GetMapping("/parqueaderos/buscar")
    public String buscarParqueaderos(@RequestParam(required = false) String nombre,
                                    @RequestParam(required = false) Integer idZona,
                                    Model model) {
        try {
            List<Parqueadero> resultados;

            if (nombre != null && !nombre.trim().isEmpty()) {
                resultados = parqueaderoService.buscarPorNombre(nombre.trim());
                model.addAttribute("mensaje", "Resultados de búsqueda para nombre: " + nombre);
            } else if (idZona != null && idZona > 0) {
                resultados = parqueaderoService.buscarPorZona(idZona);
                model.addAttribute("mensaje", "Resultados de búsqueda para zona seleccionada.");
            } else {
                resultados = parqueaderoService.listarParqueaderos();
                model.addAttribute("mensaje", "Mostrando todos los parqueaderos.");
            }

            model.addAttribute("parqueaderos", resultados);
        } catch (Exception e) {
            model.addAttribute("error", "Error al buscar parqueaderos: " + e.getMessage());
            model.addAttribute("parqueaderos", parqueaderoService.listarParqueaderos());
        }

        model.addAttribute("zonas", zonaService.obtenerZonas());

        return "superadmin/parqueaderos";
    }

    @PostMapping("/parqueaderos/cambiarEstado")
    public String cambiarEstadoParqueadero(
            @RequestParam int idParqueadero,
            @RequestParam boolean habilitado,
            RedirectAttributes redirectAttributes) {
        try {
            parqueaderoService.cambiarEstado(idParqueadero, habilitado);
            redirectAttributes.addFlashAttribute("mensaje",
                    "El parqueadero ha sido " + (habilitado ? "habilitado" : "deshabilitado") + " correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al cambiar el estado: " + e.getMessage());
        }
        return "redirect:/superadmin/parqueaderos";
    }

    @GetMapping("/buscarUsuario")
    public String buscarUsuario(@RequestParam("cedula") String cedula, Model model) {
        List<Usuario> usuarios = usuarioService.buscarPorCedula(cedula);

        if (usuarios.isEmpty()) {
            model.addAttribute("error", "No se encontró ningún usuario con la cédula: " + cedula);
        } else {
            model.addAttribute("mensaje", "Resultados de búsqueda para: " + cedula);
        }

        model.addAttribute("usuarios", usuarios);
        return "superadmin/usuarios";
    }

}
