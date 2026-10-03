package com.proyecto.parking.controller;

import com.proyecto.parking.config.PerfilCompletoInterceptor;
import com.proyecto.parking.dto.CompletarPerfilForm;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.security.UsuarioPrincipal;
import com.proyecto.parking.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Pantalla donde el cliente que entró con Google completa su cédula y placa
 * antes de reservar. A ella lo manda {@link PerfilCompletoInterceptor}.
 */
@Controller
@RequestMapping(PerfilCompletoInterceptor.RUTA_COMPLETAR)
public class CompletarPerfilController {

    private final UsuarioService usuarioService;

    public CompletarPerfilController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public String mostrarFormulario(@AuthenticationPrincipal UsuarioPrincipal cliente,
                                    @RequestParam(required = false) String continuar,
                                    Model model) {
        if (cliente.isPerfilCompleto()) {
            return "redirect:/cliente";
        }

        Usuario usuario = usuarioService.obtenerUsuarioPorId(cliente.getId());
        CompletarPerfilForm form = new CompletarPerfilForm();
        // Si ya tenía uno de los dos datos, se muestra fijo (de solo lectura).
        form.setCedula(usuario.getCedula());
        form.setPlaca(usuario.getPlaca());
        form.setContinuar(continuar);

        prepararVista(model, form, usuario);
        return "cliente/completar_perfil";
    }

    @PostMapping
    public String completar(@AuthenticationPrincipal UsuarioPrincipal cliente,
                            @Valid @ModelAttribute("form") CompletarPerfilForm form,
                            BindingResult errores,
                            Model model,
                            RedirectAttributes flash) {

        if (errores.hasErrors()) {
            prepararVista(model, form, usuarioService.obtenerUsuarioPorId(cliente.getId()));
            return "cliente/completar_perfil";
        }

        try {
            usuarioService.completarPerfil(cliente.getId(), form.getCedula(), form.getPlaca());
        } catch (ReglaNegocioException e) {
            model.addAttribute("error", e.getMessage());
            prepararVista(model, form, usuarioService.obtenerUsuarioPorId(cliente.getId()));
            return "cliente/completar_perfil";
        }

        flash.addFlashAttribute("mensaje", "Datos guardados. Ya puedes reservar.");
        return PerfilCompletoInterceptor.esDestinoPermitido(form.getContinuar())
                ? "redirect:" + form.getContinuar()
                : "redirect:/cliente";
    }

    private void prepararVista(Model model, CompletarPerfilForm form, Usuario usuario) {
        model.addAttribute("form", form);
        model.addAttribute("cedulaFija", usuario.getCedula() != null);
        model.addAttribute("placaFija", usuario.getPlaca() != null);
    }
}
