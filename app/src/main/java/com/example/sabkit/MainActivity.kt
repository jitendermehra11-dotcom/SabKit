package com.example.sabkit

import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest

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
        composable("keystore") { KeystoreScreen(navController) }
        composable("placeholder/{title}") { backStackEntry ->
            val title = backStackEntry.arguments?.getString("title") ?: "Feature"
            PlaceholderScreen(navController, title)
        }
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
// 3. DASHBOARD SCREEN (4 Interactive Cards)
// ==========================================
data class DashboardItem(val title: String, val icon: ImageVector, val route: String)

@Composable
fun DashboardScreen(navController: NavController) {
    val features = listOf(
        DashboardItem("ZIP & Source Code Viewer", Icons.Default.Folder, "placeholder/ZIP & Source Code Viewer"),
        DashboardItem("Keystore & SHA Generator", Icons.Default.Lock, "keystore"),
        DashboardItem("APK & AAB Inspector", Icons.Default.Build, "placeholder/APK & AAB Inspector"),
        DashboardItem("AdMob & Developer Hub", Icons.Default.MonetizationOn, "placeholder/AdMob & Developer Hub")
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
// 4. KEYSTORE GENERATOR STATE & VIEWMODEL
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
            // 1. Initialize AndroidKeyStore
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            // 2. Generate RSA Key Pair
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            val parameterSpec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .build()

            kpg.initialize(parameterSpec)
            kpg.generateKeyPair() // Generates and stores in AndroidKeyStore

            // 3. Retrieve Certificate and Hash
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
        return digest.joinToString(":") { "%02X".format(it) } // Format to Hex
    }
}

// ==========================================
// 5. KEYSTORE GENERATOR UI
// ==========================================
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
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

// ==========================================
// 6. PLACEHOLDER SCREEN FOR OTHER FEATURES
// ==========================================
@Composable
fun PlaceholderScreen(navController: NavController, title: String) {
    Scaffold(
        topBar = { SabKitTopBar(title = title, showBackButton = true, navController) }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$title\nComing Soon in SabKit!",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
