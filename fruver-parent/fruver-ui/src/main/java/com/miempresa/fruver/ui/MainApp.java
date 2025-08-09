package com.miempresa.fruver.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * MainApp: arranque de la UI.
 * - Muestra Splash (ejecuta ServiceLocator.initializeAndTestDb)
 * - Luego abre Login (inyecta LoginUseCase + ListUsersUseCase)
 * - Tras login exitoso abre RoleView (placeholder)
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
                // mostrar mensaje / fallback
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
            ctrl.getClass().getMethod("init", com.miempresa.fruver.service.usecase.LoginUseCase.class,
                            com.miempresa.fruver.service.usecase.ListUsersUseCase.class, Runnable.class)
                    .invoke(ctrl, ServiceLocator.getLoginUseCase(), ServiceLocator.getListUsersUseCase(), (Runnable) () -> {
                        try { openRoleView(); } catch (Exception ex) { ex.printStackTrace(); }
                    });
        } catch (NoSuchMethodException nsme) {
            throw new IllegalStateException("El controlador LoginController no tiene el método init(LoginUseCase,ListUsersUseCase,Runnable).", nsme);
        }
    }

    private void openRoleView() throws Exception {
        FXMLLoader fx = new FXMLLoader(getClass().getResource("/fxml/RoleView.fxml"));
        Parent root = fx.load();
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setWidth(1024);
        primaryStage.setHeight(700);
        primaryStage.setResizable(true);
        primaryStage.centerOnScreen();

        var ctrl = fx.getController();
        // RoleController.init(Usuario, Runnable) — usamos reflexion para no forzar dependencia exacta
        try {
            var usuario = com.miempresa.fruver.service.security.SecurityContext.getCurrentUser();
            ctrl.getClass().getMethod("init", com.miempresa.fruver.domain.model.Usuario.class, Runnable.class)
                    .invoke(ctrl, usuario, (Runnable) () -> {
                        // logout -> clear context and go to login
                        com.miempresa.fruver.service.security.SecurityContext.clear();
                        Platform.runLater(() -> {
                            try { openLogin(); } catch (Exception e) { e.printStackTrace(); }
                        });
                    });
        } catch (NoSuchMethodException nsme) {
            throw new IllegalStateException("RoleController no tiene init(Usuario,Runnable).", nsme);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
