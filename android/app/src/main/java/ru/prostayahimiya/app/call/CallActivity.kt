package ru.prostayahimiya.app.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import ru.prostayahimiya.app.BuildConfig

/*
 * Звонок в курсе (ученик ↔ учитель). Показывает страницу звонка с сайта (call.html) — так звонки
 * одинаково работают и в приложении, и в браузере, а голос и видео идут напрямую между телефонами.
 * Приложение даёт странице микрофон и камеру (после разрешения человека) и включает громкую связь.
 */
class CallActivity : ComponentActivity() {

    private lateinit var web: WebView
    private var pendingWebRequest: PermissionRequest? = null
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private var oldAudioMode = AudioManager.MODE_NORMAL
    /** Громкая связь: включена сразу при любом звонке, человек может выключить кнопкой «Динамик». */
    private var speakerWanted = true

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pendingWebRequest?.let { answer(it) }
        pendingWebRequest = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (!url.startsWith(BuildConfig.SERVER_URL.trimEnd('/'))) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.statusBarColor = DARK
        @Suppress("DEPRECATION")
        window.navigationBarColor = DARK

        web = WebView(this).apply {
            setBackgroundColor(DARK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = object : WebChromeClient() {
                // Страница просит микрофон или камеру: если человек ещё не разрешил — спрашиваем.
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread {
                        val missing = neededPermissions(request).filter { !granted(it) }
                        if (missing.isEmpty()) {
                            answer(request)
                        } else {
                            pendingWebRequest = request
                            askPermissions.launch(missing.toTypedArray())
                        }
                    }
                }
            }
            webViewClient = object : WebViewClient() {
                // Внутри окна звонка открываем только наш сайт, остальные ссылки — в браузере.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val target = request.url.toString()
                    if (target.startsWith(BuildConfig.SERVER_URL.trimEnd('/') + "/call.html")) return false
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, target.toUri())) }
                    if (target.startsWith(BuildConfig.SERVER_URL.trimEnd('/'))) finish()
                    return true
                }
            }
            addJavascriptInterface(Bridge(), "HimiyaCall")
        }
        setContentView(web)

        oldAudioMode = audio.mode
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        setSpeaker(speakerWanted)
        // Кнопки громкости во время звонка меняют громкость разговора.
        volumeControlStream = AudioManager.STREAM_VOICE_CALL

        // «Назад» — положить трубку и закрыть окно.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("window.HimiyaCallPage && window.HimiyaCallPage.hangup()") { finish() }
            }
        })
        if (savedInstanceState == null) web.loadUrl(url) else web.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::web.isInitialized) web.saveState(outState)
    }

    override fun onDestroy() {
        if (::web.isInitialized) {
            web.evaluateJavascript("window.HimiyaCallPage && window.HimiyaCallPage.hangup()", null)
            web.destroy()
            setSpeaker(false)
            audio.mode = oldAudioMode
        }
        super.onDestroy()
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun neededPermissions(request: PermissionRequest): List<String> = request.resources.mapNotNull {
        when (it) {
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
            else -> null
        }
    }

    /** Даём странице то, что человек разрешил. Если ничего — страница сама скажет, что без микрофона звонить нельзя. */
    private fun answer(request: PermissionRequest) {
        val allowed = request.resources.filter {
            (it == PermissionRequest.RESOURCE_AUDIO_CAPTURE && granted(Manifest.permission.RECORD_AUDIO)) ||
                (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE && granted(Manifest.permission.CAMERA))
        }
        if (allowed.isEmpty()) request.deny() else request.grant(allowed.toTypedArray())
    }

    /** Громкая связь (для видео — сразу). Если подключены наушники, звук идёт в них. */
    private fun setSpeaker(on: Boolean) {
        val headset = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        val speaker = on && !headset
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (speaker) {
                audio.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?.let { audio.setCommunicationDevice(it) }
            } else {
                audio.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = speaker
        }
    }

    /** Что умеет страница звонка вызвать в приложении. */
    private inner class Bridge {
        @JavascriptInterface
        fun close() = runOnUiThread { finish() }

        @JavascriptInterface
        fun setSpeakerOn(on: Boolean) = runOnUiThread {
            speakerWanted = on
            setSpeaker(on)
        }

        /** Звонок соединился: телефон мог сам переключить звук в разговорный динамик — возвращаем как было выбрано. */
        @JavascriptInterface
        fun reapplySpeaker() = runOnUiThread {
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
            setSpeaker(speakerWanted)
        }

        @JavascriptInterface
        fun isSpeakerOn(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            else @Suppress("DEPRECATION") audio.isSpeakerphoneOn
    }

    companion object {
        private const val EXTRA_URL = "url"
        /** Фон окна звонка — как у страницы звонка. */
        private const val DARK = 0xFF101413.toInt()
        private const val EXTRA_VIDEO = "video"

        fun open(context: Context, url: String, video: Boolean) {
            context.startActivity(
                Intent(context, CallActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_VIDEO, video),
            )
        }
    }
}
