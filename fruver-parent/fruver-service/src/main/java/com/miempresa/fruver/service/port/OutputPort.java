package com.miempresa.fruver.service.port;

/**
 * Interfaz para presentar resultados.
 */
public interface OutputPort<T> {
    void present(T output);
}