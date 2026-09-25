package com.jitendermehra.sabkit

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

// ==========================================
// 1. MAIN ACTIVITY & NAVIGATION
// ==========================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SabKitApp()
                }
            }
        }
    }
}

@Composable
fun SabKitApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "dashboard") {
        composable("dashboard") { DashboardScreen(navController) }
        composable("zip_viewer") { ZipViewerScreen(navController) }
        composable("keystore") { KeystoreScreen(navController) }
        composable("apk_inspector") { ApkInspectorScreen(navController) }
        composable("admob_hub") { AdMobHubScreen(navController) }
    }
}

// ==========================================
// 2. REUSABLE UI COMPONENTS (Branding)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SabKitTopBar(title: String, showBackButton: Boolean = false, navController: NavController? = null) {
    TopAppBar(
        title = {
            Column {
                Text(text = title, fontWeight = FontWeight.Bold)
                Text(
                    text = "SabKit - Developed by Jitender Mehra",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = { navController?.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

// ==========================================
// 3. DASHBOARD SCREEN (4 Fully Working Features)
// ==========================================
data class DashboardItem(val title: String, val icon: ImageVector, val route: String)

@Composable
fun DashboardScreen(navController: NavController) {
    val features = listOf(
        DashboardItem("ZIP & Source Code Viewer", Icons.Default.Folder, "zip_viewer"),
        DashboardItem("Keystore & SHA Generator", Icons.Default.Lock, "keystore"),
        DashboardItem("APK & AAB Inspector", Icons.Default.Build, "apk_inspector"),
        DashboardItem("AdMob & Developer Hub", Icons.Default.MonetizationOn, "admob_hub")
    )

    Scaffold(
        topBar = { SabKitTopBar(title = "Dashboard") }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            items(features) { feature ->
                FeatureCard(feature) {
                    navController.navigate(feature.route)
                }
            }
        }
    }
}

@Composable
fun FeatureCard(item: DashboardItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = item.title,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ==========================================
// 4. ZIP & SOURCE CODE VIEWER
// ==========================================
data class ZipEntryItem(val name: String, val size: Long, val isDirectory: Boolean)

@Composable
fun ZipViewerScreen(navController: NavController) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileList by remember { mutableStateOf<List<ZipEntryItem>>(emptyList()) }
    var selectedFileContent by remember { mutableStateOf<String?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("Select a .zip file from your device to inspect.") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedUri = it
            selectedFileContent = null
            fileList = readZipEntries(context, it)
            statusMessage = "Found ${fileList.size} items in archive."
        }
    }

    Scaffold(
        topBar = { SabKitTopBar(title = "ZIP & Source Code Viewer", showBackButton = true, navController) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Button(
                onClick = { filePicker.launch("*/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open ZIP Archive File")
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(text = statusMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(12.dp))

            if (selectedFileContent != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = selectedFileName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            IconButton(onClick = { selectedFileContent = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Close View")
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = selectedFileContent ?: "",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            } else if (fileList.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(fileList) { entry ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!entry.isDirectory && selectedUri != null) {
                                        selectedFileName = entry.name
                                        selectedFileContent = readZipFileContent(context, selectedUri!!, entry.name)
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(text = entry.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                                    if (!entry.isDirectory) {
                                        Text(text = "${entry.size} bytes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun readZipEntries(context: Context, uri: Uri): List<ZipEntryItem> {
    val list = mutableListOf<ZipEntryItem>()
    try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return list
        val zipStream = ZipInputStream(inputStream)
        var entry: ZipEntry? = zipStream.nextEntry
        while (entry != null) {
            list.add(ZipEntryItem(entry.name, entry.size, entry.isDirectory))
            zipStream.closeEntry()
            entry = zipStream.nextEntry
        }
        zipStream.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun readZipFileContent(context: Context, uri: Uri, entryName: String): String {
    try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return "Unable to open archive."
        val zipStream = ZipInputStream(inputStream)
        var entry: ZipEntry? = zipStream.nextEntry
        while (entry != null) {
            if (entry.name == entryName) {
                val reader = BufferedReader(InputStreamReader(zipStream))
                val sb = StringBuilder()
                var line: String? = reader.readLine()
                var lineCount = 0
                while (line != null && lineCount < 1000) {
                    sb.append(line).append("\n")
                    line = reader.readLine()
                    lineCount++
                }
                zipStream.close()
                return if (sb.isEmpty()) "[Binary or Empty File]" else sb.toString()
            }
            zipStream.closeEntry()
            entry = zipStream.nextEntry
        }
        zipStream.close()
    } catch (e: Exception) {
        return "Error reading file: ${e.localizedMessage}"
    }
    return "File not found in archive."
}

// ==========================================
// 5. KEYSTORE GENERATOR STATE & VIEWMODEL
// ==========================================
data class KeystoreState(
    val alias: String = "SabKitAlias",
    val sha1: String = "",
    val sha256: String = "",
    val message: String = ""
)

class KeystoreViewModel : ViewModel() {
    private val _state = MutableStateFlow(KeystoreState())
    val state: StateFlow<KeystoreState> = _state.asStateFlow()

    fun updateAlias(newAlias: String) {
        _state.value = _state.value.copy(alias = newAlias)
    }

    fun generateKeys() {
        val alias = _state.value.alias
        if (alias.isBlank()) {
            _state.value = _state.value.copy(message = "Alias cannot be empty!")
            return
        }

        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            val parameterSpec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .build()

            kpg.initialize(parameterSpec)
            kpg.generateKeyPair()

            val certificate = keyStore.getCertificate(alias)
            val certBytes = certificate.encoded

            val sha1 = calculateHash(certBytes, "SHA-1")
            val sha256 = calculateHash(certBytes, "SHA-256")

            _state.value = _state.value.copy(
                sha1 = sha1,
                sha256 = sha256,
                message = "RSA Key Generated Successfully!"
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(message = "Error: ${e.localizedMessage}")
        }
    }

    private fun calculateHash(bytes: ByteArray, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        val digest = md.digest(bytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeystoreScreen(navController: NavController, viewModel: KeystoreViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Scaffold(
        topBar = { SabKitTopBar(title = "Keystore & SHA Generator", showBackButton = true, navController) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.alias,
                onValueChange = { viewModel.updateAlias(it) },
                label = { Text("Key Alias") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = { viewModel.generateKeys() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate RSA Key & Hashes")
            }

            if (state.message.isNotEmpty()) {
                Text(
                    text = state.message,
                    color = if (state.message.contains("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (state.sha1.isNotEmpty()) {
                HashDisplayCard("SHA-1 Fingerprint", state.sha1) {
                    clipboardManager.setText(AnnotatedString(state.sha1))
                    Toast.makeText(context, "SHA-1 Copied!", Toast.LENGTH_SHORT).show()
                }
            }

            if (state.sha256.isNotEmpty()) {
                HashDisplayCard("SHA-256 Fingerprint", state.sha256) {
                    clipboardManager.setText(AnnotatedString(state.sha256))
                    Toast.makeText(context, "SHA-256 Copied!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
fun HashDisplayCard(title: String, hash: String, onCopy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontWeight = FontWeight.Bold)
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.Share, contentDescription = "Copy")
                }
            }
            Text(
                text = hash,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ==========================================
// 6. APK & AAB INSPECTOR
// ==========================================
data class ApkInfoData(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val permissions: List<String>
)

@Composable
fun ApkInspectorScreen(navController: NavController) {
    val context = LocalContext.current
    var apkData by remember { mutableStateOf<ApkInfoData?>(null) }
    var statusText by remember { mutableStateOf("Select an .apk file from your device to inspect.") }

    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val data = inspectApkFile(context, it)
            if (data != null) {
                apkData = data
                statusText = "APK Parsed Successfully!"
            } else {
                statusText = "Could not parse APK metadata. Make sure it is a valid .apk package."
            }
        }
    }

    Scaffold(
        topBar = { SabKitTopBar(title = "APK & AAB Inspector", showBackButton = true, navController) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Button(
                onClick = { apkPicker.launch("application/vnd.android.package-archive") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Build, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select APK File")
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(text = statusText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(12.dp))

            apkData?.let { data ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Package Info", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Package Name: ${data.packageName}", fontFamily = FontFamily.Monospace)
                        Text(text = "Version Name: ${data.versionName}")
                        Text(text = "Version Code: ${data.versionCode}")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "Declared Permissions (${data.permissions.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(data.permissions) { perm ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Text(
                                text = perm,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

fun inspectApkFile(context: Context, uri: Uri): ApkInfoData? {
    try {
        val tempFile = java.io.File(context.cacheDir, "inspect_target.apk")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
        val pm = context.packageManager
        val info: PackageInfo? = pm.getPackageArchiveInfo(tempFile.absolutePath, PackageManager.GET_PERMISSIONS)
        if (info != null) {
            val pkgName = info.packageName ?: "Unknown"
            val verName = info.versionName ?: "1.0"
            val verCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION") info.versionCode.toLong()
            }
            val perms = info.requestedPermissions?.toList() ?: emptyList()
            return ApkInfoData(pkgName, verName, verCode, perms)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

// ==========================================
// 7. ADMOB & DEVELOPER HUB
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdMobHubScreen(navController: NavController) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var impressionsText by remember { mutableStateOf("") }
    var revenueText by remember { mutableStateOf("") }
    var ecpmResult by remember { mutableStateOf("") }

    val testAdUnits = listOf(
        "Banner Test Ad Unit" to "ca-app-pub-3940256099942544/6300978111",
        "Interstitial Test Ad Unit" to "ca-app-pub-3940256099942544/1033173712",
        "Rewarded Test Ad Unit" to "ca-app-pub-3940256099942544/5224354917",
        "App Open Test Ad Unit" to "ca-app-pub-3940256099942544/9257395921"
    )

    Scaffold(
        topBar = { SabKitTopBar(title = "AdMob & Developer Hub", showBackButton = true, navController) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "AdMob eCPM Calculator", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = impressionsText,
                        onValueChange = { impressionsText = it },
                        label = { Text("Total Impressions") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = revenueText,
                        onValueChange = { revenueText = it },
                        label = { Text("Estimated Earnings ($)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val imp = impressionsText.toDoubleOrNull() ?: 0.0
                            val rev = revenueText.toDoubleOrNull() ?: 0.0
                            if (imp > 0) {
                                val ecpm = (rev / imp) * 1000
                                ecpmResult = "Estimated eCPM: $${String.format("%.2f", ecpm)}"
                            } else {
                                ecpmResult = "Enter valid impressions > 0"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Calculate eCPM")
                    }
                    if (ecpmResult.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = ecpmResult, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Text(text = "Official AdMob Test Ad Units", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

            testAdUnits.forEach { (title, adUnitId) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(text = adUnitId, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(adUnitId))
                            Toast.makeText(context, "$title Copied!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                    }
                }
            }
        }
    }
}
