package com.example.erprukainventario.warehousemap.data;

import com.google.gson.annotations.SerializedName;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * API pública de clima Open-Meteo (https://open-meteo.com): gratuita, sin API key.
 * Ejemplo: GET https://api.open-meteo.com/v1/forecast?latitude=-36.82&longitude=-73.05
 *          &current=temperature_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m
 */
public interface OpenMeteoApi {

    String BASE_URL = "https://api.open-meteo.com/";
    String CURRENT_FIELDS = "temperature_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m";

    @GET("v1/forecast")
    Call<ForecastResponse> currentWeather(
            @Query("latitude") double latitude,
            @Query("longitude") double longitude,
            @Query("current") String fields,
            @Query("timezone") String timezone
    );

    /** Solo los campos que se usan; Gson ignora el resto del JSON. */
    class ForecastResponse {
        @SerializedName("current")
        public Current current;
    }

    class Current {
        @SerializedName("time")
        public String time;
        @SerializedName("temperature_2m")
        public double temperature;
        @SerializedName("apparent_temperature")
        public double apparentTemperature;
        @SerializedName("precipitation")
        public double precipitation;
        @SerializedName("weather_code")
        public int weatherCode;
        @SerializedName("wind_speed_10m")
        public double windSpeed;
    }
}
