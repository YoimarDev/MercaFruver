package com.miempresa.fruver.domain.exceptions;


public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String msg) { super(msg); }
}
