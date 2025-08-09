package com.miempresa.fruver.infra.db;

import com.miempresa.fruver.domain.model.Producto;
import com.miempresa.fruver.domain.repository.ProductoRepository;
import com.miempresa.fruver.domain.exceptions.DataAccessException;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.math.BigDecimal;

public class ProductoRepositoryJdbc implements ProductoRepository {
    private final DataSource ds;
    public ProductoRepositoryJdbc(DataSource ds) { this.ds = ds; }

    @Override
    public Producto save(Producto p) {
        String sql = "INSERT INTO PRODUCTO(codigo, nombre, precio_unitario, tipo, stock_actual, stock_umb) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            c.setAutoCommit(false);
            ps.setString(1, p.getCodigo());
            ps.setString(2, p.getNombre());
            ps.setBigDecimal(3, p.getPrecioUnitario());
            ps.setString(4, p.getTipo().name());
            ps.setBigDecimal(5, p.getStockActual());
            ps.setBigDecimal(6, p.getStockUmbral());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) p = new Producto(rs.getInt(1), p.getCodigo(), p.getNombre(), p.getPrecioUnitario(), p.getTipo(), p.getStockActual(), p.getStockUmbral());
            }
            c.commit();
            return p;
        } catch (SQLException ex) {
            throw new DataAccessException("Error guardando producto", ex);
        }
    }

    @Override
    public Optional<Producto> findByCodigo(String codigo) {
        String sql = "SELECT * FROM PRODUCTO WHERE codigo = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Error buscando producto por codigo", ex);
        }
    }

    @Override
    public List<Producto> findAll() {
        String sql = "SELECT * FROM PRODUCTO";
        List<Producto> list = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
            return list;
        } catch (SQLException ex) {
            throw new DataAccessException("Error listando productos", ex);
        }
    }

    @Override
    public void updateStock(Integer productoId, BigDecimal newStock) {
        String sql = "UPDATE PRODUCTO SET stock_actual = ? WHERE producto_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBigDecimal(1, newStock);
            ps.setInt(2, productoId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("Error actualizando stock", ex);
        }
    }

    private Producto mapRow(ResultSet rs) throws SQLException {
        return new Producto(
                rs.getInt("producto_id"), rs.getString("codigo"), rs.getString("nombre"),
                rs.getBigDecimal("precio_unitario"), Producto.TipoProducto.valueOf(rs.getString("tipo")),
                rs.getBigDecimal("stock_actual"), rs.getBigDecimal("stock_umb")
        );
    }
}