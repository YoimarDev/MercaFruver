// File: fruver-service/src/main/java/com/miempresa/fruver/service/usecase/CreateProductUseCase.java
package com.miempresa.fruver.service.usecase;

import com.miempresa.fruver.domain.model.Producto;
import com.miempresa.fruver.domain.model.Producto.TipoProducto;
import com.miempresa.fruver.domain.repository.ProductoRepository;
import com.miempresa.fruver.service.port.InputPort;
import com.miempresa.fruver.service.port.CreateProductRequest;

import java.util.Objects;

/**
 * UseCase para crear un producto.
 * Retorna la entidad Producto creada (con productoId).
 */
public class CreateProductUseCase implements InputPort<CreateProductRequest, Producto> {

    private final ProductoRepository productoRepo;

    public CreateProductUseCase(ProductoRepository productoRepo) {
        this.productoRepo = Objects.requireNonNull(productoRepo, "productoRepo requerido");
    }

    @Override
    public Producto execute(CreateProductRequest req) {
        Objects.requireNonNull(req, "request es requerido");

        // validar tipo
        TipoProducto tipoEnum;
        try {
            tipoEnum = TipoProducto.valueOf(req.tipo.toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Tipo inválido. Debe ser 'PESO' o 'UNIDAD'");
        }

        Producto p = new Producto(
                null,
                req.codigo.trim(),
                req.nombre.trim(),
                req.precioUnitario,
                tipoEnum,
                req.stockActual,
                req.stockUmb,
                req.imagenPath
        );

        // delegar persistencia
        return productoRepo.save(p);
    }
}
