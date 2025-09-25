package com.miempresa.fruver.service.port;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * DTO para una línea/item de venta.
 * Usado por CreateSaleRequest y por casos de uso que persistan ventas.
 */
public final class SaleItemDto implements Serializable {
    private final Integer productoId;
    private final String codigo;
    private final String nombre;
    private final BigDecimal cantidad;
    private final BigDecimal precioUnitario;
    private final BigDecimal subtotal;

    public SaleItemDto(Integer productoId, String codigo, String nombre,
                       BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {
        this.productoId = productoId;
        this.codigo = codigo;
        this.nombre = nombre;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.subtotal = subtotal;
    }

    public Integer getProductoId() { return productoId; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public BigDecimal getCantidad() { return cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public BigDecimal getSubtotal() { return subtotal; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SaleItemDto)) return false;
        SaleItemDto that = (SaleItemDto) o;
        return Objects.equals(productoId, that.productoId) &&
                Objects.equals(codigo, that.codigo) &&
                Objects.equals(nombre, that.nombre) &&
                Objects.equals(cantidad, that.cantidad) &&
                Objects.equals(precioUnitario, that.precioUnitario) &&
                Objects.equals(subtotal, that.subtotal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productoId, codigo, nombre, cantidad, precioUnitario, subtotal);
    }

    @Override
    public String toString() {
        return "SaleItemDto{" +
                "productoId=" + productoId +
                ", codigo='" + codigo + '\'' +
                ", nombre='" + nombre + '\'' +
                ", cantidad=" + cantidad +
                ", precioUnitario=" + precioUnitario +
                ", subtotal=" + subtotal +
                '}';
    }
}
