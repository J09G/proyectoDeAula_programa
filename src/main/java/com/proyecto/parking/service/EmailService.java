package com.proyecto.parking.service;

/** Envío de correo transaccional. */
public interface EmailService {

    /**
     * Encola un correo. La llamada es asíncrona: nunca bloquea ni hace fallar la
     * operación de negocio que la dispara.
     */
    void enviarCorreo(String destinatario, String asunto, String cuerpo);
}
