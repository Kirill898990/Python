package ru.prostayahimiya.app.ui.components

import android.content.ClipboardManager as AndroidClipboardManager
import android.content.Context
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString

/*
 * Защита заданий от списывания (только для учеников):
 *  - внутри блока нельзя скопировать, вырезать и вставить текст, меню «Копировать / Вставить» не появляется;
 *  - на экране запрещены скриншоты и запись экрана, поэтому текст нельзя «вытащить» и через Google Lens;
 *  - если в поле ответа разом появился большой кусок текста (так клавиатура вставляет из буфера обмена),
 *    такое изменение отклоняется.
 */

/** Сколько символов за одно изменение считается вставкой, а не набором с клавиатуры. */
const val PASTE_LIMIT_TEXT = 30
const val PASTE_LIMIT_FORMULA = 6

/** Похоже ли изменение текста на вставку: за один раз добавилось больше [limit] символов. */
fun looksLikePaste(old: String, new: String, limit: Int): Boolean = new.length - old.length > limit

fun Context.showPasteBlocked() {
    Toast.makeText(this, "Вставлять текст нельзя — напиши ответ сам", Toast.LENGTH_SHORT).show()
}

/** Если [enabled], содержимое защищено от копирования и вставки, а экран — от скриншотов. */
@Composable
fun ProtectedFromCopy(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val context = LocalContext.current
    val clipboard = remember(context) {
        BlockedClipboard(context.getSystemService(Context.CLIPBOARD_SERVICE) as AndroidClipboardManager)
    }
    SecureWindow()
    @Suppress("DEPRECATION")
    CompositionLocalProvider(
        LocalTextToolbar provides EmptyTextToolbar,
        LocalClipboard provides clipboard,
        LocalClipboardManager provides BlockedClipboardManager,
    ) {
        // Новое текстовое меню Compose: убираем из него все пункты.
        Box(Modifier.filterTextContextMenuComponents { false }) {
            content()
        }
    }
}

/** Запрещает скриншоты и запись экрана, пока экран открыт. */
@Composable
private fun SecureWindow() {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/** Меню над выделенным текстом, которое никогда не показывается. */
private object EmptyTextToolbar : TextToolbar {
    override val status: TextToolbarStatus get() = TextToolbarStatus.Hidden

    override fun hide() = Unit

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = Unit
}

/** Буфер обмена, из которого ничего не вставляется и в который ничего не копируется. */
private class BlockedClipboard(override val nativeClipboard: AndroidClipboardManager) : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? = null

    override suspend fun setClipEntry(clipEntry: ClipEntry?) = Unit
}

@Suppress("DEPRECATION")
private object BlockedClipboardManager : ClipboardManager {
    override fun getText(): AnnotatedString? = null

    override fun setText(annotatedString: AnnotatedString) = Unit

    override fun hasText(): Boolean = false
}
