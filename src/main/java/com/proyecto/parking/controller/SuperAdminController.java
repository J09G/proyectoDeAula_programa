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

        if (superadmin == null) return "redirect:/login";
        if (!superadmin.getRol().getNombre().equalsIgnoreCase("SuperAdmin")) return "redirect:/error/403";

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
                                       @RequestParam("id_zona") String idZona,
                                       @RequestParam("url_maps") String urlMaps,
                                       @RequestParam String telefono,
                                       HttpSession session,
                                       RedirectAttributes redirectAttributes) {
        try {
            Usuario superadmin = (Usuario) session.getAttribute("usuario");
            parqueaderoService.registrarParqueadero(nombre, direccion, horario, tarifa,
                    espaciosTotales, espaciosDisponibles, idZona, superadmin.getId(), urlMaps, telefono);
            redirectAttributes.addFlashAttribute("mensaje", "Parqueadero registrado correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al registrar el parqueadero: " + e.getMessage());
        }

        return "redirect:/superadmin";
    }

    @PostMapping("/asignarAdmin")
    public String asignarAdministrador(@RequestParam String correo,
                                       @RequestParam String cedula,
                                       @RequestParam String contrasena,
                                       @RequestParam("id_parqueadero") String idParqueadero,
                                       Model model) {
        try {
            Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorId(idParqueadero);

            if (parqueadero.getAdministrador() != null) {
                model.addAttribute("error", "Este parqueadero ya tiene un administrador asignado.");
            } else {
                String nombreAdmin = "Administrador " + idParqueadero;
                usuarioService.registrarUsuario(nombreAdmin, cedula, correo, contrasena, "Administrador");
                Usuario nuevoAdmin = usuarioService.obtenerUsuarioPorCorreo(correo);
                parqueaderoService.asignarAdministrador(idParqueadero, nuevoAdmin.getId());
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
    public String mostrarFormularioEdicion(@PathVariable String id, Model model) {
        Usuario usuario = usuarioService.obtenerUsuarioPorId(id);
        model.addAttribute("usuario", usuario);
        return "superadmin/editar_usuario";
    }

    @PostMapping("/actualizar")
    public String actualizarUsuario(@RequestParam String idUsuario,
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

    @PostMapping("/cambiarEstadoUsuario")
    public String cambiarEstadoUsuario(@RequestParam String idUsuario,
                                       @RequestParam boolean habilitado,
                                       RedirectAttributes redirectAttributes) {
        try {
            usuarioService.cambiarEstadoUsuario(idUsuario, habilitado);
            redirectAttributes.addFlashAttribute("mensaje",
                    "Usuario " + (habilitado ? "habilitado" : "deshabilitado") + " correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al cambiar estado: " + e.getMessage());
        }
        return "redirect:/superadmin/usuarios";
    }

    @GetMapping("/parqueaderos")
    public String verParqueaderos(Model model) {
        try {
            List<Parqueadero> parqueaderos = parqueaderoService.listarParqueaderos();
            model.addAttribute("parqueaderos", parqueaderos);
            model.addAttribute("zonas", zonaService.obtenerZonas());
            if (parqueaderos.isEmpty()) {
                model.addAttribute("mensaje", "No hay parqueaderos registrados.");
            }
        } catch (Exception e) {
            model.addAttribute("error", "Error al cargar los parqueaderos: " + e.getMessage());
        }
        return "superadmin/parqueaderos";
    }

    @GetMapping("/parqueaderos/buscar")
    public String buscarParqueaderos(@RequestParam(required = false) String nombre,
                                     @RequestParam(required = false) String idZona,
                                     @RequestParam(required = false) String cedulaAdmin,
                                     Model model) {
        try {
            List<Parqueadero> resultados;

            if (nombre != null && !nombre.trim().isEmpty()) {
                resultados = parqueaderoService.buscarPorNombre(nombre.trim());
                model.addAttribute("mensaje", "Resultados para nombre: " + nombre);
            } else if (idZona != null && !idZona.trim().isEmpty()) {
                resultados = parqueaderoService.buscarPorZona(idZona);
                model.addAttribute("mensaje", "Resultados para zona seleccionada.");
            } else if (cedulaAdmin != null && !cedulaAdmin.trim().isEmpty()) {
                resultados = parqueaderoService.buscarPorCedulaAdmin(cedulaAdmin.trim());
                model.addAttribute("mensaje", "Resultados para cédula de administrador: " + cedulaAdmin);
            } else {
                resultados = parqueaderoService.listarParqueaderos();
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
    public String cambiarEstadoParqueadero(@RequestParam String idParqueadero,
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

    @GetMapping("/zonas")
    public String verZonas(Model model) {
        model.addAttribute("zonas", zonaService.obtenerZonas());
        return "superadmin/zonas";
    }

    @PostMapping("/zonas/crear")
    public String crearZona(@RequestParam String nombreZona, RedirectAttributes redirectAttributes) {
        try {
            zonaService.crearZona(nombreZona);
            redirectAttributes.addFlashAttribute("mensaje", "Zona creada correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al crear la zona: " + e.getMessage());
        }
        return "redirect:/superadmin/zonas";
    }

    @PostMapping("/zonas/cambiarEstado")
    public String cambiarEstadoZona(@RequestParam String idZona,
                                    @RequestParam boolean habilitado,
                                    RedirectAttributes redirectAttributes) {
        try {
            zonaService.cambiarEstadoZona(idZona, habilitado);
            redirectAttributes.addFlashAttribute("mensaje",
                    "Zona " + (habilitado ? "habilitada" : "deshabilitada") + " correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al cambiar estado de zona: " + e.getMessage());
        }
        return "redirect:/superadmin/zonas";
    }

    @GetMapping("/dashboard")
    public String mostrarDashboard(HttpSession session) {
        Usuario superadmin = (Usuario) session.getAttribute("usuario");
        if (superadmin == null) return "redirect:/login";
        if (!superadmin.getRol().getNombre().equalsIgnoreCase("SuperAdmin")) return "redirect:/error/403";
        return "superadmin/dashboard";
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
