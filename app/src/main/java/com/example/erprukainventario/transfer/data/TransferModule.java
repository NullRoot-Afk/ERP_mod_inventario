package com.example.erprukainventario.transfer.data;

import com.example.erprukainventario.core.scan.ScanModes;
import com.example.erprukainventario.core.scan.ScanTaskSource;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;

@Module
@InstallIn(SingletonComponent.class)
public abstract class TransferModule {

    @Binds
    public abstract TransferRepository bindTransferRepository(TransferRepositoryImpl impl);

    // Un mismo repositorio expone dos sesiones de escaneo distintas.
    @Provides
    @IntoMap
    @StringKey(ScanModes.TRANSFER_SEND)
    static ScanTaskSource provideSendSource(TransferRepositoryImpl impl) {
        return impl.sendSource();
    }

    @Provides
    @IntoMap
    @StringKey(ScanModes.TRANSFER_RECEIVE)
    static ScanTaskSource provideReceiveSource(TransferRepositoryImpl impl) {
        return impl.receiveSource();
    }
}
