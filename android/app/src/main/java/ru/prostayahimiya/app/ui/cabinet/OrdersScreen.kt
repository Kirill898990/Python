package ru.prostayahimiya.app.ui.cabinet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.prostayahimiya.app.data.Enrollment
import ru.prostayahimiya.app.data.Order
import ru.prostayahimiya.app.data.Repository
import ru.prostayahimiya.app.data.parseServerInstant
import ru.prostayahimiya.app.data.rub
import ru.prostayahimiya.app.ui.components.BackTopBar
import ru.prostayahimiya.app.ui.components.EmptyBox
import ru.prostayahimiya.app.ui.components.ErrorBox
import ru.prostayahimiya.app.ui.components.Load
import ru.prostayahimiya.app.ui.components.LoadingBox
import ru.prostayahimiya.app.ui.components.cardBorder
import ru.prostayahimiya.app.ui.components.cardColors
import ru.prostayahimiya.app.ui.components.openLink
import ru.prostayahimiya.app.ui.components.rememberLoader
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** «Мои покупки»: заказы в магазине beemei. Купить и продлить курс можно на сайте. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(repository: Repository, onBack: () -> Unit) {
    val context = LocalContext.current
    val loader = rememberLoader(Unit) { repository.myOrders() to runCatching { repository.enrollments() }.getOrDefault(emptyList()) }
    Scaffold(topBar = { BackTopBar("Мои покупки", onBack) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        PullToRefreshBox(
            isRefreshing = loader.refreshing && loader.state is Load.Ok,
            onRefresh = loader::reload,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val state = loader.state) {
                    Load.Loading -> item { LoadingBox() }
                    is Load.Failed -> item { ErrorBox(state.message, loader::reload) }
                    is Load.Ok -> {
                        val (orders, courses) = state.value
                        if (orders.isEmpty()) {
                            item { EmptyBox("🛍️", "Покупок пока нет. Курсы подготовки к ОГЭ и ЕГЭ продаются в магазине beemei на сайте.") }
                            item {
                                Button(onClick = { context.openLink(repository.shopProductUrl("")) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Открыть магазин на сайте")
                                }
                            }
                        }
                        items(orders, key = { it.id }) { order ->
                            OrderCard(order, courses, onCheck = { context.openLink(repository.siteUrl("#/shop/order/${order.id}")) }) {
                                context.openLink(repository.shopProductUrl(it))
                            }
                        }
                        if (orders.isNotEmpty()) {
                            item {
                                Text(
                                    "Чек приходит на почту или телефон, указанные при покупке. Вопросы по заказу — на почту продавца, указав номер заказа.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderCard(order: Order, courses: List<Enrollment>, onCheck: () -> Unit, onRenew: (String) -> Unit) {
    val (label, bg, fg) = when (order.status) {
        "paid" -> Triple("Оплачен", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        "pending" -> Triple("Ждёт оплаты", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        else -> Triple("Отменён", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    }
    val date = parseServerInstant(order.created)?.let {
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru")).withZone(ZoneId.systemDefault()).format(it)
    }.orEmpty()
    Card(colors = cardColors(), border = cardBorder()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Заказ от $date", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Номер заказа: ${order.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(50), color = bg) {
                    Text(label, color = fg, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            order.items.forEach {
                val course = if (order.status == "paid") courses.firstOrNull { en ->
                    en.product == it.product && en.teacher == it.teacher && en.subject == it.subject && en.exam == it.exam
                } else null
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(it.title, style = MaterialTheme.typography.bodyLarge)
                        Text(it.optionsText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        course?.let { en ->
                            Text(
                                (if (en.isLive) "Курс открыт " else "Курс ") + en.untilText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (en.isLive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Text(rub(it.price), fontWeight = FontWeight.SemiBold)
                }
            }
            if (order.discount > 0) {
                Row {
                    Text("Промокод ${order.promo}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("−" + rub(order.discount), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row {
                Text("Итого", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(rub(order.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (order.status == "pending") {
                OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) { Text("Проверить оплату на сайте") }
            }
            if (order.status == "paid" && order.items.isNotEmpty()) {
                TextButton(onClick = { onRenew(order.items.first().product) }) { Text("Продлить или купить ещё") }
            }
        }
    }
}
