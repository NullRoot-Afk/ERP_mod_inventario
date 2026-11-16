package com.example.erprukainventario.warehousemap.domain;

/** Clima actual en una bodega (respuesta de Open-Meteo ya traducida). */
public class CurrentWeather {

    private final double temperature;
    private final double apparentTemperature;
    private final double windSpeedKmh;
    private final double precipitationMm;
    private final int weatherCode;

    public CurrentWeather(double temperature, double apparentTemperature, double windSpeedKmh,
                          double precipitationMm, int weatherCode) {
        this.temperature = temperature;
        this.apparentTemperature = apparentTemperature;
        this.windSpeedKmh = windSpeedKmh;
        this.precipitationMm = precipitationMm;
        this.weatherCode = weatherCode;
    }

    public double getTemperature() { return temperature; }
    public double getApparentTemperature() { return apparentTemperature; }
    public double getWindSpeedKmh() { return windSpeedKmh; }
    public double getPrecipitationMm() { return precipitationMm; }
    public int getWeatherCode() { return weatherCode; }

    /** Código WMO (el que entrega Open-Meteo) en palabras. */
    public String getDescription() {
        int c = weatherCode;
        if (c == 0) return "Despejado";
        if (c <= 2) return "Parcialmente nublado";
        if (c == 3) return "Nublado";
        if (c == 45 || c == 48) return "Neblina";
        if (c >= 51 && c <= 57) return "Llovizna";
        if (c >= 61 && c <= 67) return "Lluvia";
        if (c >= 71 && c <= 77) return "Nieve";
        if (c >= 80 && c <= 82) return "Chubascos";
        if (c == 85 || c == 86) return "Chubascos de nieve";
        if (c >= 95) return "Tormenta eléctrica";
        return "Sin datos";
    }

    /**
     * Recomendación para la operación de la bodega (despachos y andenes).
     * Devuelve null si el clima no exige precauciones.
     */
    public String getOperationAdvice() {
        int c = weatherCode;
        if (c >= 95) return "Tormenta: suspender carga en andenes descubiertos.";
        if ((c >= 51 && c <= 67) || (c >= 80 && c <= 82) || precipitationMm > 0) {
            return "Lluvia: proteger con film la carga de los despachos.";
        }
        if (c >= 71 && c <= 77 || c == 85 || c == 86) return "Nieve: revisar rutas antes de despachar.";
        if (windSpeedKmh >= 40) return "Viento fuerte: asegurar la carga en los camiones.";
        if (temperature >= 30) return "Calor: resguardar químicos y pinturas del sol.";
        if (temperature <= 2) return "Riesgo de helada: proteger productos sensibles al frío.";
        return null;
    }
}
