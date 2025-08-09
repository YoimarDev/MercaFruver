package com.miempresa.fruver.test;

import com.miempresa.fruver.domain.model.Usuario;
import com.miempresa.fruver.domain.exceptions.AuthenticationException;
import com.miempresa.fruver.domain.repository.UsuarioRepository;
import com.miempresa.fruver.infra.config.DataSourceFactory;
import com.miempresa.fruver.infra.db.UsuarioRepositoryJdbc;
import com.miempresa.fruver.service.usecase.LoginUseCase;
import com.miempresa.fruver.service.security.SecurityContext;

import javax.sql.DataSource;
import java.util.Scanner;

public class TestLogin {
    public static void main(String[] args) {
        // 1) Inicializar DataSource y repositorio
        DataSource ds = DataSourceFactory.getDataSource();
        UsuarioRepository userRepo = new UsuarioRepositoryJdbc(ds);

        // 2) Crear caso de uso de login
        LoginUseCase loginUc = new LoginUseCase(userRepo);

        Scanner scanner = new Scanner(System.in);
        try {
            // 3) Leer credenciales
            System.out.print("Usuario: ");
            String username = scanner.nextLine().trim();
            System.out.print("Password: ");
            String password = scanner.nextLine().trim();

            // 4) Intentar autenticación
            Usuario user = loginUc.login(username, password);

            // 5) Guardar en contexto (opcional, para próximos casos de uso)
            SecurityContext.setCurrentUser(user);

            System.out.println("✔️  Login exitoso. Bienvenido, " + user.getNombre()
                    + " (rol=" + user.getRol() + ")");
        } catch (AuthenticationException ex) {
            System.err.println("❌  Login fallido: " + ex.getMessage());
        } finally {
            // 6) Limpieza
            SecurityContext.clear();
            scanner.close();
        }
    }
}
