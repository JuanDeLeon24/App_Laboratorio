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

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer, SavedStateHandle) -> VM): VM {
    val container = (LocalContext.current.applicationContext as GeoDensidadApp).container
    return viewModel(factory = viewModelFactory { initializer { create(container, createSavedStateHandle()) } })
}

@Composable
fun AppNavHost(nav: NavHostController = rememberNavController()) {
    NavHost(nav, startDestination = "registros") {
        composable("registros") { RegistrosScreen { nav.navigate("registro/$it") } }
        composable("registro/{id}") { RegistroScreen { nav.popBackStack() } }
    }
}
