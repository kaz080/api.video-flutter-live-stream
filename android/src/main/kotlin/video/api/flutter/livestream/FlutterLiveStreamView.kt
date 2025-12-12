package video.api.flutter.livestream

import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.util.Size
import android.view.Surface
import io.flutter.view.TextureRegistry
import io.github.thibaultbee.streampack.core.elements.encoders.AudioCodecConfig
import io.github.thibaultbee.streampack.core.elements.encoders.VideoCodecConfig
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.CameraSourceFactory
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.ICameraSource
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.cameraSingleStreamer
import io.github.thibaultbee.streampack.core.utils.extensions.isClosedException
import io.github.thibaultbee.streampack.ext.rtmp.configuration.mediadescriptor.RtmpMediaDescriptor
import video.api.flutter.livestream.utils.backCameraList
import video.api.flutter.livestream.utils.externalCameraList
import video.api.flutter.livestream.utils.frontCameraList
import video.api.flutter.livestream.utils.isBackCamera
import video.api.flutter.livestream.utils.isExternalCamera
import video.api.flutter.livestream.utils.isFrontCamera
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

class FlutterLiveStreamView(
    private val context: Context,
    textureRegistry: TextureRegistry,
    private val permissionsManager: PermissionsManager,
    private val onConnectionSucceeded: () -> Unit,
    private val onDisconnected: () -> Unit,
    private val onConnectionFailed: (String) -> Unit,
    private val onGenericError: (Exception) -> Unit,
    private val onVideoSizeChanged: (Size) -> Unit,
) {
    // CoroutineScope for Flow monitoring
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val flutterTexture = textureRegistry.createSurfaceTexture()
    val textureId: Long
        get() = flutterTexture.id()

    private val streamer: SingleStreamer = runBlocking {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = cameraManager.cameraIdList
        val initialCameraId = cameraIds.firstOrNull() ?: ""
        val streamerInstance = cameraSingleStreamer(
            context = context,
            cameraId = initialCameraId
        )
        // Start Flow monitoring
        setupEventListeners(streamerInstance)
        streamerInstance
    }

    private var currentCameraId: String = runBlocking {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = cameraManager.cameraIdList
        cameraIds.firstOrNull() ?: ""
    }

    private fun setupEventListeners(streamer: SingleStreamer) {
        // Monitor general errors (excluding connection errors)
        eventScope.launch {
            streamer.throwableFlow
                .filterNotNull()
                .filter { !it.isClosedException }
                .collect { throwable ->
                    val exception = if (throwable is Exception) {
                        throwable
                    } else {
                        Exception(throwable.message ?: throwable.javaClass.simpleName, throwable)
                    }
                    onGenericError(exception)
                }
        }

        // Monitor connection errors (when connection is closed)
        eventScope.launch {
            streamer.throwableFlow
                .filterNotNull()
                .filter { it.isClosedException }
                .collect { throwable ->
                    onConnectionFailed(throwable.message ?: "Connection lost")
                }
        }

        // Monitor connection state
        eventScope.launch {
            streamer.isOpenFlow.collect { isOpen ->
                if (!isOpen && _isStreaming) {
                    // When connection is closed
                    onDisconnected()
                }
            }
        }

        // Monitor streaming state
        eventScope.launch {
            streamer.isStreamingFlow.collect { isStreaming ->
                _isStreaming = isStreaming
                if (isStreaming) {
                    // Streaming started = connection succeeded
                    onConnectionSucceeded()
                }
            }
        }
    }

    private var _isPreviewing = false
    private var _isStreaming = false
    val isStreaming: Boolean
        get() = _isStreaming

    private var _videoConfig: VideoCodecConfig? = null
    val videoConfig: VideoCodecConfig
        get() = _videoConfig!!

    fun setVideoConfig(
        videoConfig: VideoCodecConfig,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (isStreaming) {
            throw UnsupportedOperationException("You have to stop streaming first")
        }

        onVideoSizeChanged(videoConfig.resolution)

        val wasPreviewing = _isPreviewing
        if (wasPreviewing) {
            stopPreview()
        }
        runBlocking {
            try {
                streamer.setVideoConfig(videoConfig)
                _videoConfig = videoConfig
                if (wasPreviewing) {
                    startPreview(onSuccess, onError)
                } else {
                    onSuccess()
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    private var _audioConfig: AudioCodecConfig? = null
    val audioConfig: AudioCodecConfig
        get() = _audioConfig!!

    fun setAudioConfig(
        audioConfig: AudioCodecConfig,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (isStreaming) {
            throw UnsupportedOperationException("You have to stop streaming first")
        }

        permissionsManager.requestPermission(
            Manifest.permission.RECORD_AUDIO,
            onGranted = {
                runBlocking {
                    try {
                        streamer.setAudioConfig(audioConfig)
                        _audioConfig = audioConfig
                        onSuccess()
                    } catch (e: Exception) {
                        onError(e)
                    }
                }
            },
            onShowPermissionRationale = { _ ->
                onError(SecurityException("Missing permission Manifest.permission.RECORD_AUDIO"))
            },
            onDenied = {
                onError(SecurityException("Missing permission Manifest.permission.RECORD_AUDIO"))
            })
    }

    var isMuted: Boolean
        get() = runBlocking {
            streamer.audioInput?.isMuted ?: false
        }
        set(value) {
            runBlocking {
                streamer.audioInput?.isMuted = value
            }
        }

    val camera: String
        get() = currentCameraId

    fun setCamera(camera: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        permissionsManager.requestPermission(
            Manifest.permission.CAMERA,
            onGranted = {
                runBlocking {
                    try {
                        val wasPreviewing = _isPreviewing
                        if (wasPreviewing) {
                            stopPreview()
                        }
                        currentCameraId = camera
                        // Switch camera: Set new camera source using CameraSourceFactory
                        streamer.setVideoSource(CameraSourceFactory(camera))
                        if (wasPreviewing) {
                            startPreview(onSuccess, onError)
                        } else {
                            onSuccess()
                        }
                    } catch (e: Exception) {
                        onError(e)
                    }
                }
            },
            onShowPermissionRationale = { _ ->
                onError(SecurityException("Missing permission Manifest.permission.CAMERA"))
            },
            onDenied = {
                onError(SecurityException("Missing permission Manifest.permission.CAMERA"))
            })
    }

    val cameraPosition: String
        get() = runBlocking {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            try {
                val characteristics = cameraManager.getCameraCharacteristics(currentCameraId)
                val facing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                when (facing) {
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT -> "front"
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK -> "back"
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_EXTERNAL -> "other"
                    else -> throw IllegalArgumentException("Invalid camera position for camera $currentCameraId")
                }
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid camera position for camera $currentCameraId", e)
            }
        }

    fun setCameraPosition(position: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = cameraManager.cameraIdList
        val facing = when (position) {
            "front" -> android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
            "back" -> android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
            "other" -> android.hardware.camera2.CameraCharacteristics.LENS_FACING_EXTERNAL
            else -> throw IllegalArgumentException("Invalid camera position: $position")
        }
        val cameraList = cameraIds.filter { cameraId ->
            try {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) == facing
            } catch (e: Exception) {
                false
            }
        }
        if (cameraList.isEmpty()) {
            onError(IllegalArgumentException("No camera found for position: $position"))
        } else {
            setCamera(cameraList.first(), onSuccess, onError)
        }
    }

    fun dispose() {
        stopStream()
        // Stop event monitoring
        eventScope.cancel()
        runBlocking {
            try {
                val videoInput = streamer.videoInput
                videoInput?.let {
                    val videoSource = it.sourceFlow.first() as? ICameraSource
                    videoSource?.stopPreview()
                }
            } catch (e: Exception) {
                // Ignore errors during dispose
            }
            try {
                streamer.release()
            } catch (e: Exception) {
                // Ignore errors during dispose
            }
        }
        flutterTexture.release()
    }

    fun startStream(url: String) {
        runBlocking {
            try {
                // Parse URL and create RtmpMediaDescriptor
                val uri = Uri.parse(url)
                val descriptor = RtmpMediaDescriptor(uri)
                streamer.open(descriptor)
                streamer.startStream()
                // Connection success event is emitted via isStreamingFlow
            } catch (e: Exception) {
                // Connection failure event is emitted via throwableFlow
                onConnectionFailed(e.message ?: "Failed to start stream")
                throw e
            }
        }
    }

    fun stopStream() {
        runBlocking {
            try {
                streamer.stopStream()
                streamer.close()
                // Disconnection event is emitted via isOpenFlow
            } catch (e: Exception) {
                // Ignore errors during stop
            } finally {
                _isStreaming = false
            }
        }
    }

    fun startPreview(onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        permissionsManager.requestPermission(
            Manifest.permission.CAMERA,
            onGranted = {
                if (_videoConfig == null) {
                    onError(IllegalStateException("Video has not been configured!"))
                } else {
                    runBlocking {
                        try {
                            val videoInput = streamer.videoInput
                            videoInput?.let {
                                val videoSource = it.sourceFlow.first() as? ICameraSource
                                videoSource?.let {
                                    it.startPreview(getSurface(videoConfig.resolution))
                                    _isPreviewing = true
                                    onSuccess()
                                } ?: run {
                                    onError(IllegalStateException("Video source is not available"))
                                }
                            } ?: run {
                                onError(IllegalStateException("Video input is not available"))
                            }
                        } catch (e: Exception) {
                            onError(e)
                        }
                    }
                }
            },
            onShowPermissionRationale = { _ ->
                onError(SecurityException("Missing permission Manifest.permission.CAMERA"))
            },
            onDenied = {
                onError(SecurityException("Missing permission Manifest.permission.CAMERA"))
            })
    }

    fun stopPreview() {
        runBlocking {
            try {
                val videoInput = streamer.videoInput
                videoInput?.let {
                    val videoSource = it.sourceFlow.first() as? ICameraSource
                    videoSource?.stopPreview()
                }
                _isPreviewing = false
            } catch (e: Exception) {
                // Ignore errors during stop preview
            }
        }
    }

    private fun getSurface(resolution: Size): Surface {
        val surfaceTexture = flutterTexture.surfaceTexture().apply {
            setDefaultBufferSize(
                resolution.width,
                resolution.height
            )
        }
        return Surface(surfaceTexture)
    }
}