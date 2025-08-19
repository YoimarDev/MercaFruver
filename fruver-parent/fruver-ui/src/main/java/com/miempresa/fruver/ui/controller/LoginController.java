package com.miempresa.fruver.ui.controller;

import com.miempresa.fruver.domain.model.Usuario;
import com.miempresa.fruver.service.usecase.ListUsersUseCase;
import com.miempresa.fruver.service.usecase.LoginUseCase;
import com.miempresa.fruver.ui.viewmodel.LoginViewModel;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;

import java.util.List;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class LoginController {

    @FXML private ListView<Usuario> lvUsers;
    @FXML private ChoiceBox<String> choiceRoleFilter;
    @FXML private ImageView imgAvatar;
    @FXML private Label lblSelectedName;
    @FXML private Label lblSelectedRole;
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private Button btnLogin;
    @FXML private Button btnClearUser;
    @FXML private Label lblStatus;

    private LoginViewModel vm;
    private ListUsersUseCase listUsersUseCase;
    private ObservableList<Usuario> allUsers = FXCollections.observableArrayList();
    private final Preferences prefs = Preferences.userNodeForPackage(LoginController.class);
    private static final String PREF_LAST_USER = "lastUser";

    public void init(LoginUseCase loginUseCase, ListUsersUseCase listUsersUseCase, Runnable onLoginSuccess) {
        this.listUsersUseCase = listUsersUseCase;
        this.vm = new LoginViewModel(loginUseCase);

        // Bindings
        txtUsername.textProperty().bindBidirectional(vm.usernameProperty());
        txtPassword.textProperty().bindBidirectional(vm.passwordProperty());
        lblStatus.textProperty().bind(vm.statusMessageProperty());
        btnLogin.disableProperty().bind(vm.busyProperty());

        btnLogin.setOnAction(e -> vm.login(onLoginSuccess));
        btnClearUser.setOnAction(e -> clearSelection());

        // 🎨 Mejor aspecto del botón de login (puedes moverlo a CSS si prefieres)
        btnLogin.setStyle(
                "-fx-background-color: linear-gradient(#4CAF50, #388E3C);" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 8 16;" +
                        "-fx-font-size: 14px;"
        );

        // Cambiar texto del botón cuando está ocupado
        vm.busyProperty().addListener((obs, oldV, newV) -> {
            Platform.runLater(() -> {
                btnLogin.setText(newV ? "Entrando..." : "Entrar");
            });
        });

        // ENTER en txtPassword -> enviar login
        txtPassword.setOnAction(e -> {
            if (!vm.busyProperty().get()) {
                btnLogin.fire();
            }
        });

        // ENTER en txtUsername -> mover foco a password (útil si algún día es editable)
        txtUsername.setOnAction(e -> txtPassword.requestFocus());

        setupListView();
        setupRoleFilter();

        loadUsersInBackground();
    }

    private void setupListView() {
        lvUsers.setCellFactory(list -> new ListCell<Usuario>() {
            private final HBox root = new HBox(12);
            private final ImageView thumb = new ImageView();
            private final VBox texts = new VBox(2);
            private final Label nameLbl = new Label();
            private final Label roleLbl = new Label();

            {
                // Clases CSS esperadas por styles.css
                getStyleClass().add("user-tile-cell");      // aplicado a la ListCell
                root.getStyleClass().add("user-tile-root"); // aplicado al contenedor interno
                nameLbl.getStyleClass().add("user-tile-name");
                roleLbl.getStyleClass().add("user-tile-role");

                // Configuración visual básica (el resto lo controla CSS)
                thumb.setFitWidth(40);
                thumb.setFitHeight(40);
                thumb.setPreserveRatio(true);

                // Redondear avatar (clip)
                Circle clip = new Circle(20, 20, 20);
                thumb.setClip(clip);

                texts.getChildren().addAll(nameLbl, roleLbl);
                texts.setFillWidth(true);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                root.getChildren().addAll(thumb, texts, spacer);
                root.setMinHeight(64);

                // Mantener celda táctil consistente
                setPrefHeight(64);
            }

            @Override
            protected void updateItem(Usuario item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // Texto
                    nameLbl.setText(item.getNombre());
                    roleLbl.setText(item.getRol() != null ? item.getRol().name() : "");

                    // Cargar placeholder (si hay imagen real en el futuro, cámbialo aquí)
                    try (java.io.InputStream is = LoginController.class.getResourceAsStream("/images/user-placeholder.png")) {
                        if (is != null) {
                            thumb.setImage(new Image(is));
                        } else {
                            thumb.setImage(null);
                        }
                    } catch (Exception ex) {
                        thumb.setImage(null);
                    }

                    setText(null);      // evitamos el texto por defecto
                    setGraphic(root);   // asignamos nuestro tile
                }
            }
        });

        // Mantener el comportamiento original: selección y doble click
        lvUsers.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) onUserSelected(newV);
        });

        lvUsers.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Usuario sel = lvUsers.getSelectionModel().getSelectedItem();
                if (sel != null) txtPassword.requestFocus();
            }
        });

        // Altura fija para consistencia y mejor rendimiento táctil
        lvUsers.setFixedCellSize(88);
    }

    private void setupRoleFilter() {
        choiceRoleFilter.getItems().addAll("Todos", "CAJERO", "SUPERVISOR");
        choiceRoleFilter.setValue("Todos");
        choiceRoleFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            applyRoleFilter(newV);
        });
    }

    private void applyRoleFilter(String roleFilter) {
        if (roleFilter == null || roleFilter.equals("Todos")) {
            lvUsers.setItems(allUsers);
            return;
        }
        List<Usuario> filtered = allUsers.stream()
                .filter(u -> u.getRol().name().equalsIgnoreCase(roleFilter))
                .collect(Collectors.toList());
        lvUsers.setItems(FXCollections.observableArrayList(filtered));
    }

    private void onUserSelected(Usuario user) {
        vm.usernameProperty().set(user.getNombre());
        lblSelectedName.setText(user.getNombre());
        lblSelectedRole.setText(user.getRol().name());
        try {
            Image img = new Image(getClass().getResourceAsStream("/images/user-placeholder.png"));
            imgAvatar.setImage(img);
        } catch (Exception ignored) {}
        Platform.runLater(() -> txtPassword.requestFocus());
        prefs.put(PREF_LAST_USER, user.getNombre());
    }

    private void clearSelection() {
        lvUsers.getSelectionModel().clearSelection();
        vm.usernameProperty().set("");
        lblSelectedName.setText("");
        lblSelectedRole.setText("");
        txtPassword.clear();
        prefs.remove(PREF_LAST_USER);
    }

    private void loadUsersInBackground() {
        Task<List<Usuario>> t = new Task<>() {
            @Override
            protected List<Usuario> call() throws Exception {
                return listUsersUseCase.execute(null);
            }
        };

        t.setOnSucceeded(e -> {
            List<Usuario> users = t.getValue();
            if (users == null) users = List.of();
            allUsers.setAll(users);
            lvUsers.setItems(allUsers);
            String last = prefs.get(PREF_LAST_USER, null);
            if (last != null) {
                for (Usuario u : allUsers) {
                    if (u.getNombre().equalsIgnoreCase(last)) {
                        lvUsers.getSelectionModel().select(u);
                        lvUsers.scrollTo(u);
                        break;
                    }
                }
            }
        });

        t.setOnFailed(e -> {
            lblStatus.setText("Error cargando usuarios: " + t.getException().getMessage());
        });

        Thread th = new Thread(t, "load-users");
        th.setDaemon(true);
        th.start();
    }
}
