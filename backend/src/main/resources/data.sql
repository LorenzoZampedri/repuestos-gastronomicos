INSERT INTO usuarios (username, password_hash, nombre, email, rol, activo, created_at, updated_at)
SELECT 'admin', '$2a$10$LfFSXufa/KTF9Ef3uB8yF.nFtI/op/Codekup4oPf0hiZ0ZYRSfUG', 'Administrador', 'admin@repuestos.com', 'ADMIN', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM usuarios WHERE username = 'admin');

INSERT INTO categorias (nombre, descripcion, activa, created_at)
SELECT 'Hornos Pizzeros', 'Hornos para preparación de pizzas', true, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categorias WHERE nombre = 'Hornos Pizzeros');

INSERT INTO categorias (nombre, descripcion, activa, created_at)
SELECT 'Freidoras', 'Freidoras industriales para gastronomía', true, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categorias WHERE nombre = 'Freidoras');

INSERT INTO categorias (nombre, descripcion, activa, created_at)
SELECT 'Anafes', 'Anafes y cocinas industriales', true, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categorias WHERE nombre = 'Anafes');

INSERT INTO categorias (nombre, descripcion, activa, created_at)
SELECT 'Cafeteras', 'Cafeteras de bar y uso comercial', true, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categorias WHERE nombre = 'Cafeteras');

INSERT INTO categorias (nombre, descripcion, activa, created_at)
SELECT 'Refrigeración', 'Equipos de refrigeración industrial', true, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categorias WHERE nombre = 'Refrigeración');

INSERT INTO categorias (nombre, descripcion, activa, created_at)
SELECT 'Lavado', 'Lavavajillas y equipos de limpieza', true, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categorias WHERE nombre = 'Lavado');

INSERT INTO categorias (nombre, descripcion, activa, created_at)
SELECT 'Utensilios', 'Utensilios y herramientas de cocina', true, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categorias WHERE nombre = 'Utensilios');

INSERT INTO categorias (nombre, descripcion, activa, created_at)
SELECT 'Repuestos', 'Repuestos y componentes varios', true, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categorias WHERE nombre = 'Repuestos');
