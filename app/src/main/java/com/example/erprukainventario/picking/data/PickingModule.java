package com.example.erprukainventario.picking.data;

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
public abstract class PickingModule {

    @Binds
    public abstract PickingRepository bindPickingRepository(PickingRepositoryImpl impl);

    @Binds
    @IntoMap
    @StringKey(ScanModes.PICKING)
    public abstract ScanTaskSource bindPickingScanSource(PickingRepositoryImpl impl);
}
