package ru.glorient.granitbk_n.accessory

import android.os.Handler
import android.util.Log
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import ru.glorient.granitbk_n.R
import ru.glorient.granitbk_n.avtoinformer.AvtoInformatorFragment
import ru.glorient.services.ServiceManager

class Accessory {
    fun buttonEnabled() {
        Handler().postDelayed({


        }, 1000)
    }

    fun screenBlock(flag: Boolean, window: Window) {
        Log.d(AvtoInformatorFragment.TAG,"screenBlock $flag")
        if (flag)
            window.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            )
        else
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }
}