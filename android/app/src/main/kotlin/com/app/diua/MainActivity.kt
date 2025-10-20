// 🎯 android/app/src/main/kotlin/com/app/diua/MainActivity.kt 🎯

package com.app.diua

import io.flutter.embedding.android.FlutterActivity
import android.Manifest
import android.content.Intent
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.MethodCall 

class MainActivity : FlutterActivity(){
    private val REQUEST_CODE_PERMISSIONS = 10 // Camera
    private val REQUEST_CODE_STORAGE = 11
    private val REQUEST_CODE_CREATE_LOG = 12
    private val REQUIRED_PERMISSIONS = mutableListOf(Manifest.permission.CAMERA).toTypedArray()
    private val STORAGE_PERMISSIONS = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private lateinit var methodResult: MethodChannel.Result // camera result
    private var storagePermissionResult: MethodChannel.Result? = null
    private var pickLogFileResult: MethodChannel.Result? = null

    private lateinit var debugChannel: MethodChannel
    private lateinit var loggingChannel: MethodChannel

    private val prefs by lazy { getSharedPreferences("diua_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Global uncaught exception handler to capture crashes in logs
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            appendLogLine("FATAL(${thread.name}): ${throwable.javaClass.simpleName}: ${throwable.message}")
            appendLogLine(throwable.stackTrace.joinToString("\n") { it.toString() })
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        // Ensure Flutter and plugins can also receive this callback
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_CODE_PERMISSIONS -> {
                val granted = allPermissionsGranted()
                appendLogLine("Camera permission result: $granted")
                if (this::methodResult.isInitialized) {
                    methodResult.success(granted)
                }
            }
            REQUEST_CODE_STORAGE -> {
                val granted = allStoragePermissionsGranted()
                appendLogLine("Storage permission result: $granted")
                storagePermissionResult?.success(granted)
                storagePermissionResult = null
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CREATE_LOG) {
            if (resultCode == Activity.RESULT_OK) {
                val uri = data?.data
                if (uri != null) {
                    try {
                        val takeFlags = (data?.flags ?: 0) and (
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        contentResolver.takePersistableUriPermission(uri, takeFlags)
                    } catch (_: Exception) { /* ignore */ }
                    prefs.edit().putString("log_uri", uri.toString()).apply()
                    appendLogLine("Log file created: $uri")
                    pickLogFileResult?.success(uri.toString())
                } else {
                    pickLogFileResult?.success(null)
                }
            } else {
                pickLogFileResult?.success(null)
            }
            pickLogFileResult = null
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun allStoragePermissionsGranted(): Boolean {
        // On Android 10+ (API 29+), traditional storage permission is not
        // required for the Storage Access Framework flows we use.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        val writeGranted = ContextCompat.checkSelfPermission(
            baseContext, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val readGranted = ContextCompat.checkSelfPermission(
            baseContext, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        return writeGranted && readGranted
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        // Must call super
        super.configureFlutterEngine(flutterEngine) 

        flutterEngine
            .platformViewsController
            .registry
            .registerViewFactory(
                "camView",
                CameraViewFactory(this, messenger = flutterEngine.dartExecutor.binaryMessenger)
            )

        val messenger = flutterEngine.dartExecutor.binaryMessenger

        debugChannel = MethodChannel(messenger, "diua_debug")
        appendLogLine("configureFlutterEngine: debug channel ready")

        // Logging channel: storage permission, pick file, write lines, query uri
        loggingChannel = MethodChannel(messenger, "diua_logging")
        loggingChannel.setMethodCallHandler { call: MethodCall, result: Result ->
            appendLogLine("diua_logging call: ${call.method}")
            when (call.method) {
                "requestStoragePermission" -> {
                    appendLogLine("requestStoragePermission: checking")
                    if (allStoragePermissionsGranted()) {
                        appendLogLine("requestStoragePermission: already granted")
                        result.success(true)
                    } else {
                        storagePermissionResult = result
                        appendLogLine("requestStoragePermission: requesting")
                        ActivityCompat.requestPermissions(
                            this,
                            STORAGE_PERMISSIONS,
                            REQUEST_CODE_STORAGE
                        )
                    }
                }
                "pickLogFile" -> {
                    appendLogLine("pickLogFile: launching chooser")
                    pickLogFileResult = result
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TITLE, "diua_log.txt")
                    }
                    startActivityForResult(intent, REQUEST_CODE_CREATE_LOG)
                }
                "openLogFile" -> {
                    val uriString = prefs.getString("log_uri", null)
                    if (uriString == null) {
                        result.success(false)
                    } else {
                        try {
                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(uriString), "text/plain")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(viewIntent)
                            result.success(true)
                        } catch (_: Exception) {
                            result.success(false)
                        }
                    }
                }
                "writeLogLine" -> {
                    val line = (call.arguments as? String) ?: ""
                    appendLogLine("writeLogLine called")
                    val ok = appendLogLine(line)
                    result.success(ok)
                }
                "getLogUri" -> {
                    appendLogLine("getLogUri")
                    result.success(prefs.getString("log_uri", null))
                }
                else -> result.notImplemented()
            }
        }

        appendLogLine("configureFlutterEngine: camera_permission ready")
        MethodChannel(messenger, "camera_permission")
        .setMethodCallHandler { call: MethodCall, result: Result -> 
            methodResult = result
            appendLogLine("camera_permission call: ${call.method}")
            if (call.method == "getCameraPermission" || call.method == "requestCameraPermission") { 
                // We'll treat any call as a request, or better, change Dart to use ONE name.
                if (allPermissionsGranted()) {
                    appendLogLine("camera_permission: already granted")
                    result.success(true) // Permission already granted, return true immediately
                } else {
                    // Permission not granted, proceed to request
                    appendLogLine("camera_permission: requesting")
                    ActivityCompat.requestPermissions(
                        this, 
                        REQUIRED_PERMISSIONS,
                        REQUEST_CODE_PERMISSIONS
                    )
                }
            } else {
                result.notImplemented()
            }
        }
    }
    
    fun sendDebugMessage(message: String) {
        if (this::debugChannel.isInitialized) {
            // Use invokeMethod on the debugChannel to send data to Dart
            debugChannel.invokeMethod("debugMessage", message)
        }
        appendLogLine("DEBUG: $message")
    }

    private fun appendLogLine(line: String): Boolean {
        val uriString = prefs.getString("log_uri", null) ?: return false
        return try {
            val uri = Uri.parse(uriString)
            // Try append mode first; fallback to write if not supported
            val ok = contentResolver.openOutputStream(uri, "wa")?.use { os ->
                os.write((line + "\n").toByteArray())
                os.flush()
                true
            } ?: false
            if (!ok) {
                contentResolver.openOutputStream(uri, "w")?.use { os ->
                    os.write((line + "\n").toByteArray())
                    os.flush()
                } != null
            } else ok
        } catch (_: Exception) {
            false
        }
    }
}