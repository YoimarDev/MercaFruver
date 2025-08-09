package com.miempresa.fruver.domain.model;

import com.miempresa.fruver.domain.exceptions.DomainException;


/**
 * Representa un usuario del sistema.
 */
public class Usuario {
    private Integer usuarioId;
    private String nombre;
    private Role rol;
    private String passwordHash;

    public enum Role { CAJERO, SUPERVISOR, ADMIN }

    public Usuario(Integer usuarioId, String nombre, Role rol, String passwordHash) {
        if (nombre == null || nombre.isBlank())
            throw new DomainException("Nombre de usuario inválido");
        if (passwordHash == null || passwordHash.isBlank())
            throw new DomainException("Password hash inválido");
        this.usuarioId = usuarioId;
        this.nombre = nombre;
        this.rol = rol;
        this.passwordHash = passwordHash;
    }

    public Integer getUsuarioId() { return usuarioId; }
    public String getNombre() { return nombre; }
    public Role getRol() { return rol; }
    public String getPasswordHash() { return passwordHash; }
}
