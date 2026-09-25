package com.proyecto.parking.controller;

import com.proyecto.parking.dto.PerfilForm;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.security.JwtService;
import com.proyecto.parking.security.UsuarioPrincipal;
import com.proyecto.parking.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final JwtService jwtService;
    private final boolean cookieSegura;

    public PerfilController(UsuarioService usuarioService,
                            MessageSource messageSource,
                            LocaleResolver localeResolver,
                            JwtService jwtService,
                            @Value("${server.ssl.enabled}") boolean cookieSegura) {
        this.usuarioService = usuarioService;
        this.messageSource = messageSource;
        this.localeResolver = localeResolver;
        this.jwtService = jwtService;
        this.cookieSegura = cookieSegura;
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

        // Si cambió el correo, la cookie JWT vieja quedó firmada con un "sub" que
        // ya no existe con ese correo, y JwtCookieAuthenticationFilter dejaría de
        // reconocerlo en la siguiente petición. Se emite un JWT nuevo para no
        // echar al usuario justo después de guardar.
        refrescarCookieJwt(principal.getId(), response);

        flash.addFlashAttribute("mensaje", traducir(
                cambioPassword ? "perfil.passwordActualizada" : "perfil.actualizado", request));
        return "redirect:/perfil";
    }

    private void refrescarCookieJwt(String idUsuario, HttpServletResponse response) {
        Usuario actualizado = usuarioService.obtenerUsuarioPorId(idUsuario);
        UsuarioPrincipal nuevo = new UsuarioPrincipal(actualizado);

        String token = jwtService.generarToken(nuevo.getCorreo(), nuevo.getRol());
        response.addHeader(HttpHeaders.SET_COOKIE, jwtService.crearCookieJwt(token, cookieSegura).toString());
    }

    private String traducir(String clave, HttpServletRequest request) {
        return messageSource.getMessage(clave, null, localeResolver.resolveLocale(request));
    }
}
