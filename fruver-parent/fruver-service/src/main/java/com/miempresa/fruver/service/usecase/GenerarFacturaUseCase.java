package com.miempresa.fruver.service.usecase;

import com.miempresa.fruver.domain.model.*;
import com.miempresa.fruver.domain.repository.FacturaRepository;
import com.miempresa.fruver.service.port.InputPort;
import com.miempresa.fruver.infra.hardware.printer.PrinterService;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Caso de uso para generar e imprimir factura.
 */
public class GenerarFacturaUseCase implements InputPort<Venta, Factura> {
    private final FacturaRepository facturaRepo;
    private final PrinterService printer;

    public GenerarFacturaUseCase(FacturaRepository fr, PrinterService ps) {
        this.facturaRepo = fr;
        this.printer = ps;
    }

    @Override
    public Factura execute(Venta venta) {
        // Crear folio secuencial
        String folio = "F" + System.currentTimeMillis();
        Factura factura = new Factura(null, venta.getVentaId(), folio);
        factura.markPrinted(LocalDateTime.now());
        factura = facturaRepo.save(factura);

        // Preparar contenido de impresión
        List<String> lines = new ArrayList<>();
        lines.add("Factura: " + factura.getFolio());
        lines.add("Fecha: " + factura.getFechaImpresion());
        lines.add("Total: " + venta.getTotal());
        lines.add("Recibido: " + venta.getRecibido());
        lines.add("Vuelto: " + venta.getVuelto());

        // Imprimir
        printer.init("POS_Printer");
        printer.printReceipt(String.join("\n", lines));

        return factura;
    }
}