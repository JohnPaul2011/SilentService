package com.jp1828.utils

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        val textView = TextView(this).apply {
            text = "SilentService is running"
            textSize = 24f
        }
        
        val button = Button(this).apply {
            text = "Start Service"
            setOnClickListener {
                val serviceIntent = Intent(this@MainActivity, MainService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
        }
        
        layout.addView(textView)
        layout.addView(button)
        
        setContentView(layout)
    }
}
