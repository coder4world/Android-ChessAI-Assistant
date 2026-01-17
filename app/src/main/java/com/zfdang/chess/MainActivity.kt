package com.zfdang.chess

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as SystemSettings
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.zfdang.chess.services.FloatingService
import com.zfdang.chess.views.WebviewActivity

class MainActivity : AppCompatActivity() {

    // 注册 MediaProjection 权限请求回调
    private val requestMediaProjection = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            // 获取权限成功后，将结果发送给 FloatingService
            val intent = Intent(this, FloatingService::class.java).apply {
                putExtra(FloatingService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(FloatingService.EXTRA_DATA, result.data)
            }
            startService(intent)
        }
    }

    // 启动悬浮窗逻辑
    private fun startFloating() {
        if (!SystemSettings.canDrawOverlays(this)) {
            // 申请悬浮窗权限
            val intent = Intent(SystemSettings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivityForResult(intent, 101)
        } else {
            // 已有权限，请求截屏权限
            requestScreenCapturePermission()
        }
    }

    // 请求截屏权限
    private fun requestScreenCapturePermission() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        requestMediaProjection.launch(projectionManager.createScreenCaptureIntent())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 设置系统状态栏边距
        val mainView = findViewById<android.view.View>(R.id.main)
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }

        // 绑定按钮逻辑
        val buttonPlay: Button = findViewById(R.id.button_play)
        val buttonLearn: Button = findViewById(R.id.button_learn)
        val buttonHelp: Button = findViewById(R.id.button_help)
        val buttonAbout: Button = findViewById(R.id.button_about)

        buttonLearn.text = "启动悬浮"

        buttonPlay.setOnClickListener {
            startActivity(Intent(this, GameActivity::class.java))
        }

        buttonLearn.setOnClickListener {
            startFloating()
        }

        buttonHelp.setOnClickListener {
            val intent = Intent(this, WebviewActivity::class.java).apply {
                putExtra("url", "https://fish.zfdang.com/help.html")
            }
            startActivity(intent)
        }

        buttonAbout.setOnClickListener {
            val intent = Intent(this, WebviewActivity::class.java).apply {
                putExtra("url", "https://fish.zfdang.com/")
            }
            startActivity(intent)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101) {
            if (SystemSettings.canDrawOverlays(this)) {
                // 用户授予了悬浮窗权限后，紧接着申请截屏权限
                requestScreenCapturePermission()
            }
        }
    }
}