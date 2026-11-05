package com.repuestos.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class ImagenStorageService {

    static final long MAX_BYTES = 5L * 1024 * 1024;

    private final Path uploadDir;

    public ImagenStorageService(@Value("${app.upload-dir:./uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public String guardar(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ImagenInvalidaException("El archivo está vacío");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ImagenInvalidaException("La imagen supera el máximo de 5 MB");
        }

        byte[] contenido = file.getBytes();
        String extension = detectarExtension(contenido);
        if (extension == null) {
            throw new ImagenInvalidaException("Formato no permitido. Usá JPG, PNG o WebP");
        }

        String nombreArchivo = UUID.randomUUID() + extension;
        Files.createDirectories(uploadDir);
        Path destino = resolverDestino(uploadDir, nombreArchivo);
        Files.write(destino, contenido);

        return "/api/uploads/" + nombreArchivo;
    }

    static String detectarExtension(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && (bytes[1] & 0xFF) == 0x50
                && (bytes[2] & 0xFF) == 0x4E
                && (bytes[3] & 0xFF) == 0x47
                && (bytes[4] & 0xFF) == 0x0D
                && (bytes[5] & 0xFF) == 0x0A
                && (bytes[6] & 0xFF) == 0x1A
                && (bytes[7] & 0xFF) == 0x0A) {
            return ".png";
        }
        if (bytes.length >= 12
                && (bytes[0] & 0xFF) == 'R'
                && (bytes[1] & 0xFF) == 'I'
                && (bytes[2] & 0xFF) == 'F'
                && (bytes[3] & 0xFF) == 'F'
                && (bytes[8] & 0xFF) == 'W'
                && (bytes[9] & 0xFF) == 'E'
                && (bytes[10] & 0xFF) == 'B'
                && (bytes[11] & 0xFF) == 'P') {
            return ".webp";
        }
        return null;
    }

    static Path resolverDestino(Path directorio, String nombreArchivo) {
        Path base = directorio.toAbsolutePath().normalize();
        Path destino = base.resolve(nombreArchivo).normalize();
        if (!destino.startsWith(base)) {
            throw new ImagenInvalidaException("Ruta de archivo inválida");
        }
        return destino;
    }
}
