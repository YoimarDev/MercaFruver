// File: fruver-domain/src/main/java/com/miempresa/fruver/domain/repository/ProductoRepository.java
package com.miempresa.fruver.domain.repository;

import com.miempresa.fruver.domain.model.Producto;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

public interface ProductoRepository {
    Producto save(Producto p); // crea y devuelve con id
    Optional<Producto> findByCodigo(String codigo);
    Optional<Producto> findById(Integer id); // nuevo
    List<Producto> findAll();
    void updateStock(Integer productoId, BigDecimal newStock);
    Producto update(Producto p); // nuevo: actualiza y devuelve entidad actualizada
    void delete(Integer productoId); // nuevo
}
