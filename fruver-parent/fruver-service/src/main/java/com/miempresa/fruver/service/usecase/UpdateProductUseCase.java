// File: fruver-service/src/main/java/com/miempresa/fruver/service/usecase/UpdateProductUseCase.java
package com.miempresa.fruver.service.usecase;

import com.miempresa.fruver.domain.model.Producto;
import com.miempresa.fruver.domain.model.Producto.TipoProducto;
import com.miempresa.fruver.domain.repository.ProductoRepository;
import com.miempresa.fruver.service.port.InputPort;
import com.miempresa.fruver.service.port.CreateProductRequest;

import java.util.Objects;
import java.util.Optional;

/**
 * UseCase para actualizar producto existente.
 * Recibe CreateProductRequest donde productoId == id del producto a actualizar.
 */
public class UpdateProductUseCase implements InputPort<CreateProductRequest, Producto> {

    private final ProductoRepository productoRepo;

    public UpdateProductUseCase(ProductoRepository productoRepo) {
        this.productoRepo = Objects.requireNonNull(productoRepo, "productoRepo requerido");
    }

    @Override
    public Producto execute(CreateProductRequest req) {
        Objects.requireNonNull(req, "request es requerido");
        if (req.productoId == null) throw new IllegalArgumentException("productoId es requerido para actualizar");

        Optional<Producto> opt = productoRepo.findById(req.productoId);
        Producto existing = opt.orElseThrow(() -> new IllegalArgumentException("Producto no encontrado: " + req.productoId));

        // Determinar valores finales (si el caller pasa null/blank, usar el valor existente)
        String codigo = req.codigo == null || req.codigo.isBlank() ? existing.getCodigo() : req.codigo.trim();
        String nombre = req.nombre == null || req.nombre.isBlank() ? existing.getNombre() : req.nombre.trim();

        TipoProducto tipoEnum;
        if (req.tipo == null || req.tipo.isBlank()) {
            tipoEnum = existing.getTipo();
        } else {
            try {
                tipoEnum = TipoProducto.valueOf(req.tipo.toUpperCase());
            } catch (Exception ex) {
                throw new IllegalArgumentException("Tipo inválido. Debe ser 'PESO' o 'UNIDAD'");
            }
        }

        // precio / stock: si null -> conservar
        java.math.BigDecimal precio = req.precioUnitario == null ? existing.getPrecioUnitario() : req.precioUnitario;
        java.math.BigDecimal stockActual = req.stockActual == null ? existing.getStockActual() : req.stockActual;
        java.math.BigDecimal stockUmb = req.stockUmb == null ? existing.getStockUmbral() : req.stockUmb;
        String imagenPath = req.imagenPath == null ? existing.getImagenPath() : req.imagenPath;

        // Crear nueva instancia Producto con los datos actualizados (constructor que acepta imagenPath)
        Producto updated = new Producto(
                existing.getProductoId(),
                codigo,
                nombre,
                precio,
                tipoEnum,
                stockActual,
                stockUmb,
                imagenPath
        );

        return productoRepo.update(updated);
    }
}
