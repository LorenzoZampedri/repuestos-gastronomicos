# 🛠️ Repuestos Gastronómicos

Sistema de gestión integral para venta de repuestos de gastronomía pesada.

## 📋 Descripción

Plataforma web para gestión de inventario, ventas y cuentas corrientes, con catálogo público para clientes.

## 🚀 Características

- **Inventario en tiempo real** - Control de stock preciso
- **Venta rápida** - Flujo optimizado para mostrador
- **Cuentas corrientes** - Gestión de deudas y pagos
- **Catálogo público** - Web para clientes
- **Multi-rol** - Admin, Vendedor, Operario
- **Responsive** - Funciona en celular y PC

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

# 3. Levantar con Docker
docker-compose up -d

# 4. Abrir en navegador
# Frontend: http://localhost:3000
# Backend: http://localhost:8080
```

**Credenciales iniciales (se crean automáticamente):** admin / admin123

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

## 📚 Documentación

- [Guía Docker](README_DOCKER.md) - Instrucciones para levantar el proyecto
- [API Docs](http://localhost:8080/swagger-ui.html) - Documentación Swagger

## 👥 Equipo

- **Backend:** Spring Boot + PostgreSQL
- **Frontend:** React + TailwindCSS
- **DevOps:** Docker + GitHub Actions

## 📄 Licencia

© 2024 Repuestos Gastronómicos. Todos los derechos reservados.
