package com.miempresa.fruver.service.usecase;

import com.miempresa.fruver.service.port.ScalePort;
import com.miempresa.fruver.domain.exceptions.DomainException;
import com.miempresa.fruver.domain.exceptions.DataAccessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * UseCase para calcular precio de un producto por peso.
 * No depende de la implementación concreta de la báscula (usa ScalePort).
 */
public class CalcularPrecioUseCase {

    private final ScalePort scalePort;

    /**
     * Inyecta la abstracción de báscula.
     */
    public CalcularPrecioUseCase(ScalePort scalePort) {
        this.scalePort = scalePort;
    }

    /**
     * Lee el peso de la báscula y calcula subtotal = precioUnitario * pesoKg
     *
     * @param precioUnitario precio por kg (ej: 4.50)
     * @return subtotal redondeado a 2 decimales
     */
    public BigDecimal calcularSubtotalPorPeso(BigDecimal precioUnitario) {
        if (precioUnitario == null) throw new DomainException("Precio inválido");

        try {
            int gramos = scalePort.readWeightGrams();
            BigDecimal kg = new BigDecimal(gramos).divide(new BigDecimal(1000), 6, RoundingMode.HALF_UP);
            BigDecimal subtotal = precioUnitario.multiply(kg).setScale(2, RoundingMode.HALF_UP);
            return subtotal;
        } catch (DataAccessException dae) {
            // Re-lanzar como DomainException para que la capa superior lo maneje
            throw new DomainException("No se pudo leer peso: " + dae.getMessage(), dae);
        } catch (Exception ex) {
            throw new DomainException("Error calculando precio por peso: " + ex.getMessage(), ex);
        }
    }
}
