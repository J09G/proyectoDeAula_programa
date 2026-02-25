package com.proyecto.parking.controller;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Zona;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.ZonaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/zona")
public class ZonaController {

    @Autowired
    private ZonaService zonaService;

    @Autowired
    private ParqueaderoService parqueaderoService;

    @GetMapping("/{idZona}")
    public String mostrarZona(@PathVariable("idZona") int idZona, Model model) {
        try {
            Zona zona = zonaService.obtenerZonaPorId(idZona);
            model.addAttribute("zona", zona);

            List<Parqueadero> parqueaderos = parqueaderoService.obtenerParqueaderosPorZona(idZona);
            model.addAttribute("parqueaderos", parqueaderos);

        } catch (Exception e) {
            model.addAttribute("error", "Error al cargar la zona: " + e.getMessage());
            return "cliente/index";
        }

        return "cliente/zona"; 
    }
}
