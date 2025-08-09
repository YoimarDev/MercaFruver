package com.miempresa.fruver.test;

import com.miempresa.fruver.infra.config.DataSourceFactory;
import com.miempresa.fruver.infra.db.DeviceConfigRepositoryJdbc;
import com.miempresa.fruver.domain.model.DeviceConfig.DeviceType;
import com.miempresa.fruver.domain.repository.DeviceConfigRepository;
import com.miempresa.fruver.infra.hardware.printer.PrinterService;

import javax.sql.DataSource;
import java.util.Optional;

/**
 * Main de prueba para impresora térmica.
 *
 * Asegúrate de tener en CONFIG_DISP un registro:
 *  tipo = 'IMPRESORA', puerto = 'NOMBRE_IMPRESORA'  (o 'COM4' si usa puerto serie)
 */
public class TestPrinter {
    public static void main(String[] args) {
        DataSource ds = DataSourceFactory.getDataSource();
        DeviceConfigRepository repo = new DeviceConfigRepositoryJdbc(ds);
        try {
            Optional<com.miempresa.fruver.domain.model.DeviceConfig> cfg = repo.findByType(DeviceType.IMPRESORA);
            String printerId = cfg.map(c -> c.getPuerto()).orElseThrow(() -> new RuntimeException("Config de impresora no encontrada en DB"));
            System.out.println("Usando identificador de impresora: " + printerId);

            PrinterService printer = new PrinterService();
            // init puede lanzar DataAccessException con info útil (lista de impresoras)
            printer.init(printerId);

            // Imprimir ticket de prueba
            String ticket = "Ticket de prueba\nLinea 1: Hola\nLinea 2: 123\nTotal: $0.00\n";
            printer.printReceipt(ticket);
            System.out.println("Impresión enviada.");

            // Abrir cajón
            printer.openCashDrawer();
            System.out.println("Comando abrir cajón enviado.");

            printer.close();
        } catch (Exception ex) {
            System.err.println("Error en prueba de impresora: " + ex.getMessage());
            ex.printStackTrace();
            // Si falla al init, listamos impresoras disponibles para debugging
            System.out.println("Impresoras disponibles:");
            for (String s : PrinterService.listAvailablePrinters()) {
                System.out.println(" - " + s);
            }
        }
    }
}
