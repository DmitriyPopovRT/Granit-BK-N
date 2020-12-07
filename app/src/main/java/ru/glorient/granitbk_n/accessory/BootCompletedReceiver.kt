package ru.glorient.granitbk_n.accessory

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import ru.glorient.granitbk_n.MainActivity
import ru.glorient.granitbk_n.avtoinformer.AvtoInformatorFragment

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // ваш код здесь
            val toast = Toast.makeText(context?.applicationContext,
            "BootCompletedReceiver", Toast.LENGTH_LONG);
            toast.show()
            Log.d(AvtoInformatorFragment.TAG, "BootCompletedReceiver")

            val intent1 = Intent(context, MainActivity::class.java)
            intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context!!.startActivity(intent1)
            Log.d(AvtoInformatorFragment.TAG, "context = $context")
            Log.d(AvtoInformatorFragment.TAG, "intent1 = $intent1")
        }
    }
}