// 🎯 android/app/src/main/kotlin/com/app/diua/MainActivity.kt 🎯

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

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                methodResult.success(true) 
            } else {
                methodResult.success(false) 
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

        
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "camera_permission"
        )
        .setMethodCallHandler { call: MethodCall, result: Result -> 
            methodResult = result
            if (call.method == "getCameraPermission") {
                ActivityCompat.requestPermissions(
                    this, 
                    REQUIRED_PERMISSIONS,
                    REQUEST_CODE_PERMISSIONS
                )
            } else {
                result.notImplemented()
            }
        }
    }
}