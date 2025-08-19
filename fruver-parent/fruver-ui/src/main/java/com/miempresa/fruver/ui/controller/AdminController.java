package com.miempresa.fruver.ui.controller;

import com.miempresa.fruver.domain.model.Usuario;
import com.miempresa.fruver.service.port.DatabaseStorageInfo;
import com.miempresa.fruver.ui.ServiceLocator;
import com.miempresa.fruver.ui.viewmodel.AdminViewModel;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;

import java.util.Optional;

/**
 * Controller para la vista Admin.
 * - gestiona configuración de dispositivos y gestión de usuarios.
 * - mejoras visuales: cellFactory amigable, notificaciones no intrusivas.
 */
public class AdminController {

    @FXML private Label lblTitle;
    @FXML private ComboBox<String> cbDeviceType;
    @FXML private ComboBox<String> cbAvailablePorts;
    @FXML private TextField txtPort;
    @FXML private TextArea txtParams;
    @FXML private Button btnTest;
    @FXML private Button btnSave;
    @FXML private ListView<String> lvConfigs;

    @FXML private TextField txtBarcodeTest; // campo para probar lector

    // DB cleanup area
    @FXML private Button btnCleanSales;
    @FXML private ProgressBar pbCleanup;
    @FXML private Label lblCleanupStatus;

    // indicators
    @FXML private Label lblStatus;
    @FXML private Label lblScaleStatus;
    @FXML private Label lblReaderStatus;

    // --- Usuarios UI controls (nuevos)
    @FXML private ListView<Usuario> lvUsers;
    @FXML private TextField txtUserName;
    @FXML private ComboBox<String> cbUserRole;
    @FXML private PasswordField txtUserPassword;
    @FXML private Button btnCreateUser;
    @FXML private Button btnUpdateUser;
    @FXML private Button btnDeleteUser;
    @FXML private Label lblUserStatus;

    // Salir
    @FXML private Button btnSalir;

    private AdminViewModel vm;
    private Usuario currentUser;
    private Runnable onLogout;

    public void init(Usuario usuario, Runnable onLogout) {
        this.currentUser = usuario;
        this.onLogout = onLogout;
        this.vm = new AdminViewModel(ServiceLocator.getAdminService());

        lblTitle.setText("Administración — " + (usuario != null ? usuario.getNombre() : ""));

        // Device type combo - include IMPRESORA but render disabled
        cbDeviceType.getItems().addAll("BASCULA", "IMPRESORA", "LECTOR");
        cbDeviceType.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setDisable(false);
                } else {
                    setText(item);
                    if ("IMPRESORA".equalsIgnoreCase(item)) {
                        setDisable(true);
                        setStyle("-fx-text-fill: gray;");
                    } else {
                        setDisable(false);
                        setStyle(null);
                    }
                }
            }
        });

        cbDeviceType.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if ("IMPRESORA".equalsIgnoreCase(newV)) {
                cbDeviceType.getSelectionModel().select(oldV == null ? "BASCULA" : oldV);
            } else if (newV != null) {
                vm.typeProperty().set(newV);
                updateFieldsForType(newV);
            }
        });
        cbDeviceType.setValue("BASCULA");

        // Available ports
        vm.getAvailablePorts().addAll(vm.discoverPorts());
        cbAvailablePorts.itemsProperty().bind(vm.availablePortsProperty());

        // Inputs bind
        txtPort.textProperty().bindBidirectional(vm.portProperty());
        txtParams.textProperty().bindBidirectional(vm.paramsProperty());
        vm.typeProperty().set(cbDeviceType.getValue());

        // Config list
        lvConfigs.itemsProperty().bind(vm.configListProperty());
        lvConfigs.setCellFactory(list -> new TextFieldListCell<>());

        // Buttons device
        btnTest.setOnAction(e -> testConnection());
        btnSave.setOnAction(e -> saveDeviceConfig());
        btnCleanSales.setOnAction(e -> doCleanSales());

        // status & disable bindings
        lblStatus.textProperty().bind(vm.statusMessageProperty());
        btnTest.disableProperty().bind(vm.busyProperty());
        btnSave.disableProperty().bind(vm.busyProperty());
        btnCleanSales.disableProperty().bind(vm.busyProperty());
        pbCleanup.progressProperty().bind(vm.progressProperty());
        lblCleanupStatus.textProperty().bind(vm.cleanupMessageProperty());

        // choose port from detected ports
        cbAvailablePorts.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) txtPort.setText(newV);
        });

        // double-click to load config into editor
        lvConfigs.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String selected = lvConfigs.getSelectionModel().getSelectedItem();
                if (selected != null) vm.loadConfigToEditor(selected);
            }
        });

        // barcode test: show code and auto-save LECTOR keyboard mode
        if (txtBarcodeTest != null) {
            txtBarcodeTest.setOnAction(e -> {
                String code = txtBarcodeTest.getText();
                if (code != null && !code.isBlank()) {
                    showTransientInfo("Código leído", code.trim());
                    txtBarcodeTest.clear();

                    // Guardar LECTOR en modo keyboard (background)
                    new Thread(() -> {
                        try {
                            ServiceLocator.getAdminService().saveDeviceConfig("LECTOR", "", "{\"mode\":\"keyboard\"}");
                            Platform.runLater(() -> {
                                vm.refreshConfigs();
                                showTransientInfo("Configuración", "Se guardó LECTOR (keyboard).");
                                refreshIndicatorsBackground();
                            });
                        } catch (Throwable ex) {
                            Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error", "No se pudo guardar config lector:\n" + ex.getMessage()));
                        }
                    }, "save-lector-keyboard").start();
                }
            });
        }

        // --- Inicialización usuarios UI ---
        // roles en combo
        cbUserRole.getItems().addAll("CAJERO", "SUPERVISOR", "ADMIN");

        // bind lista de usuarios del VM (observable list)
        lvUsers.setItems(vm.usersProperty().get());
        vm.usersProperty().addListener((obs, oldV, newV) -> {
            // no-op (lista ya actualizada por VM)
        });

        // Mejor cellFactory: avatar + nombre (rol) • id
        lvUsers.setCellFactory(listView -> new ListCell<>() {
            private final HBox root = new HBox(10);
            private final Circle avatar = new Circle(18);
            private final VBox vbox = new VBox(2);
            private final Label lblName = new Label();
            private final Label lblMeta = new Label();

            {
                root.setAlignment(Pos.CENTER_LEFT);
                avatar.setFill(Color.web("#A5D6A7"));
                avatar.setStroke(Color.web("#2E7D32"));
                lblName.setFont(Font.font(14));
                lblMeta.setStyle("-fx-font-size:11px; -fx-text-fill: #666;");
                vbox.getChildren().addAll(lblName, lblMeta);
                root.getChildren().addAll(avatar, vbox);
            }

            @Override
            protected void updateItem(Usuario user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    lblName.setText(user.getNombre());
                    lblMeta.setText(user.getRol().name() + " • id:" + user.getUsuarioId());
                    setGraphic(root);
                }
            }
        });

        // selection listener para llenar formulario
        lvUsers.getSelectionModel().selectedItemProperty().addListener((obs, oldU, newU) -> {
            if (newU != null) {
                txtUserName.setText(newU.getNombre());
                cbUserRole.setValue(newU.getRol().name());
                txtUserPassword.clear(); // por seguridad no mostrar hash
            } else {
                txtUserName.clear();
                cbUserRole.setValue(null);
                txtUserPassword.clear();
            }
        });

        // botones usuarios
        btnCreateUser.setOnAction(e -> onCreateUser());
        btnUpdateUser.setOnAction(e -> onUpdateUser());
        btnDeleteUser.setOnAction(e -> onDeleteUser());

        // bind user status label
        lblUserStatus.textProperty().bind(vm.userStatusProperty());

        // listener para notificaciones (éxito / error desde VM)
        vm.lastNotificationProperty().addListener((obs, oldMsg, newMsg) -> {
            if (newMsg != null && !newMsg.isBlank()) {
                // Mostrar popup no bloqueante
                Platform.runLater(() -> {
                    showTransientInfo("Operación", newMsg);
                    // limpiar para evitar repetir
                    vm.lastNotificationProperty().set(null);
                });
            }
        });

        // initial tweak and load
        updateFieldsForType(cbDeviceType.getValue());
        loadConfigsBackground();
        fetchDbStorageInfoBackground();

        // cargar usuarios
        vm.loadUsers();

        // bind indicators
        lblScaleStatus.textProperty().bind(vm.scaleStatusProperty());
        lblReaderStatus.textProperty().bind(vm.readerStatusProperty());

        // Salir: si se presiona en cualquier vista debe ejecutar onSalir (vuelve al login)
        if (btnSalir != null) {
            btnSalir.setOnAction(evt -> onSalir());
        }

        // si estamos en modo demo (in-memory), avisar al admin
        if (ServiceLocator.isUsingInMemoryAdminService()) {
            vm.setStatus("Atención: modo demo (in-memory) activo — persistencia deshabilitada.");
            Platform.runLater(() -> {
                Alert a = new Alert(Alert.AlertType.WARNING);
                a.setTitle("Modo demo (in-memory)");
                a.setHeaderText("Persistencia no disponible");
                a.setContentText("El servicio arrancó en modo demo (in-memory). Las configuraciones guardadas no se persistirán en la base de datos.\n" +
                        "Para activar persistencia: compila e instala el módulo fruver-infra y ejecuta la aplicación con la BD disponible.");
                a.showAndWait();
            });
        }
    }

    /* ------------------ Usuarios handlers ------------------ */

    private void onCreateUser() {
        String nombre = txtUserName.getText();
        String rolStr = cbUserRole.getValue();
        String password = txtUserPassword.getText();

        if (nombre == null || nombre.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Validación", "Nombre es obligatorio.");
            return;
        }
        if (rolStr == null || rolStr.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Validación", "Selecciona un rol.");
            return;
        }
        if (password == null || password.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Validación", "Contraseña es obligatoria.");
            return;
        }

        // Ejecutar create en VM (async). VM disparará lastNotification cuando termine.
        vm.createUser(nombre.trim(), Usuario.Role.valueOf(rolStr), password);
    }

    private void onUpdateUser() {
        Usuario sel = lvUsers.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showAlert(Alert.AlertType.WARNING, "Validación", "Selecciona un usuario para actualizar.");
            return;
        }
        String nombre = txtUserName.getText();
        String rolStr = cbUserRole.getValue();
        String password = txtUserPassword.getText(); // puede estar vacío -> no cambiar

        if (nombre == null || nombre.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Validación", "Nombre es obligatorio.");
            return;
        }
        vm.updateUser(sel.getUsuarioId(), nombre.trim(), Usuario.Role.valueOf(rolStr), (password == null || password.isBlank()) ? null : password);
    }

    private void onDeleteUser() {
        Usuario sel = lvUsers.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showAlert(Alert.AlertType.WARNING, "Validación", "Selecciona un usuario para eliminar.");
            return;
        }
        if (sel.getUsuarioId().equals(currentUser != null ? currentUser.getUsuarioId() : -1)) {
            showAlert(Alert.AlertType.WARNING, "Acción no permitida", "No puedes eliminar el usuario con sesión activa.");
            return;
        }

        Alert conf = new Alert(Alert.AlertType.CONFIRMATION);
        conf.setTitle("Eliminar usuario");
        conf.setHeaderText("Confirmar eliminación");
        conf.setContentText("¿Deseas eliminar al usuario '" + sel.getNombre() + "' (id=" + sel.getUsuarioId() + ")?");
        Optional<ButtonType> r = conf.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) return;

        vm.deleteUser(sel.getUsuarioId());
    }

    /* ------------------ (resto del controller: dispositivos y limpieza) ------------------ */

    private void updateFieldsForType(String tipo) {
        boolean isBasc = "BASCULA".equalsIgnoreCase(tipo);
        txtParams.setDisable(!isBasc);
        if ("LECTOR".equalsIgnoreCase(tipo)) {
            if (txtBarcodeTest != null) Platform.runLater(() -> txtBarcodeTest.requestFocus());
        }
    }

    private void loadConfigsBackground() {
        Task<Void> t = new Task<>() {
            @Override
            protected Void call() {
                vm.refreshConfigs();
                return null;
            }
        };
        t.setOnSucceeded(e -> refreshIndicatorsBackground());
        new Thread(t, "admin-load-configs").start();
    }

    private void refreshIndicatorsBackground() {
        new Thread(() -> {
            try {
                for (String repr : vm.getConfigList()) {
                    if (repr == null) continue;
                    String[] parts = repr.split("\\|", 2);
                    String left = parts[0];
                    String[] leftParts = left.split("@", 2);
                    String tipo = leftParts.length > 0 ? leftParts[0] : "";
                    String puerto = leftParts.length > 1 ? leftParts[1] : "";

                    boolean ok = ServiceLocator.getAdminService().testDeviceConnection(tipo, puerto,
                            (msg) -> {}, (p) -> {});
                    final boolean connected = ok;
                    final String st = ok ? "Conectado (" + puerto + ")" : "No conectado";
                    if ("BASCULA".equalsIgnoreCase(tipo)) {
                        Platform.runLater(() -> vm.setScaleStatus(st, connected));
                    } else if ("LECTOR".equalsIgnoreCase(tipo)) {
                        Platform.runLater(() -> vm.setReaderStatus(st, connected));
                    }
                }
            } catch (Throwable ignored) {}
        }, "admin-refresh-indicators").start();
    }

    private void testConnection() {
        final String port = txtPort.getText();
        final String type = cbDeviceType.getValue();
        if (port == null || port.isBlank()) {
            if (!"LECTOR".equalsIgnoreCase(type)) {
                showAlert(Alert.AlertType.WARNING, "Puerto vacío", "Debes indicar el puerto a probar.");
                return;
            }
        }

        vm.setStatus("Probando conexión (" + type + " @ " + (port == null ? "" : port) + ")...");
        Task<Boolean> t = new Task<>() {
            @Override
            protected Boolean call() {
                return ServiceLocator.getAdminService()
                        .testDeviceConnection(type, port == null ? "" : port,
                                (msg) -> updateMessage(msg),
                                (p) -> updateProgress(p, 1.0));
            }
        };

        t.setOnSucceeded(evt -> {
            boolean ok = t.getValue();
            vm.setStatus(ok ? "Conexión OK" : "Falló la conexión");
            if ("BASCULA".equalsIgnoreCase(type)) {
                vm.setScaleStatus(ok ? "Conectada (" + port + ")" : "No conectada", ok);
            } else if ("LECTOR".equalsIgnoreCase(type)) {
                vm.setReaderStatus(ok ? "Conectada (" + port + ")" : "No conectada", ok);
            }
            if (ok) {
                showTransientInfo("Conexión OK", "Se ha detectado respuesta desde el dispositivo.");
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
        final String type = cbDeviceType.getValue();
        final String port = txtPort.getText();
        final String params = txtParams.getText();

        if (!"LECTOR".equalsIgnoreCase(type)) {
            if (port == null || port.isBlank()) {
                showAlert(Alert.AlertType.WARNING, "Puerto vacío", "Indica el puerto antes de guardar.");
                return;
            }
        }

        Alert conf = new Alert(Alert.AlertType.CONFIRMATION);
        conf.setTitle("Guardar configuración");
        conf.setHeaderText(null);
        conf.setContentText("Guardar configuración: " + type + " @ " + (port == null ? "" : port) + " ?");
        Optional<ButtonType> res = conf.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        vm.setStatus("Guardando configuración...");
        Task<Void> t = new Task<>() {
            @Override
            protected Void call() {
                ServiceLocator.getAdminService().saveDeviceConfig(type, port == null ? "" : port, params == null ? "{}" : params);
                return null;
            }
        };

        t.setOnSucceeded(evt -> {
            vm.setStatus("Configuración guardada");
            vm.refreshConfigs();
            showTransientInfo("Guardado", "Configuración guardada correctamente.");
            Platform.runLater(() -> testConnection());
        });

        t.setOnFailed(evt -> {
            vm.setStatus("Error guardando: " + t.getException().getMessage());
            showAlert(Alert.AlertType.ERROR, "Error", "No se pudo guardar la configuración:\n" + t.getException().getMessage());
        });

        new Thread(t, "admin-save-config").start();
    }

    private void fetchDbStorageInfoBackground() {
        new Thread(() -> {
            try {
                var info = vm.getDatabaseStorageInfo();
                if (info == null) {
                    Platform.runLater(() -> vm.setCleanupMessage("No se pudo obtener info DB"));
                    return;
                }
                long used = info.getUsedBytes();
                Optional<Long> free = info.getFsFreeBytes();
                double percent;
                if (free.isPresent()) {
                    percent = (double) used / ((double) used + (double) free.get());
                } else {
                    long assumed = 10L * 1024L * 1024L * 1024L; // 10GB
                    percent = (double) used / (double) assumed;
                }
                final double p = Math.min(1.0, Math.max(0.0, percent));
                Platform.runLater(() -> {
                    vm.progressProperty().set(p);
                    StringBuilder human = new StringBuilder(String.format("Usado: %.2f MB", used / 1024.0 / 1024.0));
                    free.ifPresent(f -> human.append(String.format(" • Libre FS: %.2f MB", f / 1024.0 / 1024.0)));
                    if (!free.isPresent()) human.append(" • Capacidad asumida: 10GB");
                    vm.setCleanupMessage(human.toString());
                });
            } catch (Throwable t) {
                Platform.runLater(() -> vm.setCleanupMessage("No se pudo obtener info DB: " + t.getMessage()));
            }
        }, "admin-db-info").start();
    }

    private void doCleanSales() {
        DatabaseStorageInfo info = vm.getDatabaseStorageInfo();
        StringBuilder usedHuman = new StringBuilder("Este proceso eliminará ventas y facturas de forma irreversible.");
        if (info != null) {
            usedHuman.append("\nTamaño actual DB: ").append(String.format("%.2f MB", info.getUsedBytes() / 1024.0 / 1024.0));
            info.getFsFreeBytes().ifPresent(f -> usedHuman.append("\nEspacio libre FS: ").append(String.format("%.2f MB", f / 1024.0 / 1024.0)));
        }
        Alert conf = new Alert(Alert.AlertType.CONFIRMATION);
        conf.setTitle("Confirmar limpieza");
        conf.setHeaderText("Operación destructiva: limpiar ventas");
        conf.setContentText(usedHuman.toString() + "\n\n¿Deseas continuar?");
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
            refreshIndicatorsBackground();
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

    /* ------------------ Helpers UI ------------------ */

    private void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert a = new Alert(type);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(content);
            a.showAndWait();
        });
    }

    /**
     * Muestra un popup no bloqueante e informativo (para éxito o info breve).
     */
    private void showTransientInfo(String title, String content) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(content);
            a.initOwner(lblTitle.getScene() == null ? null : lblTitle.getScene().getWindow());
            // no-blocking
            a.show();
            // opcional: auto-close después de X ms (no nativo en Alert) -> se puede cerrar manualmente en background
            new Thread(() -> {
                try {
                    Thread.sleep(2200);
                    Platform.runLater(a::close);
                } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }, "transient-close").start();
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
