package com.miempresa.fruver.ui.controller;

import com.miempresa.fruver.domain.model.Producto;
import com.miempresa.fruver.domain.model.Usuario;
import com.miempresa.fruver.service.usecase.ListProductsUseCase;
import com.miempresa.fruver.service.usecase.RegistrarVentaUseCase;
import com.miempresa.fruver.ui.ServiceLocator;
import com.miempresa.fruver.ui.widge.ProductTileController;
import com.miempresa.fruver.ui.viewmodel.CajeroViewModel;
import com.miempresa.fruver.ui.viewmodel.CajeroViewModel.CartItem;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controller para CajeroView.fxml
 *
 * Aclaraciones:
 *  - Todos los métodos referenciados desde FXML están anotados con @FXML.
 *  - Este controlador delega la lógica al CajeroViewModel.
 */
public class CajeroController implements Initializable {

    @FXML private TextField txtFilter;
    @FXML private TilePane tileProducts;
    @FXML private ComboBox<String> cbCategoria;
    @FXML private ToggleButton tbOnlyPeso;
    @FXML private Label lblLastWeight;
    @FXML private Label lblScaleStatus;
    @FXML private Label lblReaderStatus;
    @FXML private ListView<CartItem> lvCart;
    @FXML private Label lblSubtotal;
    @FXML private Label lblIva;
    @FXML private Label lblTotal;
    @FXML private Button btnEditLine;
    @FXML private Button btnRemoveLine;
    @FXML private Button btnClearCart;
    @FXML private TextField txtReceived;
    @FXML private Label lblChange;
    @FXML private FlowPane flowBills;     // <- CORRECCIÓN: FlowPane, no VBox
    @FXML private Label lblMessage;
    @FXML private Button btnRefreshProducts;
    @FXML private Button btnSalir;
    @FXML private Label lblWelcome;

    private CajeroViewModel vm;
    private Usuario currentUser;
    private Runnable onLogout;
    private RegistrarVentaUseCase registrarVentaUseCase;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // inicialización mínima; el wiring completo se hace en init(...)
    }

    /**
     * Inicializa el controller después de cargar FXML.
     * @param usuario usuario que hizo login (rol CAJERO)
     * @param onLogout callback para volver al login
     */
    public void init(Usuario usuario, Runnable onLogout) {
        this.currentUser = usuario;
        this.onLogout = onLogout;

        // Obtener usecases / servicios desde ServiceLocator
        ListProductsUseCase listProductsUseCase = ServiceLocator.getListProductsUseCase();
        this.vm = new CajeroViewModel(listProductsUseCase, ServiceLocator.getAdminService());

        // RegistrarVentaUseCase es opcional en algunos entornos (demo); intentamos obtenerlo
        try {
            this.registrarVentaUseCase = ServiceLocator.getRegistrarVentaUseCase();
            this.vm.setRegistrarVentaUseCase(this.registrarVentaUseCase);
        } catch (Throwable ignored) {}

        // UI bindings
        lblWelcome.setText("Caja · " + (currentUser == null ? "Invitado" : currentUser.getNombre()));
        lblScaleStatus.textProperty().bind(vm.scaleStatusProperty());
        lblReaderStatus.textProperty().bind(vm.readerStatusProperty());
        lblLastWeight.textProperty().bind(vm.lastWeightProperty());

        // Los labels de importe se formatean desde bindings (añaden $ desde Java)
        lblSubtotal.textProperty().bind(Bindings.createStringBinding(() -> formatCurrency(vm.subtotalProperty().get()), vm.subtotalProperty()));
        lblIva.textProperty().bind(Bindings.createStringBinding(() -> formatCurrency(vm.ivaProperty().get()), vm.ivaProperty()));
        lblTotal.textProperty().bind(Bindings.createStringBinding(() -> formatCurrency(vm.totalProperty().get()), vm.totalProperty()));
        lblMessage.textProperty().bind(vm.statusMessageProperty());

        // Carrito
        lvCart.setItems(vm.getCart());
        lvCart.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(CartItem it, boolean empty) {
                super.updateItem(it, empty);
                if (empty || it == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String qty = it.getQuantity().stripTrailingZeros().toPlainString();
                    setText(it.getProduct().getNombre() + " • " + qty + " × " + formatCurrency(it.getProduct().getPrecioUnitario()) +
                            " = " + formatCurrency(it.getSubtotal()));
                }
            }
        });

        // selección en carrito habilita botones
        lvCart.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            btnEditLine.setDisable(n == null);
            btnRemoveLine.setDisable(n == null);
        });
        btnEditLine.setDisable(true);
        btnRemoveLine.setDisable(true);

        // Filtros y listeners
        txtFilter.textProperty().addListener((obs, o, n) -> vm.filterBy(n, tbOnlyPeso.isSelected()));
        tbOnlyPeso.selectedProperty().addListener((obs, o, n) -> vm.filterBy(txtFilter.getText(), n));
        cbCategoria.valueProperty().addListener((obs, o, n) -> vm.filterBy(txtFilter.getText(), tbOnlyPeso.isSelected()));

        // Renderizar tiles cuando la lista filtrada cambie
        vm.getFilteredProducts().addListener((javafx.collections.ListChangeListener<? super Producto>) c -> {
            renderTiles(vm.getFilteredProducts());
        });

        // refresh manual (tambien existe el handler onRefreshProducts para FXML)
        btnRefreshProducts.setOnAction(e -> vm.loadProducts());

        // construir botones de billetes
        buildBillButtons();

        // recalcular totales cuando cambie carrito
        vm.getCart().addListener((javafx.collections.ListChangeListener<? super CartItem>) c -> vm.recalcTotals());

        // acciones de botones (programáticas)
        btnEditLine.setOnAction(e -> onEditLine());
        btnRemoveLine.setOnAction(e -> onRemoveLine());
        btnClearCart.setOnAction(e -> onClearCart());
        txtReceived.setOnKeyPressed(ev -> { if (ev.getCode() == KeyCode.ENTER) onCharge(); });
        btnSalir.setOnAction(e -> onSalir());

        // cargar inicialmente
        vm.loadProducts();
        vm.refreshDeviceIndicators();
    }

    private void renderTiles(List<Producto> products) {
        Platform.runLater(() -> {
            tileProducts.getChildren().clear();
            tileProducts.setPrefColumns(4);
            for (Producto p : products) {
                try {
                    Node tile = ProductTileController.createNode(p, prod -> {
                        // Comportamiento al pulsar tile: si es PESO leer báscula, si UNIDAD añadir 1
                        if (prod.getTipo() == Producto.TipoProducto.PESO) vm.readWeightAndAdd(prod);
                        else vm.addOrMergeCartItem(prod, BigDecimal.ONE);
                    });
                    tileProducts.getChildren().add(tile);
                } catch (IOException e) {
                    tileProducts.getChildren().add(createFallbackTile(p));
                }
            }
        });
    }

    private javafx.scene.Node createFallbackTile(Producto p) {
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(6);
        root.getStyleClass().add("user-tile-root");
        root.setPrefWidth(230);
        root.setPrefHeight(110);
        javafx.scene.control.Label name = new javafx.scene.control.Label(p.getNombre());
        name.getStyleClass().add("user-tile-name");
        javafx.scene.control.Label meta = new javafx.scene.control.Label(formatCurrency(p.getPrecioUnitario()) + " • " + (p.getTipo() == Producto.TipoProducto.PESO ? "PESO" : "UNIDAD"));
        meta.getStyleClass().add("product-meta");
        javafx.scene.control.Label badge = new javafx.scene.control.Label(p.getTipo() == Producto.TipoProducto.PESO ? "PESO" : "UNIDAD");
        badge.getStyleClass().addAll("product-type-badge", p.getTipo() == Producto.TipoProducto.PESO ? "peso":"unidad");
        root.getChildren().addAll(name, meta, badge);
        root.setOnMouseClicked(ev -> {
            if (p.getTipo() == Producto.TipoProducto.PESO) vm.readWeightAndAdd(p);
            else vm.addOrMergeCartItem(p, BigDecimal.ONE);
        });
        return root;
    }

    /* ------------------ Handlers referenciados desde FXML ------------------ */

    @FXML
    private void onRefreshProducts() {
        if (vm != null) vm.loadProducts();
    }

    @FXML
    private void onEditLine() {
        CartItem sel = lvCart.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        TextInputDialog d = new TextInputDialog(sel.getQuantity().toPlainString());
        d.setTitle("Editar cantidad");
        d.setHeaderText("Producto: " + sel.getProduct().getNombre());
        d.setContentText("Cantidad:");
        Optional<String> r = d.showAndWait();
        r.ifPresent(s -> {
            try {
                BigDecimal q = new BigDecimal(s.replace(",", ".")).setScale(3, RoundingMode.HALF_UP);
                vm.updateItemQuantity(sel, q);
            } catch (Exception ex) {
                showAlert(Alert.AlertType.WARNING, "Cantidad inválida", "Ingrese una cantidad válida.");
            }
        });
    }

    @FXML
    private void onRemoveLine() {
        CartItem sel = lvCart.getSelectionModel().getSelectedItem();
        if (sel != null) vm.removeItem(sel);
    }

    @FXML
    private void onClearCart() {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "¿Limpiar carrito?", ButtonType.YES, ButtonType.NO);
        Optional<ButtonType> r = a.showAndWait();
        if (r.isPresent() && r.get() == ButtonType.YES) vm.clearCart();
    }

    @FXML
    private void onCharge() {
        if (vm == null || vm.getCart().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Carrito vacío", "No hay productos en el carrito.");
            return;
        }
        BigDecimal received;
        try {
            String txt = txtReceived == null ? null : txtReceived.getText();
            if (txt == null || txt.isBlank()) {
                showAlert(Alert.AlertType.WARNING, "Monto recibido requerido", "Ingresa monto recibido o pulsa un billete.");
                return;
            }
            received = new BigDecimal(txt.replace(",", ".")).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception ex) {
            showAlert(Alert.AlertType.WARNING, "Monto inválido", "Revisa el monto ingresado.");
            return;
        }
        BigDecimal total = vm.totalProperty().get();
        BigDecimal change = received.subtract(total).setScale(2, RoundingMode.HALF_UP);
        if (change.compareTo(BigDecimal.ZERO) < 0) {
            showAlert(Alert.AlertType.WARNING, "Monto insuficiente", "El dinero recibido es menor que el total.");
            return;
        }

        Alert conf = new Alert(Alert.AlertType.CONFIRMATION);
        conf.setTitle("Confirmar cobro");
        conf.setHeaderText("Total: " + formatCurrency(total) + " • Recibido: " + formatCurrency(received));
        conf.setContentText("¿Completar la venta?");
        Optional<ButtonType> r = conf.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) return;

        vm.setCajero(currentUser);
        vm.setReceived(received);
        vm.setOnSaleCompleted(() -> Platform.runLater(() -> {
            if (txtReceived != null) txtReceived.clear();
            if (lblChange != null) lblChange.setText(formatCurrency(BigDecimal.ZERO));
            showAlert(Alert.AlertType.INFORMATION, "Venta", "Venta completada correctamente.");
        }));
        vm.commitSale();
    }

    @FXML
    private void onExactReceived() {
        if (vm == null || vm.totalProperty().get() == null) return;
        if (txtReceived != null) {
            txtReceived.setText(vm.totalProperty().get().toPlainString());
            try {
                BigDecimal rec = new BigDecimal(txtReceived.getText().replace(",", "."));
                BigDecimal ch = rec.subtract(vm.totalProperty().get()).setScale(2, RoundingMode.HALF_UP);
                if (ch.compareTo(BigDecimal.ZERO) >= 0 && lblChange != null) lblChange.setText(formatCurrency(ch));
            } catch (Exception ignored) {}
        }
    }

    /** Botones rápidos de billetes (aux) */
    private void buildBillButtons() {
        int[] bills = new int[]{1000,2000,5000,10000,20000,50000,100000};
        for (int b : bills) {
            Button btn = new Button(String.valueOf(b));
            btn.setPrefWidth(100);
            btn.getStyleClass().add("primary-button");
            btn.setOnAction(e -> {
                BigDecimal current = BigDecimal.ZERO;
                try {
                    String t = (txtReceived == null || txtReceived.getText() == null) ? "" : txtReceived.getText();
                    current = new BigDecimal(t.isBlank() ? "0" : t.replace(",", "."));
                } catch (Exception ex) { current = BigDecimal.ZERO; }
                current = current.add(BigDecimal.valueOf(b)).setScale(2, RoundingMode.HALF_UP);
                if (txtReceived != null) txtReceived.setText(current.toPlainString());
                try {
                    BigDecimal rec = new BigDecimal(txtReceived.getText().replace(",", "."));
                    BigDecimal ch = rec.subtract(vm.totalProperty().get()).setScale(2, RoundingMode.HALF_UP);
                    if (ch.compareTo(BigDecimal.ZERO) >= 0 && lblChange != null) lblChange.setText(formatCurrency(ch));
                } catch (Exception ignored) {}
            });
            // FlowPane soporta getChildren()
            flowBills.getChildren().add(btn);
        }
    }

    private String formatCurrency(BigDecimal v) {
        if (v == null) return "$0.00";
        return "$" + v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private void showAlert(Alert.AlertType type, String title, String text) {
        Platform.runLater(() -> {
            Alert a = new Alert(type);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(text);
            a.showAndWait();
        });
    }

    /**
     * Logout / volver al login.
     */
    @FXML
    private void onSalir() {
        if (onLogout != null) {
            Platform.runLater(onLogout);
            return;
        }
        com.miempresa.fruver.service.security.SecurityContext.clear();
        Platform.runLater(() -> {
            if (btnSalir != null && btnSalir.getScene() != null && btnSalir.getScene().getWindow() != null)
                btnSalir.getScene().getWindow().hide();
        });
    }
}
