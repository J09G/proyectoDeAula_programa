"""Entrena y evalúa el modelo de ocupación y lo exporta para ParkingApp.

Compara cuatro alternativas con una partición temporal (se entrena con el
pasado y se evalúa con el último mes, que el modelo nunca vio):

  1. Promedio histórico por parqueadero, día de la semana y hora (referencia).
  2. Regresión lineal.
  3. Árbol de decisión.
  4. Bosque aleatorio.

El mejor se exporta a JSON para que la aplicación (Java) lo evalúe sin
depender de Python.

Uso:  py entrenar_modelo.py
"""
import json
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestRegressor
from sklearn.linear_model import LinearRegression
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.tree import DecisionTreeRegressor

AQUI = Path(__file__).parent
DATOS = AQUI / "datos" / "ocupacion_parqueaderos.csv"
RESULTADOS = AQUI / "resultados"
MODELO_APP = AQUI.parent / "src" / "main" / "resources" / "modelo" / "ocupacion.json"

SEMILLA = 2026
ZONAS = ["Centro Historico", "Bocagrande", "Getsemani", "Manga", "Otra"]
VARIABLES = ["hora", "dia_semana", "es_fin_de_semana", "es_festivo", "temporada_alta",
             "capacidad", "tarifa_hora", "reservas_pct", "ocupacion_semana_anterior"] \
            + ["zona_" + z for z in ZONAS]
OBJETIVO = "ocupacion_pct"
ORDEN_NIVELES = ["Baja", "Media", "Alta"]


def nivel(pct):
    """Baja (< 50 %), Media (50-85 %) o Alta (> 85 %)."""
    pct = np.asarray(pct)
    return np.where(pct > 85, "Alta", np.where(pct >= 50, "Media", "Baja"))


def preparar(df):
    df = df.dropna(subset=["ocupacion_semana_anterior"]).copy()
    df["reservas_pct"] = (100 * df["reservas_aceptadas"] / df["capacidad"]).round(2)
    for z in ZONAS:
        df["zona_" + z] = (df["zona"] == z).astype(int)
    return df


def metricas(real, predicho):
    predicho = np.clip(predicho, 0, 100)
    return {
        "MAE": round(float(mean_absolute_error(real, predicho)), 2),
        "RMSE": round(float(np.sqrt(mean_squared_error(real, predicho))), 2),
        "R2": round(float(r2_score(real, predicho)), 3),
        "acierto_nivel": round(float((nivel(real) == nivel(predicho)).mean()), 3),
    }


def exportar_arbol(arbol):
    t = arbol.tree_
    return {
        "variable": [int(v) for v in t.feature],              # -2 en las hojas
        "umbral": [round(float(u), 6) for u in t.threshold],
        "izquierda": [int(i) for i in t.children_left],
        "derecha": [int(d) for d in t.children_right],
        "valor": [round(float(v[0][0]), 3) for v in t.value],
    }


def main():
    df = preparar(pd.read_csv(DATOS))
    entreno = df[df["fecha"] < "2026-05-01"]
    validacion = df[(df["fecha"] >= "2026-05-01") & (df["fecha"] < "2026-06-01")]
    prueba = df[df["fecha"] >= "2026-06-01"]
    desarrollo = pd.concat([entreno, validacion])
    print(f"Entrenamiento {len(entreno)} | validación {len(validacion)} | prueba {len(prueba)} filas")

    # Profundidad del árbol: la que menos se equivoca en validación.
    busqueda = []
    for profundidad in (3, 4, 5, 6, 8, 10, 12, 15):
        arbol = DecisionTreeRegressor(max_depth=profundidad, min_samples_leaf=20, random_state=SEMILLA)
        arbol.fit(entreno[VARIABLES], entreno[OBJETIVO])
        busqueda.append({"profundidad": profundidad,
                         **metricas(validacion[OBJETIVO], arbol.predict(validacion[VARIABLES]))})
    mejor_profundidad = min(busqueda, key=lambda b: b["MAE"])["profundidad"]
    print(pd.DataFrame(busqueda).to_string(index=False))

    claves = ["id_parqueadero", "dia_semana", "hora"]
    promedio = desarrollo.groupby(claves)[OBJETIVO].mean().rename("pred").reset_index()
    pred_promedio = (prueba.merge(promedio, on=claves, how="left")["pred"]
                     .fillna(desarrollo[OBJETIVO].mean()).to_numpy())

    modelos = {
        "Regresión lineal": LinearRegression(),
        "Árbol de decisión": DecisionTreeRegressor(max_depth=mejor_profundidad, min_samples_leaf=20,
                                                   random_state=SEMILLA),
        "Bosque aleatorio": RandomForestRegressor(n_estimators=40, max_depth=10, min_samples_leaf=15,
                                                  random_state=SEMILLA, n_jobs=-1),
    }
    resultados = {"Promedio histórico": metricas(prueba[OBJETIVO], pred_promedio)}
    predicciones = prueba[["id_parqueadero", "parqueadero", "zona", "fecha", "hora", OBJETIVO]].copy()
    predicciones["Promedio histórico"] = np.round(pred_promedio, 1)
    for nombre, modelo in modelos.items():
        modelo.fit(desarrollo[VARIABLES], desarrollo[OBJETIVO])
        pred = np.clip(modelo.predict(prueba[VARIABLES]), 0, 100)
        resultados[nombre] = metricas(prueba[OBJETIVO], pred)
        predicciones[nombre] = np.round(pred, 1)

    print("\nResultados en el mes de prueba (junio de 2026):\n" + pd.DataFrame(resultados).T.to_string())

    elegido = min(modelos, key=lambda n: resultados[n]["MAE"])
    bosque = modelos["Bosque aleatorio"]
    importancia = dict(sorted(zip(VARIABLES, np.round(bosque.feature_importances_, 4).tolist()),
                              key=lambda par: -par[1]))
    pred_bosque = predicciones["Bosque aleatorio"].to_numpy()
    por_zona = {z: metricas(g[OBJETIVO], g["Bosque aleatorio"]) for z, g in predicciones.groupby("zona")}
    confusion = pd.crosstab(pd.Categorical(nivel(prueba[OBJETIVO]), ORDEN_NIVELES),
                            pd.Categorical(nivel(pred_bosque), ORDEN_NIVELES), dropna=False).to_numpy().tolist()

    RESULTADOS.mkdir(exist_ok=True)
    predicciones.to_csv(RESULTADOS / "predicciones_prueba.csv", index=False, encoding="utf-8")
    (RESULTADOS / "metricas.json").write_text(json.dumps({
        "filas": {"entrenamiento": len(entreno), "validacion": len(validacion), "prueba": len(prueba)},
        "busqueda_profundidad_arbol": busqueda, "profundidad_elegida": mejor_profundidad,
        "resultados_prueba": resultados, "menor_error": elegido, "modelo_exportado": "Bosque aleatorio",
        "importancia_variables": importancia, "resultados_por_zona": por_zona,
        "matriz_confusion_niveles": {"orden": ORDEN_NIVELES, "filas_real_columnas_predicho": confusion},
    }, ensure_ascii=False, indent=2), encoding="utf-8")

    # Perfil medio por zona, tipo de día y hora: la app lo usa como "semana
    # anterior" cuando un parqueadero todavía no tiene historial propio.
    con_tipo = df.assign(descanso=((df["es_fin_de_semana"] == 1) | (df["es_festivo"] == 1)).astype(int))
    perfil = {z: {("descanso" if t else "laboral"):
                  [round(float(g2[g2["hora"] == h][OBJETIVO].mean()), 1) for h in range(6, 23)]
                  for t, g2 in g.groupby("descanso")} for z, g in con_tipo.groupby("zona")}

    # Casos de comprobación: la app debe dar lo mismo que scikit-learn.
    muestra = prueba.sample(25, random_state=SEMILLA)
    casos = [{"entrada": [float(v) for v in fila], "esperado": round(float(p), 3)}
             for fila, p in zip(muestra[VARIABLES].to_numpy(), bosque.predict(muestra[VARIABLES]))]

    MODELO_APP.parent.mkdir(parents=True, exist_ok=True)
    MODELO_APP.write_text(json.dumps({
        "descripcion": "Bosque aleatorio que estima el porcentaje de ocupación de un parqueadero en una franja de una hora.",
        "entrenadoCon": "Datos simulados de 8 parqueaderos de Cartagena, enero a junio de 2026 (ml/generar_dataset.py).",
        "variables": VARIABLES, "zonas": ZONAS, "horaInicial": 6, "horaFinal": 22,
        "metricas": resultados["Bosque aleatorio"], "perfilPorZona": perfil, "casosDePrueba": casos,
        "arboles": [exportar_arbol(a) for a in bosque.estimators_],
    }, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"\nMenor error: {elegido}. Modelo exportado: {MODELO_APP.name} ({MODELO_APP.stat().st_size // 1024} KB)")
    print("Importancia:", importancia)
    print("Por zona:", por_zona)
    print("Confusión (real x predicho, Baja/Media/Alta):", confusion)


if __name__ == "__main__":
    main()
