package com.example.erprukainventario.warehousemap.data;

import android.util.Log;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.data.Docs;
import com.example.erprukainventario.core.data.FirestoreCollections;
import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.warehousemap.domain.CurrentWeather;
import com.example.erprukainventario.warehousemap.domain.WarehouseLocation;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Bodegas de la empresa con coordenadas (WAREHOUSES: lat, lng, address) y el
 * clima actual en cada una, consultado a la API pública de Open-Meteo.
 *
 * Retrofit entrega las respuestas de enqueue() en el hilo principal en Android,
 * igual que los listeners de Firestore: el ViewModel puede usar setValue().
 */
@Singleton
public class WarehouseMapRepository {

    private static final String TAG = "WarehouseMapRepo";

    private final FirebaseFirestore firestore;
    private final WorkContext context;
    private final OpenMeteoApi weatherApi;

    @Inject
    public WarehouseMapRepository(FirebaseFirestore firestore, WorkContext context, OpenMeteoApi weatherApi) {
        this.firestore = firestore;
        this.context = context;
        this.weatherApi = weatherApi;
    }

    /** null si la consulta falló (sin conexión y sin caché, o sin permisos). */
    public void getWarehouses(RepositoryCallback<List<WarehouseLocation>> callback) {
        String companyId = context.companyId();
        if (companyId == null) {
            callback.onResult(new ArrayList<>());
            return;
        }
        firestore.collection(FirestoreCollections.WAREHOUSES)
                .whereEqualTo("companyId", companyId)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        Log.e(TAG, "Consulta de bodegas falló", task.getException());
                        callback.onResult(null);
                        return;
                    }
                    List<WarehouseLocation> result = new ArrayList<>();
                    for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                        result.add(new WarehouseLocation(
                                doc.getId(),
                                Docs.str(doc, doc.getId(), "name"),
                                number(doc, "lat", "latitude"),
                                number(doc, "lng", "longitude"),
                                Docs.str(doc, "", "address")));
                    }
                    Collections.sort(result, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                    callback.onResult(result);
                });
    }

    /** null si no hubo respuesta válida (sin red, error HTTP o JSON inesperado). */
    public void getCurrentWeather(double latitude, double longitude, RepositoryCallback<CurrentWeather> callback) {
        weatherApi.currentWeather(latitude, longitude, OpenMeteoApi.CURRENT_FIELDS, "auto")
                .enqueue(new Callback<OpenMeteoApi.ForecastResponse>() {
                    @Override
                    public void onResponse(Call<OpenMeteoApi.ForecastResponse> call,
                                           Response<OpenMeteoApi.ForecastResponse> response) {
                        OpenMeteoApi.ForecastResponse body = response.body();
                        if (!response.isSuccessful() || body == null || body.current == null) {
                            Log.w(TAG, "Open-Meteo respondió " + response.code());
                            callback.onResult(null);
                            return;
                        }
                        OpenMeteoApi.Current c = body.current;
                        callback.onResult(new CurrentWeather(c.temperature, c.apparentTemperature,
                                c.windSpeed, c.precipitation, c.weatherCode));
                    }

                    @Override
                    public void onFailure(Call<OpenMeteoApi.ForecastResponse> call, Throwable t) {
                        Log.w(TAG, "Open-Meteo sin respuesta", t);
                        callback.onResult(null);
                    }
                });
    }

    public String myWarehouseId() {
        return context.warehouseId();
    }

    /** Acepta número o texto ("-36.82"); null si falta o no es válido. */
    private static Double number(DocumentSnapshot d, String... fields) {
        for (String f : fields) {
            Object v = d.get(f);
            if (v instanceof Number) return ((Number) v).doubleValue();
            if (v instanceof String) {
                try {
                    return Double.parseDouble(((String) v).trim());
                } catch (NumberFormatException ignored) {
                    // probar el siguiente alias
                }
            }
        }
        return null;
    }
}
