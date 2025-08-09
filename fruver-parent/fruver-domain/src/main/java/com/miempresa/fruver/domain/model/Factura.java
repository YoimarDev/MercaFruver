package com.miempresa.fruver.domain.model;

import java.time.LocalDateTime;

/**
 * Representa una factura vinculada a una venta.
 */
public class Factura {
    private Integer facturaId;
    private Integer ventaId;
    private String folio;
    private boolean impresa;
    private LocalDateTime fechaImpresion;

    public Factura(Integer facturaId, Integer ventaId, String folio) {
        this.facturaId = facturaId;
        this.ventaId = ventaId;
        this.folio = folio;
        this.impresa = false;
    }

    public Integer getFacturaId() { return facturaId; }
    public Integer getVentaId() { return ventaId; }
    public String getFolio() { return folio; }
    public boolean isImpresa() { return impresa; }
    public LocalDateTime getFechaImpresion() { return fechaImpresion; }

    /** Marca la factura como impresa y registra la fecha. */
    public void markPrinted(LocalDateTime fecha) {
        this.impresa = true;
        this.fechaImpresion = fecha;
    }
}