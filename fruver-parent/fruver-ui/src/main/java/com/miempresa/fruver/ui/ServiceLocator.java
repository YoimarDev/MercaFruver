package com.miempresa.fruver.ui;

import com.miempresa.fruver.domain.model.Usuario;
import com.miempresa.fruver.domain.repository.UsuarioRepository;
import com.miempresa.fruver.domain.model.DeviceConfig;
import com.miempresa.fruver.domain.model.DeviceConfig.DeviceType;
import com.miempresa.fruver.service.port.DatabaseStorageInfo;
import com.miempresa.fruver.service.usecase.GetDatabaseStorageUseCase;
import com.miempresa.fruver.service.usecase.ListUsersUseCase;
import com.miempresa.fruver.service.usecase.LoginUseCase;
import com.miempresa.fruver.service.usecase.SaveDeviceConfigUseCase;

import com.miempresa.fruver.service.usecase.CreateUserUseCase;
import com.miempresa.fruver.service.usecase.UpdateUserUseCase;
import com.miempresa.fruver.service.usecase.DeleteUserUseCase;

// --- productos ---
import com.miempresa.fruver.domain.model.Producto;
import com.miempresa.fruver.domain.repository.ProductoRepository;
import com.miempresa.fruver.service.usecase.CreateProductUseCase;
import com.miempresa.fruver.service.usecase.UpdateProductUseCase;
import com.miempresa.fruver.service.usecase.DeleteProductUseCase;
import com.miempresa.fruver.service.usecase.ListProductsUseCase;

import org.mindrot.jbcrypt.BCrypt;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * ServiceLocator consolidado:
 * - inicializa Login/ListUsers/Create/Update/Delete (JDBC o in-memory)
 * - expone AdminService (JdbcAdminService cuando hay DataSource; InMemoryAdminService fallback)
 *
 * Nota: esta versión NO implementa backup (performBackup) por petición del cliente.
 */
public final class ServiceLocator {

    private static volatile LoginUseCase loginUseCase;
    private static volatile ListUsersUseCase listUsersUseCase;

    // user management usecases
    private static volatile CreateUserUseCase createUserUseCase;
    private static volatile UpdateUserUseCase updateUserUseCase;
    private static volatile DeleteUserUseCase deleteUserUseCase;

    // product management usecases
    private static volatile ListProductsUseCase listProductsUseCase;
    private static volatile CreateProductUseCase createProductUseCase;
    private static volatile UpdateProductUseCase updateProductUseCase;
    private static volatile DeleteProductUseCase deleteProductUseCase;

    // Admin service (stubs / fallback)
    private static volatile AdminService adminService;

    // Flag para indicar si se está en modo demo (in-memory)
    private static volatile boolean usingInMemoryAdminService = false;

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

            // Usecases core de autenticación / usuarios
            loginUseCase = new LoginUseCase(repoJdbc);
            listUsersUseCase = new ListUsersUseCase(repoJdbc);
            createUserUseCase = new CreateUserUseCase(repoJdbc);
            updateUserUseCase = new UpdateUserUseCase(repoJdbc);
            deleteUserUseCase = new DeleteUserUseCase(repoJdbc);

            // --- ProductoRepository (JDBC) y casos de uso de productos ---
            com.miempresa.fruver.infra.db.ProductoRepositoryJdbc prodRepoJdbc =
                    new com.miempresa.fruver.infra.db.ProductoRepositoryJdbc(ds);

            listProductsUseCase = new ListProductsUseCase(prodRepoJdbc);
            createProductUseCase = new CreateProductUseCase(prodRepoJdbc);
            updateProductUseCase = new UpdateProductUseCase(prodRepoJdbc);
            deleteProductUseCase = new DeleteProductUseCase(prodRepoJdbc);

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

                // DatabaseRepositoryJdbc puede ser opcional: si no existe, se intenta crear un JdbcAdminService "parcial".
                com.miempresa.fruver.infra.db.DatabaseRepositoryJdbc dbRepo = null;
                try {
                    dbRepo = new com.miempresa.fruver.infra.db.DatabaseRepositoryJdbc(ds);
                } catch (Throwable dbEx) {
                    // log y continuar; algunas funcionalidades de AdminService estarán limitadas (e.g. getDatabaseStorageInfo)
                    System.err.println("[ServiceLocator] DatabaseRepositoryJdbc no disponible: " + dbEx.getClass().getSimpleName() + " - " + dbEx.getMessage());
                }

                adminService = new JdbcAdminService(deviceRepo, dbRepo, ds);
                usingInMemoryAdminService = false;
                progressMsg.accept("AdminService (JDBC) listo");
                progressMsg.accept("AdminService: JDBC inicializado correctamente.");
                System.out.println("[ServiceLocator] AdminService inicializado: " + adminService.getClass().getName());
            } catch (Throwable t) {
                // Mostrar traza y usar fallback in-memory
                System.err.println("[ServiceLocator] No se pudo inicializar AdminService JDBC: " + t.getClass().getName() + " - " + t.getMessage());
                System.err.println("[ServiceLocator] Recomendación: compila e instala fruver-infra en el local repo (mvn clean install desde el root) y revisa que fruver-ui dependa de fruver-infra en su pom.xml.");
                t.printStackTrace();
                usingInMemoryAdminService = true;
                adminService = new InMemoryAdminService();
                // En este punto, los usecases de usuario ya fueron inicializados por repoJdbc más arriba,
                // por tanto la gestión de usuarios persistirá aunque adminService sea in-memory (separación de responsabilidades).
                progressMsg.accept("No se pudo inicializar AdminService JDBC: " + t.getClass().getSimpleName() + " - " + t.getMessage());
                progressMsg.accept("Modo DEMO (in-memory) activado. Algunas operaciones en AdminService no serán persistentes.");
                System.out.println("[ServiceLocator] Fallback AdminService inicializado: " + adminService.getClass().getName());
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

        // inicializar los usecases de gestión de usuarios basados en el repo in-memory
        createUserUseCase = new CreateUserUseCase(inmem);
        updateUserUseCase = new UpdateUserUseCase(inmem);
        deleteUserUseCase = new DeleteUserUseCase(inmem);

        // --- productos en memoria para modo demo ---
        InMemoryProductoRepository prodMem = new InMemoryProductoRepository();

        // (semilla opcional desactivada para no asumir constructores/campos del dominio)
        // Si deseas datos demo, puedes guardarlos aquí contra prodMem.save(...)

        listProductsUseCase = new ListProductsUseCase(prodMem);
        createProductUseCase = new CreateProductUseCase(prodMem);
        updateProductUseCase = new UpdateProductUseCase(prodMem);
        deleteProductUseCase = new DeleteProductUseCase(prodMem);

        // inicializar adminService in-memory también para que la UI admin funcione
        usingInMemoryAdminService = true;
        adminService = new InMemoryAdminService();
        System.out.println("[ServiceLocator] InMemoryAdminService inicializado (modo demo).");
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

    public static CreateUserUseCase getCreateUserUseCase() {
        if (createUserUseCase == null) {
            throw new IllegalStateException("ServiceLocator no inicializado. Llama a initializeAndTestDb primero.");
        }
        return createUserUseCase;
    }

    public static UpdateUserUseCase getUpdateUserUseCase() {
        if (updateUserUseCase == null) {
            throw new IllegalStateException("ServiceLocator no inicializado. Llama a initializeAndTestDb primero.");
        }
        return updateUserUseCase;
    }

    public static DeleteUserUseCase getDeleteUserUseCase() {
        if (deleteUserUseCase == null) {
            throw new IllegalStateException("ServiceLocator no inicializado. Llama a initializeAndTestDb primero.");
        }
        return deleteUserUseCase;
    }

    // --- getters de productos ---
    public static ListProductsUseCase getListProductsUseCase() {
        if (listProductsUseCase == null) {
            throw new IllegalStateException("ServiceLocator no inicializado. Llama a initializeAndTestDb primero.");
        }
        return listProductsUseCase;
    }

    public static CreateProductUseCase getCreateProductUseCase() {
        if (createProductUseCase == null) {
            throw new IllegalStateException("ServiceLocator no inicializado. Llama a initializeAndTestDb primero.");
        }
        return createProductUseCase;
    }

    public static UpdateProductUseCase getUpdateProductUseCase() {
        if (updateProductUseCase == null) {
            throw new IllegalStateException("ServiceLocator no inicializado. Llama a initializeAndTestDb primero.");
        }
        return updateProductUseCase;
    }

    public static DeleteProductUseCase getDeleteProductUseCase() {
        if (deleteProductUseCase == null) {
            throw new IllegalStateException("ServiceLocator no inicializado. Llama a initializeAndTestDb primero.");
        }
        return deleteProductUseCase;
    }

    /* ----------------------
       AdminService exposure
       ---------------------- */

    public interface AdminService {
        List<String> listAvailablePorts();
        List<String> listDeviceConfigs(); // formato: "TIPO@PUERTO|JSON"
        boolean testDeviceConnection(String tipo, String port, Consumer<String> progressMsg, Consumer<Double> progressPercent);
        void saveDeviceConfig(String tipo, String port, String params);
        void cleanSalesData(Consumer<String> progressMsg, Consumer<Double> progressPercent) throws Exception;
        // utilidad: obtener info espacio db (usado por UI)
        DatabaseStorageInfo getDatabaseStorageInfo();
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

    public static boolean isUsingInMemoryAdminService() {
        return usingInMemoryAdminService;
    }

    /**
     * Implementación JDBC de AdminService (usa DeviceConfigRepositoryJdbc y opcionalmente DatabaseRepositoryJdbc).
     * Esta versión NO implementa backup (performBackup).
     */
    private static class JdbcAdminService implements AdminService {

        private final com.miempresa.fruver.infra.db.DeviceConfigRepositoryJdbc deviceRepo;
        private final com.miempresa.fruver.infra.db.DatabaseRepositoryJdbc dbRepo; // puede ser null si no disponible
        private final DataSource ds;

        public JdbcAdminService(com.miempresa.fruver.infra.db.DeviceConfigRepositoryJdbc deviceRepo,
                                com.miempresa.fruver.infra.db.DatabaseRepositoryJdbc dbRepo,
                                DataSource ds) {
            this.deviceRepo = deviceRepo;
            this.dbRepo = dbRepo;
            this.ds = ds;
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
                List<DeviceConfig> list = deviceRepo.findAll();
                List<String> out = new ArrayList<>();
                for (var c : list) {
                    out.add(c.getTipo().name() + "@" + c.getPuerto() + "|" + (c.getParametrosJson() == null ? "{}" : c.getParametrosJson()));
                }
                return out;
            } catch (Throwable t) {
                System.err.println("[JdbcAdminService] Error listDeviceConfigs(): " + t.getMessage());
                return List.of();
            }
        }

        /**
         * Test de conexión: intenta usar ScaleService / BarcodeService por reflexión si están disponibles.
         * Si no están, usa heurística (COM/tty).
         */
        @Override
        public boolean testDeviceConnection(String tipo, String port, Consumer<String> progressMsg, Consumer<Double> progressPercent) {
            try {
                if ("BASCULA".equalsIgnoreCase(tipo)) {
                    // obtener baud si existe config
                    int baud = 9600;
                    try {
                        Optional<DeviceConfig> opt = deviceRepo.findByType(DeviceType.BASCULA);
                        if (opt.isPresent()) {
                            String pj = opt.get().getParametrosJson();
                            String s = pj.replaceAll("(?s).*\"baudRate\"\\s*:\\s*(\\d+).*", "$1");
                            if (s != null && s.matches("\\d+")) baud = Integer.parseInt(s);
                        }
                    } catch (Throwable ignored) {}

                    try {
                        Class<?> cls = Class.forName("com.miempresa.fruver.infra.hardware.scale.ScaleService");
                        Object scaleSvc = cls.getConstructor().newInstance();
                        try {
                            // intentar abrir y leer
                            try {
                                java.lang.reflect.Method openM = cls.getMethod("open", String.class, int.class);
                                openM.invoke(scaleSvc, port, baud);
                            } catch (NoSuchMethodException nsm) {
                                // si no existe open(String,int) intentamos open(String) o ignoramos
                                try {
                                    java.lang.reflect.Method openM2 = cls.getMethod("open", String.class);
                                    openM2.invoke(scaleSvc, port);
                                } catch (NoSuchMethodException ignored) {}
                            }

                            // intentar leer peso por readWeightGrams, readWeightKg o readWeight
                            Object val = null;
                            try {
                                java.lang.reflect.Method m = cls.getMethod("readWeightGrams");
                                val = m.invoke(scaleSvc);
                            } catch (NoSuchMethodException nm1) {
                                try {
                                    java.lang.reflect.Method m2 = cls.getMethod("readWeightKg");
                                    val = m2.invoke(scaleSvc);
                                } catch (NoSuchMethodException nm2) {
                                    try {
                                        java.lang.reflect.Method m3 = cls.getMethod("readWeight");
                                        val = m3.invoke(scaleSvc);
                                    } catch (NoSuchMethodException ignored) {}
                                }
                            }

                            if (progressMsg != null) progressMsg.accept("Báscula probada" + (val == null ? "" : " (valor=" + val + ")"));
                            if (progressPercent != null) progressPercent.accept(1.0);
                            return true;
                        } finally {
                            try {
                                java.lang.reflect.Method closeM = cls.getMethod("close");
                                closeM.invoke(scaleSvc);
                            } catch (Throwable ignored) {}
                        }
                    } catch (ClassNotFoundException cnf) {
                        // no hay infra; fallback heurístico
                        if (progressMsg != null) progressMsg.accept("ScaleService no disponible en classpath (heurística).");
                    } catch (ReflectiveOperationException | RuntimeException ex) {
                        if (progressMsg != null) progressMsg.accept("Error leyendo báscula: " + ex.getMessage());
                        if (progressPercent != null) progressPercent.accept(0.0);
                        return false;
                    }
                } else if ("LECTOR".equalsIgnoreCase(tipo)) {
                    // si puerto vacío -> keyboard OK
                    if (port == null || port.isBlank()) {
                        if (progressMsg != null) progressMsg.accept("Modo keyboard (sin puerto) detectado.");
                        if (progressPercent != null) progressPercent.accept(1.0);
                        return true;
                    }
                    try {
                        Class<?> cls = Class.forName("com.miempresa.fruver.infra.hardware.barcode.BarcodeService");
                        Object barcodeSvc = cls.getConstructor().newInstance();
                        try {
                            // intentar init si existe
                            try {
                                java.lang.reflect.Method initM = cls.getMethod("init");
                                initM.invoke(barcodeSvc);
                            } catch (NoSuchMethodException ignored) {}
                            if (progressMsg != null) progressMsg.accept("Lector inicializado");
                            if (progressPercent != null) progressPercent.accept(1.0);
                            return true;
                        } finally {
                            try { java.lang.reflect.Method closeM = cls.getMethod("close"); closeM.invoke(barcodeSvc); } catch (Throwable ignored) {}
                        }
                    } catch (ClassNotFoundException cnf) {
                        if (progressMsg != null) progressMsg.accept("BarcodeService no disponible (heurística).");
                    } catch (ReflectiveOperationException ex) {
                        if (progressMsg != null) progressMsg.accept("Error inicializando lector: " + ex.getMessage());
                        if (progressPercent != null) progressPercent.accept(0.0);
                        return false;
                    }
                }

                // Fallback heurístico final
                boolean ok = port != null && (port.toUpperCase().contains("COM") || port.contains("tty"));
                if (progressMsg != null) progressMsg.accept(ok ? "Respuesta recibida (heurística)." : "Sin respuesta (heurística).");
                if (progressPercent != null) progressPercent.accept(1.0);
                return ok;
            } catch (Throwable t) {
                if (progressPercent != null) progressPercent.accept(0.0);
                return false;
            }
        }

        /**
         * Guarda/actualiza config del dispositivo.
         * - usa deviceRepo.findByType(...) para ver si hay existente y llama deviceRepo.save(obj) pasando id si existe.
         *
         * Implementación: intenta usar el UseCase si es posible, pero tolera LECTOR con puerto vacío (keyboard).
         */
        @Override
        public void saveDeviceConfig(String tipo, String port, String params) {
            DeviceType dt = DeviceType.valueOf(tipo.toUpperCase());
            String effectiveParams = params == null ? "{}" : params;
            try {
                // Caso especial: LECTOR puede venir con puerto vacío (keyboard wedge) — repo.save debe tolerarlo.
                if (dt == DeviceType.LECTOR && (port == null || port.isBlank())) {
                    // usar repo directamente para crear la config con puerto vacío
                    DeviceConfig cfg = new DeviceConfig(null, dt, "", effectiveParams);
                    deviceRepo.save(cfg);
                    System.out.println("[JdbcAdminService] Guardada configuración LECTOR (keyboard) directamente en repo.");
                    return;
                }

                // Intentar usar el UseCase (fruver-service). Si fruver-service no está en classpath, fallback a deviceRepo.
                try {
                    SaveDeviceConfigUseCase usecase = new SaveDeviceConfigUseCase(deviceRepo);
                    usecase.execute(tipo, port == null ? "" : port, effectiveParams);
                    System.out.println("[JdbcAdminService] Guardada configuración usando SaveDeviceConfigUseCase.");
                    return;
                } catch (NoClassDefFoundError | Exception useEx) {
                    // si falla el usecase por cualquier motivo, caemos a repo.save
                    System.err.println("[JdbcAdminService] SaveDeviceConfigUseCase no disponible o falló: " + useEx.getMessage());
                }

                // Fallback: comportamiento clásico
                Optional<DeviceConfig> existOpt = Optional.empty();
                try {
                    existOpt = deviceRepo.findByType(dt);
                } catch (Throwable t) {
                    System.err.println("[JdbcAdminService] findByType falló: " + t.getMessage());
                }

                if (existOpt != null && existOpt.isPresent()) {
                    DeviceConfig existing = existOpt.get();
                    Integer existingId = existing.getConfigId();
                    DeviceConfig toSave = new DeviceConfig(existingId, dt, port == null ? "" : port, effectiveParams);
                    deviceRepo.save(toSave);
                    System.out.println("[JdbcAdminService] Actualizada configuración para tipo=" + tipo + " id=" + existingId);
                } else {
                    DeviceConfig saved = deviceRepo.save(new DeviceConfig(null, dt, port == null ? "" : port, effectiveParams));
                    System.out.println("[JdbcAdminService] Nueva configuración guardada para tipo=" + tipo + " -> id=" + (saved == null ? "null" : saved.getConfigId()));
                }
            } catch (Throwable t) {
                throw new RuntimeException("Error al persistir configuración: " + t.getMessage(), t);
            }
        }

        @Override
        public void cleanSalesData(Consumer<String> progressMsg, Consumer<Double> progressPercent) throws Exception {
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
        public DatabaseStorageInfo getDatabaseStorageInfo() {
            try {
                if (dbRepo == null) {
                    System.err.println("[JdbcAdminService] DatabaseRepositoryJdbc no disponible - getDatabaseStorageInfo retorna null.");
                    return null;
                }
                return new GetDatabaseStorageUseCase(dbRepo).execute();
            } catch (Throwable t) {
                // si falla, retornamos null para que la UI lo maneje
                System.err.println("[JdbcAdminService] getDatabaseStorageInfo falló: " + t.getMessage());
                return null;
            }
        }
    }

    /* ---------------------------
       In-memory AdminService (fallback)
       --------------------------- */

    private static class InMemoryAdminService implements AdminService {

        private final Map<String, String> configs = new LinkedHashMap<>();

        public InMemoryAdminService() {
            // Notar: no seed por defecto. Evitamos mostrar configuraciones "falsas".
            // Para pruebas locales explícitas se puede pasar -Dfruver.demo.seed=true
            String demoSeed = System.getProperty("fruver.demo.seed");
            if ("true".equalsIgnoreCase(demoSeed)) {
                configs.put("BASCULA@COM1", "{ \"baudRate\": 9600, \"dataBits\": 8 }");
            }
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
                if (progressMsg != null) progressMsg.accept("Intentando abrir puerto " + port + " (simulado)...");
                Thread.sleep(200);
                if (progressPercent != null) progressPercent.accept(0.3);
                if (progressMsg != null) progressMsg.accept("Enviando comando de prueba (simulado)...");
                Thread.sleep(200);
                if (progressPercent != null) progressPercent.accept(0.7);
                boolean ok = port != null && (port.toUpperCase().contains("COM") || port.contains("tty")) || (tipo != null && tipo.equalsIgnoreCase("LECTOR") && (port == null || port.isBlank()));
                if (progressPercent != null) progressPercent.accept(1.0);
                if (progressMsg != null) progressMsg.accept(ok ? "Respuesta recibida (simulado)" : "Sin respuesta (simulado)");
                return ok;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (progressMsg != null) progressMsg.accept("Prueba cancelada (simulado)");
                return false;
            }
        }

        @Override
        public void saveDeviceConfig(String tipo, String port, String params) {
            String key = tipo + "@" + (port == null ? "" : port);
            configs.put(key, params == null ? "{}" : params);
            System.out.println("[InMemoryAdminService] saveDeviceConfig: " + key + " -> " + (params == null ? "{}" : params));
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
        public DatabaseStorageInfo getDatabaseStorageInfo() {
            // fake data
            return new DatabaseStorageInfo(1024L * 1024L * 120L, Optional.of(1024L * 1024L * 300L), Optional.empty());
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

    /* ---------------------------
       In-memory ProductoRepository
       --------------------------- */
    private static class InMemoryProductoRepository implements ProductoRepository {
        private final Map<Integer, Producto> byId = new ConcurrentHashMap<>();
        private final Map<String, Integer> idByCode = new ConcurrentHashMap<>();
        private final AtomicInteger seq = new AtomicInteger(1);

        @Override
        public Producto save(Producto p) {
            Integer id = seq.getAndIncrement();

            // evitar códigos duplicados
            if (p.getCodigo() != null) {
                Integer existing = idByCode.get(p.getCodigo());
                if (existing != null) {
                    throw new IllegalArgumentException("Código ya existe: " + p.getCodigo());
                }
            }

            Producto toStore = new Producto(
                    id, p.getCodigo(), p.getNombre(),
                    p.getPrecioUnitario(), p.getTipo(),
                    p.getStockActual(), p.getStockUmbral()
            );
            byId.put(id, toStore);
            if (p.getCodigo() != null) idByCode.put(p.getCodigo(), id);
            return toStore;
        }

        @Override
        public Producto update(Producto p) {
            Integer id = p.getProductoId();
            if (id == null || !byId.containsKey(id)) {
                throw new IllegalArgumentException("Producto no existe: " + id);
            }

            // si cambia el código, mantener consistencia del índice
            String oldCode = byId.get(id).getCodigo();
            String newCode = p.getCodigo();

            if (newCode != null && !newCode.equals(oldCode)) {
                Integer clash = idByCode.get(newCode);
                if (clash != null && !clash.equals(id)) {
                    throw new IllegalArgumentException("Código ya existe: " + newCode);
                }
                if (oldCode != null) idByCode.remove(oldCode);
                idByCode.put(newCode, id);
            }

            Producto updated = new Producto(
                    id, newCode, p.getNombre(),
                    p.getPrecioUnitario(), p.getTipo(),
                    p.getStockActual(), p.getStockUmbral()
            );
            byId.put(id, updated);
            return updated;
        }

        @Override
        public Optional<Producto> findByCodigo(String codigo) {
            if (codigo == null) return Optional.empty();
            Integer id = idByCode.get(codigo);
            return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Producto> findById(Integer id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public java.util.List<Producto> findAll() {
            return new ArrayList<>(byId.values());
        }

        @Override
        public void delete(Integer id) {
            if (id == null) return;
            Producto removed = byId.remove(id);
            if (removed != null && removed.getCodigo() != null) {
                idByCode.remove(removed.getCodigo());
            }
        }

        @Override
        public void updateStock(Integer productoId, BigDecimal newStock) {
            Producto p = byId.get(productoId);
            if (p == null) throw new IllegalArgumentException("Producto no existe: " + productoId);
            Producto updated = new Producto(
                    p.getProductoId(), p.getCodigo(), p.getNombre(),
                    p.getPrecioUnitario(), p.getTipo(),
                    newStock, p.getStockUmbral()
            );
            byId.put(productoId, updated);
        }
    }
}
