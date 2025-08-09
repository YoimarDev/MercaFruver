package com.miempresa.fruver.infra.hardware.printer;

import com.miempresa.fruver.domain.exceptions.DataAccessException;
import com.fazecast.jSerialComm.SerialPort;

import javax.print.*;
import javax.print.attribute.*;
import javax.print.attribute.standard.*;
import java.io.*;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * Servicio para impresora térmica ESC/POS.
 * - Usa Java PrintService (preferred) si la impresora está instalada como impresora en Windows.
 * - Si recibe un puerto 'COMx' usa jSerialComm para escribir bytes directamente.
 */
public class PrinterService {

    private enum Mode { PRINTSERVICE, SERIAL }

    private Mode mode;
    private String printerNameOrPort;
    private PrintService printService;
    private SerialPort serialPort;
    private Charset encoding = Charset.forName("UTF-8"); // fallback; puede cambiarse si requiere CP437/1252

    public PrinterService() {}

    /**
     * Inicializa la impresora. printerIdentifier puede ser:
     *  - nombre de impresora (conteniendo parte del nombre)
     *  - "COM4", "COM3" para puerto serie
     */
    public void init(String printerIdentifier) {
        if (printerIdentifier == null || printerIdentifier.isBlank()) {
            throw new DataAccessException("Identificador de impresora vacío");
        }
        this.printerNameOrPort = printerIdentifier.trim();

        if (printerNameOrPort.toUpperCase().startsWith("COM")) {
            initSerial(printerNameOrPort);
            this.mode = Mode.SERIAL;
            return;
        }

        // Try PrintService by name (contains / equals)
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        if (services == null || services.length == 0) {
            throw new DataAccessException("No hay servicios de impresión disponibles en el sistema");
        }
        for (PrintService ps : services) {
            if (ps.getName().equalsIgnoreCase(printerNameOrPort)
                    || ps.getName().toLowerCase().contains(printerNameOrPort.toLowerCase())) {
                this.printService = ps;
                this.mode = Mode.PRINTSERVICE;
                return;
            }
        }

        // Not found -> helpful message listing available printers
        StringBuilder sb = new StringBuilder();
        sb.append("Impresora '").append(printerIdentifier).append("' no encontrada. Impresoras disponibles:\n");
        Arrays.stream(services).forEach(s -> sb.append("  - ").append(s.getName()).append("\n"));
        throw new DataAccessException(sb.toString());
    }

    private void initSerial(String portName) {
        serialPort = SerialPort.getCommPort(portName);
        if (serialPort == null) throw new DataAccessException("Puerto serial no encontrado: " + portName);
        serialPort.setBaudRate(9600);
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(SerialPort.ONE_STOP_BIT);
        serialPort.setParity(SerialPort.NO_PARITY);
        // intentar abrir
        if (!serialPort.openPort(2000)) {
            throw new DataAccessException("No se pudo abrir puerto serial: " + portName);
        }
        // Pequeña espera
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
    }

    /** Devuelve listado de nombres de impresoras disponibles (útil para UI). */
    public static String[] listAvailablePrinters() {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        return Arrays.stream(services).map(PrintService::getName).toArray(String[]::new);
    }

    /** Envía un recibo (texto) a la impresora. */
    public void printReceipt(String receiptText) {
        byte[] body = buildEscPosReceipt(receiptText);
        sendBytes(body);
    }

    /** Construye una estructura ESC/POS sencilla: init, texto + newline, cut */
    private byte[] buildEscPosReceipt(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            // Init
            out.write(new byte[]{0x1B, 0x40}); // ESC @
            // Center and title example (you can extend)
            out.write(new byte[]{0x1B, 0x61, 0x01}); // ESC a 1 (center)
            out.write("Merca Fruver C.Y.E.\n".getBytes(encoding));
            out.write(new byte[]{0x1B, 0x61, 0x00}); // left
            out.write("------------------------------\n".getBytes(encoding));
            out.write(text.getBytes(encoding));
            out.write("\n\n".getBytes(encoding));
            // Cut paper (full cut)
            out.write(new byte[]{0x1D, 0x56, 0x00}); // GS V 0
        } catch (IOException ignored) {}
        return out.toByteArray();
    }

    /** Abre cajón de dinero enviando comando estándar ESC p 0 25 250 */
    public void openCashDrawer() {
        byte[] cmd = new byte[]{0x1B, 0x70, 0x00, 0x19, (byte)0xFA};
        sendBytes(cmd);
    }

    /** Envío genérico de bytes según el modo configurado. */
    private void sendBytes(byte[] data) {
        if (mode == null) throw new DataAccessException("Impresora no inicializada");
        if (mode == Mode.PRINTSERVICE) {
            sendToPrintService(data);
        } else {
            sendToSerial(data);
        }
    }

    private void sendToPrintService(byte[] data) {
        if (printService == null)
            throw new DataAccessException("PrintService no inicializado");

        DocPrintJob job = printService.createPrintJob();
        DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
        Doc doc = new SimpleDoc(data, flavor, null);
        PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
        attrs.add(new JobName("Ticket", null));
        try {
            job.print(doc, attrs);
        } catch (PrintException ex) {
            throw new DataAccessException("Error imprimiendo: " + ex.getMessage(), ex);
        }
    }

    private void sendToSerial(byte[] data) {
        if (serialPort == null || !serialPort.isOpen()) {
            throw new DataAccessException("Puerto serie no abierto");
        }
        try (OutputStream out = serialPort.getOutputStream()) {
            out.write(data);
            out.flush();
            // esperar un poco para que buffer se vacíe
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        } catch (IOException ex) {
            throw new DataAccessException("Error enviando a puerto serial: " + ex.getMessage(), ex);
        }
    }

    /** Cierra recursos (si se usó puerto serial). */
    public void close() {
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.flushIOBuffers();
            serialPort.closePort();
            serialPort = null;
        }
    }
}
