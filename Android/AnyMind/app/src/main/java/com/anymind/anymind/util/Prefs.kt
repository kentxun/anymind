package com.anymind.anymind.util

import android.content.Context

object Prefs {
    private const val PREFS_NAME = "anymind_prefs"

    fun get(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
