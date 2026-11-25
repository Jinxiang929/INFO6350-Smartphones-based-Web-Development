package com.example.week7speechtools.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object LocationPermissions {

    fun hasAccess(context: Context): Boolean {
        return hasPreciseAccess(context) ||
                isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    fun hasPreciseAccess(context: Context): Boolean {
        return isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }
}