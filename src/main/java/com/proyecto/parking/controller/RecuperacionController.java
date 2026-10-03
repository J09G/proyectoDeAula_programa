package com.proyecto.parking.controller;

import com.proyecto.parking.dto.RecuperarForm;
import com.proyecto.parking.dto.RestablecerForm;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.service.RecuperacionService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** "¿Olvidaste tu contraseña?": pedir el enlace (/recuperar) y usarlo (/restablecer). */
@Controller
public class RecuperacionController {

    private final RecuperacionService recuperacionService;

    public RecuperacionController(RecuperacionService recuperacionService) {
        this.recuperacionService = recuperacionService;
    }

    @GetMapping("/recuperar")
    public String mostrarSolicitud(Model model) {
        model.addAttribute("form", new RecuperarForm());
        return "recuperar";
    }

    @PostMapping("/recuperar")
    public String solicitar(@Valid @ModelAttribute("form") RecuperarForm form, BindingResult errores) {
        if (errores.hasErrors()) {
            return "recuperar";
        }
        recuperacionService.solicitarRecuperacion(form.getCorreo());
        // Misma respuesta exista o no el correo: no se revela quién está registrado.
        return "redirect:/recuperar?enviado";
    }

    @GetMapping("/restablecer")
    public String mostrarRestablecer(@RequestParam(required = false) String token, Model model) {
        if (!recuperacionService.enlaceValido(token)) {
            model.addAttribute("enlaceInvalido", true);
            return "restablecer";
        }
        RestablecerForm form = new RestablecerForm();
        form.setToken(token);
        model.addAttribute("form", form);
        return "restablecer";
    }

    @PostMapping("/restablecer")
    public String restablecer(@Valid @ModelAttribute("form") RestablecerForm form,
                              BindingResult errores,
                              Model model) {
        // Si la contraseña no cumple las reglas, el enlace no se gasta.
        if (errores.hasErrors()) {
            return "restablecer";
        }
        try {
            recuperacionService.restablecerContrasena(form.getToken(), form.getPassword());
        } catch (ReglaNegocioException e) {
            model.addAttribute("enlaceInvalido", true);
            return "restablecer";
        }
        return "redirect:/login?restablecida";
    }
}
