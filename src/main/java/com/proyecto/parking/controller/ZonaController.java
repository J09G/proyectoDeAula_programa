package com.proyecto.parking.controller;

import com.proyecto.parking.model.Zona;
import com.proyecto.parking.service.PortadaService;
import com.proyecto.parking.service.ZonaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/zona")
public class ZonaController {

    private final ZonaService zonaService;
    private final PortadaService portadaService;

    public ZonaController(ZonaService zonaService, PortadaService portadaService) {
        this.zonaService = zonaService;
        this.portadaService = portadaService;
    }

    @GetMapping("/{idZona}")
    public String mostrarZona(@PathVariable String idZona,
                              @RequestParam(required = false) String llegada,
                              Model model) {
        // Si la zona no existe salta RecursoNoEncontradoException y la maneja
        // GlobalExceptionHandler con un 404. Antes se capturaba aquí y se
        // devolvía cliente/index sin sus atributos, que salía medio vacía.
        Zona zona = zonaService.obtenerZonaPorId(idZona);

        model.addAttribute("zona", zona);
        model.addAttribute("parqueaderos", portadaService.porZona(idZona));
        // Hora elegida en el buscador de la portada; se pasa a la reserva.
        model.addAttribute("llegada", HomeController.esFechaDeFormulario(llegada) ? llegada : null);
        return "cliente/zona";
    }
}
