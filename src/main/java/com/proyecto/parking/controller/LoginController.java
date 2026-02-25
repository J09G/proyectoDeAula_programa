package com.proyecto.parking.controller;

import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.service.UsuarioService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class LoginController {

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping("/login")
    public String mostrarLogin(HttpSession session) {
        Usuario usuario = (Usuario) session.getAttribute("usuario");
        if (usuario != null) {
            String rol = usuario.getRol().getNombre().toLowerCase();
            switch (rol) {
                case "cliente": return "redirect:/cliente";
                case "administrador": return "redirect:/admin";
                case "superadmin": return "redirect:/superadmin";
            }
        }
        return "login";
    }

    @PostMapping("/login")
    public String procesarLogin(@RequestParam("email") String correo,
                                @RequestParam("password") String contrasena,
                                Model model,
                                HttpSession session) {

        Usuario usuario = usuarioService.obtenerUsuarioPorCorreo(correo);

        if (usuario != null && usuario.getContrasena().equals(contrasena)) {
            session.setAttribute("usuario", usuario);

            String rol = usuario.getRol().getNombre().toLowerCase();
            switch (rol) {
                case "cliente": return "redirect:/cliente";
                case "administrador": return "redirect:/admin";
                case "superadmin": return "redirect:/superadmin";
                default:
                    model.addAttribute("error", "Rol desconocido.");
                    return "login";
            }
        } else {
            model.addAttribute("error", "Correo o contraseña incorrectos.");
            return "login";
        }
    }

    @GetMapping("/logout")
    public String cerrarSesion(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
