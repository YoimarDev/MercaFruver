package com.miempresa.fruver.ui;

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
                updateMessage("Cargando configuración...");
                Thread.sleep(300); // breve pausa para animación
                // Llamar al inicializador central (implementarlo en ServiceLocator)
                updateMessage("Conectando a la base de datos...");
                boolean dbOk = ServiceLocator.initializeAndTestDb((msg) -> updateMessage(msg));
                if (!dbOk) {
                    updateMessage("No se pudo conectar a la BD.");
                    return false;
                }
                updateMessage("Inicializando dispositivos...");
                // aquí podrías invocar detection de báscula/lector (opcional)
                Thread.sleep(300);
                updateMessage("Listo");
                Thread.sleep(200);
                return true;
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
                // Si falla, mostrar mensaje persistente (ya lo hace lblStatus). Se podría añadir botón de reintento.
            }
        });

        initTask.setOnFailed(e -> {
            lblStatus.textProperty().unbind();
            lblStatus.setText("Error durante inicialización: " + initTask.getException().getMessage());
        });

        Thread t = new Thread(initTask, "splash-init");
        t.setDaemon(true);
        t.start();
    }

    public void setOnFinished(Runnable onFinished) { this.onFinished = onFinished; }
}
