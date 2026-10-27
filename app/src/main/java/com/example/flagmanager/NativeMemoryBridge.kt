package com.example.flagmanager // Change this to match your app's package name

object NativeMemoryBridge {
    init {
        System.loadLibrary("tacostrap_native")
    }

    // Native declarations matching your C++ functions
    external fun nativeInit(pid: Int): Boolean
    external fun nativeSetMaxFPS(fpsOffset: Long, maxFps: Double): Boolean
    external fun nativeSetWalkspeed(humanoidAddress: Long, walkspeedOffset: Long, speed: Float): Boolean
}
