package com.lele.llpower.ui.widget
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import com.lele.llpower.ui.MainActivity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.LinearProgressIndicator
import android.content.res.Configuration
import java.util.Locale
import kotlin.math.abs

object BatteryWidgetKeys {
    val POWER = floatPreferencesKey("power")
    val CURRENT = floatPreferencesKey("current")
    val CAPACITY = intPreferencesKey("capacity")
    val TOTAL_CAPACITY = intPreferencesKey("total_capacity")
    val TEMP = floatPreferencesKey("temp")
    val UPDATE_TIME = stringPreferencesKey("update_time")
}

class BatteryWidget : GlanceAppWidget() {

    companion object {
        private val SMALL_SQUARE = DpSize(100.dp, 100.dp) // 2x2
        private val WIDE_STRIP = DpSize(250.dp, 70.dp)    // 4x1
        private val LARGE_RECT = DpSize(250.dp, 160.dp)   // 4x2
    }

    override val sizeMode = SizeMode.Exact


    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                WidgetContent(size)
            }
        }
    }

    @Composable
    fun WidgetContent(size: DpSize) {
        val prefs = currentState<Preferences>()

        // 1. 获取原始功率
        val rawPower = prefs[BatteryWidgetKeys.POWER] ?: 0f

        // 2. 处理 -0.0 问题：如果绝对值小于显示精度(0.05)，则强制归零
        // 公式：|P| < 0.05 => P = 0.0
        val power = if (abs(rawPower) < 0.05f) 0.0f else rawPower

        val current = prefs[BatteryWidgetKeys.CURRENT] ?: 0f
        val capacity = prefs[BatteryWidgetKeys.CAPACITY] ?: 0
        val totalCapacity = prefs[BatteryWidgetKeys.TOTAL_CAPACITY] ?: 5000
        val temp = prefs[BatteryWidgetKeys.TEMP] ?: 0f

        val progress = if (totalCapacity > 0) capacity.toFloat() / totalCapacity else 0f
        val percentage = (progress * 100).toInt()
        // 统一背景 - 使用 SquircleBitmapHelper 实现真正的 G2 连续圆角
        val context = LocalContext.current
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val backgroundColor = if (isDark) {
            android.graphics.Color.parseColor("#1C1B1F") // 适配 Material 3 默认 Surface (Dark)
        } else {
            android.graphics.Color.parseColor("#FFFBFE") // 适配 Material 3 默认 Surface (Light)
        }
        
        val squircleBitmap = SquircleBitmapHelper.createSquircleBitmap(
            context = context,
            widthDp = size.width,
            heightDp = size.height,
            radiusDp = 16.dp, // 统一为 16dp，与应用内标准卡片保持绝对一致
            color = backgroundColor
        )

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(androidx.glance.ImageProvider(squircleBitmap))
                .clickable(actionStartActivity<MainActivity>())
                // 4x1 横条时使用极小边距，防止垂直空间不足
                .padding(if (size.height < 100.dp) 10.dp else 16.dp)
        ) {
            if (size.width < 180.dp) {
                // [2x2] 小方块 (垂直布局)
                LayoutSmallVertical(power, temp, percentage, progress, capacity, totalCapacity) // <--- 记得补全参数
            } else {
                // [4x1] 横条 (压缩版垂直布局) - 结构与大卡片一致，但更紧凑
                LayoutWideVerticalCompressed(power, temp, percentage, progress, capacity, totalCapacity)
            }
        }
    }

    // ==========================================
    // 1. 小方块 (2x2) - 垂直排列
    // ==========================================
    @Composable
    fun LayoutSmallVertical(
        power: Float,
        temp: Float,
        percentage: Int,
        progress: Float,
        capacity: Int,       // 新增参数
        totalCapacity: Int   // 新增参数
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // 上半部分：左对齐的 Column，包含 功率 和 温度
            Column(horizontalAlignment = Alignment.Start) {
                // 1. 功率行 (锁死不换行)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(Locale.US, "%.1f", power),
                        style = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary)
                    )
                    Spacer(GlanceModifier.width(2.dp))
                    Text(
                        text = "W",
                        style = TextStyle(fontSize = 18.sp, color = GlanceTheme.colors.onSurfaceVariant),
                        modifier = GlanceModifier.padding(bottom = 4.dp)
                    )
                }

                // 2. 温度 (移动到功率下方)
                Text(
                    text = "${temp}°C", // 这里空间够，可以加回 C
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.secondary),
                    modifier = GlanceModifier.padding(top = 2.dp)
                )
            }

            Spacer(GlanceModifier.defaultWeight())

            // 中：进度条
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surfaceVariant
            )

            Spacer(GlanceModifier.height(8.dp))

            // 下：百分比 + 容量
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 左下：百分比
                Text(
                    text = "$percentage%",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface)
                )

                Spacer(GlanceModifier.defaultWeight())

                // 右下：容量 (原温度位置)
                // 注意：2x2 宽度有限，字号设为 10.sp 且不显示单位，防止换行
                Text(
                    text = "$capacity/$totalCapacity",
                    style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.outline)
                )
            }
        }
    }
    // ==========================================
    // 2. 横条 (4x1) - 压缩版垂直排列 (与大卡片设计一致)
    // ==========================================
    @Composable
    fun LayoutWideVerticalCompressed(
        power: Float, temp: Float, percentage: Int,
        progress: Float, capacity: Int, totalCapacity: Int
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically // 整体垂直居中
        ) {
            // 上层：左边功率，右边温度 (去掉了电流以节省空间)
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                // 功率与单位锁死一行
                Text(
                    text = String.format(Locale.US, "%.1f", power),
                    style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary)
                )
                Spacer(GlanceModifier.width(2.dp))
                Text(
                    text = "W",
                    style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    modifier = GlanceModifier.padding(bottom = 5.dp)
                )

                Spacer(GlanceModifier.defaultWeight())

                // 温度
                Text(
                    text = "${temp}°C",
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.secondary),
                    modifier = GlanceModifier.padding(bottom = 4.dp)
                )
            }

            // 极小的间距
            Spacer(GlanceModifier.height(3.dp))

            // 中层：进度条
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surfaceVariant
            )

            // 极小的间距
            Spacer(GlanceModifier.height(4.dp))

            // 下层：左边百分比，右边容量
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = " $percentage%",
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface)
                )

                Spacer(GlanceModifier.defaultWeight())

                Text(
                    text = "$capacity / $totalCapacity",
                    style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant)
                )
            }
        }
    }

}

class BatteryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BatteryWidget()
}