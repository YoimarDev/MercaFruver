package com.miempresa.fruver.service.usecase;

import com.miempresa.fruver.domain.exceptions.DomainException;
import com.miempresa.fruver.domain.model.Usuario;
import com.miempresa.fruver.domain.repository.UsuarioRepository;
import com.miempresa.fruver.service.port.InputPort;
import com.miempresa.fruver.service.port.UpdateUserRequest;
import com.miempresa.fruver.service.security.SecurityContext;
import org.mindrot.jbcrypt.BCrypt;

public class UpdateUserUseCase implements InputPort<UpdateUserRequest, Usuario> {
    private final UsuarioRepository repo;

    public UpdateUserUseCase(UsuarioRepository repo) {
        this.repo = repo;
    }

    @Override
    public Usuario execute(UpdateUserRequest req) {
        Usuario existing = repo.findById(req.getUsuarioId())
                .orElseThrow(() -> new DomainException("Usuario no existe: " + req.getUsuarioId()));

        // Si estoy actualizando mi propio ADMIN, no permito cambiar el rol
        Usuario current = SecurityContext.getCurrentUser();
        boolean isSelfAdmin = current != null
                && current.getRol() == Usuario.Role.ADMIN
                && current.getUsuarioId().equals(existing.getUsuarioId());

        // Nombre
        String name = req.getNuevoNombre().isBlank()
                ? existing.getNombre()
                : req.getNuevoNombre();

        // Password
        String hash = existing.getPasswordHash();
        if (!req.getNuevaPassword().isBlank()) {
            if (req.getNuevaPassword().length() < 4)
                throw new DomainException("Password muy corto");
            hash = BCrypt.hashpw(req.getNuevaPassword(), BCrypt.gensalt(10));
        }

        // Rol: si es self-admin, conservo existing rol; si no, uso el pedido
        Usuario.Role role = existing.getRol();
        if (!isSelfAdmin) {
            // para otros casos (admin actualizando otro usuario) sí permito cambiar rol
            if (req.getNuevoRol() != null) {
                role = req.getNuevoRol();
            }
        }

        Usuario updated = new Usuario(existing.getUsuarioId(), name, role, hash);
        return repo.update(updated);
    }
}
