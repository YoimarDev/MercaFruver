package com.miempresa.fruver.domain.exceptions;
import com.miempresa.fruver.domain.exceptions.DomainException;

public class EntityNotFoundException extends DomainException {
    public EntityNotFoundException(String message) { super(message); }
}
