package com.example.doorbell_mobile

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import timber.log.Timber

class DoorbellApp : Application(), DefaultLifecycleObserver {
    companion object {
        var isInForeground = false
            private set
    }

    override fun onCreate() {
        super<Application>.onCreate()
        // Đăng ký để nhận các event lifecycle của process
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        Timber.plant(Timber.DebugTree())
    }

    override fun onStart(owner: LifecycleOwner) {
        // Event ON_START: app vừa vào foreground
        isInForeground = true
    }

    override fun onStop(owner: LifecycleOwner) {
        // Event ON_STOP: app vừa vào background
        isInForeground = false
    }
}