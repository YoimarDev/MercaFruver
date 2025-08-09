package com.miempresa.fruver.infra.hardware.scale;

import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Manager que intenta mantener la báscula conectada.
 * Envuelve un ScaleService (no lo modifica).
 * Reintenta apertura con backoff exponencial cuando detecta desconexión.
 * Notifica el estado vía onStatus consumer.
 */
public class ScaleAutoReconnectManager {
    private final ScaleService scale;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ScaleAutoReconnect");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean monitoring = false;
    private volatile String currentPort;
    private volatile int currentBaud;
    private Consumer<String> onStatus = s -> {};

    public ScaleAutoReconnectManager(ScaleService scale) {
        this.scale = scale;
    }

    public void setOnStatus(Consumer<String> onStatus) {
        this.onStatus = onStatus == null ? s -> {} : onStatus;
    }

    /**
     * Inicia monitor que mantiene la conexión.
     */
    public void startMonitoring(String portName, int baudRate) {
        if (monitoring) return;
        this.currentPort = portName;
        this.currentBaud = baudRate;
        monitoring = true;

        scheduler.scheduleWithFixedDelay(new Runnable() {
            private int attempt = 0;

            @Override
            public void run() {
                if (!monitoring) return;
                try {
                    // 1) si puerto está abierto y responde -> informamos y salimos de esta ejecución
                    try {
                        // readWeightKg lanza excepción si no responde
                        double w = scale.readWeightKg();
                        attempt = 0;
                        onStatus.accept(String.format("Conectada (%.3f kg)", w));
                        // terminar run() — será invocado de nuevo por el scheduler
                        return;
                    } catch (Exception ignore) {
                        // Caído -> intentaremos reconectar
                    }

                    // 2) intentar reconexión
                    attempt++;
                    long backoff = Math.min(30_000L, (long) (1000L * Math.pow(2, Math.min(attempt, 6))));
                    onStatus.accept("Intento conexión #" + attempt + " a " + currentPort);
                    try {
                        scale.open(currentPort, currentBaud);
                        onStatus.accept("Puerto abierto: " + currentPort);
                        attempt = 0;
                    } catch (Exception ex) {
                        onStatus.accept("Fallo apertura: " + ex.getMessage() + " -> reintento en " + backoff + "ms");
                        try { Thread.sleep(backoff); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    }

                } catch (Throwable t) {
                    // proteger scheduler: log y continuar en próxima iteración
                    t.printStackTrace();
                }
            }
        }, 0, 3, TimeUnit.SECONDS);
    }

    public void stop() {
        monitoring = false;
        try {
            scheduler.shutdownNow();
        } catch (Exception ignored) {}
        try { scale.close(); } catch (Exception ignored) {}
        onStatus.accept("Reconnector stopped");
    }
}
