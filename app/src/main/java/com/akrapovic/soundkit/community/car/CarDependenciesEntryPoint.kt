package com.akrapovic.soundkit.community.car

import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.SettingsStore
import com.akrapovic.soundkit.community.domain.DriveModeEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CarDependenciesEntryPoint {
    fun bleRepository(): BleRepository

    fun settingsStore(): SettingsStore

    fun driveModeEngine(): DriveModeEngine

    fun carSessionTracker(): CarSessionTracker
}

