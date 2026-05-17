package com.akrapovic.soundkit.community.di

import android.bluetooth.BluetoothManager
import android.content.Context
import com.akrapovic.soundkit.community.ble.BleConnectionGateway
import com.akrapovic.soundkit.community.ble.BleConnectionManager
import com.akrapovic.soundkit.community.ble.BleScanner
import com.akrapovic.soundkit.community.ble.BleScannerGateway
import com.akrapovic.soundkit.community.ble.RetryPolicy
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.BleRepositoryImpl
import com.akrapovic.soundkit.community.data.GeofenceZonesRepository
import com.akrapovic.soundkit.community.data.GeofenceZonesStore
import com.akrapovic.soundkit.community.data.RuleExecutionLogRepository
import com.akrapovic.soundkit.community.data.RuleExecutionLogStore
import com.akrapovic.soundkit.community.data.RulesRepository
import com.akrapovic.soundkit.community.data.RulesStore
import com.akrapovic.soundkit.community.data.SettingsRepository
import com.akrapovic.soundkit.community.data.SettingsStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindBleRepository(impl: BleRepositoryImpl): BleRepository

    @Binds
    @Singleton
    abstract fun bindBleScannerGateway(impl: BleScanner): BleScannerGateway

    @Binds
    @Singleton
    abstract fun bindBleConnectionGateway(impl: BleConnectionManager): BleConnectionGateway

    @Binds
    @Singleton
    abstract fun bindSettingsStore(impl: SettingsRepository): SettingsStore

    @Binds
    @Singleton
    abstract fun bindRulesStore(impl: RulesRepository): RulesStore

    @Binds
    @Singleton
    abstract fun bindGeofenceZonesStore(impl: GeofenceZonesRepository): GeofenceZonesStore

    @Binds
    @Singleton
    abstract fun bindRuleExecutionLogStore(impl: RuleExecutionLogRepository): RuleExecutionLogStore
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideBluetoothManager(@ApplicationContext context: Context): BluetoothManager {
        return context.getSystemService(BluetoothManager::class.java)
    }

    @Provides
    @Singleton
    fun provideRetryPolicy(): RetryPolicy = RetryPolicy()
}

