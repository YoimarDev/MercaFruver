// fruver-test/src/main/java/com/miempresa/fruver/test/TestBarcode.java
package com.miempresa.fruver.test;

import com.miempresa.fruver.infra.hardware.barcode.BarcodeService;
import com.miempresa.fruver.domain.exceptions.DataAccessException;

import java.util.Scanner;

/**
 * Main de prueba para el lector de código de barras.
 *
 * Pasos:
 *  - Levantar MySQL y tener CONFIG_DISP con modo "keyboard"
 *    o puerto COM si es serial.
 *  - Ejecutar mvn clean install en el padre.
 *  - Ejecutar este Main y escanear (o teclear) códigos.
 */
public class TestBarcode {
    public static void main(String[] args) {
        BarcodeService svc = new BarcodeService();
        try {
            // 1) Inicializar según DB
            svc.init();

            // 2) Definir callback
            svc.setOnCodeScanned(code ->
                    System.out.println("Código escaneado: " + code)
            );

            // 3) Esperar entradas manuales para simular
            System.out.println("Esperando código (enter para enviar). Escribe 'exit' para terminar.");
            Scanner sc = new Scanner(System.in);
            while (true) {
                String line = sc.nextLine().trim();
                if ("exit".equalsIgnoreCase(line)) break;
                svc.handleInput(line);
            }

            sc.close();
        } catch (DataAccessException ex) {
            System.err.println("Error inicializando lector: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            svc.close();
        }
    }
}
