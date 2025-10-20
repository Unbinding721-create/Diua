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
import androidx.camera.core.resolutionselector.Resolutionselector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.Constraintlayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.androidx.core.content.ContextCompat
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
    private val activity, FlutterActivity
) : PlatformView,GestureRecognizerHelper.GestureRecognizerListener, LifecycleEventObserver {

    private var constraintLayout = Constraintlayout(context)
    private var viewFinder = PreviewView(context)
    private var overlayView: OverlayView = OverlayView(context, null)

    private var backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT
    private var imageAnalyzer = ImageAnalysis? = null
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
        constraintLayout.LayoutParams = layoutParams

        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)

        viewFinder.id = View.generateViewId()
        viewFinder.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        constraintLayout.addView(viewFinder)
        constraintSet.constraintWidth(viewFinder.id, ConstraintSet.MATCH_CONSTRAINT)
        constraintSet.constraintHeight(viewFinder.id, ConstraintSet.MATCH_CONSTRAINT)
        constraintSet.connect(
            viewFinder.id,
            ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.LEFT
        )
        constraintSet.connect(
            viewFinder.id,
            ConstraintSet.RIGHT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.RIGHT
        )
        constraintSet.connect(
            viewFinder.id,
            ConstraintSet.TOP,
            ConstraintSet.PARENT_ID,
            ConstraintSet.TOP
        )
        constraintSet.connect(
            viewFinder.id,
            ConstraintSet.BOTTOM,
            ConstraintSet.PARENT_ID,
            ConstraintSet.BOTTOM
        )

        overlayView.id = View.generateViewId()
        constraintLayout.addView(overlayView)
        constraintSet.constraintWidth(viewFinder.id, ConstraintSet.MATCH_CONSTRAINT)
        constraintSet.constraintHeight(viewFinder.id, ConstraintSet.MATCH_CONSTRAINT)
        constraintSet.connect(
            overlayView.id,
            ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.LEFT
        )
        constraintSet.connect(
            overlayView.id,
            ConstraintSet.RIGHT,
            ConstraintSet.PARENT_ID,
            ConstraintSet.RIGHT
        )
        constraintSet.connect(
            overlayView.id,
            ConstraintSet.TOP,
            ConstraintSet.PARENT_ID,
            ConstraintSet.TOP
        )
        constraintSet.connect(
            overlayView.id,
            ConstraintSet.BOTTOM,
            ConstraintSet.PARENT_ID,
            ConstraintSet.BOTTOM
        )
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
        val resolutionselector = Resolutionselector.Builder()
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
                .setResolutionSelector(resolutionSelector)
                .setTargetRotation(viewFinder.display.rotation)
                .build()

        // ImageAnalysis using RGBA 8888 because it said so
        imageAnalyzer =
            ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setTargetRotation(viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // Then assign the analyzer to the instance
                .also {
                    it.setAnalyzer(backgroundExecutor) { image —>
                        recognizeHand(image)
                    }
                }


        // Unbind the use cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // idk what to comment
            camera = cameraProvider.bindToLifecycle(
                activity,
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
        gestureRecognizerHelper.recognizeLiveStream(
            imageProxy = ImageProxy,
        )
    }

     override fun onError(error: String, errorCode: Int) {
        log.i("Erroris",error)
    }

    override fin onResults(resultBundle: GestureRecognizerHelper.ResultBundle) {
        overlayView.setResults(
            resultBundle.results.first(),
            resultBundle.inputImageHeight,
            resultBundle.inputImageWidth,
            RunningMode.LIVE_STREAM
        )
        overlayView.invalidate() //idk if this is wrong spelling or its actually correct, the tutorial said so
    }

    override fun onStateChanged(
        source: LifecycleOwner,
        event: Lifecycle.Event
    ) {
        when (event) {
            Lifecycle.Event.ON_RESUME —> { //Is it actually em dash or is it just dash???
                backgroundExecutor.execute {
                    if (gestureRecognizerHelper.isClosed()) {
                        gestureRecognizerHelper.setupGestureRecognizer()
                    }
                }
            }

            Lifecycle.Event.ON_PAUSE —> {
                if (this::gestureRecognizerHelper.isInitialized) {
                    //close gesture recognizer helper 
                    backgroundExecutor.execute { gestureRecognizerHelper.clearGestureRecognizer() }
                }
            }

            Lifecycle.Event.ON_DESTROY —> {
                backgroundExecutor.shutdown()
                backgroundExecutor.awaitTermination(
                    Long.MAX_VALUE, TimeUnit.NANOSECONDS
                )
            }

            else —> {}
        }
    }
}

                