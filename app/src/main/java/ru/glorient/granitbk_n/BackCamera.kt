package ru.glorient.granitbk_n

import android.content.Context
import android.hardware.Camera
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.io.IOException

class BackCamera(
    context: Context?,
    private val mCamera: Camera?
) :
    SurfaceView(context), SurfaceHolder.Callback {
    private val mHolder: SurfaceHolder = holder

    override fun surfaceCreated(holder: SurfaceHolder) {
        try {
            mCamera!!.setPreviewDisplay(mHolder)
            mCamera.startPreview()
        } catch (e: IOException) {
            Log.d(TAG, "Error setting camera : " + e.message)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        mCamera?.stopPreview()
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        w: Int,
        h: Int
    ) {
        if (mHolder.surface == null) {
            return
        }

        try {
            mCamera!!.stopPreview()
        } catch (e: java.lang.Exception) {
        }

        try {
            mCamera!!.setPreviewDisplay(mHolder)
            mCamera.startPreview()
        } catch (e: java.lang.Exception) {
            Log.d(TAG, "Error starting camera : " + e.message)
        }
    }

    companion object {
        var TAG = "BackCamera"
    }

    init {
        mHolder.addCallback(this)
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
    }
}