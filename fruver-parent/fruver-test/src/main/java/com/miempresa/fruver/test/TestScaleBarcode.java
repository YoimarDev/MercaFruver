package com.miempresa.fruver.test;

import com.miempresa.fruver.infra.hardware.scale.ScaleService;
import com.miempresa.fruver.infra.hardware.barcode.BarcodeService;
import com.miempresa.fruver.infra.db.DeviceConfigRepositoryJdbc;
import com.miempresa.fruver.infra.config.DataSourceFactory;
import com.miempresa.fruver.domain.model.DeviceConfig.DeviceType;
import com.miempresa.fruver.domain.exceptions.DataAccessException;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.Scanner;

public class TestScaleBarcode {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("=== Test combinado báscula + lector ===");
        System.out.println("ENTER = Leer peso  |  texto+ENTER = Simular código  |  exit+ENTER = Salir");

        // 1) Leer configuración de puertos desde la BD
        DataSource ds = DataSourceFactory.getDataSource();
        DeviceConfigRepositoryJdbc cfgRepo = new DeviceConfigRepositoryJdbc(ds);

        // BASCULA
        String scalePort = cfgRepo.findByType(DeviceType.BASCULA)
                .map(cfg -> cfg.getPuerto())
                .orElseThrow(() -> new RuntimeException("Config de báscula no encontrada"));
        int baud = cfgRepo.findByType(DeviceType.BASCULA)
                .map(cfg -> cfg.getParametrosJson())
                .map(json -> {
                    // esperamos {"baudRate":9600,...}
                    String s = json.replaceAll(".*\"baudRate\"\\s*:\\s*(\\d+).*", "$1");
                    return Integer.parseInt(s);
                })
                .orElse(9600);

        // LECTOR
        Optional<String> optReaderPort = cfgRepo.findByType(DeviceType.LECTOR)
                .map(cfg -> cfg.getPuerto());
        boolean readerKeyboard = optReaderPort.isEmpty() || optReaderPort.get().isBlank();

        // 2) Inicializar servicios
        ScaleService scale = new ScaleService();
        BarcodeService barcode = new BarcodeService();

        try {
            // abre báscula
            scale.open(scalePort, baud);
            // inicializa lector en teclado o serial
            if (!readerKeyboard) {
                barcode.init();
            } else {
                // forzar keyboard-mode sin COM
                System.out.println("-> Lector en modo teclado (keyboard), sin COM");
            }

            // 3) callback del lector
            barcode.setOnCodeScanned(code ->
                    System.out.println("[LECTOR] Código escaneado: " + code)
            );

            // 4) bucle
            while (true) {
                String line = sc.nextLine().trim();
                if ("exit".equalsIgnoreCase(line)) break;

                if (line.isEmpty()) {
                    // leer peso en gramos
                    try {
                        int g = scale.readWeightGrams();
                        System.out.printf("[BÁSCULA] Peso: %d g%n", g);
                    } catch (DataAccessException ex) {
                        System.err.println("[BÁSCULA] ERROR: " + ex.getMessage());
                    }
                } else {
                    // simular lector
                    barcode.handleInput(line);
                }
            }
        } catch (Exception ex) {
            System.err.println("⛔ Error en prueba: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            // cerrar
            try { scale.close(); } catch (Exception ignored) {}
            try { barcode.close(); } catch (Exception ignored) {}
            sc.close();
            System.out.println("=== Fin Test ===");
        }
    }
}
