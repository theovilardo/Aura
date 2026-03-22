package com.theveloper.aura.engine.ecosystem

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.theveloper.aura.data.repository.AppSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EcosystemLifecycleManager @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val auraServiceRegistration: AuraServiceRegistration,
    private val invitationListener: InvitationListener,
    private val pairingManager: PairingManager
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var attached = false
    private var isForeground = false
    private var ecosystemEnabled = false
    private var discoveryActive = false
    private var reconnectAttemptedThisForeground = false

    fun attach() {
        if (attached) return
        attached = true

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            appSettingsRepository.settingsFlow
                .map { it.ecosystemEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    ecosystemEnabled = enabled
                    if (!enabled) {
                        reconnectAttemptedThisForeground = false
                    }
                    reconcile()
                }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        isForeground = true
        reconnectAttemptedThisForeground = false
        reconcile()
    }

    override fun onStop(owner: LifecycleOwner) {
        isForeground = false
        reconnectAttemptedThisForeground = false
        reconcile()
    }

    private fun reconcile() {
        val shouldAdvertise = ecosystemEnabled && isForeground
        if (shouldAdvertise && !discoveryActive) {
            auraServiceRegistration.register()
            invitationListener.start()
            discoveryActive = true
        } else if (!shouldAdvertise && discoveryActive) {
            auraServiceRegistration.unregister()
            invitationListener.stop()
            discoveryActive = false
        }

        if (shouldAdvertise && !reconnectAttemptedThisForeground) {
            reconnectAttemptedThisForeground = true
            scope.launch {
                pairingManager.attemptReconnectIfNeeded()
            }
        }
    }
}
