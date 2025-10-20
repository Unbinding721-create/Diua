// android/app/src/main/kotlin/com/app/diua/MainActivity.kt 

package com.app.diua

import io.flutter.embedding.android.FlutterActivity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.MethodCall 

class MainActivity : FlutterActivity(){
    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = mutableListOf(Manifest.permission.CAMERA).toTypedArray()
    private lateinit var methodResult: MethodChannel.Result 
    private lateinit var debugChannel: MethodChannel

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        // Ensure Flutter and plugins can also receive this callback
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            val granted = allPermissionsGranted()
            // Guard against uninitialized result (e.g., activity recreation)
            if (this::methodResult.isInitialized) {
                methodResult.success(granted)
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
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

        debugChannel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "diua_debug" // Use a unique channel name
        )

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "camera_permission"
        )
        .setMethodCallHandler { call: MethodCall, result: Result -> 
            methodResult = result
            if (call.method == "getCameraPermission" || call.method == "requestCameraPermission") { 
                // We'll treat any call as a request, or better, change Dart to use ONE name.
                if (allPermissionsGranted()) {
                    result.success(true) // Permission already granted, return true immediately
                } else {
                    // Permission not granted, proceed to request
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
    }
}