package com.proyecto.parking.controller;

import com.proyecto.parking.prediccion.Prediccion;
import com.proyecto.parking.service.PrediccionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio web del módulo predictivo: ocupación estimada de un parqueadero.
 *
 * <p>Responde en JSON a cualquier usuario con sesión; lo consumen la pantalla
 * de reserva del cliente y el panel del administrador.</p>
 */
@RestController
@RequestMapping("/prediccion")
public class PrediccionController {

    /** No tiene sentido pedir predicciones a años vista: el modelo mira una semana atrás. */
    private static final int MAX_DIAS_ADELANTE = 60;

    private final PrediccionService prediccionService;

    public PrediccionController(PrediccionService prediccionService) {
        this.prediccionService = prediccionService;
    }

    /** {@code GET /prediccion/ocupacion?idParqueadero=...&fecha=2026-10-13T18:00} */
    @GetMapping("/ocupacion")
    public ResponseEntity<?> ocupacion(@RequestParam String idParqueadero, @RequestParam String fecha) {
        LocalDateTime momento;
        try {
            momento = LocalDateTime.parse(fecha);
        } catch (DateTimeParseException e) {
            return fechaInvalida("2026-10-13T18:00");
        }
        if (fueraDeRango(momento.toLocalDate())) {
            return fueraDeRango();
        }
        return ResponseEntity.ok(prediccionService.predecir(idParqueadero, momento));
    }

    /** {@code GET /prediccion/dia?idParqueadero=...&fecha=2026-10-13} */
    @GetMapping("/dia")
    public ResponseEntity<?> dia(@RequestParam String idParqueadero, @RequestParam String fecha) {
        LocalDate dia;
        try {
            dia = LocalDate.parse(fecha);
        } catch (DateTimeParseException e) {
            return fechaInvalida("2026-10-13");
        }
        if (fueraDeRango(dia)) {
            return fueraDeRango();
        }
        List<Prediccion> franjas = prediccionService.predecirDia(idParqueadero, dia);
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("idParqueadero", idParqueadero);
        respuesta.put("fecha", dia.toString());
        respuesta.put("conHistorial", !franjas.isEmpty() && franjas.get(0).conHistorial());
        respuesta.put("franjas", franjas);
        return ResponseEntity.ok(respuesta);
    }

    private static boolean fueraDeRango(LocalDate dia) {
        LocalDate hoy = LocalDate.now();
        return dia.isBefore(hoy.minusDays(1)) || dia.isAfter(hoy.plusDays(MAX_DIAS_ADELANTE));
    }

    private static ResponseEntity<Map<String, String>> fueraDeRango() {
        return ResponseEntity.badRequest().body(Map.of("error",
                "Sólo se estima la ocupación desde hoy hasta " + MAX_DIAS_ADELANTE + " días adelante."));
    }

    private static ResponseEntity<Map<String, String>> fechaInvalida(String ejemplo) {
        return ResponseEntity.badRequest().body(Map.of("error", "Fecha no válida. Ejemplo: " + ejemplo));
    }
}
