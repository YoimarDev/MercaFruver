package com.miempresa.fruver.infra.hardware.scale;

import com.miempresa.fruver.domain.port.ScalePort;
import com.miempresa.fruver.domain.exceptions.DataAccessException;

/**
 * Adapter que implementa ScalePort delegando en ScaleService.
 * Importante: usa el port desde fruver-domain (no desde fruver-service).
 */
public class ScalePortAdapter implements ScalePort {

    private final ScaleService delegate;

    public ScalePortAdapter() {
        this.delegate = new ScaleService();
    }

    @Override
    public void open(String portName, int baudRate) {
        // ScaleService lanza DataAccessException (runtime) en fallos
        delegate.open(portName, baudRate);
    }

    @Override
    public double readWeightKg() {
        return delegate.readWeightKg();
    }

    @Override
    public int readWeightGrams() {
        return delegate.readWeightGrams();
    }

    @Override
    public void close() {
        try { delegate.close(); } catch (Throwable ignored) { }
    }
}
