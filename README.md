# 🛠️ Repuestos Gastronómicos

Sistema de gestión integral para venta de repuestos de gastronomía pesada.

## 📋 Descripción

Plataforma web para gestión de inventario, ventas y cuentas corrientes, con catálogo público para clientes.

## 🚀 Características

- **Inventario en tiempo real** - Control de stock preciso
- **Venta rápida** - Flujo optimizado para mostrador
- **Cuentas corrientes** - Gestión de deudas y pagos
- **Catálogo público** - Web para clientes
- **Pedidos online** - Carrito, seguimiento y conversión a venta
- **Auditoría** - Registro inmutable de acciones y accesos, consultable solo por ADMIN
- **Multi-rol** - Admin, Empleado y Cliente
- **Responsive** - Funciona en celular y PC

## 🔐 Roles y permisos

| Rol | Alcance |
|-----|---------|
| **ADMIN** | Acceso completo: dashboard, productos, ventas, clientes, cuentas corrientes, usuarios, configuración y logs de auditoría. |
| **EMPLEADO** | Productos (crear/editar), ventas con efectivo/tarjeta/transferencia/otro y clientes (crear/editar). No puede vender a cuenta corriente (solo ADMIN), eliminar productos/clientes, anular ventas, ver cuentas corrientes, administrar usuarios ni consultar logs. |
| **CLIENTE** | Catálogo público, carrito, pedidos online y consulta por WhatsApp. Puede registrarse desde `/registro` y hace seguimiento en `/mis-pedidos`. |

Los usuarios inactivos no pueden iniciar sesión. Un administrador no puede
desactivarse a sí mismo, quitarse el rol ADMIN ni dejar el sistema sin al menos
un ADMIN activo.

## 🛠️ Stack Técnico

| Capa | Tecnología |
|------|------------|
| Frontend | React + Vite + TailwindCSS |
| Backend | Spring Boot 3.x + Java 17 |
| Base de datos | PostgreSQL 15 |
| Auth | JWT + Spring Security |
| Container | Docker + Docker Compose |

## 🏃 Inicio Rápido

```bash
# 1. Clonar repositorio
git clone https://github.com/LorenzoZampedri/repuestos-gastronomicos.git
cd repuestos-gastronomicos

# 2. Copiar variables de entorno
cp .env.example .env    # Linux/Mac
copy .env.example .env  # Windows (CMD)

# 3. Levantar con Docker (siempre con --build para reflejar los últimos cambios)
docker compose up -d --build

# 4. Verificar que los 3 servicios estén "Up" (la DB debe decir "healthy")
docker compose ps

# 5. Abrir en navegador
# Frontend: http://localhost:3000
# Backend: http://localhost:8080
```

**Credenciales iniciales (se crean automáticamente):** admin / admin123

> Definí `ADMIN_PASSWORD` en `.env` antes del primer arranque para reemplazar la
> contraseña inicial. Si no se define, se usa `admin123` y el backend registra
> una advertencia. Una vez creado el usuario `admin`, cambiarlo desde la app con
> el botón de contraseña del menú lateral.

> **⚠️ Solo una instancia a la vez:** los puertos 3000/8080/5432 son fijos y los
> contenedores comparten nombres. Si ya tenés otra copia del proyecto levantada
> (carpeta vieja o versión anterior), parala antes de levantar una nueva:
>
> ```bash
> cd /ruta/a/la/carpeta/vieja && docker compose down
> # o, si no sabés de dónde salieron:
> docker ps -a | grep repuestos
> docker rm -f repuestos_db repuestos_backend repuestos_frontend
> ```
>
> Detalles y más errores: [Guía Docker - Solución de Problemas](README_DOCKER.md#-solución-de-problemas).

## 📁 Estructura del Proyecto

```
repuestos-gastronomicos/
├── backend/           # Spring Boot API
├── frontend/          # React + Vite
├── db/               # Scripts SQL
├── scripts/          # Utilidades (import Excel)
├── docker-compose.yml
└── README_DOCKER.md  # Guía completa Docker
```

## 👤 Gestión de usuarios

Los administradores pueden crear, editar, activar/desactivar y restablecer
contraseñas desde **Usuarios** en el panel. El registro público (`/registro`)
siempre crea usuarios con rol `CLIENTE`.

Endpoints principales:

- `POST /api/auth/register` - registro público de clientes.
- `PUT /api/auth/password` - cambio de contraseña del usuario autenticado.
- `GET/POST /api/usuarios` - listado y alta de usuarios (solo ADMIN).
- `PUT /api/usuarios/{id}` - edición de nombre, email, rol y estado (solo ADMIN).
- `PUT /api/usuarios/{id}/password` - restablecimiento de contraseña (solo ADMIN).
- `PATCH /api/usuarios/{id}/activo` - activar o desactivar usuario (solo ADMIN).
- `PUT /api/usuarios/{id}/cliente` - vincular un usuario CLIENTE a una ficha de cliente (solo ADMIN).

## 🛒 Pedidos online

Un usuario `CLIENTE` arma una compra desde el carrito (botón **Comprar** en el
panel del carrito, modal **Confirmar compra**). Antes de enviar debe elegir uno de
los métodos de pago disponibles para compras online: **Efectivo**, **Tarjeta** o
**Transferencia**. `CUENTA_CORRIENTE` y `OTRO` no están disponibles para el
cliente ni por UI ni por API.

La compra queda `PENDIENTE`, **no reserva stock, no genera una venta y no modifica
la cuenta corriente**: recién al aprobarse se registra la venta. Un `ADMIN` o
`EMPLEADO` la revisa en `/pedidos` y puede aprobarla o rechazarla con un motivo.

Flujo:

1. El `CLIENTE` envía la compra desde el carrito (mínimo 1 y máximo 50 líneas,
   cantidad por producto 1 a 9999, con stock validado al momento de pedir) y elige
   el método de pago.
2. Aparece en `/mis-pedidos` (para el cliente) y en `/pedidos` (para el panel),
   con badge de pendientes en el menú.
3. El empleado/administrador revisa el detalle en **solo lectura**. No puede
   modificar productos, cantidades ni el método de pago: es una medida anti-fraude.
   El detalle muestra el método elegido y el stock actual como indicador.
4. Al **aprobar**, se revalida stock y precios, se descuenta el stock, se crea la
   venta con el cliente y el usuario que aprueba, y se registra el pago por el
   total con **el método que eligió el cliente**. La venta queda `PAGADA`, el saldo
   del cliente en 0 y el pago aparece en su historial. La venta queda con
   observaciones `Pedido #id`.
5. Si falta stock al aprobar, la aprobación falla con un mensaje que nombra el
   producto y la compra sigue `PENDIENTE`. El staff no la corrige: puede
   rechazarla con un motivo para que el cliente vuelva a comprar. Dos
   aprobaciones simultáneas generan una sola venta (bloqueo del pedido).
6. `RECHAZADO` (con motivo) y `CANCELADO` (por el cliente) no tocan stock ni
   saldo.

Los pedidos anteriores a la selección de método de pago no tienen método guardado.
Esos pedidos `PENDIENTE` **no se pueden aprobar**: solo rechazar. La API responde
`409` indicando que el pedido es anterior y la UI oculta el botón de aprobar.

### Datos de transferencia

Cuando el cliente elige **Transferencia**, ve el bloque **MP · Mercado Pago** con
CVU, alias y titular de la cuenta, con botones para copiar CVU y alias. Los mismos
datos se muestran en la pantalla de éxito y en `/mis-pedidos` mientras la compra
siga pendiente y el método sea transferencia.

Los datos **no están fijos en el frontend**: se editan desde **Configuración** →
sección desplegable **Datos de transferencia** (solo ADMIN), en un único formulario
con CVU (22 dígitos), alias (6 a 20 caracteres) y titular. Se guardan en la tabla
de configuración como `transferencia_cvu`, `transferencia_alias` y
`transferencia_titular`; el sembrado inicial solo ocurre si la clave no existe y
nunca pisa un valor editado por el admin.

El cliente puede seguir el estado en `/mis-pedidos` y cancelar mientras siga
`PENDIENTE`. Al registrarse, cada usuario `CLIENTE` obtiene automáticamente su
ficha de cliente (sin duplicarla nunca); un ADMIN puede vincularlo a una ficha
existente desde `/usuarios`, regla: el cliente destino debe estar activo y no
tener otro usuario vinculado, y si el cliente actual tiene ventas o pagos la
vinculación se rechaza.

### Venta anónima vs. venta con cliente

| | Venta anónima (Venta Rápida sin cliente) | Venta con cliente (pedido o venta con cliente) |
|---|---|---|
| Origen | Venta Rápida / API con `clienteId = null` | Pedido convertido o venta con cliente elegido |
| Stock | Se descuenta | Se descuenta |
| Cuenta corriente | **No existe**: no toca ningún saldo | `CUENTA_CORRIENTE` suma el total al `saldoCorriente` |
| Pago | Queda ligado solo a la venta | Queda ligado a la venta y al cliente (visible en su historial) |
| Método `CUENTA_CORRIENTE` | **Rechazado** con 400: *"Para vender a cuenta corriente tenés que seleccionar un cliente registrado"* | Permitido **solo a ADMIN** (deja la venta `PENDIENTE` y genera deuda). Un EMPLEADO recibe 403: *"Solo un administrador puede vender a cuenta corriente"* |

En la UI de Venta Rápida la opción “Cuenta corriente” solo aparece para un
usuario ADMIN con un cliente seleccionado y, si se deselecciona el cliente o el
usuario no es ADMIN, el método vuelve a `EFECTIVO`.

Los pedidos online **nunca** se aprueban a cuenta corriente: el cliente solo
puede elegir efectivo, tarjeta o transferencia y la aprobación usa ese método.

### Permisos de los endpoints de pedidos

| Endpoint | ADMIN | EMPLEADO | CLIENTE | Sin token |
|---|---|---|---|---|
| `POST /api/pedidos` | ✗ 403 | ✗ 403 | ✓ | 401 |
| `GET /api/pedidos/mios`, `/mios/{id}`, `/mios/contacto`, `PUT /mios/{id}/cancelar` | ✗ 403 | ✗ 403 | ✓ | 401 |
| `GET /api/pedidos?estado=`, `GET /api/pedidos/{id}`, `GET /api/pedidos/pendientes/count` | ✓ | ✓ | ✗ 403 | 401 |
| `POST /api/pedidos/{id}/convertir`, `POST /api/pedidos/{id}/rechazar` | ✓ | ✓ | ✗ 403 | 401 |

Un pedido de otro cliente responde `404` (no `403`) para no revelar que existe,
y el `saldoCorriente` solo viaja en las respuestas hacia `ADMIN`.

Endpoints principales:

- `POST /api/pedidos` - crear compra: `{items:[{productoId, cantidad}], telefono, observaciones, metodoPago}`; `metodoPago` debe ser `EFECTIVO`, `TARJETA` o `TRANSFERENCIA` (solo CLIENTE).
- `GET /api/pedidos/mios` - historial propio (solo CLIENTE).
- `GET /api/pedidos/mios/{id}` - detalle propio (solo CLIENTE).
- `GET /api/pedidos/mios/contacto` - teléfono/dirección precargados (solo CLIENTE).
- `PUT /api/pedidos/mios/{id}/cancelar` - cancelar pedido pendiente propio.
- `GET /api/pedidos?estado=PENDIENTE` - listado para ADMIN/EMPLEADO.
- `GET /api/pedidos/{id}` - detalle para ADMIN/EMPLEADO.
- `GET /api/pedidos/pendientes/count` - contador para el menú.
- `POST /api/pedidos/{id}/convertir` - sin body útil: usa las líneas y el método guardados en el pedido (cualquier `metodoPago` o `items` enviado se ignora).
- `POST /api/pedidos/{id}/rechazar` - `{motivo}` obligatorio.
- `GET /api/configuracion/publica` - configuración pública, incluye `datosTransferencia: {cvu, alias, titular}`.
- `PUT /api/configuracion/transferencia` - `{cvu, alias, titular}` (solo ADMIN) transaccional, con validaciones e historial.

## 🧾 Logs de auditoría

Todas las acciones relevantes quedan registradas en una tabla inmutable
(`logs_actividad`, sin endpoints de edición ni borrado): quién, cuándo, qué
acción, sobre qué entidad, el resultado y desde qué IP/navegador. Se registran
altas y cambios de usuarios, productos, clientes, ventas, pagos, pedidos,
configuración, inicios de sesión fallidos y accesos denegados.

Los registros se consultan en **Logs** (solo `ADMIN`) con filtros combinables por
fecha, usuario, rol, módulo, acción, resultado y texto libre. La tabla se pagina
en el servidor y se puede exportar a CSV (máximo 5000 filas por exportación).

Endpoints principales (todos solo `ADMIN`):

- `GET /api/logs` - listado paginado con filtros (`desde`, `hasta`, `usuario`, `rol`, `modulo`, `accion`, `resultado`, `q`, `page`, `size`).
- `GET /api/logs/{id}` - detalle con el JSON de cambios.
- `GET /api/logs/export` - CSV con los mismos filtros; responde `X-Logs-Filas` y `X-Logs-Truncados`.
- `GET /api/logs/filtros` - módulos, acciones y roles para poblar los selects.

Los eventos se guardan en la zona horaria del backend (`TZ=America/Argentina/Buenos_Aires`
en Docker). El logging nunca interrumpe la operación: si falla, el negocio continúa.
Ver [Resumen de logs](RESUMEN_LOGS.md) para el detalle de implementación.

## 📚 Documentación

- [Guía Docker](README_DOCKER.md) - Instrucciones para levantar el proyecto
- [Resumen de pedidos online](RESUMEN_PEDIDOS.md) - Implementación y verificación
- [Resumen de roles](RESUMEN_ROLES.md) - Detalle de implementación y verificación
- [Resumen de logs](RESUMEN_LOGS.md) - Auditoría, filtros, exportación y verificación
- [API Docs](http://localhost:8080/swagger-ui.html) - Documentación Swagger

## 👥 Equipo

- **Backend:** Spring Boot + PostgreSQL
- **Frontend:** React + TailwindCSS
- **DevOps:** Docker + GitHub Actions

## 📄 Licencia

© 2024 Repuestos Gastronómicos. Todos los derechos reservados.
