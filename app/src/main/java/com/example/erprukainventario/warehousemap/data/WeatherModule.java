package com.example.erprukainventario.warehousemap.data;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Cliente Retrofit de Open-Meteo (uno para toda la app). */
@Module
@InstallIn(SingletonComponent.class)
public class WeatherModule {

    @Provides
    @Singleton
    public OpenMeteoApi provideOpenMeteoApi() {
        return new Retrofit.Builder()
                .baseUrl(OpenMeteoApi.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenMeteoApi.class);
    }
}
