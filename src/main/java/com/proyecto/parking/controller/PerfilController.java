package com.proyecto.parking.controller;

import com.proyecto.parking.dto.PerfilForm;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.security.UsuarioPrincipal;
import com.proyecto.parking.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Edición del propio perfil. Accesible a cualquier usuario autenticado, sea
 * cliente, administrador o superadministrador.
 */
@Controller
@RequestMapping("/perfil")
public class PerfilController {

    private final UsuarioService usuarioService;
    private final MessageSource messageSource;
    private final LocaleResolver localeResolver;

    /** Necesario para que el cambio de identidad sobreviva a la redirección. */
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public PerfilController(UsuarioService usuarioService,
                            MessageSource messageSource,
                            LocaleResolver localeResolver) {
        this.usuarioService = usuarioService;
        this.messageSource = messageSource;
        this.localeResolver = localeResolver;
    }

    @GetMapping
    public String mostrarPerfil(@AuthenticationPrincipal UsuarioPrincipal principal, Model model) {
        if (!model.containsAttribute("form")) {
            Usuario usuario = usuarioService.obtenerUsuarioPorId(principal.getId());

            PerfilForm form = new PerfilForm();
            form.setNombre(usuario.getNombre());
            form.setCorreo(usuario.getCorreo());
            model.addAttribute("form", form);
        }
        model.addAttribute("principal", principal);
        return "perfil";
    }

    @PostMapping
    public String actualizarPerfil(@AuthenticationPrincipal UsuarioPrincipal principal,
                                   @Valid @ModelAttribute("form") PerfilForm form,
                                   BindingResult errores,
                                   Model model,
                                   RedirectAttributes flash,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {

        if (errores.hasErrors()) {
            // Se repinta la vista en vez de redirigir para conservar los errores
            // campo a campo junto a cada input.
            model.addAttribute("principal", principal);
            return "perfil";
        }

        boolean cambioPassword;
        try {
            cambioPassword = usuarioService.actualizarPerfil(
                    principal.getId(), form.getNombre(), form.getCorreo(),
                    form.getPasswordActual(), form.getPasswordNueva());
        } catch (ReglaNegocioException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("principal", principal);
            return "perfil";
        }

        // Si cambió el correo, el principal en sesión apunta a un usuario que ya
        // no existe con ese nombre y EstadoCuentaFilter cerraría la sesión en la
        // siguiente petición. Se refresca la identidad para no echar al usuario
        // justo después de guardar.
        refrescarSesion(principal.getId(), request, response);

        flash.addFlashAttribute("mensaje", traducir(
                cambioPassword ? "perfil.passwordActualizada" : "perfil.actualizado", request));
        return "redirect:/perfil";
    }

    private void refrescarSesion(String idUsuario, HttpServletRequest request, HttpServletResponse response) {
        Usuario actualizado = usuarioService.obtenerUsuarioPorId(idUsuario);
        UsuarioPrincipal nuevo = new UsuarioPrincipal(actualizado);

        Authentication auth = new UsernamePasswordAuthenticationToken(
                nuevo, nuevo.getPassword(), nuevo.getAuthorities());

        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(auth);
        SecurityContextHolder.setContext(contexto);
        // En Spring Security 6 el contexto no se guarda solo: sin esto el cambio
        // se perdería al terminar la petición.
        securityContextRepository.saveContext(contexto, request, response);
    }

    private String traducir(String clave, HttpServletRequest request) {
        return messageSource.getMessage(clave, null, localeResolver.resolveLocale(request));
    }
}
