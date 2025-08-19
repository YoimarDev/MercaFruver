package com.miempresa.fruver.test;

import com.miempresa.fruver.infra.hardware.scale.ScaleService;
import com.miempresa.fruver.infra.hardware.barcode.BarcodeService;
import com.miempresa.fruver.infra.db.DeviceConfigRepositoryJdbc;
import com.miempresa.fruver.infra.config.DataSourceFactory;
import com.miempresa.fruver.domain.model.DeviceConfig;
import com.miempresa.fruver.domain.model.DeviceConfig.DeviceType;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Scanner;

/**
 * Test combinado para báscula + lector. Versión defensiva y compatible.
 */
public class TestScaleBarcode {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("=== Test combinado báscula + lector ===");
        System.out.println("ENTER = Leer peso  |  texto+ENTER = Simular código  |  exit+ENTER = Salir");

        DataSource ds = DataSourceFactory.getDataSource();
        DeviceConfigRepositoryJdbc cfgRepo = new DeviceConfigRepositoryJdbc(ds);

        String scalePort = null;
        int baud = 9600;

        try {
            Object res = cfgRepo.getClass().getMethod("findByType", DeviceType.class).invoke(cfgRepo, DeviceType.BASCULA);

            if (res instanceof Optional) {
                Optional<?> opt = (Optional<?>) res;
                if (opt.isEmpty()) throw new RuntimeException("Config de báscula no encontrada");
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
            } else if (res instanceof DeviceConfig) {
                DeviceConfig cfg = (DeviceConfig) res;
                scalePort = cfg.getPuerto();
                String paramsJson = cfg.getParametrosJson();
                try {
                    String s = paramsJson.replaceAll("(?s).*\"baudRate\"\\s*:\\s*(\\d+).*", "$1");
                    if (s != null && s.matches("\\d+")) baud = Integer.parseInt(s);
                } catch (Exception ignored) {}
            } else {
                throw new RuntimeException("Respuesta inesperada de findByType");
            }
        } catch (ReflectiveOperationException roe) {
            System.err.println("Error leyendo configuración de báscula: " + roe.getMessage());
            roe.printStackTrace();
            sc.close();
            return;
        } catch (Throwable ex) {
            System.err.println("No se pudo leer configuración de báscula: " + ex.getMessage());
            ex.printStackTrace();
            sc.close();
            return;
        }

        String readerPort = null;
        try {
            Object res2 = cfgRepo.getClass().getMethod("findByType", DeviceType.class).invoke(cfgRepo, DeviceType.LECTOR);
            if (res2 instanceof Optional) {
                Optional<?> opt = (Optional<?>) res2;
                if (opt.isPresent()) {
                    Object cfg = opt.get();
                    try {
                        Method g = cfg.getClass().getMethod("getPuerto");
                        Object p = g.invoke(cfg);
                        if (p != null) readerPort = p.toString();
                    } catch (NoSuchMethodException ignored) {}
                }
            } else if (res2 instanceof DeviceConfig) {
                readerPort = ((DeviceConfig) res2).getPuerto();
            }
        } catch (Throwable ignored) {}

        boolean readerKeyboard = (readerPort == null || readerPort.isBlank());

        ScaleService scale = new ScaleService();
        BarcodeService barcode = new BarcodeService();

        try {
            scale.open(scalePort, baud);
            System.out.println("Báscula abierta en " + scalePort + "@" + baud);

            if (!readerKeyboard) {
                try { barcode.init(); System.out.println("Lector inicializado (serial)."); }
                catch (Throwable t) { System.err.println("No se pudo inicializar lector serial: " + t.getMessage()); }
            } else {
                System.out.println("Lector en modo teclado (keyboard).");
            }

            try {
                Method m = barcode.getClass().getMethod("setOnCodeScanned", java.util.function.Consumer.class);
                java.util.function.Consumer<String> consumer = new java.util.function.Consumer<String>() {
                    public void accept(String code) {
                        System.out.println("[LECTOR] Código escaneado: " + code);
                    }
                };
                m.invoke(barcode, consumer);
            } catch (NoSuchMethodException ns) {
                // ok si no existe
            } catch (ReflectiveOperationException roe) {
                System.err.println("No se pudo instalar listener reflectivo: " + roe.getMessage());
            }

            while (true) {
                String line = sc.nextLine();
                if (line == null) break;
                line = line.trim();
                if ("exit".equalsIgnoreCase(line)) break;

                if (line.isEmpty()) {
                    try {
                        int g = scale.readWeightGrams();
                        System.out.printf("[BÁSCULA] Peso: %d g%n", g);
                    } catch (Throwable ex) {
                        System.err.println("[BÁSCULA] ERROR: " + ex.getMessage());
                    }
                } else {
                    try {
                        Method h = barcode.getClass().getMethod("handleInput", String.class);
                        h.invoke(barcode, line);
                    } catch (NoSuchMethodException ns) {
                        System.out.println("[SIMULADO LECTOR] " + line);
                    } catch (ReflectiveOperationException roe) {
                        System.err.println("Error invocando handleInput: " + roe.getMessage());
                    }
                }
            }

        } catch (Throwable ex) {
            System.err.println("⛔ Error en prueba: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            try { scale.close(); } catch (Exception ignored) {}
            try { barcode.close(); } catch (Exception ignored) {}
            sc.close();
            System.out.println("=== Fin Test ===");
        }
    }
}
