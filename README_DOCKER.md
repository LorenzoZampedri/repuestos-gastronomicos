# 🐳 Guía Docker - Repuestos Gastronómicos

> Guía completa para levantar el proyecto con Docker.
> Si tenés problemas, revisá la sección de [Solución de Problemas](#-solución-de-problemas).

---

## 📋 Tabla de Contenidos

1. [Requisitos Previos](#-requisitos-previos)
2. [Instalación Rápida](#-instalación-rápida)
3. [Primeros Pasos después de Levantar](#-primeros-pasos-después-de-levantar)
4. [Comandos Útiles](#-comandos-útiles)
5. [URLs de Servicios](#-urls-de-servicios)
6. [Variables de Entorno](#-variables-de-entorno)
7. [Migración de Excel a PostgreSQL](#-migración-de-excel-a-postgresql)
8. [Solución de Problemas](#-solución-de-problemas)
9. [Notas para el Equipo](#-notas-para-el-equipo)

---

## 🚀 Requisitos Previos

### Opción A: Docker Desktop (Recomendada)

| Requisito | Detalle |
|-----------|---------|
| **Sistema operativo** | Windows 10/11 Pro o Enterprise, o macOS |
| **Docker Desktop** | [Descargar](https://www.docker.com/products/docker-desktop/) |
| **WSL2** | Habilitado en Windows (Docker Desktop lo configura) |

### Opción B: Docker Engine en WSL2 (Sin Docker Desktop)

| Requisito | Detalle |
|-----------|---------|
| **Sistema operativo** | Windows 10/11 Home, o sin Docker Desktop |
| **WSL2** | Habilitado manualmente |
| **Ubuntu** | Instalado desde Microsoft Store |

---

## 🏃 Instalación Rápida

### Si usás Docker Desktop (Opción A)

```bash
# 1. Clonar el repositorio
git clone https://github.com/tu-equipo/repuestos-gastronomicos.git
cd repuestos-gastronomicos

# 2. Copiar archivo de entorno
cp .env.example .env

# 3. Levantar todo
docker-compose up -d

# 4. Verificar que funcione
docker-compose ps

# 5. Abrir en navegador
# Frontend: http://localhost:3000
# Backend API: http://localhost:8080
```

### Si NO podés usar Docker Desktop (Opción B - WSL2)

#### Paso 1: Habilitar WSL2

```powershell
# Abrir PowerShell como Administrador
wsl --install
```

1. Reiniciar el equipo
2. Abrir Ubuntu desde el menú Inicio
3. Crear usuario y contraseña de Ubuntu

#### Paso 2: Instalar Docker Engine en Ubuntu

```bash
# Actualizar paquetes
sudo apt update && sudo apt upgrade -y

# Instalar dependencias
sudo apt install -y ca-certificates curl gnupg lsb-release

# Agregar clave GPG oficial de Docker
sudo mkdir -m 0755 -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# Agregar repositorio
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Instalar Docker Engine
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Agregar usuario al grupo docker (para no usar sudo)
sudo usermod -aG docker $USER

# Aplicar cambios (cerrar y abrir terminal)
newgrp docker
```

#### Paso 3: Verificar instalación

```bash
docker --version
# Debería mostrar: Docker version 24.x.x o similar

docker-compose --version
# Debería mostrar: Docker Compose version v2.x.x
```

#### Paso 4: Clonar y acceder al proyecto

```bash
# Opción 1: Acceder a carpeta de Windows
cd /mnt/c/Users/TU_USUARIO/Desktop/repuestos-gastronomicos

# Opción 2: Clonar en Ubuntu
git clone https://github.com/tu-equipo/repuestos-gastronomicos.git
cd repuestos-gastronomicos
```

#### Paso 5: Levantar el proyecto

```bash
# Copiar variables de entorno
cp .env.example .env

# Levantar servicios
docker-compose up -d

# Ver logs en tiempo real
docker-compose logs -f

# Verificar contenedores
docker-compose ps
```

---

## 🎯 Primeros Pasos después de Levantar

Una vez que `docker-compose ps` muestre todos los servicios corriendo:

### 1. Verificar que todo funciona

```bash
# Verificar backend
curl http://localhost:8080/actuator/health
# Debería mostrar: {"status":"UP"}

# Verificar frontend
curl -I http://localhost:3000
# Debería mostrar: HTTP/1.1 200 OK
```

### 2. Crear usuario administrador

Abrir en el navegador: `http://localhost:8080/swagger-ui.html`

Ejecutar el endpoint de registro:
```json
POST /api/auth/register
{
  "username": "admin",
  "password": "admin123",
  "nombre": "Administrador",
  "email": "admin@repuestos.com",
  "rol": "ADMIN"
}
```

**Credenciales iniciales:**
| Usuario | Contraseña | Rol |
|---------|------------|-----|
| admin | admin123 | ADMIN |

### 3. Importar productos desde Excel

```bash
# Asegurar que el archivo Excel está en la carpeta correcta
ls scripts/
# Debería mostrar: importar_excel.py, inventario.xlsx

# Ejecutar importación
python scripts/importar_excel.py

# Verificar en la base de datos
docker-compose exec postgres psql -U admin -d repuestos_db -c "SELECT COUNT(*) FROM productos;"
```

### 4. Probar el sistema

1. **Login**: Ir a `http://localhost:3000` e iniciar sesión
2. **Inventario**: Verificar que los productos aparecen
3. **Venta rápida**: Crear una venta de prueba
4. **Catálogo público**: Ir a `http://localhost:3000/catalogo` (sin login)

### 5. Probar en celular (misma red)

```bash
# Obtener IP de tu PC
ipconfig  # Windows
hostname -I  # Linux/Mac

# Abrir en celular
http://TU_IP:3000
```

---

## 📦 Comandos Útiles

### Gestión de contenedores

```bash
# ═══════════════════════════════════════════════════════════
# INICIAR / PARAR
# ═══════════════════════════════════════════════════════════

# Levantar todo en background
docker-compose up -d

# Parar todos los contenedores
docker-compose down

# Parar y eliminar volúmenes (BORRA la base de datos)
docker-compose down -v


# ═══════════════════════════════════════════════════════════
# VER ESTADO
# ═══════════════════════════════════════════════════════════

# Ver contenedores corriendo
docker-compose ps

# Ver todos los contenedores (incluyendo parados)
docker-compose ps -a

# Ver uso de recursos
docker stats


# ═══════════════════════════════════════════════════════════
# LOGS
# ═══════════════════════════════════════════════════════════

# Ver logs en tiempo real
docker-compose logs -f

# Logs de un servicio específico
docker-compose logs -f backend
docker-compose logs -f frontend
docker-compose logs -f postgres

# Últimas 100 líneas de logs
docker-compose logs --tail=100 backend


# ═══════════════════════════════════════════════════════════
# REINICIAR
# ═══════════════════════════════════════════════════════════

# Reiniciar todo
docker-compose restart

# Reiniciar un servicio específico
docker-compose restart backend
docker-compose restart frontend
docker-compose restart postgres
```

### Desarrollo

```bash
# ═══════════════════════════════════════════════════════════
# RECONSTRUIR (después de cambios en Dockerfile o código)
# ═══════════════════════════════════════════════════════════

# Reconstruir solo backend
docker-compose up -d --build backend

# Reconstruir solo frontend
docker-compose up -d --build frontend

# Reconstruir todo
docker-compose up -d --build


# ═══════════════════════════════════════════════════════════
# ACCEDER A CONTENEDORES (para debug)
# ═══════════════════════════════════════════════════════════

# Entrar al backend (bash)
docker-compose exec backend bash

# Entrar a PostgreSQL
docker-compose exec postgres psql -U admin -d repuestos_db

# Entrar al frontend
docker-compose exec frontend sh


# ═══════════════════════════════════════════════════════════
# COMANDOS DE BASE DE DATOS
# ═══════════════════════════════════════════════════════════

# Ver tablas
docker-compose exec postgres psql -U admin -d repuestos_db -c "\dt"

# Contar productos
docker-compose exec postgres psql -U admin -d repuestos_db -c "SELECT COUNT(*) FROM productos;"

# Ver productos
docker-compose exec postgres psql -U admin -d repuestos_db -c "SELECT * FROM productos LIMIT 10;"

# Backup de la base de datos
docker-compose exec postgres pg_dump -U admin repuestos_db > backup.sql

# Restaurar backup
cat backup.sql | docker-compose exec -T postgres psql -U admin -d repuestos_db
```

---

## 🌐 URLs de Servicios

| Servicio | URL | Descripción |
|----------|-----|-------------|
| **Frontend (Panel)** | http://localhost:3000 | Panel de administración |
| **Catálogo Público** | http://localhost:3000/catalogo | Catálogo para clientes |
| **Backend API** | http://localhost:8080 | API REST |
| **Swagger UI** | http://localhost:8080/swagger-ui.html | Documentación interactiva de la API |
| **PostgreSQL** | localhost:5432 | Base de datos |

---

## 🔧 Variables de Entorno

El archivo `.env` debe contener:

```env
# ═══════════════════════════════════════════════════════════
# BASE DE DATOS
# ═══════════════════════════════════════════════════════════
DB_PASSWORD=repuestos123

# ═══════════════════════════════════════════════════════════
# JWT (para autenticación)
# ═══════════════════════════════════════════════════════════
JWT_SECRET=tu-clave-secreta-aqui-cambiar-en-produccion

# ═══════════════════════════════════════════════════════════
# PUERTOS
# ═══════════════════════════════════════════════════════════
BACKEND_PORT=8080
FRONTEND_PORT=3000
POSTGRES_PORT=5432
```

**⚠️ IMPORTANTE:** 
- **NUNCA** commitear el archivo `.env`
- Usar `.env.example` como referencia
- Si agregás una variable nueva, actualizar `.env.example`

---

## 📊 Migración de Excel a PostgreSQL

### Paso 1: Preparar el archivo Excel

Asegurar que el Excel tenga las siguientes columnas (en cualquier orden):

| Columna | Ejemplo | Obligatoria |
|---------|---------|-------------|
| `codigo` | "QUEM-4B-001" | Sí |
| `nombre` | "Quemador 4 bocas" | Sí |
| `descripcion` | "Quemador para horno pizzero..." | No |
| `precio` | 15000.50 | Sí |
| `stock` | 25 | Sí |
| `categoria` | "Quemadores" | No |

**Ejemplo de Excel:**
| codigo | nombre | descripcion | precio | stock | categoria |
|--------|--------|-------------|--------|-------|-----------|
| QUEM-4B-001 | Quemador 4 bocas | Quemador industrial para horno | 15000.50 | 25 | Quemadores |
| FRE-20L-001 | Freidora 20L | Freidora industrial | 280000 | 8 | Freidoras |

### Paso 2: Instalar dependencias Python

```bash
# Asegurar que Python está instalado
python --version

# Instalar librerías necesarias
pip install pandas psycopg2-binary openpyxl
```

### Paso 3: Ejecutar la importación

```bash
# Asegurar que estás en la raíz del proyecto
cd repuestos-gastronomicos

# Ejecutar script
python scripts/importar_excel.py
```

**Salida esperada:**
```
📊 Leyendo archivo: scripts/inventario.xlsx
✅ Productos encontrados: 10
✅ Importación completada!
   - Productos insertados: 10
   - Errores: 0
```

### Paso 4: Verificar la importación

```bash
# Ver todos los productos
docker-compose exec postgres psql -U admin -d repuestos_db -c "SELECT * FROM productos;"

# Contar productos
docker-compose exec postgres psql -U admin -d repuestos_db -c "SELECT COUNT(*) FROM productos;"

# Buscar un producto específico
docker-compose exec postgres psql -U admin -d repuestos_db -c "SELECT * FROM productos WHERE nombre ILIKE '%quemador%';"
```

---

## 🐛 Solución de Problemas

### ❌ "Cannot connect to the Docker daemon"

**En Windows con Docker Desktop:**
```bash
# 1. Abrir Docker Desktop y esperar a que inicie
# 2. Verificar el ícono en la bandeja (debe ser verde)
# 3. Si no inicia, reiniciar Docker Desktop
```

**En WSL2 sin Docker Desktop:**
```bash
# Verificar que Docker está corriendo
sudo service docker start

# O reiniciar el servicio
sudo systemctl restart docker

# Verificar estado
sudo service docker status
```

---

### ❌ "Port already in use"

```bash
# En Windows:
netstat -ano | findstr :3000
netstat -ano | findstr :8080

# En Linux/Mac:
lsof -i :3000
lsof -i :8080

# Matar el proceso que usa el puerto (reemplazar PID)
taskkill /PID <PID> /F  # Windows
kill -9 <PID>           # Linux/Mac
```

**O cambiar el puerto en `.env`:**
```env
FRONTEND_PORT=3001  # Cambiar de 3000 a 3001
```

---

### ❌ "Permission denied" (WSL2)

```bash
# El usuario no está en el grupo docker
sudo usermod -aG docker $USER
newgrp docker

# Verificar grupos
groups $USER
# Debería mostrar "docker" en la lista

# Si sigue sin funcionar, usar sudo (no recomendado)
sudo docker-compose up -d
```

---

### ❌ No se conecta a la base de datos

```bash
# 1. Verificar que PostgreSQL está corriendo
docker-compose ps postgres
# Debería mostrar "Up" y "healthy"

# 2. Ver logs de PostgreSQL
docker-compose logs postgres
# Buscar errores como "FATAL" o "password authentication failed"

# 3. Esperar 10 segundos y reintentar
# PostgreSQL tarda en iniciar completamente

# 4. Verificar contraseña
docker-compose exec postgres psql -U admin -d repuestos_db -c "\q"
# Si pide contraseña, usar: repuestos123
```

---

### ❌ Frontend no carga / errores CORS

```bash
# 1. Verificar que el backend está corriendo
docker-compose ps backend
# Debería mostrar "Up"

# 2. Verificar CORS en el código
cat backend/src/main/java/com/repuestos/config/SecurityConfig.java
# Debería tener configuración de CORS permitiendo localhost:3000

# 3. Ver logs del backend
docker-compose logs backend | grep -i "cors\|error"

# 4. Probar el backend directamente
curl http://localhost:8080/api/productos
```

---

### ❌ "Module not found" o errores de dependencias

```bash
# Backend (Java/Spring)
docker-compose exec backend ./mvnw clean install

# Frontend (React)
docker-compose exec frontend npm install
docker-compose exec frontend npm run build

# Reconstruir contenedor
docker-compose up -d --build backend
docker-compose up -d --build frontend
```

---

### ❌ Cambios no se reflejan

```bash
# Si estás editando código y no se actualiza:

# Para backend: El volumen está montado, pero Maven puede cachear
docker-compose exec backend ./mvnw clean spring-boot:run

# Para frontend: React tiene hot-reload, pero verificar
docker-compose logs frontend | grep "Compiled"

# Si nada funciona, reconstruir
docker-compose up -d --build
```

---

### ❌ "No space left on device"

```bash
# Verificar uso de disco
docker system df

# Limpiar imágenes no usadas
docker system prune -a

# Limpiar volúmenes no usados
docker volume prune

# Limpiar todo (CUIDADO: borra datos no persistidos)
docker system prune -a --volumes
```

---

### ❌ Base de datos corrupta o con errores

```bash
# Si necesitás resetear la base de datos:
# ⚠️ ESTO BORRA TODOS LOS DATOS

# 1. Parar todo
docker-compose down

# 2. Eliminar volúmenes
docker volume rm repuestos-gastronomicos_pgdata

# 3. Levantar de nuevo
docker-compose up -d

# 4. Reimportar Excel
python scripts/importar_excel.py
```

---

## 📝 Notas para el Equipo

### 🔄 Git + Docker

```bash
# Después de pulled cambios del repo
git pull origin main

# Si hay cambios en Dockerfile o docker-compose.yml
docker-compose up -d --build

# Si solo hay cambios en código (sin Dockerfile)
docker-compose restart backend  # O frontend
```

### 🔐 Variables de Entorno

- **NUNCA** commitear el archivo `.env`
- Usar `.env.example` como referencia
- Si agregás una variable nueva, actualizar `.env.example`
- En producción, las variables se configuran en Railway

### 🐳 Docker en la CI/CD

El workflow de GitHub Actions ya está configurado para:
- Build automático al hacer push a `main`
- Tests antes del build
- Deploy automático a Railway

### 📱 Testing en Celular

```bash
# 1. Asegurar que el celular está en la misma red WiFi

# 2. Obtener IP de tu PC
ipconfig  # Windows
hostname -I  # Linux

# 3. Abrir en el navegador del celular
http://TU_IP:3000

# Ejemplo: http://192.168.1.100:3000
```

### 🔄 Hot Reload

**Backend (Spring Boot):**
- Los cambios se detectan automáticamente
- Si no funciona: `docker-compose restart backend`

**Frontend (React):**
- Hot reload está habilitado por defecto
- Los cambios se reflejan al guardar el archivo

### 🗃️ Backup de la Base de Datos

```bash
# Crear backup
docker-compose exec postgres pg_dump -U admin repuestos_db > backup_$(date +%Y%m%d).sql

# Restaurar backup
cat backup_20240115.sql | docker-compose exec -T postgres psql -U admin -d repuestos_db
```

---

## 🆘 Ayuda

Si tenés problemas:

1. **Revisar esta guía** - La mayoría de los problemas están documentados
2. **Ver logs** - `docker-compose logs -f` muestra qué está pasando
3. **Preguntar en el canal** - Compartí el output de `docker-compose logs`
4. **Reiniciar** - Muchas veces `docker-compose down && docker-compose up -d` soluciona

### Comando mágico para debugging

```bash
# Ver todo: estado, logs, y errores
echo "=== CONTENEDORES ===" && docker-compose ps && echo -e "\n=== LOGS ===" && docker-compose logs --tail=50
```

---

## 📚 Recursos

- [Docker Docs](https://docs.docker.com/)
- [Docker Compose](https://docs.docker.com/compose/)
- [Spring Boot + Docker](https://spring.io/guides/gs/spring-boot-docker/)
- [React + Docker](https://github.com/reactjs/react-docker-example)
