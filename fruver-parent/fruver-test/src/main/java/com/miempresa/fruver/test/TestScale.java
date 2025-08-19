package com.miempresa.fruver.test;

import com.miempresa.fruver.infra.hardware.scale.ScaleService;

/**
 * Main de prueba para la báscula (ScaleService).
 *
 * Ajusta el puerto y baudRate según tu configuración.
 */
public class TestScale {
    public static void main(String[] args) {
        ScaleService scale = new ScaleService();
        String port = "COM4";
        int baud = 9600;

        // Si se pasan args, usamos los primeros (puerto, baud)
        if (args.length >= 1 && args[0] != null && !args[0].isBlank()) port = args[0];
        if (args.length >= 2) {
            try { baud = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
        }

        try {
            scale.open(port, baud);

            // Leer en gramos (método típico: readWeightGrams())
            int gramos = scale.readWeightGrams();
            System.out.println("Peso leído: " + gramos + " g");

            // Si la clase ofrece método readWeight() con double en kg, podrías probarlo así:
            try {
                java.lang.reflect.Method m = scale.getClass().getMethod("readWeight");
                Object val = m.invoke(scale);
                System.out.println("Peso (readWeight): " + String.valueOf(val));
            } catch (NoSuchMethodException ignored) {
                // no existe readWeight(), está bien
            }

        } catch (Exception ex) {
            System.err.println("Error en prueba de báscula: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            try { scale.close(); } catch (Exception ignored) {}
            System.out.println("TestScale finalizado.");
        }
    }
}
