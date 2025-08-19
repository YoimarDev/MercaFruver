package com.miempresa.fruver.service.port;

import com.miempresa.fruver.domain.exceptions.DataAccessException;

/** Abstracción de impresora térmica para usar desde casos de uso. */
public interface PrinterPort {
    /** Inicializa la impresora por su identificador (nombre de cola o COM). */
    void init(String printerId) throws DataAccessException;

    /** Imprime el texto del recibo (con saltos de línea). */
    void printReceipt(String content) throws DataAccessException;

    /** Abre el cajón (si aplica). */
    void openCashDrawer() throws DataAccessException;

    /** Libera recursos. */
    void close();
}
