package com.miempresa.fruver.infra.hardware.scale;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.miempresa.fruver.domain.exceptions.DataAccessException;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ScaleService — versión con reintentos/backoff y cierre forzado en nueva instancia
 * para mitigar casos en los que el driver/OS mantiene handles nativos entre open/close.
 *
 * Conserva firmas públicas originales y comportamiento observable.
 */
public class ScaleService {
    private static final ConcurrentHashMap<String, ReentrantLock> PORT_LOCKS = new ConcurrentHashMap<>();

    private SerialPort port;
    private InputStream in;
    private OutputStream out;

    private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(16);
    private final StringBuilder buffer = new StringBuilder();

    private volatile Double lastKg = null;
    private SerialPortDataListener listener;

    // configuración
    private String requestCommand = "96814C";
    private int readTimeoutMs = 1500;
    private boolean debug = false;
    // aumentamos intentos por defecto
    private final int openAttempts = 6;

    // lock que esta instancia adquirió (si lo hizo)
    private ReentrantLock heldLock;
    private String heldPortName;

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
        // intentar cerrar cualquier estado anterior
        close();

        // esperar hasta que el driver reporte que el puerto quedó cerrado (pequeño timeout)
        long waitStart = System.currentTimeMillis();
        long waitTimeout = 2200; // ms total máximo
        while (System.currentTimeMillis() - waitStart < waitTimeout) {
            try {
                SerialPort probe = SerialPort.getCommPort(portName);
                boolean stillOpen = false;
                try { stillOpen = probe.isOpen(); } catch (Throwable ignored) {}
                if (!stillOpen) break;
            } catch (Throwable ignored) {}
            sleep(120);
        }

        ReentrantLock lock = PORT_LOCKS.computeIfAbsent(portName, p -> new ReentrantLock());
        boolean locked = false;
        try {
            locked = lock.tryLock(2000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        if (!locked) {
            throw new DataAccessException("Puerto ocupado por otra operación: " + portName);
        }
        this.heldLock = lock;
        this.heldPortName = portName;

        try {
            port = SerialPort.getCommPort(portName);
            if (port == null) throw new DataAccessException("Puerto no encontrado: " + portName);

            port.setBaudRate(baudRate);
            port.setNumDataBits(8);
            port.setNumStopBits(SerialPort.ONE_STOP_BIT);
            port.setParity(SerialPort.NO_PARITY);
            port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);

            boolean opened = false;
            // backoff exponencial sencillo
            for (int i = 1; i <= openAttempts; i++) {
                // attempt-specific pre-open reset
                try { port.clearDTR(); port.clearRTS(); } catch (Throwable ignored) {}
                sleep(60);
                try { port.setDTR(); port.setRTS(); } catch (Throwable ignored) {}
                sleep(60);

                port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);

                // intentamos abrir con timeout creciente
                int openTimeout = 1500 + (i - 1) * 500; // 1500, 2000, 2500...
                if (debug) System.out.println("ScaleService: intentando open() intento=" + i + " timeout=" + openTimeout);
                try {
                    if (port.openPort(openTimeout)) {
                        opened = true;
                        if (debug) System.out.println("ScaleService: openPort() succeeded on attempt " + i);
                        break;
                    } else {
                        if (debug) System.out.println("ScaleService: openPort() returned false on attempt " + i);
                    }
                } catch (Throwable t) {
                    if (debug) System.err.println("ScaleService: openPort() exception attempt " + i + " -> " + t.getMessage());
                }

                // si no abrió, hacemos safe purge/toggle y esperamos backoff
                try {
                    safePurge();
                } catch (Throwable ignored) {}
                long backoff = 120L * i; // leve aumento
                sleep(backoff + 80);
            }

            // Si no se abrió con los intentos normales, intentar cierre forzado vía una nueva instancia SerialPort
            if (!opened) {
                if (debug) System.out.println("ScaleService: no abierto tras intentos regulares, intentando cierre forzado externo...");
                try {
                    SerialPort alt = SerialPort.getCommPort(portName);
                    try {
                        // intentar flush/close en nueva instancia
                        try { alt.flushIOBuffers(); } catch (Throwable ignored) {}
                        try { alt.closePort(); } catch (Throwable ignored) {}
                        // intentar abrir y cerrar rápidamente para "resetear" el driver
                        try {
                            if (alt.openPort(800)) {
                                sleep(80);
                                try { alt.closePort(); } catch (Throwable ignored) {}
                            }
                        } catch (Throwable ignored) {}
                    } finally {
                        // asegurar que alt sea descartado
                        try { alt.closePort(); } catch (Throwable ignored) {}
                    }
                    // darle tiempo al OS/driver
                    sleep(300);
                } catch (Throwable ignored) {}
                // reintentar abrir unas veces más con mayor espera
                for (int j = 1; j <= 3 && !opened; j++) {
                    try {
                        if (debug) System.out.println("ScaleService: reintento forzado " + j + " para openPort()");
                        if (port.openPort(2000)) {
                            opened = true;
                            break;
                        }
                    } catch (Throwable t) {
                        if (debug) System.err.println("ScaleService: reintento exception -> " + t.getMessage());
                    }
                    sleep(250 + j * 200);
                }
            }

            if (!opened) throw new DataAccessException("No se pudo abrir puerto tras " + openAttempts + " intentos: " + portName);

            // obtener streams
            try {
                in = port.getInputStream();
                out = port.getOutputStream();
            } catch (Exception ex) {
                throw new DataAccessException("Error obteniendo streams del puerto: " + ex.getMessage(), ex);
            }

            // purga y listener
            safePurge();
            installListener();

            if (debug) System.out.println("ScaleService: puerto abierto " + portName + "@" + baudRate);
        } catch (DataAccessException dae) {
            try { close(); } catch (Throwable ignored) {}
            throw dae;
        } catch (Throwable t) {
            try { close(); } catch (Throwable ignored) {}
            throw new DataAccessException("Error abriendo puerto: " + t.getMessage(), t);
        }
    }

    private void installListener() {
        if (listener != null) {
            try { if (port != null) port.removeDataListener(); } catch (Throwable ignored) {}
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
                        while (true) {
                            int stx = buffer.indexOf("\u0002");
                            if (stx < 0) break;
                            int etx = buffer.indexOf("\u0003", stx + 1);
                            if (etx < 0) break;
                            String payload = buffer.substring(stx + 1, etx);
                            buffer.delete(0, etx + 1);
                            handlePayload(payload);
                        }
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
        try {
            port.addDataListener(listener);
        } catch (Throwable t) {
            if (debug) System.err.println("ScaleService: fallo al agregar listener: " + t.getMessage());
        }
    }

    private void handlePayload(String payload) {
        String cleaned = payload.replaceAll("[^0-9,\\.\\-]+", "").replace(',', '.').trim();
        if (cleaned.isEmpty()) return;
        if (debug) System.out.println("ScaleService: payload parsed -> '" + cleaned + "'");
        try {
            double kg = Double.parseDouble(cleaned);
            lastKg = kg;
            queue.offer(cleaned);
        } catch (NumberFormatException nfe) {
            if (debug) System.err.println("ScaleService: parse error payload='" + cleaned + "'");
        }
    }

    public synchronized double readWeightKg() {
        if (port == null || !port.isOpen()) throw new DataAccessException("Puerto no abierto");

        try {
            if (requestCommand != null && !requestCommand.isBlank()) {
                queue.clear();
                try {
                    out.write(requestCommand.getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    if (debug) System.out.println("ScaleService: request enviado -> " + requestCommand);
                } catch (Exception ioe) {
                    if (debug) System.err.println("ScaleService: fallo al enviar request: " + ioe.getMessage());
                }

                String msg = queue.poll(readTimeoutMs, TimeUnit.MILLISECONDS);
                if (msg != null) {
                    try {
                        double kg = Double.parseDouble(msg);
                        lastKg = kg;
                        return kg;
                    } catch (NumberFormatException ex) {
                        if (lastKg != null) return lastKg;
                        throw new DataAccessException("Respuesta inválida de báscula: " + msg);
                    }
                } else {
                    if (lastKg != null) return lastKg;
                    throw new DataAccessException("Timeout esperando respuesta de báscula (" + readTimeoutMs + "ms)");
                }
            } else {
                if (lastKg != null) return lastKg;
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
            // remover listener primero
            if (port != null && listener != null) {
                try { port.removeDataListener(); } catch (Throwable ignored) {}
                // dar margen para que driver procese la remoción
                sleep(80);
                listener = null;
            }
        } catch (Throwable ignored) {}

        // cerrar streams
        try { if (in != null) { try { in.close(); } catch (Throwable ignored) {} in = null; } } catch (Throwable ignored) {}
        try { if (out != null) { try { out.close(); } catch (Throwable ignored) {} out = null; } } catch (Throwable ignored) {}

        // intentar cerrar el puerto de forma robusta y esperar a que realmente quede cerrado
        try {
            if (port != null) {
                try {
                    safePurge();
                } catch (Throwable ignored) {}
                try {
                    if (port.isOpen()) {
                        try {
                            port.flushIOBuffers();
                        } catch (Throwable ignored) {}
                        boolean closed = false;
                        try {
                            closed = port.closePort();
                        } catch (Throwable ignored) {}
                        // esperar un poco más (hasta ~2s) para que libere handle
                        int tries = 0;
                        while (port.isOpen() && tries < 20) { // 20 * 100ms = 2s máximo
                            sleep(100);
                            tries++;
                        }
                        if (debug) System.out.println("ScaleService: close() - closed flag=" + closed + " isOpen=" + port.isOpen());
                    }
                } catch (Throwable t) {
                    if (debug) System.err.println("ScaleService: error cerrando puerto: " + t.getMessage());
                } finally {
                    try { port = null; } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // Intento extra: crear nueva instancia SerialPort y forzar close (ayuda cuando handle quedó fuera de esta instancia)
        if (heldPortName != null) {
            try {
                SerialPort alt = SerialPort.getCommPort(heldPortName);
                try {
                    try { alt.flushIOBuffers(); } catch (Throwable ignored) {}
                    try { alt.closePort(); } catch (Throwable ignored) {}
                } finally {
                    try { alt.closePort(); } catch (Throwable ignored) {}
                }
                // pequeño margen para driver
                sleep(150);
                if (debug) System.out.println("ScaleService: attempted external close on " + heldPortName);
            } catch (Throwable t) {
                if (debug) System.err.println("ScaleService: external close attempt failed: " + t.getMessage());
            }
        }

        // segundo intento más agresivo para asegurar liberación de handles
        try {
            forceReleasePort(heldPortName);
        } catch (Throwable ignored) {}

        // liberar lock siempre que podamos (capturando IllegalMonitorState si se da)
        try {
            if (heldLock != null) {
                try {
                    heldLock.unlock();
                } catch (IllegalMonitorStateException ims) {
                    if (debug) System.err.println("ScaleService: unlock -> IllegalMonitorStateException (ignored).");
                } catch (Throwable t) {
                    if (debug) System.err.println("ScaleService: unlock -> " + t.getMessage());
                }
            }
        } catch (Throwable ignored) {}

        heldLock = null;
        heldPortName = null;

        if (debug) System.out.println("ScaleService: closed (heldPort=" + heldPortName + ")");
    }

    private void safePurge() {
        if (port == null) return;
        try {
            // Intentar invocar purgePort(int) y las constantes mediante reflection
            Method purgeMethod = port.getClass().getMethod("purgePort", int.class);
            int flags = 0;
            try {
                Field f1 = port.getClass().getField("PURGE_RXCLEAR");
                Field f2 = port.getClass().getField("PURGE_TXCLEAR");
                Object v1 = f1.get(null);
                Object v2 = f2.get(null);
                if (v1 instanceof Number) flags |= ((Number) v1).intValue();
                if (v2 instanceof Number) flags |= ((Number) v2).intValue();
            } catch (Throwable ignored) {
                // si no existen las constantes, dejar flags=0
            }
            try {
                purgeMethod.invoke(port, flags);
                if (debug) System.out.println("ScaleService: safePurge -> purgePort invoked (flags=" + flags + ")");
                return;
            } catch (Throwable t) {
                // ignore and fallback
            }
        } catch (Throwable ignored) {
            // método no disponible -> fallback
        }

        // fallback conocido: flushIOBuffers
        try {
            port.flushIOBuffers();
            if (debug) System.out.println("ScaleService: safePurge -> flushIOBuffers used");
        } catch (Throwable ignored) {}

        // intento extra: forzar clear/invertir líneas de control para reset rápido del adaptador
        try {
            port.clearDTR();
            port.clearRTS();
            sleep(40);
            port.setDTR();
            port.setRTS();
            sleep(40);
            if (debug) System.out.println("ScaleService: safePurge -> toggled DTR/RTS");
        } catch (Throwable ignored) {}
    }

    /**
     * Intenta forzar la liberación del puerto a nivel driver creando
     * instancias alternativas y abriéndolas/cerrándolas varias veces.
     * Esto ayuda cuando el OS mantiene handles nativos entre close() y
     * un nuevo open().
     */
    private void forceReleasePort(String portName) {
        if (portName == null || portName.isBlank()) return;
        for (int k = 0; k < 4; k++) {
            try {
                SerialPort alt = SerialPort.getCommPort(portName);
                try {
                    // intentos de purge/close rápidos para forzar liberación
                    try { alt.flushIOBuffers(); } catch (Throwable ignored) {}
                    try { alt.clearDTR(); alt.clearRTS(); } catch (Throwable ignored) {}
                    try { alt.closePort(); } catch (Throwable ignored) {}
                    // abrir y cerrar rápidamente
                    try {
                        if (alt.openPort(350)) {
                            sleep(80);
                            try { alt.clearDTR(); alt.clearRTS(); } catch (Throwable ignored) {}
                            try { alt.flushIOBuffers(); } catch (Throwable ignored) {}
                            try { alt.closePort(); } catch (Throwable ignored) {}
                        }
                    } catch (Throwable ignored) {}
                } finally {
                    try { alt.closePort(); } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                if (debug) System.err.println("ScaleService: forceReleasePort iteration " + k + " failed: " + t.getMessage());
            }
            // pequeño margen entre intentos
            sleep(120 + k * 60);
        }
        // margen final para que el driver procese la liberación
        sleep(180);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
