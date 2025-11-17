package com.designated.driverapp.di

import com.designated.driverapp.data.repository.CustomerPointsRepository
import com.designated.driverapp.data.repository.CustomerPointsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository 의존성 주입 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCustomerPointsRepository(
        impl: CustomerPointsRepositoryImpl
    ): CustomerPointsRepository
}
