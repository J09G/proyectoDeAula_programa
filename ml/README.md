# Modelo predictivo de ocupación

Estima el **porcentaje de ocupación** de un parqueadero para un día y una franja
de una hora. Es el entregable de Minería de Datos I del proyecto de aula:
estructura del dataset, dataset, modelo e integración en ParkingApp.

| Archivo | Qué es |
|---|---|
| `calendario.py` | Festivos de Colombia y temporada alta (las mismas reglas están en Java) |
| `generar_dataset.py` | Genera el conjunto de datos simulado |
| `datos/ocupacion_parqueaderos.csv` | El conjunto de datos (24 616 filas) |
| `entrenar_modelo.py` | Entrena, compara y exporta el modelo |
| `graficos.py` | Figuras del análisis |
| `resultados/` | Métricas, predicciones del mes de prueba y figuras |
| `../src/main/resources/modelo/ocupacion.json` | El modelo exportado, que evalúa la aplicación |

## Cómo reproducirlo

```bash
pip install -r requirements.txt
py generar_dataset.py     # datos/ocupacion_parqueaderos.csv
py entrenar_modelo.py     # resultados/ y el modelo de la aplicación
py graficos.py            # resultados/figuras/
```

Todo usa una semilla fija (2026): los números salen iguales cada vez.

## Los datos son simulados

ParkingApp todavía no tiene historial suficiente para entrenar un modelo. El
conjunto de datos reproduce cómo se llenan ocho parqueaderos de cinco zonas de
Cartagena entre el 1 de enero y el 30 de junio de 2026, de 6:00 a 22:00, según
la zona, la hora, el tipo de día y la temporada, con ruido aleatorio y con
factores que el modelo no ve (lluvia, eventos). **Los resultados miden qué tan
bien el modelo recupera esos patrones; no son una medición de parqueaderos
reales.** La estructura es la que se obtiene de la base de datos de la
aplicación, así que el modelo puede reentrenarse con datos reales cuando existan.

## Estructura del dataset

Una fila por parqueadero, día y franja horaria.

| Columna | Tipo | Descripción | Origen en ParkingApp |
|---|---|---|---|
| `id_parqueadero` | texto | Identificador del parqueadero | `parqueaderos._id` |
| `parqueadero` | texto | Nombre | `parqueaderos.nombre` |
| `zona` | categórica | Centro Histórico, Bocagrande, Getsemaní, Manga u Otra | `zonas.nombreZona` |
| `capacidad` | entero | Cubículos totales | `parqueaderos.espaciosTotales` |
| `tarifa_hora` | entero | Tarifa por hora en pesos | `parqueaderos.tarifaHora` |
| `fecha` | fecha | Día (AAAA-MM-DD) | — |
| `hora` | entero | Inicio de la franja (6 a 22) | — |
| `dia_semana` | entero | 0 = lunes … 6 = domingo | calculada |
| `es_fin_de_semana` | 0/1 | Sábado o domingo | calculada |
| `es_festivo` | 0/1 | Festivo en Colombia | calculada (`calendario.py`) |
| `temporada_alta` | 0/1 | Semana Santa, mitad y fin de año, fiestas de noviembre | calculada |
| `reservas_aceptadas` | entero | Reservas aceptadas para esa franja | `reservas` (estado `ACEPTADA`) |
| `ocupacion_semana_anterior` | decimal | Ocupación (%) de la misma franja 7 días antes | `registros_parqueo` |
| `cubiculos_ocupados` | entero | Vehículos dentro durante la franja | `registros_parqueo` (entrada y salida) |
| **`ocupacion_pct`** | decimal | **Variable objetivo**: `cubiculos_ocupados / capacidad × 100` | calculada |

Para entrenar se descarta la primera semana (no tiene "semana anterior"), las
reservas se expresan como porcentaje de la capacidad (`reservas_pct`) y la zona
se convierte en cinco columnas 0/1.

## Resultados

Partición temporal: entrenamiento de enero a abril (15 368 filas), validación en
mayo (4 216) y prueba en junio (4 080), un mes que el modelo nunca vio.

| Modelo | MAE | RMSE | R² | Acierto de nivel |
|---|---|---|---|---|
| Promedio histórico (referencia) | 7,61 | 10,13 | 0,781 | 85,0 % |
| Regresión lineal | 8,14 | 10,48 | 0,765 | 83,3 % |
| Árbol de decisión (profundidad 12) | 7,73 | 10,04 | 0,784 | 84,6 % |
| **Bosque aleatorio** (40 árboles) | **7,40** | **9,60** | **0,803** | 84,9 % |

MAE y RMSE en puntos porcentuales de ocupación. "Nivel" es Baja (< 50 %), Media
(50–85 %) o Alta (> 85 %).

El bosque aleatorio es el que menos se equivoca, pero mejora poco a la
referencia (un 2,8 % menos de error absoluto) y no acierta más niveles. Donde
más falla es en la ocupación **alta**: de 208 franjas realmente altas en junio
sólo anticipó 95 como altas; las demás las dio como medias.

## Integración

`entrenar_modelo.py` exporta los 40 árboles a un JSON. La aplicación los evalúa
en Java (`prediccion/ModeloOcupacion`), arma las variables con sus propios datos
(`PrediccionServiceImpl`) y las publica en un servicio web:

```
GET /prediccion/dia?idParqueadero=<id>&fecha=2026-10-13
GET /prediccion/ocupacion?idParqueadero=<id>&fecha=2026-10-13T18:00
```

Una prueba (`ModeloOcupacionTest`) comprueba que Java predice lo mismo que
scikit-learn en 25 casos exportados por el entrenamiento. Si un parqueadero no
tiene registros de la semana anterior, se usa la ocupación típica de su zona y
la respuesta lo indica (`conHistorial: false`).
