package com.example.week7speechtools.model

data class DevicePosition(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val approximate: Boolean
)