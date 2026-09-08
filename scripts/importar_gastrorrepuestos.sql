-- ============================================
-- IMPORTAR GASTRORREPUESTOS A SCHEMA DEL PROYECTO
-- PostgreSQL - adaptado de datos limpios del Excel
-- ============================================

-- Insertar categorías de Gastrorrepuestos (evitar duplicados)
INSERT INTO categorias (nombre, descripcion) VALUES
('Gas', 'Productos de gas: quemadores, válvulas, termocuplas'),
('Eléctrico', 'Componentes eléctricos: termostatos, resistencias'),
('Accesorios', 'Accesorios y repuestos varios'),
('Bazar', 'Artículos de bazar y ferretería general'),
('Ventilación', 'Sistemas de ventilación y extracción')
ON CONFLICT (nombre) DO NOTHING;

-- Obtener IDs de las categorías recién insertadas
-- Gas, Eléctrico, Accesorios, Bazar, Ventilación

-- ============================================
-- INSERTAR PRODUCTOS
-- ============================================

-- ART-001: Quemador 4 bocas (variante ARS)
INSERT INTO productos (codigo_barras, nombre, descripcion, precio, precio_usd, stock_actual, stock_minimo, imagen_url, categoria_id)
SELECT 'ART-001', 'Quemador 4 bocas', 'Quemador 4 bocas de gas', 45000, NULL, 8, 5, '/images/quemador4Bocas.jpeg', c.id
FROM categorias c WHERE c.nombre = 'Gas'
ON CONFLICT (codigo_barras) DO UPDATE SET
    nombre = EXCLUDED.nombre,
    descripcion = EXCLUDED.descripcion,
    precio = EXCLUDED.precio,
    precio_usd = EXCLUDED.precio_usd,
    stock_actual = EXCLUDED.stock_actual,
    imagen_url = EXCLUDED.imagen_url,
    updated_at = CURRENT_TIMESTAMP;

-- ART-001-B: Quemador 4 bocas con Robinete (USD)
INSERT INTO productos (codigo_barras, nombre, descripcion, precio, precio_usd, stock_actual, stock_minimo, imagen_url, categoria_id)
SELECT 'ART-001-B', 'Quemador 4 bocas c/Robinete', 'Quemador 4 bocas con robinete de gas', 0, 38, 2, 5, '/images/quemador4Bocas.jpeg', c.id
FROM categorias c WHERE c.nombre = 'Gas'
ON CONFLICT (codigo_barras) DO UPDATE SET
    nombre = EXCLUDED.nombre,
    descripcion = EXCLUDED.descripcion,
    precio_usd = EXCLUDED.precio_usd,
    stock_actual = EXCLUDED.stock_actual,
    imagen_url = EXCLUDED.imagen_url,
    updated_at = CURRENT_TIMESTAMP;

-- ART-002-A: Termostato Robertshaw (ARS)
INSERT INTO productos (codigo_barras, nombre, descripcion, precio, precio_usd, stock_actual, stock_minimo, imagen_url, categoria_id)
SELECT 'ART-002-A', 'Termostato Robertshaw 50-300', 'Termostato Robertshaw rango 50-300°F', 32500, NULL, 0, 5, '/images/termostatoRobertshaw50-300.jpeg', c.id
FROM categorias c WHERE c.nombre = 'Eléctrico'
ON CONFLICT (codigo_barras) DO UPDATE SET
    nombre = EXCLUDED.nombre,
    descripcion = EXCLUDED.descripcion,
    precio = EXCLUDED.precio,
    stock_actual = EXCLUDED.stock_actual,
    imagen_url = EXCLUDED.imagen_url,
    updated_at = CURRENT_TIMESTAMP;

-- ART-002-B: Termostato Robertshaw (USD)
INSERT INTO productos (codigo_barras, nombre, descripcion, precio, precio_usd, stock_actual, stock_minimo, imagen_url, categoria_id)
SELECT 'ART-002-B', 'Termostato Robertshaw 50-300 (USD)', 'Termostato Robertshaw rango 50-300°F - precio USD', 0, 28.5, 0, 5, '/images/termostatoRobertshaw50-300.jpeg', c.id
FROM categorias c WHERE c.nombre = 'Eléctrico'
ON CONFLICT (codigo_barras) DO UPDATE SET
    nombre = EXCLUDED.nombre,
    descripcion = EXCLUDED.descripcion,
    precio_usd = EXCLUDED.precio_usd,
    stock_actual = EXCLUDED.stock_actual,
    imagen_url = EXCLUDED.imagen_url,
    updated_at = CURRENT_TIMESTAMP;

-- ART-103: Válvula de seguridad
INSERT INTO productos (codigo_barras, nombre, descripcion, precio, precio_usd, stock_actual, stock_minimo, imagen_url, categoria_id)
SELECT 'ART-103', 'Válvula de seguridad Eitar 1/2', 'Válvula de seguridad Eitar 1/2 pulgada', 1890, NULL, 15, 5, '/images/valvulaDeSeguridad.jpeg', c.id
FROM categorias c WHERE c.nombre = 'Gas'
ON CONFLICT (codigo_barras) DO UPDATE SET
    nombre = EXCLUDED.nombre,
    descripcion = EXCLUDED.descripcion,
    precio = EXCLUDED.precio,
    stock_actual = EXCLUDED.stock_actual,
    imagen_url = EXCLUDED.imagen_url,
    updated_at = CURRENT_TIMESTAMP;

-- ART-104: Canasto Freidora
INSERT INTO productos (codigo_barras, nombre, descripcion, precio, precio_usd, stock_actual, stock_minimo, imagen_url, categoria_id)
SELECT 'ART-104', 'Canasto Freidora 30L Acero', 'Canasto para freidora 30L de acero inoxidable', 0, NULL, 5, 5, '/images/canastoFreidora30L.jpeg', c.id
FROM categorias c WHERE c.nombre = 'Accesorios'
ON CONFLICT (codigo_barras) DO UPDATE SET
    nombre = EXCLUDED.nombre,
    descripcion = EXCLUDED.descripcion,
    stock_actual = EXCLUDED.stock_actual,
    imagen_url = EXCLUDED.imagen_url,
    updated_at = CURRENT_TIMESTAMP;

-- ART-105: Resistencia cafetera (ARS)
INSERT INTO productos (codigo_barras, nombre, descripcion, precio, precio_usd, stock_actual, stock_minimo, imagen_url, categoria_id)
SELECT 'ART-105', 'Resistencia cafetera 2000W', 'Resistencia para cafetera 2000W', 54000, NULL, 12, 5, '/images/resistenciaCafetera2000W.jpeg', c.id
FROM categorias c WHERE c.nombre = 'Eléctrico'
ON CONFLICT (codigo_barras) DO UPDATE SET
    nombre = EXCLUDED.nombre,
    descripcion = EXCLUDED.descripcion,
    precio = EXCLUDED.precio,
    stock_actual = EXCLUDED.stock_actual,
    imagen_url = EXCLUDED.imagen_url,
    updated_at = CURRENT_TIMESTAMP;

-- ART-105-B: Resistencia cafetera (USD, 220V)
INSERT INTO productos (codigo_barras, nombre, descripcion, precio, precio_usd, stock_actual, stock_minimo, imagen_url, categoria_id)
SELECT 'ART-105-B', 'Resistencia Cafetera 2000W 220V', 'Resistencia para cafetera 2000W 220V - precio USD', 0, 42, 3, 5, '/images/resistenciaCafeteria220Vjpeg.webp', c.id
FROM categorias c WHERE c.nombre = 'Eléctrico'
ON CONFLICT (codigo_barras) DO UPDATE SET
    nombre = EXCLUDED.nombre,
    descripcion = EXCLUDED.descripcion,
    precio_usd = EXCLUDED.precio_usd,
    stock_actual = EXCLUDED.stock_actual,
    imagen_url = EXCLUDED.imagen_url,
    updated_at = CURRENT_TIMESTAMP;

-- ART-106: Perilla anafe
INSERT INTO productos (codigo_barras, nombre, descripcion, precio, precio_usd, stock_actual, stock_minimo, imagen_url, categoria_id)
SELECT 'ART-106', 'Perilla anafe estándar negra', 'Perilla estándar negra para anafe', 2500, NULL, 50, 5, '/images/perillaStandar.jpeg', c.id
FROM categorias c WHERE c.nombre = 'Bazar'
ON CONFLICT (codigo_barras) DO UPDATE SET
    nombre = EXCLUDED.nombre,
    descripcion = EXCLUDED.descripcion,
    precio = EXCLUDED.precio,
    stock_actual = EXCLUDED.stock_actual,
    imagen_url = EXCLUDED.imagen_url,
    updated_at = CURRENT_TIMESTAMP;

-- ART-107: Motor extractor (USD)
INSERT INTO productos (codigo_barras, nombre, descripcion, precio, precio_usd, stock_actual, stock_minimo, imagen_url, categoria_id)
SELECT 'ART-107', 'Motor extractor 1/2 HP', 'Motor extractor de aire 1/2 HP', 0, 110, 1, 5, '/images/motorExtractorhp.jpeg', c.id
FROM categorias c WHERE c.nombre = 'Ventilación'
ON CONFLICT (codigo_barras) DO UPDATE SET
    nombre = EXCLUDED.nombre,
    descripcion = EXCLUDED.descripcion,
    precio_usd = EXCLUDED.precio_usd,
    stock_actual = EXCLUDED.stock_actual,
    imagen_url = EXCLUDED.imagen_url,
    updated_at = CURRENT_TIMESTAMP;

-- ART-108: Termocupla universal
INSERT INTO productos (codigo_barras, nombre, descripcion, precio, precio_usd, stock_actual, stock_minimo, imagen_url, categoria_id)
SELECT 'ART-108', 'Termocupla universal 90cm', 'Termocupla universal de 90cm', 8500, NULL, 20, 5, '/images/termocupulaUniversal90cm.jpeg', c.id
FROM categorias c WHERE c.nombre = 'Gas'
ON CONFLICT (codigo_barras) DO UPDATE SET
    nombre = EXCLUDED.nombre,
    descripcion = EXCLUDED.descripcion,
    precio = EXCLUDED.precio,
    stock_actual = EXCLUDED.stock_actual,
    imagen_url = EXCLUDED.imagen_url,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================
-- VERIFICACIÓN
-- ============================================
SELECT 
    p.codigo_barras,
    p.nombre,
    c.nombre as categoria,
    p.precio as precio_ars,
    p.precio_usd,
    p.stock_actual,
    p.imagen_url
FROM productos p
LEFT JOIN categorias c ON p.categoria_id = c.id
ORDER BY p.codigo_barras;
