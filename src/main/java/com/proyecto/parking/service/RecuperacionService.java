package com.proyecto.parking.service;

/** "¿Olvidaste tu contraseña?": enlace de un solo uso enviado por correo. */
public interface RecuperacionService {

    /**
     * Envía el enlace de recuperación si el correo pertenece a un usuario
     * habilitado. No avisa si el correo no existe: quien llama debe responder
     * siempre lo mismo, para no revelar quién está registrado.
     */
    void solicitarRecuperacion(String correo);

    /** El enlace existe y no ha vencido. No lo consume. */
    boolean enlaceValido(String token);

    /**
     * Consume el enlace y fija la contraseña nueva.
     *
     * @throws com.proyecto.parking.exception.ReglaNegocioException
     *         si el enlace no existe, ya se usó o venció
     */
    void restablecerContrasena(String token, String contrasenaNueva);
}
