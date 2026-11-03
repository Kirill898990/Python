package ru.prostayahimiya.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.prostayahimiya.app.data.ProgressStore
import ru.prostayahimiya.app.data.Subject
import ru.prostayahimiya.app.ui.home.HomeScreen
import ru.prostayahimiya.app.ui.practice.PracticeScreen
import ru.prostayahimiya.app.ui.subject.SubjectScreen
import ru.prostayahimiya.app.ui.theory.TheoryScreen

/** Экраны приложения и переходы между ними. */
@Composable
fun AppNavigation(store: ProgressStore) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                store = store,
                onOpenSubject = { subject -> nav.navigate("subject/${subject.name}") },
                onOpenTheory = { nav.navigate("theory") },
            )
        }
        composable("subject/{subject}") { entry ->
            val subject = Subject.valueOf(entry.arguments?.getString("subject") ?: Subject.CHEMISTRY.name)
            SubjectScreen(
                subject = subject,
                store = store,
                onBack = dropUnlessResumed { nav.popBackStack() },
                onOpenPractice = { practiceId -> nav.navigate("practice/$practiceId") },
            )
        }
        composable("practice/{id}") { entry ->
            PracticeScreen(
                practiceId = entry.arguments?.getString("id").orEmpty(),
                onBack = dropUnlessResumed { nav.popBackStack() },
            )
        }
        composable("theory") {
            TheoryScreen(onBack = dropUnlessResumed { nav.popBackStack() })
        }
    }
}
