package com.lele.llmonitor.i18n

import com.lele.llmonitor.data.AppLanguageOption
import com.lele.llmonitor.data.resolveCurrentAppLanguageOption
import com.lele.llmonitor.data.resolveSupportedSystemLanguageOption

private enum class AppTextLocale {
    ZH_CN,
    ZH_HK,
    ZH_TW,
    EN
}

private val zhHantMap: Map<String, String> = mapOf(
    "开源许可" to "開源授權",
    "开源许可暂不可用" to "開源授權暫不可用",
    "正在读取当前版本附带的许可材料…" to "正在讀取當前版本附帶的授權材料…",
    "适用组件" to "適用組件",
    "许可标识与链接" to "授權標識與連結",
    "许可证" to "授權條款",
    "未声明" to "未聲明",
    "链接" to "連結",
    "返回" to "返回",
    "许可详情" to "授權詳情",
    "未找到对应的许可信息。" to "未找到對應的授權資訊。",
    "许可证全文" to "授權條款全文",
    "NOTICE 声明" to "NOTICE 聲明",
    "许可材料" to "授權材料",
    "暂无可展示的许可文本。" to "暫無可展示的授權文本。",
    "裁切壁纸" to "裁切桌布",
    "外观设置" to "外觀設定",
    "场景设置" to "場景設定",
    "硬件修正" to "硬體修正",
    "系统与诊断" to "系統與診斷",
    "数据管理" to "資料管理",
    "关于" to "關於",
    "设置" to "設定",
    "清除记录" to "清除紀錄",
    "确定要清空所有充电历史数据吗？\n此操作不可撤销，图表将被重置。" to "確定要清空所有充電歷史資料嗎？\n此操作不可復原，圖表將被重設。",
    "删除" to "刪除",
    "取消" to "取消",
    "确认切换显示模式" to "確認切換顯示模式",
    "切换显示模式后，应用将尝试自动重启以刷新启动图标；若重启失败，请手动重新打开应用。是否继续？" to "切換顯示模式後，應用程式將嘗試自動重新啟動以刷新啟動圖示；若重新啟動失敗，請手動重新開啟應用程式。是否繼續？",
    "确认并重启" to "確認並重新啟動",
    "确认切换启动图标" to "確認切換啟動圖示",
    "切换启动图标样式需要刷新桌面入口，应用将尝试自动重启；若重启失败，请手动重新打开应用。是否继续？" to "切換啟動圖示樣式需要刷新桌面入口，應用程式將嘗試自動重新啟動；若重新啟動失敗，請手動重新開啟應用程式。是否繼續？",
    "确认切换主题配色" to "確認切換主題配色",
    "切换主题配色会同步更新应用图标，应用将尝试自动重启；若重启失败，请手动重新打开应用。是否继续？" to "切換主題配色會同步更新應用程式圖示，應用程式將嘗試自動重新啟動；若重新啟動失敗，請手動重新開啟應用程式。是否繼續？",
    "确认切换语言" to "確認切換語言",
    "切换语言后，应用将尝试自动重启以应用新的界面语言；若重启失败，请手动重新打开应用。是否继续？" to "切換語言後，應用程式將嘗試自動重新啟動以套用新的介面語言；若重新啟動失敗，請手動重新開啟應用程式。是否繼續？",
    "关于 LLMonitor" to "關於 LLMonitor",
    "充电/未充电通知与刷新频率" to "充電／未充電通知與更新頻率",
    "充电时" to "充電時",
    "未充电时" to "未充電時",
    "通知" to "通知",
    "在通知栏显示实时功率信息" to "在通知欄顯示即時功率資訊",
    "实时活动" to "即時活動",
    "显示灵动岛风格实况通知" to "顯示靈動島風格的即時通知",
    "显示本次关闭按钮" to "顯示本次關閉按鈕",
    "在实时活动中显示“本次关闭”按钮" to "在即時活動中顯示「本次關閉」按鈕",
    "应用界面更新率" to "應用程式介面更新率",
    "通知/组件更新率" to "通知／組件更新率",
    "熄屏下通知/组件更新率" to "熄屏時通知／組件更新率",
    "保留通知栏常驻显示" to "保留通知欄常駐顯示",
    "兼容不同设备的电池读数" to "相容不同裝置的電池讀數",
    "反转电流正负" to "反轉電流正負",
    "如果充电时电流显示为负，请开启此项" to "如果充電時電流顯示為負，請開啟此項",
    "双芯电池修正" to "雙芯電池修正",
    "如果使用双芯电池，请开启此项" to "如果使用雙芯電池，請開啟此項",
    "虚拟电压" to "虛擬電壓",
    "若设备无法读取电压，尝试使用估算电压" to "若裝置無法讀取電壓，請嘗試使用估算電壓",
    "后台保活与调试选项" to "背景保活與偵錯選項",
    "禁用电池优化" to "停用電池最佳化",
    "电池优化已禁用" to "電池最佳化已停用",
    "点击禁用，确保后台实时更新不中断" to "點擊停用，確保背景即時更新不中斷",
    "后台更新不受限制" to "背景更新不受限制",
    "电池优化" to "電池最佳化",
    "前往设置" to "前往設定",
    "调试模式" to "偵錯模式",
    "显示各指标可用数据来源（仅用于诊断）" to "顯示各指標可用資料來源（僅用於診斷）",
    "历史记录维护" to "歷史紀錄維護",
    "清除历史数据" to "清除歷史資料",
    "删除所有已存储的充电功率记录" to "刪除所有已儲存的充電功率紀錄",
    "清除" to "清除",
    "跟随系统" to "跟隨系統",
    "浅色模式" to "淺色模式",
    "深色模式" to "深色模式",
    "简体中文（中国）" to "簡體中文（中國）",
    "繁體中文（中國香港）" to "繁體中文（中國香港）",
    "繁體中文（中國台灣）" to "繁體中文（中國台灣）",
    "确认删除历史壁纸" to "確認刪除歷史桌布",
    "删除后无法恢复，这张历史壁纸将从列表中移除。" to "刪除後無法復原，這張歷史桌布將從清單中移除。",
    "确认删除" to "確認刪除",
    "显示模式" to "顯示模式",
    "启动图标样式" to "啟動圖示樣式",
    "浅色图标" to "淺色圖示",
    "深色图标" to "深色圖示",
    "语言" to "語言",
    "应用语言" to "應用程式語言",
    "主题配色" to "主題配色",
    "动态多彩" to "動態多彩",
    "沧蓝" to "滄藍",
    "松青" to "松青",
    "曛橙" to "曛橙",
    "樱霭" to "櫻靄",
    "霁紫" to "霽紫",
    "主页卡片透明度" to "主頁卡片透明度",
    "主界面壁纸" to "主介面桌布",
    "添加壁纸" to "新增桌布",
    "透明度" to "透明度",
    "模糊度" to "模糊度",
    "删除历史壁纸" to "刪除歷史桌布",
    "未覆盖语言将自动使用英语" to "未涵蓋語言將自動使用英語",
    "风在耳边" to "風在耳邊",
    "基本信息" to "基本資訊",
    "应用名称" to "應用程式名稱",
    "作者" to "作者",
    "版本" to "版本",
    "开源许可信息加载失败" to "開源授權資訊載入失敗",
    "LLMonitor 当前版本使用的第三方组件及其许可信息" to "LLMonitor 目前版本使用的第三方組件及其授權資訊",
    "保存失败，请重试" to "儲存失敗，請重試",
    "壁纸导入失败，请重试" to "桌布匯入失敗，請重試",
    "保存" to "儲存",
    "角度" to "角度",
    "重置" to "重設",
    "90度" to "90度",
    "图片加载失败" to "圖片載入失敗",
    "返回重选" to "返回重選",
    "正在读取 SoC 指标…" to "正在讀取 SoC 指標…",
    "SoC 采集不可用" to "SoC 採集不可用",
    "CPU 占用" to "CPU 佔用",
    "内存占用" to "記憶體使用",
    "受限" to "受限",
    "SoC 温度" to "SoC 溫度",
    "在线CPU核心" to "在線 CPU 核心",
    "系统1min负载" to "系統 1min 負載",
    "CPU 型号" to "CPU 型號",
    "当前设备未暴露可用的 CPU 频率节点，CPU 频率信息不可用。" to "目前裝置未暴露可用的 CPU 頻率節點，CPU 頻率資訊不可用。",
    "SoC 相关 Thermal Zones" to "SoC 相關 Thermal Zones",
    "未发现可读且与 SoC 相关的 thermal_zone 节点。" to "未發現可讀且與 SoC 相關的 thermal_zone 節點。",
    "未分簇" to "未分簇",
    "占比" to "佔比",
    "CPU10 离线" to "CPU10 離線",
    "采集失败" to "採集失敗",
    "实时通知受限" to "即時通知受限",
    "为了在通知栏显示实时充电功率，需要授予通知权限。" to "為了在通知欄顯示即時充電功率，需要授予通知權限。",
    "允许通知" to "允許通知",
    "不再提醒" to "不再提醒",
    "后台保活受限" to "背景保活受限",
    "为了保证桌面小组件实时刷新，请将本应用加入电池优化白名单。" to "為了確保桌面小組件即時更新，請將本應用程式加入電池最佳化白名單。",
    "立即开启" to "立即開啟",
    "检测到电压读数异常" to "偵測到電壓讀數異常",
    "设备似乎无法读取实时电压。建议开启“虚拟电压”功能以获得估算数据。" to "裝置似乎無法讀取即時電壓。建議開啟「虛擬電壓」功能以取得估算資料。",
    "开启虚拟电压" to "開啟虛擬電壓",
    "瞬时功率" to "瞬時功率",
    "电池温度" to "電池溫度",
    "电池电压" to "電池電壓",
    "电池电流" to "電池電流",
    "供电状态" to "供電狀態",
    "电池状态" to "電池狀態",
    "当前剩余电量 / 总容量" to "目前剩餘電量／總容量",
    "系统剩余容量 / 总容量" to "系統剩餘容量／總容量",
    "容量计算说明" to "容量計算說明",
    "以下内容用于解释本卡片容量数据来源：" to "以下內容用於說明本卡片容量資料來源：",
    "此处容量与百分比基于系统提供的电池容量数据计算，并非系统状态栏百分比。" to
        "此處容量與百分比是根據系統提供的電池容量資料計算，並非系統狀態列百分比。",
    "部分厂商存在锁容策略，可能出现系统显示已充满，但实际电池容量尚未达到满值的情况。" to
        "部分廠商可能有鎖容策略，可能出現系統顯示已充滿，但實際電池容量尚未達到滿值的情況。",
    "此处容量与百分比基于系统提供的电池容量数据计算，并非系统状态栏百分比。部分厂商存在锁容策略，可能出现系统显示已充满，但实际电池容量尚未达到满值的情况。" to
        "此處容量與百分比是根據系統提供的電池容量資料計算，並非系統狀態列百分比。部分廠商可能有鎖容策略，可能出現系統顯示已充滿，但實際電池容量尚未達到滿值的情況。",
    "1. 此处容量与百分比基于系统提供的电池容量数据计算，并非系统状态栏百分比。\n2. 部分厂商存在锁容策略，可能出现系统显示已充满，但实际电池容量尚未达到满值的情况。" to
        "1. 此處容量與百分比是根據系統提供的電池容量資料計算，並非系統狀態列百分比。\n2. 部分廠商可能有鎖容策略，可能出現系統顯示已充滿，但實際電池容量尚未達到滿值的情況。",
    "知道了" to "知道了",
    "未知" to "未知",
    "检测中" to "偵測中",
    "电源适配器" to "電源轉接器",
    "电脑 (USB)" to "電腦（USB）",
    "无线充电" to "無線充電",
    "底座供电" to "底座供電",
    "电池供电" to "電池供電",
    "良好" to "良好",
    "过热" to "過熱",
    "损坏" to "損壞",
    "过压" to "過壓",
    "故障" to "故障",
    "过冷" to "過冷",
    "状态未知" to "狀態未知",
    "充电中" to "充電中",
    "放电中" to "放電中",
    "已接电源(未充电)" to "已接電源（未充電）",
    "未充电" to "未充電",
    "已充满" to "已充滿",
    "状态异常" to "狀態異常",
    "教室课表查询" to "教室課表查詢",
    "课表选择" to "課表選擇",
    "跟随最新" to "跟隨最新",
    "实时功率曲线" to "即時功率曲線",
    "实时温度曲线" to "即時溫度曲線",
    "充电时普通通知" to "充電時一般通知",
    "未充电时普通通知" to "未充電時一般通知",
    "本次关闭" to "本次關閉",
    "电池监控后台服务" to "電池監控背景服務",
    "显示实时充电功率（静默通知，不会打扰）" to "顯示即時充電功率（靜默通知，不會打擾）",
    "实时活动 (灵动岛)" to "即時活動（靈動島）",
    "充电时显示灵动岛风格的实况通知" to "充電時顯示靈動島風格的即時通知",
    "后台监测中" to "背景監測中"
)

private val zhHkOverrides: Map<String, String> = mapOf(
    "数据管理" to "數據管理",
    "清除历史数据" to "清除歷史數據",
    "删除所有已存储的充电功率记录" to "刪除所有已儲存的充電功率數據",
    "教室课表查询" to "課室時間表查詢",
    "课表选择" to "時間表選擇",
    "后台监测中" to "背景監控中"
)

private val zhTwOverrides: Map<String, String> = mapOf(
    "适用组件" to "適用元件",
    "在通知栏显示实时功率信息" to "在通知列顯示即時功率資訊",
    "通知/组件更新率" to "通知／元件更新率",
    "保留通知栏常驻显示" to "保留通知列常駐顯示",
    "兼容不同设备的电池读数" to "相容不同裝置的電池讀值",
    "双芯电池修正" to "雙芯電池校正",
    "若设备无法读取电压，尝试使用估算电压" to "若裝置無法讀取電壓，請改用估算電壓",
    "实时活动" to "即時動態",
    "显示灵动岛风格实况通知" to "顯示靈動島風格的即時動態通知",
    "在实时活动中显示“本次关闭”按钮" to "在即時動態中顯示「本次關閉」按鈕",
    "后台保活与调试选项" to "背景常駐與偵錯選項",
    "后台保活受限" to "背景常駐受限",
    "调试模式" to "偵錯模式",
    "显示各指标可用数据来源（仅用于诊断）" to "顯示各指標可用資料來源（僅供診斷）",
    "删除所有已存储的充电功率记录" to "刪除所有已儲存的充電功率資料",
    "主界面壁纸" to "主畫面桌布",
    "应用语言" to "應用程式語言",
    "主页卡片透明度" to "主畫面卡片透明度",
    "应用界面更新率" to "應用程式畫面更新率",
    "应用名称" to "應用程式名稱",
    "保存失败，请重试" to "儲存失敗，請再試一次",
    "壁纸导入失败，请重试" to "桌布匯入失敗，請再試一次",
    "SoC 采集不可用" to "SoC 採集不可用",
    "在线CPU核心" to "線上 CPU 核心",
    "系统1min负载" to "系統 1 分鐘負載",
    "当前设备未暴露可用的 CPU 频率节点，CPU 频率信息不可用。" to "目前裝置未提供可用的 CPU 頻率節點，CPU 頻率資訊不可用。",
    "未发现可读且与 SoC 相关的 thermal_zone 节点。" to "未找到可讀且與 SoC 相關的 thermal_zone 節點。",
    "采集失败" to "採集失敗",
    "为了在通知栏显示实时充电功率，需要授予通知权限。" to "為了在通知列顯示即時充電功率，需要授予通知權限。",
    "实时通知受限" to "即時通知受限",
    "为了保证桌面小组件实时刷新，请将本应用加入电池优化白名单。" to "為了確保主畫面小工具即時更新，請將本 App 加入電池最佳化白名單。",
    "供电状态" to "電源狀態",
    "检测到电压读数异常" to "偵測到電壓讀值異常",
    "设备似乎无法读取实时电压。建议开启“虚拟电压”功能以获得估算数据。" to "裝置似乎無法讀取即時電壓，建議啟用「虛擬電壓」以取得估算資料。",
    "瞬时功率" to "即時功率",
    "已接电源(未充电)" to "已接上電源（尚未充電）",
    "LLMonitor 当前版本使用的第三方组件及其许可信息" to "LLMonitor 目前版本使用的第三方元件及其授權資訊",
    "显示实时充电功率（静默通知，不会打扰）" to "顯示即時充電功率（靜默通知，不會打擾）",
    "实时活动 (灵动岛)" to "即時動態（靈動島）",
    "充电时显示灵动岛风格的实况通知" to "充電時顯示靈動島風格的即時動態通知",
    "后台监测中" to "背景監測中"
)

private val zhHkMap: Map<String, String> = zhHantMap + zhHkOverrides

private val zhTwMap: Map<String, String> = zhHantMap + zhTwOverrides

private val enMap: Map<String, String> = mapOf(
    "开源许可" to "Open Source Licenses",
    "开源许可暂不可用" to "Open source licenses are currently unavailable",
    "正在读取当前版本附带的许可材料…" to "Loading license materials bundled with this version...",
    "适用组件" to "Applicable Components",
    "许可标识与链接" to "License IDs & Links",
    "许可证" to "License",
    "未声明" to "Not declared",
    "链接" to "Link",
    "返回" to "Back",
    "许可详情" to "License Details",
    "未找到对应的许可信息。" to "No matching license information found.",
    "许可证全文" to "Full License Text",
    "NOTICE 声明" to "NOTICE Statement",
    "许可材料" to "License Materials",
    "暂无可展示的许可文本。" to "No license text available.",
    "裁切壁纸" to "Crop Wallpaper",
    "外观设置" to "Appearance",
    "场景设置" to "Scenarios",
    "硬件修正" to "Hardware Adjustments",
    "系统与诊断" to "System & Diagnostics",
    "数据管理" to "Data Management",
    "关于" to "About",
    "设置" to "Settings",
    "清除记录" to "Clear Records",
    "确定要清空所有充电历史数据吗？\n此操作不可撤销，图表将被重置。" to "Clear all charging history data?\nThis action cannot be undone, and charts will be reset.",
    "删除" to "Delete",
    "取消" to "Cancel",
    "确认切换显示模式" to "Confirm Display Mode Change",
    "切换显示模式后，应用将尝试自动重启以刷新启动图标；若重启失败，请手动重新打开应用。是否继续？" to "After switching display mode, the app will try to restart to refresh the launcher icon. If restart fails, reopen the app manually. Continue?",
    "确认并重启" to "Confirm and Restart",
    "确认切换启动图标" to "Confirm Launcher Icon Change",
    "切换启动图标样式需要刷新桌面入口，应用将尝试自动重启；若重启失败，请手动重新打开应用。是否继续？" to "Changing launcher icon style requires refreshing the home screen shortcut. The app will try to restart automatically. If restart fails, reopen the app manually. Continue?",
    "确认切换主题配色" to "Confirm Theme Palette Change",
    "切换主题配色会同步更新应用图标，应用将尝试自动重启；若重启失败，请手动重新打开应用。是否继续？" to "Changing theme palette also updates the app icon. The app will try to restart automatically. If restart fails, reopen the app manually. Continue?",
    "确认切换语言" to "Confirm Language Change",
    "切换语言后，应用将尝试自动重启以应用新的界面语言；若重启失败，请手动重新打开应用。是否继续？" to "After changing language, the app will try to restart to apply the new UI language. If restart fails, reopen the app manually. Continue?",
    "关于 LLMonitor" to "About LLMonitor",
    "充电/未充电通知与刷新频率" to "Notification and refresh rates for charging/non-charging",
    "充电时" to "While charging",
    "未充电时" to "Not charging",
    "通知" to "Notification",
    "在通知栏显示实时功率信息" to "Show real-time power info in notifications",
    "实时活动" to "Live Activity",
    "显示灵动岛风格实况通知" to "Show Dynamic Island-style live notification",
    "显示本次关闭按钮" to "Show Close Once button",
    "在实时活动中显示“本次关闭”按钮" to "Show the \"Close Once\" button in Live Activity",
    "应用界面更新率" to "App UI Update Rate",
    "通知/组件更新率" to "Notification/Widget Update Rate",
    "熄屏下通知/组件更新率" to "Screen-off Notification/Widget Update Rate",
    "保留通知栏常驻显示" to "Keep persistent notification visible",
    "兼容不同设备的电池读数" to "Improve compatibility for battery readings on different devices",
    "反转电流正负" to "Invert Current Sign",
    "如果充电时电流显示为负，请开启此项" to "Enable this if charging current appears negative",
    "双芯电池修正" to "Dual-Cell Battery Correction",
    "如果使用双芯电池，请开启此项" to "Enable this if your device uses a dual-cell battery",
    "虚拟电压" to "Virtual Voltage",
    "若设备无法读取电压，尝试使用估算电压" to "If voltage cannot be read, try estimated voltage",
    "后台保活与调试选项" to "Background keep-alive and debug options",
    "禁用电池优化" to "Disable Battery Optimization",
    "电池优化已禁用" to "Battery Optimization Disabled",
    "点击禁用，确保后台实时更新不中断" to "Tap to disable and keep background real-time updates uninterrupted",
    "后台更新不受限制" to "Background updates are unrestricted",
    "电池优化" to "Battery Optimization",
    "前往设置" to "Open Settings",
    "调试模式" to "Debug Mode",
    "显示各指标可用数据来源（仅用于诊断）" to "Show available data sources for each metric (diagnostics only)",
    "历史记录维护" to "History Maintenance",
    "清除历史数据" to "Clear History",
    "删除所有已存储的充电功率记录" to "Delete all stored charging power records",
    "清除" to "Clear",
    "跟随系统" to "Follow System",
    "浅色模式" to "Light Mode",
    "深色模式" to "Dark Mode",
    "简体中文（中国）" to "Simplified Chinese (China)",
    "繁體中文（中國香港）" to "Traditional Chinese (Hong Kong, China)",
    "繁體中文（中國台灣）" to "Traditional Chinese (Taiwan, China)",
    "确认删除历史壁纸" to "Confirm Deleting Wallpaper History",
    "删除后无法恢复，这张历史壁纸将从列表中移除。" to "This action cannot be undone. The selected wallpaper will be removed from history.",
    "确认删除" to "Confirm Delete",
    "显示模式" to "Display Mode",
    "启动图标样式" to "Launcher Icon Style",
    "浅色图标" to "Light Icon",
    "深色图标" to "Dark Icon",
    "语言" to "Language",
    "应用语言" to "App Language",
    "主题配色" to "Theme Palette",
    "动态多彩" to "Dynamic Spectrum",
    "沧蓝" to "Ocean Blue",
    "松青" to "Forest Green",
    "曛橙" to "Sunset Amber",
    "樱霭" to "Blossom Pink",
    "霁紫" to "Misty Violet",
    "主页卡片透明度" to "Home Card Transparency",
    "主界面壁纸" to "Main Screen Wallpaper",
    "添加壁纸" to "Add Wallpaper",
    "透明度" to "Opacity",
    "模糊度" to "Blur",
    "删除历史壁纸" to "Delete History Wallpaper",
    "未覆盖语言将自动使用英语" to "Unsupported languages will fall back to English",
    "风在耳边" to "Wind by your ear.",
    "基本信息" to "Basic Information",
    "应用名称" to "App Name",
    "作者" to "Author",
    "版本" to "Version",
    "开源许可信息加载失败" to "Failed to load open-source license information",
    "LLMonitor 当前版本使用的第三方组件及其许可信息" to "Third-party components and license information used by this version of LLMonitor",
    "保存失败，请重试" to "Save failed, please try again",
    "壁纸导入失败，请重试" to "Wallpaper import failed, please try again",
    "保存" to "Save",
    "角度" to "Angle",
    "重置" to "Reset",
    "90度" to "90°",
    "图片加载失败" to "Failed to load image",
    "返回重选" to "Back and reselect",
    "正在读取 SoC 指标…" to "Reading SoC metrics...",
    "SoC 采集不可用" to "SoC collection unavailable",
    "CPU 占用" to "CPU Usage",
    "内存占用" to "Memory Usage",
    "受限" to "Limited",
    "SoC 温度" to "SoC Temperature",
    "在线CPU核心" to "Online CPU Cores",
    "系统1min负载" to "System 1-min Load",
    "CPU 型号" to "CPU Model",
    "当前设备未暴露可用的 CPU 频率节点，CPU 频率信息不可用。" to "This device does not expose readable CPU frequency nodes, so CPU frequency info is unavailable.",
    "SoC 相关 Thermal Zones" to "SoC-related Thermal Zones",
    "未发现可读且与 SoC 相关的 thermal_zone 节点。" to "No readable thermal_zone nodes related to SoC were found.",
    "未分簇" to "Unclustered",
    "占比" to "Share",
    "CPU10 离线" to "CPU10 Offline",
    "采集失败" to "Collection failed",
    "实时通知受限" to "Live notification restricted",
    "为了在通知栏显示实时充电功率，需要授予通知权限。" to "Notification permission is required to show real-time charging power in notifications.",
    "允许通知" to "Allow Notifications",
    "不再提醒" to "Don't remind again",
    "后台保活受限" to "Background keep-alive restricted",
    "为了保证桌面小组件实时刷新，请将本应用加入电池优化白名单。" to "To keep widgets refreshing in real time, add this app to the battery optimization whitelist.",
    "立即开启" to "Enable Now",
    "检测到电压读数异常" to "Abnormal voltage reading detected",
    "设备似乎无法读取实时电压。建议开启“虚拟电压”功能以获得估算数据。" to "This device seems unable to read real-time voltage. Enable \"Virtual Voltage\" for estimated values.",
    "开启虚拟电压" to "Enable Virtual Voltage",
    "瞬时功率" to "Instant Power",
    "电池温度" to "Battery Temperature",
    "电池电压" to "Battery Voltage",
    "电池电流" to "Battery Current",
    "供电状态" to "Power Source",
    "电池状态" to "Battery Status",
    "当前剩余电量 / 总容量" to "Current Remaining Charge / Total Capacity",
    "系统剩余容量 / 总容量" to "System Remaining Capacity / Total Capacity",
    "容量计算说明" to "Capacity Calculation Notes",
    "以下内容用于解释本卡片容量数据来源：" to "The following explains the data source for the capacity card:",
    "此处容量与百分比基于系统提供的电池容量数据计算，并非系统状态栏百分比。" to
        "The capacity and percentage here are calculated from system-provided battery capacity data, not the status-bar percentage.",
    "部分厂商存在锁容策略，可能出现系统显示已充满，但实际电池容量尚未达到满值的情况。" to
        "Some vendors apply capacity locks, so the system may show fully charged while actual battery capacity is still below full.",
    "此处容量与百分比基于系统提供的电池容量数据计算，并非系统状态栏百分比。部分厂商存在锁容策略，可能出现系统显示已充满，但实际电池容量尚未达到满值的情况。" to
        "The capacity and percentage here are calculated from system-provided battery capacity data, not the status-bar percentage. Some vendors apply capacity locks, so the system may show fully charged while actual battery capacity is still below full.",
    "1. 此处容量与百分比基于系统提供的电池容量数据计算，并非系统状态栏百分比。\n2. 部分厂商存在锁容策略，可能出现系统显示已充满，但实际电池容量尚未达到满值的情况。" to
        "1. The capacity and percentage here are calculated from system-provided battery capacity data, not the status-bar percentage.\n2. Some vendors apply capacity locks, so the system may show fully charged while actual battery capacity is still below full.",
    "知道了" to "Got it",
    "未知" to "Unknown",
    "检测中" to "Detecting",
    "电源适配器" to "AC Adapter",
    "电脑 (USB)" to "Computer (USB)",
    "无线充电" to "Wireless Charging",
    "底座供电" to "Dock Power",
    "电池供电" to "Battery Power",
    "良好" to "Good",
    "过热" to "Overheat",
    "损坏" to "Damaged",
    "过压" to "Over-voltage",
    "故障" to "Failure",
    "过冷" to "Too Cold",
    "状态未知" to "Status Unknown",
    "充电中" to "Charging",
    "放电中" to "Discharging",
    "已接电源(未充电)" to "Plugged in (Not Charging)",
    "未充电" to "Not Charging",
    "已充满" to "Fully Charged",
    "状态异常" to "Abnormal Status",
    "教室课表查询" to "Classroom Schedule Query",
    "课表选择" to "Schedule Selection",
    "跟随最新" to "Follow Latest",
    "实时功率曲线" to "Real-time Power Curve",
    "实时温度曲线" to "Real-time Temperature Curve",
    "充电时普通通知" to "Standard notification while charging",
    "未充电时普通通知" to "Standard notification while not charging",
    "本次关闭" to "Close Once",
    "电池监控后台服务" to "Battery Monitor Background Service",
    "显示实时充电功率（静默通知，不会打扰）" to "Shows real-time charging power (silent notification, no interruption)",
    "实时活动 (灵动岛)" to "Live Activity (Dynamic Island)",
    "充电时显示灵动岛风格的实况通知" to "Shows Dynamic Island-style live notification while charging",
    "后台监测中" to "Monitoring in Background"
)

private val patternStatusWithRemaining = Regex("""^状态：(.+) \(余 (.+)\)$""")
private val patternStatus = Regex("""^状态：(.+)$""")
private val patternSeconds = Regex("""^(\d+)秒$""")
private val patternMinutes = Regex("""^(\d+)分钟$""")
private val patternComponents = Regex("""^(\d+) 个组件$""")
private val patternLicenseGroups = Regex("""^(\d+) 组许可$""")
private val patternLicenseTypes = Regex("""^(\d+) 类许可$""")
private val patternForComponents = Regex("""^适用于 (\d+) 个组件$""")
private val patternLicenseSummary = Regex("""^(.*) · (.*) 等 (\d+) 类许可$""")
private val patternOnline = Regex("""^在线 (\d+) / (\d+)$""")
private val patternCpuOffline = Regex("""^CPU(\d+) 离线$""")
private val patternSource = Regex("""^当前来源: (.+)$""")

fun l10n(raw: String): String {
    val locale = resolveAppTextLocale(resolveCurrentAppLanguageOption())
    translateDynamic(raw, locale)?.let { return it }
    return translateStatic(raw, locale)
}

fun l10n(raw: String, option: AppLanguageOption): String {
    val locale = resolveAppTextLocale(option)
    translateDynamic(raw, locale)?.let { return it }
    return translateStatic(raw, locale)
}

private fun translateStatic(raw: String, locale: AppTextLocale): String {
    return when (locale) {
        AppTextLocale.ZH_CN -> raw
        AppTextLocale.ZH_HK -> zhHkMap[raw] ?: raw
        AppTextLocale.ZH_TW -> zhTwMap[raw] ?: raw
        AppTextLocale.EN -> enMap[raw] ?: raw
    }
}

private fun translateDynamic(raw: String, locale: AppTextLocale): String? {
    val statusWithRemainingMatch = patternStatusWithRemaining.matchEntire(raw)
    if (statusWithRemainingMatch != null) {
        val statusText = statusWithRemainingMatch.groupValues[1]
        val remaining = statusWithRemainingMatch.groupValues[2]
        val translatedStatus = translateStatic(statusText, locale)
        val translatedRemaining = translateDynamic(remaining, locale) ?: translateStatic(remaining, locale)
        return when (locale) {
            AppTextLocale.ZH_CN -> raw
            AppTextLocale.ZH_HK, AppTextLocale.ZH_TW -> "狀態：$translatedStatus（餘 $translatedRemaining）"
            AppTextLocale.EN -> "Status: $translatedStatus ($translatedRemaining left)"
        }
    }

    val statusMatch = patternStatus.matchEntire(raw)
    if (statusMatch != null) {
        val statusText = statusMatch.groupValues[1]
        val translatedStatus = translateStatic(statusText, locale)
        return when (locale) {
            AppTextLocale.ZH_CN -> raw
            AppTextLocale.ZH_HK, AppTextLocale.ZH_TW -> "狀態：$translatedStatus"
            AppTextLocale.EN -> "Status: $translatedStatus"
        }
    }

    fun group1(regex: Regex): String? = regex.matchEntire(raw)?.groupValues?.get(1)

    group1(patternSeconds)?.let { value ->
        return when (locale) {
            AppTextLocale.ZH_CN -> "${value}秒"
            AppTextLocale.ZH_HK, AppTextLocale.ZH_TW -> "${value}秒"
            AppTextLocale.EN -> "$value sec"
        }
    }

    group1(patternMinutes)?.let { value ->
        return when (locale) {
            AppTextLocale.ZH_CN -> "${value}分钟"
            AppTextLocale.ZH_HK, AppTextLocale.ZH_TW -> "${value}分鐘"
            AppTextLocale.EN -> "$value min"
        }
    }

    group1(patternComponents)?.let { value ->
        return when (locale) {
            AppTextLocale.ZH_CN -> "$value 个组件"
            AppTextLocale.ZH_HK -> "$value 個組件"
            AppTextLocale.ZH_TW -> "$value 個元件"
            AppTextLocale.EN -> "$value components"
        }
    }

    group1(patternLicenseGroups)?.let { value ->
        return when (locale) {
            AppTextLocale.ZH_CN -> "$value 组许可"
            AppTextLocale.ZH_HK, AppTextLocale.ZH_TW -> "$value 組授權"
            AppTextLocale.EN -> "$value license groups"
        }
    }

    group1(patternLicenseTypes)?.let { value ->
        return when (locale) {
            AppTextLocale.ZH_CN -> "$value 类许可"
            AppTextLocale.ZH_HK, AppTextLocale.ZH_TW -> "$value 類授權"
            AppTextLocale.EN -> "$value license types"
        }
    }

    group1(patternForComponents)?.let { value ->
        return when (locale) {
            AppTextLocale.ZH_CN -> "适用于 $value 个组件"
            AppTextLocale.ZH_HK -> "適用於 $value 個組件"
            AppTextLocale.ZH_TW -> "適用於 $value 個元件"
            AppTextLocale.EN -> "Applies to $value components"
        }
    }

    patternLicenseSummary.matchEntire(raw)?.let { match ->
        val summary = match.groupValues[1]
        val firstLicense = match.groupValues[2]
        val count = match.groupValues[3]
        val translatedSummary = translateDynamic(summary, locale) ?: translateStatic(summary, locale)
        val translatedFirstLicense = translateStatic(firstLicense, locale)
        return when (locale) {
            AppTextLocale.ZH_CN -> raw
            AppTextLocale.ZH_HK, AppTextLocale.ZH_TW -> "$translatedSummary · $translatedFirstLicense 等 $count 類授權"
            AppTextLocale.EN -> "$translatedSummary · $translatedFirstLicense and $count more license types"
        }
    }

    patternOnline.matchEntire(raw)?.let { match ->
        val online = match.groupValues[1]
        val total = match.groupValues[2]
        return when (locale) {
            AppTextLocale.ZH_CN -> raw
            AppTextLocale.ZH_HK -> "在線 $online / $total"
            AppTextLocale.ZH_TW -> "線上 $online / $total"
            AppTextLocale.EN -> "Online $online / $total"
        }
    }

    group1(patternCpuOffline)?.let { id ->
        return when (locale) {
            AppTextLocale.ZH_CN -> raw
            AppTextLocale.ZH_HK, AppTextLocale.ZH_TW -> "CPU$id 離線"
            AppTextLocale.EN -> "CPU$id offline"
        }
    }

    group1(patternSource)?.let { source ->
        val translatedSource = translateDynamic(source, locale) ?: translateStatic(source, locale)
        return when (locale) {
            AppTextLocale.ZH_CN -> raw
            AppTextLocale.ZH_HK -> "當前來源: $translatedSource"
            AppTextLocale.ZH_TW -> "目前來源: $translatedSource"
            AppTextLocale.EN -> "Current source: $translatedSource"
        }
    }

    return null
}

private fun resolveAppTextLocale(option: AppLanguageOption): AppTextLocale {
    return when (option) {
        AppLanguageOption.ENGLISH -> AppTextLocale.EN
        AppLanguageOption.CHINESE_SIMPLIFIED_CHINA -> AppTextLocale.ZH_CN
        AppLanguageOption.CHINESE_TRADITIONAL_HONG_KONG -> AppTextLocale.ZH_HK
        AppLanguageOption.CHINESE_TRADITIONAL_TAIWAN -> AppTextLocale.ZH_TW
        AppLanguageOption.FOLLOW_SYSTEM -> resolveSystemTextLocale()
    }
}

private fun resolveSystemTextLocale(): AppTextLocale {
    return when (resolveSupportedSystemLanguageOption()) {
        AppLanguageOption.CHINESE_SIMPLIFIED_CHINA -> AppTextLocale.ZH_CN
        AppLanguageOption.CHINESE_TRADITIONAL_HONG_KONG -> AppTextLocale.ZH_HK
        AppLanguageOption.CHINESE_TRADITIONAL_TAIWAN -> AppTextLocale.ZH_TW
        AppLanguageOption.ENGLISH, AppLanguageOption.FOLLOW_SYSTEM -> AppTextLocale.EN
    }
}
