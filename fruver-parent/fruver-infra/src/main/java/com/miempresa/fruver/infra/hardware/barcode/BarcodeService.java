package com.miempresa.fruver.infra.hardware.barcode;

import com.miempresa.fruver.domain.exceptions.DataAccessException;
import com.miempresa.fruver.domain.model.DeviceConfig.DeviceType;
import com.miempresa.fruver.domain.repository.DeviceConfigRepository;
import com.miempresa.fruver.infra.db.DeviceConfigRepositoryJdbc;
import com.miempresa.fruver.infra.config.DataSourceFactory;
import com.fazecast.jSerialComm.SerialPort;

import javax.sql.DataSource;
import java.util.function.Consumer;

/**
 * Servicio para manejar un lector de código de barras.
 * - Si está en modo "keyboard" o no hay puerto configurado, no abre puerto.
 * - Si está en modo "serial" y hay puerto, abre el COM indicado.
 */
public class BarcodeService {

    private SerialPort port;
    private Consumer<String> onCodeScanned;

    /** Inicializa según configuración en DB. */
    public void init() {
        DataSource ds = DataSourceFactory.getDataSource();
        DeviceConfigRepository cfgRepo = new DeviceConfigRepositoryJdbc(ds);

        // Leer parámetros JSON y puerto
        String parametrosJson = cfgRepo.findByType(DeviceType.LECTOR)
                .map(cfg -> cfg.getParametrosJson())
                .orElseThrow(() -> new DataAccessException("Config de lector no encontrada"));

        String portName = cfgRepo.findByType(DeviceType.LECTOR)
                .map(cfg -> cfg.getPuerto())
                .orElse("");

        // Si está en modo keyboard O no hay puerto, saltar apertura
        boolean keyboardMode = parametrosJson.contains("\"mode\":\"keyboard\"")
                || portName.isBlank();
        if (keyboardMode) {
            System.out.println("-> Lector en modo teclado (keyboard), sin COM");
            return;
        }

        // Modo serial: abrir puerto
        port = SerialPort.getCommPort(portName);
        port.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 5000, 0);
        if (!port.openPort()) {
            throw new DataAccessException("No se pudo abrir puerto del lector: " + portName);
        }
        System.out.println("-> Lector serial inicializado en " + portName);
    }

    /** Callback al escanear un código. */
    public void setOnCodeScanned(Consumer<String> callback) {
        this.onCodeScanned = callback;
    }

    /** Procesa manualmente un código (teclado) o lee del serial. */
    public void handleInput(String manualCode) {
        // teclado
        if (port == null) {
            if (onCodeScanned != null) onCodeScanned.accept(manualCode);
            return;
        }
        // serial: leer hasta CR/LF
        if (onCodeScanned != null) {
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[1];
            try {
                while (true) {
                    int n = port.readBytes(buf, 1);
                    if (n <= 0) break;
                    char c = (char) buf[0];
                    if (c=='\n' || c=='\r') break;
                    sb.append(c);
                }
                String code = sb.toString().trim();
                if (!code.isEmpty()) onCodeScanned.accept(code);
            } catch (Exception e) {
                throw new DataAccessException("Error leyendo lector serial", e);
            }
        }
    }

    /** Cierra el puerto si estaba abierto. */
    public void close() {
        if (port != null && port.isOpen()) port.closePort();
    }
}
