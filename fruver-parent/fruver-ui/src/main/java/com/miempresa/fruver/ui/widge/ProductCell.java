package com.miempresa.fruver.ui.widget;

import com.miempresa.fruver.domain.model.Producto;
import com.miempresa.fruver.ui.util.ProductImageHelper;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * ListCell personalizado para mostrar Producto con imagen, nombre y metadatos.
 *
 * Uso: lvProductos.setCellFactory(list -> new ProductCell());
 */
public class ProductCell extends ListCell<Producto> {

    private final HBox root = new HBox(10);
    private final ImageView iv = new ImageView();
    private final VBox metaBox = new VBox(4);
    private final Label lblName = new Label();
    private final Label lblMeta = new Label();
    private final Label lblStock = new Label();

    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));

    public ProductCell() {
        super();

        // ImageView configuración (tamaño uniforme)
        iv.setFitWidth(92);
        iv.setFitHeight(64);
        iv.setPreserveRatio(true);
        iv.getStyleClass().add("avatar"); // usa clase genérica del css

        lblName.getStyleClass().add("user-tile-name");
        lblMeta.getStyleClass().add("product-meta"); // puede no existir en styles.css pero no rompe
        lblStock.getStyleClass().add("label-status");

        metaBox.getChildren().addAll(lblName, lblMeta);
        metaBox.setAlignment(Pos.CENTER_LEFT);

        // Ensure the meta box grows if cell wide
        HBox.setHgrow(metaBox, Priority.ALWAYS);

        root.setAlignment(Pos.CENTER_LEFT);
        root.getStyleClass().add("user-tile-root"); // reutiliza tu clase de estilo global
        root.getChildren().addAll(iv, metaBox, lblStock);

        // Ajustes visuales
        lblStock.setMinWidth(80);
        lblStock.setAlignment(Pos.CENTER_RIGHT);
    }

    @Override
    protected void updateItem(Producto item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        // Nombre
        lblName.setText(item.getNombre() == null ? "(sin nombre)" : item.getNombre());

        // Meta: código, tipo y precio
        StringBuilder meta = new StringBuilder();
        if (item.getCodigo() != null && !item.getCodigo().isBlank()) {
            meta.append(item.getCodigo());
        }
        if (item.getTipo() != null) {
            if (meta.length() > 0) meta.append(" • ");
            meta.append(item.getTipo().name());
        }
        if (item.getPrecioUnitario() != null) {
            if (meta.length() > 0) meta.append(" • ");
            try {
                meta.append(CURRENCY.format(item.getPrecioUnitario()));
            } catch (Exception e) {
                meta.append(item.getPrecioUnitario().toPlainString());
            }
        }
        lblMeta.setText(meta.toString());

        // Stock badge (si aplica)
        if (item.getStockActual() != null) {
            lblStock.setText(item.getStockActual().toPlainString());
            if (item.isStockLow()) {
                lblStock.getStyleClass().add("stock-badge-low"); // clase definida en tu optional, si no existe no rompe
            } else {
                lblStock.getStyleClass().remove("stock-badge-low");
            }
        } else {
            lblStock.setText("");
            lblStock.getStyleClass().remove("stock-badge-low");
        }

        // Imagen
        try {
            String path = item.getImagenPath();
            if (path != null && !path.isBlank()) {
                iv.setImage(ProductImageHelper.loadImage(path));
            } else {
                iv.setImage(ProductImageHelper.loadImage(null));
            }
        } catch (Throwable t) {
            iv.setImage(ProductImageHelper.loadImage(null)); // placeholder
        }

        setText(null);
        setGraphic(root);
    }
}
