package com.miempresa.fruver.test;

import com.miempresa.fruver.infra.config.DataSourceFactory;
import com.miempresa.fruver.infra.db.DeviceConfigRepositoryJdbc;
import com.miempresa.fruver.domain.model.DeviceConfig;
import com.miempresa.fruver.domain.model.DeviceConfig.DeviceType;
import com.miempresa.fruver.infra.hardware.printer.PrinterService;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.Optional;

public class TestPrinter {
    public static void main(String[] args) {
        DataSource ds = null;
        DeviceConfigRepositoryJdbc repo = null;
        PrinterService printer = null;
        try {
            ds = DataSourceFactory.getDataSource();
            repo = new DeviceConfigRepositoryJdbc(ds);

            // Usamos reflexión defensiva para soportar findByType que devuelva Optional o el objeto directamente.
            String printerId = null;
            try {
                Method m = repo.getClass().getMethod("findByType", DeviceType.class);
                Object res = m.invoke(repo, DeviceType.IMPRESORA);
                if (res == null) {
                    throw new RuntimeException("findByType retornó null");
                }
                if (res instanceof Optional) {
                    Optional<?> opt = (Optional<?>) res;
                    if (opt.isPresent()) {
                        Object cfg = opt.get();
                        if (cfg instanceof DeviceConfig) {
                            printerId = ((DeviceConfig) cfg).getPuerto();
                        }
                    }
                } else if (res instanceof DeviceConfig) {
                    printerId = ((DeviceConfig) res).getPuerto();
                } else {
                    // intentar castear por reflexión (campo/prop getter)
                    Object cfg = res;
                    try {
                        Method g = cfg.getClass().getMethod("getPuerto");
                        Object p = g.invoke(cfg);
                        if (p != null) printerId = p.toString();
                    } catch (NoSuchMethodException ignored) {}
                }
            } catch (NoSuchMethodException ns) {
                throw new RuntimeException("Repositorio no tiene método findByType(DeviceType).", ns);
            }

            if (printerId == null || printerId.isBlank()) {
                throw new RuntimeException("Config de impresora no encontrada en DB");
            }
            System.out.println("Usando identificador de impresora: " + printerId);

            printer = new PrinterService();
            printer.init(printerId);

            String ticket = "Ticket de prueba\nLinea 1: Hola\nLinea 2: 123\nTotal: $0.00\n";
            printer.printReceipt(ticket);
            System.out.println("Impresión enviada.");

            try {
                printer.openCashDrawer();
                System.out.println("Comando abrir cajón enviado.");
            } catch (Throwable t) {
                System.out.println("Comando abrir cajón no soportado o falló: " + t.getMessage());
            }

        } catch (Throwable ex) {
            System.err.println("Error en prueba de impresora: " + ex.getMessage());
            ex.printStackTrace();
            try {
                for (String s : PrinterService.listAvailablePrinters()) {
                    System.out.println(" - " + s);
                }
            } catch (Throwable t) {
                // ignore
            }
        } finally {
            try { if (printer != null) printer.close(); } catch (Exception ignored) {}
            System.out.println("TestPrinter finalizado.");
        }
    }
}
