// File: fruver-service/src/main/java/com/miempresa/fruver/service/usecase/ListProductsUseCase.java
package com.miempresa.fruver.service.usecase;

import com.miempresa.fruver.domain.model.Producto;
import com.miempresa.fruver.domain.repository.ProductoRepository;
import com.miempresa.fruver.service.port.InputPort;

import java.util.List;
import java.util.Objects;

/**
 * UseCase para listar todos los productos.
 * Input: null (no requiere parámetros).
 */
public class ListProductsUseCase implements InputPort<Void, List<Producto>> {

    private final ProductoRepository productoRepo;

    public ListProductsUseCase(ProductoRepository productoRepo) {
        this.productoRepo = Objects.requireNonNull(productoRepo, "productoRepo requerido");
    }

    @Override
    public List<Producto> execute(Void unused) {
        return productoRepo.findAll();
    }
}
