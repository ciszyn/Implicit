package com.example.implicit

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val canvas = findViewById<CanvasView>(R.id.canvas)
        val text = findViewById<TextInputEditText>(R.id.textInputEditText)

        text.setOnKeyListener(object : View.OnKeyListener {
            override fun onKey(v: View?, keyCode: Int, event: KeyEvent): Boolean {
                if (event.action === KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                    val thread = Thread {
                        canvas.setGraph(text.text.toString())
                    }
                    thread.start()
                    return true
                }
                return false
            }
        })
    }
}