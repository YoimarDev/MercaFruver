package com.miempresa.fruver.ui.controller;

import com.miempresa.fruver.domain.model.Producto;
import com.miempresa.fruver.domain.model.Usuario;
import com.miempresa.fruver.service.port.DatabaseStorageInfo;
import com.miempresa.fruver.ui.ServiceLocator;
import com.miempresa.fruver.ui.util.ProductImageHelper;
import com.miempresa.fruver.ui.viewmodel.SupervisorViewModel;
import com.miempresa.fruver.ui.widget.ProductCell;
import com.miempresa.fruver.service.security.SecurityContext;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Controller JavaFX para la vista Supervisor (mejorado).
 * Incluye:
 *  - contrato público init(Usuario, Runnable) esperado por MainApp
 *  - fallback robusto para volver al login (inicializando controller del Login como MainApp lo hace)
 *  - formateo consistente de precios/cantidades
 *  - vista ampliada de estadísticas (chart modal)
 */
public class SupervisorController {

    // Filtro + listas
    @FXML private TextField txtFiltro;
    @FXML private ListView<Producto> lvProductosPeso;
    @FXML private ListView<Producto> lvProductosUnidad;

    // Form fields
    @FXML private TextField txtCodigo;
    @FXML private TextField txtNombre;
    @FXML private ComboBox<String> cbTipo;
    @FXML private TextField txtPrecio;
    @FXML private TextField txtStock;
    @FXML private TextField txtStockUmb;
    @FXML private ImageView ivImagen;
    @FXML private Label lblImagenPath;
    @FXML private Button btnCrear;
    @FXML private Button btnActualizar;
    @FXML private Button btnEliminar;
    @FXML private Button btnSubirImagen;
    @FXML private Button btnNuevoProducto;
    @FXML private Label lblStatus;
    @FXML private Button btnSalir;

    // Estadísticas UI (nuevos controles)
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private Button btnRefreshStats;
    @FXML private Button btnLast7;
    @FXML private Button btnToday;
    @FXML private Label lblTotalVentas;
    @FXML private Label lblRangeInfo;
    @FXML private ListView<String> lvTopProductos;
    @FXML private Label lblDbInfo;
    @FXML private Button btnExpandStats; // opcional en FXML, manejado si existe

    // Estadísticas de producto (existentes)
    @FXML private Label lblTotalProductos;
    @FXML private Label lblPesoCount;
    @FXML private Label lblUnidadCount;

    private SupervisorViewModel vm;

    // Contexto / navegación (contrato con MainApp)
    private Usuario currentUser = null;
    private Runnable onReturnToLogin = null;

    @FXML
    public void initialize() {
        vm = new SupervisorViewModel();

        // Set cell factories
        lvProductosPeso.setCellFactory(list -> new ProductCell());
        lvProductosUnidad.setCellFactory(list -> new ListCell<Producto>() {
            @Override
            protected void updateItem(Producto item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                StringBuilder sb = new StringBuilder();
                sb.append(item.getNombre() == null ? "(sin nombre)" : item.getNombre());
                if (item.getCodigo() != null && !item.getCodigo().isBlank()) {
                    sb.append(" • ").append(item.getCodigo());
                }
                if (item.getPrecioUnitario() != null) {
                    sb.append(" • ").append(formatPrice(item.getPrecioUnitario()));
                    if (item.getTipo() != null && item.getTipo().name().equalsIgnoreCase("PESO")) sb.append("/kg");
                }
                if (item.getStockActual() != null) {
                    sb.append(" • st=").append(formatQuantity(item.getStockActual()));
                }
                setText(sb.toString());
            }
        });

        // Bind lists
        lvProductosPeso.setItems(vm.getProductosPeso());
        lvProductosUnidad.setItems(vm.getProductosUnidad());

        // Combo tipo
        cbTipo.getItems().setAll("PESO", "UNIDAD");

        // Filter reactivo
        txtFiltro.textProperty().addListener((obs, oldV, newV) -> vm.setFilter(newV));

        // Selection listeners
        lvProductosPeso.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                lvProductosUnidad.getSelectionModel().clearSelection();
                populateForm(sel);
            }
        });
        lvProductosUnidad.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                lvProductosPeso.getSelectionModel().clearSelection();
                populateForm(sel);
            }
        });

        // Disable image upload for UNIDAD
        cbTipo.valueProperty().addListener((obs, old, val) -> {
            boolean isUnidad = val != null && val.equalsIgnoreCase("UNIDAD");
            btnSubirImagen.setDisable(isUnidad);
            ivImagen.setOpacity(isUnidad ? 0.5 : 1.0);
            if (isUnidad) ivImagen.getStyleClass().add("disabled-image"); else ivImagen.getStyleClass().remove("disabled-image");
        });

        // Inicializar rangos por defecto: últimos 7 días
        dpTo.setValue(LocalDate.now());
        dpFrom.setValue(LocalDate.now().minusDays(6));

        // Presets
        btnLast7.setOnAction(e -> {
            dpTo.setValue(LocalDate.now());
            dpFrom.setValue(LocalDate.now().minusDays(6));
            onRefreshStats();
        });
        btnToday.setOnAction(e -> {
            dpTo.setValue(LocalDate.now());
            dpFrom.setValue(LocalDate.now());
            onRefreshStats();
        });

        // Expandir estadísticas (si existe el botón)
        if (btnExpandStats != null) {
            btnExpandStats.setOnAction(e -> onExpandStats());
        }

        // Refresh manual
        btnRefreshStats.setOnAction(e -> onRefreshStats());

        // Cargar productos (background)
        runBackground(() -> {
            try {
                vm.loadProducts();
                Platform.runLater(() -> {
                    lblStatus.setText("Productos cargados.");
                    refreshStatsToUI(); // refrescar stats derivados de productos
                    // además actualizar las estadísticas por rango usando default
                    onRefreshStats();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> lblStatus.setText("Error cargando productos: " + ex.getMessage()));
            }
        });
    }

    /**
     * Método público que MainApp llama para inyectar contexto.
     * - user: usuario en sesión
     * - onReturn: callback que debería ejecutar la app para regresar al login
     */
    public void init(Usuario user, Runnable onReturn) {
        this.currentUser = user;
        this.onReturnToLogin = onReturn;
        // Si necesitas mostrar el usuario en la UI, lo puedes hacer aquí.
    }

    private void populateForm(Producto p) {
        txtCodigo.setText(p.getCodigo());
        txtNombre.setText(p.getNombre());
        cbTipo.setValue(p.getTipo() == null ? null : p.getTipo().name());

        // Precio: mostrar siempre 2 decimales para claridad (precio por kilo si es PESO)
        txtPrecio.setText(p.getPrecioUnitario() == null ? "" : formatPriceForInput(p.getPrecioUnitario()));

        // Stock: mostrar sin ceros innecesarios (pero almacenamos a 3 decimales internamente)
        txtStock.setText(p.getStockActual() == null ? "" : formatQuantity(p.getStockActual()));
        txtStockUmb.setText(p.getStockUmbral() == null ? "" : formatQuantity(p.getStockUmbral()));

        lblImagenPath.setText(p.getImagenPath() == null ? "" : p.getImagenPath());
        if (p.getImagenPath() != null && !p.getImagenPath().isBlank() && (p.getTipo() != null && p.getTipo().name().equalsIgnoreCase("PESO"))) {
            ivImagen.setImage(ProductImageHelper.loadImage(p.getImagenPath()));
        } else {
            ivImagen.setImage(null);
        }
    }

    private void clearForm() {
        txtCodigo.clear();
        txtNombre.clear();
        cbTipo.setValue(null);
        txtPrecio.clear();
        txtStock.clear();
        txtStockUmb.clear();
        ivImagen.setImage(null);
        lblImagenPath.setText("");
        lblStatus.setText("");
    }

    @FXML
    private void onNuevoProducto() {
        lvProductosPeso.getSelectionModel().clearSelection();
        lvProductosUnidad.getSelectionModel().clearSelection();
        clearForm();
    }

    @FXML
    private void onSubirImagen() {
        String tipoActual = cbTipo.getValue();
        if (tipoActual != null && tipoActual.equalsIgnoreCase("UNIDAD")) {
            lblStatus.setText("No es posible asignar imagen a productos UNIDAD.");
            return;
        }
        Window w = btnSubirImagen.getScene().getWindow();
        FileChooser fc = new FileChooser();
        fc.setTitle("Seleccionar imagen del producto");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg"));
        File f = fc.showOpenDialog(w);
        if (f == null) return;

        runBackground(() -> {
            try {
                String saved = ProductImageHelper.saveImage(f);
                Platform.runLater(() -> {
                    lblImagenPath.setText(saved);
                    ivImagen.setImage(ProductImageHelper.loadImage(saved));
                    lblStatus.setText("Imagen cargada: " + f.getName());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> lblStatus.setText("Error al guardar imagen: " + ex.getMessage()));
            }
        });
    }

    @FXML
    private void onCrear() {
        runBackground(() -> {
            try {
                validateFormForCreate();
                Producto p = vm.createProduct(
                        txtCodigo.getText(),
                        txtNombre.getText(),
                        cbTipo.getValue(),
                        txtPrecio.getText(),
                        txtStock.getText(),
                        txtStockUmb.getText(),
                        lblImagenPath.getText()
                );
                Platform.runLater(() -> {
                    lblStatus.setText("Producto creado (id=" + (p == null ? "?" : p.getProductoId()) + ").");
                    vm.loadProducts();
                    refreshStatsToUI();
                    onRefreshStats();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> lblStatus.setText("Error crear producto: " + ex.getMessage()));
            }
        });
    }

    @FXML
    private void onActualizar() {
        Producto sel = lvProductosPeso.getSelectionModel().getSelectedItem();
        if (sel == null) sel = lvProductosUnidad.getSelectionModel().getSelectedItem();
        if (sel == null) {
            lblStatus.setText("Selecciona un producto para actualizar.");
            return;
        }
        final Producto target = sel;
        runBackground(() -> {
            try {
                Producto updated = vm.updateProduct(
                        target.getProductoId(),
                        txtCodigo.getText(),
                        txtNombre.getText(),
                        cbTipo.getValue(),
                        txtPrecio.getText(),
                        txtStock.getText(),
                        txtStockUmb.getText(),
                        lblImagenPath.getText()
                );
                Platform.runLater(() -> {
                    lblStatus.setText("Producto actualizado (id=" + updated.getProductoId() + ").");
                    vm.loadProducts();
                    refreshStatsToUI();
                    onRefreshStats();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> lblStatus.setText("Error actualizar producto: " + ex.getMessage()));
            }
        });
    }

    @FXML
    private void onEliminar() {
        Producto sel = lvProductosPeso.getSelectionModel().getSelectedItem();
        if (sel == null) sel = lvProductosUnidad.getSelectionModel().getSelectedItem();
        if (sel == null) {
            lblStatus.setText("Selecciona un producto a eliminar.");
            return;
        }

        final Producto target = sel; // final antes de usar en lambda
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Eliminar producto '" + target.getNombre() + "'?", ButtonType.YES, ButtonType.NO);
        a.setHeaderText("Confirmar eliminación");
        a.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                runBackground(() -> {
                    try {
                        vm.deleteProduct(target.getProductoId());
                        Platform.runLater(() -> {
                            lblStatus.setText("Producto eliminado.");
                            vm.loadProducts();
                            refreshStatsToUI();
                            onRefreshStats();
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> lblStatus.setText("Error eliminando producto: " + ex.getMessage()));
                    }
                });
            }
        });
    }

    /**
     * Salir: intenta regresar al Login sin cerrar la aplicación.
     * - Usar callback onReturnToLogin si MainApp lo pasó (es el camino preferido).
     * - Si no existe callback, fallback robusto que carga LoginView.fxml
     *   e intenta inicializar su controller con la misma firma que MainApp.
     */
    @FXML
    private void onSalir() {
        // Preferimos delegar a MainApp si nos pasó callback (forma esperada)
        try {
            if (onReturnToLogin != null) {
                // NO cerramos/ocultamos el Stage aquí. MainApp/ callback se encargará.
                Platform.runLater(onReturnToLogin);
                return;
            }
        } catch (Throwable ignored) {}

        // Fallback: intentar abrir Login en el mismo Stage sin cerrar antes.
        try {
            Stage stage = (Stage) btnSalir.getScene().getWindow();
            // limpiar contexto por seguridad
            com.miempresa.fruver.service.security.SecurityContext.clear();
            // abrir login en el mismo Stage
            openLoginLocal(stage);
        } catch (Throwable t) {
            System.err.println("[SupervisorController] No se pudo volver al login (fallback): " + t.getMessage());
            // último recurso: ocultar ventana actual (evitar Platform.exit())
            try {
                btnSalir.getScene().getWindow().hide();
            } catch (Throwable ignored) {}
        }
    }


    /**
     * Fallback: carga LoginView.fxml en el stage dado y trata de invocar init(...) en su controller.
     * Intentos, en orden:
     *  1) init(LoginUseCase, ListUsersUseCase, Runnable)
     *  2) init(Usuario, Runnable)
     *  3) init()
     *
     * El Runnable que pasamos en (1) reusa este mismo Stage y, al ejecutarse (login ok),
     * llama a openRoleViewLocal(...) para cargar la vista correspondiente al usuario logeado.
     */
    private void openLoginLocal(Stage stage) throws IOException {
        var res = getClass().getResource("/fxml/LoginView.fxml");
        if (res == null) throw new IOException("LoginView.fxml no encontrado en recursos.");
        FXMLLoader loader = new FXMLLoader(res);
        Parent root = loader.load();

        Scene scene = new Scene(root);
        var css = getClass().getResource("/css/styles.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        // preparar runnable que será pasado al login controller (si acepta Runnable)
        Runnable onLoginSuccess = () -> {
            Usuario usuario = SecurityContext.getCurrentUser();
            Platform.runLater(() -> {
                try {
                    openRoleViewLocal(usuario, stage);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    lblStatus.setText("Error abriendo vista por rol tras login: " + ex.getMessage());
                }
            });
        };

        // intentos de inicialización del controller
        Object ctrl = loader.getController();
        boolean initialized = false;
        if (ctrl != null) {
            try {
                // 1) buscar init(LoginUseCase, ListUsersUseCase, Runnable)
                Method m = ctrl.getClass().getMethod("init",
                        com.miempresa.fruver.service.usecase.LoginUseCase.class,
                        com.miempresa.fruver.service.usecase.ListUsersUseCase.class,
                        Runnable.class);
                // invocar con los usecases del ServiceLocator y el runnable
                m.invoke(ctrl,
                        ServiceLocator.getLoginUseCase(),
                        ServiceLocator.getListUsersUseCase(),
                        onLoginSuccess);
                initialized = true;
            } catch (NoSuchMethodException nsme) {
                // intentar siguiente firma
            } catch (Throwable invokeEx) {
                System.err.println("[SupervisorController] Error invocando init(loginUseCase,...): " + invokeEx.getMessage());
            }

            if (!initialized) {
                try {
                    // 2) init(Usuario, Runnable)
                    Method m2 = ctrl.getClass().getMethod("init", Usuario.class, Runnable.class);
                    m2.invoke(ctrl, null, onLoginSuccess);
                    initialized = true;
                } catch (NoSuchMethodException nsme2) {
                    // intentar init()
                } catch (Throwable invokeEx) {
                    System.err.println("[SupervisorController] Error invocando init(usuario,...): " + invokeEx.getMessage());
                }
            }

            if (!initialized) {
                try {
                    Method m3 = ctrl.getClass().getMethod("init");
                    m3.invoke(ctrl);
                    initialized = true;
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable invokeEx) {
                    System.err.println("[SupervisorController] Error invocando init(): " + invokeEx.getMessage());
                }
            }
        }

        stage.setScene(scene);
        stage.setWidth(1000);
        stage.setHeight(640);
        stage.setResizable(true);
        stage.centerOnScreen();
        stage.show();

        // si no se pudo inicializar, al menos mostramos la escena; el controller puede inicializarse por su cuenta.
    }

    /**
     * Similar a MainApp.openViewForRole(...), pero local (usa el stage dado).
     * Intenta invocar init(Usuario,Runnable) en el controller y pasa como Runnable
     * un callback que limpia el SecurityContext y vuelve a login (openLoginLocal).
     */
    private void openRoleViewLocal(Usuario usuario, Stage stage) throws Exception {
        if (usuario == null) {
            openLoginLocal(stage);
            return;
        }

        String fxmlPath;
        double width = 1024, height = 700;
        switch (usuario.getRol()) {
            case CAJERO:
                fxmlPath = "/fxml/CajeroView.fxml";
                width = 1024; height = 700;
                break;
            case SUPERVISOR:
                fxmlPath = "/fxml/SupervisorView.fxml";
                width = 1100; height = 740;
                break;
            case ADMIN:
                fxmlPath = "/fxml/AdminView.fxml";
                width = 1200; height = 800;
                break;
            default:
                throw new IllegalStateException("Rol no soportado: " + usuario.getRol());
        }

        var res = getClass().getResource(fxmlPath);
        if (res == null) throw new IOException("Recurso no encontrado: " + fxmlPath);
        FXMLLoader fx = new FXMLLoader(res);
        Parent root = fx.load();

        Scene scene = new Scene(root);
        var css = getClass().getResource("/css/styles.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage.setScene(scene);
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setResizable(true);
        stage.centerOnScreen();
        stage.show();

        Object ctrl = fx.getController();
        if (ctrl == null) return;

        // Logout callback para esta vista: limpiar contexto y volver al login (misma stage)
        Runnable logoutCallback = () -> {
            SecurityContext.clear();
            Platform.runLater(() -> {
                try {
                    openLoginLocal(stage);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        };

        // Intentar invocar init(Usuario,Runnable)
        try {
            Method m = ctrl.getClass().getMethod("init", Usuario.class, Runnable.class);
            m.invoke(ctrl, usuario, logoutCallback);
            return;
        } catch (NoSuchMethodException nsme) {
            System.err.println("Aviso: controlador de " + fxmlPath + " no expone init(Usuario,Runnable).");
        } catch (Throwable ex) {
            System.err.println("[SupervisorController] Error invocando init(user,logout) en " + fxmlPath + ": " + ex.getMessage());
        }
        // si no existe, dejamos que el controlador siga su ciclo de vida por defecto.
    }

    private void validateFormForCreate() {
        if (txtCodigo.getText() == null || txtCodigo.getText().isBlank()) throw new IllegalArgumentException("Código es obligatorio");
        if (txtNombre.getText() == null || txtNombre.getText().isBlank()) throw new IllegalArgumentException("Nombre es obligatorio");
        if (cbTipo.getValue() == null || cbTipo.getValue().isBlank()) throw new IllegalArgumentException("Tipo es obligatorio (PESO o UNIDAD)");
    }

    private void runBackground(Runnable r) {
        Thread t = new Thread(r, "supervisor-bg");
        t.setDaemon(true);
        t.start();
    }

    /* ---------------------- Estadísticas: refresco y UI ---------------------- */

    @FXML
    private void onRefreshStats() {
        LocalDate from = dpFrom.getValue();
        LocalDate to = dpTo.getValue();
        if (from == null || to == null) {
            lblStatus.setText("Selecciona las fechas 'Desde' y 'Hasta'.");
            return;
        }
        if (from.isAfter(to)) {
            lblStatus.setText("'Desde' no puede ser posterior a 'Hasta'.");
            return;
        }

        lblStatus.setText("Consultando estadísticas...");
        runBackground(() -> {
            try {
                Map<String, Object> stats = vm.fetchStatistics(from, to);
                Platform.runLater(() -> {
                    // Mostrar totalVentas si existe
                    Object tv = stats.get("totalVentas");
                    lblTotalVentas.setText(tv == null ? "0" : String.valueOf(tv));

                    // Range info
                    lblRangeInfo.setText(String.format("%s → %s", from.toString(), to.toString()));

                    // Top productos: flexible parsing
                    lvTopProductos.getItems().clear();
                    Object topObj = stats.get("topProductos");
                    if (topObj instanceof List) {
                        List<?> list = (List<?>) topObj;
                        for (Object o : list) {
                            if (o instanceof Map) {
                                Map<?,?> m = (Map<?,?>) o;
                                Object nombreObj = m.get("nombre");
                                if (nombreObj == null) nombreObj = m.get("nombreProducto");
                                String nombre = nombreObj == null ? "(sin nombre)" : String.valueOf(nombreObj);

                                Object qtyObj = null;
                                if (m.containsKey("cantidad")) qtyObj = m.get("cantidad");
                                else if (m.containsKey("stock")) qtyObj = m.get("stock");

                                String qtyStr = qtyObj == null ? "-" : String.valueOf(qtyObj);
                                String line = String.format("%s — %s", nombre, qtyStr);
                                lvTopProductos.getItems().add(line);
                            } else {
                                lvTopProductos.getItems().add(String.valueOf(o));
                            }
                        }
                    } else {
                        // fallback: si no vienen topProductos, usar el top derivado de VM
                        List<Producto> top = vm.topByStock(10);
                        for (Producto p : top) {
                            String line = String.format("%s — %s", p.getNombre(), p.getStockActual() == null ? "-" : formatQuantity(p.getStockActual()));
                            lvTopProductos.getItems().add(line);
                        }
                    }

                    // DB info (opcional)
                    try {
                        var admin = com.miempresa.fruver.ui.ServiceLocator.getAdminService();
                        DatabaseStorageInfo dbInfo = admin.getDatabaseStorageInfo();
                        updateDbInfo(dbInfo);
                    } catch (Throwable t) {
                        lblDbInfo.setText("sin datos");
                    }

                    lblStatus.setText("Estadísticas actualizadas.");
                });
            } catch (Throwable ex) {
                Platform.runLater(() -> {
                    lblStatus.setText("Error consultando estadísticas: " + ex.getMessage());
                    // Como último recurso, refrescar UI con snapshot local
                    refreshStatsToUI();
                });
            }
        });
    }

    /**
     * Abre un modal (grande) con gráficos para visualizar estadísticas con mayor claridad.
     */
    @FXML
    private void onExpandStats() {
        LocalDate from = dpFrom.getValue();
        LocalDate to = dpTo.getValue();
        if (from == null || to == null) {
            lblStatus.setText("Selecciona las fechas 'Desde' y 'Hasta' antes de expandir.");
            return;
        }
        lblStatus.setText("Generando vista ampliada de estadísticas...");
        runBackground(() -> {
            Map<String, Object> stats = vm.fetchStatistics(from, to);
            Platform.runLater(() -> {
                CategoryAxis xAxis = new CategoryAxis();
                NumberAxis yAxis = new NumberAxis();
                BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
                chart.setTitle("Top productos (cantidad/ventas)");
                xAxis.setLabel("Producto");
                yAxis.setLabel("Cantidad");

                XYChart.Series<String, Number> series = new XYChart.Series<>();
                Object topObj = stats.get("topProductos");
                if (topObj instanceof List) {
                    for (Object o : (List<?>) topObj) {
                        try {
                            if (o instanceof Map) {
                                Map<?,?> m = (Map<?,?>) o;
                                Object nombreObj = m.get("nombre") != null ? m.get("nombre") : m.get("nombreProducto");
                                String nombre = nombreObj == null ? "(sin nombre)" : String.valueOf(nombreObj);
                                Object qtyObj = m.containsKey("cantidad") ? m.get("cantidad") : m.get("stock");
                                double v = 0.0;
                                if (qtyObj instanceof Number) v = ((Number) qtyObj).doubleValue();
                                else if (qtyObj != null) v = Double.parseDouble(String.valueOf(qtyObj));
                                series.getData().add(new XYChart.Data<>(nombre, v));
                            }
                        } catch (Exception ignore) { /* skip malformed rows */ }
                    }
                } else {
                    List<Producto> top = vm.topByStock(10);
                    for (Producto p : top) {
                        double v = p.getStockActual() == null ? 0.0 : p.getStockActual().doubleValue();
                        series.getData().add(new XYChart.Data<>(p.getNombre(), v));
                    }
                }
                chart.getData().add(series);

                // Small visual tweaks
                chart.setCategoryGap(8);
                chart.setBarGap(4);

                VBox root = new VBox(8, chart);
                root.setStyle("-fx-padding:12;");
                Scene scene = new Scene(root, 1000, 600);
                Stage st = new Stage();
                st.initModality(Modality.APPLICATION_MODAL);
                st.setTitle("Estadísticas completas — Merca Fruver");
                st.setScene(scene);
                st.show();
                lblStatus.setText("Estadísticas ampliadas.");
            });
        });
    }

    /**
     * Refresca estadísticas simples en la UI usando los métodos locales del VM.
     */
    private void refreshStatsToUI() {
        Platform.runLater(() -> {
            try {
                SupervisorViewModel.StatsSnapshot s = vm.refreshStats();
                lblTotalProductos.setText(String.valueOf(s.total));
                lblPesoCount.setText(String.valueOf(s.peso));
                lblUnidadCount.setText(String.valueOf(s.unidad));

                // Top list (por stock) -> mostrar name + stock
                lvTopProductos.getItems().clear();
                List<Producto> top = s.topByStock;
                for (Producto p : top) {
                    String line = String.format("%s — %s", p.getNombre(), p.getStockActual() == null ? "-" : formatQuantity(p.getStockActual()));
                    lvTopProductos.getItems().add(line);
                }

                // DB info: opcional
                try {
                    var admin = com.miempresa.fruver.ui.ServiceLocator.getAdminService();
                    DatabaseStorageInfo dbInfo = admin.getDatabaseStorageInfo();
                    updateDbInfo(dbInfo);
                } catch (Throwable t) {
                    lblDbInfo.setText("sin datos");
                }

            } catch (Exception ex) {
                lblStatus.setText("Error actualizando estadísticas: " + ex.getMessage());
            }
        });
    }

    /* ---------------------- Helpers DB info y formateo ---------------------- */

    private void updateDbInfo(DatabaseStorageInfo dbInfo) {
        if (dbInfo == null) {
            lblDbInfo.setText("sin datos");
            return;
        }
        long used = dbInfo.getUsedBytes();
        Optional<Long> freeOpt = dbInfo.getFsFreeBytes();
        Optional<String> dirOpt = dbInfo.getDataDir();

        String human;
        if (freeOpt != null && freeOpt.isPresent()) {
            long free = freeOpt.get();
            long total = used + free;
            int pct = total > 0 ? (int) Math.round((used * 100.0) / total) : 0;
            human = humanBytes(used) + " / " + humanBytes(total) + " (" + pct + "%)";
        } else {
            human = humanBytes(used) + " usados";
        }

        if (dirOpt != null && dirOpt.isPresent()) {
            human += " • " + dirOpt.get();
        }

        lblDbInfo.setText(human);
    }

    private String humanBytes(long bytes) {
        final long KB = 1024L, MB = KB * 1024, GB = MB * 1024;
        if (bytes >= GB) return String.format("%.1f GB", bytes / (double) GB);
        if (bytes >= MB) return String.format("%.1f MB", bytes / (double) MB);
        if (bytes >= KB) return String.format("%.1f KB", bytes / (double) KB);
        return bytes + " B";
    }

    // Formateo: precio para mostrar (2 decimales, moneda local)
    private String formatPrice(BigDecimal bd) {
        if (bd == null) return "";
        return String.format(Locale.forLanguageTag("es-CO"), "%,.2f", bd);
    }
    // Para el campo de entrada (input) dejamos "3000" o "3000.00" pero normalizamos en VM.
    private String formatPriceForInput(BigDecimal bd) {
        if (bd == null) return "";
        return bd.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    // Formateo de cantidades (stock): elimina ceros innecesarios -> 10 en vez de 10.000
    private String formatQuantity(BigDecimal bd) {
        if (bd == null) return "";
        try {
            BigDecimal z = bd.stripTrailingZeros();
            String s = z.toPlainString();
            return s;
        } catch (Exception ex) {
            return bd.toPlainString();
        }
    }
}
