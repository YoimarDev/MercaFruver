package com.miempresa.fruver.ui;

import com.miempresa.fruver.domain.model.Usuario;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class RoleController {
    @FXML private Label lblUser;
    @FXML private Label lblWelcome;
    @FXML private Button btnLogout;

    private Runnable onLogout;

    public void init(Usuario user, Runnable onLogout) {
        this.onLogout = onLogout;
        lblUser.setText(user.getNombre() + " (" + user.getRol() + ")");
        lblWelcome.setText("Bienvenido, " + user.getNombre() + " — Vista: " + user.getRol());
        btnLogout.setOnAction(e -> {
            if (this.onLogout != null) this.onLogout.run();
        });
    }
}
