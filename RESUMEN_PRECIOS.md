# Resumen: actualización masiva de precios y sincronización de ARS

Rama `feature/precios-masivos` (base `main`). Implementa la actualización masiva de precios en dólares con vista previa mediante token, lotes históricos con reversión atómica, y la sincronización automática de `Producto.precio` (ARS) al modificar la cotización, con tests rigurosos y vista de administración.

## 1. Qué quedó funcionando

- **Modelo de Precios Dual**: El precio de referencia es `Producto.precioUsd` (USD). `Producto.precio` (ARS) es un campo derivado calculado mediante `PreciosArs.desdeUsd(precioUsd, cotizacion)` (redondeo a 2 decimales, `HALF_UP`).
- **Cotización y Sincronización Automática**: Al actualizar la cotización USD/ARS en Configuración, todos los productos activos con precio USD se recalculan automáticamente. También se provee el endpoint y botón `/api/precios/recalcular-ars` para sincronización manual idempotente.
- **Lotes de Precios y Auditoría**:
  - `LotePrecios` e `LotePreciosItem`: registran la operación masiva con sus valores anteriores y posteriores (tanto en USD como en ARS), cotización usada, usuario, alcance, nota y estado (`APLICADO` o `REVERTIDO`).
  - Inmutabilidad y seguridad de lotes: un lote aplicado solo se puede revertir una vez (doble reversión retorna `409 Conflict`).
- **Actualización Masiva con Vista Previa**:
  - Alcance: `TODOS`, `CATEGORIAS` (múltiples IDs) o `PRODUCTOS` (múltiples IDs).
  - Tipos de ajuste: `PORCENTAJE` (entre -90% y 100%) o `MONTO_USD`. Redondeo configurable (`0.01`, `0.10`, `0.50`, `1.00`).
  - Validación de precios máximos: excede el máximo permitido por la columna (`numeric(10,2)` → máx `99999999.99`) excluye al producto con motivo descriptivo.
  - Alerta y confirmación de variación alta: variaciones promedio >25% exigen `confirmarVariacionAlta=true` y escribir `CONFIRMAR` en la interfaz.
  - Token SHA-256: la vista previa genera un token único que expira o se invalida si los precios cambiaron antes de aplicar (`409 Conflict`).
  - Atomicidad todo-o-nada protegida por bloqueo pesimista (`@Lock(PESSIMISTIC_WRITE)`).
- **Interfaz Web (Precios)**:
  - Panel exclusivo para administradores (`/precios`).
  - Tarjeta de estado de cotización y productos desactualizados o sin precio USD.
  - Formulario completo de ajuste masivo con vista previa en tabla antes/después y listado de excluidos.
  - Historial de lotes paginado con modal de detalle y opción de reversión.
  - Toast de confirmación al actualizar cotización en Configuración.

## 2. Archivos

### Creados

| Capa | Archivos |
|---|---|
| Modelos y repositorios | `models/LotePrecios.java`, `models/LotePreciosItem.java`, `repositories/LotePreciosRepository.java`, `repositories/LotePreciosItemRepository.java`, `util/PreciosArs.java` |
| Servicios | `services/PreciosMasivosService.java` (vista previa, SHA-256, aplicación atómica, reversión, recálculo ARS) |
| API y DTOs | `controllers/PreciosController.java`, DTOs de solicitud/respuesta (`PreciosVistaPreviaRequest`, `PreciosAplicarRequest`, `PreciosVistaPreviaResponse`, `PreciosAplicarResponse`, `PaginaLotesResponse`, `LotePreciosDetalleResponse`, `PreciosEstadoResponse`, `PreciosRevertirResponse`, `PreciosRecalcularResponse`, etc.) |
| Tests | `PreciosMasivosTest` (23), `PreciosCotizacionTest` (7), `PreciosAtomicidadTest` (2) |
| Frontend | `pages/Precios.jsx` |

### Modificados

| Archivo | Cambio |
|---|---|
| `models/Producto.java` | Sincronización de precio ARS (`sincronizarPrecioArs`, `precioUsd`, método estático o de utilidad) |
| `models/AccionAuditoria.java` | Nuevas acciones: `PRECIOS_ACTUALIZADOS_MASIVO`, `PRECIOS_REVERTIDOS`, `PRECIOS_RECALCULADOS` |
| `services/ConfiguracionService.java` | Recálculo automático de productos activos al cambiar la cotización USD/ARS |
| `services/ProductoService.java` | Uso de `PreciosArs.desdeUsd` al guardar/actualizar productos con USD |
| `controllers/ConfiguracionController.java` | Integración con recálculo masivo |
| `security/SecurityConfig.java` | Restricción de `/api/precios/**` exclusivamente al rol `ADMIN` |
| `test/java/.../SecurityRolesTest.java` | Ampliado a 34 tests incluyendo validación de roles para el controlador de precios |
| `frontend/src/App.jsx`, `components/Layout.jsx` | Ruta protegida `/precios` para `ADMIN` e item en el menú lateral |
| `frontend/pages/Configuracion.jsx` | Actualización de texto y toast informando el recálculo automático de precios en pesos |
| `README.md` | Documentación del módulo de precios masivos |

## 3. Endpoints de la API (`/api/precios/**`)

Todos requieren rol `ADMIN` (401 sin auth, 403 si no es admin).

| Método y Ruta | Descripción |
|---|---|
| `GET /api/precios/estado` | Devuelve la cotización vigente, cantidad de productos desactualizados y sin precio USD |
| `POST /api/precios/vista-previa` | Genera vista previa del ajuste masivo, token SHA-256, resumen y pedidos pendientes afectados |
| `POST /api/precios/aplicar` | Aplica de forma atómica el lote usando el token y confirmación si hay variación alta |
| `GET /api/precios/lotes` | Historial paginado de lotes (`page`, `size`) |
| `GET /api/precios/lotes/{id}` | Detalle de un lote con sus items antes/después |
| `POST /api/precios/lotes/{id}/revertir` | Revierte un lote aplicado (409 si ya estaba revertido) |
| `POST /api/precios/recalcular-ars` | Recalcula todos los precios en pesos con la cotización vigente |

## 4. Resultados de verificación

| Qué se corrió | Resultado |
|---|---|
| Tests de backend (`PreciosMasivosTest`, `PreciosCotizacionTest`, `PreciosAtomicidadTest`, `SecurityRolesTest`, etc.) | **194/194 tests exitosos (BUILD SUCCESS)** en contenedor Maven |
| Build frontend (`npm run build`, Docker `node:20-alpine`) | **OK** (dist generado correctamente) |
| Control de ramas y commits | Commits limpios con rutas explícitas en rama `feature/precios-masivos` |

## 5. Deuda y riesgos

- **Bloqueo concurrente**: Se utiliza `@Lock(PESSIMISTIC_WRITE)` en la aplicación del lote para evitar colisiones con actualizaciones simultáneas de productos.
- **Rendimiento con catálogos masivos**: Para miles de productos en un solo lote, la transacción maneja todo atómicamente; en catálogos extremos (>10,000 items) podría requerir procesamiento asincrónico por lotes (no aplicable al alcance actual).
