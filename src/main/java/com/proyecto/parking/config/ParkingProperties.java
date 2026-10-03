package com.proyecto.parking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros de negocio configurables (prefijo {@code parking} en application.yml).
 * Tenerlos aquí evita las constantes mágicas repartidas por los servicios.
 */
@ConfigurationProperties(prefix = "parking")
public class ParkingProperties {

    private final Reservas reservas = new Reservas();
    private final Seguridad seguridad = new Seguridad();
    private final Recuperacion recuperacion = new Recuperacion();

    /**
     * Dirección pública de la app (p. ej. https://parking.onrender.com), usada
     * para armar los enlaces de los correos. Sale de la configuración y no de la
     * cabecera Host de la petición: esa la controla quien hace la petición, y
     * con ella podría hacer que el correo apunte a su propio sitio.
     */
    private String urlPublica = "http://localhost:8081";

    public Reservas getReservas() { return reservas; }
    public Seguridad getSeguridad() { return seguridad; }
    public Recuperacion getRecuperacion() { return recuperacion; }

    public String getUrlPublica() { return urlPublica; }
    public void setUrlPublica(String urlPublica) { this.urlPublica = urlPublica; }

    public static class Recuperacion {
        /** Vida del enlace de recuperación de contraseña. */
        private long minutosVigencia = 30;
        /** Tiempo mínimo entre dos correos de recuperación al mismo usuario. */
        private long segundosEntreEnvios = 60;

        public long getMinutosVigencia() { return minutosVigencia; }
        public void setMinutosVigencia(long minutosVigencia) { this.minutosVigencia = minutosVigencia; }
        public long getSegundosEntreEnvios() { return segundosEntreEnvios; }
        public void setSegundosEntreEnvios(long segundosEntreEnvios) { this.segundosEntreEnvios = segundosEntreEnvios; }
    }

    public static class Reservas {
        /** Minutos de cortesía tras la hora de llegada antes de liberar el cubículo. */
        private long minutosTolerancia = 120;
        private long intervaloExpiracionMs = 900_000;

        public long getMinutosTolerancia() { return minutosTolerancia; }
        public void setMinutosTolerancia(long minutosTolerancia) { this.minutosTolerancia = minutosTolerancia; }

        public long getIntervaloExpiracionMs() { return intervaloExpiracionMs; }
        public void setIntervaloExpiracionMs(long intervaloExpiracionMs) { this.intervaloExpiracionMs = intervaloExpiracionMs; }
    }

    public static class Seguridad {
        private int maxIntentosLogin = 5;
        private int minutosBloqueo = 30;

        public int getMaxIntentosLogin() { return maxIntentosLogin; }
        public void setMaxIntentosLogin(int maxIntentosLogin) { this.maxIntentosLogin = maxIntentosLogin; }

        public int getMinutosBloqueo() { return minutosBloqueo; }
        public void setMinutosBloqueo(int minutosBloqueo) { this.minutosBloqueo = minutosBloqueo; }
    }
}
