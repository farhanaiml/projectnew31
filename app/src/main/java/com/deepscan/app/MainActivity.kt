package com.deepscan.app

import android.content.Context
import android.content.res.Configuration
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.util.Date

private val Background @Composable get() = MaterialTheme.colorScheme.background
private val CardBg @Composable get() = MaterialTheme.colorScheme.surface
private val Card2 @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val Muted @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val Mint @Composable get() = MaterialTheme.colorScheme.primary
private val White @Composable get() = MaterialTheme.colorScheme.onSurface
private val Blue @Composable get() = MaterialTheme.colorScheme.secondary
private val Amber @Composable get() = MaterialTheme.colorScheme.tertiary
private val Red @Composable get() = MaterialTheme.colorScheme.error

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val preferences = remember { context.getSharedPreferences("deepscan-v2-settings", Context.MODE_PRIVATE) }
            var themePreference by remember { mutableStateOf(preferences.getString("theme", "SYSTEM") ?: "SYSTEM") }
            val systemDark = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val isDark = when (themePreference) {
                "DARK" -> true
                "LIGHT" -> false
                else -> systemDark
            }
            val colors = if (isDark) darkColorScheme(
                primary = Color(0xFF62F2CF), onPrimary = Color(0xFF09111E),
                secondary = Color(0xFF79AFFC), tertiary = Color(0xFFFFC978),
                background = Color(0xFF09111E), surface = Color(0xFF142337),
                surfaceVariant = Color(0xFF1B2D43), onSurface = Color(0xFFF5FAFF),
                onSurfaceVariant = Color(0xFF9AAEC6), error = Color(0xFFFF8B8B)
            ) else lightColorScheme(
                primary = Color(0xFF006B5C), onPrimary = Color.White,
                secondary = Color(0xFF275BB4), tertiary = Color(0xFF986000),
                background = Color(0xFFF7F9FC), surface = Color.White,
                surfaceVariant = Color(0xFFE9EFF5), onSurface = Color(0xFF172437),
                onSurfaceVariant = Color(0xFF5A6674), error = Color(0xFFB3261E)
            )
            MaterialTheme(colorScheme = colors) {
                DeepScanApp(themePreference = themePreference, onThemeChange = { choice ->
                    themePreference = choice
                    preferences.edit().putString("theme", choice).apply()
                })
            }
        }
    }
}

@Composable
private fun DeepScanApp(themePreference: String, onThemeChange: (String) -> Unit, vm: ScanViewModel = viewModel()) {
    val activity = LocalContext.current as Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var permitted by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    var page by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<ScanItem?>(null) }
    var folder by remember { mutableStateOf<String?>(null) }
    var explorerMode by remember { mutableIntStateOf(0) }
    val report = vm.report
    val context = LocalContext.current
    var snapshots by remember { mutableStateOf(StorageHistoryStore.read(context)) }
    LaunchedEffect(report) {
        if (report != null && !vm.scanning && !report.truncated) {
            snapshots = StorageHistoryStore.record(context, report)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permitted = Environment.isExternalStorageManager()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permitted = Environment.isExternalStorageManager()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun askAccess() {
        val direct = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${activity.packageName}"))
        try { permissionLauncher.launch(direct) } catch (_: Exception) {
            permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    if (selected != null) {
        val item = selected!!
        FileDetailsDialog(item, onDismiss = { selected = null }, onDelete = {
            selected = null
            vm.deleteFile(item)
            folder = null
            page = 0
        })
    }
    if (vm.message != null) {
        AlertDialog(
            onDismissRequest = vm::clearMessage,
            title = { Text("DeepScan") },
            text = { Text(vm.message.orEmpty()) },
            confirmButton = { TextButton(onClick = vm::clearMessage) { Text("OK") } },
            containerColor = CardBg
        )
    }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            NavigationBar(containerColor = CardBg, tonalElevation = 0.dp) {
                listOf("Home", "Explore", "Cleanup", "Settings").forEachIndexed { i, name ->
                    val icon = listOf("⌂", "▦", "◎", "⚙")[i]
                    NavigationBarItem(
                        selected = page == i, onClick = { page = i },
                        icon = { Text(icon, fontSize = 22.sp) },
                        label = { Text(name, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Mint, selectedTextColor = Mint,
                            indicatorColor = Card2, unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BrandHeader()
            if (!permitted && page != 3) {
                AccessScreen(onRequest = { askAccess() })
            } else when (page) {
                0 -> OverviewPage(vm, report, snapshots, onExplore = { page = 1; explorerMode = 1 },
                    onDuplicates = { page = 2 }, onSelect = { selected = it })
                1 -> ExplorerPage(report, folder, onFolder = { folder = it }, explorerMode,
                    onMode = { explorerMode = it }, onSelect = { selected = it })
                2 -> DuplicatesPage(report, onSelect = { selected = it })
                else -> AboutPage(permitted, onRequest = { askAccess() }, themePreference = themePreference, onThemeChange = onThemeChange, snapshots = snapshots)
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(41.dp).clip(RoundedCornerShape(13.dp)).background(Mint),
            contentAlignment = Alignment.Center) {
            Text("D", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Background)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("DeepScan", color = White, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold)
            Text("STORAGE INTELLIGENCE", fontSize = 9.sp, color = Muted,
                letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.clip(RoundedCornerShape(50)).background(Card2)
            .padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text("ON DEVICE", fontSize = 10.sp, color = Mint, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AccessScreen(onRequest: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("◉", fontSize = 64.sp, color = Mint)
        Spacer(Modifier.height(10.dp))
        Text("See what takes up space", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("To map your folders and find large or duplicate files, DeepScan needs Android's All files access permission.",
            color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        ElevatedPanel {
            Text("WHAT YOU CONTROL", color = Mint, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            Text("✓  Scanning stays on your phone", color = White)
            Spacer(Modifier.height(9.dp))
            Text("✓  No file is deleted automatically", color = White)
            Spacer(Modifier.height(9.dp))
            Text("✓  Android-protected app data stays private", color = White)
        }
        Spacer(Modifier.height(22.dp))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp)) { Text("Grant storage access", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(10.dp))
        Text("In Settings, enable ‘Allow access to manage all files’, then return here.",
            color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun OverviewPage(
    vm: ScanViewModel, report: ScanReport?, snapshots: List<StorageSnapshot>, onExplore: () -> Unit,
    onDuplicates: () -> Unit, onSelect: (ScanItem) -> Unit
) {
    val root = remember { Environment.getExternalStorageDirectory() }
    // Recomputed on each scan result; absolute capacity may include space outside scannable folders.
    val total = remember(report) { root.totalSpace.coerceAtLeast(0) }
    val free = remember(report) { root.freeSpace.coerceAtLeast(0) }
    val used = (total - free).coerceAtLeast(0)
    LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 25.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item {
            Spacer(Modifier.height(5.dp))
            Text("Know where your\nspace disappears.", fontSize = 30.sp, lineHeight = 35.sp,
                fontWeight = FontWeight.ExtraBold, color = White)
            Spacer(Modifier.height(8.dp))
            Text("Find the exact files behind your storage usage.", color = Muted, fontSize = 13.sp)
        }
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF19384D), CardBg)))) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("DEVICE STORAGE", fontSize = 11.sp, letterSpacing = 2.sp,
                            color = Mint, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("${if (total > 0) (used * 100 / total) else 0}% FULL",
                            color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(readableSize(used), fontSize = 36.sp, color = White,
                            fontWeight = FontWeight.ExtraBold)
                        Text("  /  ${readableSize(total)}", color = Muted, fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Spacer(Modifier.height(15.dp))
                    LinearProgressIndicator(progress = { if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(12.dp)),
                        color = Mint, trackColor = Color(0xFF30485A))
                    Spacer(Modifier.height(11.dp))
                    Text("${readableSize(free)} free  ·  ${readableSize(report?.scannedBytes ?: 0)} mapped in shared files",
                        color = Muted, fontSize = 12.sp)
                }
            }
        }
        item {
            Button(onClick = vm::scan, enabled = !vm.scanning,
                modifier = Modifier.fillMaxWidth().height(53.dp),
                shape = RoundedCornerShape(16.dp)) {
                Text(if (vm.scanning) "Scanning your storage…" else if (report == null) "Start deep scan" else "Scan again",
                    fontWeight = FontWeight.Bold)
            }
            if (vm.scanning) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Mint)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = vm::stop, modifier = Modifier.fillMaxWidth()) { Text("Cancel scan") }
            }
            Spacer(Modifier.height(8.dp))
            Text(vm.progress, color = Muted, fontSize = 12.sp)
        }
        if (report != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("FILES FOUND", "${report.fileCount}", "Indexed", Modifier.weight(1f), Blue)
                    StatCard("FOLDERS", "${report.directoryCount}", "Mapped", Modifier.weight(1f), Mint)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("DUPLICATE SPACE", readableSize(report.reclaimableBytes),
                        "Possible savings", Modifier.weight(1f), Amber)
                    StatCard("RESTRICTED", "${report.skippedPaths}", "Skipped entries", Modifier.weight(1f), Red)
                }
                if (report.truncated) {
                    Spacer(Modifier.height(10.dp))
                    Text("Scan reached its safety limit of 200,000 files; some results may be missing.",
                        color = Amber, fontSize = 12.sp)
                }
            }
            item {
                ElevatedPanel {
                    Text("STORAGE X-RAY · LARGEST TOP-LEVEL FOLDERS", color = Mint,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    val big = report.folders.filter { it.parent == report.rootPath }
                        .sortedByDescending { it.bytes }.take(7)
                    big.forEach { folder ->
                        val percentage = (folder.bytes.toFloat() / report.scannedBytes.coerceAtLeast(1L)).coerceIn(0f, 1f)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(folder.name, modifier = Modifier.weight(1f), maxLines = 1,
                                overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = White)
                            Text(readableSize(folder.bytes), fontSize = 11.sp, color = Muted)
                        }
                        Spacer(Modifier.height(5.dp))
                        LinearProgressIndicator(progress = { percentage }, modifier = Modifier
                            .fillMaxWidth().height(7.dp).clip(RoundedCornerShape(6.dp)),
                            color = Blue, trackColor = Card2)
                        Spacer(Modifier.height(12.dp))
                    }
                    Text("Bars represent the proportion of mapped shared-file bytes; inaccessible space is excluded.",
                        fontSize = 10.sp, color = Muted)
                }
            }
            if (snapshots.size >= 2) item {
                val previous = snapshots[snapshots.lastIndex - 1]
                val latest = snapshots.last()
                val diff = latest.mappedBytes - previous.mappedBytes
                ElevatedPanel {
                    Text("CHANGE DETECTIVE", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(9.dp))
                    Text((if (diff > 0) "+" else "") + readableSize(diff),
                        color = if (diff > 0) Amber else Mint, fontSize = 24.sp,
                        fontWeight = FontWeight.Bold)
                    Text("Mapped shared-file change since the prior completed scan",
                        color = Muted, fontSize = 12.sp)
                }
            }
            item { SectionHeading("Heavy hitters", "Largest files", onExplore) }
            items(report.files.take(6), key = { it.path }) { file ->
                StorageRow(file = file, onClick = { onSelect(file) })
            }
            item {
                OutlinedButton(onClick = onDuplicates, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)) {
                    Text("View ${report.duplicateGroups.size} duplicate groups")
                }
                Spacer(Modifier.height(8.dp))
                Text("Mapped files aren't the same as total used storage. Private app data and system files cannot be inspected here.",
                    color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        } else if (!vm.scanning) {
            item {
                ElevatedPanel {
                    Text("WHAT DEEPSCAN FINDS", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text("01   Exact file and folder locations", color = White)
                    Spacer(Modifier.height(9.dp))
                    Text("02   Largest space-consuming items", color = White)
                    Spacer(Modifier.height(9.dp))
                    Text("03   Hash-verified duplicate files", color = White)
                    Spacer(Modifier.height(9.dp))
                    Text("04   Optional, confirmed file cleanup", color = White)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, sub: String, modifier: Modifier, accent: Color) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(CardBg).padding(15.dp)) {
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(10.dp))
        Text(value, color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun ExplorerPage(
    report: ScanReport?, currentFolder: String?, onFolder: (String?) -> Unit,
    mode: Int, onMode: (Int) -> Unit, onSelect: (ScanItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val root = report?.rootPath
    val path = if (currentFolder != null && report?.folders?.any { it.path == currentFolder } == true)
        currentFolder else root
    val visible = remember(report, path, mode, query) {
        val raw = if (report == null) emptyList() else if (mode == 1) report.files
            else report.items.filter { it.parent == path }
        raw.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .sortedWith(compareByDescending<ScanItem> { it.isDirectory && mode == 0 }.thenByDescending { it.bytes })
            .take(500)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Text("Storage explorer", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("Inspect files and their exact paths", fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == 0, onClick = { onMode(0); query = "" },
                label = { Text("Folder tree") })
            FilterChip(selected = mode == 1, onClick = { onMode(1); query = "" },
                label = { Text("Largest files") })
        }
        Spacer(Modifier.height(9.dp))
        OutlinedTextField(value = query, onValueChange = { query = it },
            label = { Text("Search ${if (mode == 0) "this folder" else "all files"}") },
            placeholder = { Text("Filename or extension") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None))
        Spacer(Modifier.height(8.dp))
        if (mode == 0 && report != null) {
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable {
                    onFolder(if (path == root) null else File(path!!).parent?.takeIf { parent ->
                        parent.startsWith(root.orEmpty()) && parent != root
                    })
                }.padding(vertical = 9.dp)) {
                Text("‹", fontSize = 25.sp, color = Mint)
                Spacer(Modifier.width(8.dp))
                Text(if (path == root) "Shared storage" else path?.removePrefix(root.orEmpty()).orEmpty(),
                    color = Mint, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("${visible.size} items displayed${if (mode == 1 && report != null && report.files.size > 500) " · top 500" else ""}",
            fontSize = 11.sp, color = Muted)
        Spacer(Modifier.height(6.dp))
        if (report == null) EmptyState("No scan results yet", "Start a scan from the Overview tab.")
        else if (visible.isEmpty()) EmptyState("Nothing found", "Try another folder or search term.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 20.dp)) {
            items(visible, key = { it.path }) { item ->
                StorageRow(item, onClick = { if (item.isDirectory) onFolder(item.path) else onSelect(item) })
            }
        }
    }
}

@Composable
private fun DuplicatesPage(report: ScanReport?, onSelect: (ScanItem) -> Unit) {
    var opened by remember(report) { mutableIntStateOf(-1) }
    val groups = report?.duplicateGroups.orEmpty()
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Cleanup preview", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Review duplicate copies before removing anything", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            ElevatedPanel {
                Text("ESTIMATED DUPLICATE SAVINGS · NO FILES DELETED", color = Muted, fontSize = 10.sp,
                    letterSpacing = 1.3.sp)
                Spacer(Modifier.height(7.dp))
                Text(readableSize(report?.reclaimableBytes ?: 0), color = Mint,
                    fontWeight = FontWeight.ExtraBold, fontSize = 33.sp)
                Text("${groups.size} duplicate groups · verify before deleting", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(9.dp))
        }
        if (report == null) item { EmptyState("Run a scan first", "Duplicate analysis runs after the file scan.") }
        else if (groups.isEmpty()) item { EmptyState("No matching files found", "No identical non-empty files were discovered in the scan.") }
        items(groups.size) { i ->
            val group = groups[i]
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg)) {
                Row(Modifier.fillMaxWidth().clickable { opened = if (opened == i) -1 else i }
                    .padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(group.copies.first().name, color = White, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${group.copies.size} copies  ·  ${readableSize(group.bytesPerFile)} each",
                            color = Muted, fontSize = 12.sp)
                        Text("Potential saving: ${readableSize(group.reclaimableBytes)}", color = Mint,
                            fontSize = 12.sp)
                    }
                    Text(if (opened == i) "⌃" else "⌄", color = Mint, fontSize = 22.sp)
                }
                if (opened == i) {
                    HorizontalDivider(color = Card2)
                    group.copies.forEach { file ->
                        Row(Modifier.fillMaxWidth().clickable { onSelect(file) }.padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("↳", color = Blue)
                            Spacer(Modifier.width(9.dp))
                            Text(file.path, color = White, fontSize = 12.sp,
                                modifier = Modifier.weight(1f), maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                            Text("DETAILS", color = Mint, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        item {
            Text("Matching content doesn't mean a file is safe to remove. Keep at least one copy and check what each app needs.",
                color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun AboutPage(permitted: Boolean, onRequest: () -> Unit, themePreference: String, onThemeChange: (String) -> Unit, snapshots: List<StorageSnapshot>) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item {
            Text("About this scan", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Transparent about what Android allows", color = Muted, fontSize = 12.sp)
        }
        item {
            ElevatedPanel {
                Text("APPEARANCE", color = Mint, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                listOf("SYSTEM" to "Follow system", "LIGHT" to "Light", "DARK" to "Dark").forEach { (choice, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onThemeChange(choice) }
                        .padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = themePreference == choice, onClick = { onThemeChange(choice) })
                        Spacer(Modifier.width(6.dp))
                        Text(label, color = White)
                    }
                }
            }
        }
        if (snapshots.isNotEmpty()) item {
            ElevatedPanel {
                Text("SCAN HISTORY", color = Mint, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                snapshots.takeLast(5).reversed().forEach { snap ->
                    Text("${java.text.DateFormat.getDateTimeInstance().format(Date(snap.capturedAt))} · ${readableSize(snap.mappedBytes)} · ${snap.fileCount} files",
                        color = White, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                }
                Text("Only summaries are stored on this device.", color = Muted, fontSize = 11.sp)
            }
        }
        item {
            ElevatedPanel {
                Text("STORAGE PERMISSION", color = Muted, fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))
                Text(if (permitted) "●  All files access enabled" else "●  Not granted",
                    color = if (permitted) Mint else Amber, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onRequest) { Text("Open access settings") }
            }
        }
        item {
            ElevatedPanel {
                Text("WHAT IS INSPECTED", color = Mint, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text("Shared storage: Downloads, DCIM, Movies, Documents, accessible hidden files, and Android/media where readable.",
                    color = White)
                Spacer(Modifier.height(15.dp))
                Text("WHAT ANDROID PROTECTS", color = Amber, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text("Other apps' private data, protected Android/data and Android/obb directories, and system partitions. Root access is not used.",
                    color = White)
            }
        }
        item {
            ElevatedPanel {
                Text("PRIVACY & FILE SAFETY", color = Mint, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text("• All scanning and hashing happen locally.\n• No sign-in, internet or server is used.\n• Nothing is deleted during scanning.\n• The Delete action permanently deletes one selected file after confirmation.\n• The app never clears app caches or system data.",
                    color = White, lineHeight = 22.sp)
            }
        }
        item { Text("DeepScan · Android 11+ · Personal testing build", color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun ElevatedPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardBg).padding(18.dp),
        content = content)
}

@Composable
private fun SectionHeading(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
            modifier = Modifier.weight(1f))
        Text(action + "  ›", color = Mint, fontSize = 12.sp,
            modifier = Modifier.clickable(onClick = onClick).padding(5.dp))
    }
}

@Composable
private fun StorageRow(file: ScanItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg)
        .clickable(onClick = onClick).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Card2),
            contentAlignment = Alignment.Center) {
            Text(if (file.isDirectory) "▣" else when (file.name.substringAfterLast('.', "").lowercase()) {
                "mp4", "mkv", "mov", "avi" -> "▶"
                "jpg", "jpeg", "png", "webp", "heic" -> "▧"
                "pdf", "doc", "docx", "txt" -> "≡"
                else -> "◇"
            }, color = if (file.isDirectory) Mint else Blue, fontSize = 24.sp)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(file.path, color = Muted, fontSize = 10.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(readableSize(file.bytes), color = Mint, fontSize = 12.sp,
                fontWeight = FontWeight.Bold)
            Text(if (file.isDirectory) "Open ›" else "Details ›", color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("◌", color = Mint, fontSize = 50.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, fontWeight = FontWeight.Bold, color = White)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, color = Muted, textAlign = TextAlign.Center, fontSize = 12.sp)
    }
}

@Composable
private fun FileDetailsDialog(item: ScanItem, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("FILE SIZE", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(readableSize(item.bytes), fontWeight = FontWeight.Bold)
                Text("EXACT LOCATION", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(item.path, color = White, fontSize = 12.sp)
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(item.path))
                    Toast.makeText(context, "Path copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy full path") }
                if (item.modified > 0) {
                    Text("Modified: ${java.text.DateFormat.getDateTimeInstance().format(Date(item.modified))}",
                        fontSize = 12.sp, color = Muted)
                }
                Text("Deletion is permanent and cannot be undone. Only delete files you recognize and have backed up.",
                    fontSize = 11.sp, color = Amber)
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) { Text("Delete permanently", color = Red) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    try {
                        val target = File(item.path)
                        val uri = FileProvider.getUriForFile(context,
                            "${context.packageName}.fileprovider", target)
                        val ext = target.extension.lowercase()
                        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "No compatible viewer found", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Open") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
