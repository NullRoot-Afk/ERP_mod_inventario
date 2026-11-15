package com.example.erprukainventario.count.data;

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
public abstract class CountModule {

    @Binds
    public abstract CountRepository bindCountRepository(CountRepositoryImpl impl);

    @Binds
    @IntoMap
    @StringKey(ScanModes.COUNT)
    public abstract ScanTaskSource bindCountScanSource(CountRepositoryImpl impl);
}
