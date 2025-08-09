// fruver-service/src/main/java/com/miempresa/fruver/service/usecase/CreateUserUseCase.java
package com.miempresa.fruver.service.usecase;

import com.miempresa.fruver.domain.exceptions.DomainException;
import com.miempresa.fruver.domain.model.Usuario;
import com.miempresa.fruver.domain.repository.UsuarioRepository;
import com.miempresa.fruver.service.port.CreateUserRequest;
import com.miempresa.fruver.service.port.InputPort;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Crea un Usuario (CAJERO o SUPERVISOR).
 * Sólo el ADMIN (en RoleGuard) puede invocar este UseCase.
 */
public class CreateUserUseCase implements InputPort<CreateUserRequest, Usuario> {
    private final UsuarioRepository repo;

    public CreateUserUseCase(UsuarioRepository repo) {
        this.repo = repo;
    }

    @Override
    public Usuario execute(CreateUserRequest req) {
        // Validaciones básicas
        if (req.getNombre().isBlank())
            throw new DomainException("Nombre inválido");
        if (req.getPassword().length() < 4)
            throw new DomainException("Password muy corto");

        // Hashear la contraseña
        String hash = BCrypt.hashpw(req.getPassword(), BCrypt.gensalt(10));

        // Construir dominio y persistir
        Usuario u = new Usuario(
                null,
                req.getNombre(),
                req.getRol(),
                hash
        );
        return repo.save(u);
    }
}
