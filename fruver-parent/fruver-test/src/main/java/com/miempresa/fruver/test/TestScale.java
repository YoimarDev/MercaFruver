package com.miempresa.fruver.test;

import com.miempresa.fruver.infra.hardware.scale.ScaleService;

public class TestScale {
    public static void main(String[] args) {
        ScaleService scale = new ScaleService();
        try {
            scale.open("COM4", 9600);

            // Leer en gramos:
            int gramos = scale.readWeightGrams();
            System.out.println("Peso leído: " + gramos + " g");

        } catch (Exception ex) {
            System.err.println("Error en prueba: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            scale.close();
        }
    }
}
