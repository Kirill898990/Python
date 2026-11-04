package ru.prostayahimiya.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.prostayahimiya.app.data.Repository
import ru.prostayahimiya.app.data.Subject
import ru.prostayahimiya.app.ui.admin.AdminScreen
import ru.prostayahimiya.app.ui.admin.PracticeEditorScreen
import ru.prostayahimiya.app.ui.auth.AuthScreen
import ru.prostayahimiya.app.ui.cabinet.CabinetScreen
import ru.prostayahimiya.app.ui.cabinet.SubmissionScreen
import ru.prostayahimiya.app.ui.components.BackTopBar
import ru.prostayahimiya.app.ui.components.EmptyBox
import ru.prostayahimiya.app.ui.home.HomeScreen
import ru.prostayahimiya.app.ui.practice.PracticeScreen
import ru.prostayahimiya.app.ui.subject.SubjectScreen
import ru.prostayahimiya.app.ui.theory.TheoryScreen

/** Экраны приложения и переходы между ними. */
@Composable
fun AppNavigation(repository: Repository) {
    val user by repository.user.collectAsState()

    // Без входа доступен только экран входа и регистрации.
    val signedIn = user != null
    if (!signedIn) {
        AuthScreen(repository)
        return
    }

    val nav = rememberNavController()
    LaunchedEffect(user?.id) {
        repository.refreshSession()
        repository.refreshContent()
        if (repository.user.value?.isTeacher != true) repository.refreshMySubmissions()
    }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                repository = repository,
                onOpenSubject = { subject -> nav.navigate("subject/${subject.name}") },
                onOpenTheory = { nav.navigate("theory") },
                onOpenCabinet = { nav.navigate("cabinet") },
                onOpenAdmin = { nav.navigate("admin") },
            )
        }
        composable("subject/{subject}") { entry ->
            val subject = Subject.valueOf(entry.arguments?.getString("subject") ?: Subject.CHEMISTRY.name)
            SubjectScreen(
                subject = subject,
                repository = repository,
                onBack = dropUnlessResumed { nav.popBackStack() },
                onOpenPractice = { id -> nav.navigate("practice/$id") },
            )
        }
        composable("practice/{id}") { entry ->
            PracticeScreen(
                practiceId = entry.arguments?.getString("id").orEmpty(),
                repository = repository,
                onBack = dropUnlessResumed { nav.popBackStack() },
                onOpenCabinet = { nav.navigateSingle("cabinet") },
            )
        }
        composable("theory") {
            TheoryScreen(onBack = dropUnlessResumed { nav.popBackStack() })
        }
        composable("cabinet") {
            CabinetScreen(
                repository = repository,
                onBack = dropUnlessResumed { nav.popBackStack() },
                onOpenSubmission = { id -> nav.navigate("submission/$id") },
            )
        }
        composable("submission/{id}") { entry ->
            SubmissionScreen(
                submissionId = entry.arguments?.getString("id").orEmpty(),
                repository = repository,
                onBack = dropUnlessResumed { nav.popBackStack() },
            )
        }
        composable("admin") {
            TeacherOnly(user?.isTeacher == true, onBack = dropUnlessResumed { nav.popBackStack() }) {
                AdminScreen(
                    repository = repository,
                    onBack = dropUnlessResumed { nav.popBackStack() },
                    onOpenSubmission = { id -> nav.navigate("submission/$id") },
                    onEditPractice = { id -> nav.navigate("admin/practice/$id") },
                )
            }
        }
        composable("admin/practice/{id}") { entry ->
            TeacherOnly(user?.isTeacher == true, onBack = dropUnlessResumed { nav.popBackStack() }) {
                PracticeEditorScreen(
                    practiceId = entry.arguments?.getString("id").orEmpty(),
                    repository = repository,
                    onBack = dropUnlessResumed { nav.popBackStack() },
                )
            }
        }
    }
}

/** Открывает экран, а если он уже открыт ниже в стеке, возвращается к нему. */
private fun NavHostController.navigateSingle(route: String) {
    if (!popBackStack(route, inclusive = false)) navigate(route)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeacherOnly(isTeacher: Boolean, onBack: () -> Unit, content: @Composable () -> Unit) {
    if (isTeacher) {
        content()
    } else {
        Scaffold(topBar = { BackTopBar("Нет доступа", onBack) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
            EmptyBox("🔒", "Этот раздел доступен только учителю", Modifier.padding(padding))
        }
    }
}
