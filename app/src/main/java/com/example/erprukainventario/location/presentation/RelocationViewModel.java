package com.example.erprukainventario.location.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.core.data.ProductRepository;
import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.core.domain.LocationCode;
import com.example.erprukainventario.core.domain.OperationResult;
import com.example.erprukainventario.core.domain.Product;
import com.example.erprukainventario.core.ui.Event;
import com.example.erprukainventario.location.data.LocationRepository;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * Reubicación con escaneo doble (guía §4): primero el producto, luego la ubicación
 * destino. El paso actual se deduce de si ya hay producto seleccionado.
 */
@HiltViewModel
public class RelocationViewModel extends ViewModel {

    private final ProductRepository products;
    private final LocationRepository locations;
    private final WorkContext context;

    private final MutableLiveData<Product> product = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Event<String>> error = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> pendingConfirmation = new MutableLiveData<>();
    private final MutableLiveData<Event<OperationResult>> result = new MutableLiveData<>();

    @Inject
    public RelocationViewModel(ProductRepository products, LocationRepository locations, WorkContext context) {
        this.products = products;
        this.locations = locations;
        this.context = context;
    }

    public LiveData<Product> getProduct() { return product; }
    public LiveData<Boolean> isLoading() { return loading; }
    public LiveData<Event<String>> getError() { return error; }
    /** Ubicación destino ya validada, esperando que el usuario confirme. */
    public LiveData<Event<String>> getPendingConfirmation() { return pendingConfirmation; }
    public LiveData<Event<OperationResult>> getResult() { return result; }

    public boolean isWaitingLocation() {
        return product.getValue() != null;
    }

    public int stockOf(Product p) {
        return p.stockIn(context.warehouseId());
    }

    /** Punto único de entrada de cada lectura (cámara o manual). */
    public void onCode(String code) {
        if (isWaitingLocation()) {
            onLocationCode(code);
        } else {
            findProduct(code);
        }
    }

    public void findProduct(String code) {
        if (LocationCode.isValid(code)) {
            error.setValue(new Event<>("Eso es una ubicación. Primero escanea el producto a mover."));
            return;
        }
        loading.setValue(true);
        products.findByCode(code, found -> {
            loading.setValue(false);
            if (found == null) {
                error.setValue(new Event<>("No hay un producto con el código " + code + "."));
            } else {
                product.setValue(found);
            }
        });
    }

    private void onLocationCode(String code) {
        Product p = product.getValue();
        String target = LocationCode.normalize(code);
        if (target == null) {
            error.setValue(new Event<>("\"" + code + "\" no es una ubicación válida (ej. A-03-02-B)."));
            return;
        }
        if (p != null && target.equalsIgnoreCase(p.getLocation())) {
            error.setValue(new Event<>("El producto ya está en " + target + "."));
            return;
        }
        pendingConfirmation.setValue(new Event<>(target));
    }

    public void confirm(String target) {
        Product p = product.getValue();
        if (p == null) return;
        loading.setValue(true);
        locations.relocate(p, target, r -> {
            loading.setValue(false);
            if (r.isSuccess()) product.setValue(null); // listo para el siguiente producto
            result.setValue(new Event<>(r));
        });
    }

    public void clearProduct() {
        product.setValue(null);
    }
}
