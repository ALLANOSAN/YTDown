package com.example.ytdown.ui.navigation

import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import com.example.ytdown.core.business.AgrupamentoDaBiblioteca
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * O clique num grupo da Biblioteca navega com o NOME do artista/album na rota
 * (GroupedList -> MainNavigation:56 -> playlist_detail/{playlistId}).
 *
 * No aparelho isso deu FATAL 4x seguidas: IllegalArgumentException "Wrong
 * argument type for 'playlistId' in argument savedState. string expected."
 * Reproduzido aqui antes do fix com os nomes reais do banco do usuário.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PlaylistDetailRouteTest {

    private fun controlador(): NavHostController {
        val contexto = RuntimeEnvironment.getApplication()
        return NavHostController(contexto).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            setViewModelStore(ViewModelStore())
            graph = createGraph(startDestination = "home") {
                composable("home") { }
                composable(
                    Screen.PlaylistDetail.route,
                    arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
                ) { }
            }
        }
    }

    private fun tituloEntregue(controlador: NavHostController): String? =
        controlador.currentBackStackEntry?.arguments?.getString("playlistId")

    @Test
    fun `grupo com nome normal abre o detalhe`() {
        val controlador = controlador()

        controlador.navigate(Screen.PlaylistDetail.createRoute("Petra"))

        assertEquals("Petra", tituloEntregue(controlador))
    }

    @Test
    fun `grupo com espaco no nome abre o detalhe`() {
        val controlador = controlador()

        controlador.navigate(Screen.PlaylistDetail.createRoute("The Scorpions"))

        assertEquals("The Scorpions", tituloEntregue(controlador))
    }

    /**
     * O caminho que o dedo faz: a lista agrupa pela chave (o "null" do banco
     * vira "Desconhecido") e só a chave viaja na rota.
     */
    @Test
    fun `grupo com nome null abre como Desconhecido sem crash`() {
        val controlador = controlador()

        controlador.navigate(
            Screen.PlaylistDetail.createRoute(AgrupamentoDaBiblioteca.chave("null"))
        )

        assertEquals("Desconhecido", tituloEntregue(controlador))
    }

    @Test
    fun `album com barra abre o detalhe sem crash e com o titulo intacto`() {
        val controlador = controlador()

        controlador.navigate(Screen.PlaylistDetail.createRoute("Big City Nights / Holiday"))

        assertEquals("Big City Nights / Holiday", tituloEntregue(controlador))
    }
}
