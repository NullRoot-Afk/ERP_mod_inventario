package com.example.erprukainventario.auth.data;

import java.util.concurrent.TimeUnit;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Crea las piezas de networking una sola vez para toda la app (@Singleton)
 * y le enseña a Hilt cómo construirlas. A diferencia de AuthModule (que
 * usaba @Binds porque solo "amarraba" una interfaz a una clase que ya
 * sabía construirse sola), aquí se necesita @Provides porque Retrofit y
 * OkHttpClient son clases de una librería externa — no podemos ponerles
 * @Inject en su constructor, así que hay que construirlas nosotros mismos
 * y decirle a Hilt "así es como se hace".
 *
 * IMPORTANTE: BASE_URL es un placeholder. Reemplázalo por la URL real
 * del backend del ERP apenas la tengas. Debe terminar en "/".
 */
@Module
@InstallIn(SingletonComponent.class)
public class NetworkModule {

    private static final String BASE_URL = "http://192.168.1.7:5000/";

    @Provides
    @javax.inject.Singleton
    public HttpLoggingInterceptor provideLoggingInterceptor() {
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
        // BODY loguea todo (headers + cuerpo de la petición/respuesta).
        // Útil mientras desarrollamos; en release conviene bajarlo a NONE.
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        return interceptor;
    }

    @Provides
    @javax.inject.Singleton
    public OkHttpClient provideOkHttpClient(HttpLoggingInterceptor loggingInterceptor) {
        return new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        // TODO: cuando tengamos el accessToken del login, acá se agrega
        // un segundo interceptor que lo inyecte automáticamente en cada
        // request (Authorization: Bearer ...), para no repetirlo a mano
        // en cada llamada del ERP (picking, recepción, etc.).
    }

    @Provides
    @javax.inject.Singleton
    public Retrofit provideRetrofit(OkHttpClient client) {
        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    @Provides
    @javax.inject.Singleton
    public AuthApi provideAuthApi(Retrofit retrofit) {
        return retrofit.create(AuthApi.class);
    }
}