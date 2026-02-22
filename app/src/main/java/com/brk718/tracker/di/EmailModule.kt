package com.brk718.tracker.di

import android.content.Context
import com.brk718.tracker.data.remote.EmailService
import com.brk718.tracker.data.remote.GmailService
import com.brk718.tracker.data.remote.OutlookService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EmailModule {

    @Provides
    @Singleton
    fun provideEmailService(gmailService: GmailService): EmailService {
        return gmailService
    }

    /**
     * OutlookService se inyecta directamente en OutlookViewModel (igual que GmailService
     * en GmailViewModel). No está vinculado a la interfaz EmailService para que ambas
     * integraciones puedan coexistir de forma independiente.
     */
    @Provides
    @Singleton
    fun provideOutlookService(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): OutlookService = OutlookService(context, okHttpClient)
}
