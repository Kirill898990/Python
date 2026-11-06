package ru.prostayahimiya.app.ui

import android.net.Uri
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.prostayahimiya.app.data.ChatRoom
import ru.prostayahimiya.app.data.Repository
import ru.prostayahimiya.app.data.Subject
import ru.prostayahimiya.app.ui.admin.AdminScreen
import ru.prostayahimiya.app.ui.admin.PracticeEditorScreen
import ru.prostayahimiya.app.ui.auth.AuthScreen
import ru.prostayahimiya.app.ui.auth.PendingTeacherScreen
import ru.prostayahimiya.app.ui.cabinet.CabinetScreen
import ru.prostayahimiya.app.ui.cabinet.OrdersScreen
import ru.prostayahimiya.app.ui.course.IncomingCallWatcher
import ru.prostayahimiya.app.ui.cabinet.SubmissionScreen
import ru.prostayahimiya.app.ui.chat.ChatListScreen
import ru.prostayahimiya.app.ui.chat.ChatScreen
import ru.prostayahimiya.app.ui.components.BackTopBar
import ru.prostayahimiya.app.ui.components.EmptyBox
import ru.prostayahimiya.app.ui.home.HomeScreen
import ru.prostayahimiya.app.ui.practice.PracticeScreen
import ru.prostayahimiya.app.ui.reference.ReferenceScreen
import ru.prostayahimiya.app.ui.subject.SubjectScreen
import ru.prostayahimiya.app.ui.course.StudentCourseScreen
import ru.prostayahimiya.app.ui.course.TeacherCourseScreen
import ru.prostayahimiya.app.ui.theory.TheoryScreen
import ru.prostayahimiya.app.ui.trainer.TrainerScreen

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
    // Новый учитель ждёт подтверждения администратора.
    if (user?.wantsTeacher == true && user?.isTeacher != true) {
        PendingTeacherScreen(repository)
        return
    }

    val nav = rememberNavController()
    LaunchedEffect(user?.id) {
        repository.refreshSession()
        repository.refreshContent()
        if (repository.user.value?.isTeacher != true) repository.refreshMySubmissions()
    }

    // Нажали на уведомление — открываем нужный экран (практику, работу, чат или панель учителя).
    val openRequest by repository.openRequest.collectAsState()
    LaunchedEffect(openRequest) {
        val route = openRequest ?: return@LaunchedEffect
        repository.openRequest.value = null
        runCatching { nav.navigate(route) }
    }

    // Входящий звонок по курсу — окно «Ответить / Отклонить» поверх любого экрана.
    IncomingCallWatcher(repository)

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                repository = repository,
                onOpenSubject = { subject -> nav.navigate("subject/${subject.name}") },
                onOpenTheory = { nav.navigate("theory") },
                onOpenCabinet = { nav.navigate("cabinet") },
                onOpenAdmin = { nav.navigate("admin") },
                onOpenChat = { nav.navigate("chats") },
                onOpenPractice = { id -> nav.navigate("practice/$id") },
                onOpenReference = { nav.navigate("reference") },
                onOpenTrainer = { nav.navigate("trainer") },
                onOpenCourse = { id -> nav.navigate("course/$id") },
            )
        }
        composable("chats") {
            ChatListScreen(
                repository = repository,
                onBack = dropUnlessResumed { nav.popBackStack() },
                onOpenRoom = { room, title -> nav.navigate(chatRoute(room, title)) },
            )
        }
        composable(
            "chat/{kind}/{key}?title={title}",
            arguments = listOf(navArgument("title") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            val kind = entry.arguments?.getString("kind").orEmpty()
            val key = entry.arguments?.getString("key").orEmpty()
            ChatScreen(
                room = ChatRoom.of(kind, key),
                title = entry.arguments?.getString("title").orEmpty(),
                repository = repository,
                onBack = dropUnlessResumed { nav.popBackStack() },
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
                onOpenReference = { nav.navigate("reference") },
                onOpenSubmission = { id -> nav.navigate("submission/$id") },
            )
        }
        composable("reference") {
            ReferenceScreen(onBack = dropUnlessResumed { nav.popBackStack() })
        }
        composable("trainer") {
            TrainerScreen(
                repository = repository,
                onBack = dropUnlessResumed { nav.popBackStack() },
                onOpenReference = { nav.navigate("reference") },
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
                onOpenOrders = { nav.navigate("orders") },
            )
        }
        composable("orders") {
            OrdersScreen(repository = repository, onBack = dropUnlessResumed { nav.popBackStack() })
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
                    onWriteToStudent = { student ->
                        val title = if (student.group.isNotBlank()) "${student.name}, ${student.group}" else student.name
                        nav.navigate(chatRoute(ChatRoom.private(student.id), title))
                    },
                    onOpenCourse = { id -> nav.navigate("admin/course/$id") },
                )
            }
        }
        composable(
            "admin/practice/{id}?enrollment={enrollment}&subject={subject}&grade={grade}",
            arguments = listOf(
                navArgument("enrollment") { type = NavType.StringType; defaultValue = "" },
                navArgument("subject") { type = NavType.StringType; defaultValue = "" },
                navArgument("grade") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) { entry ->
            TeacherOnly(user?.isTeacher == true, onBack = dropUnlessResumed { nav.popBackStack() }) {
                PracticeEditorScreen(
                    practiceId = entry.arguments?.getString("id").orEmpty(),
                    repository = repository,
                    onBack = dropUnlessResumed { nav.popBackStack() },
                    enrollment = entry.arguments?.getString("enrollment").orEmpty(),
                    newSubject = entry.arguments?.getString("subject").orEmpty(),
                    newGrade = entry.arguments?.getInt("grade") ?: 0,
                )
            }
        }
        // Курс ученика (купленный в магазине beemei на сайте).
        composable("course/{id}") { entry ->
            StudentCourseScreen(
                enrollmentId = entry.arguments?.getString("id").orEmpty(),
                repository = repository,
                onBack = dropUnlessResumed { nav.popBackStack() },
                onOpenPractice = { id -> nav.navigate("practice/$id") },
                onOpenChat = { room, title -> nav.navigate(chatRoute(room, title)) },
                onOpenTrainer = { nav.navigate("trainer") },
            )
        }
        composable("admin/course/{id}") { entry ->
            TeacherOnly(user?.isTeacher == true, onBack = dropUnlessResumed { nav.popBackStack() }) {
                TeacherCourseScreen(
                    enrollmentId = entry.arguments?.getString("id").orEmpty(),
                    repository = repository,
                    onBack = dropUnlessResumed { nav.popBackStack() },
                    onOpenChat = { room, title -> nav.navigate(chatRoute(room, title)) },
                    onEditPractice = { id -> nav.navigate("admin/practice/$id") },
                    onNewPractice = { en -> nav.navigate("admin/practice/$NEW_PRACTICE?enrollment=${en.id}&subject=${en.subject}&grade=${en.grade}") },
                    onOpenSubmission = { id -> nav.navigate("submission/$id") },
                )
            }
        }
    }
}

private const val NEW_PRACTICE = ru.prostayahimiya.app.ui.admin.NEW

/** Адрес экрана переписки. Класс и имя могут быть по-русски, поэтому кодируем их. */
private fun chatRoute(room: ChatRoom, title: String) =
    "chat/${room.kind}/${Uri.encode(room.key)}?title=${Uri.encode(title)}"

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
