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
import java.util.List;
import java.util.Scanner;

/**
 * Test CLI para gestión de usuarios. Compatible con compiladores más restrictivos.
 */
public class TestUserManagement {
    public static void main(String[] args) {
        DataSource ds = DataSourceFactory.getDataSource();
        UsuarioRepository repo = new UsuarioRepositoryJdbc(ds);

        Scanner sc = new Scanner(System.in);

        ListUsersUseCase listUc = new ListUsersUseCase(repo);
        List<Usuario> all = listUc.execute(null);

        System.out.println("--- Cajeros registrados ---");
        for (Usuario u : all) {
            if (u.getRol() == Usuario.Role.CAJERO) {
                System.out.printf("%d: %s%n", u.getUsuarioId(), u.getNombre());
            }
        }

        System.out.println("--- Supervisores registrados ---");
        for (Usuario u : all) {
            if (u.getRol() == Usuario.Role.SUPERVISOR) {
                System.out.printf("%d: %s%n", u.getUsuarioId(), u.getNombre());
            }
        }

        try {
            System.out.print("\nUsuario: ");
            String u = sc.nextLine().trim();
            System.out.print("Password: ");
            String p = sc.nextLine().trim();
            LoginUseCase loginUc = new LoginUseCase(repo);
            Usuario admin = loginUc.login(u, p);
            SecurityContext.setCurrentUser(admin);
            System.out.println("✔️  Bienvenido, " + admin.getNombre());

            RoleGuard<CreateUserRequest, Usuario> createGuard =
                    new RoleGuard<CreateUserRequest, Usuario>(new CreateUserUseCase(repo), Usuario.Role.ADMIN);
            RoleGuard<UpdateUserRequest, Usuario> updateGuard =
                    new RoleGuard<UpdateUserRequest, Usuario>(new UpdateUserUseCase(repo), Usuario.Role.ADMIN);
            RoleGuard<Integer, Void> deleteGuard =
                    new RoleGuard<Integer, Void>(new DeleteUserUseCase(repo), Usuario.Role.ADMIN);

            System.out.println("\nElige acción: 1=Crear  2=Actualizar  3=Eliminar");
            int opcion = Integer.parseInt(sc.nextLine().trim());

            switch (opcion) {
                case 1: {
                    System.out.print("Nombre nuevo: ");
                    String nombre = sc.nextLine().trim();
                    System.out.print("Password: ");
                    String pw = sc.nextLine().trim();
                    System.out.print("Rol (CAJERO/SUPERVISOR): ");
                    String rlStr = sc.nextLine().trim().toUpperCase();
                    Usuario.Role rl = Usuario.Role.valueOf(rlStr);
                    Usuario creado = createGuard.execute(new CreateUserRequest(nombre, pw, rl));
                    System.out.println("-> Creado ID=" + creado.getUsuarioId());
                    break;
                }
                case 2: {
                    System.out.print("ID a actualizar: ");
                    int idAct = Integer.parseInt(sc.nextLine().trim());
                    System.out.print("Nuevo nombre (ENTER para mantener): ");
                    String nn = sc.nextLine();
                    System.out.print("Nueva pass (ENTER para mantener): ");
                    String np = sc.nextLine();
                    System.out.print("Nuevo rol (ENTER para mantener): ");
                    String ir = sc.nextLine().trim();
                    Usuario.Role nr = ir.isEmpty() ? null : Usuario.Role.valueOf(ir.toUpperCase());
                    UpdateUserRequest updReq = new UpdateUserRequest(idAct, nn.isBlank() ? null : nn, np.isBlank() ? null : np, nr);
                    Usuario upd = updateGuard.execute(updReq);
                    System.out.println("-> Actualizado: " + upd.getNombre() + " (rol=" + upd.getRol() + ")");
                    break;
                }
                case 3: {
                    System.out.print("ID a eliminar: ");
                    int idDel = Integer.parseInt(sc.nextLine().trim());
                    deleteGuard.execute(idDel);
                    System.out.println("-> Usuario " + idDel + " eliminado.");
                    break;
                }
                default:
                    System.out.println("Opción no válida");
            }

        } catch (AuthenticationException ex) {
            System.err.println("❌  No autenticado o sin permisos: " + ex.getMessage());
        } catch (DomainException ex) {
            System.err.println("❌  Error en datos (DomainException): " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            System.err.println("❌  Error en datos (IllegalArgumentException): " + ex.getMessage());
        } catch (Exception ex) {
            System.err.println("❌  Error inesperado: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            SecurityContext.clear();
            sc.close();
            System.out.println("TestUserManagement finalizado.");
        }
    }
}
