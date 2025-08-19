package com.miempresa.fruver.ui;

import com.miempresa.fruver.ui.controller.SplashController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * MainApp: arranque de la UI.
 * - Muestra Splash (ejecuta ServiceLocator.initializeAndTestDb)
 * - Luego abre Login (inyecta LoginUseCase + ListUsersUseCase)
 * - Tras login exitoso abre la vista correspondiente al rol:
 *   - CAJERO   -> /fxml/CajeroView.fxml
 *   - SUPERVISOR -> /fxml/SupervisorView.fxml
 *   - ADMIN    -> /fxml/AdminView.fxml
 *
 * Cada controlador de rol debe exponer init(Usuario, Runnable) para inyección y logout callback.
 */
public class MainApp extends Application {
    private Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        this.primaryStage = stage;
        showSplashThenLogin();
    }

    private void showSplashThenLogin() throws Exception {
        FXMLLoader fx = new FXMLLoader(getClass().getResource("/fxml/SplashView.fxml"));
        Parent root = fx.load();
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setWidth(640);
        primaryStage.setHeight(420);
        primaryStage.setResizable(false);
        primaryStage.centerOnScreen();
        primaryStage.show();

        // SplashController ejecutará ServiceLocator.initializeAndTestDb(...) en su Task.
        SplashController splashCtrl = fx.getController();
        splashCtrl.setOnFinished(() -> Platform.runLater(() -> {
            try {
                openLogin();
            } catch (Exception e) {
                e.printStackTrace();
                showError("Error", "No se pudo abrir la pantalla de login.", e);
            }
        }));
    }

    private void openLogin() throws Exception {
        FXMLLoader fx = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
        Parent root = fx.load();
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setWidth(1000);
        primaryStage.setHeight(640);
        primaryStage.setResizable(true);
        primaryStage.centerOnScreen();

        // Inyectar usecases desde ServiceLocator (asegúrate init fue llamado)
        var ctrl = fx.getController();

        // El controller que usamos espera init(LoginUseCase, ListUsersUseCase, onSuccess)
        try {
            ctrl.getClass().getMethod("init",
                            com.miempresa.fruver.service.usecase.LoginUseCase.class,
                            com.miempresa.fruver.service.usecase.ListUsersUseCase.class,
                            Runnable.class)
                    .invoke(ctrl,
                            ServiceLocator.getLoginUseCase(),
                            ServiceLocator.getListUsersUseCase(),
                            (Runnable) () -> {
                                // Al autenticarse, abrimos la vista según el rol del usuario actual
                                var usuario = com.miempresa.fruver.service.security.SecurityContext.getCurrentUser();
                                Platform.runLater(() -> {
                                    try {
                                        openViewForRole(usuario);
                                    } catch (Exception ex) {
                                        ex.printStackTrace();
                                        showError("Error", "No se pudo cargar la vista para el rol: " +
                                                (usuario != null ? usuario.getRol() : "desconocido"), ex);
                                        // Intentamos volver al login como fallback
                                        try { openLogin(); } catch (Exception e) { e.printStackTrace(); }
                                    }
                                });
                            });
        } catch (NoSuchMethodException nsme) {
            throw new IllegalStateException("El controlador LoginController no tiene el método init(LoginUseCase,ListUsersUseCase,Runnable).", nsme);
        }
    }

    /**
     * Carga la vista correspondiente al rol del usuario. Cada controlador de vista de rol
     * debe exponer: init(Usuario usuario, Runnable onLogout)
     */
    private void openViewForRole(com.miempresa.fruver.domain.model.Usuario usuario) throws Exception {
        if (usuario == null) {
            // Seguridad: si no hay usuario, volvemos al login
            openLogin();
            return;
        }

        String fxmlPath;
        double width = 1024, height = 700;

        switch (usuario.getRol()) {
            case CAJERO:
                fxmlPath = "/fxml/CajeroView.fxml";
                width = 1024; height = 700;
                break;
            case SUPERVISOR:
                fxmlPath = "/fxml/SupervisorView.fxml";
                width = 1100; height = 740;
                break;
            case ADMIN:
                fxmlPath = "/fxml/AdminView.fxml";
                width = 1200; height = 800;
                break;
            default:
                throw new IllegalStateException("Rol no soportado: " + usuario.getRol());
        }

        FXMLLoader fx = new FXMLLoader(getClass().getResource(fxmlPath));
        Parent root = fx.load();
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setWidth(width);
        primaryStage.setHeight(height);
        primaryStage.setResizable(true);
        primaryStage.centerOnScreen();

        var ctrl = fx.getController();

        // Si el controlador no tiene init(Usuario,Runnable) lo intentamos detectar y seguir sin crash.
        try {
            ctrl.getClass().getMethod("init",
                            com.miempresa.fruver.domain.model.Usuario.class,
                            Runnable.class)
                    .invoke(ctrl, usuario, (Runnable) () -> {
                        // Logout callback: limpiar contexto y volver al login
                        com.miempresa.fruver.service.security.SecurityContext.clear();
                        Platform.runLater(() -> {
                            try {
                                openLogin();
                            } catch (Exception e) {
                                e.printStackTrace();
                                showError("Error", "No se pudo volver al login tras logout.", e);
                            }
                        });
                    });
        } catch (NoSuchMethodException nsme) {
            // No existe init(Usuario,Runnable) — lo notificamos pero no rompemos la app.
            System.err.println("Aviso: el controlador de " + fxmlPath + " no expone init(Usuario,Runnable).");
        }
    }

    private void showError(String title, String content, Throwable ex) {
        ex.printStackTrace();
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content + (ex != null ? "\n\nDetalles: " + ex.getMessage() : ""));
        a.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
