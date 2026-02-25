package com.proyecto.parking.controller;

import com.proyecto.parking.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class RegistroController {

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping("/registro/cliente")
    public String mostrarFormulario() {
        return "registro_cliente";
    }

    @PostMapping("/registro/cliente")
    public String registrarCliente(@RequestParam String nombre,
                                   @RequestParam String cedula,
                                   @RequestParam("email") String correo,
                                   @RequestParam("password") String contrasena,
                                   @RequestParam(required = false) String placa, 
                                   Model model) {

        try {
            Integer rolPorDefecto = 1; 

            if (usuarioService.existeCorreo(correo)) {
                model.addAttribute("error", "El correo ya está registrado. Intente con otro.");
                return "registro_cliente";
            }

            usuarioService.registrarUsuario(nombre, cedula, correo, contrasena, placa, rolPorDefecto);

            model.addAttribute("mensaje", "Registro exitoso. ¡Ya puedes iniciar sesión!");
            return "login";

        } catch (Exception e) {
            model.addAttribute("error", "Error al registrar usuario: " + e.getMessage());
            return "registro_cliente";
        }
    }
}
