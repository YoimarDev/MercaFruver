package com.miempresa.fruver.infra.db;

import com.miempresa.fruver.domain.model.VentaItem;
import com.miempresa.fruver.domain.repository.VentaItemRepository;
import com.miempresa.fruver.domain.exceptions.DataAccessException;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;


public class VentaItemRepositoryJdbc implements VentaItemRepository {
    private final DataSource ds;
    public VentaItemRepositoryJdbc(DataSource ds) { this.ds = ds; }

    @Override
    public VentaItem save(VentaItem item) {
        String sql = "INSERT INTO VENTA_ITEM(venta_id, producto_id, cantidad, precio_unit, subtotal) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, item.getVentaId());
            ps.setInt(2, item.getProductoId());
            ps.setBigDecimal(3, item.getCantidad());
            ps.setBigDecimal(4, item.getPrecioUnit());
            ps.setBigDecimal(5, item.getSubtotal());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) item = new VentaItem(rs.getInt(1), item.getVentaId(), item.getProductoId(), item.getCantidad(), item.getPrecioUnit());
            }
            return item;
        } catch (SQLException ex) {
            throw new DataAccessException("Error guardando item de venta", ex);
        }
    }

    @Override
    public List<VentaItem> findByVentaId(Integer ventaId) {
        String sql = "SELECT * FROM VENTA_ITEM WHERE venta_id = ?";
        List<VentaItem> list = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, ventaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Error listando items de venta", ex);
        }
    }

    private VentaItem mapRow(ResultSet rs) throws SQLException {
        return new VentaItem(
                rs.getInt("item_id"), rs.getInt("venta_id"), rs.getInt("producto_id"),
                rs.getBigDecimal("cantidad"), rs.getBigDecimal("precio_unit")
        );
    }
}
