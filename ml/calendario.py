"""Calendario de Colombia: festivos (Ley 51 de 1983) y temporada turística alta.

Las mismas reglas están en Java (CalendarioColombia) para que la aplicación
calcule estas dos variables igual que el conjunto de datos de entrenamiento.
"""
from datetime import date, timedelta


def domingo_de_pascua(anio: int) -> date:
    """Algoritmo de Butcher-Meeus para el calendario gregoriano."""
    a = anio % 19
    b, c = divmod(anio, 100)
    d, e = divmod(b, 4)
    f = (b + 8) // 25
    g = (b - f + 1) // 3
    h = (19 * a + b - d - g + 15) % 30
    i, k = divmod(c, 4)
    l = (32 + 2 * e + 2 * i - h - k) % 7
    m = (a + 11 * h + 22 * l) // 451
    mes, dia = divmod(h + l - 7 * m + 114, 31)
    return date(anio, mes, dia + 1)


def _al_lunes(d: date) -> date:
    """Los festivos "trasladables" se celebran el lunes siguiente si no caen en lunes."""
    return d + timedelta(days=(7 - d.weekday()) % 7)


def festivos(anio: int) -> set:
    pascua = domingo_de_pascua(anio)
    fijos = [(1, 1), (5, 1), (7, 20), (8, 7), (12, 8), (12, 25)]
    trasladables = [(1, 6), (3, 19), (6, 29), (8, 15), (10, 12), (11, 1), (11, 11)]
    dias = {date(anio, m, d) for m, d in fijos}
    dias |= {_al_lunes(date(anio, m, d)) for m, d in trasladables}
    dias |= {pascua - timedelta(days=3), pascua - timedelta(days=2)}      # Jueves y Viernes Santo
    dias |= {pascua + timedelta(days=n) for n in (43, 64, 71)}            # Ascensión, Corpus, Sagrado Corazón
    return dias


def es_festivo(d: date) -> bool:
    return d in festivos(d.year)


def es_temporada_alta(d: date) -> bool:
    """Temporadas de mayor llegada de visitantes a Cartagena."""
    pascua = domingo_de_pascua(d.year)
    if pascua - timedelta(days=7) <= d <= pascua:                         # Semana Santa
        return True
    if (d.month == 12 and d.day >= 15) or (d.month == 1 and d.day <= 15):  # fin de año
        return True
    if (d.month == 6 and d.day >= 15) or (d.month == 7 and d.day <= 15):   # vacaciones de mitad de año
        return True
    if d.month == 11 and 8 <= d.day <= 17:                                # fiestas de la Independencia
        return True
    return False
