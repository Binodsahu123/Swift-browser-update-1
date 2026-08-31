package com.swift.browser.browserengine

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.swift.browser.data.BrowserRepository
import com.swift.browser.databasecore.PreferenceManager

class BrowserViewModelFactory(
    private val application: Application,
    private val repository: BrowserRepository,
    private val prefs: PreferenceManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BrowserViewModel::class.java)) {
            return BrowserViewModel(application, repository, prefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
