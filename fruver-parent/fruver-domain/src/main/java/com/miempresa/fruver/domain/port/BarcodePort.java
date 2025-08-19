package com.miempresa.fruver.domain.port;

import java.util.function.Consumer;

/**
 * Abstracción del lector de códigos. 
 * - init(): inicializa (puede leer DB/config y decidir modo).
 * - setOnCodeScanned: instala listener que recibe códigos (UI o servicio lo utilizarán).
 * - handleInput: método auxiliar para pruebas/simulación (keyboard-mode).
 */
public interface BarcodePort {
    void init();
    void setOnCodeScanned(Consumer<String> listener);
    void handleInput(String input);
    void close();
}
