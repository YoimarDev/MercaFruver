// File: fruver-service/src/main/java/com/miempresa/fruver/service/usecase/dto/CreateProductRequest.java
package com.miempresa.fruver.service.port;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * DTO para crear/actualizar productos desde la capa de servicio / UI.
 *
 * Nota: para el campo 'tipo' se utiliza la representación String "PESO" | "UNIDAD"
 * y luego se convierte a Producto.TipoProducto en los usecases.
 */
public final class CreateProductRequest {
    public final Integer productoId; // null = crear, non-null = actualizar
    public final String codigo;
    public final String nombre;
    public final String tipo; // "PESO" o "UNIDAD"
    public final BigDecimal precioUnitario;
    public final BigDecimal stockActual;
    public final BigDecimal stockUmb;
    public final String imagenPath; // ruta relativa/absoluta, nullable

    public CreateProductRequest(Integer productoId,
                                String codigo,
                                String nombre,
                                String tipo,
                                BigDecimal precioUnitario,
                                BigDecimal stockActual,
                                BigDecimal stockUmb,
                                String imagenPath) {
        this.productoId = productoId;
        this.codigo = Objects.requireNonNull(codigo, "codigo es requerido");
        this.nombre = Objects.requireNonNull(nombre, "nombre es requerido");
        this.tipo = Objects.requireNonNull(tipo, "tipo es requerido");
        this.precioUnitario = precioUnitario == null ? BigDecimal.ZERO : precioUnitario;
        this.stockActual = stockActual == null ? BigDecimal.ZERO : stockActual;
        this.stockUmb = stockUmb == null ? BigDecimal.ZERO : stockUmb;
        this.imagenPath = imagenPath;
    }
}
