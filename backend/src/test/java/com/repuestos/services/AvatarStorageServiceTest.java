package com.repuestos.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvatarStorageServiceTest {

    @TempDir
    Path tempDir;

    private AvatarStorageService servicio() {
        return new AvatarStorageService(tempDir.toString());
    }

    private MockMultipartFile archivo(String nombre, byte[] contenido) {
        return new MockMultipartFile("file", nombre, "application/octet-stream", contenido);
    }

    private byte[] jpeg(int ancho, int alto, Color color) throws IOException {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        try {
            g.setColor(color);
            g.fillRect(0, 0, ancho, alto);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(imagen, "jpg", out);
        return out.toByteArray();
    }

    private byte[] png(int ancho, int alto, Color color) throws IOException {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = imagen.createGraphics();
        try {
            g.setColor(color);
            g.fillRect(0, 0, ancho, alto);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", out);
        return out.toByteArray();
    }

    private BufferedImage leer(Path archivo) throws IOException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(Files.readAllBytes(archivo))) {
            return ImageIO.read(in);
        }
    }

    private Path archivoDe(String url) {
        return tempDir.resolve(url.substring("/api/avatares/".length()));
    }

    private long archivosEn(Path directorio) throws IOException {
        if (!Files.exists(directorio)) {
            return 0;
        }
        try (Stream<Path> stream = Files.list(directorio)) {
            List<Path> archivos = stream.filter(Files::isRegularFile).toList();
            return archivos.size();
        }
    }

    @Test
    void aceptaJpegValidoYLoRecodificaComoJpegCuadrado() throws IOException {
        String url = servicio().guardar(archivo("foto.jpg", jpeg(1000, 800, Color.BLUE)));

        assertTrue(url.matches("^/api/avatares/[0-9a-f-]{36}\\.jpg$"), "URL inesperada: " + url);
        Path archivo = archivoDe(url);
        assertTrue(Files.exists(archivo));

        BufferedImage resultado = leer(archivo);
        assertNotNull(resultado);
        assertEquals(512, resultado.getWidth());
        assertEquals(512, resultado.getHeight());
        assertEquals(1, archivosEn(tempDir), "solo debe escribirse dentro del directorio de avatares");
    }

    @Test
    void aceptaPngValidoYLoConvierteAJpeg() throws IOException {
        String url = servicio().guardar(archivo("foto.png", png(300, 200, Color.GREEN)));

        assertTrue(url.endsWith(".jpg"));
        BufferedImage resultado = leer(archivoDe(url));
        assertNotNull(resultado);
        assertEquals(200, resultado.getWidth());
        assertEquals(200, resultado.getHeight());
    }

    @Test
    void noAgrandaImagenesMasChicasQue512() throws IOException {
        String url = servicio().guardar(archivo("chica.jpg", jpeg(120, 80, Color.RED)));

        BufferedImage resultado = leer(archivoDe(url));
        assertEquals(80, resultado.getWidth());
        assertEquals(80, resultado.getHeight());
    }

    @Test
    void pngConTransparenciaQuedaSobreFondoBlanco() throws IOException {
        BufferedImage transparente = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = transparente.createGraphics();
        try {
            g.setColor(new Color(255, 0, 0, 255));
            g.fillRect(75, 75, 50, 50);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(transparente, "png", out);

        String url = servicio().guardar(archivo("transparente.png", out.toByteArray()));
        BufferedImage resultado = leer(archivoDe(url));
        Color esquina = new Color(resultado.getRGB(0, 0));
        assertTrue(esquina.getRed() > 240 && esquina.getGreen() > 240 && esquina.getBlue() > 240,
                "la zona transparente debe quedar blanca, fue " + esquina);
    }

    @Test
    void eliminaLosMetadatosExif() throws IOException {
        byte[] conExif = insertarApp1Exif(jpeg(400, 400, Color.ORANGE));
        assertTrue(tieneMarcadorApp1(conExif), "el archivo de prueba debe contener APP1");

        String url = servicio().guardar(archivo("celular.jpg", conExif));
        byte[] resultado = Files.readAllBytes(archivoDe(url));

        assertFalse(tieneMarcadorApp1(resultado), "la salida no debe conservar el segmento APP1/Exif");
        assertFalse(new String(resultado, StandardCharsets.ISO_8859_1).contains("Exif"));
    }

    @Test
    void rechazaGif() {
        byte[] gif = "GIF89a\u0001\u0000\u0001\u0000".getBytes(StandardCharsets.ISO_8859_1);

        ImagenInvalidaException ex = assertThrows(ImagenInvalidaException.class,
                () -> servicio().guardar(archivo("animado.gif", gif)));
        assertEquals("Formato no permitido. Usá JPG o PNG", ex.getMessage());
    }

    @Test
    void rechazaWebp() {
        byte[] webp = new byte[]{'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'};

        assertThrows(ImagenInvalidaException.class,
                () -> servicio().guardar(archivo("foto.webp", webp)));
    }

    @Test
    void rechazaTextoRenombradoComoJpg() {
        byte[] texto = "<html>no soy una imagen</html>".getBytes(StandardCharsets.UTF_8);

        assertThrows(ImagenInvalidaException.class,
                () -> servicio().guardar(archivo("falsa.jpg", texto)));
    }

    @Test
    void rechazaArchivoVacio() {
        ImagenInvalidaException ex = assertThrows(ImagenInvalidaException.class,
                () -> servicio().guardar(archivo("vacio.jpg", new byte[0])));
        assertEquals("El archivo está vacío", ex.getMessage());
    }

    @Test
    void rechazaArchivoMayorA5Mb() {
        byte[] grande = new byte[(int) AvatarStorageService.MAX_BYTES + 1];
        grande[0] = (byte) 0xFF;
        grande[1] = (byte) 0xD8;
        grande[2] = (byte) 0xFF;

        ImagenInvalidaException ex = assertThrows(ImagenInvalidaException.class,
                () -> servicio().guardar(archivo("grande.jpg", grande)));
        assertEquals("La imagen supera el máximo de 5 MB", ex.getMessage());
    }

    @Test
    void rechazaImagenCorruptaConFirmaValida() {
        byte[] corrupta = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF,
                (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00};

        assertThrows(ImagenInvalidaException.class,
                () -> servicio().guardar(archivo("corrupta.jpg", corrupta)));
    }

    @Test
    void rechazaDimensionesExcesivasAntesDeDecodificar() throws IOException {
        byte[] enorme = pngConDimensiones(8001, 10);

        ImagenInvalidaException ex = assertThrows(ImagenInvalidaException.class,
                () -> servicio().guardar(archivo("enorme.png", enorme)));
        assertTrue(ex.getMessage().contains("dimensiones excesivas"));
    }

    @Test
    void rechazaBombaDeDescompresionPorCantidadDePixeles() throws IOException {
        byte[] bomba = pngConDimensiones(6000, 5000);

        ImagenInvalidaException ex = assertThrows(ImagenInvalidaException.class,
                () -> servicio().guardar(archivo("bomba.png", bomba)));
        assertTrue(ex.getMessage().contains("dimensiones excesivas"));
    }

    @Test
    void cargarSoloAceptaNombresUuidJpgEnMinusculas() throws IOException {
        AvatarStorageService servicio = servicio();
        String url = servicio.guardar(archivo("foto.jpg", jpeg(64, 64, Color.CYAN)));
        String nombre = url.substring("/api/avatares/".length());

        assertNotNull(servicio.cargar(nombre));
        assertNull(servicio.cargar("../" + nombre));
        assertNull(servicio.cargar("sub/" + nombre));
        assertNull(servicio.cargar(nombre.toUpperCase()));
        assertNull(servicio.cargar("no-es-un-uuid.jpg"));
        assertNull(servicio.cargar("11111111-1111-1111-1111-111111111111.jpg"));
    }

    @Test
    void elArchivoSeEscribeSoloDentroDeAvatarDir() throws IOException {
        String url = servicio().guardar(archivo("../../etc/passwd.jpg", jpeg(32, 32, Color.PINK)));
        Path archivo = archivoDe(url);

        assertTrue(archivo.normalize().startsWith(tempDir.toAbsolutePath().normalize()));
        assertEquals(1, archivosEn(tempDir));
        UUID.fromString(archivo.getFileName().toString().replace(".jpg", ""));
    }

    @Test
    void eliminarEsSeguroEIdempotente() throws IOException {
        AvatarStorageService servicio = servicio();
        String url = servicio.guardar(archivo("foto.jpg", jpeg(64, 64, Color.GRAY)));
        Path archivo = archivoDe(url);
        assertTrue(Files.exists(archivo));

        servicio.eliminar(url);
        assertFalse(Files.exists(archivo));
        servicio.eliminar(url);
        servicio.eliminar(null);
        servicio.eliminar("../../etc/passwd.jpg");
    }

    private byte[] insertarApp1Exif(byte[] jpeg) {
        byte[] payload = new byte[]{'E', 'x', 'i', 'f', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] segmento = new byte[2 + 2 + payload.length];
        segmento[0] = (byte) 0xFF;
        segmento[1] = (byte) 0xE1;
        int largo = 2 + payload.length;
        segmento[2] = (byte) ((largo >> 8) & 0xFF);
        segmento[3] = (byte) (largo & 0xFF);
        System.arraycopy(payload, 0, segmento, 4, payload.length);

        byte[] resultado = new byte[jpeg.length + segmento.length];
        System.arraycopy(jpeg, 0, resultado, 0, 2);
        System.arraycopy(segmento, 0, resultado, 2, segmento.length);
        System.arraycopy(jpeg, 2, resultado, 2 + segmento.length, jpeg.length - 2);
        return resultado;
    }

    private boolean tieneMarcadorApp1(byte[] jpeg) {
        int i = 2;
        while (i + 3 < jpeg.length) {
            if ((jpeg[i] & 0xFF) != 0xFF) {
                i++;
                continue;
            }
            int marcador = jpeg[i + 1] & 0xFF;
            if (marcador == 0xDA) {
                return false;
            }
            if (marcador == 0xE1) {
                return true;
            }
            if (marcador == 0xD8 || marcador == 0xD9) {
                i += 2;
                continue;
            }
            int largo = ((jpeg[i + 2] & 0xFF) << 8) | (jpeg[i + 3] & 0xFF);
            i += 2 + largo;
        }
        return false;
    }

    private byte[] pngConDimensiones(int ancho, int alto) throws IOException {
        byte[] png = png(10, 10, Color.BLACK);
        png[16] = (byte) ((ancho >> 24) & 0xFF);
        png[17] = (byte) ((ancho >> 16) & 0xFF);
        png[18] = (byte) ((ancho >> 8) & 0xFF);
        png[19] = (byte) (ancho & 0xFF);
        png[20] = (byte) ((alto >> 24) & 0xFF);
        png[21] = (byte) ((alto >> 16) & 0xFF);
        png[22] = (byte) ((alto >> 8) & 0xFF);
        png[23] = (byte) (alto & 0xFF);

        CRC32 crc = new CRC32();
        crc.update(png, 12, 17);
        long valor = crc.getValue();
        png[29] = (byte) ((valor >> 24) & 0xFF);
        png[30] = (byte) ((valor >> 16) & 0xFF);
        png[31] = (byte) ((valor >> 8) & 0xFF);
        png[32] = (byte) (valor & 0xFF);
        return png;
    }
}
