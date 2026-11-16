package com.example.erprukainventario.warehousemap.domain;

/** Bodega con su posición en el mapa. Sin coordenadas: lat/lng son null. */
public class WarehouseLocation {

    private final String id;
    private final String name;
    private final Double latitude;
    private final Double longitude;
    private final String address;

    public WarehouseLocation(String id, String name, Double latitude, Double longitude, String address) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getAddress() { return address; }

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }
}
