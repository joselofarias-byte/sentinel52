package com.fiftytwo.sentinel.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fiftytwo.sentinel.core.BackendKind
import com.fiftytwo.sentinel.core.BackendProbe
import com.fiftytwo.sentinel.core.EventKind
import com.fiftytwo.sentinel.core.PrivilegedState
import com.fiftytwo.sentinel.core.PrivilegedStatus
import com.fiftytwo.sentinel.core.SentinelEvent
import com.fiftytwo.sentinel.core.UninstallPlanner
import com.fiftytwo.sentinel.core.WatchRule
import com.fiftytwo.sentinel.data.SentinelState
import com.fiftytwo.sentinel.data.UninstallStats
import com.fiftytwo.sentinel.privileged.PackageOps
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CARD_SHAPE = RoundedCornerShape(18.dp)
private val PAGE_PADDING = 16.dp

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val privileged by viewModel.privileged.collectAsState()
    val batteryExempt by viewModel.batteryExempt.collectAsState()
    val apps by viewModel.apps.collectAsState()
    val appsLoading by viewModel.appsLoading.collectAsState()

    var ruleInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<WatchRule?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showProtectList by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(AppScreen.Main) }

    // Android 13 起没有通知权限时不显示前台服务通知，这里一并申请
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(showPicker) {
        if (showPicker) viewModel.loadApps()
    }

    // Paquete → 应用名。Registro和统计上把Paquete配成人看得懂的名字
    val labels = remember(state.rules) { state.rules.associate { it.pattern to it.label } }

    // 系统返回键：在Registro页要退回主页面，而不是直接退出应用
    BackHandler(enabled = screen == AppScreen.Log) { screen = AppScreen.Main }

    if (screen == AppScreen.Log) {
        LogPage(
            events = state.events,
            labels = labels,
            onBack = { screen = AppScreen.Main },
            onClear = { viewModel.clearLog() },
        )
        return
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PAGE_PADDING, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header()
            BackendCard(
                status = privileged,
                onRequest = { viewModel.requestPermission(it) },
                onOpenApp = { viewModel.openManager(it) },
                onRefresh = { viewModel.refreshPrivileged() },
                onHelp = { showHelp = true },
            )
            MonitorCard(
                state = state,
                backendReady = privileged?.state == PrivilegedState.READY,
                batteryExempt = batteryExempt,
                onToggle = { viewModel.setMonitoring(it) },
                onCheckNow = { viewModel.checkNow() },
                onDryRun = { viewModel.setDryRun(it) },
                onAllUsers = { viewModel.setAllUsers(it) },
                onInterval = { viewModel.setIntervalMs(it) },
                onRequestBattery = { viewModel.requestIgnoreBattery() },
            )
            StatsCard(
                stats = state.stats,
                logCount = state.events.size,
                labels = labels,
                onOpenLog = { screen = AppScreen.Log },
            )
            RulesCard(
                rules = state.rules,
                input = ruleInput,
                onInputChange = { ruleInput = it },
                name = nameInput,
                onNameChange = { nameInput = it },
                onAdd = {
                    val problem = viewModel.addRule(ruleInput, nameInput)
                    if (problem == null) {
                        notice = null
                        ruleInput = ""
                        nameInput = ""
                    } else {
                        notice = problem
                    }
                },
                onPick = { showPicker = true },
                onToggleRule = { pattern, enabled -> viewModel.setRuleEnabled(pattern, enabled) },
                onEdit = { editing = it },
                onShowProtect = { showProtectList = true },
                notice = notice,
            )

            Text(
                text = "La desinstalación se ejecuta primero con un backend autorizado: Root (su, uid 0) → Dhizuku (propietario del dispositivo)" +
                    "→ Stellar (identidad Shell). Se vigila por sondeo periódico y eventos de instalación; al coincidir una regla, se desinstala.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
        }
    }

    if (showHelp) {
        HelpDialog(onDismiss = { showHelp = false })
    }

    if (showProtectList) {
        AlertDialog(
            onDismissRequest = { showProtectList = false },
            confirmButton = { TextButton(onClick = { showProtectList = false }) { Text("Cerrar") } },
            title = { Text("Lista de protección integrada") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Los siguientes paquetes nunca se desinstalan aunque coincidan con una regla, para evitar eliminar por error componentes del sistema o privilegiados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    UninstallPlanner.PROTECTED.forEach { pkg ->
                        Text(
                            text = pkg,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            },
        )
    }

    if (showPicker) {
        AppPickerDialog(
            apps = apps,
            loading = appsLoading,
            existing = state.rules.map { it.pattern }.toSet(),
            onPick = { pattern, label ->
                val problem = viewModel.addRule(pattern, label)
                notice = problem
                if (problem == null) showPicker = false
            },
            onRefresh = { viewModel.loadApps(force = true) },
            onDismiss = { showPicker = false },
        )
    }

    // Editar已有规则（改Paquete / 改应用名）。校验与去重走的是和新增同一条路
    editing?.let { rule ->
        RuleEditorDialog(
            rule = rule,
            onSave = { pattern, label -> viewModel.updateRule(rule.pattern, pattern, label) },
            onDelete = {
                viewModel.removeRule(rule.pattern)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

// ---------------------------------------------------------------- 通用积木

@Composable
private fun Header() {
    Column(modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)) {
        Text(
            text = "Sentinela de paquetes",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Desinstala automáticamente una aplicación en cuanto se instala si su paquete está vigilado",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 卡片描边：浅色模式把边界交代清楚，深色模式收着一点，避免勾得太硬。 */
@Composable
private fun cardBorder(): BorderStroke {
    val cs = MaterialTheme.colorScheme
    val lightMode = cs.background.luminance() > 0.5f
    return BorderStroke(1.dp, cs.outline.copy(alpha = if (lightMode) 1f else 0.5f))
}

/** 开关配色：Material 默认的Cerrar态轨道在浅色下几乎融进卡片，这里给它描一道边。 */
@Composable
private fun switchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
)

/** 分区卡片：标题前一颗色点（与下面各行的点同一列）+ 标题/副标题 + 右侧挂件。 */
@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        shape = CARD_SHAPE,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = cardBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(9.dp).background(accent, CircleShape))
                Spacer(modifier = Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (trailing != null) trailing()
            }
            content()
        }
    }
}

/** 状态药丸：小圆点 + 文本。底色透明度按明暗分工，浅色下压淡一点、深色下抬亮一点。 */
@Composable
private fun StatusPill(text: String, tint: Color, dot: Boolean = true) {
    val lightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
    Surface(shape = RoundedCornerShape(50), color = tint.copy(alpha = if (lightMode) 0.14f else 0.22f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
        ) {
            if (dot) {
                Box(modifier = Modifier.size(6.dp).background(tint, CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = tint,
            )
        }
    }
}

/** 小标签，用在标题行里（例如「En uso」）。 */
@Composable
private fun TagChip(text: String, tint: Color) {
    Surface(shape = RoundedCornerShape(50), color = tint.copy(alpha = 0.14f)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** 一行设置项：左标题+说明，右挂件。 */
@Composable
private fun InfoRow(
    title: String,
    desc: String,
    descTint: Color? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = descTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

/** 卡片内的浅色分隔线。 */
@Composable
private fun Hairline() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
}

/** 空状态 / 提示块。 */
@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 错误 / 警告条。 */
@Composable
private fun WarnBanner(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = "!",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** 选项卡：间隔之类的单选。 */
@Composable
private fun ChoiceChip(selected: Boolean, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) cs.primary.copy(alpha = 0.18f) else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (selected) cs.primary.copy(alpha = 0.8f) else cs.outline.copy(alpha = 0.7f),
        ),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) cs.primary else cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

// ---------------------------------------------------------------- Backend privilegiado

@Composable
private fun BackendCard(
    status: PrivilegedStatus?,
    onRequest: (BackendKind) -> Unit,
    onOpenApp: (BackendKind) -> Unit,
    onRefresh: () -> Unit,
    onHelp: () -> Unit,
) {
    val active = status?.kind
    val ready = status?.state == PrivilegedState.READY

    SectionCard(
        title = "Backend privilegiado",
        subtitle = status?.let { backendSummary(it) } ?: "Detectando Root / Dhizuku / Stellar…",
        accent = if (ready) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
        trailing = {
            StatusPill(
                text = when {
                    status == null -> "Detectando"
                    ready -> "Listo"
                    status.kind == null -> "Ninguno instalado"
                    else -> "${status.kindLabel} no está listo"
                },
                tint = when {
                    status == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    ready -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.error
                },
            )
        },
    ) {
        val probes = status?.probes.orEmpty()
        if (probes.isEmpty()) {
            EmptyHint("Aún no hay resultados. Pulsa «Volver a detectar».")
        }

        probes.forEachIndexed { index, probe ->
            if (index > 0) Hairline()
            BackendRow(
                probe = probe,
                active = probe.kind == active,
                onRequest = { onRequest(probe.kind) },
                onOpenApp = { onOpenApp(probe.kind) },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = onRefresh, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Volver a detectar")
            }
            TextButton(onClick = onHelp, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                Text("Cómo configurarlo")
            }
        }
    }
}

@Composable
private fun BackendRow(
    probe: BackendProbe,
    active: Boolean,
    onRequest: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val tint = when (probe.state) {
        PrivilegedState.READY -> cs.secondary
        PrivilegedState.NO_PERMISSION -> cs.primary
        PrivilegedState.NOT_INSTALLED, PrivilegedState.NOT_RUNNING -> cs.error
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(9.dp).background(tint, CircleShape))
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = probe.kind.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${backendRole(probe.kind)} · ${backendHint(probe)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (probe.state == PrivilegedState.READY) cs.onSurfaceVariant else tint,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        when {
            // Listo的行只在「正在用哪一个」上给一颗实心标签，避免每行都写一遍「Listo」
            probe.state == PrivilegedState.READY && active -> StatusPill("En uso", cs.primary, dot = false)
            probe.state == PrivilegedState.READY -> StatusPill("Listo", cs.secondary, dot = false)

            probe.state == PrivilegedState.NO_PERMISSION ->
                Button(
                    onClick = onRequest,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(if (probe.kind == BackendKind.ROOT) "Solicitar permiso Root" else "Solicitar permiso")
                }

            // Root 没有可打开的管理器界面：su 授权框由 su 管理器自己弹出，因此这一行不给按钮
            probe.kind != BackendKind.ROOT ->
                OutlinedButton(
                    onClick = onOpenApp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("Abrir gestor")
                }
        }
    }
}

/** 卡片副标题：优先说明En uso的后端，没有可用后端时说明缺什么。 */
private fun backendSummary(status: PrivilegedStatus): String = when {
    status.state == PrivilegedState.READY && status.kind == BackendKind.ROOT ->
        "Las desinstalaciones se ejecutan con Root (su, uid 0): puede quitar apps del sistema y no necesita un servicio residente."

    status.state == PrivilegedState.READY && status.kind == BackendKind.DHIZUKU ->
        "Dhizuku tiene permisos de propietario del dispositivo y ejecuta la desinstalación en su propio proceso; es silenciosa y no requiere volver a autorizar tras reiniciar."

    status.state == PrivilegedState.READY ->
        "Stellar Listo（身份：${status.uidMode}），可执行卸载。"

    status.kind == null ->
        "未检测到 Root、Dhizuku 或 Stellar。请任选其一准备Listo：Root（Magisk / KernelSU 等）、" +
            "Dhizuku (debe ser propietario del dispositivo) o Stellar (debe tener el servicio iniciado)."

    else ->
        "优先使用 ${status.kind.label}，但该后端尚未Listo，详见下列各项状态。"
}

private fun backendRole(kind: BackendKind): String = when (kind) {
    BackendKind.ROOT -> "Superusuario"
    BackendKind.DHIZUKU -> "Propietario del dispositivo"
    BackendKind.STELLAR -> "Identidad Shell"
}

private fun backendHint(probe: BackendProbe): String = when (probe.state) {
    PrivilegedState.READY -> when (probe.kind) {
        BackendKind.ROOT -> "Autorizado (uid 0)"
        BackendKind.DHIZUKU -> "Autorizado; sigue válido tras reiniciar"
        BackendKind.STELLAR -> "Autorizado; servicio en ejecución"
    }

    PrivilegedState.NOT_INSTALLED, PrivilegedState.NOT_RUNNING ->
        when (probe.kind) {
            BackendKind.ROOT -> "No se detectó su (Magisk / KernelSU / APatch no instalado)"
            BackendKind.DHIZUKU -> "尚未设为Propietario del dispositivo"
            BackendKind.STELLAR -> "El servicio no está ejecutándose"
        }

    PrivilegedState.NO_PERMISSION ->
        if (probe.kind == BackendKind.ROOT) "Pendiente de autorización: pulsa el botón y permite el acceso en el gestor de su" else "Pendiente de autorización"
}

// ---------------------------------------------------------------- Vigilancia

@Composable
private fun MonitorCard(
    state: SentinelState,
    backendReady: Boolean,
    batteryExempt: Boolean,
    onToggle: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onDryRun: (Boolean) -> Unit,
    onAllUsers: (Boolean) -> Unit,
    onInterval: (Long) -> Unit,
    onRequestBattery: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    SectionCard(
        title = "Vigilancia",
        subtitle = when {
            !state.monitoring -> "Detenida"
            state.dryRun -> "Activa · solo registrar"
            !backendReady -> "运行中 · 无可用Backend privilegiado"
            else -> "Activa · desinstalar al coincidir"
        },
        accent = if (state.monitoring) cs.secondary else cs.onSurfaceVariant,
        trailing = { Switch(checked = state.monitoring, onCheckedChange = onToggle, colors = switchColors()) },
    ) {
        if (state.monitoring && !backendReady) {
            WarnBanner(
                "当前没有可用的Backend privilegiado：即使Coincidencias规则也不会执行卸载，仅在Registro中记录，并发送一次通知提醒。",
            )
        }

        InfoRow(
            title = "Solo registrar (modo prueba)",
            desc = "Coincidencias时只写入Registro、不执行卸载；确认名单无误后再Cerrar。",
            trailing = { Switch(checked = state.dryRun, onCheckedChange = onDryRun, colors = switchColors()) },
        )
        Hairline()
        InfoRow(
            title = "Desinstalar para todos los usuarios",
            desc = "Desinstala también la misma app de otros usuarios y perfiles de trabajo (DELETE_ALL_USERS).",
            trailing = { Switch(checked = state.allUsers, onCheckedChange = onAllUsers, colors = switchColors()) },
        )
        Hairline()
        InfoRow(
            title = "Ignorar optimización de batería",
            desc = if (batteryExempt) {
                "Añadido a la lista permitida; el sondeo en segundo plano no queda limitado por Doze."
            } else {
                "No está en la lista permitida: Android puede limitar la actividad en segundo plano y alargar el intervalo."
            },
            descTint = if (batteryExempt) null else cs.error,
            trailing = {
                if (batteryExempt) {
                    StatusPill("Añadido", cs.secondary, dot = false)
                } else {
                    OutlinedButton(
                        onClick = onRequestBattery,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text("Añadir")
                    }
                }
            },
        )
        Hairline()

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Intervalo de comprobación", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    text = "${state.intervalMs / 1000} s",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.primary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1_000L, 3_000L, 5_000L, 10_000L, 30_000L).forEach { ms ->
                    ChoiceChip(
                        selected = state.intervalMs == ms,
                        label = "${ms / 1000} s",
                        onClick = { onInterval(ms) },
                    )
                }
            }
        }
        Hairline()

        Button(onClick = onCheckNow, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.monitoring) "Comprobar ahora" else "开始Vigilancia并检查一次")
        }
    }
}

// ---------------------------------------------------------------- Vigilancia名单

@Composable
private fun RulesCard(
    rules: List<WatchRule>,
    input: String,
    onInputChange: (String) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    onAdd: () -> Unit,
    onPick: () -> Unit,
    onToggleRule: (String, Boolean) -> Unit,
    onEdit: (WatchRule) -> Unit,
    onShowProtect: () -> Unit,
    notice: String?,
) {
    val cs = MaterialTheme.colorScheme
    SectionCard(
        title = "Vigilancia名单",
        subtitle = "${rules.count { it.enabled }} / ${rules.size} reglas activadas",
        trailing = {
            TextButton(onClick = onShowProtect, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                Text("Lista protegida")
            }
        },
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            label = { Text("Paquete, por ejemplo com.example.app") },
            supportingText = { Text("Admite comodín final: com.tencent.* solo coincide con sus subpaquetes") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Nombre de la app (opcional)") },
            supportingText = { Text("Solo afecta a la visualización; al elegir una app instalada se completa automáticamente") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (notice != null) {
            Text(
                text = notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onAdd,
                enabled = input.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text("Añadir")
            }
            OutlinedButton(onClick = onPick, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Elegir app instalada")
            }
        }

        if (rules.isEmpty()) {
            EmptyHint("名单为空。AñadirPaquete并开启Vigilancia后，该应用被安装时将自动卸载。")
        }

        rules.forEachIndexed { index, rule ->
            if (index > 0) Hairline()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    if (rule.label.isNotEmpty()) {
                        // 配了应用名就把名字当主标题，Paquete退到第二行小字
                        Text(
                            text = rule.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (rule.enabled) cs.onSurface else cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = rule.pattern,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            text = rule.pattern,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = if (rule.enabled) cs.onSurface else cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (UninstallPlanner.isProtected(rule.pattern)) {
                        Text(
                            text = "位于Lista protegida，不会被执行卸载",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.error,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = rule.enabled, onCheckedChange = { onToggleRule(rule.pattern, it) }, colors = switchColors())
                TextButton(
                    onClick = { onEdit(rule) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text("Editar")
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Estadísticas de desinstalación

@Composable
private fun StatsCard(stats: UninstallStats, logCount: Int, labels: Map<String, String>, onOpenLog: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    SectionCard(
        title = "Estadísticas de desinstalación",
        subtitle = when {
            stats.total == 0 -> "Todavía no se realizó ninguna desinstalación"
            stats.lastAt > 0 -> {
                val who = labels[stats.lastPackage]?.takeIf { it.isNotEmpty() } ?: stats.lastPackage
                "Última: ${shortTime(stats.lastAt)} · $who"
            }
            else -> "Total desinstalado: ${stats.total}"
        },
        accent = cs.secondary,
        trailing = {
            if (stats.failed > 0) StatusPill("Fallos ${stats.failed}", cs.error)
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            StatCell(stats.total.toString(), "Total", cs.secondary, Modifier.weight(1f))
            StatDivider()
            StatCell(stats.today.toString(), "Hoy", cs.primary, Modifier.weight(1f))
            StatDivider()
            StatCell(stats.session.toString(), "本次Vigilancia", cs.onSurface, Modifier.weight(1f))
            StatDivider()
            StatCell(
                value = stats.failed.toString(),
                label = "Fallos",
                tint = if (stats.failed > 0) cs.error else cs.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }

        Hairline()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenLog() }
                .padding(vertical = 4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ver registro",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (logCount == 0) "Sin registros" else "$logCount entradas",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = tint,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .size(width = 1.dp, height = 26.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

private fun shortTime(at: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(at))

// ---------------------------------------------------------------- Registro页

@Composable
private fun LogPage(
    events: List<SentinelEvent>,
    labels: Map<String, String>,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.US) }
    var filter by remember { mutableStateOf(LogFilter.ALL) }
    val shown = remember(events, filter) { events.asReversed().filter { filter.match(it.kind) } }
    val counts = remember(events) {
        LogFilter.entries.associateWith { option -> events.count { option.match(it.kind) } }
    }

    Scaffold(containerColor = cs.background) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PAGE_PADDING, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onBack,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    Text("‹ Volver")
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onClear,
                    enabled = events.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Vaciar")
                }
            }

            Column {
                Text(
                    text = "Registro",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (filter == LogFilter.ALL) {
                        "${events.size} entradas"
                    } else {
                        "${events.size} entradas · 当前筛选 ${shown.size} 条"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                LogFilter.entries.forEach { option ->
                    ChoiceChip(
                        selected = filter == option,
                        label = "${option.label} ${counts[option] ?: 0}",
                        onClick = { filter = option },
                    )
                }
            }

            Card(
                shape = CARD_SHAPE,
                colors = CardDefaults.cardColors(containerColor = cs.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = cardBorder(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (shown.isEmpty()) {
                        EmptyHint(if (events.isEmpty()) "Sin registros。" else "No hay registros con el filtro actual.")
                    }
                    shown.forEachIndexed { index, event ->
                        if (index > 0) Hairline()
                        LogRow(
                            event = event,
                            time = formatter.format(Date(event.at)),
                            label = labels[event.packageName],
                        )
                    }
                }
            }
        }
    }
}

/** Registro筛选分组。 */
private enum class LogFilter(val label: String) {
    ALL("Todos"),
    UNINSTALLED("Desinstaladas"),
    DETECTED("Coincidencias"),
    FAILED("Fallos"),
    OTHER("Otros");

    fun match(kind: EventKind): Boolean = when (this) {
        ALL -> true
        UNINSTALLED -> kind == EventKind.UNINSTALLED
        DETECTED -> kind == EventKind.DETECTED
        FAILED -> kind == EventKind.FAILED || kind == EventKind.ERROR
        OTHER -> kind == EventKind.SKIPPED || kind == EventKind.INFO
    }
}

/** 应用内的页面。目前两个，不引 Navigation 库。 */
private enum class AppScreen { Main, Log }

@Composable
private fun LogRow(event: SentinelEvent, time: String, label: String? = null) {
    val tint = kindColor(event.kind)
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(tint.copy(alpha = 0.16f), RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = kindGlyph(event.kind), fontSize = 12.sp, color = tint)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (event.packageName.isNotEmpty()) {
                    // 规则里配了应用名就一起显示：名字用正文字体，Paquete用等宽小字
                    Text(
                        text = if (label.isNullOrBlank()) {
                            AnnotatedString(event.packageName)
                        } else {
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontFamily = FontFamily.Default, color = MaterialTheme.colorScheme.onSurface)) {
                                    append(label)
                                }
                                append("  ")
                                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = tint)) {
                                    append(event.packageName)
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            if (event.detail.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = event.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun kindColor(kind: EventKind): Color = when (kind) {
    EventKind.UNINSTALLED -> MaterialTheme.colorScheme.secondary
    EventKind.FAILED, EventKind.ERROR -> MaterialTheme.colorScheme.error
    EventKind.DETECTED -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurface
}

private fun kindGlyph(kind: EventKind): String = when (kind) {
    EventKind.DETECTED -> "◉"
    EventKind.UNINSTALLED -> "✔"
    EventKind.FAILED -> "✘"
    EventKind.SKIPPED -> "»"
    EventKind.INFO -> "·"
    EventKind.ERROR -> "!"
}

// ---------------------------------------------------------------- 弹窗

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        title = { Text("Backend privilegiadoCómo configurarlo") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HelpSection(
                    heading = "1. Root (preferido)",
                    lines = listOf(
                        "1. El dispositivo debe tener acceso root (Magisk / KernelSU / APatch, etc.).",
                        "2. 返回本应用点击「Solicitar permiso Root」，并在 su 管理器弹窗中允许。",
                        "3. La autorización se guarda. Si la revocas en el gestor de su, la app volverá automáticamente al estado sin permiso.",
                        "uid 0 tiene los máximos privilegios y puede desinstalar apps del sistema.",
                    ),
                )
                HelpSection(
                    heading = "二、Dhizuku（Propietario del dispositivo，无需 root）",
                    lines = listOf(
                        "1. Instala Dhizuku (paquete com.rosan.dhizuku).",
                        "2. 通过 adb 将其设为Propietario del dispositivo：",
                        "adb shell dpm set-device-owner com.rosan.dhizuku/.server.DhizukuDAReceiver",
                        "（要求设备上未登录任何账号；设置成功后 Dhizuku 即为Propietario del dispositivo）",
                        "3. 返回本应用点击「Solicitar permiso」，并在 Dhizuku 弹窗中允许。",
                        "Este método no depende de un servicio residente y sigue funcionando tras reiniciar sin volver a autorizar.",
                    ),
                )
                HelpSection(
                    heading = "三、Stellar（Identidad Shell）",
                    lines = listOf(
                        "1. Instala el gestor Stellar (paquete roro.stellar.manager).",
                        "2. Ábrelo e inicia el servicio siguiendo las instrucciones. En Android 11+ puedes emparejar por «Depuración inalámbrica» o usar un PC.",
                        "3. 返回本应用点击「Solicitar permiso」，选择「始终允许」。",
                        "Tras reiniciar el servicio hay que autorizar de nuevo. Usa identidad ADB / Shell (uid 2000): puede desinstalar apps normales, no apps del sistema.",
                    ),
                )
                HelpSection(
                    heading = "Orden de selección",
                    lines = listOf("Si los tres están disponibles, se elige Root → Dhizuku → Stellar."),
                )
            }
        },
    )
}

@Composable
private fun HelpSection(heading: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Editar一条规则：改Paquete、改应用名，或直接删掉。校验与去重跟新增走同一条路。 */
@Composable
private fun RuleEditorDialog(
    rule: WatchRule,
    onSave: (pattern: String, label: String) -> String?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pattern by remember { mutableStateOf(rule.pattern) }
    var label by remember { mutableStateOf(rule.label) }
    var problem by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar规则") },
        confirmButton = {
            TextButton(
                onClick = {
                    val error = onSave(pattern, label)
                    if (error == null) onDismiss() else problem = error
                },
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text("Eliminar regla", color = MaterialTheme.colorScheme.error)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = {
                        pattern = it
                        problem = null
                    },
                    label = { Text("Paquete") },
                    supportingText = { Text("Admite comodín final, por ejemplo com.tencent.*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nombre de la app (opcional)") },
                    supportingText = { Text("Solo afecta a la visualización") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                problem?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

@Composable
private fun AppPickerDialog(
    apps: List<PackageOps.AppInfo>,
    loading: Boolean,
    existing: Set<String>,
    onPick: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val q = query.trim().lowercase(Locale.US)
        if (q.isEmpty()) {
            apps
        } else {
            apps.filter { it.packageName.lowercase(Locale.US).contains(q) || it.label.lowercase(Locale.US).contains(q) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        dismissButton = { TextButton(onClick = onRefresh) { Text("Actualizar") } },
        title = { Text("选择要Vigilancia的应用") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索应用名或Paquete") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = if (loading) "Leyendo aplicaciones instaladas…" else "共 ${filtered.size} 个应用，点击「Añadir」加入名单",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    filtered.forEachIndexed { index, app ->
                        val added = app.packageName in existing
                        if (index > 0) Hairline()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !added) { onPick(app.packageName, app.label) }
                                .padding(vertical = 8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (added) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Text(
                                    text = app.packageName + if (app.isSystem) "  ·  app del sistema" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (added) {
                                TagChip("Ya está en la lista", MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                TextButton(
                                    onClick = { onPick(app.packageName, app.label) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text("Añadir")
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}
