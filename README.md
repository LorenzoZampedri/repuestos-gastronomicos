# 🛠️ Repuestos Gastronómicos

Sistema de gestión integral para venta de repuestos de gastronomía pesada.

## 📋 Descripción

Plataforma web para gestión de inventario, ventas y cuentas corrientes, con catálogo público para clientes.

## 🚀 Características

- **Inventario en tiempo real** - Control de stock preciso
- **Venta rápida** - Flujo optimizado para mostrador
- **Cuentas corrientes** - Gestión de deudas y pagos
- **Catálogo público** - Web para clientes
- **Multi-rol** - Admin, Empleado y Cliente
- **Responsive** - Funciona en celular y PC

## 🔐 Roles y permisos

| Rol | Alcance |
|-----|---------|
| **ADMIN** | Acceso completo: dashboard, productos, ventas, clientes, cuentas corrientes, usuarios y configuración. |
| **EMPLEADO** | Productos (crear/editar), ventas (incluidas ventas en cuenta corriente) y clientes (crear/editar). No puede eliminar productos/clientes, anular ventas, ver cuentas corrientes ni administrar usuarios. |
| **CLIENTE** | Catálogo público, carrito y consulta por WhatsApp. Puede registrarse desde `/registro`. |

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

## 📚 Documentación

- [Guía Docker](README_DOCKER.md) - Instrucciones para levantar el proyecto
- [Resumen de roles](RESUMEN_ROLES.md) - Detalle de implementación y verificación
- [API Docs](http://localhost:8080/swagger-ui.html) - Documentación Swagger

## 👥 Equipo

- **Backend:** Spring Boot + PostgreSQL
- **Frontend:** React + TailwindCSS
- **DevOps:** Docker + GitHub Actions

## 📄 Licencia

© 2024 Repuestos Gastronómicos. Todos los derechos reservados.
