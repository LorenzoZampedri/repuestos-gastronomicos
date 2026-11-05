package com.repuestos.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImagenStorageServiceTest {

    private static final byte[] JPEG = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
    private static final byte[] PNG = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
    private static final byte[] WEBP = new byte[]{'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'};

    @TempDir
    Path tempDir;

    private ImagenStorageService servicio() {
        return new ImagenStorageService(tempDir.toString());
    }

    private MockMultipartFile archivo(String nombre, byte[] contenido) {
        return new MockMultipartFile("file", nombre, "application/octet-stream", contenido);
    }

    @Test
    void detectaJpegPorMagicBytes() throws IOException {
        String url = servicio().guardar(archivo("foto.txt", JPEG));

        assertTrue(url.matches("^/api/uploads/[0-9a-f-]{36}\\.jpg$"), "URL inesperada: " + url);
        assertTrue(Files.exists(tempDir.resolve(url.substring("/api/uploads/".length()))));
    }

    @Test
    void detectaPngPorMagicBytes() throws IOException {
        String url = servicio().guardar(archivo("foto.jpg", PNG));

        assertTrue(url.endsWith(".png"), "URL inesperada: " + url);
    }

    @Test
    void detectaWebpPorMagicBytes() throws IOException {
        String url = servicio().guardar(archivo("foto.jpg", WEBP));

        assertTrue(url.endsWith(".webp"), "URL inesperada: " + url);
    }

    @Test
    void usaNombreUuidYExtensionDelTipoDetectado() throws IOException {
        String url = servicio().guardar(archivo("../../etc/passwd.jpg", JPEG));
        String nombre = url.substring("/api/uploads/".length());

        assertTrue(Pattern.matches("[0-9a-f-]{36}\\.jpg", nombre), "nombre inesperado: " + nombre);
        UUID.fromString(nombre.substring(0, nombre.length() - 4));
        assertTrue(Files.exists(tempDir.resolve(nombre)));
        assertEquals(1, archivosEn(tempDir), "solo debe guardarse dentro del directorio de subidas");
    }

    @Test
    void rechazaSvg() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(StandardCharsets.UTF_8);

        ImagenInvalidaException ex = assertThrows(ImagenInvalidaException.class,
                () -> servicio().guardar(archivo("imagen.svg", svg)));
        assertEquals("Formato no permitido. Usá JPG, PNG o WebP", ex.getMessage());
    }

    @Test
    void rechazaTextoRenombradoComoJpg() {
        byte[] texto = "<html><body>no soy una imagen</body></html>".getBytes(StandardCharsets.UTF_8);

        assertThrows(ImagenInvalidaException.class,
                () -> servicio().guardar(archivo("falsa.jpg", texto)));
    }

    @Test
    void rechazaPngConFirmaFalsa() {
        byte[] firmaFalsa = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x48, 0x0D, 0x0A, 0x1A, 0x0A};

        assertThrows(ImagenInvalidaException.class,
                () -> servicio().guardar(archivo("falso.png", firmaFalsa)));
    }

    @Test
    void rechazaArchivoVacio() {
        ImagenInvalidaException ex = assertThrows(ImagenInvalidaException.class,
                () -> servicio().guardar(archivo("vacio.jpg", new byte[0])));
        assertEquals("El archivo está vacío", ex.getMessage());
    }

    @Test
    void rechazaArchivoMayorA5Mb() {
        byte[] grande = new byte[(int) ImagenStorageService.MAX_BYTES + 1];
        grande[0] = (byte) 0xFF;
        grande[1] = (byte) 0xD8;
        grande[2] = (byte) 0xFF;

        ImagenInvalidaException ex = assertThrows(ImagenInvalidaException.class,
                () -> servicio().guardar(archivo("grande.jpg", grande)));
        assertEquals("La imagen supera el máximo de 5 MB", ex.getMessage());
    }

    @Test
    void rechazaPathTraversalAlResolverDestino() {
        assertThrows(ImagenInvalidaException.class,
                () -> ImagenStorageService.resolverDestino(tempDir, "../../etc/passwd.jpg"));
        assertThrows(ImagenInvalidaException.class,
                () -> ImagenStorageService.resolverDestino(tempDir, "sub/../../../fuera.jpg"));
    }

    private long archivosEn(Path directorio) throws IOException {
        try (Stream<Path> stream = Files.list(directorio)) {
            List<Path> archivos = stream.filter(Files::isRegularFile).toList();
            return archivos.size();
        }
    }
}
