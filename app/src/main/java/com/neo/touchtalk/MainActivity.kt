package com.neo.touchtalk

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread

/** TouchTalk native Android app. Classic Bluetooth SPP protocol shared with the companion ESP32 sketch. */
class MainActivity : Activity() {
    private val bg = Color.rgb(11, 20, 36)
    private val surface = Color.rgb(20, 34, 54)
    private val surface2 = Color.rgb(29, 47, 70)
    private val mint = Color.rgb(105, 239, 192)
    private val blue = Color.rgb(137, 185, 255)
    private val text = Color.rgb(245, 248, 255)
    private val muted = Color.rgb(166, 184, 205)
    private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var socket: BluetoothSocket? = null
    @Volatile private var output: OutputStream? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var autoSpeak = true
    private var autoSend = true
    private var currentPage = "Home"
    private var lastIncoming = ""
    private var statusLine = "Not connected"
    private var lastDraft = ""

    private lateinit var pageRoot: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var latestMessageView: TextView
    private lateinit var draftView: TextView
    private lateinit var speechText: EditText
    private lateinit var braillePreview: TextView
    private lateinit var navHome: Button
    private lateinit var navTalk: Button
    private lateinit var navDevice: Button

    private val braille = mapOf(
        'a' to 1, 'b' to 3, 'c' to 9, 'd' to 25, 'e' to 17, 'f' to 11, 'g' to 27,
        'h' to 19, 'i' to 10, 'j' to 26, 'k' to 5, 'l' to 7, 'm' to 13, 'n' to 29,
        'o' to 21, 'p' to 15, 'q' to 31, 'r' to 23, 's' to 14, 't' to 30, 'u' to 37,
        'v' to 39, 'w' to 58, 'x' to 45, 'y' to 61, 'z' to 53,
        '1' to 1, '2' to 3, '3' to 9, '4' to 25, '5' to 17, '6' to 11, '7' to 27,
        '8' to 19, '9' to 10, '0' to 26, ',' to 2, ';' to 6, ':' to 18, '.' to 50,
        '!' to 22, '?' to 38, '-' to 36, '\'' to 4, '"' to 20
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        tts = TextToSpeech(this) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.getDefault()
        }
        buildShell()
        showPage("Home")
    }

    private fun buildShell() {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val scroll = ScrollView(this).apply { isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER }
        pageRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(24)) }
        scroll.addView(pageRoot)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(10), dp(8), dp(10), dp(8)); setBackgroundColor(Color.rgb(15, 27, 44)) }
        navHome = button("⌂\nHome", surface2, text) { showPage("Home") }
        navTalk = button("◉\nCommunicate", surface2, text) { showPage("Communicate") }
        navDevice = button("⌁\nDevice", surface2, text) { showPage("Device") }
        listOf(navHome, navTalk, navDevice).forEach { nav.addView(it, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginStart = dp(3); marginEnd = dp(3) }) }
        outer.addView(nav)
        setContentView(outer)
    }

    private fun showPage(page: String) {
        currentPage = page
        pageRoot.removeAllViews()
        when (page) {
            "Home" -> buildHome()
            "Communicate" -> buildCommunicate()
            else -> buildDevice()
        }
        navHome.alpha = if (page == "Home") 1f else .65f
        navTalk.alpha = if (page == "Communicate") 1f else .65f
        navDevice.alpha = if (page == "Device") 1f else .65f
    }

    private fun buildHome() {
        pageRoot.addView(rowBrand())
        pageRoot.addView(label("Communication, without barriers.", 15f, muted).apply { setPadding(0, dp(5), 0, dp(22)) })
        val connect = card()
        connect.addView(label("DEVICE STATUS", 11f, blue, true))
        statusView = label(statusLine, 19f, text, true).apply { setPadding(0, dp(9), 0, dp(5)) }
        connect.addView(statusView)
        connect.addView(label("Bluetooth Classic · ESP32-WROOM", 13f, muted).apply { setPadding(0, 0, 0, dp(14)) })
        connect.addView(button(if (socket?.isConnected == true) "Reconnect device" else "Connect TouchTalk device", mint, bg) { chooseDevice() }, full())
        pageRoot.addView(connect, bottom())

        val hero = card().apply { background = rounded(Color.rgb(21, 55, 62), 24) }
        hero.addView(label("TOUCHTALK EXPERIENCE", 11f, mint, true))
        hero.addView(label("Every message.\nA little more effortless.", 27f, text, true).apply { setPadding(0, dp(12), 0, dp(10)) })
        hero.addView(label("Your Braille keyboard and voice become one connected conversation.", 14f, Color.rgb(197, 220, 226)))
        pageRoot.addView(hero, bottom())

        val receive = card()
        receive.addView(label("BRAILLE  →  SPEECH", 11f, mint, true))
        receive.addView(label("Hear incoming messages", 20f, text, true).apply { setPadding(0, dp(8), 0, dp(4)) })
        receive.addView(label("Incoming ESP32 text is displayed and spoken automatically.", 13f, muted).apply { setPadding(0, 0, 0, dp(12)) })
        receive.addView(button("Open conversation  →", surface2, text) { showPage("Communicate") }, full())
        pageRoot.addView(receive, bottom())

        val send = card()
        send.addView(label("SPEECH  →  BRAILLE", 11f, blue, true))
        send.addView(label("Speak naturally", 20f, text, true).apply { setPadding(0, dp(8), 0, dp(4)) })
        send.addView(label("Recognized text is encoded and sent to the ESP32 automatically when recognition finishes.", 13f, muted).apply { setPadding(0, 0, 0, dp(12)) })
        send.addView(button("Start speaking  →", blue, bg) { startSpeechRecognition() }, full())
        pageRoot.addView(send, bottom())
        pageRoot.addView(label("TOUCHTALK  ·  NEO", 11f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(10)) })
    }

    private fun buildCommunicate() {
        pageRoot.addView(titleHeader("Conversation", "Messages from your device and your voice."))
        val incoming = card()
        incoming.addView(label("INCOMING · BRAILLE TO SPEECH", 11f, mint, true))
        latestMessageView = label(lastIncoming.ifBlank { "Waiting for your first message…" }, 22f, text, true).apply { setPadding(0, dp(12), 0, dp(10)); minHeight = dp(66) }
        incoming.addView(latestMessageView)
        draftView = label("Keypad draft: ${lastDraft.ifBlank { "—" }}", 13f, muted)
        incoming.addView(draftView)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("Repeat audio", surface2, text) { speak(lastIncoming) }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
        row.addView(button("Clear", surface2, text) { lastIncoming = ""; latestMessageView.text = "Waiting for your first message…" }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(5) })
        incoming.addView(row, top(12))
        pageRoot.addView(incoming, bottom())

        val outgoing = card()
        outgoing.addView(label("OUTGOING · SPEECH TO BRAILLE", 11f, blue, true))
        outgoing.addView(label("Speak a message", 21f, text, true).apply { setPadding(0, dp(8), 0, dp(4)) })
        outgoing.addView(label("When the phone finishes recognizing your speech, TouchTalk automatically converts it into Braille cells and transmits them.", 13f, muted).apply { setPadding(0, 0, 0, dp(14)) })
        outgoing.addView(button("●  Start speech recognition", blue, bg) { startSpeechRecognition() }, full())
        speechText = EditText(this).apply {
            hint = "Or type a message here…"
            setHintTextColor(muted)
            setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            textSize = 16f
            setPadding(dp(14), dp(12), dp(14), dp(12)); minLines = 2; maxLines = 5
            background = rounded(surface2, 14)
        }
        outgoing.addView(speechText, top(12))
        braillePreview = label("Braille preview will appear here.", 13f, muted).apply { setPadding(0, dp(12), 0, dp(8)) }
        outgoing.addView(braillePreview)
        outgoing.addView(button("Preview Braille", surface2, text) { updateBraillePreview() }, full())
        outgoing.addView(button("Transmit typed message", mint, bg) { transmitText(speechText.text.toString()) }, top(8))
        pageRoot.addView(outgoing, bottom())
        pageRoot.addView(label("Speech recognition opens your phone's speech service. Transmission is automatic after a successful result; typed text uses the explicit transmit control.", 12f, muted))
    }

    private fun buildDevice() {
        pageRoot.addView(titleHeader("Device & preferences", "Make TouchTalk work the way you need."))
        val conn = card()
        conn.addView(label("BLUETOOTH CONNECTION", 11f, blue, true))
        statusView = label(statusLine, 18f, text, true).apply { setPadding(0, dp(10), 0, dp(12)) }
        conn.addView(statusView)
        conn.addView(button("Find / connect paired device", mint, bg) { chooseDevice() }, full())
        pageRoot.addView(conn, bottom())

        val prefs = card()
        prefs.addView(label("AUTOMATION", 11f, mint, true))
        prefs.addView(label("Automatic voice playback", 17f, text, true).apply { setPadding(0, dp(10), 0, dp(4)) })
        prefs.addView(label("Speak every incoming message as soon as it arrives.", 13f, muted).apply { setPadding(0, 0, 0, dp(8)) })
        prefs.addView(button(if (autoSpeak) "Enabled  ✓" else "Disabled", if (autoSpeak) mint else surface2, if (autoSpeak) bg else text) { autoSpeak = !autoSpeak; showPage("Device") }, full())
        prefs.addView(label("Automatic Braille transmission", 17f, text, true).apply { setPadding(0, dp(16), 0, dp(4)) })
        prefs.addView(label("Send recognized speech to ESP32 without a second send tap.", 13f, muted).apply { setPadding(0, 0, 0, dp(8)) })
        prefs.addView(button(if (autoSend) "Enabled  ✓" else "Disabled", if (autoSend) mint else surface2, if (autoSend) bg else text) { autoSend = !autoSend; showPage("Device") }, full())
        pageRoot.addView(prefs, bottom())

        val diag = card()
        diag.addView(label("OUTPUT DIAGNOSTICS", 11f, blue, true))
        diag.addView(label("Test a Braille cell", 18f, text, true).apply { setPadding(0, dp(10), 0, dp(4)) })
        diag.addView(label("Sends a single six-dot mask to the ESP32. Use LEDs for initial tests; never connect coils directly to GPIO.", 13f, muted).apply { setPadding(0, 0, 0, dp(12)) })
        diag.addView(button("Test letter A", surface2, text) { sendLine("CELL:1"); toast("Sent Braille A (dot 1).") }, full())
        diag.addView(button("Clear outputs", surface2, text) { sendLine("CLEAR"); toast("Clear command sent.") }, top(8))
        pageRoot.addView(diag, bottom())
        pageRoot.addView(label("Build 2.0 · TouchTalk · NEO", 12f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(12)) })
    }

    private fun titleHeader(title: String, subtitle: String): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(20)) }
        box.addView(rowBrand())
        box.addView(label(title, 28f, text, true).apply { setPadding(0, dp(20), 0, dp(5)) })
        box.addView(label(subtitle, 14f, muted))
        return box
    }

    private fun rowBrand(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val mark = TextView(this).apply { text = "T"; textSize = 23f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(bg); background = rounded(mint, 15); layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)) }
        val nameCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(11), 0, 0, 0) }
        nameCol.addView(label("TouchTalk", 25f, text, true))
        nameCol.addView(label("ASSISTIVE COMMUNICATION", 9f, muted, true))
        row.addView(mark); row.addView(nameCol, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label("●", 16f, if (socket?.isConnected == true) mint else muted))
        return row
    }

    private fun card() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(17), dp(17), dp(17), dp(17)); background = rounded(surface, 22) }
    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply { text = value; textSize = size; setTextColor(color); if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD) }
    private fun button(value: String, color: Int, foreground: Int, action: () -> Unit) = Button(this).apply { text = value; textSize = 13f; isAllCaps = false; setTextColor(foreground); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); background = rounded(color, 14); setPadding(dp(9), dp(4), dp(9), dp(4)); setOnClickListener { action() } }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun full() = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun bottom() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
    private fun top(value: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(value) }

    private fun updateStatus(message: String) {
        statusLine = message
        if (::statusView.isInitialized) statusView.text = message
    }

    private fun hasBluetoothPermission(): Boolean = Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun chooseDevice() {
        if (Build.VERSION.SDK_INT >= 31 && !hasBluetoothPermission()) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 710)
            toast("Allow Bluetooth access, then connect again.")
            return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) { toast("Bluetooth is not supported on this phone."); return }
        if (!adapter.isEnabled) { toast("Turn on Bluetooth in Android settings first."); return }
        val devices = try { adapter.bondedDevices?.toList().orEmpty() } catch (_: SecurityException) { emptyList() }
        if (devices.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Pair TouchTalk first").setMessage("Open Android Bluetooth settings and pair with the device named TouchTalk, then return here.").setPositiveButton("OK", null).show()
            return
        }
        val names = devices.map { "${it.name ?: "Bluetooth device"}\n${it.address}" }
        AlertDialog.Builder(this).setTitle("Choose your device").setItems(names.toTypedArray()) { _, which -> connectDevice(devices[which]) }.setNegativeButton("Cancel", null).show()
    }

    @SuppressLint("MissingPermission")
    private fun connectDevice(device: BluetoothDevice) {
        updateStatus("Connecting to ${device.name ?: "device"}…")
        thread(name = "TouchTalkConnect") {
            try {
                try { socket?.close() } catch (_: Exception) { }
                val candidate = device.createRfcommSocketToServiceRecord(sppUuid)
                candidate.connect()
                socket = candidate
                output = candidate.outputStream
                runOnUiThread { updateStatus("Connected · ${device.name ?: "TouchTalk"}"); toast("TouchTalk connected") }
                val reader = BufferedReader(InputStreamReader(candidate.inputStream, Charsets.UTF_8))
                while (candidate.isConnected) {
                    val line = reader.readLine() ?: break
                    runOnUiThread { handleIncoming(line.trim()) }
                }
            } catch (e: Exception) {
                runOnUiThread { updateStatus("Connection interrupted"); toast("Bluetooth connection failed: ${e.message ?: "try reconnecting"}") }
            } finally {
                try { socket?.close() } catch (_: Exception) { }
                socket = null; output = null
                runOnUiThread { updateStatus("Not connected") }
            }
        }
    }

    private fun handleIncoming(line: String) {
        when {
            line.startsWith("DRAFT:") -> { lastDraft = line.removePrefix("DRAFT:"); if (::draftView.isInitialized) draftView.text = "Keypad draft: ${lastDraft.ifBlank { "—" }}" }
            line.startsWith("TEXT:") -> {
                val msg = line.removePrefix("TEXT:").trim()
                if (msg.isNotBlank()) { lastIncoming = msg; if (::latestMessageView.isInitialized) latestMessageView.text = msg; if (autoSpeak) speak(msg) }
            }
            line.startsWith("STATUS:") -> toast(line.removePrefix("STATUS:"))
            line.isNotBlank() -> { lastIncoming = line; if (::latestMessageView.isInitialized) latestMessageView.text = line; if (autoSpeak) speak(line) }
        }
    }

    private fun sendLine(line: String) {
        thread(name = "TouchTalkSend") {
            try {
                val stream = output ?: throw IllegalStateException("Connect to the ESP32 first")
                synchronized(this) { stream.write((line + "\n").toByteArray(Charsets.UTF_8)); stream.flush() }
            } catch (e: Exception) { runOnUiThread { toast(e.message ?: "Bluetooth send failed") } }
        }
    }

    private fun speak(value: String) {
        val clean = value.trim()
        if (clean.isBlank()) { toast("No message to read yet."); return }
        if (ttsReady) tts?.speak(clean, TextToSpeech.QUEUE_ADD, null, "tt-${System.currentTimeMillis()}")
        else toast("Phone text-to-speech is not ready yet.")
    }

    private fun startSpeechRecognition() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 711)
            toast("Allow microphone access, then tap Start speaking again.")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message for TouchTalk")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try { startActivityForResult(intent, 712) } catch (_: Exception) { toast("No speech recognition service is available on this phone.") }
    }

    @Deprecated("Platform speech recognition activity used for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 712 && resultCode == RESULT_OK) {
            val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
            if (result.isNotBlank()) {
                if (::speechText.isInitialized) speechText.setText(result)
                updateBraillePreview()
                if (autoSend) transmitText(result)
                else toast("Recognized. Automatic transmission is disabled in Device settings.")
            }
        }
    }

    private fun updateBraillePreview() {
        if (!::braillePreview.isInitialized || !::speechText.isInitialized) return
        val value = speechText.text.toString().trim().lowercase(Locale.ROOT)
        if (value.isBlank()) { braillePreview.text = "Speak or type a message first."; return }
        braillePreview.text = value.map { ch ->
            when { ch == ' ' -> "SPACE"; braille.containsKey(ch) -> "$ch ${dots(braille[ch]!!)}"; else -> "$ch · unsupported" }
        }.joinToString("   ")
    }

    private fun dots(mask: Int): String = (1..6).filter { mask and (1 shl (it - 1)) != 0 }.joinToString("·", prefix = "[", postfix = "]").ifEmpty { "[blank]" }

    private fun transmitText(raw: String) {
        val message = raw.trim().lowercase(Locale.ROOT)
        if (message.isBlank()) { toast("Speak or type a message first."); return }
        if (socket?.isConnected != true || output == null) { toast("Connect to your TouchTalk ESP32 before transmitting."); return }
        val cells = mutableListOf<Int>()
        message.forEach { ch -> if (ch == ' ') cells.add(0) else braille[ch]?.let { cells.add(it) } }
        if (cells.isEmpty()) { toast("No supported Braille characters were found."); return }
        thread(name = "TouchTalkBrailleSequence") {
            try {
                val stream = output ?: throw IllegalStateException("Bluetooth connection was lost")
                cells.forEachIndexed { index, mask ->
                    synchronized(this) { stream.write("CELL:$mask\n".toByteArray(Charsets.UTF_8)); stream.flush() }
                    if (index < cells.lastIndex) Thread.sleep(550)
                }
                synchronized(this) { stream.write("DONE\n".toByteArray(Charsets.UTF_8)); stream.flush() }
                runOnUiThread { toast("Message transmitted · ${cells.size} Braille cells") }
            } catch (e: Exception) { runOnUiThread { toast("Transmission stopped: ${e.message ?: "Bluetooth error"}") } }
        }
    }

    private fun toast(message: String) = runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }

    override fun onDestroy() {
        try { socket?.close() } catch (_: Exception) { }
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) { }
        super.onDestroy()
    }
}
