package com.designated.pickupapp.di

import android.content.Context
import com.designated.pickupapp.ptt.core.PTTController
import com.designated.pickupapp.ptt.core.SimplePTTEngine
import com.designated.pickupapp.ptt.core.UIDManager
import com.designated.pickupapp.ptt.network.TokenManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PTTModule {

    @Provides
    @Singleton
    fun provideSimplePTTEngine(): SimplePTTEngine {
        return SimplePTTEngine()
    }

    @Provides
    @Singleton
    fun provideTokenManager(): TokenManager {
        return TokenManager()
    }

    @Provides
    @Singleton
    fun providePTTController(
        @ApplicationContext context: Context,
        engine: SimplePTTEngine,
        tokenManager: TokenManager
    ): PTTController {
        return PTTController(
            context = context,
            engine = engine,
            tokenManager = tokenManager,
            uidManager = UIDManager
        )
    }
}