package com.example.erprukainventario.warehousemap.presentation;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.erprukainventario.R;
import com.example.erprukainventario.databinding.ActivityWarehouseMapBinding;
import com.example.erprukainventario.warehousemap.domain.CurrentWeather;
import com.example.erprukainventario.warehousemap.domain.WarehouseLocation;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Mapa de las bodegas de la empresa (OpenStreetMap vía osmdroid, sin API key).
 * Al tocar una bodega se consulta el clima actual en esa ubicación a la API
 * pública de Open-Meteo, con una recomendación para los despachos.
 */
@AndroidEntryPoint
public class WarehouseMapActivity extends AppCompatActivity {

    /** Concepción: centro por defecto si ninguna bodega tiene coordenadas. */
    private static final GeoPoint DEFAULT_CENTER = new GeoPoint(-36.8270, -73.0498);

    private ActivityWarehouseMapBinding binding;
    private WarehouseMapViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // osmdroid exige identificar la app ante los servidores de OpenStreetMap
        // (política de uso de teselas) y guarda su configuración en preferencias propias.
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        binding = ActivityWarehouseMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(WarehouseMapViewModel.class);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.map.setTileSource(TileSourceFactory.MAPNIK);
        binding.map.setMultiTouchControls(true);
        binding.map.getController().setZoom(11.0);
        binding.map.getController().setCenter(DEFAULT_CENTER);

        viewModel.getWarehouses().observe(this, this::showWarehouses);
        viewModel.getLoadFailed().observe(this, failed -> {
            if (failed) binding.tvHint.setText(R.string.map_load_error);
        });
        viewModel.getWeather().observe(this, this::showWeather);
    }

    private void showWarehouses(List<WarehouseLocation> warehouses) {
        binding.map.getOverlays().clear();
        List<GeoPoint> points = new ArrayList<>();
        int withoutCoordinates = 0;
        String mine = viewModel.myWarehouseId();

        for (WarehouseLocation w : warehouses) {
            if (!w.hasCoordinates()) {
                withoutCoordinates++;
                continue;
            }
            GeoPoint point = new GeoPoint(w.getLatitude(), w.getLongitude());
            points.add(point);

            Marker marker = new Marker(binding.map);
            marker.setPosition(point);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setTitle(w.getName());
            marker.setSnippet(w.getId().equals(mine) ? getString(R.string.map_my_warehouse) : w.getAddress());
            marker.setOnMarkerClickListener((m, mapView) -> {
                m.showInfoWindow();
                mapView.getController().animateTo(m.getPosition());
                viewModel.select(w);
                return true;
            });
            binding.map.getOverlays().add(marker);
        }
        binding.map.invalidate();

        if (points.isEmpty()) {
            binding.tvHint.setText(R.string.map_empty);
        } else {
            binding.tvHint.setText(withoutCoordinates > 0
                    ? getString(R.string.map_hint) + "\n" + getString(R.string.map_no_coordinates, withoutCoordinates)
                    : getString(R.string.map_hint));
            fitTo(points);
        }
    }

    /** Encuadra todas las bodegas (espera a que el mapa tenga tamaño). */
    private void fitTo(List<GeoPoint> points) {
        if (points.size() == 1) {
            binding.map.getController().setZoom(14.0);
            binding.map.getController().setCenter(points.get(0));
            return;
        }
        BoundingBox box = BoundingBox.fromGeoPoints(points);
        binding.map.post(() -> binding.map.zoomToBoundingBox(box.increaseByScale(1.3f), false, 64));
    }

    private void showWeather(WarehouseMapViewModel.WeatherState state) {
        if (state == null) return;
        binding.cardWeather.setVisibility(View.VISIBLE);
        binding.tvWarehouseName.setText(state.warehouse.getName());
        binding.tvAdvice.setVisibility(View.GONE);

        if (state.loading) {
            binding.progressWeather.setVisibility(View.VISIBLE);
            binding.tvWeather.setText(R.string.map_weather_loading);
            binding.tvWeatherDetail.setVisibility(View.GONE);
            return;
        }
        binding.progressWeather.setVisibility(View.GONE);
        CurrentWeather w = state.weather;
        if (w == null) {
            binding.tvWeather.setText(R.string.map_weather_error);
            binding.tvWeatherDetail.setVisibility(View.GONE);
            return;
        }
        binding.tvWeather.setText(getString(R.string.map_weather_format, w.getDescription(), w.getTemperature()));
        binding.tvWeatherDetail.setVisibility(View.VISIBLE);
        binding.tvWeatherDetail.setText(getString(R.string.map_weather_detail_format,
                w.getApparentTemperature(), w.getWindSpeedKmh(), w.getPrecipitationMm()));
        String advice = w.getOperationAdvice();
        if (advice != null) {
            binding.tvAdvice.setVisibility(View.VISIBLE);
            binding.tvAdvice.setText(advice);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        binding.map.onPause();
    }
}
