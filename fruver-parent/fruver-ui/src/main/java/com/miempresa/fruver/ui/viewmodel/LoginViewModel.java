package com.miempresa.fruver.ui.viewmodel;

import com.miempresa.fruver.domain.model.Usuario;
import com.miempresa.fruver.service.usecase.LoginUseCase;
import com.miempresa.fruver.service.security.SecurityContext;
import javafx.beans.property.*;
import javafx.concurrent.Task;

public class LoginViewModel {
    private final StringProperty username = new SimpleStringProperty();
    private final StringProperty password = new SimpleStringProperty();
    private final StringProperty statusMessage = new SimpleStringProperty();
    private final BooleanProperty busy = new SimpleBooleanProperty(false);

    private final LoginUseCase loginUseCase;

    public LoginViewModel(LoginUseCase loginUseCase) {
        this.loginUseCase = loginUseCase;
    }

    public StringProperty usernameProperty() { return username; }
    public StringProperty passwordProperty() { return password; }
    public StringProperty statusMessageProperty() { return statusMessage; }
    public BooleanProperty busyProperty() { return busy; }

    public void login(Runnable onSuccess) {
        busy.set(true);
        statusMessage.set("");
        Task<Usuario> t = new Task<>() {
            @Override
            protected Usuario call() throws Exception {
                return loginUseCase.login(username.get(), password.get());
            }
        };

        t.setOnSucceeded(evt -> {
            Usuario user = t.getValue();
            SecurityContext.setCurrentUser(user);
            busy.set(false);
            statusMessage.set("Autenticación OK");
            if (onSuccess != null) onSuccess.run();
        });

        t.setOnFailed(evt -> {
            busy.set(false);
            Throwable ex = t.getException();
            statusMessage.set(ex != null ? ex.getMessage() : "Error al autenticar");
            // limpiar password para seguridad y UX
            password.set("");
        });

        Thread th = new Thread(t, "login-thread");
        th.setDaemon(true);
        th.start();
    }
}
