package com.repuestos.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AvatarStorageService {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Pattern PATTERN_NOMBRE = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg$");
    private static final int MAX_DIMENSION = 8000;
    private static final long MAX_PIXELES = 25_000_000L;

    private final Path avatarDir;

    public AvatarStorageService(@Value("${app.avatar-dir:./avatares}") String avatarDir) {
        this.avatarDir = Paths.get(avatarDir).toAbsolutePath().normalize();
    }

    public String guardar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ImagenInvalidaException("El archivo está vacío");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ImagenInvalidaException("La imagen supera el máximo de 5 MB");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ImagenInvalidaException("No se pudo leer el archivo");
        }

        if (!esJpeg(bytes) && !esPng(bytes)) {
            throw new ImagenInvalidaException("Formato no permitido. Usá JPG o PNG");
        }

        BufferedImage procesada;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ImageInputStream iis = ImageIO.createImageInputStream(bais)) {
            if (iis == null) {
                throw new ImagenInvalidaException("No se pudo procesar la imagen");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new ImagenInvalidaException("Archivo de imagen corrupto o no decodificable");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION || (long) width * height > MAX_PIXELES) {
                    throw new ImagenInvalidaException("Imagen demasiado grande o con dimensiones excesivas");
                }

                BufferedImage img = reader.read(0);
                if (img == null) {
                    throw new ImagenInvalidaException("Archivo de imagen corrupto o no decodificable");
                }
                procesada = procesarImagen(img);
            } finally {
                reader.dispose();
            }
        } catch (ImagenInvalidaException e) {
            throw e;
        } catch (Exception e) {
            throw new ImagenInvalidaException("Archivo de imagen corrupto o no decodificable");
        }

        byte[] jpegBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) {
                throw new IllegalStateException("No hay escritor JPEG disponible");
            }
            ImageWriter writer = writers.next();
            try {
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(0.85f);
                }
                try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(procesada, null, null), param);
                }
            } finally {
                writer.dispose();
            }
            jpegBytes = baos.toByteArray();
        } catch (IOException e) {
            throw new ImagenInvalidaException("Error al codificar la imagen");
        }

        String nombreArchivo = UUID.randomUUID() + ".jpg";
        try {
            Files.createDirectories(avatarDir);
            Path destino = resolverDestino(avatarDir, nombreArchivo);
            Files.write(destino, jpegBytes);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar el archivo de avatar", e);
        }

        return "/api/avatares/" + nombreArchivo;
    }

    public Path cargar(String nombreArchivo) {
        if (nombreArchivo == null || !PATTERN_NOMBRE.matcher(nombreArchivo).matches()) {
            return null;
        }
        Path destino = resolverDestino(avatarDir, nombreArchivo);
        if (!Files.exists(destino) || !Files.isRegularFile(destino)) {
            return null;
        }
        return destino;
    }

    public void eliminar(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String nombreArchivo = url.substring(url.lastIndexOf('/') + 1);
        if (!PATTERN_NOMBRE.matcher(nombreArchivo).matches()) {
            return;
        }
        try {
            Path destino = resolverDestino(avatarDir, nombreArchivo);
            Files.deleteIfExists(destino);
        } catch (IOException e) {
            // Ignorar errores al eliminar
        }
    }

    private boolean esJpeg(byte[] bytes) {
        return bytes != null && bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private boolean esPng(byte[] bytes) {
        return bytes != null && bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && (bytes[1] & 0xFF) == 0x50
                && (bytes[2] & 0xFF) == 0x4E
                && (bytes[3] & 0xFF) == 0x47
                && (bytes[4] & 0xFF) == 0x0D
                && (bytes[5] & 0xFF) == 0x0A
                && (bytes[6] & 0xFF) == 0x1A
                && (bytes[7] & 0xFF) == 0x0A;
    }

    private BufferedImage procesarImagen(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int size = Math.min(w, h);
        int x = (w - size) / 2;
        int y = (h - size) / 2;

        BufferedImage square = img.getSubimage(x, y, size, size);
        int targetSize = Math.min(size, 512);

        BufferedImage rgb = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = rgb.createGraphics();
        try {
            g2d.setPaint(Color.WHITE);
            g2d.fillRect(0, 0, targetSize, targetSize);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(square, 0, 0, targetSize, targetSize, null);
        } finally {
            g2d.dispose();
        }
        return rgb;
    }

    private Path resolverDestino(Path directorio, String nombreArchivo) {
        Path base = directorio.toAbsolutePath().normalize();
        Path destino = base.resolve(nombreArchivo).normalize();
        if (!destino.startsWith(base)) {
            throw new ImagenInvalidaException("Ruta de archivo inválida");
        }
        return destino;
    }
}
