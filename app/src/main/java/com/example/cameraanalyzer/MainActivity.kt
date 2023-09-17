package com.example.cameraanalyzer

import android.Manifest
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast

import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette

import com.example.cameraanalyzer.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var colorTextPairs: List<Pair<View, TextView>>

    private var REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
            }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        cameraExecutor = Executors.newSingleThreadExecutor()
        colorTextPairs = listOf(
            Pair(viewBinding.color1, viewBinding.textColor1),
            Pair(viewBinding.color2, viewBinding.textColor2),
            Pair(viewBinding.color3, viewBinding.textColor3),
            Pair(viewBinding.color4, viewBinding.textColor4),
            Pair(viewBinding.color5, viewBinding.textColor5)
        )

        if (allPermissionsGranted()) {
            lifecycleScope.launch {
                startCamera()
            }
        } else {
            requestPermissions()
        }
    }

    //Interface implementation of the image analyzer for the camera executor
    inner class ImageAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(image: ImageProxy) {
            val bitmap = image.toBitmap()
            val commonColors = findMostCommonColors(bitmap)
            updateViewsColorAndText(commonColors)
            image.close()
        }
    }

    //Extract colors from bit map and return 5 most common colors
    fun findMostCommonColors(bitmap: Bitmap): List<Triple<Int,Int,Int>> {
        val colorCountMap = mutableMapOf<Triple<Int,Int,Int>, Int>()

        // Iterate through each pixel in the Bitmap
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(x, y)

                // Extract RGB components
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF

                val color = Triple(red, green, blue)
                colorCountMap[color] = colorCountMap.getOrDefault(color, 0) + 1
            }
        }

        // Sort the colors by frequency in descending order
        val sortedColors = colorCountMap.entries.sortedByDescending { it.value }

        // Take the top numColors colors
        val topColors = sortedColors.take(5)
        return topColors.map { it.key }
    }


    private suspend fun startCamera() {
        val cameraProvider = ProcessCameraProvider.getInstance(this).await()
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        val analyzer = ImageAnalyzer()
        val imageAnalysis = ImageAnalysis.Builder()
            //Image format will be in RGB
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalysis.setAnalyzer(cameraExecutor, analyzer)


        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview,imageAnalysis)
        } catch (e: Exception){
            Log.e(TAG, "UseCase binding failed", e)
        }
    }

    private fun updateViewsColorAndText(colorsList : List<Triple<Int,Int,Int>>) {
        for (i in colorTextPairs.indices) {
            val (view, textView) = colorTextPairs[i]
            val (red, green, blue) = colorsList[i]
            println(i)
            // Generate the color from the RGB values
            val bgColor = Color.rgb(red, green, blue)

            // Set the background color of the View
            view.setBackgroundColor(bgColor)
            var place = i +1
            // Set the text of the TextView
            textView.text = "$red,$green,$blue\n $place"
        }
    }
// Permissions
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && it.value == false)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                lifecycleScope.launch {
                    startCamera()
                }
            }
        }

}