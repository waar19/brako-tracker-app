package com.brk718.tracker.di

import com.brk718.tracker.data.remote.EmailService
import com.brk718.tracker.data.remote.GmailServiceMock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EmailModule {

    @Provides
    @Singleton
    fun provideEmailService(): EmailService {
        return GmailServiceMock()
    }
}
