# Resumen de implementación: roles ADMIN / EMPLEADO / CLIENTE

Rama: `feature/roles-usuarios`

## a) Resumen funcional

Se implementó un modelo de tres roles con autenticación JWT y autorización por
endpoint:

| Rol | Funcionalidad |
|-----|----------------|
| **ADMIN** | Dashboard, productos (crear/editar/eliminar), ventas (crear/consultar/anular), clientes (crear/editar/eliminar), cuentas corrientes, usuarios y configuración. |
| **EMPLEADO** | Productos (crear/editar), clientes (crear/editar), ventas (crear/consultar, incluidas ventas en cuenta corriente) y venta rápida. Sin acceso a cuentas corrientes, usuarios, dashboard, eliminación ni anulación. |
| **CLIENTE** | Registro público, login, catálogo público y carrito. No accede al panel interno. |

Reglas de negocio implementadas:

- El username es único sin distinguir mayúsculas/minúsculas y no se puede modificar.
- Un usuario inactivo no puede iniciar sesión y un token vigente deja de servir.
- Un administrador no puede desactivarse, quitarse el rol ADMIN ni dejar el
  sistema sin al menos un ADMIN activo.
- El registro público siempre crea usuarios `CLIENTE`.
- Los EMPLEADO no reciben montos de cuentas corrientes en las respuestas de la API:
  se omiten `saldoCorriente`, `montoPagado`, `saldoPendiente` y los pagos.
- El catálogo público no expone `codigoBarras` ni `stockMinimo`.
- “Cargar deuda” y el ajuste manual de saldo quedaron restringidos a ADMIN.

En el frontend:

- Login redirige según el rol: ADMIN al dashboard, EMPLEADO a venta rápida y
  CLIENTE al catálogo.
- Se agregó registro público en `/registro` con auto-login.
- Se agregó administración de usuarios en `/usuarios` (solo ADMIN).
- Se agregó cambio de contraseña desde el menú lateral.
- El menú y las acciones se adaptan al rol.
- Un 403 muestra un mensaje y no cierra la sesión.
- El catálogo muestra nombre del usuario, acceso al panel y cierre de sesión
  cuando hay sesión iniciada.

## b) Cambios técnicos

Backend:

- `Usuario.Rol`: `ADMIN`, `EMPLEADO`, `CLIENTE`.
- Migración idempotente `RolMigration` para PostgreSQL:
  - elimina y recrea `usuarios_rol_check`;
  - convierte `VENDEDOR`/`OPERARIO` a `EMPLEADO`;
  - se omite en H2 para no interferir con los tests.
- `DataInitializer` crea el admin inicial usando `ADMIN_PASSWORD`, con fallback
  `admin123` y advertencia en logs.
- Nuevos endpoints:
  - `POST /api/auth/register`
  - `PUT /api/auth/password`
  - `GET/POST /api/usuarios`
  - `PUT /api/usuarios/{id}`
  - `PUT /api/usuarios/{id}/password`
  - `PATCH /api/usuarios/{id}/activo`
- Nuevos DTOs de request/response para no filtrar campos internos según el rol.
- `SecurityConfig` con matriz por método y ruta, `anyRequest().denyAll()` y
  respuestas JSON 401/403 en español.
- `JwtAuthFilter` bloquea usuarios inactivos aunque tengan un token válido.

Frontend:

- `AuthContext` con `isAdmin`, `isEmpleado`, `isCliente`, `hasRole` y
  `homePorRol`.
- `App.jsx` con `ProtectedRoute` por `allowedRoles`, home por rol y rutas de
  registro/usuarios.
- Nuevas páginas/componentes:
  - `pages/Registro.jsx`
  - `pages/Usuarios.jsx`
  - `components/CambiarPasswordModal.jsx`
- Ajustes por rol en `Layout`, `PublicLayout`, `Login`, `Productos`, `Clientes`,
  `Ventas` y `VentaRapida`.

Infraestructura/documentación:

- `db/init.sql` con el CHECK de roles actualizado.
- `docker-compose.yml` pasa `ADMIN_PASSWORD`.
- `.env.example` documenta `ADMIN_PASSWORD`.
- `README.md` actualizado con roles, permisos y gestión de usuarios.

Commits de la rama:

1. `5d95bd1` - roles, migración y datos iniciales.
2. `c662210` - autenticación, servicio/controller de usuarios y DTOs.
3. `98d721c` - matriz de seguridad.
4. `80ff5f4` - DTOs para ocultar datos sensibles.
5. `13bc705` - tests de roles y reglas de usuarios.
6. `69ac646` - adaptación frontend, rutas, registro, usuarios y manejo de 403.
7. `1ebd3f5` - documentación de roles y despliegue.
8. `783dda3` - ocultamiento de pagos de cuenta corriente a EMPLEADO.

## c) Verificación

Backend:

```bash
docker run --rm \
  -v "$PWD/backend":/app \
  -v repuestos-maven-cache:/root/.m2 \
  -w /app maven:3.9-eclipse-temurin-17 \
  mvn -q test
```

Resultado: **86 tests, 0 failures, 0 errors**.

Cobertura relevante:

- `SecurityRolesTest`: 14 tests de la matriz de permisos por rol.
- `UsuarioServiceTest`: 9 tests de creación, registro de clientes y reglas de
  administradores.

Frontend:

```bash
docker run --rm \
  -v "$PWD/frontend":/app \
  -v repuestos-node-cache:/root/.npm \
  -w /app node:20-alpine \
  sh -c "npm ci && npm run build"
```

Resultado: build de Vite correcto.

Pruebas manuales realizadas con el stack Docker:

- Login de ADMIN, EMPLEADO y CLIENTE correcto.
- ADMIN accede a `/api/usuarios` (200); EMPLEADO y CLIENTE reciben 403.
- EMPLEADO puede listar clientes y ventas, pero sin `saldoCorriente`,
  `montoPagado`, `saldoPendiente` ni `pagos`.
- EMPLEADO recibe 403 en dashboard y deudores de cuentas corrientes.
- Catálogo público accesible sin token.
- `RolMigration` verificada en PostgreSQL al iniciar el backend.
- Usuarios de prueba eliminados después de la verificación.

## d) Puesta en marcha y consideraciones

1. Definir `ADMIN_PASSWORD` en `.env` antes del primer arranque.
2. Levantar el stack:

   ```bash
   docker compose up -d --build
   ```

3. Verificar los servicios:

   ```bash
   docker compose ps
   ```

4. Si la base ya existía con roles viejos, `RolMigration` convierte
   `VENDEDOR`/`OPERARIO` a `EMPLEADO` al iniciar el backend.
5. El usuario `admin` existente no se recrea; si se olvidó la contraseña, un
   ADMIN puede restablecerla desde **Usuarios**.
6. `scripts/importar_excel.py` no forma parte de esta implementación y no fue
   incluido en los commits.
