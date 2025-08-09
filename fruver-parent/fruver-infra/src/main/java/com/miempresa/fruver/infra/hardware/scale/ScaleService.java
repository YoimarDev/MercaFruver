package com.miempresa.fruver.infra.hardware.scale;

import com.fazecast.jSerialComm.SerialPort;
import com.miempresa.fruver.domain.exceptions.DataAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Servicio para comunicación con la báscula Moresco HY-918 via puerto serial.
 */
public class ScaleService {
    private SerialPort port;

    public ScaleService() {
        // Hook de cierre por si la JVM termina abruptamente
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (port != null && port.isOpen()) {
                port.flushIOBuffers();
                port.closePort();
                System.out.println("Puerto cerrado en shutdown hook");
            }
        }));
    }

    /**
     * Abre el puerto (9600-8-N-1), limpia y resetea, y envía comando de inicio.
     */
    public void open(String portName, int baudRate) {
        // 1) Inicializar port antes de todo
        port = SerialPort.getCommPort(portName);
        if (port == null) {
            throw new DataAccessException("Puerto no encontrado: " + portName);
        }

        System.out.println("Puertos seriales detectados:");
        for (SerialPort p : SerialPort.getCommPorts()) {
            System.out.printf("  - %s : %s%n",
                    p.getSystemPortName(),
                    p.getDescriptivePortName());
        }

        // 2) Configuración básica
        port.setBaudRate(baudRate);
        port.setNumDataBits(8);
        port.setNumStopBits(SerialPort.ONE_STOP_BIT);
        port.setParity(SerialPort.NO_PARITY);
        port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);

        // 3) Intentos de apertura
        boolean opened = false;
        for (int i = 1; i <= 3; i++) {
            System.out.println("Intento " + i + " de abrir puerto " + portName);
            if (port.isOpen()) {
                port.closePort();
                port.flushIOBuffers();
                sleep(500);
            }
            port.clearDTR(); port.clearRTS(); sleep(200);
            port.setDTR();   port.setRTS();   sleep(200);

            if (port.openPort(2000)) {
                opened = true;
                System.out.println(">> Puerto abierto en intento " + i);
                break;
            } else {
                System.out.println("** Falló apertura intento " + i);
            }
            sleep(500);
        }

        if (!opened) {
            throw new DataAccessException("No se pudo abrir puerto tras 3 intentos");
        }

        // 4) Configurar timeout de lectura
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 5000, 0);

        // 5) Enviar comando iniciador
        try {
            OutputStream out = port.getOutputStream();
            out.write("96814C".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            System.out.println(">> Comando de inicio enviado");
            sleep(1000);
        } catch (IOException ex) {
            System.err.println("Error enviando comando de inicio: " + ex.getMessage());
        }
    }

    /** Lee el peso en kilogramos. */
    public double readWeightKg() {
        try (InputStream in = port.getInputStream()) {
            // descartar residuo
            int avail = port.bytesAvailable();
            if (avail > 0) {
                in.skip(avail);
            }
            sleep(500);

            // leer trama entre STX (0x02) y ETX (0x03)
            StringBuilder sb = new StringBuilder();
            long start = System.currentTimeMillis();
            boolean started = false;
            while (true) {
                if (in.available() == 0) {
                    if (System.currentTimeMillis() - start > 5000) {
                        throw new IOException("Timeout esperando peso");
                    }
                    sleep(50);
                    continue;
                }
                int b = in.read();
                if (b == 0x02) { started = true; continue; }
                if (!started) continue;
                if (b == 0x03) break;
                sb.append((char)b);
            }
            String raw = sb.toString()
                    .replaceAll("[^0-9,\\.\\-]+", "")
                    .replace(',', '.');
            double kg = Double.parseDouble(raw);
            System.out.println("Peso parseado: " + kg + " kg");
            return kg;
        } catch (Exception ex) {
            throw new DataAccessException("Error leyendo peso", ex);
        }
    }

    /** Lee el peso en gramos. */
    public int readWeightGrams() {
        return (int)Math.round(readWeightKg() * 1000);
    }

    /** Cierra y purga buffers. */
    public void close() {
        if (port != null && port.isOpen()) {
            port.flushIOBuffers();
            port.closePort();
            System.out.println("Puerto cerrado correctamente");
        }
    }

    /** Helper sleep. */
    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
