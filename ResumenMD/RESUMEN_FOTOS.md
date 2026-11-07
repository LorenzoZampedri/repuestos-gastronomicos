# Resumen: fotos de perfil de usuarios y clientes

Rama `feature/precios-masivos` (base `main`). Implementa fotos de perfil privadas para usuarios y clientes, con procesamiento en servidor, compartición automática cuando la cuenta y la ficha están vinculadas, reconciliación de vínculos, auditoría y una interfaz de selección/recorte desde el navegador.

## 1. Qué quedó funcionando

- **Foto en Usuario y Cliente**: ambos modelos tienen `fotoUrl` (nullable). El archivo se guarda en `app.avatar-dir` (por defecto `./avatares`, en Docker `/app/avatares`), fuera de `uploads`.
- **Procesamiento seguro en el servidor**:
  - Solo acepta JPEG o PNG validados por firma (no por extensión ni `Content-Type`).
  - Límite de 5 MB, 8000 px por lado y 25 millones de píxeles.
  - Recorte cuadrado centrado, redimensionado a un máximo de 512×512 (nunca agranda).
  - Fondo blanco para transparencias PNG.
  - Salida siempre JPEG calidad 0.85 sin metadatos EXIF.
  - Nombres aleatorios UUID y validación estricta contra path traversal.
- **Foto compartida e invariante**:
  - Si el usuario está vinculado a una ficha de cliente, cambiar o quitar la foto en cualquiera de los dos lados actualiza ambos registros con la misma URL.
  - Sin vínculo, las fotos son independientes.
  - Al crear o revincular una ficha se reconcilia: si solo uno tiene foto, se copia al otro; si ambos tienen fotos distintas, gana la del usuario y la del cliente se elimina si queda huérfana.
  - El cliente anterior de una revinculación conserva su `fotoUrl`; el archivo no se borra si sigue referenciado.
- **Limpieza diferida**:
  - Los archivos viejos se eliminan `afterCommit` y solo si ya no están referenciados por `usuarios.foto_url` ni `clientes.foto_url`.
  - Si la transacción falla, el archivo nuevo se elimina en `afterCompletion` con estado `ROLLED_BACK`.
- **Privacidad**:
  - `GET /api/avatares/{archivo}` requiere autenticación.
  - `ADMIN` y `EMPLEADO` pueden ver cualquier avatar; `CLIENTE` solo el suyo (404 para el resto).
  - El frontend descarga la imagen como blob autenticado y la muestra con un object URL cacheado por sesión.
- **Auditoría**: una fila por acción (`USUARIO_FOTO_CAMBIADA`, `USUARIO_FOTO_ELIMINADA`, `CLIENTE_FOTO_CAMBIADA`, `CLIENTE_FOTO_ELIMINADA`) con `sincronizadoCon` cuando corresponde. Las acciones del propio `CLIENTE` sobre su foto siguen la política existente de no auditar.
- **Interfaz**:
  - Componente `Avatar` con foto, iniciales o icono de respaldo.
  - Modal `SelectorFoto` con vista previa, validación local de tipo/tamaño, aviso de foto compartida y opción de quitar.
  - Integración en el panel lateral, catálogo público, listado/edición de usuarios, listado/edición de clientes y detalle/listado de pedidos.

## 2. Endpoints

| Método y ruta | Permiso | Descripción |
|---|---|---|
| `PUT /api/auth/foto` | Autenticado | Cambia la foto del usuario actual |
| `DELETE /api/auth/foto` | Autenticado | Quita la foto del usuario actual |
| `PUT /api/usuarios/{id}/foto` | ADMIN | Cambia la foto de un usuario |
| `DELETE /api/usuarios/{id}/foto` | ADMIN | Quita la foto de un usuario |
| `PUT /api/clientes/{id}/foto` | ADMIN, EMPLEADO | Cambia la foto de un cliente |
| `DELETE /api/clientes/{id}/foto` | ADMIN, EMPLEADO | Quita la foto de un cliente |
| `GET /api/avatares/{archivo}` | Autenticado | Entrega el JPEG; CLIENTE solo puede ver el propio |

## 3. Archivos

### Creados

| Capa | Archivos |
|---|---|
| Servicios | `services/AvatarStorageService.java`, `services/FotoPerfilService.java` |
| API | `controllers/AvatarController.java` |
| Tests | `AvatarStorageServiceTest`, `FotoPerfilServiceTest`, `FotoPerfilServiceRollbackTest` |
| Frontend | `components/Avatar.jsx`, `components/SelectorFoto.jsx`, `lib/avatarCache.js` |

### Modificados

| Archivo | Cambio |
|---|---|
| `models/Usuario.java`, `models/Cliente.java` | Campo `fotoUrl` |
| `models/AccionAuditoria.java` | Acciones de foto para usuario y cliente |
| `repositories/UsuarioRepository.java`, `repositories/ClienteRepository.java` | Conteo de referencias y clientes con cuenta |
| `services/ClienteService.java` | Reconciliación al crear/revincular fichas |
| `config/ClienteUsuarioMigration.java` | Reconciliación en la migración inicial |
| `controllers/AuthController.java`, `UsuarioController.java`, `ClienteController.java` | Endpoints de foto y `fotoUrl` en respuestas |
| `controllers/GlobalExceptionHandler.java` | `ImagenInvalidaException` → 400 |
| `security/SecurityConfig.java` | Matchers de fotos/avatares y orden de `DELETE /api/clientes/**` |
| `dto/UsuarioResponse.java`, `ClienteResponse.java`, `UsuarioResumenResponse.java`, `ClientePedidoResponse.java` | Exposición de `fotoUrl` y `cuentaVinculada` |
| `resources/application.properties`, `docker-compose.yml`, `backend/Dockerfile`, `.gitignore` | Directorio y volumen de avatares |
| `frontend/src/context/AuthContext.jsx` | `fotoUrl` en sesión y `actualizarFoto` |
| `frontend/src/components/Layout.jsx`, `PublicLayout.jsx` | Avatar propio y acceso al selector |
| `frontend/src/pages/Usuarios.jsx`, `Clientes.jsx`, `ClienteForm.jsx`, `Pedidos.jsx` | Avatares y cambio de foto |
| `README.md` | Documentación de la funcionalidad |

## 4. Resultados de verificación

| Qué se corrió | Resultado |
|---|---|
| Tests de backend completos (Docker `maven:3.9-eclipse-temurin-17`) | **240/240 tests exitosos**, 0 fallos, 0 errores |
| Build frontend (Docker `node:20-alpine`, `npm run build`) | **OK** (dist generado) |
| `ImageIO` JPEG en `eclipse-temurin:17-jre-alpine` | **OK** (módulo `java.desktop` disponible y escritura JPEG verificada) |

## 5. Deuda y riesgos

- **Orientación EXIF**: se eliminan los metadatos, pero no se aplica rotación automática según la orientación original. Una foto de celular podría verse rotada.
- **Caché de avatares**: los object URLs se guardan por sesión y se liberan al cerrar sesión. Si un administrador cambia la foto de otro usuario, ese usuario la verá actualizada al recargar la página o al volver a iniciar sesión (la URL cambia).
- **Descargas por listado**: cada avatar visible requiere una petición autenticada. La caché evita repetirlas, pero listados muy grandes podrían generar varias solicitudes simultáneas.
- **Almacenamiento local**: los avatares viven en un volumen Docker (`avatares`), separado de `uploads`; no se sincronizan entre múltiples instancias del backend.
