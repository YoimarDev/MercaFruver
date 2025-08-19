package com.miempresa.fruver.ui.viewmodel;

import com.miempresa.fruver.ui.ServiceLocator;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Objects;

/**
 * ViewModel para AdminController
 * - Actualizaciones de propiedades en FX thread (safe)
 * - expone operaciones síncronas usadas por el controller.
 */
public class AdminViewModel {

    private final StringProperty type = new SimpleStringProperty("BASCULA");
    private final StringProperty port = new SimpleStringProperty();
    private final StringProperty params = new SimpleStringProperty("{}");
    private final StringProperty statusMessage = new SimpleStringProperty();
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final DoubleProperty progress = new SimpleDoubleProperty(0.0);

    private final ListProperty<String> availablePorts = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final ListProperty<String> configList = new SimpleListProperty<>(FXCollections.observableArrayList());

    private final StringProperty cleanupMessage = new SimpleStringProperty();

    private final ServiceLocator.AdminService adminService;

    public AdminViewModel(ServiceLocator.AdminService adminService) {
        this.adminService = Objects.requireNonNull(adminService);
    }

    /* ---------------------
       Properties (bindings)
       --------------------- */
    public StringProperty typeProperty() { return type; }
    public StringProperty portProperty() { return port; }
    public StringProperty paramsProperty() { return params; }
    public StringProperty statusMessageProperty() { return statusMessage; }
    public BooleanProperty busyProperty() { return busy; }
    public DoubleProperty progressProperty() { return progress; }

    public ListProperty<String> availablePortsProperty() { return availablePorts; }
    public ListProperty<String> configListProperty() { return configList; }

    public StringProperty cleanupMessageProperty() { return cleanupMessage; }

    /* ---------------------
       Simple getters
       --------------------- */
    public javafx.collections.ObservableList<String> getAvailablePorts() { return availablePorts.get(); }
    public javafx.collections.ObservableList<String> getConfigList() { return configList.get(); }

    /* ---------------------
       Utility / actions
       --------------------- */

    /**
     * Descubre puertos usando adminService y actualiza la lista observable (EN FX THREAD).
     */
    public List<String> discoverPorts() {
        List<String> ports = adminService.listAvailablePorts();
        // actualizar en FX thread
        Platform.runLater(() -> {
            try {
                availablePorts.get().setAll(ports);
            } catch (Exception ex) {
                availablePorts.set(FXCollections.observableArrayList(ports));
            }
        });
        return ports;
    }

    /**
     * Refresca la lista de configuraciones desde el servicio.
     * Ejecuta actualizaciones de propiedades en FX thread para evitar excepciones.
     */
    public void refreshConfigs() {
        setBusy(true);
        try {
            List<String> confs = adminService.listDeviceConfigs();
            // actualizar observable y status en hilo FX
            Platform.runLater(() -> {
                try {
                    if (confs == null) {
                        configList.get().clear();
                        setStatus("No hay configuraciones (o no se pudo leer).");
                    } else {
                        configList.get().setAll(confs);
                        setStatus("Configuraciones cargadas: " + confs.size());
                    }
                } finally {
                    setBusy(false);
                }
            });
        } catch (Throwable t) {
            // reportar error en hilo FX
            Platform.runLater(() -> {
                setStatus("Error cargando configuraciones: " + t.getMessage());
                setBusy(false);
            });
        }
    }


    /**
     * Carga una representación de configuración en los campos editables del VM.
     */
    public void loadConfigToEditor(String repr) {
        if (repr == null) return;
        String[] parts = repr.split("\\|", 2);
        String left = parts.length > 0 ? parts[0] : repr;
        String paramsText = parts.length > 1 ? parts[1] : "{}";
        String[] leftParts = left.split("@", 2);
        String tipo = leftParts.length > 0 ? leftParts[0] : "BASCULA";
        String puerto = leftParts.length > 1 ? leftParts[1] : "";
        //estas actualizaciones pueden hacerse en cualquier hilo pero normalmente se ejecutan en FX thread desde Controller
        Platform.runLater(() -> {
            type.set(tipo);
            port.set(puerto);
            params.set(paramsText);
        });
    }

    /* ---------------------
       Small setters used by controller
       --------------------- */

    public void setStatus(String s) {
        Platform.runLater(() -> statusMessage.set(s));
    }
    public void setCleanupMessage(String s) { Platform.runLater(() -> cleanupMessage.set(s)); }
    public void setBusy(boolean b) { Platform.runLater(() -> busy.set(b)); }

    // Indicadores para UI (escala/lector)
    private final StringProperty scaleStatus = new SimpleStringProperty("Desconocido");
    private final StringProperty readerStatus = new SimpleStringProperty("Desconocido");
    public StringProperty scaleStatusProperty() { return scaleStatus; }
    public StringProperty readerStatusProperty() { return readerStatus; }

    public void setScaleStatus(String s, boolean connected) {
        Platform.runLater(() -> scaleStatus.set(s));
    }
    public void setReaderStatus(String s, boolean connected) {
        Platform.runLater(() -> readerStatus.set(s));
    }

    /* -------------- Helpers to expose DB info to controller ------------- */
    public com.miempresa.fruver.service.port.DatabaseStorageInfo getDatabaseStorageInfo() {
        try {
            return adminService.getDatabaseStorageInfo();
        } catch (Throwable t) {
            return null;
        }
    }
}
