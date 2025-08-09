package com.miempresa.fruver.service.usecase;

import com.miempresa.fruver.domain.repository.VentaRepository;
import com.miempresa.fruver.service.port.InputPort;
import java.time.LocalDate;
import java.util.*;


/**
 * Caso de uso para obtener estadísticas de ventas.
 */
public class ObtenerEstadisticasUseCase implements InputPort<LocalDate[], Map<String, Object>> {
    private final VentaRepository ventaRepo;
    public ObtenerEstadisticasUseCase(VentaRepository vr) { this.ventaRepo = vr; }

    @Override
    public Map<String, Object> execute(LocalDate[] range) {
        LocalDate from = range[0], to = range[1];
        List<?> ventas = ventaRepo.findByDateRange(from, to);
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalVentas", ventas.size());
        // otras métricas...
        return stats;
    }
}
