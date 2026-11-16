package com.imagemanager.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.imagemanager.app.ImageManagerApp
import com.imagemanager.app.ui.cardswipe.CardSwipeScreen
import com.imagemanager.app.ui.cardswipe.CardSwipeViewModel
import com.imagemanager.app.ui.compare.ComparisonScreen
import com.imagemanager.app.ui.favorites.FavoritesScreen
import com.imagemanager.app.ui.favorites.FavoritesViewModel
import com.imagemanager.app.ui.permissions.PermissionGateScreen
import com.imagemanager.app.ui.piles.PileListScreen
import com.imagemanager.app.ui.piles.PileViewModel
import com.imagemanager.app.ui.similar.SimilarGroupsScreen
import com.imagemanager.app.ui.similar.SimilarGroupsViewModel

private sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    object Swipe : Tab("swipe", "정리", Icons.Filled.SwapVert)
    object Favorites : Tab("favorites", "즐겨찾기", Icons.Filled.Favorite)
    object Similar : Tab("similar", "유사 사진", Icons.Filled.Compare)
    object Piles : Tab("piles", "폴더함", Icons.Filled.Folder)
}

private val tabs = listOf(Tab.Swipe, Tab.Favorites, Tab.Similar, Tab.Piles)

@Composable
fun AppNavHost(app: ImageManagerApp) {
    var permissionGranted by remember { mutableStateOf(false) }

    if (!permissionGranted) {
        PermissionGateScreen(onGranted = { permissionGranted = true })
        return
    }

    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Swipe.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Swipe.route) {
                val viewModel: CardSwipeViewModel = viewModel(
                    factory = AppViewModelFactory(
                        app,
                        cardSwipePhotos = app.mediaStoreRepository.listAllPhotos(),
                        restorePosition = true,
                    ),
                )
                CardSwipeScreen(
                    viewModel = viewModel,
                    onCompareRequested = { mediaIds ->
                        navController.navigate("compare/${mediaIds.joinToString(",")}")
                    },
                )
            }
            composable(Tab.Favorites.route) {
                val viewModel: FavoritesViewModel = viewModel(factory = AppViewModelFactory(app))
                FavoritesScreen(viewModel, onPhotoClick = {})
            }
            composable(Tab.Similar.route) {
                val viewModel: SimilarGroupsViewModel = viewModel(factory = AppViewModelFactory(app))
                SimilarGroupsScreen(
                    viewModel = viewModel,
                    photoUriFor = { app.mediaStoreRepository.uriFor(it) },
                    onGroupClick = { group ->
                        navController.navigate("swipe_group/${group.mediaIds.joinToString(",")}")
                    },
                )
            }
            composable(Tab.Piles.route) {
                val viewModel: PileViewModel = viewModel(factory = AppViewModelFactory(app))
                PileListScreen(
                    viewModel = viewModel,
                    onPileClick = { pile ->
                        navController.navigate("swipe_pile/${Uri.encode(pile.folderRelativePath)}")
                    },
                )
            }
            composable(
                route = "swipe_group/{mediaIds}",
                arguments = listOf(navArgument("mediaIds") { type = NavType.StringType }),
            ) { backStackEntry ->
                val ids = backStackEntry.arguments?.getString("mediaIds")
                    ?.split(",")?.mapNotNull { it.toLongOrNull() }
                    ?: emptyList()
                val groupPhotos = app.mediaStoreRepository.listByIds(ids)
                val viewModel: CardSwipeViewModel = viewModel(
                    factory = AppViewModelFactory(app, cardSwipePhotos = groupPhotos),
                )
                CardSwipeScreen(
                    viewModel = viewModel,
                    onCompareRequested = { compareIds ->
                        navController.navigate("compare/${compareIds.joinToString(",")}")
                    },
                )
            }
            composable(
                route = "swipe_pile/{relativePath}",
                arguments = listOf(navArgument("relativePath") { type = NavType.StringType }),
            ) { backStackEntry ->
                val relativePath = Uri.decode(backStackEntry.arguments?.getString("relativePath").orEmpty())
                val pilePhotos = app.mediaStoreRepository.listByRelativePath(relativePath)
                val viewModel: CardSwipeViewModel = viewModel(
                    factory = AppViewModelFactory(app, cardSwipePhotos = pilePhotos),
                )
                CardSwipeScreen(
                    viewModel = viewModel,
                    onCompareRequested = { compareIds ->
                        navController.navigate("compare/${compareIds.joinToString(",")}")
                    },
                )
            }
            composable(
                route = "compare/{mediaIds}",
                arguments = listOf(navArgument("mediaIds") { type = NavType.StringType }),
            ) { backStackEntry ->
                val ids = backStackEntry.arguments?.getString("mediaIds")
                    ?.split(",")?.mapNotNull { it.toLongOrNull() }
                    ?: emptyList()
                ComparisonScreen(photos = app.mediaStoreRepository.listByIds(ids))
            }
        }
    }
}
