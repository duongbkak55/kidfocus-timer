package com.kidfocus.timer.domain.model

enum class TaskCategory(val displayName: String, val emoji: String) {
    STUDY("Học tập", "📚"),
    HYGIENE("Vệ sinh & Sức khỏe", "🧼"),
    CHORES("Sinh hoạt hàng ngày", "🏠"),
    ENTERTAINMENT("Giải trí", "🎉"),
}
