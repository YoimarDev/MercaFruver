package com.miempresa.fruver.infra.hardware.scale;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.miempresa.fruver.domain.exceptions.DataAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * ScaleService event-driven y responsive.
 * - Listener en background que parsea tramas STX(0x02) ... ETX(0x03)
 * - readWeightKg() envía request (si está configurado) y espera la siguiente trama
 * - getLastWeightKg() devuelve la última lectura conocida inmediatamente
 */
public class ScaleService {
    private SerialPort port;
    private InputStream in;
    private OutputStream out;

    private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(16);
    private final StringBuilder buffer = new StringBuilder();

    private volatile Double lastKg = null;
    private SerialPortDataListener listener;

    // configuración
    private String requestCommand = "96814C"; // comando que solicita peso; si "" -> modo streaming
    private int readTimeoutMs = 1500;
    private boolean debug = false;
    private final int openAttempts = 3;

    public ScaleService() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { close(); } catch (Throwable ignored) {}
        }));
    }

    public void setRequestCommand(String cmd) { this.requestCommand = cmd == null ? "" : cmd; }
    public void setReadTimeoutMs(int ms) { this.readTimeoutMs = Math.max(200, ms); }
    public void setDebug(boolean d) { this.debug = d; }

    public synchronized void open(String portName, int baudRate) {
        if (port != null && port.isOpen() && portName != null && portName.equals(port.getSystemPortName())) {
            if (debug) System.out.println("ScaleService: puerto ya abierto " + portName);
            return;
        }
        close();

        port = SerialPort.getCommPort(portName);
        if (port == null) throw new DataAccessException("Puerto no encontrado: " + portName);

        // configuración básica
        port.setBaudRate(baudRate);
        port.setNumDataBits(8);
        port.setNumStopBits(SerialPort.ONE_STOP_BIT);
        port.setParity(SerialPort.NO_PARITY);
        port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);

        boolean opened = false;
        for (int i = 1; i <= openAttempts; i++) {
            if (port.isOpen()) {
                try { port.closePort(); } catch (Throwable ignored) {}
            }
            port.clearDTR(); port.clearRTS(); sleep(100);
            port.setDTR(); port.setRTS(); sleep(100);

            if (port.openPort(1500)) {
                opened = true;
                break;
            }
            sleep(200);
        }
        if (!opened) throw new DataAccessException("No se pudo abrir puerto tras " + openAttempts + " intentos: " + portName);

        // streams
        try {
            in = port.getInputStream();
            out = port.getOutputStream();
        } catch (Exception ex) {
            throw new DataAccessException("Error obteniendo streams del puerto: " + ex.getMessage(), ex);
        }

        // purge razonable al abrir (no en cada lectura)
        purgeOnOpen();

        // instalar listener (event-driven)
        installListener();

        if (debug) System.out.println("ScaleService: puerto abierto " + portName + "@" + baudRate);
    }

    private void installListener() {
        // remover antes si existe
        if (listener != null) {
            try { port.removeDataListener(); } catch (Throwable ignored) {}
            listener = null;
        }

        listener = new SerialPortDataListener() {
            @Override
            public int getListeningEvents() { return SerialPort.LISTENING_EVENT_DATA_AVAILABLE; }

            @Override
            public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) return;
                try {
                    int avail = port.bytesAvailable();
                    int toRead = Math.max(avail, 64);
                    byte[] buf = new byte[toRead];
                    int read = port.readBytes(buf, Math.min(buf.length, avail > 0 ? avail : buf.length));
                    if (read <= 0) return;
                    String s = new String(buf, 0, read, StandardCharsets.US_ASCII);
                    if (debug) System.out.println("ScaleService: raw incoming -> [" + s.replace("\r","\u00B6").replace("\n","\u00B7") + "]");
                    synchronized (buffer) {
                        buffer.append(s);
                        // buscar tramas STX..ETX
                        while (true) {
                            int stx = buffer.indexOf("\u0002");
                            if (stx < 0) break;
                            int etx = buffer.indexOf("\u0003", stx + 1);
                            if (etx < 0) break;
                            String payload = buffer.substring(stx + 1, etx);
                            buffer.delete(0, etx + 1);
                            handlePayload(payload);
                        }
                        // también intentar líneas terminadas en \n si no usa STX/ETX
                        int nl;
                        while ((nl = buffer.indexOf("\n")) >= 0) {
                            String line = buffer.substring(0, nl).trim();
                            buffer.delete(0, nl + 1);
                            if (!line.isEmpty()) handlePayload(line);
                        }
                    }
                } catch (Throwable t) {
                    if (debug) System.err.println("ScaleService listener error: " + t.getMessage());
                }
            }
        };
        port.addDataListener(listener);
    }

    private void handlePayload(String payload) {
        String cleaned = payload.replaceAll("[^0-9,\\.\\-]+", "").replace(',', '.').trim();
        if (cleaned.isEmpty()) return;
        if (debug) System.out.println("ScaleService: payload parsed -> '" + cleaned + "'");
        try {
            double kg = Double.parseDouble(cleaned);
            lastKg = kg;
            // offer without blocking
            queue.offer(cleaned);
        } catch (NumberFormatException nfe) {
            if (debug) System.err.println("ScaleService: parse error payload='" + cleaned + "'");
        }
    }

    /**
     * Método principal que usa UI: envía request (si está configurado) y espera la siguiente trama.
     * Si no hay requestCommand (streaming), devuelve el último valor conocido de inmediato.
     */
    public synchronized double readWeightKg() {
        if (port == null || !port.isOpen()) throw new DataAccessException("Puerto no abierto");

        try {
            // Si hay requestCommand -> pedir nueva lectura y esperar próxima trama
            if (requestCommand != null && !requestCommand.isBlank()) {
                // vaciar cola de mensajes previos (no bloquear)
                queue.clear();

                // enviar request
                try {
                    out.write(requestCommand.getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    if (debug) System.out.println("ScaleService: request enviado -> " + requestCommand);
                } catch (IOException ioe) {
                    if (debug) System.err.println("ScaleService: fallo al enviar request: " + ioe.getMessage());
                }

                // esperar la próxima trama parseada por el listener
                String msg = queue.poll(readTimeoutMs, TimeUnit.MILLISECONDS);
                if (msg != null) {
                    try {
                        double kg = Double.parseDouble(msg);
                        lastKg = kg;
                        return kg;
                    } catch (NumberFormatException ex) {
                        // si no parsea, fallback a lastKg si existe
                        if (lastKg != null) return lastKg;
                        throw new DataAccessException("Respuesta inválida de báscula: " + msg);
                    }
                } else {
                    // timeout: si tenemos lastKg retornarlo como fallback
                    if (lastKg != null) return lastKg;
                    throw new DataAccessException("Timeout esperando respuesta de báscula (" + readTimeoutMs + "ms)");
                }
            } else {
                // modo streaming: devolver el último valor conocido inmediatamente
                if (lastKg != null) return lastKg;
                // si no hay ninguno, intentar esperar muy poco por la cola
                String msg = queue.poll(200, TimeUnit.MILLISECONDS);
                if (msg != null) {
                    double kg = Double.parseDouble(msg);
                    lastKg = kg;
                    return kg;
                }
                throw new DataAccessException("No hay lecturas disponibles aún (modo streaming)");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new DataAccessException("Lectura interrumpida", ie);
        } catch (DataAccessException dae) {
            throw dae;
        } catch (Exception ex) {
            throw new DataAccessException("Error leyendo peso: " + ex.getMessage(), ex);
        }
    }

    public synchronized int readWeightGrams() {
        double kg = readWeightKg();
        return (int) Math.round(kg * 1000.0);
    }

    public synchronized double getLastWeightKg() {
        return lastKg == null ? Double.NaN : lastKg;
    }

    public synchronized void close() {
        try {
            if (port != null && listener != null) {
                try { port.removeDataListener(); } catch (Throwable ignored) {}
                listener = null;
            }
        } catch (Throwable ignored) {}
        try { if (in != null) { try { in.close(); } catch (Throwable ignored) {} in = null; } } catch (Throwable ignored) {}
        try { if (out != null) { try { out.close(); } catch (Throwable ignored) {} out = null; } } catch (Throwable ignored) {}
        try {
            if (port != null) {
                try { if (port.isOpen()) { try { port.flushIOBuffers(); } catch (Throwable ignored) {} port.closePort(); } } catch (Throwable ignored) {}
                port = null;
            }
        } catch (Throwable ignored) {}
        if (debug) System.out.println("ScaleService: closed");
    }

    private void purgeOnOpen() {
        // intento simple: flushIOBuffers + small drain
        try {
            port.flushIOBuffers();
        } catch (Throwable ignored) {}
        // small drain from InputStream if available
        if (in != null) {
            try {
                byte[] tmp = new byte[256];
                long deadline = System.currentTimeMillis() + 200;
                while (System.currentTimeMillis() < deadline) {
                    int avail = 0;
                    try { avail = in.available(); } catch (IOException ignored) {}
                    if (avail <= 0) { sleep(20); continue; }
                    int r = in.read(tmp, 0, Math.min(tmp.length, avail));
                    if (r <= 0) break;
                }
            } catch (Throwable ignored) {}
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
