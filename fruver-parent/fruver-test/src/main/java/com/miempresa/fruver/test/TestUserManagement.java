package com.miempresa.fruver.test;

import com.miempresa.fruver.domain.model.Usuario;
import com.miempresa.fruver.domain.exceptions.*;
import com.miempresa.fruver.domain.repository.UsuarioRepository;
import com.miempresa.fruver.infra.config.DataSourceFactory;
import com.miempresa.fruver.infra.db.UsuarioRepositoryJdbc;
import com.miempresa.fruver.service.port.CreateUserRequest;
import com.miempresa.fruver.service.port.UpdateUserRequest;
import com.miempresa.fruver.service.usecase.*;
import com.miempresa.fruver.service.security.*;

import javax.sql.DataSource;
import java.util.*;
import java.util.stream.Collectors;

public class TestUserManagement {
    public static void main(String[] args) {
        DataSource ds = DataSourceFactory.getDataSource();
        UsuarioRepository repo = new UsuarioRepositoryJdbc(ds);

        Scanner sc = new Scanner(System.in);

        // 0) Mostrar al arranque TODOS los CAJEROS y SUPERVISORES agrupados
        ListUsersUseCase listUc = new ListUsersUseCase(repo);
        List<Usuario> all = listUc.execute(null);
        System.out.println("--- Cajeros registrados ---");
        all.stream()
                .filter(u -> u.getRol() == Usuario.Role.CAJERO)
                .forEach(u -> System.out.printf("%d: %s%n", u.getUsuarioId(), u.getNombre()));
        System.out.println("--- Supervisores registrados ---");
        all.stream()
                .filter(u -> u.getRol() == Usuario.Role.SUPERVISOR)
                .forEach(u -> System.out.printf("%d: %s%n", u.getUsuarioId(), u.getNombre()));

        try {
            // 1) Login ADMIN
            System.out.print("\nUsuario: "); String u = sc.nextLine().trim();
            System.out.print("Password: "); String p = sc.nextLine().trim();
            LoginUseCase loginUc = new LoginUseCase(repo);
            Usuario admin = loginUc.login(u, p);
            SecurityContext.setCurrentUser(admin);
            System.out.println("✔️  Bienvenido, " + admin.getNombre());

            // 2) Preparar casos de uso con RoleGuard para ADMIN
            RoleGuard<CreateUserRequest, Usuario> createGuard =
                    new RoleGuard<>(new CreateUserUseCase(repo), Usuario.Role.ADMIN);
            RoleGuard<UpdateUserRequest, Usuario> updateGuard =
                    new RoleGuard<>(new UpdateUserUseCase(repo), Usuario.Role.ADMIN);
            RoleGuard<Integer, Void> deleteGuard =
                    new RoleGuard<>(new DeleteUserUseCase(repo), Usuario.Role.ADMIN);

            // 3) Menú simplificado
            System.out.println("\nElige acción: 1=Crear  2=Actualizar  3=Eliminar");
            int opcion = Integer.parseInt(sc.nextLine().trim());
            switch (opcion) {
                case 1 -> {
                    System.out.print("Nombre nuevo: "); String nombre = sc.nextLine().trim();
                    System.out.print("Password: "); String pw = sc.nextLine().trim();
                    System.out.print("Rol (CAJERO/SUPERVISOR): ");
                    Usuario.Role rl = Usuario.Role.valueOf(sc.nextLine().trim());
                    Usuario creado = createGuard.execute(new CreateUserRequest(nombre, pw, rl));
                    System.out.println("-> Creado ID=" + creado.getUsuarioId());
                }
                case 2 -> {
                    System.out.print("ID a actualizar: "); int idAct = Integer.parseInt(sc.nextLine());
                    System.out.print("Nuevo nombre (ENTER para mantener): "); String nn = sc.nextLine();
                    System.out.print("Nueva pass (ENTER para mantener): "); String np = sc.nextLine();
                    System.out.print("Nuevo rol (ENTER para mantener): "); String ir = sc.nextLine().trim();
                    Usuario.Role nr = ir.isEmpty() ? null : Usuario.Role.valueOf(ir);
                    UpdateUserRequest updReq = new UpdateUserRequest(idAct, nn, np, nr);
                    Usuario upd = updateGuard.execute(updReq);
                    System.out.println("-> Actualizado: " + upd.getNombre() + " (rol=" + upd.getRol() + ")");
                }
                case 3 -> {
                    System.out.print("ID a eliminar: "); int idDel = Integer.parseInt(sc.nextLine());
                    deleteGuard.execute(idDel);
                    System.out.println("-> Usuario " + idDel + " eliminado.");
                }
                default -> System.out.println("Opción no válida");
            }
        } catch (AuthenticationException ex) {
            System.err.println("❌  No autenticado o sin permisos: " + ex.getMessage());
        } catch (DomainException|IllegalArgumentException ex) {
            System.err.println("❌  Error en datos: " + ex.getMessage());
        } finally {
            SecurityContext.clear();
            sc.close();
        }
    }
}
