
package com.miempresa.fruver.service.usecase;

import java.io.*;
import java.util.function.Consumer;

/**
 * Realiza backup usando mysqldump. Debe ser ejecutado por ADMIN; requiere
 * que mysqldump esté en PATH o se provea ruta absoluta.
 *
 * NOTA DE SEGURIDAD: Las credenciales no deben ir en código. Aquí se asume
 * que ServiceLocator proveerá usuario/host/port/db/pass de forma segura.
 */
public class PerformBackupUseCase {

    private final String mysqldumpCmd; // "mysqldump" o ruta absoluta
    private final String dbUser;
    private final String dbPassword;
    private final String dbName;
    private final String dbHost;
    private final int dbPort;

    public PerformBackupUseCase(String mysqldumpCmd,
                                String dbUser, String dbPassword,
                                String dbName, String dbHost, int dbPort) {
        this.mysqldumpCmd = mysqldumpCmd;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.dbName = dbName;
        this.dbHost = dbHost;
        this.dbPort = dbPort;
    }

    /**
     * Ejecuta mysqldump y escribe archivo en targetPath (ej: /backups/fruver-2025-08-...).
     * Usa progressMsg para enviar mensajes intermedios.
     */
    public void execute(String targetPath, Consumer<String> progressMsg) throws Exception {
        progressMsg.accept("Iniciando mysqldump...");
        // Evitar pasar la password en args visible: usar archivo option --defaults-extra-file is safer.
        // Para simplicidad aquí usaremos args directos, pero en producción prefiere archivo de config.
        ProcessBuilder pb = new ProcessBuilder(
                mysqldumpCmd,
                "-h", dbHost,
                "-P", String.valueOf(dbPort),
                "-u", dbUser,
                "-p" + dbPassword,
                "--single-transaction",
                "--quick",
                "--routines",
                "--events",
                dbName
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();

        // leer salida y escribir en el archivo comprimido (por simplicidad lo guardamos .sql)
        try (InputStream in = p.getInputStream();
             FileOutputStream fos = new FileOutputStream(targetPath)) {
            byte[] buf = new byte[8192];
            int read;
            long total = 0;
            while ((read = in.read(buf)) != -1) {
                fos.write(buf, 0, read);
                total += read;
                // no hay forma fácil de saber % exacto; reportar bytes (opcional)
                if (progressMsg != null) progressMsg.accept("Volcando... bytes=" + total);
            }
        }

        int rc = p.waitFor();
        if (rc != 0) {
            throw new RuntimeException("mysqldump falló con código: " + rc);
        }
        progressMsg.accept("Backup completado: " + targetPath);
    }
}
