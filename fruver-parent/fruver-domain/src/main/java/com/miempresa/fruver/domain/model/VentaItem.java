package com.miempresa.fruver.domain.model;

import java.math.BigDecimal;

/**
 * Representa un ítem de venta.
 */
public class VentaItem {
    private Integer itemId;
    private Integer ventaId;
    private Integer productoId;
    private BigDecimal cantidad;
    private BigDecimal precioUnit;
    private BigDecimal subtotal;

    public VentaItem(Integer itemId, Integer ventaId, Integer productoId,
                     BigDecimal cantidad, BigDecimal precioUnit) {
        this.itemId = itemId;
        this.ventaId = ventaId;
        this.productoId = productoId;
        this.cantidad = cantidad;
        this.precioUnit = precioUnit;
        this.subtotal = precioUnit.multiply(cantidad);
    }

    public Integer getItemId() { return itemId; }
    public Integer getVentaId() { return ventaId; }
    public Integer getProductoId() { return productoId; }
    public BigDecimal getCantidad() { return cantidad; }
    public BigDecimal getPrecioUnit() { return precioUnit; }
    public BigDecimal getSubtotal() { return subtotal; }
}
