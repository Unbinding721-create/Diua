package com.app.diua

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.tasks.vision.core.RunningMode
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.platform.PlatformView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import androidx.camera.core.ImageProxy

class CameraView(
    private val context: Context, 
    messenger: BinaryMessenger,
    id: Int,
    creationParams: Map<String?, Any?>?,
    private val activity: FlutterActivity
) : PlatformView, GestureRecognizerHelper.GestureRecognizerListener, LifecycleEventObserver {

    private var constraintLayout = ConstraintLayout(context)
    private var viewFinder = PreviewView(context)
    private var overlayView: OverlayView = OverlayView(context, null)

    private var backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT
    
    private var imageAnalyzer: ImageAnalysis? = null
    private var preview: Preview? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private lateinit var gestureRecognizerHelper: GestureRecognizerHelper

    private var delegate: Int = GestureRecognizerHelper.DELEGATE_CPU
    private var minHandDetectionConfidence: Float =
        GestureRecognizerHelper.DEFAULT_HAND_DETECTION_CONFIDENCE
    private var minHandTrackingConfidence: Float = GestureRecognizerHelper
        .DEFAULT_HAND_TRACKING_CONFIDENCE
    private var minHandPresenceConfidence: Float = GestureRecognizerHelper
        .DEFAULT_HAND_PRESENCE_CONFIDENCE


    init {
        val layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        constraintLayout.layoutParams = layoutParams 

        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout) 

        viewFinder.id = View.generateViewId()
        viewFinder.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        constraintLayout.addView(viewFinder)
        
        constraintSet.connect(viewFinder.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        constraintSet.connect(viewFinder.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        constraintSet.connect(viewFinder.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(viewFinder.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        
        constraintSet.constrainWidth(viewFinder.id, ConstraintSet.MATCH_CONSTRAINT)
        constraintSet.constrainHeight(viewFinder.id, ConstraintSet.MATCH_CONSTRAINT)

        overlayView.id = View.generateViewId()
        constraintLayout.addView(overlayView)

        constraintSet.connect(overlayView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        constraintSet.connect(overlayView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        constraintSet.connect(overlayView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(overlayView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        
        constraintSet.constrainWidth(overlayView.id, ConstraintSet.MATCH_CONSTRAINT) 
        constraintSet.constrainHeight(overlayView.id, ConstraintSet.MATCH_CONSTRAINT)
        
        constraintLayout.bringChildToFront(overlayView)
        constraintSet.applyTo(constraintLayout)

        backgroundExecutor.execute {
             gestureRecognizerHelper = GestureRecognizerHelper(
                 context = context,
                 runningMode = RunningMode.LIVE_STREAM,
                 minHandDetectionConfidence = minHandDetectionConfidence,
                 minHandTrackingConfidence = minHandTrackingConfidence,
                 minHandPresenceConfidence = minHandPresenceConfidence,
                 currentDelegate = delegate,
                 gestureRecognizerListener = this
             )

             viewFinder.post {
                 setUpCamera()
             }
        }
    }

    override fun dispose() {
        backgroundExecutor.shutdownNow() 
    }

    private fun setUpCamera() {
        Log.i("DiuaCamera", "Attempting to get CameraProvider.") // 💡 Log added
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
                Log.i("DiuaCamera", "CameraProvider received. Binding use cases.") // 💡 Log added
                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    override fun getView(): View {
        return constraintLayout
    }

    private fun bindCameraUseCases() {

        val aspectRatioStrategy = AspectRatioStrategy(
            AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_NONE
        )
        val resolutionSelector = ResolutionSelector.Builder() 
            .setAspectRatioStrategy(aspectRatioStrategy)
            .build()

        val cameraProvider =
            cameraProvider
                ?: throw IllegalStateException("Camera Initialization failed.")

        val cameraSelector = 
            CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
        
        preview =
            Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .setTargetRotation(viewFinder.display.rotation)
                .build()

        imageAnalyzer =
            ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setTargetRotation(viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(backgroundExecutor) { image -> 
                        recognizeHand(image)
                    }
                }

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(
                activity as LifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            preview?.surfaceProvider = viewFinder.surfaceProvider
            Log.i("DiuaCamera", "Camera bound and surface attached.") // 💡 Log added
        } catch (exc: Exception) {
            Log.e("TAG", "Use case binding failed, lol", exc)
        }
    }

    private fun recognizeHand(imageProxy: ImageProxy) {
        // Ensure ImageProxy is always closed to prevent buffer overflow crashes.
        try {
            if (this::gestureRecognizerHelper.isInitialized) {
                gestureRecognizerHelper.recognizeLiveStream(imageProxy = imageProxy)
            }
        } finally {
            imageProxy.close()
        }
    }

    override fun onError(error: String, errorCode: Int) { 
        Log.e("DiuaGesture", "MediaPipe Error: $error (Code: $errorCode)")
    (activity as MainActivity).sendDebugMessage("MP ERROR: $error (Code: $errorCode)")
    }

    override fun onResults(resultBundle: GestureRecognizerHelper.ResultBundle) { 
        val handCount = resultBundle.results.first().handedness().size

        (activity as MainActivity).sendDebugMessage("MP SUCCESS: Detected $handCount hand(s).")

        overlayView.setResults(
            resultBundle.results.first(),
            resultBundle.inputImageHeight,
            resultBundle.inputImageWidth,
            RunningMode.LIVE_STREAM
        )
        overlayView.invalidate()
    }

    override fun onStateChanged(
        source: LifecycleOwner,
        event: Lifecycle.Event
    ) {
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                backgroundExecutor.execute {
                    if (this::gestureRecognizerHelper.isInitialized && gestureRecognizerHelper.isClosed()) {
                        gestureRecognizerHelper.setupGestureRecognizer()
                    }
                }
            }

            Lifecycle.Event.ON_PAUSE -> {
                if (this::gestureRecognizerHelper.isInitialized) {
                    backgroundExecutor.execute { gestureRecognizerHelper.clearGestureRecognizer() }
                }
            }

            Lifecycle.Event.ON_DESTROY -> {
                backgroundExecutor.shutdown()
                try {
                    backgroundExecutor.awaitTermination(
                        Long.MAX_VALUE, TimeUnit.NANOSECONDS
                    )
                } catch (e: InterruptedException) {
                    Log.e("TAG", "Background executor interrupted during shutdown")
                }
            }

            else -> {}
        }
    }
}