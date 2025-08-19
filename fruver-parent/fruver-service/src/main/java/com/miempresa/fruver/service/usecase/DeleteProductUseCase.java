// File: fruver-service/src/main/java/com/miempresa/fruver/service/usecase/DeleteProductUseCase.java
package com.miempresa.fruver.service.usecase;

import com.miempresa.fruver.domain.repository.ProductoRepository;
import com.miempresa.fruver.service.port.InputPort;

import java.util.Objects;

/**
 * UseCase para eliminar un producto por id.
 */
public class DeleteProductUseCase implements InputPort<Integer, Void> {

    private final ProductoRepository productoRepo;

    public DeleteProductUseCase(ProductoRepository productoRepo) {
        this.productoRepo = Objects.requireNonNull(productoRepo, "productoRepo requerido");
    }

    @Override
    public Void execute(Integer productoId) {
        Objects.requireNonNull(productoId, "productoId requerido");
        productoRepo.delete(productoId);
        return null;
    }
}
