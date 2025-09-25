package com.miempresa.fruver.service.port;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * DTO que representa la petición para registrar una venta.
 * - cajeroId: id del usuario que realizó la venta (opcional).
 * - items: líneas de venta.
 * - total/recibido/vuelto: valores monetarios.
 * - fecha: opcional, por defecto Instant.now() si null.
 *
 * Se recomienda que RegistrarVentaUseCase acepte esta clase como entrada.
 */
public final class CreateSaleRequest implements Serializable {
    private final Integer cajeroId;
    private final List<SaleItemDto> items;
    private final BigDecimal total;
    private final BigDecimal recibido;
    private final BigDecimal vuelto;
    private final Instant fecha;

    public CreateSaleRequest(Integer cajeroId, List<SaleItemDto> items,
                             BigDecimal total, BigDecimal recibido, BigDecimal vuelto, Instant fecha) {
        this.cajeroId = cajeroId;
        this.items = items;
        this.total = total;
        this.recibido = recibido;
        this.vuelto = vuelto;
        this.fecha = fecha;
    }

    public Integer getCajeroId() { return cajeroId; }
    public List<SaleItemDto> getItems() { return items; }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getRecibido() { return recibido; }
    public BigDecimal getVuelto() { return vuelto; }
    public Instant getFecha() { return fecha; }

    public Instant getFechaOrNow() {
        return Optional.ofNullable(fecha).orElse(Instant.now());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CreateSaleRequest)) return false;
        CreateSaleRequest that = (CreateSaleRequest) o;
        return Objects.equals(cajeroId, that.cajeroId) &&
                Objects.equals(items, that.items) &&
                Objects.equals(total, that.total) &&
                Objects.equals(recibido, that.recibido) &&
                Objects.equals(vuelto, that.vuelto) &&
                Objects.equals(fecha, that.fecha);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cajeroId, items, total, recibido, vuelto, fecha);
    }

    @Override
    public String toString() {
        return "CreateSaleRequest{" +
                "cajeroId=" + cajeroId +
                ", items=" + items +
                ", total=" + total +
                ", recibido=" + recibido +
                ", vuelto=" + vuelto +
                ", fecha=" + fecha +
                '}';
    }
}
