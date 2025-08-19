// File: fruver-domain/src/main/java/com/miempresa/fruver/domain/model/Producto.java
package com.miempresa.fruver.domain.model;

import com.miempresa.fruver.domain.exceptions.DomainException;
import com.miempresa.fruver.domain.exceptions.InvalidOperationException;
import java.math.BigDecimal;

/**
 * Representa un producto en el catálogo.
 */
public class Producto {
    private Integer productoId;
    private String codigo;
    private String nombre;
    private BigDecimal precioUnitario;
    private TipoProducto tipo;
    private BigDecimal stockActual;
    private BigDecimal stockUmbral;
    // Nuevo campo: ruta a la imagen (nullable)
    private String imagenPath;

    public enum TipoProducto {PESO, UNIDAD}

    public Producto(Integer productoId, String codigo, String nombre,
                    BigDecimal precioUnitario, TipoProducto tipo,
                    BigDecimal stockActual, BigDecimal stockUmbral) {
        this(productoId, codigo, nombre, precioUnitario, tipo, stockActual, stockUmbral, null);
    }

    // Constructor extendido que incluye imagenPath
    public Producto(Integer productoId, String codigo, String nombre,
                    BigDecimal precioUnitario, TipoProducto tipo,
                    BigDecimal stockActual, BigDecimal stockUmbral, String imagenPath) {
        if (codigo == null || codigo.isBlank())
            throw new DomainException("Código inválido");
        if (nombre == null || nombre.isBlank())
            throw new DomainException("Nombre inválido");
        this.productoId = productoId;
        this.codigo = codigo;
        this.nombre = nombre;
        this.precioUnitario = precioUnitario;
        this.tipo = tipo;
        this.stockActual = stockActual;
        this.stockUmbral = stockUmbral;
        this.imagenPath = imagenPath;
    }

    public Integer getProductoId() {
        return productoId;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public TipoProducto getTipo() {
        return tipo;
    }

    public BigDecimal getStockActual() {
        return stockActual;
    }

    public BigDecimal getStockUmbral() {
        return stockUmbral;
    }

    /**
     * Nuevo getter para la ruta de la imagen (puede ser null).
     */
    public String getImagenPath() {
        return imagenPath;
    }

    /**
     * Nuevo setter (útil para actualizar desde repositorio / usecases / UI).
     */
    public void setImagenPath(String imagenPath) {
        this.imagenPath = imagenPath;
    }

    /**
     * Ajusta el stock en delta (puede ser negativo). Lanza InvalidOperationException si resultaría negativo.
     */
    public void adjustStock(BigDecimal delta) {
        BigDecimal nuevo = stockActual.add(delta);
        if (nuevo.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidOperationException(
                    String.format("Stock insuficiente para %s. Intento de ajustar por %s, pero stock es %s",
                            codigo, delta, stockActual));
        }
        stockActual = nuevo;
    }

    /**
     * Indica si el stock actual está por debajo o igual al umbral.
     */
    public boolean isStockLow() {
        return stockActual.compareTo(stockUmbral) <= 0;
    }
}
