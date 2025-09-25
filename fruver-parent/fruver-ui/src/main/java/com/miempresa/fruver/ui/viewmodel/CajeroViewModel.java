package com.miempresa.fruver.ui.viewmodel;

import com.miempresa.fruver.domain.model.Producto;
import com.miempresa.fruver.domain.model.Usuario;
import com.miempresa.fruver.domain.model.VentaItem;
import com.miempresa.fruver.service.usecase.ListProductsUseCase;
import com.miempresa.fruver.service.usecase.RegistrarVentaUseCase;
import com.miempresa.fruver.ui.ServiceLocator;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel para la vista Cajero.
 *
 * Conserva la lógica de negocio mínima para el rol Cajero.
 */
public class CajeroViewModel {

    private final ObservableList<Producto> allProducts = FXCollections.observableArrayList();
    private final ObservableList<Producto> filteredProducts = FXCollections.observableArrayList();
    private final ObservableList<CartItem> cart = FXCollections.observableArrayList();

    private final ObjectProperty<BigDecimal> subtotal = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> iva = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> total = new SimpleObjectProperty<>(BigDecimal.ZERO);

    private final StringProperty scaleStatus = new SimpleStringProperty("Desconocido");
    private final StringProperty readerStatus = new SimpleStringProperty("Desconocido");
    private final StringProperty lastWeight = new SimpleStringProperty("-- kg");
    private final StringProperty statusMessage = new SimpleStringProperty();

    private final BooleanProperty busy = new SimpleBooleanProperty(false);

    private final ListProductsUseCase listProductsUseCase;
    private final ServiceLocator.AdminService adminService;
    private RegistrarVentaUseCase registrarVentaUseCase;

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    // Datos para commit
    private Usuario cajero;
    private BigDecimal received = BigDecimal.ZERO;
    private Runnable onSaleCompleted;

    public CajeroViewModel(ListProductsUseCase listProductsUseCase, ServiceLocator.AdminService adminService) {
        this.listProductsUseCase = listProductsUseCase;
        this.adminService = adminService;
    }

    /* ------------------ Properties / accessors ------------------ */

    public ObservableList<Producto> getFilteredProducts() { return filteredProducts; }
    public ObservableList<CartItem> getCart() { return cart; }

    public ObjectProperty<BigDecimal> subtotalProperty() { return subtotal; }
    public ObjectProperty<BigDecimal> ivaProperty() { return iva; }
    public ObjectProperty<BigDecimal> totalProperty() { return total; }

    public StringProperty scaleStatusProperty() { return scaleStatus; }
    public StringProperty readerStatusProperty() { return readerStatus; }
    public StringProperty lastWeightProperty() { return lastWeight; }

    public StringProperty statusMessageProperty() { return statusMessage; }
    public BooleanProperty busyProperty() { return busy; }

    /* ------------------ Productos ------------------ */

    public void loadProducts() {
        busy.set(true);
        statusMessage.set("Cargando productos...");
        Task<java.util.List<Producto>> t = new Task<>() {
            @Override
            protected java.util.List<Producto> call() throws Exception {
                // El usecase en tu proyecto espera execute(Void) -> pasamos null
                return listProductsUseCase.execute(null);
            }
        };
        t.setOnSucceeded(evt -> {
            java.util.List<Producto> list = t.getValue();
            if (list == null) list = Collections.emptyList();
            allProducts.setAll(list);
            filteredProducts.setAll(list);
            busy.set(false);
            statusMessage.set("");
            recalcTotals();
        });
        t.setOnFailed(evt -> {
            busy.set(false);
            statusMessage.set("Error cargando productos: " + t.getException().getMessage());
            t.getException().printStackTrace();
        });
        executor.execute(t);
    }

    public void filterBy(String q, boolean onlyPeso) {
        java.util.List<Producto> out = new ArrayList<>();
        for (Producto p : allProducts) {
            boolean ok = (q == null || q.isBlank()) ||
                    p.getNombre().toLowerCase().contains(q.toLowerCase()) ||
                    (p.getCodigo() != null && p.getCodigo().toLowerCase().contains(q.toLowerCase()));
            if (!ok) continue;
            if (onlyPeso && p.getTipo() != Producto.TipoProducto.PESO) continue;
            out.add(p);
        }
        Platform.runLater(() -> filteredProducts.setAll(out));
    }

    /* ------------------ Carrito ------------------ */

    public void addOrMergeCartItem(Producto p, BigDecimal qty) {
        if (p == null || qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) return;
        boolean merged = false;
        for (CartItem it : cart) {
            if (Objects.equals(it.product.getProductoId(), p.getProductoId())) {
                it.quantity = it.quantity.add(qty);
                it.recalcSubtotal();
                merged = true;
                break;
            }
        }
        if (!merged) cart.add(new CartItem(p, qty));
        recalcTotals();
    }

    public void updateItemQuantity(CartItem item, BigDecimal newQty) {
        if (item == null || newQty == null || newQty.compareTo(BigDecimal.ZERO) <= 0) return;
        item.quantity = newQty;
        item.recalcSubtotal();
        recalcTotals();
    }

    public void removeItem(CartItem item) { cart.remove(item); recalcTotals(); }
    public void clearCart() { cart.clear(); recalcTotals(); }

    public void recalcTotals() {
        BigDecimal st = BigDecimal.ZERO;
        for (CartItem it : cart) st = st.add(it.getSubtotal());
        subtotal.set(st.setScale(2, RoundingMode.HALF_UP));
        iva.set(BigDecimal.ZERO); // placeholder si no aplicas IVA
        total.set(subtotal.get().add(iva.get()).setScale(2, RoundingMode.HALF_UP));
    }

    /* ------------------ Scale integration ------------------ */

    public void readWeightAndAdd(Producto p) {
        busy.set(true);
        statusMessage.set("Leyendo báscula...");
        Task<Void> t = new Task<>() {
            BigDecimal weight = null;
            String error = null;
            @Override
            protected Void call() {
                try {
                    java.util.List<String> configs = adminService.listDeviceConfigs();
                    String port = null;
                    String params = "{}";
                    for (String c : configs) {
                        if (c == null) continue;
                        if (c.toUpperCase().startsWith("BASCULA@")) {
                            String after = c.substring("BASCULA@".length());
                            int pipe = after.indexOf("|");
                            if (pipe >= 0) { port = after.substring(0, pipe); params = after.substring(pipe + 1); }
                            else port = after;
                            break;
                        }
                    }
                    if (port == null || port.isBlank()) {
                        error = "Configuración de báscula no encontrada (ADMIN debe configurar).";
                        return null;
                    }
                    int baud = 9600;
                    try {
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"baudRate\"\\s*:\\s*(\\d+)").matcher(params);
                        if (m.find()) baud = Integer.parseInt(m.group(1));
                    } catch (Exception ignored) {}

                    try {
                        Class<?> cls = Class.forName("com.miempresa.fruver.infra.hardware.scale.ScaleService");
                        Object svc = cls.getDeclaredConstructor().newInstance();
                        try {
                            try { cls.getMethod("open", String.class, int.class).invoke(svc, port, baud); }
                            catch (NoSuchMethodException nm) {
                                try { cls.getMethod("open", String.class).invoke(svc, port); } catch (NoSuchMethodException ignored) {}
                            }
                            Object val = null;
                            try { val = cls.getMethod("readWeightKg").invoke(svc); } catch (NoSuchMethodException ignored) {}
                            if (val == null) {
                                try { val = cls.getMethod("readWeight").invoke(svc); } catch (NoSuchMethodException ignored) {}
                            }
                            if (val == null) {
                                try { val = cls.getMethod("readWeightGrams").invoke(svc); } catch (NoSuchMethodException ignored) {}
                                if (val instanceof Number) {
                                    Number n = (Number) val;
                                    weight = BigDecimal.valueOf(n.doubleValue()).divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
                                }
                            } else {
                                if (val instanceof Number) weight = BigDecimal.valueOf(((Number) val).doubleValue()).setScale(3, RoundingMode.HALF_UP);
                                else if (val instanceof String) weight = new BigDecimal(((String) val).trim()).setScale(3, RoundingMode.HALF_UP);
                            }
                        } finally {
                            try { cls.getMethod("close").invoke(svc); } catch (Throwable ignored) {}
                        }
                    } catch (ClassNotFoundException cnf) { error = "ScaleService no disponible en classpath."; }
                } catch (Throwable t) { error = t.getMessage(); }
                return null;
            }

            @Override
            protected void succeeded() {
                busy.set(false);
                if (error != null) statusMessage.set(error);
                else if (weight == null) statusMessage.set("No se pudo leer peso.");
                else {
                    lastWeight.set(weight.stripTrailingZeros().toPlainString() + " kg");
                    addOrMergeCartItem(p, weight.setScale(3, RoundingMode.HALF_UP));
                    statusMessage.set("Peso leído: " + lastWeight.get());
                }
            }

            @Override
            protected void failed() {
                busy.set(false);
                statusMessage.set("Error leyendo báscula: " + getException().getMessage());
            }
        };
        executor.execute(t);
    }

    /* ------------------ Sale commit ------------------ */

    public void setRegistrarVentaUseCase(RegistrarVentaUseCase uc) { this.registrarVentaUseCase = uc; }
    public void setCajero(Usuario u) { this.cajero = u; }
    public void setReceived(BigDecimal r) { this.received = r; }
    public void setOnSaleCompleted(Runnable r) { this.onSaleCompleted = r; }

    public void commitSale() {
        if (cart.isEmpty()) { statusMessage.set("Carrito vacío."); return; }
        busy.set(true);
        statusMessage.set("Registrando venta...");
        Task<Void> t = new Task<>() {
            @Override
            protected Void call() throws Exception {
                BigDecimal totalVal = total.get();
                BigDecimal vuelto = (received == null ? BigDecimal.ZERO : received.subtract(totalVal).setScale(2, RoundingMode.HALF_UP));

                // Construir lista de VentaItem usando constructor con 5 parámetros:
                // VentaItem(Integer itemId, Integer ventaId, Integer productoId, BigDecimal cantidad, BigDecimal precioUnit)
                java.util.List<VentaItem> items = new ArrayList<>();
                for (CartItem it : cart) {
                    VentaItem vi = new VentaItem(
                            null, // itemId -> NULL para que DB lo genere
                            null, // ventaId -> NULL, será asignado al persistir
                            it.product.getProductoId(),
                            it.quantity,
                            it.product.getPrecioUnitario()
                    );
                    items.add(vi);
                }

                // Intentar obtener usecase si no fue inyectado
                if (registrarVentaUseCase == null) {
                    try { registrarVentaUseCase = ServiceLocator.getRegistrarVentaUseCase(); } catch (Throwable ignored) {}
                }

                if (registrarVentaUseCase != null) {
                    // Firma esperada: execute(List<VentaItem>)
                    registrarVentaUseCase.execute(items);
                } else {
                    // dry-run: log para depuración
                    System.out.println("[DryRun RegistrarVenta] total=" + totalVal + ", recibido=" + received + ", vuelto=" + vuelto);
                    for (VentaItem vi : items) System.out.println("  item: producto=" + vi.getProductoId() + " qty=" + vi.getCantidad() + " precio=" + vi.getPrecioUnit());
                }

                return null;
            }

            @Override
            protected void succeeded() {
                busy.set(false);
                cart.clear();
                recalcTotals();
                statusMessage.set("Venta completada.");
                if (onSaleCompleted != null) onSaleCompleted.run();
            }

            @Override
            protected void failed() {
                busy.set(false);
                statusMessage.set("Error registrando venta: " + getException().getMessage());
                getException().printStackTrace();
            }
        };
        executor.execute(t);
    }

    /* ------------------ Barcode helper ------------------ */

    public boolean addProductByBarcode(String codigo) {
        if (codigo == null || codigo.isBlank()) return false;
        for (Producto p : allProducts) {
            if (codigo.equalsIgnoreCase(p.getCodigo())) {
                if (p.getTipo() == Producto.TipoProducto.PESO) readWeightAndAdd(p);
                else addOrMergeCartItem(p, BigDecimal.ONE);
                return true;
            }
        }
        statusMessage.set("Producto no registrado: " + codigo);
        return false;
    }

    /* ------------------ Device indicators ------------------ */

    public void refreshDeviceIndicators() {
        executor.execute(() -> {
            try {
                java.util.List<String> configs = adminService.listDeviceConfigs();
                boolean hasScale = configs.stream().anyMatch(s -> s != null && s.toUpperCase().startsWith("BASCULA@"));
                boolean hasReader = configs.stream().anyMatch(s -> s != null && s.toUpperCase().startsWith("LECTOR@"));
                Platform.runLater(() -> {
                    scaleStatus.set(hasScale ? "Configurada" : "No configurada");
                    readerStatus.set(hasReader ? "Configurado" : "No configurado");
                });
            } catch (Throwable t) {
                Platform.runLater(() -> { scaleStatus.set("Error"); readerStatus.set("Error"); });
            }
        });
    }

    /* ------------------ DTO / small class ------------------ */

    public static class CartItem {
        public final Producto product;
        public BigDecimal quantity;
        private BigDecimal subtotal;

        public CartItem(Producto p, BigDecimal q) {
            this.product = p;
            this.quantity = q;
            recalcSubtotal();
        }
        public void recalcSubtotal() {
            this.subtotal = product.getPrecioUnitario().multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        }
        public BigDecimal getSubtotal() { return subtotal; }
        public BigDecimal getQuantity() { return quantity; }
        public Producto getProduct() { return product; }
    }
}
