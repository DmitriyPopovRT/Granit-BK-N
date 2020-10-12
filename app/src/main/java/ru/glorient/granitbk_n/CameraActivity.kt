package ru.glorient.granitbk_n

import android.Manifest
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_camera.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class CameraActivity : AppCompatActivity() {

    // Разрешения для android 6 и выше
    private val WRITE_REQUEST_CODE = 0
    var permissions = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    // Задняя камера
    private var mBackCamera: Camera? = null

    // Фронтальная камера
    private var mFrontCamera: Camera? = null

    // Первью задней камеры
    private var mBackCamPreview: BackCamera? = null

    // Первью фронтальной камеры
    private var mFrontCamPreview: FrontCamera? = null

    // Кнопка начать/остановить запись с задней камеры
    private var recordBtn: Button? = null

    // Кнопка начать/остановить запись с фронтальной камеры
    private var recordBtn2: Button? = null

    // Флаги для записи
    private var recording = false
    private var recording2 = false

    private var file: File? = null
    private var videoFileCat: File? = null

    private var mediaRecorder1: MediaRecorder? = MediaRecorder()
    private var mediaRecorder2: MediaRecorder? = MediaRecorder()

    private var TAG = "DualCamActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        // Путь к файлу видео
        videoFileCat = File("storage/87CB-16F2/Movies/cat.mp4")

        // Проверяем версию андроид, если выше 6.0 то выдаем запросы о разрешениях
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //тут шестая версия и выше
            ActivityCompat.requestPermissions(this, permissions, WRITE_REQUEST_CODE)
        }

        recordBtn = findViewById(R.id.btnStartRecord)
        recordBtn?.setOnClickListener(recordVideoListener1)

        recordBtn2 = findViewById(R.id.btnStartRecord2)
        recordBtn2?.setOnClickListener(recordVideoListener2)

        btnPreview1.setOnClickListener {
            restartSurface1()
        }
        btnPreview2.setOnClickListener {
            restartSurface2()
        }
    }

    // Обработчик нажатия на кнопку Запись с задней камеры
    private val recordVideoListener1 = View.OnClickListener {
        if (recording) {
            mediaRecorder1?.stop()
            mediaRecorder1?.reset()
            mBackCamera!!.lock()
            recording = false
            recordBtn!!.text = "Старт 1"
            restartSurface1()
        } else {
            prepareToRecordVideo()
            try {
                mediaRecorder1?.prepare()
            } catch (e: IOException) {
                Log.i(TAG, "Prepare failed")
            }
            mediaRecorder1?.start()
            recording = true
            recordBtn!!.text = "Стоп 1"
        }
    }

    // Обработчик нажатия на кнопку Запись с фронтальной камеры
    private val recordVideoListener2 = View.OnClickListener {
        if (recording2) {
            mediaRecorder2?.stop()
            mediaRecorder2?.reset()
            mFrontCamera!!.lock()
            recording2 = false
            recordBtn2!!.text = "Старт 2"
            restartSurface2()
        } else {
            prepareToRecordVideo2()
            try {
                mediaRecorder2?.prepare()
            } catch (e: IOException) {
                Log.i(TAG, "Prepare failed")
            }
            mediaRecorder2?.start()
            recording2 = true
            recordBtn2!!.text = "Стоп 2"
        }
    }

    // Подготавливаемся к записи с задней камеры
    private fun prepareToRecordVideo() {
        mBackCamera!!.unlock()
        mediaRecorder1 = MediaRecorder()
        mediaRecorder1?.setCamera(mBackCamera)
        mediaRecorder1?.setAudioSource(MediaRecorder.AudioSource.DEFAULT)
        mediaRecorder1?.setVideoSource(MediaRecorder.VideoSource.CAMERA)
        mediaRecorder1?.setProfile(CamcorderProfile.get(0, CamcorderProfile.QUALITY_1080P))
        mediaRecorder1?.setAudioSamplingRate(8000)
        mediaRecorder1?.setOutputFile(initFile("CAMERA_1_").toString())
        mediaRecorder1?.setPreviewDisplay(mBackCamPreview?.holder?.surface)
        mediaRecorder1?.setOnErrorListener { mediaRecorder, i, i1 ->
            Log.i(
                TAG,
                "RECORDING FAILED ERROR CODE: $i AND EXTRA CODE: $i1"
            )
        }
    }

    // Подготавливаемся к записи с фронтальной камеры
    private fun prepareToRecordVideo2() {
        mFrontCamera!!.unlock()
        mediaRecorder2 = MediaRecorder()
        mediaRecorder2?.setCamera(mFrontCamera)

        val cp = CamcorderProfile.get(1, CamcorderProfile.QUALITY_1080P)

        mediaRecorder2?.setVideoSource(MediaRecorder.VideoSource.CAMERA)
        mediaRecorder2?.setOutputFormat(cp.fileFormat)
        mediaRecorder2?.setVideoEncoder(cp.videoCodec)
        mediaRecorder2?.setVideoEncodingBitRate(cp.videoBitRate)
        mediaRecorder2?.setVideoFrameRate(cp.videoFrameRate)
        mediaRecorder2?.setVideoSize(cp.videoFrameWidth, cp.videoFrameHeight)
        mediaRecorder2?.setOutputFile(initFile("CAMERA_2_").toString())
        mediaRecorder2?.setPreviewDisplay(mFrontCamPreview?.holder?.surface)
    }

    // Захватываем изображение задней камеры
    private fun restartSurface1() {
        mBackCamera = getCameraInstance(0)
        mBackCamPreview = BackCamera(this, mBackCamera)
        val backPreview = findViewById<View>(R.id.surfaceView) as FrameLayout
        backPreview.addView(mBackCamPreview)
        try {
            mBackCamera!!.setPreviewDisplay(mBackCamPreview!!.getHolder())
            mBackCamera!!.startPreview()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    // Захватываем изображение фронтальной камеры
    private fun restartSurface2() {
        mFrontCamera = getCameraInstance(1)
        mFrontCamPreview = FrontCamera(this, mFrontCamera)
        val frontPreview = findViewById<View>(R.id.surfaceView2) as FrameLayout
        frontPreview.addView(mFrontCamPreview)
        try {
            mFrontCamera!!.setPreviewDisplay(mFrontCamPreview!!.getHolder())
            mFrontCamera!!.startPreview()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()

        // Захватываем изображение
        restartSurface1()
        restartSurface2()
    }

    // Останавливаем камеру и освобождаем ее
    override fun onPause() {
        super.onPause()
        releaseMediaRecorder()
        if (mBackCamera != null) mBackCamera!!.release()
        mBackCamera = null
        if (mFrontCamera != null) mFrontCamera!!.release()
        mFrontCamera = null
    }

    // Указываем директорию для записи видеофайлов
    private fun initFile(cameraNamePrefix: String): File? {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            ), "DualCameraCapture"
        )
        if (!dir.exists() && !dir.mkdirs()) {
            file = null
        } else {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val mediaFile: File
            mediaFile = File(
                dir.path + File.separator +
                        cameraNamePrefix + timeStamp + ".mp4"
            )
            Log.i(TAG, mediaFile.absolutePath)
            return mediaFile
        }
        return file
    }

    // Воспроизводим видео с SD карты
    fun onClickPlay(view: View?) {
        val mediaController = MediaController(this)
        mediaController.setMediaPlayer(videoView)
        videoView.setOnPreparedListener { mp -> mp.isLooping = true }
        videoView.setVideoPath(videoFileCat!!.absolutePath)
        videoView.setMediaController(mediaController)
        videoView.requestFocus()
        videoView.start()
        mediaController.show()
    }

    // Закрываем и освобождаем объекты
    private fun releaseMediaRecorder() {
        if (mediaRecorder1 != null) {
            mediaRecorder1?.reset()
            mediaRecorder1?.release()
            mediaRecorder1 = null
            mBackCamera!!.lock()
        }
        if (mediaRecorder2 != null) {
            mediaRecorder2?.reset()
            mediaRecorder2?.release()
            mediaRecorder2 = null
            mFrontCamera!!.lock()
        }
    }

    // Открываем камеры
    private fun getCameraInstance(cameraId: Int): Camera? {
        var camera: Camera? = null
        try {
            camera = Camera.open(cameraId)
        } catch (e: java.lang.Exception) {
            Log.e(
                TAG,
                "Camera $cameraId is not available $e"
            )
        }
        return camera
    }
}