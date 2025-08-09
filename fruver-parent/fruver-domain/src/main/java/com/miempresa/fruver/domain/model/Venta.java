package com.miempresa.fruver.domain.model;

import com.miempresa.fruver.domain.exceptions.InvalidOperationException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Representa una venta.
 */
public class Venta {
    private Integer ventaId;
    private LocalDateTime fecha;
    private Integer cajeroId;
    private BigDecimal total;
    private BigDecimal recibido;
    private BigDecimal vuelto;

    public Venta(Integer ventaId, LocalDateTime fecha, Integer cajeroId) {
        this.ventaId = ventaId;
        this.fecha = fecha;
        this.cajeroId = cajeroId;
        this.total = BigDecimal.ZERO;
        this.recibido = BigDecimal.ZERO;
        this.vuelto = BigDecimal.ZERO;
    }

    public Integer getVentaId() { return ventaId; }
    public LocalDateTime getFecha() { return fecha; }
    public Integer getCajeroId() { return cajeroId; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getRecibido() { return recibido; }
    public BigDecimal getVuelto() { return vuelto; }

    /** Agrega un subtotal al total de la venta. */
    public void addItem(BigDecimal subtotal) {
        total = total.add(subtotal);
    }

    /** Calcula el vuelto en base al monto recibido. */
    public void calcularVuelto(BigDecimal recibido) {
        if (recibido.compareTo(total) < 0) {
            throw new InvalidOperationException(
                    String.format("Monto recibido %s menor que total %s", recibido, total));
        }
        this.recibido = recibido;
        this.vuelto = recibido.subtract(total);
    }
}