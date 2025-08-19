package com.miempresa.fruver.ui.util;

import javafx.scene.image.Image;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
    private static final String PLACEHOLDER_RESOURCE = "/images/user-placeholder.png"; // ya existe en recursos según spec

    private ProductImageHelper() {}

    /**
     * Guarda la imagen proporcionada en la carpeta ./data/images/ con un nombre UUID.
     * Devuelve la ruta relativa (ej: "data/images/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx.png").
     *
     * @param sourceFile archivo origen (seleccionado por FileChooser)
     * @return ruta relativa donde se guardó la imagen (para persistir en DB)
     * @throws IOException si ocurre problema al copiar
     */
    public static String saveImage(File sourceFile) throws IOException {
        if (sourceFile == null) throw new IllegalArgumentException("sourceFile es null");
        if (!sourceFile.exists()) throw new IOException("Archivo no encontrado: " + sourceFile.getAbsolutePath());

        String ext = extractExtension(sourceFile.getName());
        if (ext == null || ext.isBlank()) ext = "png";
        String uuid = UUID.randomUUID().toString();
        String targetFileName = uuid + "." + ext.toLowerCase(Locale.ROOT);

        File dir = new File(IMAGES_DIR);
        if (!dir.exists()) {
            boolean ok = dir.mkdirs();
            if (!ok && !dir.exists()) {
                throw new IOException("No se pudo crear el directorio de imágenes: " + dir.getAbsolutePath());
            }
        }

        File target = new File(dir, targetFileName);

        // Copiar archivo (con fallback a streams por seguridad)
        try (InputStream in = new FileInputStream(sourceFile);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        } catch (IOException ex) {
            // Intentar limpieza si algo quedó a medias
            try { Files.deleteIfExists(target.toPath()); } catch (Throwable ignored) {}
            throw ex;
        }

        // Retornar ruta relativa para almacenar en BD
        String relative = IMAGES_DIR + File.separator + targetFileName;
        return relative.replace('\\', '/'); // normalizar slash para la DB y multiplataforma
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
                    // intentar con path relativo a working dir
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

        // Último recurso: image vacío (1x1 transparent)
        return new Image("data:,"); // tiny empty image URI
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
