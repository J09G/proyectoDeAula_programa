package com.proyecto.parking.controller;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.ReservaService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/reserva")
public class ReservaController {

    @Autowired
    private ReservaService reservaService;

    @Autowired
    private ParqueaderoService parqueaderoService;

    @GetMapping("/{idParqueadero}")
    public String mostrarFormularioReserva(@PathVariable("idParqueadero") int idParqueadero,
                                           Model model) {
        Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorId(idParqueadero);
        model.addAttribute("parqueadero", parqueadero);
        return "cliente/reserva"; 
    }

    @PostMapping("/crear")
    public String crearReserva(@RequestParam("idParqueadero") int idParqueadero,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        try {
            Usuario cliente = (Usuario) session.getAttribute("usuario");

            if (cliente == null) {
                redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión para hacer una reserva.");
                return "redirect:/login";
            }

            Reserva nuevaReserva = reservaService.crearReserva(cliente.getIdUsuario(), idParqueadero);
            redirectAttributes.addFlashAttribute("mensaje",
                    "Reserva creada con éxito. Estado: " + nuevaReserva.getEstado());

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al crear la reserva: " + e.getMessage());
        }

        return "redirect:/cliente";
    }

}
