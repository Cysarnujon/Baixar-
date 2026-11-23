package com.cysarnujon.baixar

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : Activity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var engineReady = false
    private var isDownloading = false

    private lateinit var urlInput: EditText
    private lateinit var btnPaste: Button
    private lateinit var btnClear: Button
    private lateinit var btnMp4: Button
    private lateinit var btnMp3: Button
    private lateinit var btnUpdate: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        handleShareIntent(intent)
        initEngine()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        val title = TextView(this).apply {
            text = "Baixar YouTube"
            textSize = 24f
            setPadding(0, 0, 0, dp(6))
        }

        val subtitle = TextView(this).apply {
            text = "Cole o link abaixo e escolha MP4 (vídeo) ou MP3 (áudio). Os arquivos vão para Downloads/Baixar."
            textSize = 14f
            setPadding(0, 0, 0, dp(16))
        }

        urlInput = EditText(this).apply {
            hint = "https://youtube.com/watch?v=..."
            setSingleLine(false)
            maxLines = 3
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.parseColor("#EFEFEF"))
            setTextColor(Color.BLACK)
        }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(16))
        }

        btnPaste = Button(this).apply {
            text = "Colar link"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
            setOnClickListener { pasteFromClipboard() }
        }

        btnClear = Button(this).apply {
            text = "Limpar"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
            setOnClickListener { urlInput.setText("") }
        }

        actionRow.addView(btnPaste)
        actionRow.addView(btnClear)

        btnMp4 = Button(this).apply {
            text = "Baixar Vídeo (MP4)"
            isEnabled = false
            setBackgroundColor(Color.parseColor("#1976D2"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
            setOnClickListener { startDownload(audioOnly = false) }
        }

        btnMp3 = Button(this).apply {
            text = "Baixar Áudio (MP3)"
            isEnabled = false
            setBackgroundColor(Color.parseColor("#2E7D32"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16)
            }
            setOnClickListener { startDownload(audioOnly = true) }
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        }

        statusText = TextView(this).apply {
            text = "Inicializando motor de download..."
            textSize = 14f
            gravity = Gravity.START
            setPadding(0, 0, 0, dp(20))
        }

        btnUpdate = Button(this).apply {
            text = "Atualizar motor (yt-dlp)"
            isEnabled = false
            setOnClickListener { updateEngine() }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(urlInput)
        root.addView(actionRow)
        root.addView(btnMp4)
        root.addView(btnMp3)
        root.addView(progressBar)
        root.addView(statusText)
        root.addView(btnUpdate)

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            if (!sharedText.isNullOrEmpty()) {
                urlInput.setText(sharedText)
            }
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this)?.toString()?.trim()
            if (!text.isNullOrEmpty()) {
                urlInput.setText(text)
            }
        }
    }

    private fun initEngine() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().init(applicationContext)
                    FFmpeg.getInstance().init(applicationContext)
                }
                engineReady = true
                setControlsEnabled(true)
                statusText.text = "Pronto! Cole o link e escolha MP4 ou MP3."
            } catch (e: Exception) {
                statusText.text = "Erro ao iniciar motor: ${e.message}"
            }
        }
    }

    private fun updateEngine() {
        if (!engineReady || isDownloading) return
        setControlsEnabled(false)
        statusText.text = "Atualizando yt-dlp..."
        scope.launch {
            try {
                val status = withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().updateYoutubeDL(applicationContext)
                }
                statusText.text = "Atualização concluída: $status"
            } catch (e: Exception) {
                statusText.text = "Não foi possível atualizar: ${e.message}"
            } finally {
                setControlsEnabled(true)
            }
        }
    }

    private fun setControlsEnabled(enabled: Boolean) {
        btnMp4.isEnabled = enabled && engineReady
        btnMp3.isEnabled = enabled && engineReady
        btnUpdate.isEnabled = enabled && engineReady
    }

    private fun startDownload(audioOnly: Boolean) {
        val rawUrl = urlInput.text.toString().trim()
        if (rawUrl.isEmpty()) {
            statusText.text = "Cole um link primeiro."
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 101)
            statusText.text = "Permita o acesso ao armazenamento e toque no botão novamente."
            return
        }

        isDownloading = true
        setControlsEnabled(false)
        progressBar.progress = 0
        statusText.text = if (audioOnly) "Preparando áudio MP3..." else "Preparando vídeo MP4..."

        scope.launch {
            val workDir = File(cacheDir, "dl_${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                val savedPath = withContext(Dispatchers.IO) {
                    val request = YoutubeDLRequest(rawUrl).apply {
                        addOption("--no-playlist")
                        addOption("--windows-filenames")
                        addOption("-o", "${workDir.absolutePath}/%(title).80B.%(ext)s")
                        if (audioOnly) {
                            addOption("-x")
                            addOption("--audio-format", "mp3")
                            addOption("--audio-quality", "0")
                        } else {
                            addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
                            addOption("--merge-output-format", "mp4")
                        }
                    }

                    YoutubeDL.getInstance().execute(request, null) { progress, etaInSeconds, _ ->
                        runOnUiThread {
                            if (progress >= 0f) {
                                progressBar.progress = progress.toInt().coerceIn(0, 100)
                                statusText.text = "Baixando: ${progress.toInt()}% (restante: ${etaInSeconds}s)"
                            } else {
                                statusText.text = "Processando / convertendo..."
                            }
                        }
                    }

                    val outputFile = workDir.listFiles()?.firstOrNull { it.isFile && it.length() > 0 }
                        ?: throw IllegalStateException("Nenhum arquivo foi gerado.")

                    saveToDownloads(outputFile, audioOnly)
                }

                progressBar.progress = 100
                statusText.text = "Concluído! Salvo em:\n$savedPath"
            } catch (e: Exception) {
                statusText.text = "Erro no download: ${e.message}"
            } finally {
                workDir.deleteRecursively()
                isDownloading = false
                setControlsEnabled(true)
            }
        }
    }

    private fun saveToDownloads(sourceFile: File, audioOnly: Boolean): String {
        val mimeType = if (audioOnly) "audio/mpeg" else "video/mp4"
        val fileName = sourceFile.name

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Baixar")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Falha ao criar arquivo em Downloads.")

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    sourceFile.inputStream().use { input -> input.copyTo(out) }
                } ?: throw IllegalStateException("Falha ao gravar arquivo.")

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }

            return "Downloads/Baixar/$fileName"
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Baixar"
            ).apply { mkdirs() }

            val destFile = File(downloadsDir, fileName)
            sourceFile.copyTo(destFile, overwrite = true)
            MediaScannerConnection.scanFile(this, arrayOf(destFile.absolutePath), arrayOf(mimeType), null)
            return destFile.absolutePath
        }
    }
}
