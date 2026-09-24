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

    public Reservas getReservas() { return reservas; }
    public Seguridad getSeguridad() { return seguridad; }

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
