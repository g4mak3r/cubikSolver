package com.cubecraft.solver

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("CUBECRAFT", if(OpenCVLoader.initLocal()) "OpenCV loaded" else "OpenCV load failed")
        setContent { CubecraftApp() }
    }
}
