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
- [Despliegue en Render](#despliegue-en-render)
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

La aplicación queda en <http://localhost:8081>.

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
| `PORT` | no | `8081` | Puerto HTTP |
| `JWT_SECRET` | sí en `prod` | secreto de desarrollo | Firma de los JWT. En `prod` la app no arranca sin él. Generar con `openssl rand -base64 48` |
| `JWT_EXPIRATION_MS` | no | `1800000` (30 min) | Vigencia del JWT |
| `APP_URL` | sí en producción | `http://localhost:8081` | URL pública; con ella se arman los enlaces de los correos |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | para el login con Google | `no-configurado` | Credenciales OAuth de Google Cloud |
| `SSL_ENABLED` | no | `false` en `dev` y `prod` | TLS en el propio backend. En Render debe quedar en `false` |
| `KEYSTORE_PATH` / `KEYSTORE_PASSWORD` | si `SSL_ENABLED=true` | — | Certificado propio y su contraseña (ver "HTTPS en local") |
| `COOKIE_SEGURA` | no | `true` (`false` en `dev`) | Atributo `Secure` de la cookie JWT |
| `BREVO_API_KEY` | para enviar correos | vacía | Sin ella, los correos sólo se registran en el log |
| `BREVO_SENDER_EMAIL` | sí si hay `BREVO_API_KEY` | `no-reply@parkingapp.local` | Remitente. Debe estar **verificado en Brevo**; si no, Brevo acepta el envío pero no lo entrega |
| `BREVO_SENDER_NAME` | no | `ParkingApp` | Nombre visible del remitente |
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

### HTTPS en local

El repositorio **no incluye ningún certificado ni llave privada**. En producción
(Render) el HTTPS lo pone el proxy de la plataforma y la app habla HTTP
(`SSL_ENABLED=false`, el valor por defecto). Si quieres probar HTTPS en tu
equipo, genera un certificado autofirmado **fuera del repositorio**:

```bash
keytool -genkeypair -alias parking -keyalg RSA -keysize 4096 \
        -storetype PKCS12 -keystore C:/certs/parking.p12 -validity 365
```

y arranca con `SSL_ENABLED=true`, `KEYSTORE_PATH=file:C:/certs/parking.p12` y
`KEYSTORE_PASSWORD=<la que elegiste>`.

> Hasta octubre de 2026 el repositorio llevaba un `keystore.p12` de desarrollo,
> y su contraseña quedó en commits antiguos. Era un certificado autofirmado que
> no se usaba en producción ni protegía nada; se retiró en vez de reescribir el
> historial de git.

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

## Despliegue en Render

Demo: <https://proyectodeaula-programa.onrender.com>

El servicio despliega solo cada `push` a la rama configurada (unos 3 minutos).

**Variables mínimas:** `SPRING_PROFILES_ACTIVE=prod`, `MONGODB_URI` (Atlas),
`JWT_SECRET`, `APP_URL` (la URL de arriba), `GOOGLE_CLIENT_ID`,
`GOOGLE_CLIENT_SECRET`, `BREVO_API_KEY` y `BREVO_SENDER_EMAIL`.

**HTTPS.** Lo pone el proxy de Render; la app habla HTTP plano detrás de él
(`SSL_ENABLED=false`). Con `server.forward-headers-strategy: native` Tomcat lee
las cabeceras `X-Forwarded-*` del proxy, así sabe que el usuario llegó por
`https://` (Google exige esa misma *redirect URI*) y cuál es su IP real.

**Google.** En Google Cloud Console, la *redirect URI* autorizada debe ser
`https://proyectodeaula-programa.onrender.com/login/oauth2/code/google`.

**Brevo.** La primera vez que Render envía un correo, Brevo bloquea la IP nueva
y manda un aviso a la cuenta: hay que autorizarla. Render sale a internet por
varias IP (panel del servicio > *Connect* > *Outbound*); conviene autorizarlas
todas en Brevo > Seguridad > IP autorizadas.

### Limitaciones del plan gratuito

- **El servidor se duerme.** Tras unos 15 minutos sin visitas, Render apaga la
  instancia. La siguiente visita la vuelve a encender y **tarda alrededor de un
  minuto** en responder; después funciona con normalidad. Antes de una demo,
  abrir la URL un par de minutos antes.
- **Recursos limitados** (512 MB de RAM, CPU compartida): pensado para pruebas,
  no para carga real.
- **Sin estado entre reinicios** en memoria: al dormirse se pierde el conteo de
  intentos de login fallidos (`LoginAttemptService`). Los datos viven en Atlas y
  no se ven afectados.
- **Brevo gratuito:** 300 correos al día. Con remitente `@gmail.com`, algunos
  proveedores pueden mandar los correos a spam.

---

## Despliegue con Docker

```bash
cp .env.example .env     # define MONGODB_URI y el resto
docker compose up --build
```

Levanta un solo contenedor con la aplicación, construido con el mismo
`Dockerfile` que usa Render, y queda en <http://localhost:8080> (o el `PORT`
de tu `.env`). `MONGODB_URI` y `JWT_SECRET` son obligatorios.

No hay nginx: en Render el HTTPS lo pone su propio proxy y en local se usa
HTTP. La configuración anterior de Railway (nginx hacia su red interna) se
retiró.

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
