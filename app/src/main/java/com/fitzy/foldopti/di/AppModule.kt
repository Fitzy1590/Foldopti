package com.fitzy.foldopti.di

import android.content.Context
import androidx.room.Room
import com.fitzy.foldopti.core.whitelist.FoldOptiDatabase
import com.fitzy.foldopti.core.whitelist.WhitelistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): FoldOptiDatabase =
        Room.databaseBuilder(ctx, FoldOptiDatabase::class.java, "foldopti.db").build()

    @Provides
    fun whitelistDao(db: FoldOptiDatabase): WhitelistDao = db.whitelistDao()
}
