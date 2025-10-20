package com.app.diua

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

class CameraViewFactory(
    // activity must be declared as val to be accessible inside create()
    val activity: FlutterActivity, 
    private val messenger: BinaryMessenger
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val creationParams = args as Map<String?, Any?>?
        
        val cameraView = CameraView(context, messenger, viewId, creationParams, activity)
        
        // hi there
        (activity as LifecycleOwner).lifecycle.addObserver(cameraView)
        
        return cameraView
    }
}