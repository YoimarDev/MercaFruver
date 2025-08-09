package com.miempresa.fruver.service.usecase;

import com.miempresa.fruver.domain.exceptions.AuthenticationException;
import com.miempresa.fruver.domain.model.Usuario;
import com.miempresa.fruver.domain.repository.UsuarioRepository;
import org.mindrot.jbcrypt.BCrypt;

public class LoginUseCase {
    private final UsuarioRepository usuarioRepo;

    public LoginUseCase(UsuarioRepository usuarioRepo) {
        this.usuarioRepo = usuarioRepo;
    }

    public Usuario login(String username, String password) {
        Usuario user = usuarioRepo.findByName(username)
                .orElseThrow(() -> new AuthenticationException("Usuario no encontrado"));

        // 1) Extraer el hash de la base de datos
        String hash = user.getPasswordHash();

        // 2) Normalizar prefijo $2y$ → $2a$
        if (hash.startsWith("$2y$")) {
            hash = "$2a$" + hash.substring(4);
        }

        // 3) Verificar la contraseña contra el hash normalizado
        if (!BCrypt.checkpw(password, hash)) {
            throw new AuthenticationException("Contraseña incorrecta");
        }

        // 4) Si pasa, devolvemos el usuario
        return user;
    }
}
