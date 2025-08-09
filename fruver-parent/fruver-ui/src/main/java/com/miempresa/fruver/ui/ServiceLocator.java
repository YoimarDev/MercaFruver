package com.miempresa.fruver.ui;

import com.miempresa.fruver.domain.model.Usuario;
import com.miempresa.fruver.domain.repository.UsuarioRepository;
import com.miempresa.fruver.service.usecase.ListUsersUseCase;
import com.miempresa.fruver.service.usecase.LoginUseCase;
import org.mindrot.jbcrypt.BCrypt;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Locator simple para construir y proveer UseCases a la UI.
 * - Intenta inicializar usando fruver-infra (DataSourceFactory + UsuarioRepositoryJdbc).
 * - Si falla, cae a un stub en memoria (InMemoryUsuarioRepository) con usuarios demo.
 *
 * Asegúrate de que tu módulo fruver-ui tenga acceso a los artefactos fruver-domain y fruver-infra
 * en el classpath para usar la conexión real.
 */
public final class ServiceLocator {

    private static volatile LoginUseCase loginUseCase;
    private static volatile ListUsersUseCase listUsersUseCase;

    private ServiceLocator() {}

    /**
     * Intenta inicializar recursos y probar la BD.
     * Llama a progress.accept(msg) con mensajes intermedios.
     * Retorna true si todo OK (DB) o si inicializó el stub en memoria.
     */
    public static boolean initializeAndTestDb(Consumer<String> progress) {
        // Primero intentamos inicializar la versión real (si las clases están disponibles)
        try {
            progress.accept("Inicializando DataSource...");
            // Ajusta la ruta al DataSourceFactory según tu paquete infra (com.miempresa.fruver.infra.config.*)
            DataSource ds = com.miempresa.fruver.infra.config.DataSourceFactory.getDataSource();

            progress.accept("Construyendo UsuarioRepository (JDBC)...");
            com.miempresa.fruver.infra.db.UsuarioRepositoryJdbc repoJdbc =
                    new com.miempresa.fruver.infra.db.UsuarioRepositoryJdbc(ds);

            progress.accept("Construyendo casos de uso...");
            loginUseCase = new LoginUseCase(repoJdbc);
            listUsersUseCase = new ListUsersUseCase(repoJdbc);

            progress.accept("Probando consulta mínima a BD...");
            // Fuerza una operación ligera para comprobar conexión
            try {
                repoJdbc.findByName("___test_connection___").orElse(null);
            } catch (Exception ex) {
                // Si la consulta lanza excepción, lo tratamos como fallo de conexión
                throw ex;
            }

            progress.accept("Conexión a BD OK");
            return true;
        } catch (Throwable t) {
            // Si algo falla — clase no encontrada, NPE o error de conexión — hacemos fallback a stub.
            progress.accept("No fue posible inicializar BD: " + t.getMessage());
            t.printStackTrace();
            progress.accept("Inicializando modo demo (in-memory)...");
            initInMemoryDemo();
            progress.accept("Modo demo listo");
            return true;
        }
    }

    private static void initInMemoryDemo() {
        InMemoryUsuarioRepository inmem = new InMemoryUsuarioRepository();

        // crear usuarios demo (password plain: "1234")
        String pass = "1234";
        String hash = BCrypt.hashpw(pass, BCrypt.gensalt(10));
        Usuario cajero = new Usuario(null, "cajero", Usuario.Role.CAJERO, hash);
        Usuario supervisor = new Usuario(null, "supervisor", Usuario.Role.SUPERVISOR, hash);
        Usuario admin = new Usuario(null, "admin", Usuario.Role.ADMIN, hash);

        inmem.save(cajero);
        inmem.save(supervisor);
        inmem.save(admin);

        loginUseCase = new LoginUseCase(inmem);
        listUsersUseCase = new ListUsersUseCase(inmem);
    }

    public static LoginUseCase getLoginUseCase() {
        if (loginUseCase == null) {
            throw new IllegalStateException("ServiceLocator no inicializado. Llama a initializeAndTestDb primero.");
        }
        return loginUseCase;
    }

    public static ListUsersUseCase getListUsersUseCase() {
        if (listUsersUseCase == null) {
            throw new IllegalStateException("ServiceLocator no inicializado. Llama a initializeAndTestDb primero.");
        }
        return listUsersUseCase;
    }

    /* ---------------------------
       In-memory UsuarioRepository
       (implemeta mínimo del contrato usado por los UseCases)
       --------------------------- */
    private static class InMemoryUsuarioRepository implements UsuarioRepository {
        private final Map<Integer, Usuario> store = new ConcurrentHashMap<>();
        private final AtomicInteger idSeq = new AtomicInteger(1);

        @Override
        public Optional<Usuario> findByName(String name) {
            if (name == null) return Optional.empty();
            return store.values().stream()
                    .filter(u -> name.equalsIgnoreCase(u.getNombre()))
                    .findFirst();
        }

        @Override
        public java.util.List<Usuario> findAll() {
            return java.util.List.copyOf(store.values());
        }

        @Override
        public Optional<Usuario> findById(Integer id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Usuario save(Usuario u) {
            int id = idSeq.getAndIncrement();
            Usuario copy = new Usuario(id, u.getNombre(), u.getRol(), u.getPasswordHash());
            store.put(id, copy);
            return copy;
        }

        @Override
        public void delete(Integer id) {
            store.remove(id);
        }

        @Override
        public Usuario update(Usuario u) {
            if (u.getUsuarioId() == null || !store.containsKey(u.getUsuarioId())) {
                throw new IllegalArgumentException("Usuario no existe: " + u.getUsuarioId());
            }
            store.put(u.getUsuarioId(), u);
            return u;
        }
    }
}
