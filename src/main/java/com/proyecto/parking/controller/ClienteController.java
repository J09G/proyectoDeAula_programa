package com.proyecto.parking.controller;

import com.proyecto.parking.model.Zona;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.service.ComentarioService;
import com.proyecto.parking.service.ZonaService;
import com.proyecto.parking.service.ReservaService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class ClienteController {

    @Autowired
    private ZonaService zonaService;

    @Autowired
    private ReservaService reservaService;

    @Autowired
    private ComentarioService comentarioService;

    @GetMapping("/cliente")
    public String mostrarPanelCliente(Model model, HttpSession session) {
        Usuario cliente = (Usuario) session.getAttribute("usuario");

        if (cliente == null) return "redirect:/login";
        if (!cliente.getRol().getNombre().equalsIgnoreCase("Cliente")) return "redirect:/error/403";

        List<Zona> zonas = zonaService.obtenerZonasHabilitadas();
        List<Reserva> reservas = reservaService.listarReservasCliente(cliente.getId());

        model.addAttribute("zonas", zonas);
        model.addAttribute("reservas", reservas);
        model.addAttribute("usuario", cliente);

        return "cliente/index";
    }

    @PostMapping("/comentario/crear")
    public String crearComentario(@RequestParam("idParqueadero") String idParqueadero,
                                  @RequestParam("texto") String texto,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        try {
            Usuario cliente = (Usuario) session.getAttribute("usuario");
            if (cliente == null) return "redirect:/login";

            if (texto == null || texto.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorComentario", "El comentario no puede estar vacío.");
                return "redirect:/reserva/" + idParqueadero;
            }

            comentarioService.crearComentario(cliente.getId(), idParqueadero, texto);
            redirectAttributes.addFlashAttribute("mensajeComentario", "Comentario publicado correctamente.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorComentario", "Error al publicar el comentario: " + e.getMessage());
        }

        return "redirect:/reserva/" + idParqueadero;
    }
}
