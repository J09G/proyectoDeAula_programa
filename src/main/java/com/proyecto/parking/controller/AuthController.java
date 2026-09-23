package com.proyecto.parking.controller;

import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.security.JwtService;
import com.proyecto.parking.service.UsuarioService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    public record LoginRequest(@NotBlank @Email String correo, @NotBlank String password) {}

    public record RegistroRequest(
            @NotBlank String nombre,
            @NotBlank String cedula,
            @NotBlank @Email String correo,
            @NotBlank String contrasena,
            String placa) {}

    public record TokenResponse(String token, long expiraEnMs, String rol) {}

    public record ErrorResponse(String mensaje) {}

    public record ValidacionErrorResponse(String mensaje, Map<String, String> campos) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Usuario usuario = usuarioService.obtenerUsuarioPorCorreo(request.correo());

        if (usuario == null
                || usuario.getContrasena() == null
                || !passwordEncoder.matches(request.password(), usuario.getContrasena())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Correo o contraseña incorrectos."));
        }

        if (!usuario.isHabilitado()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Correo o contraseña incorrectos."));
        }

        String rol = usuario.getRol().getNombre();
        String token = jwtService.generarToken(usuario.getCorreo(), rol);

        return ResponseEntity.ok(new TokenResponse(token, jwtService.getExpirationMs(), rol));
    }

    @PostMapping("/registro")
    public ResponseEntity<?> registro(@Valid @RequestBody RegistroRequest request) {
        try {
            usuarioService.registrarUsuario(
                    request.nombre(),
                    request.cedula(),
                    request.correo(),
                    request.contrasena(),
                    request.placa(),
                    "Cliente");
        } catch (RuntimeException e) {
            // registrarUsuario lanza RuntimeException con mensaje en español para correo o cedula duplicados.
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
        }

        Usuario creado = usuarioService.obtenerUsuarioPorCorreo(request.correo());
        String token = jwtService.generarToken(creado.getCorreo(), creado.getRol().getNombre());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new TokenResponse(token, jwtService.getExpirationMs(), creado.getRol().getNombre()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidacionErrorResponse> manejarValidacion(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(err ->
                campos.put(err.getField(), err.getDefaultMessage()));

        return ResponseEntity.badRequest()
                .body(new ValidacionErrorResponse("Hay campos obligatorios sin completar o inválidos.", campos));
    }
}
