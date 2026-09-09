-- ═══════════════════════════════════════════════════════════
-- SEED: Usuarios, Clientes, Ventas, Detalles y Pagos
-- Usa subqueries para referenciar IDs dinámicos
-- ═══════════════════════════════════════════════════════════

-- Usuario admin (password: admin123)
INSERT INTO usuarios (username, password_hash, nombre, email, rol)
VALUES ('admin', '$2a$10$LfFSXufa/KTF9Ef3uB8yF.nFtI/op/Codekup4oPf0hiZ0ZYRSfUG', 'Administrador', 'admin@repuestos.com', 'ADMIN')
ON CONFLICT (username) DO NOTHING;

-- Clientes
INSERT INTO clientes (nombre, telefono, email, direccion, cuit, condicion_iva, saldo_corriente, observaciones) VALUES
('Pizzeria Don Carlos', '+5491155551234', 'doncarlos@email.com', 'Av. San Martin 1250, CABA', '30-71234567-9', 'RI', 0, 'Cliente frecuente, compra mensual'),
('Restaurant La Parrilla', '+5491166662345', 'laparrilla@email.com', 'Av. Corrientes 4580, CABA', '30-72345678-0', 'RI', 0, 'Compra constante de repuestos'),
('Cocina Industrial Mendez', '+5491177773456', 'mendez@email.com', 'Av. Callao 890, CABA', '30-73456789-1', 'RI', 0, 'Compra repuestos para sus clientes'),
('Heladeria Arcoiris', '+5491188884567', 'heladeriaarcoiris@email.com', 'Av. Rivadavia 3200, CABA', '27-2845678901-2', 'MONOTRIBUTISTA', 0, 'Sector refrigeracion'),
('Bar El Rincon', '+5491199995678', 'elrincon@email.com', 'Av. de Mayo 1500, CABA', '30-74567890-2', 'RI', 0, 'Paga en efectivo'),
('Cafeteria Aroma', '+5491122226789', 'aroma@email.com', 'Av. Santa Fe 2800, CABA', '27-2756789012-3', 'MONOTRIBUTISTA', 0, 'Sector cafeteria'),
('Bodegon El Tropezon', '+5491133337890', 'eltropezon@email.com', 'Av. Belgrano 670, CABA', '30-75678901-3', 'RI', 0, 'Compra esporadica'),
('Rotiseria Sabor Casero', '+5491144448901', 'saborcasero@email.com', 'Av. San Juan 2300, CABA', '27-2667890123-4', 'MONOTRIBUTISTA', 0, 'Pedidos por WhatsApp');

-- Venta 1: Pizzeria Don Carlos - hace 12 dias - PAGADA
INSERT INTO ventas (cliente_id, usuario_id, fecha, total, estado, observaciones)
SELECT (SELECT id FROM clientes WHERE nombre = 'Pizzeria Don Carlos'),
       (SELECT id FROM usuarios WHERE username = 'admin'),
       CURRENT_TIMESTAMP - INTERVAL '12 days', 46890.00, 'PAGADA', 'Quemador + valvula de seguridad';

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal)
SELECT (SELECT id FROM ventas ORDER BY id LIMIT 1), p.id, 1, p.precio, p.precio
FROM productos p WHERE p.codigo_barras = 'ART-001';

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal)
SELECT (SELECT id FROM ventas ORDER BY id LIMIT 1), p.id, 1, p.precio, p.precio
FROM productos p WHERE p.codigo_barras = 'ART-103';

INSERT INTO pagos (venta_id, monto, metodo_pago, moneda, fecha, usuario_id)
SELECT (SELECT id FROM ventas ORDER BY id LIMIT 1), 46890.00, 'EFECTIVO', 'ARS',
       CURRENT_TIMESTAMP - INTERVAL '12 days', (SELECT id FROM usuarios WHERE username = 'admin');

-- Venta 2: Restaurant La Parrilla - hace 10 dias - PAGADA
INSERT INTO ventas (cliente_id, usuario_id, fecha, total, estado, observaciones)
SELECT (SELECT id FROM clientes WHERE nombre = 'Restaurant La Parrilla'),
       (SELECT id FROM usuarios WHERE username = 'admin'),
       CURRENT_TIMESTAMP - INTERVAL '10 days', 98950.00, 'PAGADA', 'Termocuplas + perillas + valvulas + resistencia';

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal)
SELECT (SELECT id FROM ventas ORDER BY id DESC LIMIT 1), p.id, 3, p.precio, p.precio * 3
FROM productos p WHERE p.codigo_barras = 'ART-108';

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal)
SELECT (SELECT id FROM ventas ORDER BY id DESC LIMIT 1), p.id, 4, p.precio, p.precio * 4
FROM productos p WHERE p.codigo_barras = 'ART-106';

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal)
SELECT (SELECT id FROM ventas ORDER BY id DESC LIMIT 1), p.id, 5, p.precio, p.precio * 5
FROM productos p WHERE p.codigo_barras = 'ART-103';

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal)
SELECT (SELECT id FROM ventas ORDER BY id DESC LIMIT 1), p.id, 1, p.precio, p.precio
FROM productos p WHERE p.codigo_barras = 'ART-105';

INSERT INTO pagos (venta_id, monto, metodo_pago, moneda, fecha, usuario_id)
SELECT (SELECT id FROM ventas ORDER BY id DESC LIMIT 1), 98950.00, 'TARJETA', 'ARS',
       CURRENT_TIMESTAMP - INTERVAL '10 days', (SELECT id FROM usuarios WHERE username = 'admin');

-- Venta 3: Heladeria Arcoiris - hace 7 dias - PAGADA
INSERT INTO ventas (cliente_id, usuario_id, fecha, total, estado, observaciones)
SELECT (SELECT id FROM clientes WHERE nombre = 'Heladeria Arcoiris'),
       (SELECT id FROM usuarios WHERE username = 'admin'),
       CURRENT_TIMESTAMP - INTERVAL '7 days', 54000.00, 'PAGADA', 'Resistencia para heladera';

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal)
SELECT (SELECT id FROM ventas ORDER BY id DESC LIMIT 1), p.id, 1, p.precio, p.precio
FROM productos p WHERE p.codigo_barras = 'ART-105-B';

INSERT INTO pagos (venta_id, monto, metodo_pago, moneda, fecha, usuario_id)
SELECT (SELECT id FROM ventas ORDER BY id DESC LIMIT 1), 54000.00, 'TRANSFERENCIA', 'ARS',
       CURRENT_TIMESTAMP - INTERVAL '7 days', (SELECT id FROM usuarios WHERE username = 'admin');

-- Venta 4: Bar El Rincon - hace 5 dias - PAGADA
INSERT INTO ventas (cliente_id, usuario_id, fecha, total, estado, observaciones)
SELECT (SELECT id FROM clientes WHERE nombre = 'Bar El Rincon'),
       (SELECT id FROM usuarios WHERE username = 'admin'),
       CURRENT_TIMESTAMP - INTERVAL '5 days', 59000.00, 'PAGADA', 'Resistencia cafetera + perillas';

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal)
SELECT (SELECT id FROM ventas ORDER BY id DESC LIMIT 1), p.id, 1, p.precio, p.precio
FROM productos p WHERE p.codigo_barras = 'ART-105';

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal)
SELECT (SELECT id FROM ventas ORDER BY id DESC LIMIT 1), p.id, 2, p.precio, p.precio * 2
FROM productos p WHERE p.codigo_barras = 'ART-106';

INSERT INTO pagos (venta_id, monto, metodo_pago, moneda, fecha, usuario_id)
SELECT (SELECT id FROM ventas ORDER BY id DESC LIMIT 1), 59000.00, 'EFECTIVO', 'ARS',
       CURRENT_TIMESTAMP - INTERVAL '5 days', (SELECT id FROM usuarios WHERE username = 'admin');

-- Venta 5: Cocina Industrial Mendez - hace 3 dias - PARCIAL (debe 47000)
INSERT INTO ventas (cliente_id, usuario_id, fecha, total, estado, observaciones)
SELECT (SELECT id FROM clientes WHERE nombre = 'Cocina Industrial Mendez'),
       (SELECT id FROM usuarios WHERE username = 'admin'),
       CURRENT_TIMESTAMP - INTERVAL '3 days', 112670.00, 'PARCIAL', '2 quemadores + valvulas + termocuplas';

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal)
SELECT (SELECT id FROM ventas ORDER BY id DESC LIMIT 1), p.id, 2, p.precio, p.precio * 2
FROM productos p WHERE p.codigo_barras = 'ART-001';

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal)
SELECT (SELECT id FROM ventas ORDER BY id DESC LIMIT 1), p.id, 3, p.precio, p.precio * 3
FROM productos p WHERE p.codigo_barras = 'ART-103';

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal)
SELECT (SELECT id FROM ventas ORDER BY id DESC LIMIT 1), p.id, 2, p.precio, p.precio * 2
FROM productos p WHERE p.codigo_barras = 'ART-108';

INSERT INTO pagos (venta_id, monto, metodo_pago, moneda, fecha, usuario_id)
SELECT (SELECT id FROM ventas ORDER BY id DESC LIMIT 1), 65670.00, 'TRANSFERENCIA', 'ARS',
       CURRENT_TIMESTAMP - INTERVAL '3 days', (SELECT id FROM usuarios WHERE username = 'admin');

-- Venta 6: Cafeteria Aroma - ayer - PAGADA
INSERT INTO ventas (cliente_id, usuario_id, fecha, total, estado, observaciones)
SELECT (SELECT id FROM clientes WHERE nombre = 'Cafeteria Aroma'),
       (SELECT id FROM usuarios WHERE username = 'admin'),
       CURRENT_TIMESTAMP - INTERVAL '1 day', 56500.00, 'PAGADA', 'Resistencia + perilla';

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal)
SELECT (SELECT id FROM ventas ORDER BY id DESC LIMIT 1), p.id, 1, p.precio, p.precio
FROM productos p WHERE p.codigo_barras = 'ART-105';

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal)
SELECT (SELECT id FROM ventas ORDER BY id DESC LIMIT 1), p.id, 1, p.precio, p.precio
FROM productos p WHERE p.codigo_barras = 'ART-106';

INSERT INTO pagos (venta_id, monto, metodo_pago, moneda, fecha, usuario_id)
SELECT (SELECT id FROM ventas ORDER BY id DESC LIMIT 1), 56500.00, 'EFECTIVO', 'ARS',
       CURRENT_TIMESTAMP - INTERVAL '1 day', (SELECT id FROM usuarios WHERE username = 'admin');

-- Venta 7: Rotiseria Sabor Casero - hoy - PENDIENTE (no pago nada)
INSERT INTO ventas (cliente_id, usuario_id, fecha, total, estado, observaciones)
SELECT (SELECT id FROM clientes WHERE nombre = 'Rotiseria Sabor Casero'),
       (SELECT id FROM usuarios WHERE username = 'admin'),
       CURRENT_TIMESTAMP, 46890.00, 'PENDIENTE', 'Quemador + valvula';

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal)
SELECT (SELECT id FROM ventas ORDER BY id DESC LIMIT 1), p.id, 1, p.precio, p.precio
FROM productos p WHERE p.codigo_barras = 'ART-001';

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal)
SELECT (SELECT id FROM ventas ORDER BY id DESC LIMIT 1), p.id, 1, p.precio, p.precio
FROM productos p WHERE p.codigo_barras = 'ART-103';

-- Actualizar saldos corrientes (solo los que tienen deuda)
UPDATE clientes SET saldo_corriente = 47000.00 WHERE nombre = 'Cocina Industrial Mendez';
UPDATE clientes SET saldo_corriente = 46890.00 WHERE nombre = 'Rotiseria Sabor Casero';
