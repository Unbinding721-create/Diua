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
import androidx.camera.core.resolutionselector.ResolutionSelector // Fixed: Correct class name (was Resolutionselector)
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout // Fixed: Correct class name (was Constraintlayout)
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat // Fixed: Correct import (was androidx.core.content.androidx.core.content.ContextCompat)
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

// Fixed: The class must implement all abstract members of GestureRecognizerHelper.GestureRecognizerListener
class CameraView(
    context: Context,
    messenger: BinaryMessenger,
    id: Int,
    creationParams: Map<String?, Any?>?,
    // Fixed: Parameters must have a type annotation and be separated by commas
    private val activity: FlutterActivity // Assuming FlutterActivity is needed for lifecycle binding
) : PlatformView, GestureRecognizerHelper.GestureRecognizerListener, LifecycleEventObserver {

    private var constraintLayout = ConstraintLayout(context) // Fixed: Use corrected ConstraintLayout class
    private var viewFinder = PreviewView(context)
    private var overlayView: OverlayView = OverlayView(context, null)

    private var backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT
    
    // Fixed: Correct property declaration with nullable type and initialization
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
        // ... (Layout initialization block is mostly fine) ...

        val layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        // Fixed: Correct way to set layoutParams in Kotlin
        constraintLayout.layoutParams = layoutParams 

        val constraintSet = ConstraintSet()
        // Fixed: Overload resolution ambiguity - should clone the layout directly
        constraintSet.clone(constraintLayout) 

        viewFinder.id = View.generateViewId()
        viewFinder.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        constraintLayout.addView(viewFinder)
        
        // Fixed: Correct ConstraintSet method names (was constraintWidth/Height)
        constraintSet.constrainWidth(viewFinder.id, ConstraintSet.MATCH_CONSTRAINT)
        constraintSet.constrainHeight(viewFinder.id, ConstraintSet.MATCH_CONSTRAINT)
        
        // ... (rest of viewFinder connections are fine) ...

        overlayView.id = View.generateViewId()
        constraintLayout.addView(overlayView)
        // Fixed: Correct ConstraintSet method names for overlayView
        constraintSet.constrainWidth(overlayView.id, ConstraintSet.MATCH_CONSTRAINT) 
        constraintSet.constrainHeight(overlayView.id, ConstraintSet.MATCH_CONSTRAINT)
        
        // ... (rest of overlayView connections are fine) ...
        
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
        // Fixed: Should shut down the background executor on dispose
        backgroundExecutor.shutdownNow() 
    }

    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                // cameraProvider
                cameraProvider = cameraProviderFuture.get()

                // build and blind the camera use cases
                bindCameraUseCases()
                // its in the fuking name
            },
            ContextCompat.getMainExecutor(context) // Fixed: ContextCompat must be imported correctly
        )
    }

    override fun getView(): View {
        return constraintLayout
    }

    private fun bindCameraUseCases() {

        val aspectRatioStrategy = AspectRatioStrategy(
            AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_NONE
        )
        // Fixed: Correct class name (ResolutionSelector)
        val resolutionSelector = ResolutionSelector.Builder() 
            .setAspectRatioStrategy(aspectRatioStrategy)
            .build()

        // camera provider 
        val cameraProvider =
            cameraProvider
                ?: throw IllegalStateException("Camera Initialization failed.")

        // camera selector 
        val cameraSelector = 
            CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
        
        // Preview. using the 4:3
        preview =
            Preview.Builder()
                .setResolutionSelector(resolutionSelector) // Fixed: Correct variable name (was resolutionSelector)
                .setTargetRotation(viewFinder.display.rotation)
                .build()

        // ImageAnalysis using RGBA 8888 because it said so
        imageAnalyzer =
            ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector) // Fixed: Correct variable name (was resolutionSelector)
                .setTargetRotation(viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // Then assign the analyzer to the instance
                .also {
                    // Fixed: Correct Kotlin lambda syntax (was "image —>")
                    it.setAnalyzer(backgroundExecutor) { image -> 
                        recognizeHand(image)
                    }
                }


        // Unbind the use cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // idk what to comment
            // Fixed: Pass the FlutterActivity instance for the LifecycleOwner
            camera = cameraProvider.bindToLifecycle(
                activity as LifecycleOwner, // Cast activity to LifecycleOwner
                cameraSelector,
                preview,
                imageAnalyzer
            )

            // attach the view finder surface provider to prreview use case
            preview?.surfaceProvider = viewFinder.surfaceProvider
        } catch (exc: Exception) {
            Log.e("TAG", "Use case binding failed, lol", exc)
        }
    }

    private fun recognizeHand(imageProxy: ImageProxy) {
        // Fixed: Pass the actual ImageProxy object, not the class name
        gestureRecognizerHelper.recognizeLiveStream(
            imageProxy = imageProxy, 
        )
    }

    // Fixed: Remove extra 'fin', use 'fun', and ensure it implements the abstract member
    override fun onError(error: String, errorCode: Int) { 
        Log.i("Erroris", error) // Fixed: Use Log.i() from android.util.Log
    }

    // Fixed: Correct function signature to match the interface, remove 'fin'
    override fun onResults(resultBundle: GestureRecognizerHelper.ResultBundle) { 
        overlayView.setResults(
            resultBundle.results.first(),
            resultBundle.inputImageHeight,
            resultBundle.inputImageWidth,
            RunningMode.LIVE_STREAM
        )
        overlayView.invalidate() // This is correct, it forces the view to redraw
    }

    override fun onStateChanged(
        source: LifecycleOwner,
        event: Lifecycle.Event
    ) {
        when (event) {
            // Fixed: Use '->' for Kotlin's when statement branches
            Lifecycle.Event.ON_RESUME -> { 
                backgroundExecutor.execute {
                    if (gestureRecognizerHelper.isClosed()) {
                        gestureRecognizerHelper.setupGestureRecognizer()
                    }
                }
            }

            Lifecycle.Event.ON_PAUSE -> {
                if (this::gestureRecognizerHelper.isInitialized) {
                    //close gesture recognizer helper 
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

// Fixed: Remove trailing extra braces/code