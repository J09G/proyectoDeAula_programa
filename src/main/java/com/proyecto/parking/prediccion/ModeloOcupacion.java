package com.proyecto.parking.prediccion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Modelo de ocupación entrenado en Python ({@code ml/entrenar_modelo.py}) y
 * evaluado aquí, sin depender de Python en producción.
 *
 * <p>Es un bosque aleatorio: cada árbol llega a una hoja comparando variables
 * con umbrales, y la predicción es el promedio de las hojas.</p>
 */
@Component
public class ModeloOcupacion {

    public static final String RECURSO = "modelo/ocupacion.json";

    /** Posición de cada variable en el vector de entrada, en el orden del entrenamiento. */
    public static final int HORA = 0;
    public static final int DIA_SEMANA = 1;
    public static final int FIN_DE_SEMANA = 2;
    public static final int FESTIVO = 3;
    public static final int TEMPORADA_ALTA = 4;
    public static final int CAPACIDAD = 5;
    public static final int TARIFA = 6;
    public static final int RESERVAS_PCT = 7;
    public static final int SEMANA_ANTERIOR = 8;
    public static final int PRIMERA_ZONA = 9;

    private record Arbol(int[] variable, double[] umbral, int[] izquierda, int[] derecha, double[] valor) {

        double evaluar(double[] x) {
            int nodo = 0;
            while (variable[nodo] >= 0) {
                // scikit-learn compara en precisión simple: se hace igual para dar lo mismo.
                nodo = (float) x[variable[nodo]] <= umbral[nodo] ? izquierda[nodo] : derecha[nodo];
            }
            return valor[nodo];
        }
    }

    private final List<Arbol> arboles = new ArrayList<>();
    private final List<String> variables = new ArrayList<>();
    private final List<String> zonas = new ArrayList<>();
    private final JsonNode raiz;
    private final int horaInicial;
    private final int horaFinal;

    public ModeloOcupacion() {
        try (InputStream entrada = new ClassPathResource(RECURSO).getInputStream()) {
            raiz = new ObjectMapper().readTree(entrada);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el modelo de ocupación (" + RECURSO + ").", e);
        }
        raiz.get("variables").forEach(v -> variables.add(v.asText()));
        raiz.get("zonas").forEach(z -> zonas.add(z.asText()));
        horaInicial = raiz.get("horaInicial").asInt();
        horaFinal = raiz.get("horaFinal").asInt();
        for (JsonNode a : raiz.get("arboles")) {
            arboles.add(new Arbol(enteros(a.get("variable")), decimales(a.get("umbral")),
                    enteros(a.get("izquierda")), enteros(a.get("derecha")), decimales(a.get("valor"))));
        }
        if (variables.size() != PRIMERA_ZONA + zonas.size() || arboles.isEmpty()) {
            throw new IllegalStateException("El modelo de ocupación no tiene la estructura esperada.");
        }
    }

    /** Porcentaje de ocupación estimado (0 a 100) para un vector de variables. */
    public double predecir(double[] x) {
        double suma = 0;
        for (Arbol arbol : arboles) {
            suma += arbol.evaluar(x);
        }
        return Math.max(0, Math.min(100, suma / arboles.size()));
    }

    public int numeroDeVariables() {
        return variables.size();
    }

    public int horaInicial() {
        return horaInicial;
    }

    public int horaFinal() {
        return horaFinal;
    }

    /**
     * Posición de la zona del modelo que corresponde a una zona de la aplicación.
     * Las zonas las crea el superadministrador con texto libre, así que se
     * reconocen por el nombre; las que no se conocen cuentan como "Otra".
     */
    public int indiceDeZona(String nombreZona) {
        String n = nombreZona == null ? "" : Normalizer.normalize(nombreZona, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        String zona = n.contains("centro") || n.contains("san diego") || n.contains("amurallad") ? "Centro Historico"
                : n.contains("bocagrande") || n.contains("laguito") || n.contains("castillogrande") ? "Bocagrande"
                : n.contains("getsemani") ? "Getsemani"
                : n.contains("manga") ? "Manga"
                : "Otra";
        return zonas.indexOf(zona);
    }

    /**
     * Ocupación media que se observó en el entrenamiento para una zona, un tipo
     * de día y una hora. Hace de "semana anterior" mientras el parqueadero no
     * tenga historial propio.
     */
    public double ocupacionTipica(int indiceZona, boolean diaDeDescanso, int hora) {
        JsonNode perfil = raiz.get("perfilPorZona").get(zonas.get(indiceZona))
                .get(diaDeDescanso ? "descanso" : "laboral");
        return perfil.get(Math.max(horaInicial, Math.min(horaFinal, hora)) - horaInicial).asDouble();
    }

    /** Casos que exportó el entrenamiento para comprobar que aquí se predice igual. */
    public JsonNode casosDePrueba() {
        return raiz.get("casosDePrueba");
    }

    private static int[] enteros(JsonNode lista) {
        int[] r = new int[lista.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = lista.get(i).asInt();
        }
        return r;
    }

    private static double[] decimales(JsonNode lista) {
        double[] r = new double[lista.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = lista.get(i).asDouble();
        }
        return r;
    }
}
