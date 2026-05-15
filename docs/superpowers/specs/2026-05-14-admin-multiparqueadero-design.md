# Diseño: Admin multi-parqueadero + cubículos seleccionables

**Fecha:** 2026-05-14  
**Rama:** version-2  
**Base de datos:** parkingapp_v2 (nueva, limpia)

---

## Contexto

El sistema anterior asumía que cada administrador gestiona exactamente un parqueadero, registrado por el SuperAdmin. Este diseño rompe esa restricción: el admin registra sus propios parqueaderos, puede tener varios, y el cliente selecciona un cubículo específico al reservar.

---

## Decisiones de negocio

- El admin es dueño/gestor de sus propios parqueaderos. Los registra él mismo.
- El SuperAdmin ya no registra parqueaderos.
- Al reservar, el cliente escoge el cubículo específico. Ese espacio queda bloqueado inmediatamente.
- Las entradas directas (cliente de paso) también requieren asignar un cubículo específico.

---

## 1. Cambios de modelo de datos

### Parqueadero
- **Eliminar** campo `registradoPor` (`@DBRef Usuario`) — redundante en el nuevo modelo.
- `ParqueaderoRepository.findByAdministrador_Id` cambia de `Parqueadero` a `List<Parqueadero>`.

### Reserva
- **Agregar** campo `int espacioReservado` — número del cubículo escogido por el cliente.

### RegistroParqueo
- **Agregar** campo `int espacioReservado` — número del cubículo asignado al momento de la entrada.

---

## 2. Nueva base de datos

Cambiar en `application.properties`:

```
spring.data.mongodb.database=parkingapp_v2
```

La BD arranca vacía. No hay documentos con `registradoPor` desde el inicio.

---

## 3. Estructura de rutas del panel admin

El panel admin se reorganiza con routing por parqueadero (Enfoque 2).

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/admin` | Lista de parqueaderos del admin + formulario para registrar nuevo |
| GET | `/admin/parqueadero/{id}` | Panel de gestión del parqueadero específico |
| POST | `/admin/parqueadero/{id}/editar` | Editar info del parqueadero |
| POST | `/admin/parqueadero/{id}/espacios` | Actualizar espacios totales/disponibles |
| POST | `/admin/parqueadero/{id}/reserva/aceptar/{rid}` | Aceptar reserva |
| POST | `/admin/parqueadero/{id}/reserva/rechazar/{rid}` | Rechazar reserva |
| POST | `/admin/parqueadero/{id}/reserva/eliminar/{rid}` | Eliminar reserva |
| POST | `/admin/parqueadero/{id}/registros/entrada` | Registrar entrada de vehículo |
| POST | `/admin/parqueadero/{id}/registros/salida/{rid}` | Registrar salida |
| GET | `/admin/parqueadero/{id}/registros` | Ver registros del parqueadero |
| GET | `/admin/parqueadero/{id}/registros/factura/{rid}` | Ver factura |
| GET | `/admin/parqueadero/{id}/registros/factura/pdf/{rid}` | Descargar factura PDF |
| POST | `/admin/registrarParqueadero` | Registrar nuevo parqueadero (sin cambio de ruta) |

### Vista `/admin` (lista)
- Muestra cards de todos los parqueaderos del admin.
- Cada card tiene: nombre, zona, espacios disponibles/totales, estado, botón "Gestionar" → `/admin/parqueadero/{id}`.
- Incluye botón/formulario "Registrar nuevo parqueadero" (expandible o sección al final).

### Vista `/admin/parqueadero/{id}` (gestión)
- Equivalente al `admin/index.html` actual pero recibe el `id` del parqueadero por ruta.
- Secciones: info editable, grid de cubículos, reservas pendientes, buscar reserva por cédula.
- Botón "← Mis parqueaderos" para volver a `/admin`.

---

## 4. Lógica de cubículos seleccionables

### Cálculo de espacios bloqueados

Al cargar cualquier formulario con grid interactivo, el backend calcula:

```
espaciosOcupados = 
  { reserva.espacioReservado | reserva.parqueadero == id AND reserva.estado IN [PENDIENTE, ACEPTADA] }
  UNION
  { registro.espacioReservado | registro.parqueadero == id AND registro.horaSalida == null }
```

Se pasa al template como `Set<Integer> espaciosOcupados`.

### Formulario de reserva (cliente)

- Grid interactivo renderizado con Thymeleaf (`#numbers.sequence`).
- Cubículos en `espaciosOcupados` → clase `ocupado`, no clickeables.
- Cubículos libres → clase `libre`, clickeables.
- Al hacer clic en un libre, un campo `<input type="hidden" name="espacioReservado">` recibe el número.
- El formulario no se puede enviar sin haber seleccionado un espacio (validación HTML5 + JS mínimo).

### Formulario de entrada directa (admin)

- Mismo grid interactivo en la vista de registros del parqueadero.
- Admin ingresa la placa y selecciona el cubículo disponible.
- El campo hidden `espacioReservado` se envía junto con la placa.

---

## 5. Cambios en panel SuperAdmin

- **Eliminar** formulario de registro de parqueaderos del panel SuperAdmin.
- **Eliminar** el endpoint correspondiente en `SuperAdminController`.
- El SuperAdmin conserva: ver todos los parqueaderos, habilitar/deshabilitar, gestionar zonas, gestionar usuarios.

---

## Archivos principales afectados

| Archivo | Cambio |
|---------|--------|
| `application.properties` | Cambiar nombre de BD |
| `model/Parqueadero.java` | Eliminar `registradoPor` |
| `model/Reserva.java` | Agregar `espacioReservado` |
| `model/RegistroParqueo.java` | Agregar `espacioReservado` |
| `repository/ParqueaderoRepository.java` | `findByAdministrador_Id` retorna `List<Parqueadero>` |
| `controller/AdminController.java` | Reescribir con nuevas rutas `/admin/parqueadero/{id}/*` |
| `controller/SuperAdminController.java` | Eliminar endpoints de registro de parqueadero |
| `service/ReservaService.java` | Método para obtener espacios ocupados por parqueadero |
| `service/RegistroParqueoService.java` | Método para obtener registros activos por parqueadero |
| `templates/admin/index.html` | Reemplazar por vista de lista de parqueaderos |
| `templates/admin/parqueadero.html` | Nueva vista de gestión por parqueadero |
| `templates/superadmin/*.html` | Quitar sección de registro de parqueadero |
| `templates/cliente/reserva.html` | Grid de cubículos interactivo |
| `templates/admin/registros.html` | Grid de cubículos interactivo para entrada |
| `static/css/admin.css` | Estilos para cubículos clickeables |
| `static/js/app.js` | Lógica de selección de cubículo (campo hidden) |
