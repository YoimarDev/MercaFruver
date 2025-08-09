package com.miempresa.fruver.domain.repository;

import com.miempresa.fruver.domain.model.Venta;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VentaRepository {
    Venta save(Venta v);
    Optional<Venta> findById(Integer id);
    List<Venta> findByDateRange(LocalDate from, LocalDate to);
}
