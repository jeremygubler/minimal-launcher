package dev.minimal.launcher.pro

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Sideload-Version (GitHub): alle Pro-Funktionen sind frei. */
object Pro {
    val isPro: StateFlow<Boolean> = MutableStateFlow(true)
    val price: StateFlow<String?> = MutableStateFlow(null)

    fun init(context: Context) = Unit
    fun purchase(activity: Activity) = Unit
    fun restore() = Unit
}
