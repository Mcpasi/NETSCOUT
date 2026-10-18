package de.netscout.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import de.netscout.core.SearchEngine
import de.netscout.data.ProviderFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetScoutTheme {
                val model: SearchViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchViewModel(EncryptedSettingsStore(applicationContext)) { settings ->
                        SearchEngine(ProviderFactory.create(settings), relevance = LocalSemanticSearch.relevance(applicationContext))
                    } as T
                })
                NetScoutApp(model)
            }
        }
    }
}
