"""
Migración: objetos embebidos completos → DBRef
Colecciones afectadas:
  - usuarios        : rol
  - parqueaderos    : zona, registradoPor, administrador
  - reservas        : cliente, parqueadero
  - registros_parqueo: usuario, parqueadero, reserva
"""

from pymongo import MongoClient
from bson import DBRef, ObjectId

MONGO_URI = "mongodb+srv://Jgomez:52134Jg2005@cluster0.br6d4qz.mongodb.net/parking?appName=Cluster0"
DB_NAME   = "parking"

client = MongoClient(MONGO_URI)
db     = client[DB_NAME]


def to_dbref(coleccion, doc_embebido):
    """Convierte un objeto embebido a DBRef usando su _id."""
    if doc_embebido is None:
        return None
    if isinstance(doc_embebido, DBRef):
        return doc_embebido  # ya es DBRef, no tocar
    raw_id = doc_embebido.get("_id")
    if raw_id is None:
        return doc_embebido
    oid = raw_id if isinstance(raw_id, ObjectId) else ObjectId(str(raw_id))
    return DBRef(coleccion, oid)


def migrar_usuarios():
    col = db["usuarios"]
    total = actualizados = 0
    for doc in col.find():
        total += 1
        updates = {}
        if isinstance(doc.get("rol"), dict):
            updates["rol"] = to_dbref("roles", doc["rol"])
        if updates:
            col.update_one({"_id": doc["_id"]}, {"$set": updates})
            actualizados += 1
    print(f"usuarios      → {actualizados}/{total} actualizados")


def migrar_parqueaderos():
    col = db["parqueaderos"]
    total = actualizados = 0
    for doc in col.find():
        total += 1
        updates = {}
        if isinstance(doc.get("zona"), dict):
            updates["zona"] = to_dbref("zonas", doc["zona"])
        if isinstance(doc.get("registradoPor"), dict):
            updates["registradoPor"] = to_dbref("usuarios", doc["registradoPor"])
        if isinstance(doc.get("administrador"), dict):
            updates["administrador"] = to_dbref("usuarios", doc["administrador"])
        if updates:
            col.update_one({"_id": doc["_id"]}, {"$set": updates})
            actualizados += 1
    print(f"parqueaderos  → {actualizados}/{total} actualizados")


def migrar_reservas():
    col = db["reservas"]
    total = actualizados = 0
    for doc in col.find():
        total += 1
        updates = {}
        if isinstance(doc.get("cliente"), dict):
            updates["cliente"] = to_dbref("usuarios", doc["cliente"])
        if isinstance(doc.get("parqueadero"), dict):
            updates["parqueadero"] = to_dbref("parqueaderos", doc["parqueadero"])
        if updates:
            col.update_one({"_id": doc["_id"]}, {"$set": updates})
            actualizados += 1
    print(f"reservas      → {actualizados}/{total} actualizados")


def migrar_registros_parqueo():
    col = db["registros_parqueo"]
    total = actualizados = 0
    for doc in col.find():
        total += 1
        updates = {}
        if isinstance(doc.get("usuario"), dict):
            updates["usuario"] = to_dbref("usuarios", doc["usuario"])
        if isinstance(doc.get("parqueadero"), dict):
            updates["parqueadero"] = to_dbref("parqueaderos", doc["parqueadero"])
        if isinstance(doc.get("reserva"), dict):
            updates["reserva"] = to_dbref("reservas", doc["reserva"])
        if updates:
            col.update_one({"_id": doc["_id"]}, {"$set": updates})
            actualizados += 1
    print(f"registros_parqueo → {actualizados}/{total} actualizados")


if __name__ == "__main__":
    print("=== Iniciando migración a DBRef ===")
    migrar_usuarios()
    migrar_parqueaderos()
    migrar_reservas()
    migrar_registros_parqueo()
    print("=== Migración completada ===")
    client.close()
