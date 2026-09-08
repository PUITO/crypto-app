package com.puito.cryptoapp

import android.app.Application

class CryptoApp : Application() {
    lateinit var repository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(this)
    }
}
