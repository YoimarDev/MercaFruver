package com.miempresa.fruver.domain.port;

/**
 * Puerto (port) para la báscula: definición de la abstracción usada por service.
 * Mantener métodos simples y sin declarar excepciones checked.
 */
public interface ScalePort {
    void open(String portName, int baudRate);
    double readWeightKg();
    int readWeightGrams();
    void close();
}
