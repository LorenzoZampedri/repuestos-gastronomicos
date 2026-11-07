# Resumen: logs de auditoría

Rama `feature/logs` (base `main`). Agrega un registro inmutable de acciones y
accesos, consultable y exportable solo por `ADMIN`, sin cambiar el comportamiento
ni los permisos existentes del resto del sistema.

## 1. Qué quedó funcionando

- Cada acción relevante guarda: fecha, usuario (id, username y rol), acción,
  módulo, entidad afectada, resumen, detalle en JSON, resultado, IP y user agent.
- La tabla `logs_actividad` es **inmutable desde la aplicación**: no tiene setters
  públicos ni endpoints de edición o borrado.
- Se registran:
  - Autenticación: login correcto, login fallido, cambio/reseteo de contraseña.
  - Usuarios: creación, edición, activación/desactivación y vinculación de cliente.
  - Productos: creación, edición, eliminación, ajuste de stock, imagen y categorías.
  - Clientes: creación, edición, eliminación y ajuste de saldo.
  - Ventas: creación, anulación, pagos y pagos a cuenta corriente.
  - Pedidos: creación, cancelación, aprobación (conversión) y rechazo.
  - Configuración: edición general y datos de transferencia (antes/después).
  - Seguridad: accesos denegados por rol.
- El panel **Logs** (solo `ADMIN`) permite filtrar por rango de fechas, usuario,
  rol, módulo, acción, resultado y texto libre; pagina en el servidor y muestra el
  detalle completo en un modal.
- Exportación CSV con los filtros activos, hasta 5000 filas, con BOM UTF-8 y
  protección contra fórmulas de Excel/Sheets.
- Los logs se escriben en la zona horaria del backend; en Docker se fijó
  `TZ=America/Argentina/Buenos_Aires`.

## 2. Archivos

### Creados

| Capa | Archivos |
|---|---|
| Modelo / repositorio | `models/AccionAuditoria.java`, `models/LogActividad.java`, `repositories/LogActividadRepository.java` |
| Servicios | `services/AuditoriaService.java` (registro, política por rol, diff, redacción, truncado, IP/UA), `services/LogsService.java` (consulta paginada, filtros y CSV) |
| API | `controllers/LogsController.java` (`GET /api/logs`, `/{id}`, `/export`, `/filtros`), DTOs `LogActividadResponse`, `PaginaLogsResponse`, `LogsFiltros` |
| Tests | `AuditoriaServiceTest` (9), `LogsServiceTest` (7), `AuditoriaFlujosTest` (6) |
| Frontend | `pages/Logs.jsx` |

### Modificados

| Archivo | Cambio |
|---|---|
| `services/UsuarioService.java` | logs de alta, edición, estado, contraseña y vinculación de cliente |
| `services/ProductoService.java`, `controllers/ProductoController.java` | logs de producto, stock, imagen y categorías |
| `services/ClienteService.java`, `controllers/CategoriaController.java` | logs de cliente y categorías |
| `services/VentaService.java` | `crearVentaBase` compartido; una sola `VENTA_CREADA` (incluye pago inicial y origen de pedido); `PAGO_REGISTRADO`, `PAGO_CUENTA_CORRIENTE` y `VENTA_ANULADA` sin duplicados |
| `services/PedidoService.java` | logs de creación, cancelación, conversión y rechazo con actor explícito |
| `services/ConfiguracionService.java` | `CONFIG_EDITADA` solo con cambios reales; `CONFIG_TRANSFERENCIA_EDITADA` con diff antes/después |
| `controllers/AuthController.java` | `LOGIN_OK` y `LOGIN_FALLIDO` (usuario inactivo o credenciales inválidas) |
| `security/SecurityConfig.java` | `ACCESO_DENEGADO` en el handler de 403 y `/api/logs/**` solo `ADMIN` |
| Tests existentes | `@Import(AuditoriaService.class)` donde se construyen servicios por test; `SecurityRolesTest` suma `/api/logs` y logs de login |
| `frontend/src/App.jsx`, `components/Layout.jsx` | ruta `/logs` protegida para `ADMIN` y acceso en el menú lateral |
| `docker-compose.yml` | `TZ: America/Argentina/Buenos_Aires` en el backend |
| `README.md` | sección de logs, permisos y documentación |

## 3. API y filtros

| Endpoint | Descripción |
|---|---|
| `GET /api/logs` | Página de logs. Parámetros: `page` (0-based), `size` (default 20, máximo 100), `desde`, `hasta`, `usuario`, `rol`, `modulo`, `accion`, `resultado`, `q` |
| `GET /api/logs/{id}` | Detalle de un log (404 si no existe) |
| `GET /api/logs/export` | CSV con los mismos filtros (hasta 5000 filas). Headers `X-Logs-Filas` y `X-Logs-Truncados` |
| `GET /api/logs/filtros` | Módulos, acciones y roles disponibles para los selects |

`q` busca coincidencias en resumen y detalle. El orden es siempre fecha
descendente y, ante empate, id descendente.

## 4. Decisiones tomadas

- **Política centralizada por rol** en `AuditoriaService`: un `CLIENTE` solo puede
  generar `PEDIDO_CREADO` y `PEDIDO_CANCELADO`; sin actor no se registra nada,
  salvo `LOGIN_FALLIDO`. El intento de login fallido de un usuario existente con
  rol `CLIENTE` no se registra para no filtrar si la cuenta existe.
- **Logging best-effort**: `REQUIRED` para acciones exitosas y `REQUIRES_NEW` para
  `LOGIN_FALLIDO`/`ACCESO_DENEGADO`. Cualquier error al auditar se captura y no
  interrumpe la operación de negocio.
- **Accesos denegados con throttle** de 60 segundos por usuario/IP/ruta para evitar
  inundar la tabla cuando la UI reintenta.
- **Sin foreign keys** en usuario/entidad: el log debe sobrevivir a bajas o
  renombres.
- **IP confiable solo del proxy interno**: se usa `X-Real-IP` únicamente cuando
  `getRemoteAddr()` es una IP privada/Docker; se ignora `X-Forwarded-For`.
- **Truncado defensivo**: resumen máximo 500, user agent 255 y detalle de hasta
  20000 caracteres (si se excede se guarda `{"truncado": true}`).
- **Exportación acotada** a 5000 filas para no bloquear la respuesta; la UI avisa
  cuando el resultado quedó truncado.
- **Cálculo de diff** antes/después para configuración y datos de transferencia,
  incluyendo etiquetas legibles de los campos.

## 5. Resultados de verificación

| Qué se corrió | Resultado |
|---|---|
| Suite backend completa (`mvn test`, Docker `maven:3.9-eclipse-temurin-17`) | **158 tests, 0 fallos, 0 errores** (19 clases) |
| Build frontend (`npm run build`, Docker `node:20-alpine`) | **OK** (solo advertencia de tamaño de chunk >500 kB) |
| Arranque real (`docker compose up -d --build backend frontend`) | **OK**; backend y frontend recreados |
| Smoke API | Login `admin` → `200`, genera `LOGIN_OK`; `GET /api/logs` → `200` con paginación; `/filtros` → 8 módulos y 30 acciones; `/export` → `200 text/csv` con BOM, `X-Logs-Filas` y `X-Logs-Truncados` |
| Zona horaria | Log de login guardado a las **23:49** local mientras el contenedor estaba en **02:49 UTC**: `TZ` aplicada |
| Permisos | Cubiertos por `SecurityRolesTest`: `ADMIN` accede a `/api/logs`; `EMPLEADO` recibe `403` y se registra `ACCESO_DENEGADO` |

## 6. Deuda y riesgos

- **Sin suite E2E completa de la UI**: se verificó build y smoke de la API; falta
  recorrer filtros, paginación, modal y descarga desde un navegador.
- **Sin retención ni particionado**: la tabla crece indefinidamente. Si el volumen
  lo requiere, conviene una política de archivo/particionado por fecha.
- **Exportación en memoria**: el CSV se arma como string y se corta en 5000 filas;
  para exportaciones mayores habría que usar streaming.
- **IP detrás de un proxy externo**: el backend está expuesto directo en
  `docker-compose`. Si se publica detrás de otro proxy, hay que validar que
  `X-Real-IP` se reescriba y que el backend no sea accesible saltando el proxy.
- **La auditoría no es a prueba de un administrador con acceso directo a la base**:
  la inmutabilidad es a nivel de aplicación. Para un requisito más fuerte haría
  falta un append-only log o permisos de DB restringidos.
- **Sin logs de lectura**: solo se auditan acciones de negocio y seguridad; no
  cada consulta a la API.
