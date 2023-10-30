package org.rjpd.data

import android.content.Context
import android.content.res.Resources
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.mobile.config.AWSConfiguration
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserAttributes
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


fun createSubDirectory(rootDirectory: String, subDirectory: String): File {
    val directory = File(
        rootDirectory,
        subDirectory
    )
    if (!directory.exists()) {
        directory.mkdirs()
    }
    return directory
}

fun moveContent(sourceDirOrFile: File, destDir: File): Boolean {
    if (!sourceDirOrFile.exists()) {
        Log.d("MultiSensorDataCollector", "$sourceDirOrFile does not exist")
        return false
    }

    if (!destDir.exists()) {
        destDir.mkdirs()
    }

    if (sourceDirOrFile.isDirectory) {
        val files = sourceDirOrFile.listFiles()
        for (file in files) {
            Log.d("MultiSensorDataCollector", "Moving file ${file.absolutePath}")
            val destFile = File(destDir, file.name)
            if (file.isDirectory) {
                moveContent(file, destFile)
            } else {
                file.renameTo(destFile)
            }
        }
    } else {
        val destFile = File(destDir, sourceDirOrFile.name)
        sourceDirOrFile.renameTo(destFile)
    }

    return true
}

fun getZipTargetFilename(currentOutputDir: File): String {
    val s = currentOutputDir.parent
    val f = currentOutputDir.name

    val destinationZipFile = File(s, "${f}.zip")

    return destinationZipFile.absolutePath
}

fun createZipFile(sourceDirectory: String, zipFilePath: String): Boolean {
    val sourceDir = File(sourceDirectory)
    val zipFile = File(zipFilePath)

    if (sourceDir.exists() && sourceDir.isDirectory) {
        val files = sourceDir.listFiles()
        val zipOutputStream = ZipOutputStream(FileOutputStream(zipFile))

        for (file in files) {
            addFileToZip(sourceDir, file, zipOutputStream)
        }

        zipOutputStream.close()
        return true

    } else {
        return false
    }
}

fun addFileToZip(baseDir: File, file: File, zipOutputStream: ZipOutputStream) {
    if (file.isDirectory) {
        val files = file.listFiles()
        for (f in files) {
            addFileToZip(baseDir, f, zipOutputStream)
        }
    } else {
        val entryName = file.path.substring(baseDir.path.length + 1)
        val zipEntry = ZipEntry(entryName)

        zipOutputStream.putNextEntry(zipEntry)
        val buffer = ByteArray(1024)
        val inputStream = FileInputStream(file)

        var length: Int
        while (inputStream.read(buffer).also { length = it } > 0) {
            zipOutputStream.write(buffer, 0, length)
        }

        inputStream.close()
        zipOutputStream.closeEntry()
    }
}

fun writeGeolocationData(
    gpsInterval: String,
    accuracy: String,
    latitude: String,
    longitude: String,
    outputDir: String,
    filename: String,
) {
    val localeDate = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())

    val line = "$localeDate,$gpsInterval,$accuracy,$latitude,$longitude\n"

    try {
        val file = File(outputDir, "${filename}.gps.csv")
        val writer = BufferedWriter(FileWriter(file, true))
        writer.append(line)
        writer.close()
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

fun writeSensorData(
    name: String?,
    axisData: String?,
    accuracy: Int?,
    timestamp: Long?,
    filename: String?,
) {
    val fmtDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS", Locale.getDefault()).format(Date())

    val line = "$fmtDate,$name,$axisData,$accuracy,$timestamp\n"

    try {
        val writer = BufferedWriter(FileWriter(filename, true))
        writer.append(line)
        writer.close()
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

fun getDeviceInfo(): JSONObject {
    val deviceInfo = JSONObject()
    deviceInfo.put("DeviceModel", Build.MODEL)
    deviceInfo.put("FirmwareVersion", Build.VERSION.RELEASE)
    deviceInfo.put("SoftwareVersion", Build.VERSION.SDK_INT)
    deviceInfo.put("AndroidVersion", Build.VERSION.RELEASE)
    Log.d("SensorsService", "$deviceInfo")
    return deviceInfo
}

fun getSensorInfo(sensorManager: SensorManager): JSONArray {
    val sensorArray = JSONArray()
    for (sensor in sensorManager.getSensorList(Sensor.TYPE_ALL)) {
        val sensorObj = JSONObject()
        sensorObj.put("Sensor Name", sensor.name)
        sensorObj.put("Type", sensor.type)
        sensorObj.put("Device Manufacturer", sensor.vendor)
        sensorObj.put("Version", sensor.version)
        sensorObj.put("Resolution", sensor.resolution)
        sensorObj.put("Power", sensor.power)

        sensorArray.put(sensorObj)
        Log.d("SensorsService", "$sensorObj")
    }
    return sensorArray
}

fun saveInfoToJson(file: String, info: JSONObject) {
    try {
        val file = File(file, "metadata.json")
        val fileWriter = FileWriter(file)
        fileWriter.write(info.toString())
        fileWriter.close()
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

fun retrieveAWSPoolInfo(resources: Resources, packageName:String): String {
    val resourceId = resources.getIdentifier("awsconfiguration", "raw", packageName)

    if (resourceId != 0) {
        val inputStream: InputStream = resources.openRawResource(resourceId)

        return inputStream.bufferedReader().use { it.readText() }
    } else {
        throw IllegalArgumentException("Jsonfile  with AWS configuration not found")
    }
}

fun sendFileToS3(context:Context, filePath: String, bucketName: String, bucketPath: String) {
    val accessKey = ""
    val secretKey = ""
    val file = File(filePath)

    try {
        //------------------------------------------------------------------------
        val credentials: BasicAWSCredentials = BasicAWSCredentials(accessKey, secretKey)
        val s3Client: AmazonS3Client = AmazonS3Client(
            credentials, Region.getRegion(Regions.US_WEST_2))

        val transferUtility: TransferUtility = TransferUtility.builder().context(context).s3Client(s3Client)
            .defaultBucket(bucketName).build()

        val uploadObserver = transferUtility.upload(bucketPath, file)

        uploadObserver.setTransferListener(object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState?) {
                if (TransferState.COMPLETED == state) {
                    Log.d(">>>", "Upload successfully complete")
                } else if (TransferState.FAILED == state) {
                    //Failed case from
                }
            }
            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
            //Useful to show upload progress
            }
            override fun onError(id: Int, ex: java.lang.Exception?) {
            //Error case
            }
        })
    }
    catch (exc: Exception) {
        Log.d(">>>", "Failed to send to S3", exc)
    }
    Log.d(">>>", "Done")
}

