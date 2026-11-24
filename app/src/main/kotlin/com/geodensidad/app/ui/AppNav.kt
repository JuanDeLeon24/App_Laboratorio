package com.geodensidad.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.geodensidad.app.AppContainer
import com.geodensidad.app.GeoDensidadApp
import com.geodensidad.app.ui.screens.*

/** Crea ViewModels con acceso al contenedor y a SavedStateHandle (argumentos de navegación). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer, SavedStateHandle) -> VM): VM {
    val container = (LocalContext.current.applicationContext as GeoDensidadApp).container
    return viewModel(factory = viewModelFactory { initializer { create(container, createSavedStateHandle()) } })
}

object Routes {
    const val HOME = "home"
    fun project(id: String) = "project/$id"
    fun activity(id: String) = "activity/$id"
    fun sample(id: String) = "sample/$id"
    fun photo(id: String) = "photo/$id"
    fun lot(id: String) = "lot/$id"
    fun report(id: String) = "report/$id"
    fun history(id: String) = "history/$id"
    fun journeyPhotos(activityId: String, journeyId: String) = "journeyPhotos/$activityId/$journeyId"
    const val CATALOGS = "catalogs"
    const val FIELDS = "fields"
    const val BACKUPS = "backups"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost(nav: NavHostController = rememberNavController()) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(nav) }
        composable("project/{id}") { ProjectScreen(nav, back) }
        composable("activity/{id}") { ActivityScreen(nav, back) }
        composable("sample/{id}") { SampleScreen(nav, back) }
        composable("photo/{id}") { PhotoScreen(back) }
        composable("lot/{id}") { LotScreen(nav, back) }
        composable("report/{id}") { ReportPreviewScreen(back) }
        composable("history/{id}") { HistoryScreen(back) }
        composable("journeyPhotos/{activityId}/{journeyId}") { JourneyPhotosScreen(nav, back) }
        composable(Routes.CATALOGS) { CatalogsScreen(back) }
        composable(Routes.FIELDS) { CustomFieldsScreen(back) }
        composable(Routes.BACKUPS) { BackupScreen(back) }
        composable(Routes.SETTINGS) { SettingsScreen(nav, back) }
    }
}
