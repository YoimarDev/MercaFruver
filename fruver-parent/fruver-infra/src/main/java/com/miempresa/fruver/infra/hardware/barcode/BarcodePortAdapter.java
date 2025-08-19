package com.miempresa.fruver.infra.hardware.barcode;

import com.miempresa.fruver.domain.port.BarcodePort;

import java.util.function.Consumer;

/**
 * Adapter que implementa BarcodePort delegando en BarcodeService.
 */
public class BarcodePortAdapter implements BarcodePort {

    private final BarcodeService delegate;

    public BarcodePortAdapter() {
        this.delegate = new BarcodeService();
    }

    @Override
    public void init() {
        delegate.init();
    }

    @Override
    public void setOnCodeScanned(Consumer<String> listener) {
        delegate.setOnCodeScanned(listener);
    }

    @Override
    public void handleInput(String input) {
        // útil para pruebas en modo keyboard
        try { delegate.handleInput(input); } catch (Exception ignored) {}
    }

    @Override
    public void close() {
        try { delegate.close(); } catch (Exception ignored) {}
    }
}
