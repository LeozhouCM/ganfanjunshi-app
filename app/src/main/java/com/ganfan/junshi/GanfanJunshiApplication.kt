package com.ganfan.junshi

import android.app.Application
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.amap.api.services.core.ServiceSettings
import com.ganfan.junshi.BuildConfig
import com.ganfan.junshi.data.GanfanRepository
import com.ganfan.junshi.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GanfanJunshiApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        initAmapPrivacy()
        NotificationHelper.createChannels(this)
        applicationScope.launch {
            GanfanRepository.getInstance(this@GanfanJunshiApplication).seedLocalDataIfNeeded()
        }
    }

    private fun initAmapPrivacy() {
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)
        ServiceSettings.updatePrivacyShow(this, true, true)
        ServiceSettings.updatePrivacyAgree(this, true)
        ServiceSettings.getInstance().setApiKey(BuildConfig.AMAP_API_KEY)
    }
}
