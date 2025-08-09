package com.miempresa.fruver.service.port;

/**
 * Interfaz genérica para casos de uso.
 */
public interface InputPort<I, O> {
    O execute(I input);
}