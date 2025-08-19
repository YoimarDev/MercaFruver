package com.miempresa.fruver.infra.hardware.scale;

import com.miempresa.fruver.domain.exceptions.DataAccessException;
import com.miempresa.fruver.service.port.ScalePort;

/**
 * Adapter que implementa ScalePort delegando en ScaleService (clase concreta en infra).
 * Mantén este código en fruver-infra (no en fruver-service).
 */
public class ScalePortAdapter implements ScalePort {

    private final ScaleService delegate;

    public ScalePortAdapter() {
        this.delegate = new ScaleService();
    }

    @Override
    public void open(String portName, int baudRate) throws DataAccessException {
        delegate.open(portName, baudRate);
    }

    @Override
    public double readWeightKg() throws DataAccessException {
        return delegate.readWeightKg();
    }

    @Override
    public int readWeightGrams() throws DataAccessException {
        return delegate.readWeightGrams();
    }

    @Override
    public void close() {
        try { delegate.close(); } catch (Throwable ignored) {}
    }
}
