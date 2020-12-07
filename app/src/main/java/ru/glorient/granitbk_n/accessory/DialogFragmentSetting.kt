package ru.glorient.granitbk_n.accessory

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Point
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import org.json.JSONObject
import ru.glorient.granitbk_n.MainActivity
import ru.glorient.granitbk_n.R
import java.io.File
import java.io.FileWriter


// Диалог для чтения и записи настроек из/в json
class DialogFragmentSetting : DialogFragment(), DialogVerificationListener {

    private lateinit var containerLock: LinearLayout
    private lateinit var buttonLock: ImageView
    private lateinit var textViewButtonLock: TextView
    private lateinit var server: EditText
    private lateinit var port: EditText
    private lateinit var transportId: EditText
    private lateinit var ssl: CheckBox
    private lateinit var user: EditText
    private lateinit var password: EditText
    private lateinit var timeout: EditText

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val alertDialog = requireActivity().let {
            val myBuilder = AlertDialog.Builder(it)

            // Парсим настройки при открытии диалога
            MainActivity.settingsParse =
                SettingsParse(requireContext())
            val settingsParse =
                MainActivity.settingsParse

            // Инфлэйтим вьюху
            val view = requireActivity()
                .layoutInflater
                .inflate(R.layout.dialog_layout_setting, null)

//            //Set the dialog to immersive
//            dialog?.getWindow()?.getDecorView()?.setSystemUiVisibility(
//                requireContext().getWindow().getDecorView().getSystemUiVisibility());

//            //Clear the not focusable flag from the window
//            dialog?.getWindow()?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

            server = view.findViewById(R.id.editDialogSettingServer) as EditText
            port = view.findViewById(R.id.editDialogSettingPort) as EditText
            transportId = view.findViewById(R.id.editDialogSettingTransportId) as EditText
            ssl = view.findViewById(R.id.dialog_setting_ssl) as CheckBox
            user = view.findViewById(R.id.editDialogSettingUser) as EditText
            password = view.findViewById(R.id.editDialogSettingPassword) as EditText
            timeout = view.findViewById(R.id.editDialogSettingTimeout) as EditText
            buttonLock = view.findViewById(R.id.buttonLock) as ImageView
            textViewButtonLock = view.findViewById(R.id.textViewButtonLock) as TextView
            containerLock = view.findViewById(R.id.containerLock) as LinearLayout

            // Заполняем поля из json
            server.setText(settingsParse.server)
            port.setText(settingsParse.port.toString())
            transportId.setText(settingsParse.transportId.toString())
            ssl.isChecked = settingsParse.ssl
            user.setText(settingsParse.login)
            password.setText(settingsParse.password)
            timeout.setText(settingsParse.timeout.toString())

            // Обрабатываем нажатие на иконку замка
            containerLock.setOnClickListener { viewLock ->
                // Открываем диалог с авторизацией
                LoginFragment().showDialog(this)
            }

            // Блокируем поля от ввода
            verificationViewVisible(false)

            myBuilder
                .setTitle(requireActivity().resources.getString(R.string.dialog_setting_title))
                .setView(view)
                .setPositiveButton(
                    requireActivity()
                        .resources
                        .getString(R.string.ok)
                ) { _, _ ->
                    // При нажатии ок сохраняем новый файл json в память
                    val filePath = "/storage/emulated/0/Granit BK-N/settings.jsonc"
                    val file = File(filePath)
                    val str = StringBuilder()
                    file.forEachLine {
                        str.append(it.substringBeforeLast("//").trim())
                    }

                    val settings = JSONObject(str.toString())
                    settings.put("server", server.text.toString())
                    settings.put("port", port.text.toString().toInt())
                    settings.put("transport_id", transportId.text.toString().toInt())
                    settings.put("user", user.text.toString())
                    settings.put("password", password.text.toString())
                    settings.put("timeout", timeout.text.toString().toInt())
                    settings.put("ssl", ssl.isChecked)

                    val fileWriter = FileWriter(filePath)
                    fileWriter.write(settings.toString())
                    fileWriter.flush()
                }
                .setNegativeButton(
                    requireActivity()
                        .resources
                        .getString(R.string.cancel)
                ) { _, _ -> }
                .create()
        }

        alertDialog.window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

        return alertDialog
    }

    fun showDialog(activity: AppCompatActivity) {
        show(activity.supportFragmentManager, null)

        fragmentManager?.executePendingTransactions()

        dialog?.window?.decorView?.systemUiVisibility =
            activity.window.decorView.systemUiVisibility

        // Make the dialogs window focusable again.
        dialog?.window?.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
    }

    override fun onResume() {
        super.onResume()
        // прячем панель навигации и строку состояния
        val uiOptions = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        dialog?.window?.decorView?.systemUiVisibility = uiOptions

//        dialog?.getWindow()?.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

        // Задаем размер диалога
        val window = dialog?.window
        val size = Point()
        val display = window?.windowManager?.defaultDisplay
        display?.getSize(size)
        val width = size.x
        window?.setLayout((width * 0.85).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        window?.setGravity(Gravity.CENTER)
    }

    // Функция блокировки/разблокировки полей ввода
    private fun verificationViewVisible(flag: Boolean) {
        server.isEnabled = flag
        port.isEnabled = flag
        transportId.isEnabled = flag
        ssl.isEnabled = flag
        user.isEnabled = flag
        password.isEnabled = flag
        timeout.isEnabled = flag
    }

    // Ловим событие авторизации пользователя
    override fun successfulVerification() {
        verificationViewVisible(true)

        // Меняем картинку и текст
        buttonLock.setImageResource(R.drawable.ic_lock_open)
        textViewButtonLock.setText(R.string.access_open)
        textViewButtonLock.setTextColor(Color.GREEN)
    }
}