package com.miempresa.fruver.infra.hardware.printer;

import com.miempresa.fruver.domain.port.PrinterPort;

public class PrinterPortAdapter implements PrinterPort {

    private final PrinterService printerService;

    public PrinterPortAdapter() {
        this.printerService = new PrinterService();
    }

    @Override
    public void init(String printerId) {
        printerService.init(printerId);
    }

    @Override
    public void printReceipt(String payload) {
        printerService.printReceipt(payload);
    }

    @Override
    public void openCashDrawer() {
        printerService.openCashDrawer();
    }

    @Override
    public void close() {
        printerService.close();
    }
}
