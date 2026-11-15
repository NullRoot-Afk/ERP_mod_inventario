package com.example.erprukainventario.lookup.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.auth.domain.Warehouse;
import com.example.erprukainventario.core.data.LotRepository;
import com.example.erprukainventario.core.data.ProductRepository;
import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.core.domain.Lot;
import com.example.erprukainventario.core.domain.Movement;
import com.example.erprukainventario.core.domain.Product;
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

    @Inject
    public ProductLookupViewModel(ProductRepository repository, LotRepository lotRepository, WorkContext context) {
        this.repository = repository;
        this.lotRepository = lotRepository;
        this.context = context;
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
            loading.setValue(false);
            if (found == null) {
                notFound.setValue(new Event<>(code));
                return;
            }
            product.setValue(found);
            movements.setValue(new ArrayList<>());
            lots.setValue(new ArrayList<>());
            repository.getMovements(found.getId(), MOVEMENTS_LIMIT, movements::setValue);
            lotRepository.getLotsForProduct(found.getId(), lots::setValue);
        });
    }
}
