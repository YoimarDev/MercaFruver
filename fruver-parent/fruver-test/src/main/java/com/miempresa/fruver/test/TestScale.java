package com.miempresa.fruver.test;

import com.miempresa.fruver.infra.hardware.scale.ScaleService;
import com.miempresa.fruver.infra.db.DeviceConfigRepositoryJdbc;
import com.miempresa.fruver.infra.config.DataSourceFactory;
import com.miempresa.fruver.domain.model.DeviceConfig;
import com.miempresa.fruver.domain.model.DeviceConfig.DeviceType;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Scanner;

/**
 * TestScale definitivo:
 *  - NO realiza ninguna lectura al iniciar.
 *  - Al primer ENTER intenta abrir la báscula y leer.
 *  - Cada ENTER siguiente hace otra lectura (reintenta abrir si es necesario).
 *  - Escribe 'q' o 'exit' + ENTER para salir.
 */
public class TestScale {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("=== Test de báscula (no lee hasta que pulses ENTER) ===");
        System.out.println("ENTER = Leer peso  |  q / exit + ENTER = Salir");

        // Intento de leer configuración desde BD (opcional)
        DataSource ds = null;
        DeviceConfigRepositoryJdbc cfgRepo = null;
        try {
            ds = DataSourceFactory.getDataSource();
            cfgRepo = new DeviceConfigRepositoryJdbc(ds);
        } catch (Throwable t) {
            // ignoramos: usaremos valores por defecto
        }

        String scalePort = null;
        int baud = 9600;

        if (cfgRepo != null) {
            try {
                Object res = cfgRepo.getClass().getMethod("findByType", DeviceType.class).invoke(cfgRepo, DeviceType.BASCULA);

                if (res instanceof Optional) {
                    Optional<?> opt = (Optional<?>) res;
                    if (opt.isPresent()) {
                        Object cfg = opt.get();
                        if (cfg instanceof DeviceConfig) {
                            scalePort = ((DeviceConfig) cfg).getPuerto();
                            String paramsJson = ((DeviceConfig) cfg).getParametrosJson();
                            try {
                                String s = paramsJson.replaceAll("(?s).*\"baudRate\"\\s*:\\s*(\\d+).*", "$1");
                                if (s != null && s.matches("\\d+")) baud = Integer.parseInt(s);
                            } catch (Exception ignored) {}
                        } else {
                            try {
                                Method g = cfg.getClass().getMethod("getPuerto");
                                Object p = g.invoke(cfg);
                                if (p != null) scalePort = p.toString();
                                Method pj = cfg.getClass().getMethod("getParametrosJson");
                                Object paramsObj = pj.invoke(cfg);
                                if (paramsObj != null) {
                                    String paramsJson = paramsObj.toString();
                                    String s = paramsJson.replaceAll("(?s).*\"baudRate\"\\s*:\\s*(\\d+).*", "$1");
                                    if (s != null && s.matches("\\d+")) baud = Integer.parseInt(s);
                                }
                            } catch (NoSuchMethodException ignored) {}
                        }
                    }
                } else if (res instanceof DeviceConfig) {
                    DeviceConfig cfg = (DeviceConfig) res;
                    scalePort = cfg.getPuerto();
                    String paramsJson = cfg.getParametrosJson();
                    try {
                        String s = paramsJson.replaceAll("(?s).*\"baudRate\"\\s*:\\s*(\\d+).*", "$1");
                        if (s != null && s.matches("\\d+")) baud = Integer.parseInt(s);
                    } catch (Exception ignored) {}
                } else {
                    try {
                        Method getPuerto = res.getClass().getMethod("getPuerto");
                        Object p = getPuerto.invoke(res);
                        if (p != null) scalePort = p.toString();
                        Method getParams = res.getClass().getMethod("getParametrosJson");
                        Object paramsObj = getParams.invoke(res);
                        if (paramsObj != null) {
                            String paramsJson = paramsObj.toString();
                            String s = paramsJson.replaceAll("(?s).*\"baudRate\"\\s*:\\s*(\\d+).*", "$1");
                            if (s != null && s.matches("\\d+")) baud = Integer.parseInt(s);
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                // ignore and continue with defaults
            }
        }

        if (scalePort == null || scalePort.isBlank()) {
            scalePort = "COM4";
            System.out.println("Usando puerto por defecto: " + scalePort + "@" + baud);
        } else {
            System.out.println("Usando configuración BD: " + scalePort + "@" + baud);
        }

        final ScaleService scale = new ScaleService();
        // IMPORTANTE: debug DESACTIVADO para evitar prints automáticos al abrir.
        scale.setDebug(false);

        // Shutdown hook seguro (compatible con Java 7+)
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                try { scale.close(); } catch (Throwable ignored) {}
            }
        }));

        boolean opened = false;

        try {
            // NO abrimos aquí: la apertura se intenta únicamente en el primer ENTER.
            while (true) {
                String line;
                try {
                    line = sc.nextLine();
                } catch (Throwable t) {
                    System.err.println("Error leyendo entrada: " + t.getMessage());
                    break;
                }
                if (line == null) {
                    System.out.println("Entrada EOF detectada, saliendo.");
                    break;
                }
                line = line.trim();
                if ("q".equalsIgnoreCase(line) || "exit".equalsIgnoreCase(line)) {
                    System.out.println("Saliendo por petición del usuario.");
                    break;
                }

                // Al presionar ENTER (line vacío) o cualquier otra entrada, intentamos leer:
                if (!opened) {
                    try {
                        System.out.println("Intentando abrir puerto " + scalePort + "@" + baud + " ...");
                        scale.open(scalePort, baud);
                        opened = true;
                        System.out.println("Puerto abierto. Ahora se leerá al presionar ENTER.");
                    } catch (Throwable t) {
                        System.err.println("No se pudo abrir el puerto: " + t.getMessage());
                        System.out.println("Pulsa ENTER para reintentar o escribe 'q'/'exit' para salir.");
                        // no hacemos lectura si no fue posible abrir
                        continue;
                    }
                }

                // Realizar la lectura solamente cuando el usuario pidió (ENTER)
                try {
                    int gramos = scale.readWeightGrams();
                    System.out.printf("[BÁSCULA] Peso: %d g%n", gramos);
                } catch (Throwable ex) {
                    System.err.println("[BÁSCULA] ERROR leyendo peso: " + ex.getMessage());
                    // Intento de recuperación: cerrar y marcar como no abierto para reintento en siguiente ENTER
                    try { scale.close(); } catch (Throwable ignored) {}
                    opened = false;
                    System.out.println("Se cerró la conexión a la báscula. Pulsa ENTER para reintentar abrir y leer.");
                }
            }
        } finally {
            try { scale.close(); } catch (Throwable ignored) {}
            try { sc.close(); } catch (Throwable ignored) {}
            System.out.println("=== Fin TestScale ===");
        }
    }
}
