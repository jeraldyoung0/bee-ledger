package com.beeledger.app

import android.app.Application
import com.beeledger.app.data.Repo

class BeeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Repo.init(this)
    }
}
