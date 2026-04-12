package com.proyecto.parking.controller;

import com.proyecto.parking.model.Usuario;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String mostrarLogin(HttpSession session) {
        Usuario usuario = (Usuario) session.getAttribute("usuario");
        if (usuario != null) {
            String rol = usuario.getRol().getNombre().toLowerCase();
            return switch (rol) {
                case "cliente" -> "redirect:/cliente";
                case "administrador" -> "redirect:/admin";
                case "superadmin" -> "redirect:/superadmin";
                default -> "login";
            };
        }
        return "login";
    }
}
