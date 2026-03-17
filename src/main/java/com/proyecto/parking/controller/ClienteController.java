package com.proyecto.parking.controller;

import com.proyecto.parking.model.Zona;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.service.ZonaService;
import com.proyecto.parking.service.ReservaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.HttpSession;

import java.util.List;

@Controller
public class ClienteController {

    @Autowired
    private ZonaService zonaService;

    @Autowired
    private ReservaService reservaService;

    @GetMapping("/cliente")
    public String mostrarPanelCliente(Model model, HttpSession session) {
        Usuario cliente = (Usuario) session.getAttribute("usuario");

        if (cliente == null) {
            return "redirect:/login";
        }
        if (!cliente.getRol().getNombre().equalsIgnoreCase("Cliente")) {
            return "redirect:/error/403";
        }

        List<Zona> zonas = zonaService.obtenerZonasHabilitadas();
        List<Reserva> reservas = reservaService.listarReservasCliente(cliente.getIdUsuario());

        model.addAttribute("zonas", zonas);
        model.addAttribute("reservas", reservas);

        return "cliente/index";
    }
}
