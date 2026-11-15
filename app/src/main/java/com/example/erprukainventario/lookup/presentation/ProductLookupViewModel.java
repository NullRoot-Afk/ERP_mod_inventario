package com.example.erprukainventario.lookup.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.auth.domain.Warehouse;
import com.example.erprukainventario.core.data.LotRepository;
import com.example.erprukainventario.core.data.ProductRepository;
import com.example.erprukainventario.core.data.SerialRepository;
import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.core.domain.Lot;
import com.example.erprukainventario.core.domain.Movement;
import com.example.erprukainventario.core.domain.Product;
import com.example.erprukainventario.core.domain.SerialNumber;
import com.example.erprukainventario.core.domain.Serials;
import com.example.erprukainventario.core.ui.Event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ProductLookupViewModel extends ViewModel {

    private static final int MOVEMENTS_LIMIT = 15;

    private final ProductRepository repository;
    private final LotRepository lotRepository;
    private final WorkContext context;

    private final MutableLiveData<Product> product = new MutableLiveData<>();
    private final MutableLiveData<List<Movement>> movements = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Lot>> lots = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Event<String>> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Event<String>> notFound = new MutableLiveData<>();
    private final Map<String, String> warehouseNames = new HashMap<>();

    private final SerialRepository serialRepository;
    private final MutableLiveData<List<SerialNumber>> serials = new MutableLiveData<>(new ArrayList<>());
    /** Se buscó un número de serie: a qué unidad corresponde. */
    private final MutableLiveData<Event<SerialNumber>> serialFound = new MutableLiveData<>();

    @Inject
    public ProductLookupViewModel(ProductRepository repository, LotRepository lotRepository, WorkContext context,
                                  SerialRepository serialRepository) {
        this.repository = repository;
        this.lotRepository = lotRepository;
        this.context = context;
        this.serialRepository = serialRepository;
        repository.getWarehouses(list -> {
            for (Warehouse w : list) warehouseNames.put(w.getId(), w.getName());
        });
    }

    public LiveData<Product> getProduct() { return product; }
    public LiveData<List<Movement>> getMovements() { return movements; }
    public LiveData<List<Lot>> getLots() { return lots; }
    public LiveData<Event<String>> getMessage() { return message; }
    public LiveData<Boolean> isLoading() { return loading; }
    public LiveData<Event<String>> getNotFound() { return notFound; }
    public LiveData<List<SerialNumber>> getSerials() { return serials; }
    public LiveData<Event<SerialNumber>> getSerialFound() { return serialFound; }

    public String myWarehouseId() { return context.warehouseId(); }
    public String myWarehouseName() { return context.warehouseName(); }

    public String warehouseName(String id) {
        String name = warehouseNames.get(id);
        return name != null ? name : id;
    }

    /** Retira un lote vencido: queda un ajuste pendiente de aprobación. */
    public void retireLot(Lot lot) {
        lotRepository.retire(lot, result -> {
            message.setValue(new Event<>(result.getMessage()));
            Product current = product.getValue();
            if (result.isSuccess() && current != null) {
                lotRepository.getLotsForProduct(current.getId(), lots::setValue);
            }
        });
    }

    public void lookup(String code) {
        loading.setValue(true);
        repository.findByCode(code, found -> {
            if (found != null) {
                loading.setValue(false);
                show(found);
                return;
            }
            // ¿Es un número de serie? (guía §10: trazabilidad por serie)
            String serial = Serials.normalize(code);
            if (serial == null) {
                loading.setValue(false);
                notFound.setValue(new Event<>(code));
                return;
            }
            serialRepository.findBySerial(serial, matches -> {
                if (matches.isEmpty()) {
                    loading.setValue(false);
                    notFound.setValue(new Event<>(code));
                    return;
                }
                SerialNumber match = matches.get(0);
                serialFound.setValue(new Event<>(match));
                repository.findByCode(match.getProductCode(), owner -> {
                    loading.setValue(false);
                    if (owner != null) show(owner);
                });
            });
        });
    }

    private void show(Product found) {
        // Primero se limpian los datos del producto anterior para no mostrarlos con el nuevo.
        movements.setValue(new ArrayList<>());
        lots.setValue(new ArrayList<>());
        serials.setValue(new ArrayList<>());
        product.setValue(found);
        repository.getMovements(found.getId(), MOVEMENTS_LIMIT, movements::setValue);
        lotRepository.getLotsForProduct(found.getId(), lots::setValue);
        serialRepository.getSerialsForProduct(found.getId(), serials::setValue);
    }

    public boolean canConfigureProducts() {
        return context.canConfigureProducts();
    }

    /** Solo supervisor/jefe: alta de series de unidades que ya estaban en bodega (no cambia el stock). */
    public void registerExistingSerials(List<String> list) {
        Product current = product.getValue();
        if (current == null) return;
        serialRepository.registerExisting(current.getId(), current.getCode(), current.getName(), list, result -> {
            message.setValue(new Event<>(result.getMessage()));
            if (result.isSuccess()) serialRepository.getSerialsForProduct(current.getId(), serials::setValue);
        });
    }

    /** Solo supervisor/jefe: activa o quita el control por número de serie del producto mostrado. */
    public void setSerialized(boolean serialized) {
        Product current = product.getValue();
        if (current == null || current.isSerialized() == serialized) return;
        repository.setSerialized(current.getId(), serialized, result -> {
            message.setValue(new Event<>(result.getMessage()));
            // Se vuelve a leer para que la pantalla muestre lo que quedó guardado.
            repository.findByCode(current.getCode(), fresh -> {
                if (fresh != null) product.setValue(fresh);
            });
        });
    }
}
