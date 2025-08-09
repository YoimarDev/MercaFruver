package com.miempresa.fruver.service.usecase;

import com.miempresa.fruver.domain.exceptions.InvalidOperationException;
import com.miempresa.fruver.domain.model.Producto;
import com.miempresa.fruver.domain.repository.ProductoRepository;
import com.miempresa.fruver.domain.exceptions.EntityNotFoundException;
import com.miempresa.fruver.service.port.InputPort;
import com.miempresa.fruver.infra.hardware.scale.ScaleService;
import java.math.BigDecimal;

/**
 * Caso de uso para cálculo de precio por peso.
 */
public class CalcularPrecioUseCase implements InputPort<String, BigDecimal> {
    private final ProductoRepository productoRepo;
    private final ScaleService scaleService;

    public CalcularPrecioUseCase(ProductoRepository pr, ScaleService ss) {
        this.productoRepo = pr;
        this.scaleService = ss;
    }

    @Override
    public BigDecimal execute(String codigo) {
        Producto p = productoRepo.findByCodigo(codigo)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado: " + codigo));
        if (p.getTipo() != Producto.TipoProducto.PESO) {
            throw new InvalidOperationException("Producto no es de tipo PESO: " + codigo);
        }
        double weight = scaleService.readWeightKg();

        return p.getPrecioUnitario().multiply(BigDecimal.valueOf(weight));
    }
}