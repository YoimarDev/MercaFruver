package com.miempresa.fruver.domain.port;

/**
 * Puerto (port) para impresora térmica.
 */
public interface PrinterPort {
    void init(String printerId);
    void printReceipt(String payload);
    void openCashDrawer();
    void close();
}
