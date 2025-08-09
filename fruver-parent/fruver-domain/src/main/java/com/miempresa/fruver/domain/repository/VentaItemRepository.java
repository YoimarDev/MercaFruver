package com.miempresa.fruver.domain.repository;

import com.miempresa.fruver.domain.model.VentaItem;

import java.util.List;

public interface VentaItemRepository {
    VentaItem save(VentaItem item);
    List<VentaItem> findByVentaId(Integer ventaId);
}
