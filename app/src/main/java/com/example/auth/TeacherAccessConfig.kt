package com.example.auth

import android.content.Context
import com.example.BuildConfig
import com.example.R

object TeacherAccessConfig {
    /**
     * Configured via AUTHORIZED_TEACHER_EMAIL in .env / AI Studio Secrets panel
     * or R.string.authorized_teacher_email in strings.xml.
     * Blank when not yet locked; once set to Ma'am's Gmail, only that exact
     * verified Google account is granted entry.
     */
    fun configuredTeacherEmail(context: Context? = null): String {
        val fromBuildConfig = BuildConfig.AUTHORIZED_TEACHER_EMAIL.trim().lowercase()
        if (fromBuildConfig.isNotBlank()) return fromBuildConfig
        val fromRes = context?.let {
            runCatching { it.getString(R.string.authorized_teacher_email).trim().lowercase() }.getOrNull()
        }.orEmpty()
        return fromRes
    }

    fun isLockConfigured(context: Context? = null): Boolean =
        configuredTeacherEmail(context).isNotBlank()

    fun isEmailAuthorized(
        email: String?,
        isEmailVerified: Boolean,
        context: Context? = null,
    ): Boolean {
        if (!isEmailVerified || email.isNullOrBlank()) return false
        val configured = configuredTeacherEmail(context)
        if (configured.isEmpty()) return true
        return email.trim().lowercase() == configured
    }
}
