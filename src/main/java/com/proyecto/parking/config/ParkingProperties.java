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
    private final Pagos pagos = new Pagos();

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
    public Pagos getPagos() { return pagos; }

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

    /**
     * Anticipo que se cobra al reservar. Sin la llave pública y el secreto de
     * integridad de la pasarela, los pagos quedan apagados y las reservas
     * funcionan sin cobro, como antes de existir esta función.
     */
    public static class Pagos {
        /** Minutos que tiene el cliente para pagar antes de que se libere el cubículo. */
        private long minutosParaPagar = 15;
        /**
         * Antelación mínima, respecto a la hora de llegada, con la que el cliente
         * debe cancelar para recuperar el anticipo. Si cancela más tarde o no
         * llega, el parqueadero se lo queda.
         */
        private long minutosAntelacionDevolucion = 60;
        /** Importe mínimo que acepta la pasarela, en pesos. */
        private double montoMinimo = 1_500;

        private final Wompi wompi = new Wompi();

        /** true si la pasarela está configurada y por tanto se cobra el anticipo. */
        public boolean isHabilitados() {
            return tieneTexto(wompi.getLlavePublica()) && tieneTexto(wompi.getSecretoIntegridad());
        }

        public long getMinutosParaPagar() { return minutosParaPagar; }
        public void setMinutosParaPagar(long minutosParaPagar) { this.minutosParaPagar = minutosParaPagar; }

        public long getMinutosAntelacionDevolucion() { return minutosAntelacionDevolucion; }
        public void setMinutosAntelacionDevolucion(long minutosAntelacionDevolucion) {
            this.minutosAntelacionDevolucion = minutosAntelacionDevolucion;
        }

        public double getMontoMinimo() { return montoMinimo; }
        public void setMontoMinimo(double montoMinimo) { this.montoMinimo = montoMinimo; }

        public Wompi getWompi() { return wompi; }

        private static boolean tieneTexto(String valor) {
            return valor != null && !valor.isBlank();
        }
    }

    /** Credenciales y direcciones de Wompi. Las de pruebas empiezan por {@code pub_test_}, etc. */
    public static class Wompi {
        /** Identifica al comercio en la página de pago. No es secreta. */
        private String llavePublica = "";
        /** Permite consultar por referencia y anular cobros. Opcional: sin ella las devoluciones son manuales. */
        private String llavePrivada = "";
        /** Firma el importe y la referencia para que nadie los cambie en la URL de pago. */
        private String secretoIntegridad = "";
        /** Verifica que un aviso de pago viene de Wompi. Opcional: sin él, los avisos se ignoran. */
        private String secretoEventos = "";
        private String urlApi = "https://sandbox.wompi.co/v1";
        private String urlCheckout = "https://checkout.wompi.co/p/";

        public String getLlavePublica() { return llavePublica; }
        public void setLlavePublica(String llavePublica) { this.llavePublica = llavePublica; }

        public String getLlavePrivada() { return llavePrivada; }
        public void setLlavePrivada(String llavePrivada) { this.llavePrivada = llavePrivada; }

        public String getSecretoIntegridad() { return secretoIntegridad; }
        public void setSecretoIntegridad(String secretoIntegridad) { this.secretoIntegridad = secretoIntegridad; }

        public String getSecretoEventos() { return secretoEventos; }
        public void setSecretoEventos(String secretoEventos) { this.secretoEventos = secretoEventos; }

        public String getUrlApi() { return urlApi; }
        public void setUrlApi(String urlApi) { this.urlApi = urlApi; }

        public String getUrlCheckout() { return urlCheckout; }
        public void setUrlCheckout(String urlCheckout) { this.urlCheckout = urlCheckout; }
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
