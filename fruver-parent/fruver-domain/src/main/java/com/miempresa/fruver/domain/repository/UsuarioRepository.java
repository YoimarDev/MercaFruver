package com.miempresa.fruver.domain.repository;

import com.miempresa.fruver.domain.model.Usuario;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository {
    Usuario save(Usuario u);
    Optional<Usuario> findById(Integer id);
    Optional<Usuario> findByName(String name);
    List<Usuario> findAll();
    void delete(Integer id);
    Usuario update(Usuario u);
}
