package com.miempresa.fruver.service.usecase;

import com.miempresa.fruver.domain.model.Usuario;
import com.miempresa.fruver.domain.repository.UsuarioRepository;
import com.miempresa.fruver.service.port.InputPort;

import java.util.List;

public class ListUsersUseCase implements InputPort<Void, List<Usuario>> {
    private final UsuarioRepository repo;
    public ListUsersUseCase(UsuarioRepository repo) { this.repo = repo; }
    @Override
    public List<Usuario> execute(Void v) {
        return repo.findAll();
    }
}