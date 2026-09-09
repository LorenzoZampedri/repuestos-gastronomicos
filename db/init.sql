-- ═══════════════════════════════════════════════════════════
-- REPOSTEROS GASTRONOMICOS - Database Schema
-- ═══════════════════════════════════════════════════════════

-- Crear extensiones necesarias
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ═══════════════════════════════════════════════════════════
-- TABLA: usuarios
-- ═══════════════════════════════════════════════════════════
CREATE TABLE usuarios (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    rol VARCHAR(20) NOT NULL CHECK (rol IN ('ADMIN', 'VENDEDOR', 'OPERARIO')),
    activo BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ═══════════════════════════════════════════════════════════
-- TABLA: categorias
-- ═══════════════════════════════════════════════════════════
CREATE TABLE categorias (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(100) UNIQUE NOT NULL,
    descripcion TEXT,
    activa BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ═══════════════════════════════════════════════════════════
-- TABLA: productos
-- ═══════════════════════════════════════════════════════════
CREATE TABLE productos (
    id SERIAL PRIMARY KEY,
    codigo_barras VARCHAR(50) UNIQUE,
    nombre VARCHAR(200) NOT NULL,
    descripcion TEXT,
    precio DECIMAL(10,2) NOT NULL,
    precio_usd DECIMAL(10,2),
    stock_actual INTEGER NOT NULL DEFAULT 0,
    stock_minimo INTEGER DEFAULT 5,
    imagen_url VARCHAR(500),
    categoria_id INTEGER REFERENCES categorias(id),
    activo BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ═══════════════════════════════════════════════════════════
-- TABLA: clientes
-- ═══════════════════════════════════════════════════════════
CREATE TABLE clientes (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR(200) NOT NULL,
    telefono VARCHAR(20),
    email VARCHAR(100),
    direccion TEXT,
    cuit VARCHAR(20),
    condicion_iva VARCHAR(20) DEFAULT 'CONSUMIDOR_FINAL',
    saldo_corriente DECIMAL(10,2) DEFAULT 0,
    observaciones TEXT,
    activo BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ═══════════════════════════════════════════════════════════
-- TABLA: ventas
-- ═══════════════════════════════════════════════════════════
CREATE TABLE ventas (
    id SERIAL PRIMARY KEY,
    cliente_id INTEGER REFERENCES clientes(id),
    usuario_id INTEGER REFERENCES usuarios(id),
    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total DECIMAL(10,2) NOT NULL DEFAULT 0,
    estado VARCHAR(20) DEFAULT 'PENDIENTE' 
        CHECK (estado IN ('PENDIENTE', 'PARCIAL', 'PAGADA', 'ANULADA')),
    observaciones TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ═══════════════════════════════════════════════════════════
-- TABLA: detalle_venta
-- ═══════════════════════════════════════════════════════════
CREATE TABLE detalle_venta (
    id SERIAL PRIMARY KEY,
    venta_id INTEGER REFERENCES ventas(id) ON DELETE CASCADE,
    producto_id INTEGER REFERENCES productos(id),
    cantidad INTEGER NOT NULL CHECK (cantidad > 0),
    precio_unitario DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL
);

-- ═══════════════════════════════════════════════════════════
-- TABLA: pagos
-- ═══════════════════════════════════════════════════════════
CREATE TABLE pagos (
    id SERIAL PRIMARY KEY,
    venta_id INTEGER REFERENCES ventas(id),
    monto DECIMAL(10,2) NOT NULL,
    metodo_pago VARCHAR(20) CHECK (metodo_pago IN ('EFECTIVO', 'TARJETA', 'TRANSFERENCIA', 'OTRO')),
    moneda VARCHAR(3) DEFAULT 'ARS' CHECK (moneda IN ('ARS', 'USD')),
    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    referencia VARCHAR(200),
    usuario_id INTEGER REFERENCES usuarios(id)
);

-- ═══════════════════════════════════════════════════════════
-- TABLA: movimientos_inventario
-- ═══════════════════════════════════════════════════════════
CREATE TABLE movimientos_inventario (
    id SERIAL PRIMARY KEY,
    producto_id INTEGER REFERENCES productos(id),
    tipo VARCHAR(20) CHECK (tipo IN ('ENTRADA', 'SALIDA', 'AJUSTE')),
    cantidad INTEGER NOT NULL,
    motivo VARCHAR(200),
    usuario_id INTEGER REFERENCES usuarios(id),
    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ═══════════════════════════════════════════════════════════
-- TABLA: configuracion
-- ═══════════════════════════════════════════════════════════
CREATE TABLE configuracion (
    id SERIAL PRIMARY KEY,
    clave VARCHAR(100) UNIQUE NOT NULL,
    valor TEXT NOT NULL,
    descripcion TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ═══════════════════════════════════════════════════════════
-- DATOS INICIALES
-- ═══════════════════════════════════════════════════════════

-- Categorías iniciales
INSERT INTO categorias (nombre, descripcion) VALUES
('Hornos Pizzeros', 'Hornos para preparación de pizzas'),
('Freidoras', 'Freidoras industriales para gastronomía'),
('Anafes', 'Anafes y cocinas industriales'),
('Cafeteras', 'Cafeteras de bar y uso comercial'),
('Refrigeración', 'Equipos de refrigeración industrial'),
('Lavado', 'Lavavajillas y equipos de limpieza'),
('Utensilios', 'Utensilios y herramientas de cocina'),
('Repuestos', 'Repuestos y componentes varios');

-- Configuración inicial
INSERT INTO configuracion (clave, valor, descripcion) VALUES
('whatsapp_number', '+5491112345678', 'Número de WhatsApp para consultas'),
('moneda_principal', 'ARS', 'Moneda principal del sistema'),
('stock_minimo_default', '5', 'Stock mínimo por defecto para productos');
