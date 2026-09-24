package com.proyecto.parking.controller;

import com.proyecto.parking.dto.AdministradorForm;
import com.proyecto.parking.dto.EditarUsuarioForm;
import com.proyecto.parking.dto.ZonaForm;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Rol;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.UsuarioService;
import com.proyecto.parking.service.ZonaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/superadmin")
public class SuperAdminController {

    private static final int POR_PAGINA = 10;

    private final ParqueaderoService parqueaderoService;
    private final ZonaService zonaService;
    private final UsuarioService usuarioService;

    public SuperAdminController(ParqueaderoService parqueaderoService,
                                ZonaService zonaService,
                                UsuarioService usuarioService) {
        this.parqueaderoService = parqueaderoService;
        this.zonaService = zonaService;
        this.usuarioService = usuarioService;
    }

    // ── Panel ────────────────────────────────────────────────────────────────

    @GetMapping("")
    public String mostrarPanel(Model model) {
        model.addAttribute("form", new AdministradorForm());
        return "superadmin/index";
    }

    @GetMapping("/dashboard")
    public String mostrarDashboard() {
        return "superadmin/dashboard";
    }

    @PostMapping("/registrarAdministrador")
    public String registrarAdministrador(@Valid @ModelAttribute("form") AdministradorForm form,
                                         BindingResult errores,
                                         RedirectAttributes flash) {
        if (errores.hasErrors()) {
            flash.addFlashAttribute("error", Errores.resumen(errores));
            return "redirect:/superadmin";
        }

        try {
            usuarioService.registrarUsuario(form.getNombre(), form.getCedula(), form.getCorreo(),
                    form.getContrasena(), null, Rol.ADMINISTRADOR);
            flash.addFlashAttribute("mensaje", "Administrador registrado correctamente.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/superadmin";
    }

    // ── Usuarios ─────────────────────────────────────────────────────────────

    @GetMapping("/usuarios")
    public String listarUsuarios(@RequestParam(required = false) String cedula,
                                 @RequestParam(defaultValue = "0") int pagina,
                                 Model model) {
        Pageable pageable = PageRequest.of(Math.max(pagina, 0), POR_PAGINA,
                Sort.by(Sort.Direction.ASC, "nombre"));

        boolean buscando = cedula != null && !cedula.isBlank();
        Page<Usuario> usuarios = buscando
                ? usuarioService.buscarPorCedula(cedula, pageable)
                : usuarioService.obtenerTodosLosUsuarios(pageable);

        if (buscando) {
            model.addAttribute(usuarios.isEmpty() ? "error" : "mensaje",
                    usuarios.isEmpty()
                            ? "No se encontró ningún usuario con la cédula " + cedula + "."
                            : "Resultados para la cédula " + cedula + ".");
        }

        model.addAttribute("usuarios", usuarios.getContent());
        model.addAttribute("cedula", cedula);
        model.addAttribute("filtros", queryString("cedula", cedula));
        agregarPaginacion(model, usuarios);
        return "superadmin/usuarios";
    }

    @GetMapping("/editar/{id}")
    public String mostrarFormularioEdicion(@PathVariable String id, Model model) {
        model.addAttribute("usuario", usuarioService.obtenerUsuarioPorId(id));
        return "superadmin/editar_usuario";
    }

    @PostMapping("/actualizar")
    public String actualizarUsuario(@Valid @ModelAttribute EditarUsuarioForm form,
                                    BindingResult errores,
                                    RedirectAttributes flash) {
        if (errores.hasErrors()) {
            flash.addFlashAttribute("error", Errores.resumen(errores));
            return "redirect:/superadmin/editar/" + form.getIdUsuario();
        }

        try {
            usuarioService.actualizarUsuario(form.getIdUsuario(), form.getNombre(),
                    form.getCorreo(), form.getCedula());
            flash.addFlashAttribute("mensaje", "Usuario actualizado correctamente.");
            return "redirect:/superadmin/usuarios";
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/superadmin/editar/" + form.getIdUsuario();
        }
    }

    @PostMapping("/cambiarEstadoUsuario")
    public String cambiarEstadoUsuario(@RequestParam String idUsuario,
                                       @RequestParam boolean habilitado,
                                       RedirectAttributes flash) {
        try {
            usuarioService.cambiarEstadoUsuario(idUsuario, habilitado);
            flash.addFlashAttribute("mensaje",
                    "Usuario " + (habilitado ? "habilitado" : "deshabilitado") + " correctamente.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/superadmin/usuarios";
    }

    // ── Parqueaderos ─────────────────────────────────────────────────────────

    @GetMapping("/parqueaderos")
    public String verParqueaderos(@RequestParam(required = false) String nombre,
                                  @RequestParam(required = false) String idZona,
                                  @RequestParam(required = false) String cedulaAdmin,
                                  @RequestParam(defaultValue = "0") int pagina,
                                  Model model) {
        Pageable pageable = PageRequest.of(Math.max(pagina, 0), POR_PAGINA,
                Sort.by(Sort.Direction.ASC, "nombre"));

        Page<Parqueadero> resultados;
        if (tieneValor(nombre)) {
            resultados = parqueaderoService.buscarPorNombre(nombre, pageable);
            model.addAttribute("mensaje", "Resultados para el nombre: " + nombre);
        } else if (tieneValor(idZona)) {
            resultados = parqueaderoService.buscarPorZona(idZona, pageable);
            model.addAttribute("mensaje", "Resultados para la zona seleccionada.");
        } else if (tieneValor(cedulaAdmin)) {
            resultados = parqueaderoService.buscarPorCedulaAdmin(cedulaAdmin, pageable);
            model.addAttribute("mensaje", "Resultados para la cédula de administrador: " + cedulaAdmin);
        } else {
            resultados = parqueaderoService.listarParqueaderos(pageable);
        }

        if (resultados.isEmpty()) {
            model.addAttribute("mensaje", "No hay parqueaderos que coincidan con la búsqueda.");
        }

        model.addAttribute("parqueaderos", resultados.getContent());
        model.addAttribute("zonas", zonaService.obtenerZonas());
        model.addAttribute("nombre", nombre);
        model.addAttribute("idZona", idZona);
        model.addAttribute("cedulaAdmin", cedulaAdmin);
        model.addAttribute("filtros",
                queryString("nombre", nombre)
                + queryString("idZona", idZona)
                + queryString("cedulaAdmin", cedulaAdmin));
        agregarPaginacion(model, resultados);
        return "superadmin/parqueaderos";
    }

    @PostMapping("/parqueaderos/cambiarEstado")
    public String cambiarEstadoParqueadero(@RequestParam String idParqueadero,
                                           @RequestParam boolean habilitado,
                                           RedirectAttributes flash) {
        parqueaderoService.cambiarEstadoComoSuperadmin(idParqueadero, habilitado);
        flash.addFlashAttribute("mensaje",
                "El parqueadero ha sido " + (habilitado ? "habilitado" : "deshabilitado") + ".");
        return "redirect:/superadmin/parqueaderos";
    }

    // ── Zonas ────────────────────────────────────────────────────────────────

    @GetMapping("/zonas")
    public String verZonas(Model model) {
        model.addAttribute("zonas", zonaService.obtenerZonas());
        model.addAttribute("form", new ZonaForm());
        return "superadmin/zonas";
    }

    @PostMapping("/zonas/crear")
    public String crearZona(@Valid @ModelAttribute("form") ZonaForm form,
                            BindingResult errores,
                            RedirectAttributes flash) {
        if (errores.hasErrors()) {
            flash.addFlashAttribute("error", Errores.resumen(errores));
            return "redirect:/superadmin/zonas";
        }

        try {
            zonaService.crearZona(form.getNombreZona());
            flash.addFlashAttribute("mensaje", "Zona creada correctamente.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/superadmin/zonas";
    }

    @PostMapping("/zonas/cambiarEstado")
    public String cambiarEstadoZona(@RequestParam String idZona,
                                    @RequestParam boolean habilitado,
                                    RedirectAttributes flash) {
        zonaService.cambiarEstadoZona(idZona, habilitado);
        flash.addFlashAttribute("mensaje",
                "Zona " + (habilitado ? "habilitada" : "deshabilitada") + " correctamente.");
        return "redirect:/superadmin/zonas";
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    private boolean tieneValor(String valor) {
        return valor != null && !valor.isBlank();
    }

    /**
     * Fragmento de query string para conservar un filtro al cambiar de página.
     * El valor se codifica para que una búsqueda con espacios o acentos no rompa
     * el enlace.
     */
    private String queryString(String nombreParametro, String valor) {
        if (!tieneValor(valor)) {
            return "";
        }
        return "&" + nombreParametro + "="
                + URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    private void agregarPaginacion(Model model, Page<?> pagina) {
        model.addAttribute("paginaActual", pagina.getNumber());
        model.addAttribute("totalPaginas", pagina.getTotalPages());
        model.addAttribute("totalElementos", pagina.getTotalElements());
    }
}
