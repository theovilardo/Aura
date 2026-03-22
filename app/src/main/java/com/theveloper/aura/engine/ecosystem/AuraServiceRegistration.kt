package com.theveloper.aura.engine.ecosystem

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.theveloper.aura.protocol.Platform
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuraServiceRegistration @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localDeviceIdentityStore: LocalDeviceIdentityStore
) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    @Synchronized
    fun register() {
        if (registrationListener != null) return

        acquireMulticastLock()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = localDeviceIdentityStore.deviceName().take(63)
            serviceType = SERVICE_TYPE
            port = InvitationListener.INVITATION_PORT
            setAttribute("deviceId", localDeviceIdentityStore.deviceId())
            setAttribute("platform", Platform.ANDROID.name)
            setAttribute("version", PROTOCOL_VERSION)
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD registration failed: $errorCode")
                clearRegistrationState()
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD unregister failed: $errorCode")
                clearRegistrationState()
            }

            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD registered: ${serviceInfo.serviceName}")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD unregistered: ${serviceInfo.serviceName}")
                clearRegistrationState()
            }
        }

        registrationListener = listener

        runCatching {
            nsdManager.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                listener
            )
        }.onFailure {
            Log.e(TAG, "Unable to register NSD service", it)
            clearRegistrationState()
        }
    }

    @Synchronized
    fun unregister() {
        val listener = registrationListener ?: return
        registrationListener = null

        runCatching {
            nsdManager.unregisterService(listener)
        }.onFailure {
            Log.w(TAG, "Unable to unregister NSD service", it)
            releaseMulticastLock()
        }
    }

    @Synchronized
    private fun clearRegistrationState() {
        registrationListener = null
        releaseMulticastLock()
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        multicastLock = wifiManager.createMulticastLock("aura:mdns").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }
        multicastLock = null
    }

    private companion object {
        const val TAG = "AuraServiceRegistration"
        const val SERVICE_TYPE = "_aura-mobile._tcp."
        const val PROTOCOL_VERSION = "2"
    }
}
