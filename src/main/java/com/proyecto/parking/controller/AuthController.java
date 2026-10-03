package com.proyecto.parking.controller;

import com.proyecto.parking.exception.RecursoNoEncontradoException;
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

    /**
     * Hash BCrypt de una contraseña cualquiera. Cuando el correo no existe se
     * compara contra él, para que la respuesta tarde lo mismo que con un correo
     * existente: si no, el tiempo delataría qué correos están registrados.
     */
    private String hashSenuelo;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        Usuario usuario;
        try {
            usuario = usuarioService.obtenerUsuarioPorCorreo(request.correo());
        } catch (RecursoNoEncontradoException e) {
            // Mismo 401 y mismo mensaje que una contraseña incorrecta (US-01 AC2).
            // Antes esta excepción salía como 404 y revelaba que el correo no existe.
            passwordEncoder.matches(request.password(), hashSenuelo());
            return credencialesInvalidas();
        }

        if (usuario.getContrasena() == null
                || !passwordEncoder.matches(request.password(), usuario.getContrasena())) {
            return credencialesInvalidas();
        }

        if (!usuario.isHabilitado()) {
            return credencialesInvalidas();
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

    private ResponseEntity<ErrorResponse> credencialesInvalidas() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Correo o contraseña incorrectos."));
    }

    private String hashSenuelo() {
        // Se calcula una vez, en la primera petición, con el mismo codificador
        // (y por lo tanto el mismo costo) que las contraseñas reales.
        if (hashSenuelo == null) {
            hashSenuelo = passwordEncoder.encode("senuelo-para-igualar-tiempos");
        }
        return hashSenuelo;
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
