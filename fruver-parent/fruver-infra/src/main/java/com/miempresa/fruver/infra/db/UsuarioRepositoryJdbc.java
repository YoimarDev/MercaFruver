package com.miempresa.fruver.infra.db;

import com.miempresa.fruver.domain.model.Usuario;
import com.miempresa.fruver.domain.repository.UsuarioRepository;
import com.miempresa.fruver.domain.exceptions.DataAccessException;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public class UsuarioRepositoryJdbc implements UsuarioRepository {
    private final DataSource ds;
    public UsuarioRepositoryJdbc(DataSource ds) { this.ds = ds; }

    @Override
    public Usuario save(Usuario u) {
        String sql = "INSERT INTO USUARIO(nombre, rol, password_hash) VALUES (?, ?, ?)";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.getNombre());
            ps.setString(2, u.getRol().name());
            ps.setString(3, u.getPasswordHash());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    u = new Usuario(rs.getInt(1), u.getNombre(), u.getRol(), u.getPasswordHash());
                }
            }
            return u;
        } catch (SQLException ex) {
            throw new DataAccessException("Error guardando usuario", ex);
        }
    }

    @Override
    public Optional<Usuario> findById(Integer id) {
        String sql = "SELECT * FROM USUARIO WHERE usuario_id = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Error buscando usuario por ID", ex);
        }
    }

    @Override
    public Optional<Usuario> findByName(String name) {
        String sql = "SELECT * FROM USUARIO WHERE nombre = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Error buscando usuario por nombre", ex);
        }
    }

    @Override
    public List<Usuario> findAll() {
        String sql = "SELECT * FROM USUARIO";
        List<Usuario> list = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException ex) {
            throw new DataAccessException("Error listando usuarios", ex);
        }
    }

    @Override
    public void delete(Integer id) {
        String sql = "DELETE FROM USUARIO WHERE usuario_id = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("No existe usuario con id=" + id);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Error eliminando usuario", ex);
        }
    }
    @Override
    public Usuario update(Usuario u) {
        String sql = """
        UPDATE USUARIO
           SET nombre = ?,
               rol = ?,
               password_hash = ?
         WHERE usuario_id = ?
        """;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, u.getNombre());
            ps.setString(2, u.getRol().name());
            ps.setString(3, u.getPasswordHash());
            ps.setInt(4, u.getUsuarioId());
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new DataAccessException("No existe usuario con id=" + u.getUsuarioId());
            }
            return u;
        } catch (SQLException ex) {
            throw new DataAccessException("Error actualizando usuario", ex);
        }
    }


    private Usuario mapRow(ResultSet rs) throws SQLException {
        return new Usuario(
                rs.getInt("usuario_id"),
                rs.getString("nombre"),
                Usuario.Role.valueOf(rs.getString("rol")),
                rs.getString("password_hash")
        );
    }
}