package ru.glorient.granitbk_n

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class MainActivity : AppCompatActivity() {

    // Storage Permissions
    val REQUEST_EXTERNAL_STORAGE = 1;
    val PERMISSIONS_STORAGE = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        verifyStoragePermissions(this)

        // При нажатии на значок переходим в активность Камера
        camera.setOnClickListener {
            val cameraActivityIntent = Intent(this, CameraActivity::class.java)
            startActivity(cameraActivityIntent)
        }

        // Раз в секунду обновляем время
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post(object : Runnable {
            override fun run() {
                time.text = getCurrentTime()
                mainHandler.postDelayed(this, 1000)
            }
        })
    }

    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    private fun verifyStoragePermissions(activity: Activity) {
        // Check if we have write permission
        val permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    // Получаем время
    fun getCurrentTime(): String? {
        val calendar = Calendar.getInstance()
        val hour = calendar[Calendar.HOUR_OF_DAY]
        val minute = calendar[Calendar.MINUTE]
        val second = calendar[Calendar.SECOND]
        val day = calendar[Calendar.DATE]
        val month = calendar[Calendar.MONTH]
        val year = calendar[Calendar.YEAR]
        return String.format("%02d.%02d.%02d  %02d:%02d:%02d",
                day, month + 1, year, hour, minute, second) // ДД.ММ.ГГГГ ЧЧ:ММ:СС - формат времени
    }
}