package com.example.erprukainventario.location.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.core.data.ProductRepository;
import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.core.domain.LocationCode;
import com.example.erprukainventario.core.domain.Product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/** Visualización de stock por ubicación (guía §4), agrupada por zona y pasillo. */
@HiltViewModel
public class LocationStockViewModel extends ViewModel {

    /** Fila de la lista: encabezado de pasillo (product == null) o producto. */
    public static class Row {
        public final String header;
        public final Product product;
        public final int stock;
        public final boolean low;

        Row(String header) {
            this.header = header;
            this.product = null;
            this.stock = 0;
            this.low = false;
        }

        Row(Product product, int stock, boolean low) {
            this.header = null;
            this.product = product;
            this.stock = stock;
            this.low = low;
        }
    }

    private static final String NO_LOCATION = "Sin ubicación válida";

    private final ProductRepository repository;
    private final WorkContext context;
    private final MutableLiveData<List<Row>> rows = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> summary = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private List<Product> all = new ArrayList<>();
    private String filter = "";

    @Inject
    public LocationStockViewModel(ProductRepository repository, WorkContext context) {
        this.repository = repository;
        this.context = context;
    }

    public LiveData<List<Row>> getRows() { return rows; }
    public LiveData<String> getSummary() { return summary; }
    public LiveData<Boolean> isLoading() { return loading; }

    public String warehouseName() {
        return context.warehouseName();
    }

    public void load() {
        loading.setValue(true);
        repository.getProducts(list -> {
            loading.setValue(false);
            all = list;
            rebuild();
        });
    }

    public void setFilter(String text) {
        filter = text != null ? text.trim().toLowerCase(Locale.ROOT) : "";
        rebuild();
    }

    private void rebuild() {
        String warehouse = context.warehouseId();
        List<Product> visible = new ArrayList<>();
        for (Product p : all) {
            if (filter.isEmpty()
                    || p.getLocation().toLowerCase(Locale.ROOT).startsWith(filter)
                    || p.getName().toLowerCase(Locale.ROOT).contains(filter)
                    || p.getCode().toLowerCase(Locale.ROOT).contains(filter)) {
                visible.add(p);
            }
        }
        // Orden de recorrido físico; lo que no tiene ubicación válida va al final.
        Collections.sort(visible, (a, b) -> {
            String ka = sortKey(a.getLocation());
            String kb = sortKey(b.getLocation());
            return ka.compareTo(kb);
        });

        List<Row> result = new ArrayList<>();
        String currentGroup = null;
        int units = 0;
        int positions = 0;
        String lastLocation = null;
        for (Product p : visible) {
            LocationCode code = LocationCode.parse(p.getLocation());
            String group = code != null ? code.aisleLabel() : NO_LOCATION;
            if (!group.equals(currentGroup)) {
                result.add(new Row(group));
                currentGroup = group;
            }
            int stock = p.stockIn(warehouse);
            units += stock;
            if (!p.getLocation().equals(lastLocation)) positions++;
            lastLocation = p.getLocation();
            result.add(new Row(p, stock, p.isLowStockIn(warehouse)));
        }
        rows.setValue(result);
        summary.setValue(visible.size() + " productos · " + positions + " ubicaciones · " + units + " un.");
    }

    private static String sortKey(String location) {
        String normalized = LocationCode.normalize(location);
        return normalized != null ? "0" + normalized : "1" + location;
    }
}
