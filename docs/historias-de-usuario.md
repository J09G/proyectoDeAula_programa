# Historias de Usuario — Sistema de Parqueaderos

> Ultima actualizacion: 2026-10-07

| ID | Historia de Usuario |
|----|---------------------|
| RF001 | Como cliente nuevo quiero registrarme ingresando mi nombre, cédula, placa, correo y contraseña para acceder al sistema y realizar reservas de parqueaderos. |
| RF002 | Como usuario registrado quiero iniciar sesión con mi correo y contraseña para acceder a mi panel según el rol asignado. |
| RF003 | Como cliente autenticado quiero ver las diferentes zonas habilitadas donde hay parqueaderos registrados para elegir una zona y consultar los parqueaderos disponibles. |
| RF004 | Como cliente autenticado quiero visualizar los parqueaderos disponibles dentro de una zona específica para escoger uno donde pueda realizar una reserva. |
| RF005 | Como cliente autenticado quiero realizar una reserva en un parqueadero seleccionando un cubículo específico para asegurarme de tener ese espacio disponible al llegar. |
| RF006 | Como cliente autenticado quiero ver el listado de mis reservas realizadas para conocer su estado y detalles. |
| RF007 | Como administrador quiero acceder a mi panel de control para ver y gestionar todos mis parqueaderos registrados. |
| RF008 | Como administrador quiero modificar el nombre, dirección, horario, tarifa y enlace de ubicación de un parqueadero para mantener actualizada su información. |
| RF009 | Como administrador quiero actualizar el número total de espacios de un parqueadero para reflejar cambios en su capacidad. |
| RF0010 | Como administrador quiero buscar reservas por número de cédula para localizarlas rápidamente. |
| RF0011 | Como administrador quiero aceptar o rechazar una reserva pendiente para confirmar la disponibilidad al cliente. |
| RF0012 | Como administrador quiero eliminar reservas del sistema para mantener la base de datos actualizada. |
| RF0013 | Como administrador de parqueadero quiero registrar la entrada de un vehículo ingresando su placa y opcionalmente la cédula del cliente para llevar un control de los vehículos activos y descontar el espacio disponible automáticamente. |
| RF0014 | Como administrador de parqueadero quiero registrar la salida de un vehículo y que el sistema calcule el tiempo de permanencia y el valor a pagar para generar el cobro correspondiente según la tarifa del parqueadero. |
| RF0015 | Como administrador de parqueadero quiero visualizar y descargar la factura de un registro de parqueo en formato PDF para entregar al cliente un comprobante físico o digital del servicio prestado. |
| RF0016 | Como superadministrador quiero acceder al panel general del sistema para gestionar zonas, administradores y parqueaderos registrados. |
| RF0017 | Como superadministrador quiero registrar nuevos administradores en el sistema para que cada uno pueda gestionar sus propios parqueaderos. |
| RF0018 | Como administrador quiero registrar mis propios parqueaderos en el sistema para incorporarlos y gestionarlos desde mi panel. |
| RF0019 | Como superadministrador quiero crear nuevas zonas y habilitar o deshabilitar las existentes para organizar la distribución geográfica de los parqueaderos. |
| RF0020 | Como superadministrador quiero visualizar y editar la información de los usuarios para mantener los datos actualizados. |
| RF0021 | Como superadministrador quiero habilitar o deshabilitar usuarios o parqueaderos para controlar su acceso y disponibilidad sin eliminar información histórica. |
| RF0022 | Como superadministrador quiero buscar parqueaderos por nombre, zona o cédula del administrador para filtrar resultados específicos rápidamente. |
| RF0023 | Como usuario quiero que el sistema valide mis credenciales y verifique que mi cuenta esté activa para acceder de forma segura, siendo bloqueado si mi cuenta está deshabilitada. |
| RF0024 | Como sistema quiero restringir funcionalidades según el rol del usuario para proteger operaciones administrativas. |
| RF0025 | Como usuario autenticado quiero cerrar sesión para finalizar mi acceso de manera segura. |
| RF0026 | Como usuario quiero visualizar un mensaje de error 403 cuando intente acceder a una funcionalidad sin permisos para saber que no estoy autorizado. |

## Agregadas en el periodo 2026-2

Funcionalidades nuevas del aplicativo web. La columna **Planeación** indica la
historia de `planeacion-scrum-v9.docx` en la que se construyó cada una.

| ID | Historia de Usuario | Planeación |
|----|---------------------|------------|
| RF0027 | Como usuario quiero que mi sesión viaje en un token JWT guardado en una cookie segura del navegador para no depender de sesiones en el servidor, y que si mi cuenta se deshabilita o mi rol cambia, el acceso se ajuste de inmediato aunque el token no haya vencido. | US-01 |
| RF0028 | Como usuario quiero iniciar sesión con mi cuenta de Google para entrar sin crear ni recordar otra contraseña; si mi correo ya está registrado, se usa la misma cuenta, y si soy nuevo, quedo registrado como cliente. | US-03 |
| RF0029 | Como cliente que entró con Google quiero completar mi cédula y mi placa la primera vez que voy a reservar para poder usar el flujo normal de reservas. | US-04 |
| RF0030 | Como usuario que olvidó su contraseña quiero recibir por correo un enlace para crear una nueva, que venza a los 30 minutos y sirva una sola vez, para recuperar mi cuenta de forma segura. | US-05 |
| RF0031 | Como cliente quiero cancelar una reserva pendiente o aceptada que ya no voy a usar para liberar el cupo; si estaba aceptada, el espacio vuelve a quedar disponible. | US-06 |
| RF0032 | Como usuario autenticado quiero editar mi nombre, mi correo y mi contraseña desde mi perfil; cambiar el correo o la contraseña exige mi contraseña actual para que nadie se apodere de mi cuenta desde una sesión abierta. | US-08 |
| RF0033 | Como cliente quiero actualizar mi placa y mi cédula desde mi perfil; la placa se cambia libremente y la cédula exige mi contraseña actual. | US-08 |
| RF0034 | Como usuario quiero cambiar entre tema claro y oscuro, y que el sistema recuerde mi elección, para usarlo cómodamente. | US-09 |
| RF0035 | Como usuario quiero cambiar el idioma de la interfaz entre español e inglés para usar el sistema en el idioma que prefiera. | — |
| RF0036 | Como administrador o superadministrador quiero que los listados largos (usuarios, parqueaderos, reservas y registros) se muestren por páginas, conservando las búsquedas, para que el sistema siga siendo rápido a medida que crecen los datos. | US-10 |
| RF0037 | Como administrador quiero tener en una página de configuración los datos de mi parqueadero, su capacidad, su estado y su ubicación, separada del panel donde atiendo las reservas. | US-10 |
| RF0038 | Como administrador quiero marcar en un mapa la ubicación de la entrada de mi parqueadero para que los clientes lo encuentren en el mapa. | US-11 |
| RF0039 | Como cliente quiero ver en un mapa los parqueaderos habilitados, con su tarifa y espacios libres, para elegir el más cercano a donde voy y reservar desde ahí. | US-11 |
| RF0040 | Como cliente quiero ver el historial de mis parqueos (entrada, salida, tiempo, valor y estado) y descargar la factura en PDF de los finalizados, sin poder ver los de otros clientes. | US-12 |
