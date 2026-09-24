package com.proyecto.parking.controller;

import com.proyecto.parking.dto.RegistroClienteForm;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Rol;
import com.proyecto.parking.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RegistroController {

    private final UsuarioService usuarioService;

    public RegistroController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/registro/cliente")
    public String mostrarFormulario(Model model) {
        model.addAttribute("form", new RegistroClienteForm());
        return "registro_cliente";
    }

    @PostMapping("/registro/cliente")
    public String registrarCliente(@Valid @ModelAttribute("form") RegistroClienteForm form,
                                   BindingResult errores,
                                   Model model,
                                   RedirectAttributes flash) {
        // Bean Validation cubre formato, longitud y que las contraseñas coincidan.
        if (errores.hasErrors()) {
            return "registro_cliente";
        }

        try {
            usuarioService.registrarUsuario(
                    form.getNombre(), form.getCedula(), form.getEmail(),
                    form.getPassword(), form.getPlaca(), Rol.CLIENTE);

        } catch (ReglaNegocioException e) {
            model.addAttribute("error", e.getMessage());
            return "registro_cliente";
        }

        flash.addFlashAttribute("mensaje", "Registro exitoso. Ya puedes iniciar sesión.");
        return "redirect:/login";
    }
}
