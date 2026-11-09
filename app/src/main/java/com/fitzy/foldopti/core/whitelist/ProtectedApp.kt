package com.fitzy.foldopti.core.whitelist

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ProtectedSource { USER, DEFAULT, SUGGESTED }

@Entity(tableName = "protected_app")
data class ProtectedApp(
    @PrimaryKey val pkg: String,
    val reason: String,
    val addedAt: Long,
    val source: ProtectedSource,
)
