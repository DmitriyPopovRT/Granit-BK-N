package ru.glorient.granitbk_n.accessory

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.DialogFragment
import ru.glorient.granitbk_n.R

// Фрагмент авторизации пользователя
class LoginFragment : DialogFragment(R.layout.fragment_login) {
    lateinit var errorMessage: TextView
    lateinit var enterName: EditText
    lateinit var enterPassword: EditText
    lateinit var enter: Button
    lateinit var container: ConstraintLayout

    private val dialogVerificationListener: DialogVerificationListener?
        get() = parentFragment.let { it as? DialogVerificationListener }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val alertDialog = requireActivity().let {
            val myBuilder = AlertDialog.Builder(it)

            val view = requireActivity()
                .layoutInflater
                .inflate(R.layout.fragment_login, null)
            enterName = view.findViewById(R.id.enterName) as EditText
            enterPassword = view.findViewById(R.id.enterPassword) as EditText
            enter = view.findViewById(R.id.enter) as Button
            errorMessage = view.findViewById(R.id.error_message) as TextView
            container = view.findViewById(R.id.container) as ConstraintLayout

            enterName.setText("логин")
            enterPassword.setText("пароль")

            // Добавляем слушателей на поля ввода имени и пароля
            addListenerEditText(enterName)
            addListenerEditText(enterPassword)

            enter.isEnabled = validate()

            // Ловим событие нажатия на кнопку входа
            enter.setOnClickListener {
                login()
            }

            myBuilder
                .setView(view)
                .create()
        }

        alertDialog.window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

        return alertDialog
    }

    fun showDialog(fragment: DialogFragment) {
        show(fragment.childFragmentManager, null)

        fragmentManager?.executePendingTransactions()

        dialog?.window?.decorView?.systemUiVisibility =
            requireActivity().window.decorView.systemUiVisibility

        // Make the dialogs window focusable again.
        dialog?.window?.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
    }

    private fun verification(): Boolean {
        return enterName.text.toString() == "логин" && enterPassword.text.toString() == "пароль"
    }

    // Осуществляем вход в систему
    private fun login() {
        if (verification()) {
            // Убираем надпись о ошибке
            errorMessage.visibility = View.GONE

            // Выключаем все кнопки и поля
            enableButton(false)

            // Создаем прогрессбар
            val progressBar = createProgressBar()
            // Удаляем прогрессбар через секунду и зажигаем кнопки и поля
            Handler().postDelayed({
                container.removeView(progressBar)
                // Включаем все кнопки и поля
                enableButton(true)
                // Оповещаем пользователя
                Toast.makeText(activity,
                    R.string.enter_toast, Toast.LENGTH_SHORT).show()

                dialogVerificationListener?.successfulVerification()
                dialog?.dismiss()

            }, 0)
        } else {
            // Пользователь неправильно ввел данные
            // Изменяем значения состояния
            // Показываем сообщение
            errorMessage.visibility = View.VISIBLE
        }
    }

    // Проверка соблюдены ли все условия
    private fun validate(): Boolean {
        return enterName.text.toString().isNotBlank() &&
                enterPassword.text.toString().isNotBlank()
    }

    // Включение/выключение кнопок и полей
    private fun enableButton(boolean: Boolean) {
        enter.isEnabled = boolean
        enterName.isEnabled = boolean
        enterPassword.isEnabled = boolean
    }

    // Добавляем слушателя на поля ввода имени и пароля
    private fun addListenerEditText(editText: EditText) {
        // Ловим события ввода пароля или ввода имени
        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Проверяем соблюдены ли все условия, если да, то зажигаем кнопку
                enter.isEnabled = validate()
            }
        })
    }

    // Функция создания прогресс бара программно
    private fun createProgressBar(): ProgressBar {
        // Создаем прогессбар
        val progressBar = ProgressBar(activity)
        // Присваиваем ему id
        progressBar.id = View.generateViewId()
        // Добавляем его в контейнер
        container.addView(progressBar)
        // Настраиваем местоположение(слева от кнопки "Войти")
        val set = ConstraintSet()
        with(set) {
            constrainWidth(progressBar.id, ConstraintSet.WRAP_CONTENT)
            constrainHeight(progressBar.id, ConstraintSet.WRAP_CONTENT)
            connect(
                progressBar.id, ConstraintSet.RIGHT,
                enter.id, ConstraintSet.LEFT, 0
            )
            connect(
                progressBar.id, ConstraintSet.TOP,
                enterPassword.id, ConstraintSet.BOTTOM, 0
            )
            applyTo(container)
        }
        return progressBar
    }

    companion object {
        private const val LOG = "LoginFragment"
    }
}