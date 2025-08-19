package com.miempresa.fruver.service.usecase;

import com.miempresa.fruver.domain.model.Factura;
import com.miempresa.fruver.domain.model.Venta;
import com.miempresa.fruver.domain.model.DeviceConfig.DeviceType;
import com.miempresa.fruver.domain.repository.FacturaRepository;
import com.miempresa.fruver.domain.repository.DeviceConfigRepository;
import com.miempresa.fruver.service.port.InputPort;
import com.miempresa.fruver.service.port.PrinterPort;
import com.miempresa.fruver.domain.exceptions.DomainException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Caso de uso para generar e imprimir factura. */
public class GenerarFacturaUseCase implements InputPort<Venta, Factura> {

    private final FacturaRepository facturaRepo;
    private final DeviceConfigRepository deviceCfgRepo; // para leer IMPRESORA de CONFIG_DISP
    private final PrinterPort printer;

    public GenerarFacturaUseCase(FacturaRepository facturaRepo,
                                 DeviceConfigRepository deviceCfgRepo,
                                 PrinterPort printer) {
        this.facturaRepo = facturaRepo;
        this.deviceCfgRepo = deviceCfgRepo;
        this.printer = printer;
    }

    @Override
    public Factura execute(Venta venta) {
        if (venta == null || venta.getVentaId() == null) {
            throw new DomainException("Venta inválida para facturar");
        }

        // 1) Generar y persistir factura (folio secuencial simple por ahora)
        String folio = "F" + System.currentTimeMillis();
        Factura factura = new Factura(null, venta.getVentaId(), folio);
        factura.markPrinted(LocalDateTime.now());
        factura = facturaRepo.save(factura);

        // 2) Preparar el texto del ticket
        List<String> lines = new ArrayList<>();
        lines.add("Factura: " + factura.getFolio());
        lines.add("Fecha: " + factura.getFechaImpresion());
        lines.add("Total: " + venta.getTotal());
        lines.add("Recibido: " + venta.getRecibido());
        lines.add("Vuelto: " + venta.getVuelto());
        String ticket = String.join("\n", lines);

        // 3) Resolver impresora desde CONFIG_DISP
        String printerId = deviceCfgRepo.findByType(DeviceType.IMPRESORA)
                .map(cfg -> cfg.getPuerto())
                .orElseThrow(() -> new DomainException("No hay configuración de IMPRESORA en CONFIG_DISP"));

        // 4) Imprimir vía port (adaptador en infra)
        try {
            printer.init(printerId);
            printer.printReceipt(ticket);
            // Si quieres abrir cajón automáticamente, descomenta:
            // printer.openCashDrawer();
        } catch (Exception ex) {
            throw new DomainException("Fallo al imprimir factura: " + ex.getMessage(), ex);
        } finally {
            try { printer.close(); } catch (Throwable ignored) {}
        }

        return factura;
    }
}
