package com.lele.llmonitor.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

object BatteryEngine {
    data class BatteryTemperatureReading(
        val celsius: Float,
        val fractionDigits: Int,
        val raw: Int,
        val divisor: Int
    )

    fun getBatteryDesignCapacityWithSources(context: Context): BatteryProbeResult<Int> {
        val probes = mutableListOf<BatterySourceProbe>()
        try {
            val powerProfileClass = "com.android.internal.os.PowerProfile"
            val mPowerProfile = Class.forName(powerProfileClass)
                .getConstructor(Context::class.java).newInstance(context)
            val capacity = Class.forName(powerProfileClass)
                .getMethod("getBatteryCapacity").invoke(mPowerProfile) as Double
            val value = capacity.toInt()
            probes += BatterySourceProbe("PowerProfile.getBatteryCapacity()", "${value}mAh", true)
            return BatteryProbeResult(value, probes)
        } catch (e: Exception) {
            probes += BatterySourceProbe("PowerProfile.getBatteryCapacity()", "unavailable", false)
        }

        val sys = readSysFile("/sys/class/power_supply/battery/charge_full_design")
        if (sys != null && sys > 0L) {
            val value = (sys / 1000L).toInt()
            probes += BatterySourceProbe("/sys/.../charge_full_design", "${value}mAh", true)
            return BatteryProbeResult(value, probes)
        }
        probes += BatterySourceProbe("/sys/.../charge_full_design", "unavailable", false)

        probes += BatterySourceProbe("fallback(default)", "5000mAh", true)
        return BatteryProbeResult(5000, probes)
    }

    fun getBatteryCurrentCapacityWithSources(context: Context): BatteryProbeResult<Int> {
        val probes = mutableListOf<BatterySourceProbe>()
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val chargeCounter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        if (chargeCounter > 0) {
            val value = chargeCounter / 1000
            probes += BatterySourceProbe("BatteryManager.CHARGE_COUNTER", "${value}mAh", true)
            return BatteryProbeResult(value, probes)
        }
        probes += BatterySourceProbe("BatteryManager.CHARGE_COUNTER", "$chargeCounter(uAh)", false)

        val paths = listOf(
            "/sys/class/power_supply/battery/charge_now",
            "/sys/class/power_supply/battery/charge_counter"
        )
        for (path in paths) {
            val raw = readSysFile(path)
            if (raw != null && raw > 0L) {
                val value = (raw / 1000L).toInt()
                probes += BatterySourceProbe(path, "${value}mAh", true)
                return BatteryProbeResult(value, probes)
            }
            probes += BatterySourceProbe(path, "unavailable", false)
        }

        probes += BatterySourceProbe("fallback", "0mAh", true)
        return BatteryProbeResult(0, probes)
    }

    fun getCurrentMaWithSources(context: Context): BatteryProbeResult<Float> {
        val probes = mutableListOf<BatterySourceProbe>()
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        var currentSource = "fallback"
        var current = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (current != 0L) {
            currentSource = "BatteryManager.CURRENT_NOW"
            probes += BatterySourceProbe(currentSource, "${current}mA", true)
        } else {
            probes += BatterySourceProbe("BatteryManager.CURRENT_NOW", "0", false)
            current = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            if (current != 0L) {
                currentSource = "BatteryManager.CURRENT_AVERAGE"
                probes += BatterySourceProbe(currentSource, "${current}mA", true)
            } else {
                probes += BatterySourceProbe("BatteryManager.CURRENT_AVERAGE", "0", false)
            }
        }

        if (current == 0L) {
            val paths = listOf(
                "/sys/class/power_supply/battery/current_now",
                "/sys/class/power_supply/main/current_now",
                "/sys/class/power_supply/battery/batt_current"
            )
            for (path in paths) {
                val value = readSysFile(path)
                if (value != null && value != 0L) {
                    current = value
                    currentSource = path
                    probes += BatterySourceProbe(path, "${value}mA", true)
                    break
                }
                probes += BatterySourceProbe(path, "unavailable", false)
            }
        }

        if (current == -2147483648L || current == 2147483647L ||
            current == Long.MIN_VALUE || current == Long.MAX_VALUE
        ) {
            probes += BatterySourceProbe("invalid-marker-filter", "${current} => 0mA", true)
            return BatteryProbeResult(0f, probes)
        }

        val finalValue = normalizeCurrentToMa(rawCurrent = current)
        probes += BatterySourceProbe(
            "unit-normalize",
            "$currentSource/mA => ${String.format("%.1f", finalValue)}mA",
            true
        )
        return BatteryProbeResult(finalValue, probes)
    }

    private fun normalizeCurrentToMa(rawCurrent: Long): Float {
        // 固定策略：所有来源都按 mA 解释，不进行单位换算。
        return rawCurrent.toFloat()
    }

    fun getVoltageVWithSources(context: Context): BatteryProbeResult<Float> {
        val probes = mutableListOf<BatterySourceProbe>()
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        if (voltageMv > 0) {
            val value = voltageMv / 1000f
            probes += BatterySourceProbe("Intent.EXTRA_VOLTAGE", "${voltageMv}mV => ${String.format("%.3f", value)}V", true)
            return BatteryProbeResult(value, probes)
        }
        probes += BatterySourceProbe("Intent.EXTRA_VOLTAGE", "${voltageMv}mV", false)

        val paths = listOf(
            "/sys/class/power_supply/battery/voltage_now",
            "/sys/class/power_supply/battery/batt_vol",
            "/sys/class/power_supply/main/voltage_now",
            "/sys/class/power_supply/Battery/voltage_now"
        )
        for (path in paths) {
            val raw = readSysFile(path)
            if (raw != null && raw > 0L) {
                val value = if (raw > 100000) {
                    raw / 1_000_000f
                } else if (raw > 1000) {
                    raw / 1000f
                } else {
                    raw.toFloat()
                }
                probes += BatterySourceProbe(path, "${raw} => ${String.format("%.3f", value)}V", true)
                return BatteryProbeResult(value, probes)
            }
            probes += BatterySourceProbe(path, "unavailable", false)
        }

        probes += BatterySourceProbe("fallback", "0V", true)
        return BatteryProbeResult(0f, probes)
    }

    fun applyCurrentAdjustments(rawCurrentMa: Float, invert: Boolean, isDoubleCell: Boolean): Float {
        var adjusted = rawCurrentMa
        if (invert) adjusted *= -1f
        if (isDoubleCell) adjusted *= 2f
        return adjusted
    }

    /**
     * 解析 BatteryManager.EXTRA_TEMPERATURE 原始值（Int），并给出自适应精度。
     * - 常规按 0.1℃（div=10）解析；
     * - 极大原始值按 0.001℃（div=1000）解析；
     * - 精度位数由最低有效位动态推断，但最多保留到小数点后 1 位。
     */
    fun parseBatteryTemperature(raw: Int): BatteryTemperatureReading {
        val absRaw = abs(raw)
        val divisor = if (absRaw > 2000) 1000 else 10
        val inferredDigits = when (divisor) {
            1000 -> when {
                raw % 10 != 0 -> 3
                raw % 100 != 0 -> 2
                raw % 1000 != 0 -> 1
                else -> 0
            }
            else -> if (raw % 10 == 0) 0 else 1
        }
        val fractionDigits = inferredDigits.coerceIn(0, 1)
        val celsius = roundToFractionDigits(raw.toFloat() / divisor.toFloat(), fractionDigits)
        return BatteryTemperatureReading(
            celsius = celsius,
            fractionDigits = fractionDigits,
            raw = raw,
            divisor = divisor
        )
    }

    fun formatTemperatureC(celsius: Float, fractionDigits: Int): String {
        val digits = fractionDigits.coerceIn(0, 1)
        return String.format(Locale.getDefault(), "%.${digits}f", celsius)
    }

    private fun roundToFractionDigits(value: Float, digits: Int): Float {
        val d = digits.coerceIn(0, 4)
        val factor = 10f.pow(d)
        return kotlin.math.round(value * factor) / factor
    }

    /**
     * 获取系统设定的电池设计容量 (mAh)
     */
    @android.annotation.SuppressLint("PrivateApi")
    fun getBatteryDesignCapacity(context: Context): Int {
        return getBatteryDesignCapacityWithSources(context).value
    }

    /**
     * 获取当前剩余容量 (mAh)
     */
    fun getBatteryCurrentCapacity(context: Context): Int {
        return getBatteryCurrentCapacityWithSources(context).value
    }

    /**
     * 获取实时电流 (mA) - 增强异常数据过滤
     */
    fun getCurrentMa(context: Context): Float {
        return getCurrentMaWithSources(context).value
    }

    /**
     * 获取修正后的电流 (考虑反转和双芯)
     */
    fun getAdjustedCurrentMa(context: Context, invert: Boolean, isDoubleCell: Boolean): Float {
        return applyCurrentAdjustments(
            rawCurrentMa = getCurrentMa(context),
            invert = invert,
            isDoubleCell = isDoubleCell
        )
    }

    /**
     * 整千电流异常过滤：
     * 在外接电源状态下（AC/USB/无线等），当电量 > 98% 时，
     * 若电流读数为“整千 mA”（如 ±1000、±2000、±10000），则钳制为 0mA。
     */
    fun sanitizeCurrentReading(rawCurrentMa: Float, level: Int, status: Int, plugged: Int): Float {
        val onExternalPower = plugged != 0
        val nearFull = level > 98

        val nearestInt = rawCurrentMa.roundToInt()
        val isInteger = abs(rawCurrentMa - nearestInt.toFloat()) < 0.001f
        val isWholeThousand = isInteger && abs(nearestInt) >= 1000 && abs(nearestInt) % 1000 == 0

        if (onExternalPower && nearFull && isWholeThousand) {
            return 0f
        }
        return rawCurrentMa
    }

    /**
     * 获取电压 (V) - 增加联发科等设备的内核节点回退
     */
    fun getVoltageV(context: Context): Float {
        return getVoltageVWithSources(context).value
    }

    /**
     * 获取虚拟电压 (估算值)
     * 针对无法读取电压的设备，基于电量容量比例和充电状态估算
     */
    fun getVirtualVoltage(currentCapacity: Int, totalCapacity: Int, isCharging: Boolean): Float {
        // 防止除以零
        if (totalCapacity <= 0) return 3.8f
        
        val ratio = (currentCapacity.toFloat() / totalCapacity).coerceIn(0f, 1f)
        
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
