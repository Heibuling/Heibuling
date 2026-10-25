package com.example.ntfypop

import android.app.Application
import android.util.Log

class NtfyPopApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Heibuling 启动")
    }

    companion object {
        const val TAG = "NtfyPop"
    }
}
