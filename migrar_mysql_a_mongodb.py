"""
Script de migracion: MySQL (parking_sm4) -> MongoDB Atlas (parking)

Requisitos (instalar antes de correr):
    pip install mysql-connector-python pymongo

Uso:
    python migrar_mysql_a_mongodb.py
"""

import mysql.connector
from pymongo import MongoClient
from bson import ObjectId

# ─── CONFIGURACION ────────────────────────────────────────────────────────────

MYSQL = {
    "host": "localhost",
    "port": 3306,
    "database": "parking_sm4",
    "user": "root",
    "password": "52134",
}

MONGO_URI = "mongodb+srv://Jgomez:52134Jg2005@cluster0.br6d4qz.mongodb.net/parking?appName=Cluster0"
MONGO_DB  = "parking"

# ─── CONEXIONES ───────────────────────────────────────────────────────────────

print("Conectando a MySQL...")
mysql_conn = mysql.connector.connect(**MYSQL)
cursor = mysql_conn.cursor(dictionary=True)

print("Conectando a MongoDB Atlas...")
mongo_client = MongoClient(MONGO_URI)
db = mongo_client[MONGO_DB]

# ─── 1. ROLES ─────────────────────────────────────────────────────────────────

db['registros'].drop()
print("Coleccion registros limpiada")

print("\n[1/5] Migrando roles...")
cursor.execute("SELECT * FROM rol")
roles_mysql = cursor.fetchall()

db.roles.drop()

roles_map = {}
roles_doc = {}

for r in roles_mysql:
    oid = str(ObjectId())
    roles_map[r["idRol"]] = oid
    doc = {"_id": oid, "nombre": r["nombre"]}
    roles_doc[r["idRol"]] = doc
    db.roles.insert_one(doc.copy())

print(f"  OK {len(roles_mysql)} roles migrados")

# ─── 2. ZONAS ─────────────────────────────────────────────────────────────────

print("\n[2/5] Migrando zonas...")
cursor.execute("SELECT * FROM zona")
zonas_mysql = cursor.fetchall()

db.zonas.drop()

zonas_map = {}
zonas_doc = {}

for z in zonas_mysql:
    oid = str(ObjectId())
    zonas_map[z["idZona"]] = oid
    doc = {
        "_id": oid,
        "nombreZona": z["nombre_zona"],
        "habilitado": True,
    }
    zonas_doc[z["idZona"]] = doc
    db.zonas.insert_one(doc.copy())

print(f"  OK {len(zonas_mysql)} zonas migradas")

# ─── 3. USUARIOS ──────────────────────────────────────────────────────────────

print("\n[3/5] Migrando usuarios...")
cursor.execute("SELECT * FROM usuario")
usuarios_mysql = cursor.fetchall()

db.usuarios.drop()

usuarios_map = {}
usuarios_doc = {}

for u in usuarios_mysql:
    oid = str(ObjectId())
    usuarios_map[u["idUsuario"]] = oid

    rol_embebido = roles_doc.get(u["idRol"])

    doc = {
        "_id": oid,
        "nombre": u["nombre"],
        "correo": u["correo"],
        "contrasena": u["contrasena"],
        "cedula": u["cedula"],
        "placa": u.get("placa"),
        "habilitado": True,
        "rol": rol_embebido,
    }
    usuarios_doc[u["idUsuario"]] = doc
    db.usuarios.insert_one(doc.copy())

print(f"  OK {len(usuarios_mysql)} usuarios migrados")

# ─── 4. PARQUEADEROS ──────────────────────────────────────────────────────────

print("\n[4/5] Migrando parqueaderos...")
cursor.execute("SELECT * FROM parqueadero")
parqueaderos_mysql = cursor.fetchall()

db.parqueaderos.drop()

parqueaderos_map = {}
parqueaderos_doc = {}

for p in parqueaderos_mysql:
    oid = str(ObjectId())
    parqueaderos_map[p["idParqueadero"]] = oid

    zona_emb       = zonas_doc.get(p["id_zona"])
    registrado_emb = usuarios_doc.get(p["registrado_por"])
    admin_emb      = usuarios_doc.get(p["id_administrador"])

    doc = {
        "_id": oid,
        "nombre": p["nombre"],
        "direccion": p["direccion"],
        "horario": p["horario"],
        "tarifaHora": float(p["tarifa_hora"]),
        "espaciosTotales": p["espacios_totales"],
        "espaciosDisponibles": p["espacios_disponibles"],
        "urlMaps": p.get("url_maps"),
        "telefono": p.get("telefono"),
        "habilitado": bool(p["habilitado"]),
        "zona": zona_emb,
        "registradoPor": registrado_emb,
        "administrador": admin_emb,
    }
    parqueaderos_doc[p["idParqueadero"]] = doc
    db.parqueaderos.insert_one(doc.copy())

print(f"  OK {len(parqueaderos_mysql)} parqueaderos migrados")

# ─── 5. RESERVAS ──────────────────────────────────────────────────────────────

print("\n[5/5] Migrando reservas...")
cursor.execute("SELECT * FROM reserva")
reservas_mysql = cursor.fetchall()

db.reservas.drop()

for r in reservas_mysql:
    cliente_emb     = usuarios_doc.get(r["idCliente"])
    parqueadero_emb = parqueaderos_doc.get(r["idParqueadero"])

    # Las reservas ACEPTADA en MySQL ya fueron procesadas en el sistema anterior
    # Se importan como UTILIZADA para no interferir con el nuevo flujo
    estado = "UTILIZADA" if r["estado"] == "ACEPTADA" else r["estado"]

    db.reservas.insert_one({
        "_id": str(ObjectId()),
        "estado": estado,
        "cliente": cliente_emb,
        "parqueadero": parqueadero_emb,
    })

print(f"  OK {len(reservas_mysql)} reservas migradas")

# ─── CIERRE ───────────────────────────────────────────────────────────────────

cursor.close()
mysql_conn.close()
mongo_client.close()

print("\nMigracion completada exitosamente.")
print(f"   Roles:        {len(roles_mysql)}")
print(f"   Zonas:        {len(zonas_mysql)}")
print(f"   Usuarios:     {len(usuarios_mysql)}")
print(f"   Parqueaderos: {len(parqueaderos_mysql)}")
print(f"   Reservas:     {len(reservas_mysql)}")
