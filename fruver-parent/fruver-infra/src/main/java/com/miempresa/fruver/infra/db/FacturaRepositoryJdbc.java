package com.miempresa.fruver.infra.db;

import com.miempresa.fruver.domain.model.Factura;
import com.miempresa.fruver.domain.repository.FacturaRepository;
import com.miempresa.fruver.domain.exceptions.DataAccessException;
import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;


public class FacturaRepositoryJdbc implements FacturaRepository {
    private final DataSource ds;
    public FacturaRepositoryJdbc(DataSource ds) { this.ds = ds; }

    @Override
    public Factura save(Factura f) {
        String sql = "INSERT INTO FACTURA(venta_id, folio, impresa, fecha_impresion) VALUES (?, ?, ?, ?)";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, f.getVentaId());
            ps.setString(2, f.getFolio());
            ps.setBoolean(3, f.isImpresa());
            ps.setTimestamp(4, f.isImpresa() ? Timestamp.valueOf(f.getFechaImpresion()) : null);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) f = new Factura(rs.getInt(1), f.getVentaId(), f.getFolio());
            }
            return f;
        } catch (SQLException ex) {
            throw new DataAccessException("Error guardando factura", ex);
        }
    }

    @Override
    public Optional<Factura> findByVentaId(Integer ventaId) {
        String sql = "SELECT * FROM FACTURA WHERE venta_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, ventaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Factura f = new Factura(rs.getInt("factura_id"), ventaId, rs.getString("folio"));
                    if (rs.getBoolean("impresa")) {
                        f.markPrinted(rs.getTimestamp("fecha_impresion").toLocalDateTime());
                    }
                    return Optional.of(f);
                }
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Error buscando factura", ex);
        }
    }
}
