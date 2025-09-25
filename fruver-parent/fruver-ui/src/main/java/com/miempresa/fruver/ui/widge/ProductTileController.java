package com.miempresa.fruver.ui.widge;

import com.miempresa.fruver.domain.model.Producto;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Controller del componente ProductTile.
 */
public class ProductTileController {

    @FXML private VBox root;
    @FXML private ImageView imgProduct;
    @FXML private Label lblName;
    @FXML private Label lblMeta;
    @FXML private Label lblBadge;
    @FXML private Button btnAdd;

    private Producto producto;
    private Consumer<Producto> onAdd = p -> {};

    @FXML
    private void initialize() {
        // click en tile o boton llama al callback onAdd
        root.setOnMouseClicked(ev -> {
            if (producto != null) onAdd.accept(producto);
        });
        btnAdd.setOnAction(ev -> {
            if (producto != null) onAdd.accept(producto);
        });
    }

    public void setProducto(Producto p) {
        this.producto = p;
        if (p == null) {
            lblName.setText("");
            lblMeta.setText("");
            lblBadge.setText("");
            imgProduct.setImage(null);
            btnAdd.setDisable(true);
            return;
        }
        lblName.setText(p.getNombre());
        String meta = (p.getCodigo() == null ? "" : p.getCodigo() + " • ") + formatPrice(p.getPrecioUnitario());
        lblMeta.setText(meta);

        // USAR TipoProducto (corrige el error)
        lblBadge.setText(p.getTipo() == Producto.TipoProducto.PESO ? "PESO" : "UNIDAD");

        btnAdd.setDisable(false);

        try {
            if (p.getImagenPath() != null && !p.getImagenPath().isBlank()) {
                Image img = new Image("file:" + p.getImagenPath(), 64, 64, true, true, true);
                imgProduct.setImage(img);
            } else {
                imgProduct.setImage(null);
            }
        } catch (Exception ex) {
            imgProduct.setImage(null);
        }
    }

    private String formatPrice(java.math.BigDecimal v) {
        if (v == null) return "$0.00";
        return "$" + v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    public void setOnAdd(Consumer<Producto> onAdd) {
        this.onAdd = Objects.requireNonNull(onAdd);
    }

    public static Node createNode(Producto p, Consumer<Producto> onAdd) throws IOException {
        FXMLLoader loader = new FXMLLoader(ProductTileController.class.getResource("/fxml/ProductTile.fxml"));
        Node node = loader.load();
        ProductTileController ctrl = loader.getController();
        ctrl.setProducto(p);
        if (onAdd != null) ctrl.setOnAdd(onAdd);
        return node;
    }
}
