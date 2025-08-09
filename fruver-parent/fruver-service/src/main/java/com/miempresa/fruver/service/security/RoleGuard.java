// fruver-service/src/main/java/com/miempresa/fruver/service/security/RoleGuard.java
package com.miempresa.fruver.service.security;

import com.miempresa.fruver.domain.exceptions.AuthenticationException;
import com.miempresa.fruver.domain.model.Usuario;
import com.miempresa.fruver.service.port.InputPort;

/**
 * Decorador de InputPort que verifica que el usuario actual
 * tenga el rol requerido antes de delegar la ejecución.
 */
public class RoleGuard<I, O> implements InputPort<I, O> {
    private final InputPort<I, O> delegate;
    private final Usuario.Role requiredRole;

    public RoleGuard(InputPort<I, O> delegate, Usuario.Role requiredRole) {
        this.delegate = delegate;
        this.requiredRole = requiredRole;
    }

    @Override
    public O execute(I input) {
        Usuario u = SecurityContext.getCurrentUser();
        if (u == null) {
            throw new AuthenticationException("Usuario no autenticado");
        }
        if (u.getRol() != requiredRole) {
            throw new AuthenticationException(
                    "Acceso denegado: requiere rol " + requiredRole);
        }
        return delegate.execute(input);
    }
}
