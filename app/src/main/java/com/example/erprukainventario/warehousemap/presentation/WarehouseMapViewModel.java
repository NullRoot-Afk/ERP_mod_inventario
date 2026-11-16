package com.example.erprukainventario.warehousemap.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.warehousemap.data.WarehouseMapRepository;
import com.example.erprukainventario.warehousemap.domain.CurrentWeather;
import com.example.erprukainventario.warehousemap.domain.WarehouseLocation;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class WarehouseMapViewModel extends ViewModel {

    /** Estado de la tarjeta inferior: qué bodega está elegida y su clima. */
    public static class WeatherState {
        public final WarehouseLocation warehouse;
        public final boolean loading;
        public final CurrentWeather weather; // null mientras carga o si falló

        WeatherState(WarehouseLocation warehouse, boolean loading, CurrentWeather weather) {
            this.warehouse = warehouse;
            this.loading = loading;
            this.weather = weather;
        }
    }

    private final WarehouseMapRepository repository;
    private final MutableLiveData<List<WarehouseLocation>> warehouses = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadFailed = new MutableLiveData<>(false);
    private final MutableLiveData<WeatherState> weather = new MutableLiveData<>();
    private int requestId; // descarta respuestas de una bodega tocada antes

    @Inject
    public WarehouseMapViewModel(WarehouseMapRepository repository) {
        this.repository = repository;
        load();
    }

    public LiveData<List<WarehouseLocation>> getWarehouses() { return warehouses; }
    public LiveData<Boolean> getLoadFailed() { return loadFailed; }
    public LiveData<WeatherState> getWeather() { return weather; }

    public String myWarehouseId() {
        return repository.myWarehouseId();
    }

    private void load() {
        repository.getWarehouses(list -> {
            loadFailed.setValue(list == null);
            if (list != null) warehouses.setValue(list);
        });
    }

    public void select(WarehouseLocation warehouse) {
        if (warehouse == null || !warehouse.hasCoordinates()) return;
        final int id = ++requestId;
        weather.setValue(new WeatherState(warehouse, true, null));
        repository.getCurrentWeather(warehouse.getLatitude(), warehouse.getLongitude(), result -> {
            if (id != requestId) return;
            weather.setValue(new WeatherState(warehouse, false, result));
        });
    }
}
