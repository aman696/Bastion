package com.aman.bastion.di

import com.aman.bastion.data.blocking.AppRuleRepositoryImpl
import com.aman.bastion.data.inapp.InAppRuleRepositoryImpl
import com.aman.bastion.domain.repository.AppRuleRepository
import com.aman.bastion.domain.repository.InAppRuleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindAppRuleRepository(impl: AppRuleRepositoryImpl): AppRuleRepository

    @Binds @Singleton
    abstract fun bindInAppRuleRepository(impl: InAppRuleRepositoryImpl): InAppRuleRepository
}
