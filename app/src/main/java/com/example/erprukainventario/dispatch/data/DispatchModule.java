package com.example.erprukainventario.dispatch.data;

import com.example.erprukainventario.core.scan.ScanModes;
import com.example.erprukainventario.core.scan.ScanTaskSource;

import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;

@Module
@InstallIn(SingletonComponent.class)
public abstract class DispatchModule {

    @Binds
    public abstract DispatchRepository bindDispatchRepository(DispatchRepositoryImpl impl);

    @Binds
    @IntoMap
    @StringKey(ScanModes.DISPATCH)
    public abstract ScanTaskSource bindDispatchScanSource(DispatchRepositoryImpl impl);
}
