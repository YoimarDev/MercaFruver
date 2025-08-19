package com.miempresa.fruver.test;

import com.miempresa.fruver.infra.hardware.barcode.BarcodeService;
import com.miempresa.fruver.domain.exceptions.DataAccessException;

import java.lang.reflect.Method;
import java.util.Scanner;

/**
 * Main de prueba para el lector de código de barras.
 * Versión compatible con compiladores antiguos (sin diamante, sin @Override en anónimos).
 */
public class TestBarcode {
    public static void main(String[] args) {
        BarcodeService svc = new BarcodeService();
        Scanner sc = new Scanner(System.in);
        try {
            svc.init();

            boolean listenerInstalled = false;
            try {
                Method m = svc.getClass().getMethod("setOnCodeScanned", java.util.function.Consumer.class);
                java.util.function.Consumer<String> consumer = new java.util.function.Consumer<String>() {
                    public void accept(String code) {
                        System.out.println("Código escaneado: " + code);
                    }
                };
                m.invoke(svc, consumer);
                listenerInstalled = true;
            } catch (NoSuchMethodException ns) {
                // intentar alternativa
                try {
                    Method alt = svc.getClass().getMethod("setOnScanned", java.util.function.Consumer.class);
                    java.util.function.Consumer<String> consumer = new java.util.function.Consumer<String>() {
                        public void accept(String code) {
                            System.out.println("Código escaneado: " + code);
                        }
                    };
                    alt.invoke(svc, consumer);
                    listenerInstalled = true;
                } catch (NoSuchMethodException nm2) {
                    // no hay método listener accesible; seguiremos con simulación por consola
                } catch (ReflectiveOperationException roe2) {
                    System.err.println("Error instalando listener alternativo: " + roe2.getMessage());
                }
            } catch (ReflectiveOperationException roe) {
                System.err.println("No se pudo instalar listener reflectivamente: " + roe.getMessage());
            }

            System.out.println("Esperando código (enter para enviar). Escribe 'exit' para terminar.");
            while (true) {
                String line = sc.nextLine();
                if (line == null) break;
                line = line.trim();
                if ("exit".equalsIgnoreCase(line)) break;

                // Intentamos invocar handleInput si existe (simulación)
                try {
                    Method handle = svc.getClass().getMethod("handleInput", String.class);
                    handle.invoke(svc, line);
                } catch (NoSuchMethodException ns) {
                    // no hay handleInput: mostramos por consola
                    System.out.println("[SIMULADO] Código: " + line);
                } catch (ReflectiveOperationException roe) {
                    System.err.println("Error invocando handleInput: " + roe.getMessage());
                }
            }

        } catch (DataAccessException ex) {
            System.err.println("Error inicializando lector: " + ex.getMessage());
            ex.printStackTrace();
        } catch (Throwable t) {
            System.err.println("Error inesperado: " + t.getMessage());
            t.printStackTrace();
        } finally {
            try { svc.close(); } catch (Exception ignored) {}
            sc.close();
            System.out.println("TestBarcode finalizado.");
        }
    }
}
