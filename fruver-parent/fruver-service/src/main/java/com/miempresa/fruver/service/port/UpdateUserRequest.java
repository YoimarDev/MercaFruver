// fruver-service/src/main/java/com/miempresa/fruver/service/port/UpdateUserRequest.java
package com.miempresa.fruver.service.port;

import com.miempresa.fruver.domain.model.Usuario;

public class UpdateUserRequest {
    private final Integer usuarioId;
    private final String nuevoNombre;
    private final String nuevaPassword;
    private final Usuario.Role nuevoRol;

    public UpdateUserRequest(Integer usuarioId,
                             String nuevoNombre,
                             String nuevaPassword,
                             Usuario.Role nuevoRol) {
        this.usuarioId = usuarioId;
        this.nuevoNombre = nuevoNombre;
        this.nuevaPassword = nuevaPassword;
        this.nuevoRol = nuevoRol;
    }

    public Integer getUsuarioId() { return usuarioId; }
    public String getNuevoNombre() { return nuevoNombre; }
    public String getNuevaPassword() { return nuevaPassword; }
    public Usuario.Role getNuevoRol() { return nuevoRol; }
}
