package video.api.flutter.livestream.utils

import android.content.Context
import android.content.DialogInterface
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog


/**
 * Show a dialog with the given title and message.
 */
fun Context.showDialog(
    @StringRes title: Int,
    @StringRes message: Int = 0,
    @StringRes
    positiveButtonText: Int = android.R.string.ok,
    @StringRes
    negativeButtonText: Int = 0,
    onPositiveButtonClick: () -> Unit = {},
    onNegativeButtonClick: () -> Unit = {}
) {
    AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .apply {
            if (positiveButtonText != 0) {
                setPositiveButton(positiveButtonText) { dialogInterface: DialogInterface, _: Int ->
                    dialogInterface.dismiss()
                    onPositiveButtonClick()
                }
            }
            if (negativeButtonText != 0) {
                setNegativeButton(negativeButtonText) { dialogInterface: DialogInterface, _: Int ->
                    dialogInterface.dismiss()
                    onNegativeButtonClick()
                }
            }
        }
        .show()
}

/**
 * Get list of front cameras
 */
val Context.frontCameraList: List<String>
    get() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = cameraManager.cameraIdList
        return cameraIds.filter { cameraId ->
            try {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } catch (e: Exception) {
                false
            }
        }
    }

/**
 * Get list of back cameras
 */
val Context.backCameraList: List<String>
    get() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = cameraManager.cameraIdList
        return cameraIds.filter { cameraId ->
            try {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } catch (e: Exception) {
                false
            }
        }
    }

/**
 * Get list of external cameras
 */
val Context.externalCameraList: List<String>
    get() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = cameraManager.cameraIdList
        return cameraIds.filter { cameraId ->
            try {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_EXTERNAL
            } catch (e: Exception) {
                false
            }
        }
    }

/**
 * Check if camera is front camera
 */
fun Context.isFrontCamera(cameraId: String): Boolean {
    val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    return try {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
    } catch (e: Exception) {
        false
    }
}

/**
 * Check if camera is back camera
 */
fun Context.isBackCamera(cameraId: String): Boolean {
    val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    return try {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    } catch (e: Exception) {
        false
    }
}

/**
 * Check if camera is external camera
 */
fun Context.isExternalCamera(cameraId: String): Boolean {
    val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    return try {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_EXTERNAL
    } catch (e: Exception) {
        false
    }
}

