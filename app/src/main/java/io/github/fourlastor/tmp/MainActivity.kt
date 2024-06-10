package io.github.fourlastor.tmp

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.fourlastor.tmp.ui.theme.TMPTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {

    private val cameraPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                setView()
            } else {
                Log.d("xxx", "Permission denied")
            }

        }

    private val viewModel: VM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) -> {
                setView()
            }
            else -> {
                cameraPermissionRequest.launch(android.Manifest.permission.CAMERA)
            }
        }
        enableEdgeToEdge()
    }

    private fun setView() {
        setContent {
            TMPTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val isShooting by viewModel.isShooting.collectAsState()
                    CameraPreviewScreen(
                        onImageCaptureStop = { viewModel.stopCapturing() },
                        onImageCaptureStart = { imageCapture, interval -> viewModel.startCapturing(this, imageCapture, interval) },
                        modifier = Modifier.padding(innerPadding),
                        isShooting = isShooting,
                    )
                }
            }
        }
    }


    class VM: ViewModel() {

        private var job: Job? = null
        private val shootingState = MutableStateFlow(false)
        val isShooting = shootingState.asStateFlow()

        private fun tickerFlow(period: Duration, initialDelay: Duration = Duration.ZERO) = flow {
            delay(initialDelay)
            while (true) {
                emit(Unit)
                delay(period)
            }
        }

        fun startCapturing(context: Context, imageCapture: ImageCapture, interval: Int) {
            if (isShooting.value) return
            shootingState.update { true }
            val tickerChannel = tickerFlow(period = interval.seconds)
            job = viewModelScope.launch {
                tickerChannel.collect {
                    captureImage(context, imageCapture)
                }
            }
        }

        fun stopCapturing() {
            job?.cancel()
            shootingState.update { false }
        }

        private fun captureImage(context: Context, imageCapture: ImageCapture) {
            val name = "CameraxImage_${LocalDateTime.now()}.jpeg"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
                }
            }
            val outputOptions = ImageCapture.OutputFileOptions
                .Builder(
                    context.contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                .build()
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        Log.d("xxx", "Successs")
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.d("xxx", "Failed $exception")
                    }

                })
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) = Column {
    var name by remember { mutableStateOf(name) }
    TextField(value = name, onValueChange = { name = it})
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TMPTheme {
        Greeting("Android")
    }
}
