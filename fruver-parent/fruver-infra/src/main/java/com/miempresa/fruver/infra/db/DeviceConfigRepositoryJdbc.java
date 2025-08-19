package com.miempresa.fruver.infra.db;

import com.miempresa.fruver.domain.model.DeviceConfig;
import com.miempresa.fruver.domain.model.DeviceConfig.DeviceType;
import com.miempresa.fruver.domain.repository.DeviceConfigRepository;
import com.miempresa.fruver.domain.exceptions.DataAccessException;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DeviceConfigRepositoryJdbc implements DeviceConfigRepository {
    private final DataSource ds;

    public DeviceConfigRepositoryJdbc(DataSource ds) {
        this.ds = ds;
    }

    @Override
    public DeviceConfig save(DeviceConfig d) {
        System.out.println("[DeviceConfigRepositoryJdbc] save() tipo=" + d.getTipo() + " puerto=" + d.getPuerto() + " params=" + d.getParametrosJson());
        // Primero intentar actualizar por tipo (si existe)
        String updateSql = "UPDATE CONFIG_DISP SET puerto = ?, parametros = ? WHERE tipo = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(updateSql)) {
            ps.setString(1, d.getPuerto());
            ps.setString(2, d.getParametrosJson());
            ps.setString(3, d.getTipo().name());
            int rows = ps.executeUpdate();
            if (rows > 0) {
                // Recuperar el registro actualizado para devolver objeto con id
                String select = "SELECT config_id, tipo, puerto, parametros FROM CONFIG_DISP WHERE tipo = ?";
                try (PreparedStatement ps2 = c.prepareStatement(select)) {
                    ps2.setString(1, d.getTipo().name());
                    try (ResultSet rs = ps2.executeQuery()) {
                        if (rs.next()) return mapRow(rs);
                    }
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Error actualizando DeviceConfig", ex);
        }

        // Si no actualizamos, insertamos
        String insertSql = "INSERT INTO CONFIG_DISP(tipo, puerto, parametros) VALUES (?, ?, ?)";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, d.getTipo().name());
            ps.setString(2, d.getPuerto());
            ps.setString(3, d.getParametrosJson());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return new DeviceConfig(rs.getInt(1), d.getTipo(), d.getPuerto(), d.getParametrosJson());
                }
            }
            // Si no hay generated key, devolver objeto original (sin id)
            return d;
        } catch (SQLException ex) {
            throw new DataAccessException("Error guardando DeviceConfig", ex);
        }
    }

    @Override
    public Optional<DeviceConfig> findByType(DeviceType type) {
        String sql = "SELECT * FROM CONFIG_DISP WHERE tipo = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Error buscando DeviceConfig por tipo", ex);
        }
    }

    @Override
    public List<DeviceConfig> findAll() {
        String sql = "SELECT * FROM CONFIG_DISP";
        List<DeviceConfig> list = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
            return list;
        } catch (SQLException ex) {
            throw new DataAccessException("Error listando DeviceConfig", ex);
        }
    }

    private DeviceConfig mapRow(ResultSet rs) throws SQLException {
        return new DeviceConfig(
                rs.getInt("config_id"),
                DeviceType.valueOf(rs.getString("tipo")),
                rs.getString("puerto"),
                rs.getString("parametros")
        );
    }
}
