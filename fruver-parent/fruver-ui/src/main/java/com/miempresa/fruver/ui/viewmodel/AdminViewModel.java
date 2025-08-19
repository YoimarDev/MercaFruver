package com.miempresa.fruver.ui.viewmodel;

import com.miempresa.fruver.ui.ServiceLocator;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableListBase;

import java.util.List;

/**
 * ViewModel para AdminController
 * - mantiene estado en properties y expone operaciones síncronas usadas por el controller.
 * - evita duplicidad de métodos y expone ListProperty para bind con controles JavaFX.
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
        this.adminService = adminService;
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
    public ObservableList<String> getAvailablePorts() { return availablePorts.get(); }
    public ObservableList<String> getConfigList() { return configList.get(); }

    /* ---------------------
       Utility / actions
       --------------------- */

    /**
     * Descubre puertos usando adminService y actualiza la lista observable.
     * Devuelve la lista detectada (útil para uso inmediato por controller).
     */
    public List<String> discoverPorts() {
        List<String> ports = adminService.listAvailablePorts();
        // Actualizar observable list (mantener la instancia para que los bindings sigan funcionando)
        availablePorts.get().setAll(ports);
        return ports;
    }

    /**
     * Refresca la lista de configuraciones desde el servicio.
     * Formato esperado por UI: "TIPO@PUERTO|JSON"
     */
    public void refreshConfigs() {
        setBusy(true);
        try {
            List<String> confs = adminService.listDeviceConfigs();
            configList.get().setAll(confs);
            setStatus("Configuraciones cargadas: " + confs.size());
        } finally {
            setBusy(false);
        }
    }

    /**
     * Carga una representación de configuración en los campos editables del VM.
     * Formato esperado: "TIPO@PUERTO|PARAMS_JSON"
     */
    public void loadConfigToEditor(String repr) {
        if (repr == null) return;
        String[] parts = repr.split("\\|", 2);
        String left = parts.length > 0 ? parts[0] : repr;
        String paramsText = parts.length > 1 ? parts[1] : "{}";
        String[] leftParts = left.split("@", 2);
        String tipo = leftParts.length > 0 ? leftParts[0] : "BASCULA";
        String puerto = leftParts.length > 1 ? leftParts[1] : "";
        type.set(tipo);
        port.set(puerto);
        params.set(paramsText);
    }

    /* ---------------------
       Small setters used by controller
       --------------------- */

    public void setStatus(String s) { statusMessage.set(s); }
    public void setCleanupMessage(String s) { cleanupMessage.set(s); }
    public void setBusy(boolean b) { busy.set(b); }
}
