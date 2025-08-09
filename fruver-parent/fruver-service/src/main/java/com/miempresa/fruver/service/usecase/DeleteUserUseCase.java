// Eliminar usuario
// fruver-service/src/main/java/com/miempresa/fruver/service/usecase/DeleteUserUseCase.java
package com.miempresa.fruver.service.usecase;

import com.miempresa.fruver.domain.exceptions.DomainException;
import com.miempresa.fruver.domain.repository.UsuarioRepository;
import com.miempresa.fruver.service.port.InputPort;

public class DeleteUserUseCase implements InputPort<Integer, Void> {
    private final UsuarioRepository repo;
    public DeleteUserUseCase(UsuarioRepository repo) { this.repo = repo; }

    @Override
    public Void execute(Integer id) {
        repo.findById(id)
                .orElseThrow(() -> new DomainException("Usuario no existe: " + id));
        repo.delete(id);
        return null;
    }
}
