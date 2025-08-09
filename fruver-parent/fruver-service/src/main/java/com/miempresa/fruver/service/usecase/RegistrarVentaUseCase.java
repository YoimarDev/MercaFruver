package com.miempresa.fruver.service.usecase;

import com.miempresa.fruver.domain.model.*;
import com.miempresa.fruver.domain.repository.*;
import com.miempresa.fruver.domain.exceptions.EntityNotFoundException;
import com.miempresa.fruver.domain.exceptions.InvalidOperationException;
import com.miempresa.fruver.service.port.InputPort;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Caso de uso para registrar una venta completa.
 */
public class RegistrarVentaUseCase implements InputPort<List<VentaItem>, Venta> {
    private final ProductoRepository productoRepo;
    private final VentaRepository ventaRepo;
    private final VentaItemRepository itemRepo;
    private final FacturaRepository facturaRepo;

    public RegistrarVentaUseCase(ProductoRepository pr, VentaRepository vr, VentaItemRepository ir, FacturaRepository fr) {
        this.productoRepo = pr;
        this.ventaRepo = vr;
        this.itemRepo = ir;
        this.facturaRepo = fr;
    }

    @Override
    public Venta execute(List<VentaItem> items) {
        if (items == null || items.isEmpty()) {
            throw new InvalidOperationException("La venta no puede estar vacía");
        }
        // 1. Crear nueva venta
        Venta venta = new Venta(null, LocalDateTime.now(), items.get(0).getVentaId());

        // 2. Procesar cada item: verificar producto, ajustar stock, calcular subtotal
        for (VentaItem item : items) {
            Producto p = productoRepo.findByCodigo(item.getProductoId().toString())
                    .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado: " + item.getProductoId()));
            p.adjustStock(item.getCantidad().negate());
            productoRepo.updateStock(p.getProductoId(), p.getStockActual());
            venta.addItem(item.getSubtotal());
            // persistir line item
            itemRepo.save(item);
        }

        // 3. Persistir venta
        venta = ventaRepo.save(venta);

        return venta;
    }
}
