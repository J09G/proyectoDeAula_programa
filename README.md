# ParkingApp

Sistema de gestión de parqueaderos para Cartagena: los clientes buscan parqueadero
por zona y reservan un cubículo concreto; los administradores aprueban reservas,
controlan entradas y salidas y emiten la factura; el superadministrador gestiona
zonas, administradores y el estado de todo el sistema.

Aplicación web monolítica con **Spring Boot 3.5 + Thymeleaf + MongoDB**.

---

## Tabla de contenidos

- [Requisitos](#requisitos)
- [Puesta en marcha](#puesta-en-marcha)
- [Variables de entorno](#variables-de-entorno)
- [Roles y credenciales](#roles-y-credenciales)
- [Arquitectura](#arquitectura)
- [Modelo de datos](#modelo-de-datos)
- [Reglas de negocio](#reglas-de-negocio)
- [Seguridad](#seguridad)
- [Tests](#tests)
- [Despliegue con Docker](#despliegue-con-docker)
- [Decisiones técnicas y limitaciones conocidas](#decisiones-técnicas-y-limitaciones-conocidas)

---

## Requisitos

| Herramienta | Versión |
|-------------|---------|
| JDK         | 21      |
| Maven       | incluido (`./mvnw`) |
| MongoDB     | 6+ (local o Atlas) |
| Docker      | opcional, para el stack completo |

---

## Puesta en marcha

```bash
# 1. Configura el entorno
cp .env.example .env
#    Edita .env y define al menos MONGODB_URI

# 2. Arranca MongoDB (si lo usas en local)
mongod --dbpath ./data

# 3. Lanza la aplicación
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

La aplicación queda en <http://localhost:8080>.

> En el perfil `dev` el TLS está desactivado y las cookies no exigen HTTPS.
> En `prod` ocurre lo contrario: revisa [Variables de entorno](#variables-de-entorno).

Para cargar el `.env` en la sesión antes de arrancar:

```bash
set -a && source .env && set +a && ./mvnw spring-boot:run
```

---

## Variables de entorno

Ningún secreto vive en el repositorio. Todo se lee del entorno; la plantilla
completa está en [`.env.example`](.env.example).

| Variable | Obligatoria | Por defecto | Descripción |
|----------|-------------|-------------|-------------|
| `MONGODB_URI` | sí en producción | `mongodb://localhost:27017/parking` | Cadena de conexión |
| `SPRING_PROFILES_ACTIVE` | no | `dev` | `dev` o `prod` |
| `PORT` | no | `8080` | Puerto HTTP |
| `SSL_ENABLED` | no | `true` (`false` en dev) | TLS en el backend |
| `KEYSTORE_PASSWORD` | si `SSL_ENABLED=true` | — | Contraseña del keystore |
| `BREVO_API_KEY` | no | vacía | Sin ella, los correos sólo se registran en el log |
| `BREVO_SENDER_EMAIL` | no | `no-reply@parkingapp.local` | Remitente |
| `SUPERADMIN_EMAIL` | no | `superadmin@parking.com` | Superadmin inicial |
| `SUPERADMIN_PASSWORD` | recomendada | generada al azar | Ver abajo |
| `RESERVAS_MINUTOS_TOLERANCIA` | no | `120` | Margen antes de expirar una reserva |
| `MAX_INTENTOS_LOGIN` | no | `5` | Intentos antes de bloquear la IP |
| `MINUTOS_BLOQUEO_LOGIN` | no | `30` | Duración del bloqueo |

---

## Roles y credenciales

Al arrancar por primera vez se crean los roles `SuperAdmin`, `Administrador` y
`Cliente`, y un superadministrador.

**La contraseña del superadmin no está en el código.** Si no defines
`SUPERADMIN_PASSWORD`, se genera una aleatoria y se imprime **una sola vez** en
el log de arranque:

```
===========================================================
 SUPERADMINISTRADOR CREADO
   correo     : superadmin@parking.com
   contraseña : k3Jd9-xQ2mPw8vLnR4sT
 Guárdala ahora: no se volverá a mostrar.
===========================================================
```

| Rol | Puede |
|-----|-------|
| **Cliente** | Ver zonas y parqueaderos, reservar un cubículo, comentar |
| **Administrador** | Gestionar **sus** parqueaderos: reservas, entradas, salidas, facturas |
| **SuperAdmin** | Crear administradores y zonas, habilitar/deshabilitar usuarios y parqueaderos |

---

## Arquitectura

```
com.proyecto.parking
├── config/          ParkingProperties (parámetros de negocio), DataInitializer
├── controller/      Capa web: sólo HTTP, validación de formularios y modelo
├── dto/             Formularios con Bean Validation
├── exception/       Excepciones de dominio + GlobalExceptionHandler
├── model/           Documentos de MongoDB
├── repository/      Spring Data MongoDB
├── scheduler/       Jobs programados (expiración de reservas)
├── security/        Spring Security, principal, filtros, bloqueo por IP
└── service/         Reglas de negocio (interfaz + impl)
```

Reglas que sigue el código:

- **Los controladores no deciden nada.** Validan el formulario, llaman al
  servicio y eligen la vista. Toda regla de negocio vive en `service/`.
- **La autorización está en el servicio, no sólo en la URL.** Los métodos del
  panel de administrador reciben el id del administrador y comprueban la
  propiedad del parqueadero. Un `requestMatcher` protege la ruta; la
  comprobación de propiedad protege el dato.
- **Inyección por constructor**, con campos `final`.
- **Las excepciones de dominio son tipadas**: `ReglaNegocioException` (mensaje
  apto para el usuario), `RecursoNoEncontradoException` (404) y
  `AccesoDenegadoException` (403). Cualquier otra se registra completa y al
  usuario le llega un mensaje genérico.

---

## Modelo de datos

| Colección | Índices |
|-----------|---------|
| `usuarios` | `correo` único, `cedula` único, `placa` único disperso |
| `roles` | `nombre` único |
| `zonas` | `nombreZona` único |
| `parqueaderos` | `zona`, `administrador` |
| `reservas` | `(parqueadero, estado)`, `(cliente, estado)`, `(parqueadero, espacio, estado)` |
| `registros_parqueo` | `(parqueadero, estado)`, `(placa, estado)`, `(usuario, estado)` |

Los índices se crean solos (`spring.data.mongodb.auto-index-creation: true`).

> **Aviso al migrar datos existentes:** si tu base de datos ya tiene correos,
> cédulas o placas duplicadas, la creación del índice único fallará al arrancar.
> Limpia los duplicados antes:
> ```js
> db.usuarios.aggregate([
>   {$group: {_id: "$correo", n: {$sum: 1}, ids: {$push: "$_id"}}},
>   {$match: {n: {$gt: 1}}}
> ])
> ```

### Estados de una reserva

```
          ┌──────────────┐
          │  PENDIENTE   │  el cliente la solicita (no consume cupo)
          └──────┬───────┘
      ┌──────────┴──────────┐
      ▼                     ▼
┌───────────┐         ┌───────────┐
│ ACEPTADA  │         │ RECHAZADA │  (final)
└─────┬─────┘         └───────────┘
      │ consume 1 cupo
      ├──────────────► UTILIZADA   el cliente llegó (final)
      └──────────────► EXPIRADA    no llegó; el job devuelve el cupo (final)
```

---

## Reglas de negocio

**Cupos.** Una reserva consume cupo al **aceptarse**, no al solicitarse. El cupo
vuelve al inventario cuando: el vehículo sale, la reserva se elimina estando
aceptada, o el job de expiración la caduca.

**Expiración.** `ExpiracionReservasJob` corre cada 15 minutos (configurable) y
caduca las reservas aceptadas cuya hora de llegada pasó hace más de
`RESERVAS_MINUTOS_TOLERANCIA`.

**Cobro.** Se factura el proporcional de horas a la tarifa vigente **en el
momento de la entrada**, con un mínimo de una hora. La tarifa se congela en el
registro (`tarifaHoraAplicada`) para que un cambio de precio no altere lo que se
cobra a un coche que ya estaba dentro. El cálculo usa `BigDecimal`.

**Cubículos.** Un cubículo no se puede asignar dos veces: se comprueba contra las
reservas aceptadas y contra los registros activos, tanto al reservar como al
registrar la entrada.

---

## Seguridad

| Medida | Dónde |
|--------|-------|
| Contraseñas con BCrypt (`DelegatingPasswordEncoder`) | `PasswordEncoderConfig` |
| CSRF activo en todos los formularios | `SecurityConfig` |
| Autorización por rol en las rutas | `SecurityConfig` |
| **Comprobación de propiedad por recurso** | `ParqueaderoService.obtenerParqueaderoDeAdministrador` |
| Revalidación de la cuenta en cada petición | `EstadoCuentaFilter` |
| Bloqueo por IP tras N intentos fallidos | `LoginAttemptService` |
| Cabeceras CSP, HSTS, Referrer-Policy, Permissions-Policy | `SecurityConfig` |
| Validación en servidor de todos los formularios | `dto/` + `@Valid` |
| Mensajes de login que no revelan si un correo existe | `LoginFailureHandler` |
| Los errores internos no se muestran al usuario | `GlobalExceptionHandler` |

### Rotar el certificado

El `keystore.p12` incluido es **autofirmado y de desarrollo**; su clave privada
está en el repositorio, así que no autentica nada. Para producción, o bien
terminas TLS en nginx y arrancas con `SSL_ENABLED=false`, o generas uno propio:

```bash
keytool -genkeypair -alias parking -keyalg RSA -keysize 4096 \
        -storetype PKCS12 -keystore keystore.p12 -validity 365
```

---

## Tests

```bash
./mvnw test
```

65 tests unitarios sobre la lógica que puede costar dinero o abrir un agujero:

| Clase | Qué fija |
|-------|----------|
| `CalculoTarifaTest` | Tarificación: hora mínima, proporcional, redondeo |
| `ParqueaderoServiceImplTest` | Comprobación de propiedad, cupos atómicos, capacidad |
| `ReservaServiceImplTest` | Ciclo de vida de la reserva, compensación de cupo, expiración |
| `RegistroParqueoServiceImplTest` | Entradas, cubículos ocupados, tarifa congelada |
| `RegistroClienteFormTest` | Validación del registro (contraseñas, placa, cédula) |

`ParkingApplicationTests` levanta el contexto completo y **sólo se ejecuta si
hay una base de datos**:

```bash
MONGODB_URI=mongodb://localhost:27017/parking-test ./mvnw test
```

---

## Despliegue con Docker

```bash
cp .env.example .env     # define MONGODB_URI y el resto
docker compose up --build
```

- `backend`: la aplicación Spring Boot
- `frontend`: nginx como proxy inverso (puertos 80 y 443)

El `docker-compose.yml` no contiene credenciales: todo sale de `.env`, que está
en `.gitignore`.

---

## Decisiones técnicas y limitaciones conocidas

**Sin `@Transactional`.** Las transacciones de MongoDB exigen un replica set, y
un `mongod` local en modo standalone no lo es: anotar los servicios haría fallar
la aplicación en desarrollo. En su lugar, las operaciones que tocan dos
documentos usan actualizaciones atómicas (`$inc` condicionado) y compensan a mano
si el segundo paso falla. Está comentado en el código donde ocurre. Si el
despliegue garantiza un replica set, se puede añadir un `MongoTransactionManager`
y anotar `aceptarReserva` y `registrarEntrada`.

**Bloqueo de login en memoria.** `LoginAttemptService` guarda el estado en un
`ConcurrentHashMap`. Con varias réplicas, cada una lleva su propia cuenta. Para
escalar habría que moverlo a Redis.

**Job sin bloqueo distribuido.** Con varias instancias, todas ejecutarían la
expiración de reservas a la vez. Las operaciones son idempotentes, pero lo
correcto sería añadir ShedLock.

**CSP con `unsafe-inline`.** Las plantillas aún usan `style="..."` y
`onclick="..."`. Mover eso a archivos `.css`/`.js` permitiría endurecer la
política.

**El buscador por placa filtra sólo la página actual.** El historial está
paginado en servidor y el filtro de la tabla es JavaScript del lado del cliente.

---

## Documentación adicional

- [Historias de usuario](docs/historias-de-usuario.md)
- [Diseño multiparqueadero](docs/superpowers/specs/2026-05-14-admin-multiparqueadero-design.md)
