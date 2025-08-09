// fruver-service/src/main/java/com/miempresa/fruver/service/port/CreateUserRequest.java
package com.miempresa.fruver.service.port;

import com.miempresa.fruver.domain.model.Usuario;

public class CreateUserRequest {
    private final String nombre;
    private final String password;
    private final Usuario.Role rol;

    public CreateUserRequest(String nombre, String password, Usuario.Role rol) {
        this.nombre = nombre;
        this.password = password;
        this.rol = rol;
    }

    public String getNombre() { return nombre; }
    public String getPassword() { return password; }
    public Usuario.Role getRol() { return rol; }
}
