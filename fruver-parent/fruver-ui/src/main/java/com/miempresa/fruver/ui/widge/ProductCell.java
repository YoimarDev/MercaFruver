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

public class ProductCell extends ListCell<Producto> {

    private final HBox root = new HBox(10);
    private final ImageView iv = new ImageView();
    private final VBox metaBox = new VBox(4);
    private final Label lblName = new Label();
    private final Label lblMeta = new Label();
    private final Label lblStock = new Label();
    private final Label lblTypeBadge = new Label();

    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));

    public ProductCell() {
        super();

        iv.setFitWidth(92);
        iv.setFitHeight(64);
        iv.setPreserveRatio(true);
        iv.getStyleClass().add("avatar");

        lblName.getStyleClass().add("user-tile-name");
        lblMeta.getStyleClass().add("product-meta");
        lblStock.getStyleClass().add("label-status");

        lblTypeBadge.getStyleClass().add("product-type-badge");
        lblTypeBadge.setAlignment(Pos.CENTER);

        metaBox.getChildren().addAll(lblName, lblMeta);
        metaBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(metaBox, Priority.ALWAYS);

        root.setAlignment(Pos.CENTER_LEFT);
        root.getStyleClass().add("user-tile-root");
        root.getChildren().addAll(iv, metaBox, lblStock);

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

        lblName.setText(item.getNombre() == null ? "(sin nombre)" : item.getNombre());

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
                String price = CURRENCY.format(item.getPrecioUnitario());
                if (item.getTipo() != null && "PESO".equalsIgnoreCase(item.getTipo().name())) {
                    price = price + "/kg";
                }
                meta.append(price);
            } catch (Exception e) {
                meta.append(item.getPrecioUnitario().toPlainString());
            }
        }
        lblMeta.setText(meta.toString());

        if (item.getStockActual() != null) {
            lblStock.setText(item.getStockActual().toPlainString());
            try {
                if (item.getStockUmbral() != null && item.getStockActual().compareTo(item.getStockUmbral()) <= 0) {
                    if (!lblStock.getStyleClass().contains("stock-badge-low"))
                        lblStock.getStyleClass().add("stock-badge-low");
                } else {
                    lblStock.getStyleClass().removeAll("stock-badge-low");
                }
            } catch (Exception ignored) { }
        } else {
            lblStock.setText("");
            lblStock.getStyleClass().removeAll("stock-badge-low");
        }

        if (item.getTipo() != null) {
            String t = item.getTipo().name();
            lblTypeBadge.setText(t);
            lblTypeBadge.getStyleClass().removeAll("peso", "unidad");
            if ("PESO".equalsIgnoreCase(t)) {
                if (!lblTypeBadge.getStyleClass().contains("peso"))
                    lblTypeBadge.getStyleClass().add("peso");
            } else {
                if (!lblTypeBadge.getStyleClass().contains("unidad"))
                    lblTypeBadge.getStyleClass().add("unidad");
            }
        } else {
            lblTypeBadge.setText("");
            lblTypeBadge.getStyleClass().removeAll("peso", "unidad");
        }

        try {
            String path = item.getImagenPath();
            if (path != null && !path.isBlank()) {
                iv.setImage(ProductImageHelper.loadImage(path));
            } else {
                iv.setImage(ProductImageHelper.loadImage(null));
            }
        } catch (Throwable t) {
            iv.setImage(ProductImageHelper.loadImage(null));
        }

        if (!metaBox.getChildren().contains(lblTypeBadge)) {
            metaBox.getChildren().add(lblTypeBadge);
        }

        setText(null);
        setGraphic(root);
    }
}
