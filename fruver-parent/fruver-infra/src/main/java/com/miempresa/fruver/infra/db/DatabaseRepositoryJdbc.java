package com.miempresa.fruver.infra.db;

import com.miempresa.fruver.domain.repository.DatabaseRepository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;

/**
 * Implementación JDBC de DatabaseRepository para MySQL/MariaDB.
 * Consulta tamaño total del esquema actual y, si es posible,
 * devuelve el datadir físico.
 */
public class DatabaseRepositoryJdbc implements DatabaseRepository {
    private final DataSource ds;

    public DatabaseRepositoryJdbc(DataSource ds) {
        this.ds = ds;
    }

    @Override
    public long getDatabaseUsedBytes() {
        String sql = "SELECT COALESCE(SUM(data_length + index_length),0) AS bytes " +
                "FROM information_schema.tables WHERE table_schema = DATABASE()";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getLong("bytes");
            }
            return 0L;

        } catch (SQLException ex) {
            throw new RuntimeException("Error consultando tamaño DB: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<String> getDataDirPath() {
        String sql = "SHOW VARIABLES LIKE 'datadir'";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                // el alias depende del driver, suele ser "Value" o la segunda columna
                String value;
                try {
                    value = rs.getString("Value");
                } catch (SQLException e) {
                    value = rs.getString(2); // fallback genérico
                }
                return Optional.ofNullable(value);
            }
            return Optional.empty();

        } catch (SQLException ex) {
            // algunos permisos o drivers pueden no exponerlo
            return Optional.empty();
        }
    }
}
