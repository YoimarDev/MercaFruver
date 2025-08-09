package com.miempresa.fruver.domain.repository;

import com.miempresa.fruver.domain.model.Producto;
import java.util.List;
import java.util.Optional;

public interface ProductoRepository {
    Producto save(Producto p);
    Optional<Producto> findByCodigo(String codigo);
    List<Producto> findAll();
    void updateStock(Integer productoId, java.math.BigDecimal newStock);
}