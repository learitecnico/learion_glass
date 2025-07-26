package com.seudominio.app_smart_companion.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import java.io.ByteArrayOutputStream
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * CameraCapture simplificado para snapshots M400
 * Captura fotos e comprime para ≤200KB conforme especificação
 */
class CameraCapture(
    private val context: Context
) {
    companion object {
        private const val TAG = "CameraCapture"
        private const val MAX_IMAGE_SIZE_KB = 200 // 200KB limit
        private const val CAPTURE_WIDTH = 1280
        private const val CAPTURE_HEIGHT = 720
        private const val JPEG_QUALITY_START = 85
    }
    
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private val cameraOpenCloseLock = Semaphore(1)
    
    // Callback para snapshot capturado
    var onSnapshotCaptured: ((ByteArray) -> Unit)? = null
    
    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    
    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "Camera opened")
            cameraOpenCloseLock.release()
            cameraDevice = camera
            createCameraPreviewSession()
        }
        
        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "Camera disconnected")
            cameraOpenCloseLock.release()
            camera.close()
            cameraDevice = null
        }
        
        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "Camera error: $error")
            cameraOpenCloseLock.release()
            camera.close()
            cameraDevice = null
        }
    }
    
    fun initialize() {
        startBackgroundThread()
        openCamera()
    }
    
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper!!)
        Log.d(TAG, "Background thread started")
    }
    
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
        Log.d(TAG, "Background thread stopped")
    }
    
    private fun openCamera() {
        try {
            // Get back camera ID
            val cameraId = getCameraId() ?: run {
                Log.e(TAG, "No back camera found")
                return
            }
            
            // Setup ImageReader for capture
            imageReader = ImageReader.newInstance(
                CAPTURE_WIDTH, 
                CAPTURE_HEIGHT, 
                ImageFormat.JPEG, 
                1
            ).apply {
                setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)
            }
            
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            
            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open camera", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission not granted", e)
        }
    }
    
    private fun getCameraId(): String? {
        return try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                
                // Use back camera for snapshots
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return cameraId
                }
            }
            null
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to get camera ID", e)
            null
        }
    }
    
    private fun createCameraPreviewSession() {
        try {
            val surfaces = listOf(imageReader?.surface)
            
            cameraDevice?.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        
                        captureSession = session
                        Log.d(TAG, "Camera preview session created")
                    }
                    
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Failed to configure camera preview session")
                    }
                },
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to create camera preview session", e)
        }
    }
    
    /**
     * Captura snapshot e chama callback quando pronto
     */
    fun takePicture(callback: (ByteArray) -> Unit) {
        val reader = imageReader
        val session = captureSession
        
        if (reader == null || session == null) {
            Log.e(TAG, "Camera not ready for capture")
            return
        }
        
        try {
            // Temporarily store callback
            onSnapshotCaptured = callback
            
            val captureBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)?.apply {
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                set(CaptureRequest.JPEG_QUALITY, JPEG_QUALITY_START.toByte())
            }
            
            captureBuilder?.let { builder ->
                session.capture(
                    builder.build(),
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            Log.d(TAG, "Capture completed")
                        }
                        
                        override fun onCaptureFailed(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            failure: CaptureFailure
                        ) {
                            Log.e(TAG, "Capture failed: ${failure.reason}")
                        }
                    },
                    backgroundHandler
                )
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to capture image", e)
        }
    }
    
    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage()
        
        image?.use { img ->
            val buffer = img.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            
            // Compress image to meet 200KB limit
            val compressedBytes = compressImageToLimit(bytes)
            
            Log.d(TAG, "Image captured and compressed: ${compressedBytes.size} bytes")
            
            // Send via callback
            onSnapshotCaptured?.invoke(compressedBytes)
        }
    }
    
    /**
     * Comprime imagem para ficar ≤200KB
     */
    private fun compressImageToLimit(originalBytes: ByteArray): ByteArray {
        val maxSizeBytes = MAX_IMAGE_SIZE_KB * 1024
        
        if (originalBytes.size <= maxSizeBytes) {
            return originalBytes
        }
        
        val bitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size)
        var quality = JPEG_QUALITY_START
        var compressedBytes: ByteArray
        
        do {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            compressedBytes = outputStream.toByteArray()
            quality -= 5
        } while (compressedBytes.size > maxSizeBytes && quality > 10)
        
        Log.d(TAG, "Image compressed from ${originalBytes.size} to ${compressedBytes.size} bytes (quality: $quality)")
        
        return compressedBytes
    }
    
    fun dispose() {
        closeCamera()
        stopBackgroundThread()
        Log.d(TAG, "CameraCapture disposed")
    }
    
    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            
            captureSession?.close()
            captureSession = null
            
            cameraDevice?.close()
            cameraDevice = null
            
            imageReader?.close()
            imageReader = null
            
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while trying to lock camera closing", e)
        } finally {
            cameraOpenCloseLock.release()
        }
        
        Log.d(TAG, "Camera closed")
    }
}