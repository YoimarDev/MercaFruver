package com.miempresa.fruver.infra.hardware.printer;

import com.miempresa.fruver.domain.exceptions.DataAccessException;
import com.miempresa.fruver.service.port.PrinterPort;

/** Implementación de PrinterPort delegando en PrinterService (infra). */
public class PrinterPortAdapter implements PrinterPort {

    private final PrinterService delegate;

    public PrinterPortAdapter() {
        this.delegate = new PrinterService();
    }

    @Override
    public void init(String printerId) throws DataAccessException {
        delegate.init(printerId);
    }

    @Override
    public void printReceipt(String content) throws DataAccessException {
        delegate.printReceipt(content);
    }

    @Override
    public void openCashDrawer() throws DataAccessException {
        delegate.openCashDrawer();
    }

    @Override
    public void close() {
        try { delegate.close(); } catch (Throwable ignored) {}
    }
}
