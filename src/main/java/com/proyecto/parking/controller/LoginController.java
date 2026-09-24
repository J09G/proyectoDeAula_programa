package com.proyecto.parking.controller;

import com.proyecto.parking.security.LoginSuccessHandler;
import com.proyecto.parking.security.UsuarioPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String mostrarLogin(@AuthenticationPrincipal UsuarioPrincipal principal) {
        // Quien ya tiene sesión no necesita ver el formulario otra vez.
        if (principal != null) {
            return "redirect:" + LoginSuccessHandler.destinoPara(principal);
        }
        return "login";
    }
}
