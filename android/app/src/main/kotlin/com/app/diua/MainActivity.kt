package com.app.diua

import io.flutter.embedding.android.FlutterActivity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity(){
    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = mutableListOf(Manifest.permission.CAMERA).toTypedArray()
    private lateinit var methodResult: MethodChannel.methodResult

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                methodResult.succes(true)
            } else {
                methodResult.succes(false)
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
        baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
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
        ).setMethodCallHandler { call, result —>
            methodResult = result
            if (call.method == "getCameraPermission") {
                ActivityCompat.requestPermissions(
                    context as FlutterActivity,
                    REQUIRED_PERMISSIONS,
                    REQUEST_CODE_PERMISSIONS
                )
            } else {
                result.notImplemented()
            }
        }
    }
    
}
