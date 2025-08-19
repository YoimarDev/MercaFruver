package com.miempresa.fruver.ui.controller;

import com.miempresa.fruver.ui.ServiceLocator;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;

import java.util.function.Consumer;

public class SplashController {
    @FXML private ProgressIndicator progress;
    @FXML private Label lblStatus;

    private Runnable onFinished;

    @FXML
    public void initialize() {
        // Ejecutar inicialización en background (no bloquear UI)
        Task<Boolean> initTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                // Bind through updateMessage / updateProgress using callbacks
                Consumer<String> msgConsumer = (msg) -> updateMessage(msg);
                Consumer<Double> progressConsumer = (p) -> updateProgress(p == null ? 0.0 : p, 1.0);

                // Llamada a ServiceLocator (ahora con progress numérico)
                boolean ok = ServiceLocator.initializeAndTestDb(msgConsumer, progressConsumer);

                // Pequeña pausa para que el usuario vea "Listo"
                updateMessage("Listo");
                updateProgress(1.0, 1.0);
                Thread.sleep(200);

                return ok;
            }
        };

        // Bind del indicador y label
        progress.progressProperty().bind(initTask.progressProperty());
        lblStatus.textProperty().bind(initTask.messageProperty());

        initTask.setOnSucceeded(e -> {
            boolean ok = initTask.getValue();
            if (ok) {
                if (onFinished != null) onFinished.run();
            } else {
                // Si falla, mostrar mensaje persistente (ya lo hace lblStatus).
                // Opcional: habilitar botón reintentar (no implementado aquí).
            }
        });

        initTask.setOnFailed(e -> {
            lblStatus.textProperty().unbind();
            lblStatus.setText("Error durante inicialización: " + initTask.getException().getMessage());
            progress.progressProperty().unbind();
            progress.setProgress(0);
        });

        Thread t = new Thread(initTask, "splash-init");
        t.setDaemon(true);
        t.start();
    }

    public void setOnFinished(Runnable onFinished) { this.onFinished = onFinished; }
}
