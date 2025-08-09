package com.miempresa.fruver.domain.exceptions;

/**
 * Excepción para errores de acceso a datos (persistencia).
 */
public class DataAccessException extends DomainException {
    public DataAccessException(String message) {
        super(message);
    }
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}