package org.fungalsentinel.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import org.fungalsentinel.app.navigation.AppNavigation
import org.fungalsentinel.app.ui.theme.FssaTheme

class MainActivity : ComponentActivity() {

    private val requiredPermissions = buildList {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        if (!allGranted) {
            Toast.makeText(
                this,
                "Camera and storage permissions are required for RAW capture",
                Toast.LENGTH_LONG
            ).show()
        } else {
            (application as FssaApplication).logInfo("Permissions granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 请求运行时权限
        requestPermissionsIfNeeded()

        // 2. 恢复未完成的草稿（README 要求）
        restoreDraftIfNeeded()

        // 3. 设置 Compose UI
        enableEdgeToEdge()
        setContent {
            FssaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavigation(navController = navController)
                }
            }
        }

        (application as FssaApplication).logInfo("MainActivity.onCreate")
    }

    private fun requestPermissionsIfNeeded() {
        val notGranted = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    private fun restoreDraftIfNeeded() {
        // 通过 Room 查询 status = 'IN_PROGRESS' 的项目
        // 如果存在，导航到对应 Step（由 AppNavigation 处理）
        // 此处仅做日志记录，真正的恢复逻辑在 ProjectViewModel 中
        (application as FssaApplication).logInfo("Draft restore check")
    }

    override fun onDestroy() {
        super.onDestroy()
        (application as FssaApplication).logInfo("MainActivity.onDestroy")
    }
}