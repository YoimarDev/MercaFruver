package com.miempresa.fruver.ui.controller;

import com.miempresa.fruver.domain.model.Usuario;
import com.miempresa.fruver.ui.ServiceLocator;
import com.miempresa.fruver.ui.viewmodel.AdminViewModel;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.Optional;

public class AdminController {

    @FXML private Label lblTitle;
    @FXML private ComboBox<String> cbDeviceType;
    @FXML private ComboBox<String> cbAvailablePorts;
    @FXML private TextField txtPort;
    @FXML private TextArea txtParams;
    @FXML private Button btnTest;
    @FXML private Button btnSave;
    @FXML private ListView<String> lvConfigs;

    @FXML private TextField txtBarcodeTest; // nuevo: campo para probar lector

    // DB cleanup area
    @FXML private Button btnBackup;
    @FXML private Button btnCleanSales;
    @FXML private ProgressBar pbCleanup;
    @FXML private Label lblCleanupStatus;
    @FXML private VBox cleanupBox;

    // status
    @FXML private Label lblStatus;

    private AdminViewModel vm;
    private Usuario currentUser;
    private Runnable onLogout;

    public void init(Usuario usuario, Runnable onLogout) {
        this.currentUser = usuario;
        this.onLogout = onLogout;
        this.vm = new AdminViewModel(ServiceLocator.getAdminService());

        lblTitle.setText("Administración — " + (usuario != null ? usuario.getNombre() : ""));
        cbDeviceType.getItems().addAll("BASCULA", "IMPRESORA", "LECTOR");
        cbDeviceType.setValue("BASCULA");

        vm.getAvailablePorts().addAll(vm.discoverPorts());
        cbAvailablePorts.itemsProperty().bind(vm.availablePortsProperty());

        txtPort.textProperty().bindBidirectional(vm.portProperty());
        txtParams.textProperty().bindBidirectional(vm.paramsProperty());
        cbDeviceType.valueProperty().addListener((o, oldV, newV) -> {
            vm.typeProperty().set(newV);
            updateFieldsForType(newV);
        });
        vm.typeProperty().set(cbDeviceType.getValue());

        lvConfigs.itemsProperty().bind(vm.configListProperty());

        btnTest.setOnAction(e -> testConnection());
        btnSave.setOnAction(e -> saveDeviceConfig());

        btnBackup.setOnAction(e -> doBackup());
        btnCleanSales.setOnAction(e -> doCleanSales());

        // Bindings
        lblStatus.textProperty().bind(vm.statusMessageProperty());
        btnTest.disableProperty().bind(vm.busyProperty());
        btnSave.disableProperty().bind(vm.busyProperty());
        btnBackup.disableProperty().bind(vm.busyProperty());
        btnCleanSales.disableProperty().bind(vm.busyProperty());
        pbCleanup.progressProperty().bind(vm.progressProperty());
        lblCleanupStatus.textProperty().bind(vm.cleanupMessageProperty());

        cbAvailablePorts.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) txtPort.setText(newV);
        });

        lvConfigs.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String selected = lvConfigs.getSelectionModel().getSelectedItem();
                if (selected != null) vm.loadConfigToEditor(selected);
            }
        });

        // Barcode test: cuando el lector envía ENTER se dispara onAction del TextField
        if (txtBarcodeTest != null) {
            txtBarcodeTest.setOnAction(e -> {
                String code = txtBarcodeTest.getText();
                if (code != null && !code.isBlank()) {
                    showAlert(Alert.AlertType.INFORMATION, "Código leído", "Código: " + code.trim());
                    txtBarcodeTest.clear();
                }
            });
        }

        // initial UI tweak based on default type
        updateFieldsForType(cbDeviceType.getValue());

        // Load configs
        loadConfigsBackground();
    }

    private void updateFieldsForType(String tipo) {
        boolean isBasc = "BASCULA".equalsIgnoreCase(tipo);
        // Sólo para la báscula permitimos editar parámetros (baudRate etc).
        txtParams.setDisable(!isBasc);
        // Para lector/impresora sólo se necesita puerto (o ninguno). Puedes ocultar/mostrar elementos si quieres.
        if ("LECTOR".equalsIgnoreCase(tipo)) {
            // sugerencia: enfocar campo de prueba para que admin escanee
            if (txtBarcodeTest != null) Platform.runLater(() -> txtBarcodeTest.requestFocus());
        }
    }

    private void loadConfigsBackground() {
        Task<Void> t = new Task<>() {
            @Override
            protected Void call() throws Exception {
                vm.refreshConfigs();
                return null;
            }
        };
        new Thread(t, "admin-load-configs").start();
    }

    private void testConnection() {
        String port = txtPort.getText();
        String type = cbDeviceType.getValue();
        if (port == null || port.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Puerto vacío", "Debes indicar el puerto a probar.");
            return;
        }

        vm.setStatus("Probando conexión (" + type + " @ " + port + ")...");
        Task<Boolean> t = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return ServiceLocator.getAdminService().testDeviceConnection(type, port, (msg)-> updateMessage(msg), (p)-> updateProgress(p,1.0));
            }
        };

        t.setOnSucceeded(evt -> {
            boolean ok = t.getValue();
            vm.setStatus(ok ? "Conexión OK" : "Falló la conexión");
            if (ok) {
                showAlert(Alert.AlertType.INFORMATION, "Conexión OK", "Se ha detectado respuesta desde el dispositivo.");
            } else {
                showAlert(Alert.AlertType.ERROR, "Conexión", "No se obtuvo respuesta del dispositivo.");
            }
        });

        t.setOnFailed(evt -> {
            vm.setStatus("Error durante la prueba: " + t.getException().getMessage());
            showAlert(Alert.AlertType.ERROR, "Error", "Error probando el dispositivo:\n" + t.getException().getMessage());
        });

        new Thread(t, "admin-test-device").start();
    }

    private void saveDeviceConfig() {
        String type = cbDeviceType.getValue();
        String port = txtPort.getText();
        String params = txtParams.getText();

        if (port == null || port.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Puerto vacío", "Indica el puerto antes de guardar.");
            return;
        }

        Alert conf = new Alert(Alert.AlertType.CONFIRMATION);
        conf.setTitle("Guardar configuración");
        conf.setHeaderText(null);
        conf.setContentText("Guardar configuración: " + type + " @ " + port + " ?");
        Optional<ButtonType> res = conf.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        vm.setStatus("Guardando configuración...");
        Task<Void> t = new Task<>() {
            @Override
            protected Void call() throws Exception {
                ServiceLocator.getAdminService().saveDeviceConfig(type, port, params);
                return null;
            }
        };

        t.setOnSucceeded(evt -> {
            vm.setStatus("Configuración guardada");
            vm.refreshConfigs();
            showAlert(Alert.AlertType.INFORMATION, "Guardado", "Configuración guardada correctamente.");
            // tras guardar, probar conexión automáticamente para dar feedback
            Platform.runLater(() -> {
                txtPort.setText(port);
                testConnection();
            });
        });

        t.setOnFailed(evt -> {
            vm.setStatus("Error guardando: " + t.getException().getMessage());
            showAlert(Alert.AlertType.ERROR, "Error", "No se pudo guardar la configuración:\n" + t.getException().getMessage());
        });

        new Thread(t, "admin-save-config").start();
    }

    private void doBackup() {
        Alert conf = new Alert(Alert.AlertType.CONFIRMATION);
        conf.setTitle("Backup");
        conf.setHeaderText("Realizar backup antes de limpieza");
        conf.setContentText("Se recomienda hacer backup. ¿Deseas crear backup ahora?");
        Optional<ButtonType> r = conf.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) return;

        vm.setStatus("Iniciando backup...");
        Task<Void> t = new Task<>() {
            @Override
            protected Void call() throws Exception {
                ServiceLocator.getAdminService().performBackup( (msg)-> updateMessage(msg), (p)-> updateProgress(p,1.0) );
                return null;
            }
        };

        pbCleanup.progressProperty().unbind();
        pbCleanup.progressProperty().bind(t.progressProperty());
        t.messageProperty().addListener((obs, oldV, newV) -> vm.setCleanupMessage(newV));

        t.setOnSucceeded(evt -> {
            vm.setStatus("Backup finalizado");
            vm.setCleanupMessage("Backup completado");
            pbCleanup.progressProperty().unbind();
            pbCleanup.setProgress(0);
            showAlert(Alert.AlertType.INFORMATION, "Backup", "Backup completado correctamente.");
        });

        t.setOnFailed(evt -> {
            vm.setStatus("Error en backup: " + t.getException().getMessage());
            vm.setCleanupMessage("Error en backup");
            pbCleanup.progressProperty().unbind();
            pbCleanup.setProgress(0);
            showAlert(Alert.AlertType.ERROR, "Backup", "Error durante backup:\n" + t.getException().getMessage());
        });

        new Thread(t, "admin-backup").start();
    }

    private void doCleanSales() {
        Alert conf = new Alert(Alert.AlertType.CONFIRMATION);
        conf.setTitle("Limpiar ventas");
        conf.setHeaderText("Operación destructiva");
        conf.setContentText("Esto eliminará datos de ventas (VENTA, VENTA_ITEM, FACTURA). ¿Confirmas?");
        Optional<ButtonType> r = conf.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) return;

        vm.setStatus("Iniciando limpieza de ventas...");
        Task<Void> t = new Task<>() {
            @Override
            protected Void call() throws Exception {
                ServiceLocator.getAdminService().cleanSalesData((msg)-> updateMessage(msg), (p)-> updateProgress(p,1.0));
                return null;
            }
        };

        pbCleanup.progressProperty().unbind();
        pbCleanup.progressProperty().bind(t.progressProperty());
        t.messageProperty().addListener((obs, oldV, newV) -> vm.setCleanupMessage(newV));

        t.setOnSucceeded(evt -> {
            vm.setStatus("Limpieza finalizada");
            vm.setCleanupMessage("Limpieza completada");
            pbCleanup.progressProperty().unbind();
            pbCleanup.setProgress(0);
            showAlert(Alert.AlertType.INFORMATION, "Limpieza", "Limpieza de ventas completada.");
            vm.refreshConfigs();
        });

        t.setOnFailed(evt -> {
            vm.setStatus("Error limpieza: " + t.getException().getMessage());
            vm.setCleanupMessage("Error en limpieza");
            pbCleanup.progressProperty().unbind();
            pbCleanup.setProgress(0);
            showAlert(Alert.AlertType.ERROR, "Limpieza", "Error durante limpieza:\n" + t.getException().getMessage());
        });

        new Thread(t, "admin-clean-sales").start();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert a = new Alert(type);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(content);
            a.showAndWait();
        });
    }

    @FXML
    private void onSalir() {
        if (onLogout != null) {
            Platform.runLater(onLogout);
        } else {
            com.miempresa.fruver.service.security.SecurityContext.clear();
            Platform.runLater(() -> System.err.println("Logout solicitado pero onLogout == null"));
        }
    }
}
