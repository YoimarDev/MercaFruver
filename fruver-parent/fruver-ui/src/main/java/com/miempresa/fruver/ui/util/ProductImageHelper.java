package com.miempresa.fruver.ui.util;

import javafx.scene.image.Image;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/**
 * Helper utilitario para almacenar y cargar imágenes de producto.
 *
 * - Guarda en ./data/images/{uuid}.{ext} y devuelve la ruta relativa "data/images/..."
 * - loadImage acepta ruta relativa/absoluta; si no existe, devuelve placeholder embebido en recursos.
 */
public final class ProductImageHelper {

    private static final String IMAGES_DIR = "data/images";
    // Nota: keep placeholder consistent with resources (user-placeholder.png incluido en resources/images/)
    private static final String PLACEHOLDER_RESOURCE = "/images/user-placeholder.png";

    private ProductImageHelper() {}

    /**
     * Guarda la imagen proporcionada en la carpeta ./data/images/ con un nombre UUID.
     * Devuelve la ruta relativa (ej: "data/images/uuid.png").
     *
     * @param sourceFile archivo origen (seleccionado por FileChooser)
     * @return ruta relativa donde se guardó la imagen (para persistir en DB)
     * @throws Exception si ocurre problema al copiar
     */
    public static String saveImage(File sourceFile) throws Exception {
        if (sourceFile == null) throw new IllegalArgumentException("sourceFile es null");
        if (!sourceFile.exists()) throw new Exception("Archivo no encontrado: " + sourceFile.getAbsolutePath());

        String ext = extractExtension(sourceFile.getName());
        if (ext == null || ext.isBlank()) ext = "png";
        String uuid = UUID.randomUUID().toString();
        String targetFileName = uuid + "." + ext.toLowerCase(Locale.ROOT);

        File dir = new File(IMAGES_DIR);
        if (!dir.exists()) {
            boolean ok = dir.mkdirs();
            if (!ok && !dir.exists()) {
                throw new Exception("No se pudo crear el directorio de imágenes: " + dir.getAbsolutePath());
            }
        }

        File target = new File(dir, targetFileName);

        // Copiar con Files (más robusto que streams en muchos casos)
        try {
            Files.copy(sourceFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable ex) {
            // Fallback a streams
            try (InputStream in = new FileInputStream(sourceFile); FileOutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            } catch (Throwable inner) {
                // intentar limpiar si quedó archivo parcial
                try { Files.deleteIfExists(target.toPath()); } catch (Throwable ignored) {}
                throw new Exception("Error copiando imagen: " + inner.getMessage(), inner);
            }
        }

        // Retornar ruta relativa normalizada para DB
        String relative = IMAGES_DIR + File.separator + targetFileName;
        return relative.replace('\\', '/');
    }

    /**
     * Carga una Image de JavaFX desde la ruta dada.
     * - Si la ruta no existe, devuelve el placeholder embebido en recursos.
     *
     * @param path ruta relativa (ej "data/images/uuid.png") o absoluta.
     * @return javafx.scene.image.Image (no nulo)
     */
    public static Image loadImage(String path) {
        if (path != null && !path.isBlank()) {
            try {
                File f = new File(path);
                if (!f.exists()) {
                    f = new File(System.getProperty("user.dir"), path);
                }
                if (f.exists()) {
                    return new Image(f.toURI().toString(), true);
                }
            } catch (Throwable ignored) {
                // caerá al placeholder
            }
        }

        // Cargar placeholder desde recursos del JAR
        try (InputStream is = ProductImageHelper.class.getResourceAsStream(PLACEHOLDER_RESOURCE)) {
            if (is != null) {
                return new Image(is);
            }
        } catch (Throwable ignored) {}

        // Último recurso: tiny empty image (1x1 transparent)
        return new Image("data:,");
    }

    /**
     * Elimina el archivo de imagen dado (ruta relativa o absoluta). No lanza excepción si no existe.
     *
     * @param path ruta relativa/absoluta
     * @return true si fue eliminado, false si no existía o falló.
     */
    public static boolean deleteImage(String path) {
        if (path == null || path.isBlank()) return false;
        try {
            File f = new File(path);
            if (!f.exists()) f = new File(System.getProperty("user.dir"), path);
            if (f.exists()) return f.delete();
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String extractExtension(String name) {
        if (name == null) return null;
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) return null;
        return name.substring(idx + 1);
    }
}
