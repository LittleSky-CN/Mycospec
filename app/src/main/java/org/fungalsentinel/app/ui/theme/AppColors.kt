package org.fungalsentinel.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 单一颜色源（Single Source of Truth）。
 * 设计约束：任一工作流屏幕同时最多 2 个色相 ——
 *   1) 强调绿（Accent 族：含 Dim/Soft 变体，覆盖交互/成功/处理中）
 *   2) 语义红（Error，仅 FAILED/BLOCKED/告警出现）
 * 其余全部为中性灰阶。主题与所有组件只允许引用本对象。
 */
object AppColors {
    // ── 中性灰阶 ──
    val Bg            = Color(0xFF121212)   // 页面背景
    val Surface       = Color(0xFF1E1E1E)   // 卡片 / 面板 / 下拉
    val SurfaceRaised = Color(0xFF2A2A2A)   // 抬升面 / 投影替代 / 禁用底
    val Outline       = Color(0xFF3A3A3A)   // 描边 / 分隔 / 次要边框
    val TextPrimary   = Color(0xFFE0E0E0)   // 主文本
    val TextSecondary = Color(0xFF9E9E9E)   // 次文本 / 空闲状态
    val OnAccent      = Color(0xFF14241A)   // 强调面上的深色字

    // ── 唯一强调色相（绿）──
    val Accent     = Color(0xFF81C784)
    val AccentDim  = Color(0xFF5F8F61)                 // 按下 / 次级
    val AccentSoft = Color(0xFF81C784).copy(alpha = 0.55f) // 处理中 / 次要高亮

    // ── 唯一语义色相（红，仅异常）──
    val Error = Color(0xFFE57373)

    // ── 数据可视化专用（通道语义编码，不计入 UI 色板）──
    object Chart {
        val R   = Color(0xFFE57373)
        val G   = Color(0xFF81C784)
        val B   = Color(0xFF90CAF9)
        val Fit = Color(0xFFB0BEC5)
    }
}

/** 全 App 统一的状态令牌→文本色映射，消除各屏幕重复的 when 块与硬编码。 */
fun statusTextColor(token: String): Color = when (token) {
    "FAILED", "BLOCKED"   -> AppColors.Error
    "COMPUTED", "PRESET"  -> AppColors.Accent
    "PROCESSING"          -> AppColors.AccentSoft
    else                  -> AppColors.TextSecondary
}