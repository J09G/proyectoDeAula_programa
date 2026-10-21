"""Figuras del análisis (para el informe). Uso:  py graficos.py"""
import json
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd

AQUI = Path(__file__).parent
FIGURAS = AQUI / "resultados" / "figuras"

# Paleta categórica validada (orden fijo) y tintas neutras.
SERIES = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4"]
TINTA, TINTA_2, REJILLA = "#0b0b0b", "#52514e", "#e4e3df"
ZONAS = ["Centro Historico", "Bocagrande", "Getsemani", "Manga", "Otra"]
NOMBRES = {"Centro Historico": "Centro Histórico", "Getsemani": "Getsemaní"}

plt.rcParams.update({
    "font.family": "DejaVu Sans", "font.size": 9, "axes.edgecolor": REJILLA, "axes.labelcolor": TINTA_2,
    "xtick.color": TINTA_2, "ytick.color": TINTA_2, "axes.spines.top": False, "axes.spines.right": False,
    "axes.grid": True, "grid.color": REJILLA, "grid.linewidth": 0.6, "axes.axisbelow": True,
    "axes.titlesize": 10, "axes.titleweight": "bold", "axes.titlecolor": TINTA, "figure.dpi": 200,
})


def guardar(fig, nombre):
    FIGURAS.mkdir(parents=True, exist_ok=True)
    fig.savefig(FIGURAS / nombre, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print("ok", nombre)


def perfil_por_zona(df):
    df = df.assign(descanso=(df["es_fin_de_semana"] == 1) | (df["es_festivo"] == 1))
    fig, ejes = plt.subplots(1, 2, figsize=(7.2, 3.0), sharey=True)
    for eje, (descanso, titulo) in zip(ejes, [(False, "Días laborales"), (True, "Fines de semana y festivos")]):
        medias = df[df["descanso"] == descanso].groupby(["zona", "hora"])["ocupacion_pct"].mean().unstack(0)
        for zona, color in zip(ZONAS, SERIES):
            eje.plot(medias.index, medias[zona], color=color, linewidth=2, label=NOMBRES.get(zona, zona))
        eje.set_title(titulo, loc="left")
        eje.set_xlabel("Hora del día")
        eje.set_xticks(range(6, 23, 2))
        eje.set_ylim(0, 100)
        eje.grid(axis="x", visible=False)
    ejes[0].set_ylabel("Ocupación media (%)")
    ejes[1].legend(frameon=False, fontsize=8, loc="upper left", bbox_to_anchor=(1.0, 1.0))
    guardar(fig, "fig1_perfil_por_zona.png")


def comparacion_modelos(metricas):
    resultados = metricas["resultados_prueba"]
    nombres = list(resultados)
    mae = [resultados[n]["MAE"] for n in nombres]
    fig, eje = plt.subplots(figsize=(5.6, 2.6))
    barras = eje.barh(nombres[::-1], mae[::-1], color=SERIES[0], height=0.55)
    eje.bar_label(barras, fmt="%.2f", padding=4, color=TINTA, fontsize=9)
    eje.set_xlabel("Error absoluto medio en junio (puntos porcentuales; menos es mejor)")
    eje.set_xlim(0, max(mae) * 1.15)
    eje.grid(axis="y", visible=False)
    eje.tick_params(axis="y", colors=TINTA, length=0)
    guardar(fig, "fig2_comparacion_modelos.png")


def real_vs_predicho(pred):
    semana = pred[(pred["id_parqueadero"] == "P01") & (pred["fecha"] >= "2026-06-08") & (pred["fecha"] <= "2026-06-14")].copy()
    semana["momento"] = pd.to_datetime(semana["fecha"]) + pd.to_timedelta(semana["hora"], unit="h")
    fig, eje = plt.subplots(figsize=(7.2, 2.8))
    for fecha, dia in semana.groupby("fecha"):   # un trazo por día: de noche no hay datos
        primero = fecha == semana["fecha"].min()
        eje.plot(dia["momento"], dia["ocupacion_pct"], color=SERIES[0], linewidth=2, label="Ocupación real" if primero else None)
        eje.plot(dia["momento"], dia["Bosque aleatorio"], color=SERIES[1], linewidth=2, label="Predicción del modelo" if primero else None)
    eje.set_ylim(0, 100)
    eje.set_ylabel("Ocupación (%)")
    eje.set_xticks(pd.to_datetime(sorted(semana["fecha"].unique())) + pd.Timedelta(hours=14))
    eje.set_xticklabels(["lun 8", "mar 9", "mié 10", "jue 11", "vie 12", "sáb 13", "dom 14"])
    eje.grid(axis="x", visible=False)
    eje.legend(frameon=False, fontsize=8, ncol=2, loc="lower left", bbox_to_anchor=(0, 1.0))
    guardar(fig, "fig3_real_vs_predicho.png")


def importancia(metricas):
    etiquetas = {
        "ocupacion_semana_anterior": "Ocupación de la semana anterior", "reservas_pct": "Reservas aceptadas",
        "hora": "Hora del día", "dia_semana": "Día de la semana", "es_fin_de_semana": "Fin de semana",
        "es_festivo": "Festivo", "capacidad": "Capacidad", "tarifa_hora": "Tarifa por hora",
        "temporada_alta": "Temporada alta",
    }
    imp = metricas["importancia_variables"]
    filas = [(etiquetas[k], v) for k, v in imp.items() if k in etiquetas]
    filas.append(("Zona (las cinco)", sum(v for k, v in imp.items() if k.startswith("zona_"))))
    filas.sort(key=lambda f: f[1])
    fig, eje = plt.subplots(figsize=(5.6, 3.2))
    barras = eje.barh([f[0] for f in filas], [100 * f[1] for f in filas], color=SERIES[0], height=0.6)
    eje.bar_label(barras, fmt="%.1f %%", padding=4, color=TINTA, fontsize=8)
    eje.set_xlabel("Importancia en el bosque aleatorio (%)")
    eje.set_xlim(0, 100)
    eje.grid(axis="y", visible=False)
    eje.tick_params(axis="y", colors=TINTA, length=0)
    guardar(fig, "fig4_importancia_variables.png")


if __name__ == "__main__":
    datos = pd.read_csv(AQUI / "datos" / "ocupacion_parqueaderos.csv")
    metricas = json.loads((AQUI / "resultados" / "metricas.json").read_text(encoding="utf-8"))
    predicciones = pd.read_csv(AQUI / "resultados" / "predicciones_prueba.csv")
    perfil_por_zona(datos)
    comparacion_modelos(metricas)
    real_vs_predicho(predicciones)
    importancia(metricas)
