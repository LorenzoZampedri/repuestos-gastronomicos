-- ═══════════════════════════════════════════════════════════
-- SEED: Clientes, Ventas, Detalles y Pagos
-- ═══════════════════════════════════════════════════════════

-- Clientes
INSERT INTO clientes (nombre, telefono, email, direccion, cuit, condicion_iva, saldo_corriente, observaciones) VALUES
('Pizzería Don Carlos', '+5491155551234', 'doncarlos@email.com', 'Av. San Martín 1250, CABA', '30-71234567-9', 'RI', 0, 'Cliente frecuente, compra mensual'),
('Restaurant La Parrilla', '+5491166662345', 'laparrilla@email.com', 'Av. Corrientes 4580, CABA', '30-72345678-0', 'RI', 0, 'Compra constante de repuestos'),
('Cocina Industrial Mendez', '+5491177773456', 'mendez@email.com', 'Av. Callao 890, CABA', '30-73456789-1', 'RI', 0, 'Compra repuestos para sus clientes'),
('Heladería Arcoíris', '+5491188884567', 'heladeriaarcoiris@email.com', 'Av. Rivadavia 3200, CABA', '27-2845678901-2', 'MONOTRIBUTISTA', 0, 'Sector refrigeración'),
('Bar El Rincón', '+5491199995678', 'elrincon@email.com', 'Av. de Mayo 1500, CABA', '30-74567890-2', 'RI', 0, 'Paga en efectivo'),
('Cafetería Aroma', '+5491122226789', 'aroma@email.com', 'Av. Santa Fe 2800, CABA', '27-2756789012-3', 'MONOTRIBUTISTA', 0, 'Sector cafetería'),
('Bodegón El Tropezón', '+5491133337890', 'eltropezon@email.com', 'Av. Belgrano 670, CABA', '30-75678901-3', 'RI', 0, 'Compra esporádica'),
('Rotisería Sabor Casero', '+5491144448901', 'saborcasero@email.com', 'Av. San Juan 2300, CABA', '27-2667890123-4', 'MONOTRIBUTISTA', 0, 'Pedidos por WhatsApp');

-- Venta 1: Pizzería Don Carlos - hace 12 días - PAGADA
-- Productos: 1 Quemador (45000) + 1 Válvula (1890) = 46890
INSERT INTO ventas (cliente_id, usuario_id, fecha, total, estado, observaciones) VALUES
(1, 1, CURRENT_TIMESTAMP - INTERVAL '12 days', 46890.00, 'PAGADA', 'Quemador + válvula de seguridad');

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal) VALUES
(1, 1, 1, 45000.00, 45000.00),
(1, 3, 1, 1890.00, 1890.00);

INSERT INTO pagos (venta_id, monto, metodo_pago, moneda, fecha, usuario_id) VALUES
(1, 46890.00, 'EFECTIVO', 'ARS', CURRENT_TIMESTAMP - INTERVAL '12 days', 1);

-- Venta 2: Restaurant La Parrilla - hace 10 días - PAGADA
-- Productos: 3 Termocuplas (25500) + 4 Perillas (10000) + 5 Válvulas (9450) + 1 Resistencia (54000) = 98950
INSERT INTO ventas (cliente_id, usuario_id, fecha, total, estado, observaciones) VALUES
(2, 1, CURRENT_TIMESTAMP - INTERVAL '10 days', 98950.00, 'PAGADA', 'Termocuplas + perillas + válvulas + resistencia');

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal) VALUES
(2, 6, 3, 8500.00, 25500.00),
(2, 5, 4, 2500.00, 10000.00),
(2, 3, 5, 1890.00, 9450.00),
(2, 9, 1, 54000.00, 54000.00);

INSERT INTO pagos (venta_id, monto, metodo_pago, moneda, fecha, usuario_id) VALUES
(2, 98950.00, 'TARJETA', 'ARS', CURRENT_TIMESTAMP - INTERVAL '10 days', 1);

-- Venta 3: Heladería Arcoíris - hace 7 días - PAGADA
-- Productos: 1 Resistencia 220V (54000) = 54000
INSERT INTO ventas (cliente_id, usuario_id, fecha, total, estado, observaciones) VALUES
(4, 1, CURRENT_TIMESTAMP - INTERVAL '7 days', 54000.00, 'PAGADA', 'Resistencia para heladera');

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal) VALUES
(3, 10, 1, 54000.00, 54000.00);

INSERT INTO pagos (venta_id, monto, metodo_pago, moneda, fecha, usuario_id) VALUES
(3, 54000.00, 'TRANSFERENCIA', 'ARS', CURRENT_TIMESTAMP - INTERVAL '7 days', 1);

-- Venta 4: Bar El Rincón - hace 5 días - PAGADA
-- Productos: 1 Resistencia (54000) + 2 Perillas (5000) = 59000
INSERT INTO ventas (cliente_id, usuario_id, fecha, total, estado, observaciones) VALUES
(5, 1, CURRENT_TIMESTAMP - INTERVAL '5 days', 59000.00, 'PAGADA', 'Resistencia cafetera + perillas');

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal) VALUES
(4, 9, 1, 54000.00, 54000.00),
(4, 5, 2, 2500.00, 5000.00);

INSERT INTO pagos (venta_id, monto, metodo_pago, moneda, fecha, usuario_id) VALUES
(4, 59000.00, 'EFECTIVO', 'ARS', CURRENT_TIMESTAMP - INTERVAL '5 days', 1);

-- Venta 5: Cocina Industrial Mendez - hace 3 días - PARCIAL (debe 47000)
-- Productos: 2 Quemadores (90000) + 3 Válvulas (5670) + 2 Termocuplas (17000) = 112670
INSERT INTO ventas (cliente_id, usuario_id, fecha, total, estado, observaciones) VALUES
(3, 1, CURRENT_TIMESTAMP - INTERVAL '3 days', 112670.00, 'PARCIAL', '2 quemadores + válvulas + termocuplas');

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal) VALUES
(5, 1, 2, 45000.00, 90000.00),
(5, 3, 3, 1890.00, 5670.00),
(5, 6, 2, 8500.00, 17000.00);

INSERT INTO pagos (venta_id, monto, metodo_pago, moneda, fecha, usuario_id) VALUES
(5, 65670.00, 'TRANSFERENCIA', 'ARS', CURRENT_TIMESTAMP - INTERVAL '3 days', 1);

-- Venta 6: Cafetería Aroma - ayer - PAGADA
-- Productos: 1 Resistencia (54000) + 1 Perilla (2500) = 56500
INSERT INTO ventas (cliente_id, usuario_id, fecha, total, estado, observaciones) VALUES
(6, 1, CURRENT_TIMESTAMP - INTERVAL '1 day', 56500.00, 'PAGADA', 'Resistencia + perilla');

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal) VALUES
(6, 9, 1, 54000.00, 54000.00),
(6, 5, 1, 2500.00, 2500.00);

INSERT INTO pagos (venta_id, monto, metodo_pago, moneda, fecha, usuario_id) VALUES
(6, 56500.00, 'EFECTIVO', 'ARS', CURRENT_TIMESTAMP - INTERVAL '1 day', 1);

-- Venta 7: Rotisería Sabor Casero - hoy - PENDIENTE (no pagó nada)
-- Productos: 1 Quemador (45000) + 1 Válvula (1890) = 46890
INSERT INTO ventas (cliente_id, usuario_id, fecha, total, estado, observaciones) VALUES
(8, 1, CURRENT_TIMESTAMP, 46890.00, 'PENDIENTE', 'Quemador + válvula');

INSERT INTO detalle_venta (venta_id, producto_id, cantidad, precio_unitario, subtotal) VALUES
(7, 1, 1, 45000.00, 45000.00),
(7, 3, 1, 1890.00, 1890.00);

-- Actualizar saldos corrientes (solo los que tienen deuda)
UPDATE clientes SET saldo_corriente = 47000.00 WHERE id = 3; -- Mendez: 112670 - 65670
UPDATE clientes SET saldo_corriente = 46890.00 WHERE id = 8; -- Sabor Casero: no pagó
