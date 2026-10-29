"""Genera el conjunto de datos de ocupación por parqueadero, día y franja horaria.

ParkingApp todavía no tiene historial suficiente para entrenar un modelo, así
que los datos se SIMULAN: se reproduce cómo se llenan los parqueaderos de
Cartagena según la zona, la hora, el día y la temporada, con ruido aleatorio.
La estructura es la misma que se obtiene de la base de datos real
(registros_parqueo, reservas y parqueaderos), de modo que el modelo podrá
reentrenarse con datos reales cuando existan.

Uso:  py generar_dataset.py
"""
from datetime import date, timedelta
from pathlib import Path

import numpy as np
import pandas as pd

from calendario import es_festivo, es_temporada_alta

SEMILLA = 2026
INICIO, FIN = date(2026, 1, 1), date(2026, 6, 30)
HORAS = range(6, 23)  # franjas de 6:00 a 22:00
SALIDA = Path(__file__).parent / "datos" / "ocupacion_parqueaderos.csv"

# (id, nombre, zona, capacidad, tarifa por hora, atractivo propio)
PARQUEADEROS = [
    ("P01", "Parqueadero Torre del Reloj", "Centro Historico", 60, 6000, 1.05),
    ("P02", "Parqueadero San Diego", "Centro Historico", 35, 5500, 0.95),
    ("P03", "Parqueadero Avenida San Martin", "Bocagrande", 80, 5000, 1.00),
    ("P04", "Parqueadero El Laguito", "Bocagrande", 45, 4500, 0.90),
    ("P05", "Parqueadero Calle de la Media Luna", "Getsemani", 30, 5000, 1.05),
    ("P06", "Parqueadero Centenario", "Getsemani", 50, 4500, 0.92),
    ("P07", "Parqueadero Manga Centro", "Manga", 40, 3500, 1.00),
    ("P08", "Parqueadero Pie de la Popa", "Otra", 55, 3000, 0.95),
]


def _campana(hora, centro, ancho, altura):
    return altura * np.exp(-((hora - centro) ** 2) / (2 * ancho ** 2))


def perfil(zona: str, hora: int, descanso: bool) -> float:
    """Fracción de ocupación esperada en un día normal, según la zona."""
    if zona == "Centro Historico":      # oficinas y comercio de día, turismo por la tarde-noche
        return 0.12 + _campana(hora, 11, 2.2, 0.55 if not descanso else 0.35) + _campana(hora, 19, 2.0, 0.50 if not descanso else 0.72)
    if zona == "Bocagrande":            # playa y comercio: sube al mediodía, más en días de descanso
        return 0.15 + _campana(hora, 13, 3.2, 0.50 if not descanso else 0.78) + _campana(hora, 20, 1.6, 0.22)
    if zona == "Getsemani":             # vida nocturna
        return 0.10 + _campana(hora, 12, 2.5, 0.22) + _campana(hora, 21, 2.3, 0.62 if not descanso else 0.85)
    if zona == "Manga":                 # oficinas y clínicas: horario laboral
        return 0.10 + _campana(hora, 10, 3.0, 0.62 if not descanso else 0.20) + _campana(hora, 16, 2.0, 0.30 if not descanso else 0.12)
    return 0.10 + _campana(hora, 9, 2.5, 0.40 if not descanso else 0.25) + _campana(hora, 17, 2.5, 0.40 if not descanso else 0.30)


def generar() -> pd.DataFrame:
    rng = np.random.default_rng(SEMILLA)
    dias = [INICIO + timedelta(days=n) for n in range((FIN - INICIO).days + 1)]
    turisticas = {"Centro Historico", "Bocagrande", "Getsemani"}
    filas = []

    for pid, nombre, zona, capacidad, tarifa, atractivo in PARQUEADEROS:
        for d in dias:
            festivo = es_festivo(d)
            fin_semana = d.weekday() >= 5
            alta = es_temporada_alta(d)
            # Lo que el modelo NO puede ver: lluvia, eventos, cierres de vías...
            choque_dia = rng.normal(1.0, 0.09)
            llueve = rng.random() < 0.18
            ruido_previo = 0.0
            for hora in HORAS:
                esperado = perfil(zona, hora, fin_semana or festivo) * atractivo
                if alta:
                    esperado *= 1.22 if zona in turisticas else 1.05
                # Las reservas se hacen antes: reflejan la demanda prevista del día,
                # no la lluvia ni lo que termine pasando en cada hora.
                demanda_prevista = float(np.clip(esperado * choque_dia, 0, 1))
                esperado *= choque_dia * (0.88 if llueve else 1.0)
                # El ruido de una hora arrastra parte del de la anterior.
                ruido = 0.6 * ruido_previo + rng.normal(0, 0.045)
                ruido_previo = ruido
                fraccion = float(np.clip(esperado + ruido, 0, 1))
                ocupados = int(round(fraccion * capacidad))
                reservas = int(rng.poisson(0.18 * demanda_prevista * capacidad))
                filas.append({
                    "id_parqueadero": pid, "parqueadero": nombre, "zona": zona,
                    "capacidad": capacidad, "tarifa_hora": tarifa,
                    "fecha": d.isoformat(), "hora": hora,
                    "dia_semana": d.weekday(),              # 0 = lunes ... 6 = domingo
                    "es_fin_de_semana": int(fin_semana), "es_festivo": int(festivo),
                    "temporada_alta": int(alta),
                    "reservas_aceptadas": reservas,
                    "cubiculos_ocupados": ocupados,
                    "ocupacion_pct": round(100 * ocupados / capacidad, 1),
                })

    df = pd.DataFrame(filas)
    # Ocupación de la misma franja una semana antes (la primera semana no la tiene).
    anterior = df[["id_parqueadero", "fecha", "hora", "ocupacion_pct"]].copy()
    anterior["fecha"] = (pd.to_datetime(anterior["fecha"]) + pd.Timedelta(days=7)).dt.strftime("%Y-%m-%d")
    anterior = anterior.rename(columns={"ocupacion_pct": "ocupacion_semana_anterior"})
    df = df.merge(anterior, on=["id_parqueadero", "fecha", "hora"], how="left")
    columnas = [c for c in df.columns if c not in ("cubiculos_ocupados", "ocupacion_pct")]
    return df[columnas + ["cubiculos_ocupados", "ocupacion_pct"]]


if __name__ == "__main__":
    datos = generar()
    SALIDA.parent.mkdir(parents=True, exist_ok=True)
    datos.to_csv(SALIDA, index=False, encoding="utf-8")
    print(f"{len(datos)} filas, {datos['id_parqueadero'].nunique()} parqueaderos, "
          f"{datos['fecha'].min()} a {datos['fecha'].max()} -> {SALIDA}")
    print(datos.describe().loc[["mean", "min", "max"], ["ocupacion_pct", "reservas_aceptadas"]].round(1))
