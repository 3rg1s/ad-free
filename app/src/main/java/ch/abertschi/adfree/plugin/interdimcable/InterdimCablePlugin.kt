/*
 * Ad Free
 * Copyright (c) 2017 by abertschi, www.abertschi.ch
 * See the file "LICENSE" for the full license governing this code.
 */

package ch.abertschi.adfree.plugin.interdimcable

import android.content.Context
import android.view.View
import ch.abertschi.adfree.AudioController
import ch.abertschi.adfree.NotificationChannel
import ch.abertschi.adfree.model.PreferencesFactory
import ch.abertschi.adfree.model.YamlRemoteConfigFactory
import ch.abertschi.adfree.plugin.AdPlugin
import ch.abertschi.adfree.plugin.AudioPlayer
import ch.abertschi.adfree.plugin.PluginActivityAction
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error
import org.jetbrains.anko.info
import java.util.concurrent.TimeUnit

/**
 * Created by abertschi on 21.04.17.
 */
class InterdimCablePlugin(val prefs: PreferencesFactory,
                          val audioController: AudioController,
                          val globalContext: Context,
                          val notificationChannel: NotificationChannel) : AdPlugin, AnkoLogger {

    private val GITHUB_RAW_SUFFIX: String = "?raw=true"
    private val AD_FREE_RESOURCE_ADRESS: String
            = "https://github.com/abertschi/ad-free-resources/blob/master/"

    private val BASE_URL: String = AD_FREE_RESOURCE_ADRESS + "plugins/interdimensional-cable/"
    private val PLUGIN_FILE_PATH: String = BASE_URL + "plugin.yaml" + GITHUB_RAW_SUFFIX

    private var configFactory: YamlRemoteConfigFactory<InterdimCableModel> =
            YamlRemoteConfigFactory(PLUGIN_FILE_PATH, InterdimCableModel::class.java, prefs)

    private var model: InterdimCableModel? = null
    private var interdimCableView: InterdimCableView? = InterdimCableView(globalContext)

    private var player: AudioPlayer = AudioPlayer(globalContext, prefs, audioController)

    init {
    }

    override fun title(): String = "interdimensional cable"

    override fun hasSettingsView(): Boolean = true

    override fun settingsView(c: Context, actions: PluginActivityAction): View? {
        return interdimCableView?.onCreate(this)
    }

    override fun onPluginLoaded() {
        model = configFactory.loadFromLocalStore()
        updatePluginSettings()
    }

    override fun onPluginActivated() {
        onPluginLoaded()
    }

    override fun onPluginDeactivated() {
        forceStop({})
    }

    override fun stop(onStoped: () -> Unit) {
        player.stop(onStoped)
    }

    private fun updatePluginSettings(callback: (() -> Unit)? = null) {
        configFactory.downloadObservable()
                .subscribe(
                        { pair ->
                            model = pair.first
                            info("Interdimensional cable plugin settings updated")
                            info("downloaded meta data for " + model?.channels?.size + " channels")
                            configFactory.storeToLocalStore(model!!)
                            callback?.invoke()
                        },
                        { error ->
                            interdimCableView?.showInternetError()
                            callback?.invoke()
                        }
                )
    }

    override fun play() {
        if (model == null || model!!.channels == null || model!!.channels!!.isEmpty()) {
            updatePluginSettings(this::doPlay)
            return
        }
        doPlay()
    }

    private fun doPlay() {
        val list = model?.channels ?: listOf()
        if (list.isNotEmpty()) {
            val item = list[(Math.random() * list.size).toInt()]

            val url = BASE_URL + item.path + GITHUB_RAW_SUFFIX
            runAndCatchException {
                player.playWithCachingProxy(url)
                val title = item.name ?: item.path?.split("/")?.last()

                Observable.just(true).delay(1000, TimeUnit.MILLISECONDS)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread()).subscribe {
                            notificationChannel.updateAdNotification(
                                    title = title)
                        }
            }
        } else {
            interdimCableView?.showNoChannelsError()
        }
    }

    override fun playTrial() = play()

    fun configureAudioVolume() {
        audioController.showVoiceCallVolume()
    }

    override fun requestStop(onStoped: () -> Unit) {
        runAndCatchException({ player.requestStop(onStoped) })
    }

    override fun forceStop(onStoped: () -> Unit) {
        runAndCatchException({ player.forceStop(onStoped) })
    }

    private fun runAndCatchException(function: () -> Unit): Unit {
        try {
            function()
        } catch (e: Throwable) {
            interdimCableView?.showAudioError()
            error(e)
        }
    }
}