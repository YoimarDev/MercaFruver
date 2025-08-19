package com.miempresa.fruver.ui.viewmodel;

import com.miempresa.fruver.domain.model.Producto;
import com.miempresa.fruver.service.usecase.CreateProductUseCase;
import com.miempresa.fruver.service.usecase.DeleteProductUseCase;
import com.miempresa.fruver.service.usecase.ListProductsUseCase;
import com.miempresa.fruver.service.usecase.UpdateProductUseCase;
import com.miempresa.fruver.service.port.CreateProductRequest;
import com.miempresa.fruver.ui.ServiceLocator;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * ViewModel del Supervisor.
 * - Mantiene una lista maestra (allProducts) y una vista filtrada (filteredProducts).
 * - Las operaciones CRUD delegan en usecases provistos por ServiceLocator.
 */
public class SupervisorViewModel {

    private final ListProductsUseCase listProductsUseCase;
    private final CreateProductUseCase createProductUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final DeleteProductUseCase deleteProductUseCase;

    private final ObservableList<Producto> allProducts = FXCollections.observableArrayList();
    private final FilteredList<Producto> filteredProducts = new FilteredList<>(allProducts, p -> true);

    private String filter = "";

    public SupervisorViewModel() {
        this.listProductsUseCase = ServiceLocator.getListProductsUseCase();
        this.createProductUseCase = ServiceLocator.getCreateProductUseCase();
        this.updateProductUseCase = ServiceLocator.getUpdateProductUseCase();
        this.deleteProductUseCase = ServiceLocator.getDeleteProductUseCase();
    }

    /**
     * Lista observable (filtrada) que puede conectarse directamente al ListView.
     */
    public ObservableList<Producto> getProductos() {
        return FXCollections.unmodifiableObservableList(FXCollections.observableList(filteredProducts));
    }

    /**
     * Ajusta el texto de filtro (se aplica a nombre y código).
     */
    public void setFilter(String filter) {
        this.filter = filter == null ? "" : filter.trim().toLowerCase();
        applyFilter();
    }

    private void applyFilter() {
        final String f = this.filter;
        Platform.runLater(() -> {
            if (f == null || f.isBlank()) {
                filteredProducts.setPredicate(p -> true);
            } else {
                filteredProducts.setPredicate(p ->
                        (p.getNombre() != null && p.getNombre().toLowerCase().contains(f))
                                || (p.getCodigo() != null && p.getCodigo().toLowerCase().contains(f))
                );
            }
        });
    }

    /**
     * Carga productos desde el usecase (sin bloquear el hilo UI).
     * Nota: este método puede llamarse desde un background thread o desde la UI; actualiza la lista en Platform.runLater.
     */
    public void loadProducts() {
        List<Producto> all = listProductsUseCase.execute(null);
        Platform.runLater(() -> {
            allProducts.setAll(all);
            applyFilter();
        });
    }

    /**
     * Crea un producto usando strings desde UI. Retorna la entidad creada.
     */
    public Producto createProduct(String codigo,
                                  String nombre,
                                  String tipo,
                                  String precioStr,
                                  String stockStr,
                                  String stockUmbStr,
                                  String imagenPath) {
        Objects.requireNonNull(codigo);
        Objects.requireNonNull(nombre);
        String t = tipo == null ? "UNIDAD" : tipo;
        BigDecimal precio = parseDecimalOrNull(precioStr);
        BigDecimal stock = parseDecimalOrNull(stockStr);
        BigDecimal stockUmb = parseDecimalOrNull(stockUmbStr);
        CreateProductRequest req = new CreateProductRequest(null,
                codigo.trim(),
                nombre.trim(),
                t,
                precio,
                stock,
                stockUmb,
                (imagenPath == null || imagenPath.isBlank()) ? null : imagenPath
        );
        Producto created = createProductUseCase.execute(req);
        // actualizar cache local (mejor recargar para mantener consistencia)
        loadProducts();
        return created;
    }

    /**
     * Actualiza producto existente. Retorna la entidad actualizada.
     */
    public Producto updateProduct(Integer productoId,
                                  String codigo,
                                  String nombre,
                                  String tipo,
                                  String precioStr,
                                  String stockStr,
                                  String stockUmbStr,
                                  String imagenPath) {
        Objects.requireNonNull(productoId);
        BigDecimal precio = parseDecimalOrNull(precioStr);
        BigDecimal stock = parseDecimalOrNull(stockStr);
        BigDecimal stockUmb = parseDecimalOrNull(stockUmbStr);
        CreateProductRequest req = new CreateProductRequest(
                productoId,
                codigo == null ? "" : codigo.trim(),
                nombre == null ? "" : nombre.trim(),
                tipo == null ? "" : tipo,
                precio,
                stock,
                stockUmb,
                (imagenPath == null || imagenPath.isBlank()) ? null : imagenPath
        );
        Producto updated = updateProductUseCase.execute(req);
        loadProducts();
        return updated;
    }

    /**
     * Elimina producto por id.
     */
    public void deleteProduct(Integer productoId) {
        Objects.requireNonNull(productoId);
        deleteProductUseCase.execute(productoId);
        loadProducts();
    }

    private BigDecimal parseDecimalOrNull(String s) {
        try {
            if (s == null || s.isBlank()) return null;
            return new BigDecimal(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
