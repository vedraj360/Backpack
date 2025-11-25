package com.vdx.backpack.demo.utils

import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

object AppRestartHelper {
    fun triggerRestart(context: Context) {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(mainIntent)
        exitProcess(0)
    }
}