package com.example.fusioncli.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Dashboard : Route

    @Serializable
    data object Chat : Route

    @Serializable
    data object Workspace : Route
}
