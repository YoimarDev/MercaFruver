package com.miempresa.fruver.infra.db;

import com.miempresa.fruver.domain.model.Venta;
import com.miempresa.fruver.domain.repository.VentaRepository;
import com.miempresa.fruver.domain.exceptions.DataAccessException;
import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class VentaRepositoryJdbc implements VentaRepository {
    private final DataSource ds;
    public VentaRepositoryJdbc(DataSource ds) { this.ds = ds; }

    @Override
    public Venta save(Venta v) {
        String sql = "INSERT INTO VENTA(fecha, cajero_id, total, recibido, vuelto) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setTimestamp(1, Timestamp.valueOf(v.getFecha()));
            ps.setInt(2, v.getCajeroId());
            ps.setBigDecimal(3, v.getTotal());
            ps.setBigDecimal(4, v.getRecibido());
            ps.setBigDecimal(5, v.getVuelto());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) v = new Venta(rs.getInt(1), v.getFecha(), v.getCajeroId());
            }
            return v;
        } catch (SQLException ex) {
            throw new DataAccessException("Error guardando venta", ex);
        }
    }

    @Override
    public Optional<Venta> findById(Integer id) {
        String sql = "SELECT * FROM VENTA WHERE venta_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Error buscando venta por ID", ex);
        }
    }

    @Override
    public List<Venta> findByDateRange(LocalDate from, LocalDate to) {
        String sql = "SELECT * FROM VENTA WHERE fecha BETWEEN ? AND ?";
        List<Venta> list = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(from.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(to.atTime(23, 59, 59)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Error listando ventas por rango", ex);
        }
    }

    private Venta mapRow(ResultSet rs) throws SQLException {
        return new Venta(
                rs.getInt("venta_id"), rs.getTimestamp("fecha").toLocalDateTime(), rs.getInt("cajero_id")
        );
    }
}