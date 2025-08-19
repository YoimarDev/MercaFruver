package com.miempresa.fruver.ui.viewmodel;

import com.miempresa.fruver.domain.model.Producto;
import com.miempresa.fruver.service.port.CreateProductRequest;
import com.miempresa.fruver.service.usecase.CreateProductUseCase;
import com.miempresa.fruver.service.usecase.DeleteProductUseCase;
import com.miempresa.fruver.service.usecase.ListProductsUseCase;
import com.miempresa.fruver.service.usecase.UpdateProductUseCase;
import com.miempresa.fruver.ui.ServiceLocator;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ViewModel del Supervisor (mejorado).
 * - Parsing robusto para precios y cantidades.
 * - Normalización: precios -> scale=2; cantidades/stock -> scale=3.
 * - Mantiene listas filtradas y delega en usecases.
 */
public class SupervisorViewModel {

    private final ListProductsUseCase listProductsUseCase;
    private final CreateProductUseCase createProductUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final DeleteProductUseCase deleteProductUseCase;

    private final ObservableList<Producto> allProducts = FXCollections.observableArrayList();

    private final FilteredList<Producto> filteredPeso =
            new FilteredList<>(allProducts, p -> p != null && p.getTipo() != null && p.getTipo().name().equalsIgnoreCase("PESO"));

    private final FilteredList<Producto> filteredUnidad =
            new FilteredList<>(allProducts, p -> p != null && p.getTipo() != null && p.getTipo().name().equalsIgnoreCase("UNIDAD"));

    private String filter = "";

    public SupervisorViewModel() {
        this.listProductsUseCase = ServiceLocator.getListProductsUseCase();
        this.createProductUseCase = ServiceLocator.getCreateProductUseCase();
        this.updateProductUseCase = ServiceLocator.getUpdateProductUseCase();
        this.deleteProductUseCase = ServiceLocator.getDeleteProductUseCase();
    }

    public ObservableList<Producto> getProductosPeso() { return filteredPeso; }
    public ObservableList<Producto> getProductosUnidad() { return filteredUnidad; }

    public void setFilter(String filter) {
        this.filter = filter == null ? "" : filter.trim().toLowerCase();
        applyFilter();
    }

    private void applyFilter() {
        final String f = this.filter;
        Platform.runLater(() -> {
            if (f == null || f.isBlank()) {
                filteredPeso.setPredicate(p -> p != null && p.getTipo() != null && p.getTipo().name().equalsIgnoreCase("PESO"));
                filteredUnidad.setPredicate(p -> p != null && p.getTipo() != null && p.getTipo().name().equalsIgnoreCase("UNIDAD"));
            } else {
                filteredPeso.setPredicate(p -> p != null && p.getTipo() != null && p.getTipo().name().equalsIgnoreCase("PESO") &&
                        ((p.getNombre() != null && p.getNombre().toLowerCase().contains(f)) ||
                                (p.getCodigo() != null && p.getCodigo().toLowerCase().contains(f))));
                filteredUnidad.setPredicate(p -> p != null && p.getTipo() != null && p.getTipo().name().equalsIgnoreCase("UNIDAD") &&
                        ((p.getNombre() != null && p.getNombre().toLowerCase().contains(f)) ||
                                (p.getCodigo() != null && p.getCodigo().toLowerCase().contains(f))));
            }
        });
    }

    public void loadProducts() {
        List<Producto> all = listProductsUseCase.execute(null);
        Platform.runLater(() -> {
            allProducts.setAll(all);
            applyFilter();
        });
    }

    /**
     * Normaliza y crea un producto:
     * - price -> scale 2
     * - stock / stockUmb -> scale 3
     * - si tipo UNIDAD entonces imagen forzada a null
     */
    public Producto createProduct(String codigo, String nombre, String tipo, String precioStr,
                                  String stockStr, String stockUmbStr, String imagenPath) {
        Objects.requireNonNull(codigo);
        Objects.requireNonNull(nombre);
        String t = tipo == null ? "UNIDAD" : tipo.trim().toUpperCase();

        BigDecimal precio = parsePriceOrNull(precioStr);
        BigDecimal stock = parseQuantityOrNull(stockStr);
        BigDecimal stockUmb = parseQuantityOrNull(stockUmbStr);

        String effectiveImage = ("UNIDAD".equalsIgnoreCase(t) ? null :
                (imagenPath == null || imagenPath.isBlank() ? null : imagenPath));

        CreateProductRequest req = new CreateProductRequest(
                null, codigo.trim(), nombre.trim(), t, precio, stock, stockUmb, effectiveImage
        );
        Producto created = createProductUseCase.execute(req);
        loadProducts();
        return created;
    }

    public Producto updateProduct(Integer productoId, String codigo, String nombre, String tipo,
                                  String precioStr, String stockStr, String stockUmbStr, String imagenPath) {
        Objects.requireNonNull(productoId);
        String t = tipo == null ? "" : tipo.trim().toUpperCase();

        BigDecimal precio = parsePriceOrNull(precioStr);
        BigDecimal stock = parseQuantityOrNull(stockStr);
        BigDecimal stockUmb = parseQuantityOrNull(stockUmbStr);

        String effectiveImage = ("UNIDAD".equalsIgnoreCase(t) ? null :
                (imagenPath == null || imagenPath.isBlank() ? null : imagenPath));

        CreateProductRequest req = new CreateProductRequest(
                productoId,
                codigo == null ? "" : codigo.trim(),
                nombre == null ? "" : nombre.trim(),
                t,
                precio,
                stock,
                stockUmb,
                effectiveImage
        );
        Producto updated = updateProductUseCase.execute(req);
        loadProducts();
        return updated;
    }

    public void deleteProduct(Integer productoId) {
        Objects.requireNonNull(productoId);
        deleteProductUseCase.execute(productoId);
        loadProducts();
    }

    /**
     * Parse price from free text input:
     * - accept "3.000", "3000", "3,000", "$3.000", "3 000" etc.
     * - return BigDecimal scaled to 2 decimals (HALF_UP) or null if empty/invalid
     */
    private BigDecimal parsePriceOrNull(String s) {
        if (s == null) return null;
        String raw = s.trim();
        if (raw.isBlank()) return null;
        raw = raw.replaceAll("[\\s\\u00A0]", "");
        raw = raw.replaceAll("[$€￡¥]", "");
        if (raw.contains(",") && !raw.contains(".")) {
            raw = raw.replace(',', '.');
        } else {
            long dotCount = raw.chars().filter(ch -> ch == '.').count();
            if (dotCount > 1) {
                raw = raw.replace(".", "");
            }
            long commaCount = raw.chars().filter(ch -> ch == ',').count();
            if (commaCount > 1 && !raw.contains(".")) {
                raw = raw.replace(",", "");
            }
        }
        try {
            BigDecimal bd = new BigDecimal(raw);
            return bd.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse quantity/stock from free text input:
     * - accepts "10", "10.5", "10,500", "10.000" (thousands) etc.
     * - returns BigDecimal scaled to 3 decimals (HALF_UP)
     */
    private BigDecimal parseQuantityOrNull(String s) {
        if (s == null) return null;
        String raw = s.trim();
        if (raw.isBlank()) return null;
        raw = raw.replaceAll("[\\s\\u00A0]", "");
        if (raw.contains(",") && !raw.contains(".")) {
            raw = raw.replace(',', '.');
        } else {
            long dotCount = raw.chars().filter(ch -> ch == '.').count();
            if (dotCount > 1) {
                raw = raw.replace(".", "");
            }
            long commaCount = raw.chars().filter(ch -> ch == ',').count();
            if (commaCount > 1 && !raw.contains(".")) {
                raw = raw.replace(",", "");
            }
        }
        try {
            BigDecimal bd = new BigDecimal(raw);
            return bd.setScale(3, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    /* ---------------------- Estadísticas locales ---------------------- */

    public int getTotalProductsCount() { return allProducts.size(); }
    public int getPesoCount() {
        return (int) allProducts.stream()
                .filter(p -> p.getTipo() != null && p.getTipo().name().equalsIgnoreCase("PESO"))
                .count();
    }
    public int getUnidadCount() {
        return (int) allProducts.stream()
                .filter(p -> p.getTipo() != null && p.getTipo().name().equalsIgnoreCase("UNIDAD"))
                .count();
    }

    public List<Producto> topByStock(int n) {
        return allProducts.stream()
                .filter(p -> p.getStockActual() != null)
                .sorted(Comparator.comparing(Producto::getStockActual).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    public StatsSnapshot refreshStats() {
        StatsSnapshot s = new StatsSnapshot();
        s.total = getTotalProductsCount();
        s.peso = getPesoCount();
        s.unidad = getUnidadCount();
        s.topByStock = topByStock(10);
        return s;
    }

    public static class StatsSnapshot {
        public int total;
        public int peso;
        public int unidad;
        public List<Producto> topByStock;
    }

    public Map<String, Object> fetchStatistics(LocalDate from, LocalDate to) {
        Map<String, Object> out = new HashMap<>();
        try {
            try {
                var uc = ServiceLocator.getObtenerEstadisticasUseCase();
                Map<String, Object> res = uc.execute(new LocalDate[]{from, to});
                if (res != null) {
                    return res;
                }
            } catch (IllegalStateException ise) {
                System.out.println("[SupervisorViewModel] ObtenerEstadisticasUseCase no registrado, usando fallback local.");
            }

            int totalVentas = 0;
            out.put("totalVentas", totalVentas);

            List<Map<String, Object>> top = new ArrayList<>();
            List<Producto> topProd = topByStock(10);
            for (Producto p : topProd) {
                Map<String, Object> m = new HashMap<>();
                m.put("nombre", p.getNombre());
                m.put("stock", p.getStockActual());
                m.put("productoId", p.getProductoId());
                top.add(m);
            }
            out.put("topProductos", top);
            out.put("fallback", true);
            return out;

        } catch (Throwable t) {
            out.put("totalVentas", 0);
            out.put("topProductos", new ArrayList<>());
            out.put("error", t.getMessage());
            return out;
        }
    }
}
