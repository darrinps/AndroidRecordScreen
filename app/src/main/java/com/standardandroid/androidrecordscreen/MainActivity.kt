package com.standardandroid.androidrecordscreen

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.SparseIntArray
import android.view.Surface
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

const val REQUEST_PERMISSION = 1000
const val REQUEST_CODE = 1001
val mediaRecorder: MediaRecorder = MediaRecorder()
var mediaProjection: MediaProjection? = null
var virtualDisplay: VirtualDisplay? = null
private var mediaProjectionCallback: MediaProjectionCallback? = null

class MainActivity : AppCompatActivity() {

    private var isRecording: Boolean = false
    private val orientations: SparseIntArray = SparseIntArray()
    var videoUri: String = ""
    var screenDensity: Int = 0
    var mediaProjectionManager: MediaProjectionManager? = null
    var displayWidth:Int = 0
    var displayHeight:Int = 0

    init {
        orientations.append(Surface.ROTATION_0, 90)
        orientations.append(Surface.ROTATION_90, 0)
        orientations.append(Surface.ROTATION_180, 270)
        orientations.append(Surface.ROTATION_270, 180)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val displayMetrics: DisplayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        screenDensity = displayMetrics.densityDpi
        displayHeight = displayMetrics.heightPixels
        displayWidth = displayMetrics.widthPixels

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) +
                     ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)

        if(result != PackageManager.PERMISSION_GRANTED) {
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
               ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO) )
            {
                Snackbar.make(rootLayoutId, "Permissions", Snackbar.LENGTH_INDEFINITE).setAction("Enable", View.OnClickListener {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                                           Manifest.permission.RECORD_AUDIO),
                                                                           REQUEST_PERMISSION)
                }).show()
            }
            else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO),
                    REQUEST_PERMISSION)
            }
        }
        else {
            toggleScreenShare()
        }

    }

    private fun toggleScreenShare() {

        isRecording = if(this.isRecording) {
            mediaRecorder.stop()
            mediaRecorder.reset()
            mediaProjectionCallback?.stopRecordScreen()
            false
        }
        else {
            initRecorder()
            recordScreen()
            true
        }
    }

    private fun initRecorder() {
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)

        val videoName = StringBuilder("/clip").append(SimpleDateFormat("dd-MM-yyyy-hh_mm_ss").format(Date())).append(".mp4").toString()
        val videoFile = File(this.filesDir, videoName)

        videoUri = videoFile.absolutePath

        mediaRecorder.setOutputFile(videoUri)
        mediaRecorder.setVideoSize(displayWidth, displayHeight)

        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        mediaRecorder.setVideoEncodingBitRate(512*1000)
        mediaRecorder.setVideoFrameRate(30)

        val rotation = windowManager.defaultDisplay.rotation
        val orientation = orientations.get(rotation*90)

        mediaRecorder.setOrientationHint(orientation)

        mediaRecorder.prepare()


/*
        val resolver = this.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "CuteKitten001")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/PerracoLabs")
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
*/
    }

    private fun recordScreen() {
        if(mediaProjection == null) {
            startActivityForResult(mediaProjectionManager?.createScreenCaptureIntent(), REQUEST_CODE)
            return
        }

        virtualDisplay = createVirtualDisplay()

        mediaRecorder.start()
    }

    private fun createVirtualDisplay() : VirtualDisplay {
        return mediaProjection!!.createVirtualDisplay("MainActivity",
                                                          displayWidth,
                                                          displayHeight,
                                                          screenDensity,
                                                          DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                                                          mediaRecorder.surface, null, null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(requestCode != REQUEST_CODE) {
            Toast.makeText(this, "Unknown request code: $requestCode", Toast.LENGTH_SHORT).show()
            return
        }

        if(resultCode != Activity.RESULT_OK) {
            Toast.makeText(this, "Unknown result/Permission denied: $resultCode", Toast.LENGTH_SHORT).show()
            return
        }

        mediaProjectionCallback = MediaProjectionCallback()
        mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data!!)
        mediaProjection?.registerCallback(mediaProjectionCallback, null)

        virtualDisplay = createVirtualDisplay()

        mediaRecorder.start()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when(requestCode) {
            REQUEST_PERMISSION -> {
                if(grantResults.isNotEmpty() && grantResults[0] + grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    toggleScreenShare()
                }
                else {
                    Snackbar.make(rootLayoutId, "Permissions", Snackbar.LENGTH_INDEFINITE).setAction("Enable", View.OnClickListener {
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.RECORD_AUDIO),
                            REQUEST_PERMISSION)
                    }).show()
                }
                return
            }
        }
    }
}

private class MediaProjectionCallback : android.media.projection.MediaProjection.Callback(){

    override fun onStop() {
        super.onStop()

        mediaRecorder.stop()
        mediaRecorder.reset()
        mediaProjection = null
        stopRecordScreen()
    }

    fun stopRecordScreen() {
        if(virtualDisplay == null) {
            return
        }

        virtualDisplay?.release()
        destroyMediaProjection()
    }

    private fun destroyMediaProjection() {
        if(mediaProjection!= null ) {
            mediaProjection?.unregisterCallback(mediaProjectionCallback)
            mediaProjection?.stop()
            mediaProjection = null
        }
    }
}
