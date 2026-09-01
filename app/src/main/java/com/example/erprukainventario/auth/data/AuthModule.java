package com.example.erprukainventario.auth.data;

import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

/**
 * Le enseña a Hilt qué clase concreta usar cuando alguien pide la
 * interfaz AuthRepository. Sin este módulo, Hilt no tiene forma de
 * saber que AuthRepositoryImpl es la implementación real — de ahí el
 * error "[Dagger/MissingBinding] AuthRepository cannot be provided
 * without an @Provides-annotated method".
 *
 * @Binds (en vez de @Provides) se usa cuando ya existe una clase con
 * @Inject en su constructor (AuthRepositoryImpl la tiene) y solo hace
 * falta "amarrarla" a la interfaz — es más liviano que @Provides,
 * que sería necesario si hubiera que construir el objeto a mano.
 */
@Module
@InstallIn(SingletonComponent.class)
public abstract class AuthModule {

    @Binds
    public abstract AuthRepository bindAuthRepository(AuthRepositoryImpl impl);
}
