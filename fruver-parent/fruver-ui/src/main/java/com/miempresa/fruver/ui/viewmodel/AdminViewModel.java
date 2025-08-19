package com.miempresa.fruver.ui.viewmodel;

import com.miempresa.fruver.domain.model.Usuario;
import com.miempresa.fruver.service.port.CreateUserRequest;
import com.miempresa.fruver.service.port.UpdateUserRequest;
import com.miempresa.fruver.service.usecase.CreateUserUseCase;
import com.miempresa.fruver.service.usecase.DeleteUserUseCase;
import com.miempresa.fruver.service.usecase.ListUsersUseCase;
import com.miempresa.fruver.service.usecase.UpdateUserUseCase;
import com.miempresa.fruver.ui.ServiceLocator;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * ViewModel para AdminController
 * - Actualizaciones de propiedades en FX thread (safe)
 * - expone operaciones para configuración de dispositivos y gestión de usuarios.
 *
 * Mejoras:
 * - lastNotificationProperty: notifica eventos de éxito/fracaso para mostrar alertas desde el controller.
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

    // Usuarios
    private final ListProperty<Usuario> users = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final ObjectProperty<Usuario> selectedUser = new SimpleObjectProperty<>();
    private final StringProperty userStatus = new SimpleStringProperty();

    // Indicadores de dispositivos
    private final StringProperty scaleStatus = new SimpleStringProperty("Desconocido");
    private final StringProperty readerStatus = new SimpleStringProperty("Desconocido");

    // Notificaciones (para que Controller muestre popups)
    private final StringProperty lastNotification = new SimpleStringProperty();

    private final ServiceLocator.AdminService adminService;

    // usecases (pueden ser nulos en modo in-memory)
    private final ListUsersUseCase listUsersUseCase;
    private final CreateUserUseCase createUserUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final DeleteUserUseCase deleteUserUseCase;

    public AdminViewModel(ServiceLocator.AdminService adminService) {
        this.adminService = Objects.requireNonNull(adminService);

        // intentar obtener usecases desde ServiceLocator (pueden lanzar si no inicializado)
        ListUsersUseCase lu = null;
        CreateUserUseCase cu = null;
        UpdateUserUseCase uu = null;
        DeleteUserUseCase du = null;
        try { lu = ServiceLocator.getListUsersUseCase(); } catch (Throwable ignored) {}
        try { cu = ServiceLocator.getCreateUserUseCase(); } catch (Throwable ignored) {}
        try { uu = ServiceLocator.getUpdateUserUseCase(); } catch (Throwable ignored) {}
        try { du = ServiceLocator.getDeleteUserUseCase(); } catch (Throwable ignored) {}

        this.listUsersUseCase = lu;
        this.createUserUseCase = cu;
        this.updateUserUseCase = uu;
        this.deleteUserUseCase = du;
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

    // Usuarios properties
    public ListProperty<Usuario> usersProperty() { return users; }
    public ObjectProperty<Usuario> selectedUserProperty() { return selectedUser; }
    public StringProperty userStatusProperty() { return userStatus; }

    // Indicadores
    public StringProperty scaleStatusProperty() { return scaleStatus; }
    public StringProperty readerStatusProperty() { return readerStatus; }

    // Notificaciones para Controller
    public StringProperty lastNotificationProperty() { return lastNotification; }

    /* ---------------------
       Simple getters
       --------------------- */
    public javafx.collections.ObservableList<String> getAvailablePorts() { return availablePorts.get(); }
    public javafx.collections.ObservableList<String> getConfigList() { return configList.get(); }
    public javafx.collections.ObservableList<Usuario> getUsers() { return users.get(); }
    public Optional<Usuario> getSelectedUser() { return Optional.ofNullable(selectedUser.get()); }

    /* ---------------------
       Utility / actions
       --------------------- */

    public List<String> discoverPorts() {
        List<String> ports = adminService.listAvailablePorts();
        Platform.runLater(() -> {
            try {
                availablePorts.get().setAll(ports);
            } catch (Exception ex) {
                availablePorts.set(FXCollections.observableArrayList(ports));
            }
        });
        return ports;
    }

    public void refreshConfigs() {
        setBusy(true);
        try {
            List<String> confs = adminService.listDeviceConfigs();
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
            Platform.runLater(() -> {
                setStatus("Error cargando configuraciones: " + t.getMessage());
                setBusy(false);
            });
        }
    }

    public void loadConfigToEditor(String repr) {
        if (repr == null) return;
        String[] parts = repr.split("\\|", 2);
        String left = parts.length > 0 ? parts[0] : repr;
        String paramsText = parts.length > 1 ? parts[1] : "{}";
        String[] leftParts = left.split("@", 2);
        String tipo = leftParts.length > 0 ? leftParts[0] : "BASCULA";
        String puerto = leftParts.length > 1 ? leftParts[1] : "";
        Platform.runLater(() -> {
            type.set(tipo);
            port.set(puerto);
            params.set(paramsText);
        });
    }

    /* ---------------------
       User management (calls to usecases)
       --------------------- */

    /** Carga la lista de usuarios desde el usecase (background). */
    public void loadUsers() {
        setBusy(true);
        new Thread(() -> {
            try {
                if (listUsersUseCase == null) {
                    Platform.runLater(() -> {
                        userStatus.set("ListUsersUseCase no disponible (modo demo?).");
                        users.get().clear();
                        setBusy(false);
                    });
                    return;
                }
                // tu implementación de test usa listUc.execute(null)
                List<Usuario> list = listUsersUseCase.execute(null);
                Platform.runLater(() -> {
                    users.get().setAll(list);
                    userStatus.set("Usuarios cargados: " + list.size());
                    setBusy(false);
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    userStatus.set("Error cargando usuarios: " + t.getMessage());
                    setBusy(false);
                });
            }
        }, "admin-load-users").start();
    }

    /** Crear usuario (background). */
    public void createUser(String nombre, Usuario.Role rol, String password) {
        setBusy(true);
        new Thread(() -> {
            try {
                if (createUserUseCase == null) {
                    Platform.runLater(() -> {
                        userStatus.set("CreateUserUseCase no disponible.");
                        setBusy(false);
                    });
                    return;
                }
                // CreateUserUseCase espera CreateUserRequest según tu TestUserManagement
                CreateUserRequest req = new CreateUserRequest(nombre, password, rol);
                var created = createUserUseCase.execute(req);
                Platform.runLater(() -> {
                    String msg = "Usuario creado: " + created.getNombre() + " (id=" + created.getUsuarioId() + ")";
                    // notificar controller para popup
                    lastNotification.set(msg);
                    // actualizar lista y status
                    loadUsers();
                    setBusy(false);
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    lastNotification.set("Error creando usuario: " + t.getMessage());
                    userStatus.set("Error creando usuario");
                    setBusy(false);
                });
            }
        }, "admin-create-user").start();
    }

    /** Actualizar usuario (background). */
    public void updateUser(Integer usuarioId, String nombre, Usuario.Role rol, String password) {
        setBusy(true);
        new Thread(() -> {
            try {
                if (updateUserUseCase == null) {
                    Platform.runLater(() -> {
                        userStatus.set("UpdateUserUseCase no disponible.");
                        setBusy(false);
                    });
                    return;
                }
                // UpdateUserRequest(id, nombre|null, password|null, rol|null)
                UpdateUserRequest req = new UpdateUserRequest(usuarioId,
                        (nombre == null || nombre.isBlank()) ? null : nombre,
                        (password == null || password.isBlank()) ? null : password,
                        rol);
                var updated = updateUserUseCase.execute(req);
                Platform.runLater(() -> {
                    String msg = "Usuario actualizado: " + updated.getNombre() + " (id=" + updated.getUsuarioId() + ")";
                    lastNotification.set(msg);
                    loadUsers();
                    setBusy(false);
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    lastNotification.set("Error actualizando usuario: " + t.getMessage());
                    userStatus.set("Error actualizando usuario");
                    setBusy(false);
                });
            }
        }, "admin-update-user").start();
    }

    /** Eliminar usuario (background). */
    public void deleteUser(Integer usuarioId) {
        setBusy(true);
        new Thread(() -> {
            try {
                if (deleteUserUseCase == null) {
                    Platform.runLater(() -> {
                        userStatus.set("DeleteUserUseCase no disponible.");
                        setBusy(false);
                    });
                    return;
                }
                deleteUserUseCase.execute(usuarioId);
                Platform.runLater(() -> {
                    String msg = "Usuario eliminado: id=" + usuarioId;
                    lastNotification.set(msg);
                    loadUsers();
                    setBusy(false);
                });
            } catch (Throwable t) {
                Platform.runLater(() -> {
                    lastNotification.set("Error eliminando usuario: " + t.getMessage());
                    userStatus.set("Error eliminando usuario");
                    setBusy(false);
                });
            }
        }, "admin-delete-user").start();
    }

    /* --------------------- small setters --------------------- */
    public void setStatus(String s) { Platform.runLater(() -> statusMessage.set(s)); }
    public void setCleanupMessage(String s) { Platform.runLater(() -> cleanupMessage.set(s)); }
    public void setBusy(boolean b) { Platform.runLater(() -> busy.set(b)); }

    /* ----------------- device indicator helpers ----------------- */
    public void setScaleStatus(String s, boolean connected) { Platform.runLater(() -> scaleStatus.set(s)); }
    public void setReaderStatus(String s, boolean connected) { Platform.runLater(() -> readerStatus.set(s)); }

    public com.miempresa.fruver.service.port.DatabaseStorageInfo getDatabaseStorageInfo() {
        try {
            return adminService.getDatabaseStorageInfo();
        } catch (Throwable t) {
            return null;
        }
    }
}
