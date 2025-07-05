package com.example.askai

import android.app.Application
import androidx.work.Configuration

class AskAIApplication : Application(), Configuration.Provider {
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
            
    override fun onCreate() {
        super.onCreate()
        // Any other app initialization can go here
    }
} 