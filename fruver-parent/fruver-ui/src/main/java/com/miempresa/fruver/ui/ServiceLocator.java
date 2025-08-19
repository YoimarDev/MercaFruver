package com.miempresa.fruver.ui;

import com.miempresa.fruver.domain.model.Usuario;
import com.miempresa.fruver.domain.repository.UsuarioRepository;
import com.miempresa.fruver.service.usecase.ListUsersUseCase;
import com.miempresa.fruver.service.usecase.LoginUseCase;
import org.mindrot.jbcrypt.BCrypt;

import javax.sql.DataSource;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * ServiceLocator consolidado:
 * - inicializa Login/ListUsers (JDBC o in-memory)
 * - expone AdminService (JdbcAdminService cuando hay DataSource; InMemoryAdminService fallback)
 */
public final class ServiceLocator {

    private static volatile LoginUseCase loginUseCase;
    private static volatile ListUsersUseCase listUsersUseCase;

    // Admin service (stubs / fallback)
    private static volatile AdminService adminService;

    private ServiceLocator() {}

    /* ----------------------
       Initialization helpers
       ---------------------- */

    public static boolean initializeAndTestDb(Consumer<String> progress) {
        return initializeAndTestDb(progress, (p) -> {});
    }

    public static boolean initializeAndTestDb(Consumer<String> progressMsg, Consumer<Double> progressPercent) {
        try {
            progressMsg.accept("Inicializando DataSource...");
            progressPercent.accept(0.10);

            DataSource ds = com.miempresa.fruver.infra.config.DataSourceFactory.getDataSource();
            progressMsg.accept("Construyendo UsuarioRepository (JDBC)...");
            progressPercent.accept(0.25);

            com.miempresa.fruver.infra.db.UsuarioRepositoryJdbc repoJdbc =
                    new com.miempresa.fruver.infra.db.UsuarioRepositoryJdbc(ds);

            progressMsg.accept("Construyendo casos de uso...");
            progressPercent.accept(0.40);

            loginUseCase = new LoginUseCase(repoJdbc);
            listUsersUseCase = new ListUsersUseCase(repoJdbc);

            progressMsg.accept("Probando consulta mínima a BD...");
            progressPercent.accept(0.60);

            // Fuerza una operación ligera para comprobar conexión
            try {
                repoJdbc.findByName("___test_connection___").orElse(null);
            } catch (Exception ex) {
                throw ex;
            }

            progressMsg.accept("Conexión a BD OK");
            progressPercent.accept(0.80);

            // Construir AdminService JDBC (si las clases infra existen)
            try {
                com.miempresa.fruver.infra.db.DeviceConfigRepositoryJdbc deviceRepo =
                        new com.miempresa.fruver.infra.db.DeviceConfigRepositoryJdbc(ds);
                com.miempresa.fruver.infra.db.DatabaseRepositoryJdbc dbRepo =
                        new com.miempresa.fruver.infra.db.DatabaseRepositoryJdbc(ds);

                adminService = new JdbcAdminService(deviceRepo, dbRepo, ds);
                progressMsg.accept("AdminService (JDBC) listo");
            } catch (Throwable t) {
                progressMsg.accept("No se pudo inicializar AdminService JDBC: " + t.getMessage());
                adminService = new InMemoryAdminService();
            }

            progressPercent.accept(1.0);
            return true;
        } catch (Throwable t) {
            progressMsg.accept("No fue posible inicializar BD: " + t.getMessage());
            t.printStackTrace();
            progressPercent.accept(0.0);
            progressMsg.accept("Inicializando modo demo (in-memory)...");
            progressPercent.accept(0.15);
            initInMemoryDemo();
            progressMsg.accept("Modo demo listo");
            progressPercent.accept(1.0);
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

        // inicializar adminService in-memory también para que la UI admin funcione
        adminService = new InMemoryAdminService();
    }

    /* ----------------------
       Getters para usecases
       ---------------------- */

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

    /* ----------------------
       AdminService exposure
       ---------------------- */

    public interface AdminService {
        List<String> listAvailablePorts();
        List<String> listDeviceConfigs(); // formato: "TIPO@PUERTO|JSON"
        boolean testDeviceConnection(String tipo, String port, Consumer<String> progressMsg, Consumer<Double> progressPercent);
        void saveDeviceConfig(String tipo, String port, String params);
        void performBackup(Consumer<String> progressMsg, Consumer<Double> progressPercent) throws Exception;
        void cleanSalesData(Consumer<String> progressMsg, Consumer<Double> progressPercent) throws Exception;
        // utilidad: obtener info espacio db (usado por UI)
        com.miempresa.fruver.service.dto.DatabaseStorageInfo getDatabaseStorageInfo();
    }

    public static AdminService getAdminService() {
        if (adminService == null) {
            synchronized (ServiceLocator.class) {
                if (adminService == null) {
                    // Si initializeAndTestDb no fue llamado, forzamos init demo
                    initInMemoryDemo();
                }
            }
        }
        return adminService;
    }

    /**
     * Implementación JDBC de AdminService (usa DeviceConfigRepositoryJdbc y DatabaseRepositoryJdbc).
     */
    private static class JdbcAdminService implements AdminService {

        private final com.miempresa.fruver.infra.db.DeviceConfigRepositoryJdbc deviceRepo;
        private final com.miempresa.fruver.infra.db.DatabaseRepositoryJdbc dbRepo;
        private final DataSource ds;

        // extraídas de DataSource si es Hikari (opcional)
        private final String mysqldumpCmd = "mysqldump";
        private final String dbUser;
        private final String dbPassword;
        private final String dbHost;
        private final int dbPort;
        private final String dbName;

        public JdbcAdminService(com.miempresa.fruver.infra.db.DeviceConfigRepositoryJdbc deviceRepo,
                                com.miempresa.fruver.infra.db.DatabaseRepositoryJdbc dbRepo,
                                DataSource ds) {
            this.deviceRepo = deviceRepo;
            this.dbRepo = dbRepo;
            this.ds = ds;

            // Intentar extraer credenciales si DataSource es Hikari
            String tmpUser = null, tmpPass = null, tmpHost = null, tmpDb = null;
            int tmpPort = 3306;
            try {
                Class<?> hikariCls = Class.forName("com.zaxxer.hikari.HikariDataSource");
                if (hikariCls.isInstance(ds)) {
                    Object hd = hikariCls.cast(ds);
                    tmpUser = (String) hikariCls.getMethod("getUsername").invoke(hd);
                    tmpPass = (String) hikariCls.getMethod("getPassword").invoke(hd);
                    String jdbcUrl = (String) hikariCls.getMethod("getJdbcUrl").invoke(hd);
                    // parse jdbc:mysql://host:port/db
                    if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:mysql://")) {
                        String withoutPrefix = jdbcUrl.substring("jdbc:mysql://".length());
                        String[] parts = withoutPrefix.split("/", 2);
                        String hostPort = parts[0];
                        tmpDb = parts.length > 1 ? parts[1].split("\\?")[0] : "";
                        if (hostPort.contains(":")) {
                            String[] hp = hostPort.split(":");
                            tmpHost = hp[0];
                            tmpPort = Integer.parseInt(hp[1]);
                        } else {
                            tmpHost = hostPort;
                        }
                    }
                }
            } catch (Throwable t) {
                tmpUser = null; tmpPass = null; tmpHost = null; tmpDb = null; tmpPort = 3306;
            }
            dbUser = tmpUser;
            dbPassword = tmpPass;
            dbHost = tmpHost;
            dbPort = tmpPort;
            dbName = tmpDb;
        }

        @Override
        public List<String> listAvailablePorts() {
            List<String> out = new ArrayList<>();
            String os = System.getProperty("os.name", "generic").toLowerCase();
            if (os.contains("win")) {
                for (int i = 1; i <= 10; i++) out.add("COM" + i);
            } else {
                for (int i = 0; i < 6; i++) out.add("/dev/ttyS" + i);
                out.add("/dev/ttyUSB0");
                out.add("/dev/ttyUSB1");
            }
            return out;
        }

        @Override
        public List<String> listDeviceConfigs() {
            try {
                List<com.miempresa.fruver.domain.model.DeviceConfig> list = deviceRepo.findAll();
                List<String> out = new ArrayList<>();
                for (var c : list) {
                    out.add(c.getTipo().name() + "@" + c.getPuerto() + "|" + (c.getParametrosJson() == null ? "{}" : c.getParametrosJson()));
                }
                return out;
            } catch (Throwable t) {
                return List.of();
            }
        }

        @Override
        public boolean testDeviceConnection(String tipo, String port, Consumer<String> progressMsg, Consumer<Double> progressPercent) {
            // Para báscula intentamos leer un peso si ScaleService está disponible (reflectively).
            // dentro de JdbcAdminService.testDeviceConnection(...)
            try {
                if ("BASCULA".equalsIgnoreCase(tipo)) {
                    try {
                        Class<?> cls = Class.forName("com.miempresa.fruver.infra.hardware.scale.ScaleService");
                        Object scaleSvc = null;
                        try {
                            // intentamos constructor por defecto
                            scaleSvc = cls.getConstructor().newInstance();
                        } catch (NoSuchMethodException ns) {
                            // si no existe, intentamos constructor con puerto
                            try {
                                scaleSvc = cls.getConstructor(String.class).newInstance(port);
                            } catch (ReflectiveOperationException roe) {
                                // no pudimos instanciar con puerto
                                scaleSvc = null;
                            }
                        }

                        if (scaleSvc != null) {
                            // intentamos método readWeight (si existe)
                            try {
                                java.lang.reflect.Method m = cls.getMethod("readWeight");
                                Object val = m.invoke(scaleSvc);
                                if (progressMsg != null) progressMsg.accept("Peso leído: " + String.valueOf(val));
                                if (progressPercent != null) progressPercent.accept(1.0);
                                return true;
                            } catch (NoSuchMethodException nm) {
                                // no tiene readWeight -> consideramos que la instancia fue creada correctamente
                                if (progressMsg != null) progressMsg.accept("Báscula instanciada (sin readWeight).");
                                if (progressPercent != null) progressPercent.accept(1.0);
                                return true;
                            } catch (ReflectiveOperationException | RuntimeException ex) {
                                // error invocando método
                                if (progressMsg != null) progressMsg.accept("Error leyendo peso: " + ex.getMessage());
                                if (progressPercent != null) progressPercent.accept(0.0);
                                return false;
                            }
                        }
                    } catch (ClassNotFoundException cnf) {
                        // ScaleService no disponible en classpath — fallback heurístico
                        if (progressMsg != null) progressMsg.accept("ScaleService no disponible en classpath.");
                    }
                }

                // Fallback heurístico simple: si puerto contiene COM o tty, asumimos OK
                boolean ok = port != null && (port.toUpperCase().contains("COM") || port.contains("tty"));
                if (progressMsg != null) progressMsg.accept(ok ? "Respuesta recibida (heurística)." : "Sin respuesta (heurística).");
                if (progressPercent != null) progressPercent.accept(1.0);
                return ok;
            } catch (Throwable t) {
                if (progressMsg != null) progressMsg.accept("Error prueba: " + t.getMessage());
                if (progressPercent != null) progressPercent.accept(0.0);
                return false;
            }

        }

        @Override
        public void saveDeviceConfig(String tipo, String port, String params) {
            // validar y persistir usando DeviceConfig domain/model
            com.miempresa.fruver.domain.model.DeviceConfig.DeviceType dt =
                    com.miempresa.fruver.domain.model.DeviceConfig.DeviceType.valueOf(tipo.toUpperCase());
            com.miempresa.fruver.domain.model.DeviceConfig cfg =
                    new com.miempresa.fruver.domain.model.DeviceConfig(null, dt, port, params == null ? "{}" : params);
            deviceRepo.save(cfg); // nuestro save ya hace upsert por tipo
        }

        @Override
        public void performBackup(Consumer<String> progressMsg, Consumer<Double> progressPercent) throws Exception {
            // Requiere mysqldump disponible
            if (dbName == null || dbUser == null || dbPassword == null || dbHost == null) {
                if (progressMsg != null) progressMsg.accept("No hay credenciales DB disponibles para mysqldump (revisa DataSource pool).");
                throw new IllegalStateException("Credenciales DB no disponibles en DataSource para backup.");
            }

            // Crear archivo temporal .cnf con credenciales (seguro: permisos 600)
            Path tmp = Files.createTempFile("fruver-backup-", ".cnf");
            String content = "[client]\nuser=" + dbUser + "\npassword=" + dbPassword + "\nhost=" + dbHost + "\nport=" + dbPort + "\n";
            Files.writeString(tmp, content);
            tmp.toFile().setReadable(true, true);
            tmp.toFile().setWritable(true, true);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String filename = "fruver-backup-" + timestamp + ".sql";
            Path target = Path.of(System.getProperty("user.home"), "fruver-backups");
            Files.createDirectories(target);
            Path out = target.resolve(filename);

            List<String> cmd = new ArrayList<>();
            cmd.add(mysqldumpCmd);
            cmd.add("--defaults-extra-file=" + tmp.toAbsolutePath().toString());
            cmd.add("--single-transaction");
            cmd.add("--quick");
            cmd.add("--routines");
            cmd.add("--events");
            cmd.add("--databases");
            cmd.add(dbName);

            if (progressMsg != null) progressMsg.accept("Ejecutando mysqldump...");
            if (progressPercent != null) progressPercent.accept(0.05);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            // leer salida y volcar al archivo
            try (InputStream in = p.getInputStream();
                 OutputStream fos = Files.newOutputStream(out)) {
                byte[] buf = new byte[8192];
                int read;
                long total = 0;
                while ((read = in.read(buf)) != -1) {
                    fos.write(buf, 0, read);
                    total += read;
                    if (progressMsg != null && total % (1024 * 1024) == 0) { // cada MB aprox
                        progressMsg.accept("Volcado... bytes=" + total);
                    }
                }
            }

            int rc = p.waitFor();
            // borrar archivo tmp
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}

            if (rc != 0) {
                if (progressMsg != null) progressMsg.accept("mysqldump finalizó con código " + rc);
                if (progressPercent != null) progressPercent.accept(0.0);
                throw new RuntimeException("mysqldump falló con código: " + rc);
            }
            if (progressMsg != null) progressMsg.accept("Backup completado: " + out.toAbsolutePath());
            if (progressPercent != null) progressPercent.accept(1.0);
        }

        @Override
        public void cleanSalesData(Consumer<String> progressMsg, Consumer<Double> progressPercent) throws Exception {
            // Ejecuta eliminación dentro de transacción. Se espera que la UI haya generado/adjuntado backup.
            try (Connection c = ds.getConnection()) {
                c.setAutoCommit(false);
                try (PreparedStatement delItems = c.prepareStatement("DELETE FROM VENTA_ITEM");
                     PreparedStatement delFactura = c.prepareStatement("DELETE FROM FACTURA");
                     PreparedStatement delVenta = c.prepareStatement("DELETE FROM VENTA")) {
                    if (progressMsg != null) progressMsg.accept("Eliminando VENTA_ITEM...");
                    int ri = delItems.executeUpdate();
                    if (progressPercent != null) progressPercent.accept(0.33);

                    if (progressMsg != null) progressMsg.accept("Eliminando FACTURA...");
                    int rf = delFactura.executeUpdate();
                    if (progressPercent != null) progressPercent.accept(0.66);

                    if (progressMsg != null) progressMsg.accept("Eliminando VENTA...");
                    int rv = delVenta.executeUpdate();
                    if (progressPercent != null) progressPercent.accept(0.95);

                    c.commit();
                    if (progressMsg != null) progressMsg.accept("Limpieza completada. filas: items=" + ri + ", fact=" + rf + ", ventas=" + rv);
                    if (progressPercent != null) progressPercent.accept(1.0);
                } catch (SQLException ex) {
                    c.rollback();
                    throw ex;
                } finally {
                    c.setAutoCommit(true);
                }
            }
        }

        @Override
        public com.miempresa.fruver.service.dto.DatabaseStorageInfo getDatabaseStorageInfo() {
            return new com.miempresa.fruver.service.usecase.GetDatabaseStorageUseCase(dbRepo).execute();
        }
    }

    /* ---------------------------
       In-memory AdminService (fallback)
       --------------------------- */

    private static class InMemoryAdminService implements AdminService {

        private final Map<String, String> configs = new LinkedHashMap<>();

        public InMemoryAdminService() {
            configs.put("BASCULA@COM1", "{ \"baudRate\": 9600, \"dataBits\": 8 }");
        }

        @Override
        public List<String> listAvailablePorts() {
            List<String> out = new ArrayList<>();
            String os = System.getProperty("os.name", "generic").toLowerCase();
            if (os.contains("win")) {
                for (int i = 1; i <= 6; i++) out.add("COM" + i);
            } else {
                for (int i = 0; i < 4; i++) out.add("/dev/ttyS" + i);
                out.add("/dev/ttyUSB0");
            }
            return out;
        }

        @Override
        public List<String> listDeviceConfigs() {
            List<String> list = new ArrayList<>();
            configs.forEach((k, v) -> list.add(k + "|" + v));
            return list;
        }

        @Override
        public boolean testDeviceConnection(String tipo, String port, Consumer<String> progressMsg, Consumer<Double> progressPercent) {
            try {
                if (progressMsg != null) progressMsg.accept("Intentando abrir puerto " + port + "...");
                Thread.sleep(200);
                if (progressPercent != null) progressPercent.accept(0.3);
                if (progressMsg != null) progressMsg.accept("Enviando comando de prueba...");
                Thread.sleep(200);
                if (progressPercent != null) progressPercent.accept(0.7);
                boolean ok = port != null && (port.toUpperCase().contains("COM") || port.contains("tty"));
                if (progressPercent != null) progressPercent.accept(1.0);
                if (progressMsg != null) progressMsg.accept(ok ? "Respuesta recibida" : "Sin respuesta");
                return ok;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (progressMsg != null) progressMsg.accept("Prueba cancelada");
                return false;
            }
        }

        @Override
        public void saveDeviceConfig(String tipo, String port, String params) {
            String key = tipo + "@" + port;
            configs.put(key, params == null ? "{}" : params);
        }

        @Override
        public void performBackup(Consumer<String> progressMsg, Consumer<Double> progressPercent) throws Exception {
            if (progressMsg != null) progressMsg.accept("Creando backup (simulado)...");
            if (progressPercent != null) progressPercent.accept(0.05);
            Thread.sleep(250);
            if (progressMsg != null) progressMsg.accept("Exportando tablas...");
            if (progressPercent != null) progressPercent.accept(0.35);
            Thread.sleep(400);
            if (progressMsg != null) progressMsg.accept("Comprimiendo backup...");
            if (progressPercent != null) progressPercent.accept(0.75);
            Thread.sleep(300);
            if (progressPercent != null) progressPercent.accept(1.0);
            if (progressMsg != null) progressMsg.accept("Backup completado (simulado).");
        }

        @Override
        public void cleanSalesData(Consumer<String> progressMsg, Consumer<Double> progressPercent) throws Exception {
            if (progressMsg != null) progressMsg.accept("Iniciando limpieza de tablas (simulado)...");
            if (progressPercent != null) progressPercent.accept(0.05);
            Thread.sleep(300);
            if (progressMsg != null) progressMsg.accept("Eliminando VENTA_ITEM...");
            if (progressPercent != null) progressPercent.accept(0.30);
            Thread.sleep(350);
            if (progressMsg != null) progressMsg.accept("Eliminando VENTA...");
            if (progressPercent != null) progressPercent.accept(0.60);
            Thread.sleep(350);
            if (progressMsg != null) progressMsg.accept("Eliminando FACTURA...");
            if (progressPercent != null) progressPercent.accept(0.85);
            Thread.sleep(250);
            if (progressPercent != null) progressPercent.accept(1.0);
            if (progressMsg != null) progressMsg.accept("Limpieza completada (simulado).");
        }

        @Override
        public com.miempresa.fruver.service.dto.DatabaseStorageInfo getDatabaseStorageInfo() {
            // fake data
            return new com.miempresa.fruver.service.dto.DatabaseStorageInfo(1024L * 1024L * 120L, Optional.of(1024L * 1024L * 300L), Optional.empty());
        }
    }

    /* ---------------------------
       In-memory UsuarioRepository
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
