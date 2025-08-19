package com.miempresa.fruver.ui.controller;

import com.miempresa.fruver.domain.model.Producto;
import com.miempresa.fruver.ui.util.ProductImageHelper;
import com.miempresa.fruver.ui.viewmodel.SupervisorViewModel;
import com.miempresa.fruver.ui.widget.ProductCell;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Controller JavaFX para la vista Supervisor.
 * Ejecuta tareas de UI y delega la lógica al SupervisorViewModel.
 */
public class SupervisorController {

    @FXML private TextField txtFiltro;
    @FXML private ListView<Producto> lvProductos;

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

    private SupervisorViewModel vm;

    @FXML
    public void initialize() {
        vm = new SupervisorViewModel();

        // Use custom cell to show image + info
        lvProductos.setCellFactory(list -> new ProductCell());
        lvProductos.setItems(vm.getProductos());

        cbTipo.getItems().setAll("PESO", "UNIDAD");

        // Filtro reactivo: la ViewModel aplica predicate
        txtFiltro.textProperty().addListener((obs, oldV, newV) -> vm.setFilter(newV));

        // Selection -> populate form
        lvProductos.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel == null) {
                clearForm();
            } else {
                populateForm(sel);
            }
        });

        // Cargar productos inicial (en background)
        runBackground(() -> {
            try {
                vm.loadProducts();
                Platform.runLater(() -> lblStatus.setText("Productos cargados."));
            } catch (Exception ex) {
                Platform.runLater(() -> lblStatus.setText("Error cargando productos: " + ex.getMessage()));
            }
        });
    }

    private void populateForm(Producto p) {
        txtCodigo.setText(p.getCodigo());
        txtNombre.setText(p.getNombre());
        cbTipo.setValue(p.getTipo().name());
        txtPrecio.setText(p.getPrecioUnitario() == null ? "" : p.getPrecioUnitario().toPlainString());
        txtStock.setText(p.getStockActual() == null ? "" : p.getStockActual().toPlainString());
        txtStockUmb.setText(p.getStockUmbral() == null ? "" : p.getStockUmbral().toPlainString());
        lblImagenPath.setText(p.getImagenPath() == null ? "" : p.getImagenPath());
        if (p.getImagenPath() != null && !p.getImagenPath().isBlank()) {
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
        lvProductos.getSelectionModel().clearSelection();
        clearForm();
    }

    @FXML
    private void onSubirImagen() {
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
                    lblStatus.setText("Producto creado (id=" + p.getProductoId() + ").");
                    vm.loadProducts(); // recargar
                });
            } catch (Exception ex) {
                Platform.runLater(() -> lblStatus.setText("Error crear producto: " + ex.getMessage()));
            }
        });
    }

    @FXML
    private void onActualizar() {
        Producto sel = lvProductos.getSelectionModel().getSelectedItem();
        if (sel == null) {
            lblStatus.setText("Selecciona un producto para actualizar.");
            return;
        }
        runBackground(() -> {
            try {
                // permitimos algunos campos vacíos para conservar valores existentes
                Producto updated = vm.updateProduct(
                        sel.getProductoId(),
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
                });
            } catch (Exception ex) {
                Platform.runLater(() -> lblStatus.setText("Error actualizar producto: " + ex.getMessage()));
            }
        });
    }

    @FXML
    private void onEliminar() {
        Producto sel = lvProductos.getSelectionModel().getSelectedItem();
        if (sel == null) { lblStatus.setText("Selecciona un producto a eliminar."); return; }
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Eliminar producto '" + sel.getNombre() + "'?", ButtonType.YES, ButtonType.NO);
        a.setHeaderText("Confirmar eliminación");
        a.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                runBackground(() -> {
                    try {
                        vm.deleteProduct(sel.getProductoId());
                        Platform.runLater(() -> {
                            lblStatus.setText("Producto eliminado.");
                            vm.loadProducts();
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> lblStatus.setText("Error eliminando producto: " + ex.getMessage()));
                    }
                });
            }
        });
    }

    @FXML
    private void onSalir() {
        // dependencia de tu app: normalmente deberías notificar al router/navegador de la app para volver al login.
        // Aquí simplemente cerramos la ventana actual.
        Window w = btnSalir.getScene().getWindow();
        w.hide();
    }

    private void validateFormForCreate() {
        if (txtCodigo.getText() == null || txtCodigo.getText().isBlank())
            throw new IllegalArgumentException("Código es obligatorio");
        if (txtNombre.getText() == null || txtNombre.getText().isBlank())
            throw new IllegalArgumentException("Nombre es obligatorio");
        if (cbTipo.getValue() == null || cbTipo.getValue().isBlank())
            throw new IllegalArgumentException("Tipo es obligatorio (PESO o UNIDAD)");
    }

    private void runBackground(Runnable r) {
        Thread t = new Thread(r, "supervisor-bg");
        t.setDaemon(true);
        t.start();
    }
}
