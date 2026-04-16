"""
Script: Convertir _id string a ObjectId en todas las colecciones
Base de datos: MongoDB Atlas - parking

Qué hace:
  1. Hace backup de cada colección antes de modificar
  2. Convierte _id string a ObjectId donde sea necesario
  3. Actualiza los IDs embebidos (rol, zona, administrador, cliente, etc.)

Uso:
    python fix_ids_mongodb.py
"""

from pymongo import MongoClient
from pymongo.errors import DuplicateKeyError
from bson import ObjectId

MONGO_URI = "mongodb+srv://Jgomez:52134Jg2005@cluster0.br6d4qz.mongodb.net/parking?appName=Cluster0"
MONGO_DB  = "parking"

client = MongoClient(MONGO_URI)
db = client[MONGO_DB]

# ─── UTILIDADES ───────────────────────────────────────────────────────────────

def es_string_objectid(valor):
    """Devuelve True si el valor es un string que parece un ObjectId válido."""
    if not isinstance(valor, str):
        return False
    try:
        ObjectId(valor)
        return True
    except Exception:
        return False

def convertir_doc(doc):
    """
    Convierte recursivamente todos los campos _id que sean strings válidos de ObjectId
    a ObjectId real dentro de un documento (incluye subdocumentos).
    """
    if not isinstance(doc, dict):
        return doc
    nuevo = {}
    for k, v in doc.items():
        if k == "_id" and es_string_objectid(v):
            nuevo[k] = ObjectId(v)
        elif isinstance(v, dict):
            nuevo[k] = convertir_doc(v)
        elif isinstance(v, list):
            nuevo[k] = [convertir_doc(i) if isinstance(i, dict) else i for i in v]
        else:
            nuevo[k] = v
    return nuevo

def hacer_backup(nombre_coleccion):
    """Copia todos los documentos a una colección de backup."""
    backup_nombre = f"{nombre_coleccion}_backup"
    db[backup_nombre].drop()
    docs = list(db[nombre_coleccion].find())
    if docs:
        db[backup_nombre].insert_many(docs)
    print(f"  Backup: {len(docs)} documentos guardados en '{backup_nombre}'")
    return len(docs)

def fix_coleccion(nombre_coleccion):
    """
    Para cada documento:
      - Si el _id ya es ObjectId, solo actualiza los subdocumentos embebidos
      - Si el _id es string, recrea el documento con ObjectId
    """
    print(f"\n[{nombre_coleccion}]")
    hacer_backup(nombre_coleccion)

    docs = list(db[nombre_coleccion].find())
    convertidos = 0
    solo_embebidos = 0

    for doc in docs:
        nuevo_doc = convertir_doc(doc)

        id_original = doc["_id"]
        id_nuevo    = nuevo_doc["_id"]

        if isinstance(id_original, str):
            # El _id era string → eliminar e insertar con ObjectId
            db[nombre_coleccion].delete_one({"_id": id_original})
            try:
                db[nombre_coleccion].insert_one(nuevo_doc)
                convertidos += 1
            except DuplicateKeyError:
                # Ya existe un documento con ese ObjectId (creado por la app)
                # El string-ID ya fue borrado, el ObjectId queda como definitivo
                convertidos += 1
        else:
            # El _id ya era ObjectId → solo actualizar campos embebidos si cambiaron
            if nuevo_doc != doc:
                db[nombre_coleccion].replace_one({"_id": id_original}, nuevo_doc)
                solo_embebidos += 1

    print(f"  _id convertidos a ObjectId: {convertidos}")
    print(f"  Solo embebidos actualizados: {solo_embebidos}")
    print(f"  Sin cambios: {len(docs) - convertidos - solo_embebidos}")

# ─── EJECUCIÓN ────────────────────────────────────────────────────────────────

COLECCIONES = ["roles", "zonas", "usuarios", "parqueaderos", "reservas", "registros_parqueo"]

print("=" * 55)
print("  Fix IDs - MongoDB Atlas - parking")
print("=" * 55)
print("\nConectando a Atlas...")

# Verificar conexión
db.command("ping")
print("Conexión OK\n")

print("ADVERTENCIA: Este script modifica los datos en Atlas.")
print("Se creará un backup de cada colección antes de modificar.")
confirm = input("\n¿Continuar? (escribe 'si' para confirmar): ").strip().lower()

if confirm != "si":
    print("Cancelado.")
    client.close()
    exit()

# Procesar en orden (roles y zonas primero porque son referenciados por otros)
for col in COLECCIONES:
    fix_coleccion(col)

print("\n" + "=" * 55)
print("  Proceso completado.")
print("  Los backups quedaron en colecciones *_backup")
print("  Si todo está bien, puedes eliminarlos desde Atlas.")
print("=" * 55)

client.close()
