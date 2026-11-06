# Resumen: pedidos online del cliente

Rama `feature/pedidos` (base `f7d6f08`). Permite que un cliente armé su carrito, haga un
pedido, el equipo lo revise y lo convierta en venta con un clic, sin afectar stock ni saldo
hasta que se convierte.

## 1. Qué quedó funcionando

- Un usuario **CLIENTE** siempre tiene 1 ficha de `Cliente` vinculada (idempotente).
- El cliente ve **"Hacer pedido"** desde su carrito → captura nombre, celular y mensaje.
- El cliente ve **"Mis pedidos"** con estado, detalle, saldo y acción de **Volver a comprar**
  (rellena el carrito con lo que pidió).
- El empleado/admin ve **"Pedidos"**: lista de pendientes con filtros, detalle con la ficha
  del cliente (nombre, celulares, zona, vendedor, email y estado de cuenta) y las acciones
  **Convertir en venta**, **Rechazar** y **Ajustar** (cantidad y precio por ítem).
- **Convertir en venta** genera la venta, registra el pago (según medio elegido), descuenta
  stock y marca el pedido como convertido — todo en la misma transacción, una sola vez.
- Badge con el conteo de pedidos pendientes en la barra pública.
- Página **Usuarios** (admin): muestra qué cliente tiene vinculado cada usuario y permite
  vincular/desvincular a mano.

## 2. Archivos

### Creados

| Capa | Archivos |
|---|---|
| Modelo / repositorio | `models/Pedido.java`, `models/DetallePedido.java`, `repositories/PedidoRepository.java` |
| API | `controllers/PedidoController.java` (`POST /api/pedidos`, `GET /api/pedidos/{id}`, listados y acciones), `config/ClienteUsuarioMigration.java` (al arrancar, sin flyway) |
| DTOs | `PedidoCrearRequest`, `PedidoItemRequest`, `PedidoResponse`, `PedidoDetalleResponse`, `PedidoContactoResponse`, `ClientePedidoResponse`, `PedidoConvertirRequest`, `PedidoRechazarRequest`, `PedidoAjusteRequest`, `VincularClienteRequest` |
| Servicios | `services/PedidoService.java` (alta, estados, ajustes y conversión transaccional) |
| Tests | `PedidoServiceTest`, `ClienteUsuarioMigrationTest`, `ClienteVinculacionTest`, `VentaAnonimaTest` |
| Frontend | `pages/Pedidos.jsx`, `pages/MisPedidos.jsx`, `components/PedidoModal.jsx` |

### Modificados

| Archivo | Cambio |
|---|---|
| `services/VentaService.java` | valida que `CUENTA_CORRIENTE` exija cliente registrado (400 en vez de venta anónima con saldo) |
| `services/ClienteService.java` | `vincularUsuario` / `desvincularUsuario` con las reglas de fase 6 |
| `services/UsuarioService.java` | crea la ficha de cliente al registrar/crear CLIENTE, con email/username idempotentes |
| `controllers/UsuarioController.java` | `POST /api/usuarios/{id}/cliente` y `DELETE /api/usuarios/{id}/cliente` (ADMIN) |
| `security/SecurityConfig.java` | reglas de `/api/pedidos` y `/api/clientes/{id}/saldo` según roles |
| `models/Usuario.java`, `repositories/UsuarioRepository.java` | relaciones `clienteAsociado` |
| `repositories/VentaRepository.java`, `PagoRepository.java` | `existsBy…Cliente_Id` para decidir la baja de la ficha anterior |
| `dto/UsuarioResponse.java` | `tieneClienteVinculado` para que el admin sepa si falta vincular |
| `dto/ClienteResponse.java`, `dto/ClientePedidoResponse.java` | `saldoCorriente` solo para ADMIN |
| `db/init.sql` | tablas `pedidos` y `detalle_pedido` (idempotente) |
| `config/DataInitializer.java` | empleado de prueba con `CLIENTE` sin duplicar al reiniciar |
| Frontend: `App.jsx`, `PublicLayout.jsx`, `Layout.jsx`, `CartContext.jsx`, `Usuarios.jsx`, `VentaRapida.jsx` | rutas, badge, sesión, acciones de cliente y manejo del error 400 por venta anónima |
| `README.md` | flujo de pedidos, tabla venta anónima vs con cliente y permisos |

## 3. Resultados de verificación

| Qué se corrió | Resultado |
|---|---|
| Suite backend (`mvn -B test`, Docker `maven:3.9-eclipse-temurin-17`) | **119 tests, 0 fallos, 0 errores** |
| Build frontend (`npm run build`, Docker `node:20-alpine`) | **OK** |
| Suite E2E contra el stack levantado (`docker compose up -d --build`) | **58 PASS / 0 FAIL** |
| Arranque sobre una base que ya tenía clientes | Migración ejecuta y reporta 1 usuario CLIENTE vinculado; al reiniciar reporta 0 pendientes (idempotente) |

## 4. Criterios de la sección 8

| # | Criterio | Evidencia | Resultado |
|---|---|---|---|
| 1 | Registrarse como cliente crea la ficha y no genera duplicados | E2E: 4 clientes creados con 4 usuarios distintos; login CLIENTE devuelve ficha con email del usuario | **OK** |
| 2 | Hacer pedido sin tocar stock ni saldo | E2E: stock de un producto y saldo del cliente sin cambios tras el pedido; `GET /api/pedidos/{id}` con estado `PENDIENTE` | **OK** |
| 3 | Convertir a cuenta corriente: 1 venta, saldo a favor, pedido PENDIENTE | E2E: `clienteSaldo=0` (total pagado de la deuda) y `saldoCorriente = −total` del pedido; pedido `PENDIENTE` | **OK** |
| 4 | Convertir en efectivo: 1 venta, pago, stock descontado, PAGADA, pedido PAGADO | E2E: venta `PAGADA`, saldo `0`, stock −1, y el pago figura en el historial del cliente | **OK** |
| 5 | Nunca hay venta anónima a cuenta corriente | E2E: `400` con el mensaje "Para vender a cuenta corriente tenés que seleccionar un cliente registrado"; efectivo anónimo crea venta `PAGADA` sin tocar saldos | **OK** |
| 6 | Un cliente no ve ni convierte pedidos de otro (IDOR) | E2E: logueado como cliente2, `GET /api/pedidos/{id}` ajeno → `404`; como cliente1 → `200` | **OK** |
| 7 | No CLIENTE no accede a Mis pedidos, Pedidos ni a decidir la conversión | E2E: `GET /api/pedidos` con ADMIN → `401` (pantalla para admin), con un JWT de cliente → `200`; sin token → `401`; `POST /convertir` con cliente → `403` | **OK** |
| 8 | La app corre con la base existente y sin registrar clientes ni ventas de más | Migración idempotente (1 → 0 pendientes); solo 1 venta creada en cada conversión (la segunda devuelve `409`) | **OK** |

Comprobaciones extra: `POST /api/pedidos` sin token → `401`; usuario `CLIENTE` sin ficha
recibe una automáticamente en el login; segunda conversión → `409` con `YA_CONVERTIDO` y
sin venta ni cobro adicionales; rechazar devuelve el stock descontado por el pedido.

## 5. Decisiones tomadas

- **Modal en el carrito, no página `/checkout`**: la spec describe un modal en el mismo
  drawer de carrito (5.1). Se eliminó la página `/checkout` que existía a medias.
- **Fase 6 — "si el anterior tiene ventas o pagos, rechazar"**: se implementó como
  **rechazo con `409`**, dejando la ficha anterior intacta, en lugar de darla de baja
  (da de baja solo si no tiene historial).
- **Cliente anónimo**: el error de venta anónima a cuenta corriente queda en la API
  (`VentaService`), así que también protege al Venta Rápida y a los clientes HTTP de a pie.
- **Badge de pendientes**: se recalcula al montar la app, en cada cambio de ruta y con el
  evento `pedidos-actualizados`; sin polling.

## 6. Deuda y riesgos

- **La conversión es atómica por diseño (bloqueo pesimista `SELECT … FOR UPDATE`) pero no
  tiene test de concurrencia real**: el `409` de doble conversión se prueba secuencialmente.
  Para simular concurrencia habría que subir el cliente H2 con `FOR UPDATE` (o correr dos
  hilos contra la base de Postgres del stack).
- **Datos de prueba en la base local**: la suite E2E dejó usuarios `cliente.e2e.*` y
  `empleado.e2e.*`, pedidos y ventas en la DB de desarrollo de Docker. Para arrancar limpio:
  `docker compose down -v` y levantar de nuevo.
- **Notificación**: no hay push ni polling de estado; si el admin rechaza el pedido, el
  cliente se entera al entrar (spec: "por ahora lo mismo" que lo del WhatsApp). El botón
  "Avisar por WhatsApp" abre wa.me y el usuario escribe a mano (sin API de WhatsApp).
- **Saldo oculto para EMPLEADO**: coincide con la matriz de permisos (el saldo es de ADMIN),
  pero implica que un empleado no ve la deuda de su cliente al preparar el pedido.
- **`Vendedor` es fijo en el pedido** (se toma de la ficha al momento del alta); si cambia
  después, el pedido no lo sigue.

## 7. Compra con método de pago, aprobación de solo lectura y datos de transferencia

Esta fase cambia el checkout del cliente y la aprobación del panel:

- El cliente elige **Efectivo**, **Tarjeta** o **Transferencia** al comprar. La API
  rechaza `CUENTA_CORRIENTE`, `OTRO`, vacío o valores inventados con `400`
  *"Método de pago no disponible para compras online"*.
- El pedido guarda `metodoPago` (columna nullable para los pedidos viejos).
- El panel `/pedidos` es **solo lectura**: no se editan cantidades, no se quitan
  líneas y no se elige método. Aprobar usa el método elegido por el cliente y
  registra la venta `PAGADA`; rechazar exige motivo.
- Los pedidos anteriores sin método no se pueden aprobar (`409` con mensaje
  explicativo); solo se rechazan. La UI oculta el botón de aprobar y muestra la
  nota.
- La venta a `CUENTA_CORRIENTE` queda restringida a **ADMIN**. Un EMPLEADO recibe
  `403` por API y la opción no aparece en Venta Rápida.
- Los datos de transferencia (CVU, alias y titular) se siembran como claves de
  configuración solo si no existen y se editan desde **Configuración** en un único
  acordeón cerrado por defecto. `GET /api/configuracion/publica` los expone en
  `datosTransferencia`; el frontend los muestra en checkout, éxito y Mis pedidos
  con botones para copiar CVU/alias.

### Archivos nuevos de esta fase

| Capa | Archivos |
|---|---|
| Backend | `dto/DatosTransferenciaRequest.java`, `dto/DatosTransferenciaResponse.java` |
| Backend tests | `services/ConfiguracionTransferenciaTest.java` |
| Frontend | `components/DatosTransferencia.jsx`, `components/SeccionDesplegable.jsx` |

### Archivos modificados de esta fase

`models/Pedido.java`, `dto/PedidoCrearRequest.java`, `dto/PedidoResponse.java`,
`services/PedidoService.java`, `controllers/PedidoController.java`,
`controllers/VentaController.java`, `services/ConfiguracionService.java`,
`controllers/ConfiguracionController.java`, `db/init.sql`,
`PedidoServiceTest.java`, `SecurityRolesTest.java`, `CurrencyContext.jsx`,
`PedidoModal.jsx`, `PublicLayout.jsx`, `MisPedidos.jsx`, `Pedidos.jsx`,
`VentaRapida.jsx`, `Configuracion.jsx`, `README.md`.

Se eliminaron `PedidoConvertirRequest`, `PedidoAjusteRequest` y la lógica de
ajuste de líneas en `PedidoService`.

### Verificación de esta fase

- Suite backend con Docker (`maven:3.9-eclipse-temurin-17`): **134 tests, 0 fallos,
  0 errores**.
- Build frontend con Docker (`node:20-alpine`): **OK**.
- La rama de trabajo se mantiene en `feature/pedidos`; `scripts/importar_excel.py`
  no se incluyó en ningún commit de esta fase.
