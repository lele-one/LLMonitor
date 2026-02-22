package com.lele.llpower.utils

import java.text.SimpleDateFormat
import java.util.*

object BatteryUtils {
    fun formatTimestamp(timestamp: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}