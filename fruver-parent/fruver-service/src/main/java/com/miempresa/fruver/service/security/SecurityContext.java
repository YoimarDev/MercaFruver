// fruver-service/src/main/java/com/miempresa/fruver/service/security/SecurityContext.java
package com.miempresa.fruver.service.security;

import com.miempresa.fruver.domain.model.Usuario;

public class SecurityContext {
    private static final ThreadLocal<Usuario> currentUser = new ThreadLocal<>();

    /** Guarda el usuario que acaba de loguearse */
    public static void setCurrentUser(Usuario u) {
        currentUser.set(u);
    }

    /** Retorna el usuario actual, o null si no hay */
    public static Usuario getCurrentUser() {
        return currentUser.get();
    }

    /** Limpia el contexto (al hacer logout) */
    public static void clear() {
        currentUser.remove();
    }
}
