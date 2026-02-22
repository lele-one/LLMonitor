package com.lele.llpower.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

object BatteryEngine {
    /**
     * 获取系统设定的电池设计容量 (mAh)
     */
    @android.annotation.SuppressLint("PrivateApi")
    fun getBatteryDesignCapacity(context: Context): Int {
        return try {
            val powerProfileClass = "com.android.internal.os.PowerProfile"
            val mPowerProfile = Class.forName(powerProfileClass)
                .getConstructor(Context::class.java).newInstance(context)
            val capacity = Class.forName(powerProfileClass)
                .getMethod("getBatteryCapacity").invoke(mPowerProfile) as Double
            capacity.toInt()
        } catch (e: Exception) {
            // 兜底方案：尝试读取系统文件
            readSysFile("/sys/class/power_supply/battery/charge_full_design")?.let { (it / 1000).toInt() } ?: 5000
        }
    }

    /**
     * 获取当前剩余容量 (mAh)
     */
    fun getBatteryCurrentCapacity(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val chargeCounter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) // uAh
        
        if (chargeCounter > 0) {
            return chargeCounter / 1000
        }
        
        // 回退：读取内核节点
        val paths = listOf(
            "/sys/class/power_supply/battery/charge_now",
            "/sys/class/power_supply/battery/charge_counter"
        )
        for (path in paths) {
            readSysFile(path)?.let { return (it / 1000).toInt() }
        }
        
        return 0
    }

    /**
     * 获取实时电流 (mA) - 增强异常数据过滤
     */
    fun getCurrentMa(context: Context): Float {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        var current = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (current == 0L) {
            current = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        }

        if (current == 0L) {
            // 尝试多个常见的内核节点
            val paths = listOf(
                "/sys/class/power_supply/battery/current_now",
                "/sys/class/power_supply/main/current_now",
                "/sys/class/power_supply/battery/batt_current"
            )
            for (path in paths) {
                val value = readSysFile(path)
                if (value != null && value != 0L) {
                    current = value
                    break
                }
            }
        }

        // 拦截硬件驱动返回的错误标志位
        if (current == -2147483648L || current == 2147483647L ||
            current == Long.MIN_VALUE || current == Long.MAX_VALUE) {
            return 0f
        }

        return if (Math.abs(current) > 20000) { // 增强判断阈值
            current / 1000f
        } else {
            current.toFloat()
        }
    }

    /**
     * 获取修正后的电流 (考虑反转和双芯)
     */
    fun getAdjustedCurrentMa(context: Context, invert: Boolean, isDoubleCell: Boolean): Float {
        var current = getCurrentMa(context)
        if (invert) current *= -1f
        if (isDoubleCell) current *= 2f
        return current
    }

    /**
     * 获取电压 (V) - 增加联发科等设备的内核节点回退
     */
    fun getVoltageV(context: Context): Float {
        // 1. 优先使用标准 API
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        
        if (voltageMv > 0) {
            return voltageMv / 1000f
        }
        
        // 2. 回退：尝试内核节点 (联发科等设备)
        val paths = listOf(
            "/sys/class/power_supply/battery/voltage_now",       // 标准路径 (μV)
            "/sys/class/power_supply/battery/batt_vol",          // 部分联发科 (mV)
            "/sys/class/power_supply/main/voltage_now",          // 备用路径
            "/sys/class/power_supply/Battery/voltage_now"        // 部分设备
        )
        
        for (path in paths) {
            val value = readSysFile(path)
            if (value != null && value > 0) {
                // voltage_now 通常是 μV，需要转换
                return if (value > 100000) {
                    value / 1_000_000f  // μV -> V
                } else if (value > 1000) {
                    value / 1000f       // mV -> V
                } else {
                    value.toFloat()     // 已经是 V
                }
            }
        }
        
        return 0f
    }

    /**
     * 获取虚拟电压 (估算值)
     * 针对无法读取电压的设备，基于电量容量比例和充电状态估算
     */
    fun getVirtualVoltage(currentCapacity: Int, totalCapacity: Int, isCharging: Boolean): Float {
        // 防止除以零
        if (totalCapacity <= 0) return 3.8f
        
        val ratio = currentCapacity.toFloat() / totalCapacity
        
        // 1. 计算基础电压 (模拟放电曲线)
        // 1.0 (100%) -> 4.45V
        // 0.2 (20%)  -> 3.75V (线性下降)
        // 0.0 (0%)   -> 3.20V (快速下降)
        val baseVoltage = when {
            ratio >= 0.2f -> {
                // 区间 0.2-1.0: 3.75V - 4.45V
                // 归一化比例: (ratio - 0.2) / 0.8
                3.75f + (ratio - 0.2f) * (4.45f - 3.75f) / 0.8f
            }
            else -> {
                // 区间 0.0-0.2: 3.20V - 3.75V
                // 归一化比例: ratio / 0.2
                3.20f + ratio * (3.75f - 3.20f) / 0.2f
            }
        }

        // 2. 计算充电补偿 (模拟内阻压降)
        // 0.0 (0%)   -> +0.5V
        // 0.8 (80%)  -> +0.2V
        // 1.0 (100%) -> +0.0V
        val compensation = if (isCharging) {
            when {
                ratio <= 0.8f -> {
                    // 区间 0-0.8: 0.5V -> 0.2V
                    0.5f - ratio * (0.5f - 0.2f) / 0.8f
                }
                else -> {
                    // 区间 0.8-1.0: 0.2V -> 0.0V
                    0.2f - (ratio - 0.8f) * (0.2f - 0.0f) / 0.2f
                }
            }
        } else {
            0f
        }

        return baseVoltage + compensation
    }

    private fun readSysFile(path: String): Long? {
        return try {
            val file = java.io.File(path)
            if (file.exists()) file.readText().trim().toLong() else null
        } catch (e: Exception) { null }
    }
}