package com.proyecto.parking.controller;

import com.proyecto.parking.dto.ZonaDestacada;
import com.proyecto.parking.security.LoginSuccessHandler;
import com.proyecto.parking.security.UsuarioPrincipal;
import com.proyecto.parking.service.PortadaService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Portada pública.
 *
 * <p>Quien llega sin sesión ve la página de presentación: zonas, parqueaderos
 * destacados y cómo funciona. Quien ya entró va directo a su panel, porque para
 * un usuario con sesión la portada no aporta nada.</p>
 */
@Controller
public class HomeController {

    /** Tarjetas de parqueadero que caben bien en la portada. */
    private static final int DESTACADOS = 3;

    private final PortadaService portadaService;

    public HomeController(PortadaService portadaService) {
        this.portadaService = portadaService;
    }

    @GetMapping("/")
    public String portada(@AuthenticationPrincipal UsuarioPrincipal principal, Model model) {
        if (principal != null) {
            return "redirect:" + LoginSuccessHandler.destinoPara(principal);
        }

        List<ZonaDestacada> zonas = portadaService.zonasConConteo();
        model.addAttribute("zonas", zonas);
        model.addAttribute("totalParqueaderos",
                zonas.stream().mapToLong(ZonaDestacada::parqueaderos).sum());
        model.addAttribute("destacados", portadaService.destacados(DESTACADOS));
        return "portada";
    }

    /**
     * Destino del buscador de la portada. Lleva a la zona elegida, o al panel
     * del cliente si se buscó en todas. Sin sesión, Spring Security intercepta
     * esas rutas y manda al login.
     */
    @GetMapping("/buscar")
    public String buscar(@RequestParam(required = false) String zona,
                         @RequestParam(required = false) String llegada,
                         RedirectAttributes redirect) {
        if (zona == null || !zona.matches("[A-Za-z0-9]+")) {
            return "redirect:/cliente";
        }
        // La fecha viaja hasta el formulario de reserva para rellenarlo. Sólo se
        // reenvía si tiene el formato de <input type="datetime-local">.
        if (esFechaDeFormulario(llegada)) {
            redirect.addAttribute("llegada", llegada);
        }
        return "redirect:/zona/" + zona;
    }

    /** {@code true} si el texto tiene la forma {@code 2026-09-24T18:30}. */
    public static boolean esFechaDeFormulario(String valor) {
        return valor != null && valor.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}");
    }
}
