package com.miempresa.fruver.service.port;

import com.miempresa.fruver.domain.exceptions.DataAccessException;

/**
 * Port (interface) para abstracción de la báscula.
 * El service usa esta interfaz; la implementación concreta vive en fruver-infra.
 */
public interface ScalePort {
    /**
     * Abrir puerto con baudRate (opcional para implementaciones que no lo requieran).
     */
    void open(String portName, int baudRate) throws DataAccessException;

    /**
     * Lee el peso en kilogramos.
     * Lanza DataAccessException si hay problemas de I/O o timeout.
     */
    double readWeightKg() throws DataAccessException;

    /**
     * Lee el peso en gramos (utilidad).
     */
    int readWeightGrams() throws DataAccessException;

    /**
     * Cierra la conexión / puerto.
     */
    void close();
}
