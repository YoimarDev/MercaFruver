package com.miempresa.fruver.domain.repository;

import com.miempresa.fruver.domain.model.Factura;

import java.util.Optional;

public interface FacturaRepository {
    Factura save(Factura f);
    Optional<Factura> findByVentaId(Integer ventaId);
}
