package org.rjpd.data

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log

class InfoUtils(context: Context) {
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun getAvailableSensors(): Map<String, Any> {
        val data = mutableMapOf<String, Any>()

        for (sensor in sensorManager.getSensorList(Sensor.TYPE_ALL)) {
            data[sensor.name] = mutableMapOf(
                "type" to sensor.type,
                "id" to sensor.id,
                "min_delay" to sensor.minDelay,
                "max_delay" to sensor.maxDelay,
                "resolution" to sensor.resolution,
                "power" to sensor.power,
                "version" to sensor.version,
            )
        }
        return data
    }

    fun getAvailableCameraConfigurations(): ArrayList<CameraConfiguration> {
        val cameraConfigurations = ArrayList<CameraConfiguration>()

        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)

            val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(
                    SurfaceTexture::class.java
                )

            val fpsRanges =
                characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)

            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)

            for (i in sizes!!.indices) {
                val size = sizes[i]
                val width = size.width
                val height = size.height

                try {
                    val fpsRange = fpsRanges?.get(i)
                    val averageFps = (fpsRange!!.lower + fpsRange.upper) / 2

                    val cc = CameraConfiguration(
                        cameraId = cameraId,
                        resolutionWidth = width,
                        resolutionHeight = height,
                        averageFps = averageFps,
                        lensFacing = lensFacing!!
                    )

                    cameraConfigurations.add(cc)
                    Log.d("InfoUtils", cc.getLabel())

                } catch (exc: ArrayIndexOutOfBoundsException) {
                    //
                }
            }
        }
        return cameraConfigurations
    }
}