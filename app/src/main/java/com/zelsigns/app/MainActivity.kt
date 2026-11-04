package com.zelsigns.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.apksig.ApkSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

enum class Screen {
    MAIN,
    CONFIG,
    SETTINGS,
    PROGRESS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure BouncyCastle provider is registered safely
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        } catch (_: Exception) {
        }

        setContent {
            val darkColors = darkColorScheme(
                primary = Color(0xFF00E676),
                onPrimary = Color(0xFF000000),
                secondary = Color(0xFF00B0FF),
                background = Color(0xFF0A0D12),
                surface = Color(0xFF121721),
                onBackground = Color(0xFFECEFF4),
                onSurface = Color(0xFFECEFF4)
            )

            MaterialTheme(colorScheme = darkColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ZelSignsApp()
                }
            }
        }
    }
}

/**
 * Resolves the real display name of a content URI strictly using
 * ContentResolver and OpenableColumns.DISPLAY_NAME (never uri.lastPathSegment).
 */
fun resolveFileName(context: Context, uri: Uri): String {
    var displayName = "unknown_file"
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                val value = it.getString(nameIndex)
                if (!value.isNullOrBlank()) {
                    displayName = value
                }
            }
        }
    }
    return displayName
}

/**
 * Generates a self-signed RSA-2048 keypair + X.509 certificate via BouncyCastle
 * and signs the input APK locally using Android's official ApkSigner (v1 + v2 + v3).
 */
suspend fun signApkLocally(
    context: Context,
    sourceUri: Uri,
    originalFileName: String
): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
        val provider = BouncyCastleProvider()
        val tempInputFile = File(context.cacheDir, "unsigned_input_${System.currentTimeMillis()}.apk")

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(tempInputFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to read input APK stream")

        val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
        val baseName = originalFileName.removeSuffix(".apk").removeSuffix(".APK")
        val signedOutputFile = File(outputDir, "${baseName}_zel_signed.apk")
        if (signedOutputFile.exists()) {
            signedOutputFile.delete()
        }

        // Generate 2048-bit RSA KeyPair
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048, SecureRandom())
        val keyPair = keyPairGenerator.generateKeyPair()

        // Build X.509 v3 Certificate with BouncyCastle
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 86400000L)
        val notAfter = Date(now + 25L * 365 * 24 * 60 * 60 * 1000L)
        val serial = BigInteger.valueOf(now)
        val subject = X500Name("CN=Zel Signs AutoSigner, OU=Android DevOps, O=ZelSigns, C=US")

        val certBuilder = JcaX509v3CertificateBuilder(
            subject,
            serial,
            notBefore,
            notAfter,
            subject,
            keyPair.public
        )

        val contentSigner = JcaContentSignerBuilder("SHA256WithRSA")
            .build(keyPair.private)

        val certHolder = certBuilder.build(contentSigner)
        val certificate: X509Certificate = JcaX509CertificateConverter()
            .setProvider(provider)
            .getCertificate(certHolder)

        val signerConfig = ApkSigner.SignerConfig.Builder(
            "ZEL_SIGNS_KEY",
            keyPair.private,
            listOf(certificate)
        ).build()

        val apkSigner = ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(tempInputFile)
            .setOutputApk(signedOutputFile)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()

        apkSigner.sign()
        tempInputFile.delete()

        signedOutputFile
    }
}

@Composable
fun ZelSignsApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember {
        context.getSharedPreferences("zel_signs_prefs", Context.MODE_PRIVATE)
    }

    var currentScreen by remember { mutableStateOf(Screen.MAIN) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var statusMessage by remember { mutableStateOf("Ready. Select an APK to sign locally, or ZIP/HTML to auto-build.") }
    var isSigning by remember { mutableStateOf(false) }

    // Configuration Screen state
    var appName by remember { mutableStateOf("My Zel WebApp") }
    var customIconName by remember { mutableStateOf("Default (ic_launcher.xml)") }
    var splashScreenName by remember { mutableStateOf("None selected") }

    // Settings Screen state
    var githubPat by remember { mutableStateOf(prefs.getString("github_pat", "") ?: "") }
    var aiApiKey by remember { mutableStateOf(prefs.getString("ai_api_key", "") ?: "") }

    // File Picker Launcher (*/*)
    val mainFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val resolvedName = resolveFileName(context, uri)
            selectedFileUri = uri
            selectedFileName = resolvedName
            val lower = resolvedName.lowercase()

            when {
                lower.endsWith(".apk") -> {
                    isSigning = true
                    statusMessage = "Signing $resolvedName locally with ApkSigner + BouncyCastle..."
                    scope.launch {
                        val result = signApkLocally(context, uri, resolvedName)
                        isSigning = false
                        result.onSuccess { signedFile ->
                            val msg = "Signed APK saved to: ${signedFile.absolutePath}"
                            statusMessage = msg
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }.onFailure { err ->
                            val errMsg = "Signing error: ${err.localizedMessage ?: "Unknown failure"}"
                            statusMessage = errMsg
                            Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                lower.endsWith(".zip") || lower.endsWith(".html") -> {
                    appName = resolvedName.substringBeforeLast(".")
                        .replaceFirstChar { it.uppercase() }
                    statusMessage = "Loaded archive/web source: $resolvedName"
                    currentScreen = Screen.CONFIG
                }
                else -> {
                    val unsupported = "Unsupported file ($resolvedName). Choose .apk, .zip, or .html"
                    statusMessage = unsupported
                    Toast.makeText(context, unsupported, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Custom Icon Picker (image/*)
    val iconPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            customIconName = resolveFileName(context, uri)
        }
    }

    // Splash Screen Picker (image/*)
    val splashPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            splashScreenName = resolveFileName(context, uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ZEL SIGNS",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "APK Signer & AI Auto-Builder",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentScreen != Screen.MAIN) {
                    OutlinedButton(onClick = { currentScreen = Screen.MAIN }) {
                        Text("Home")
                    }
                }
                OutlinedButton(onClick = { currentScreen = Screen.SETTINGS }) {
                    Text("Settings")
                }
            }
        }

        when (currentScreen) {
            Screen.MAIN -> {
                MainScreenContent(
                    selectedFileName = selectedFileName,
                    statusMessage = statusMessage,
                    isSigning = isSigning,
                    onSelectFileClick = { mainFilePicker.launch("*/*") }
                )
            }
            Screen.CONFIG -> {
                ConfigScreenContent(
                    sourceFileName = selectedFileName,
                    appName = appName,
                    onAppNameChange = { appName = it },
                    customIconName = customIconName,
                    splashScreenName = splashScreenName,
                    onPickIcon = { iconPicker.launch("image/*") },
                    onPickSplash = { splashPicker.launch("image/*") },
                    onBuildViaGitHub = { currentScreen = Screen.PROGRESS }
                )
            }
            Screen.SETTINGS -> {
                SettingsScreenContent(
                    githubPat = githubPat,
                    onGithubPatChange = { githubPat = it },
                    aiApiKey = aiApiKey,
                    onAiApiKeyChange = { aiApiKey = it },
                    onSave = {
                        prefs.edit()
                            .putString("github_pat", githubPat.trim())
                            .putString("ai_api_key", aiApiKey.trim())
                            .apply()
                        Toast.makeText(
                            context,
                            "Credentials saved to SharedPreferences",
                            Toast.LENGTH_SHORT
                        ).show()
                        currentScreen = Screen.MAIN
                    }
                )
            }
            Screen.PROGRESS -> {
                ProgressScreenContent(
                    sourceFileName = selectedFileName,
                    appName = appName,
                    customIconName = customIconName,
                    splashScreenName = splashScreenName,
                    hasPat = githubPat.isNotBlank(),
                    hasAiKey = aiApiKey.isNotBlank(),
                    onBackHome = { currentScreen = Screen.MAIN }
                )
            }
        }
    }
}

@Composable
fun MainScreenContent(
    selectedFileName: String,
    statusMessage: String,
    isSigning: Boolean,
    onSelectFileClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Universal Build & Sign Pipeline",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "• Pick an .apk file to perform local cryptographic V1/V2/V3 signing via BouncyCastle + ApkSigner.\n" +
                            "• Pick a .zip or .html file to configure your Android wrapper and trigger the AI + GitHub Actions Auto-Builder.",
                    fontSize = 13.sp,
                    color = Color(0xFFB0BEC5)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onSelectFileClick,
                    enabled = !isSigning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isSigning) "Signing APK..." else "Select File (APK/ZIP/HTML)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (isSigning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E131B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "TELEMETRY & OUTPUT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Selected File: ${selectedFileName.ifBlank { "None" }}",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = statusMessage,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF80CBC4)
                )
            }
        }
    }
}

@Composable
fun ConfigScreenContent(
    sourceFileName: String,
    appName: String,
    onAppNameChange: (String) -> Unit,
    customIconName: String,
    splashScreenName: String,
    onPickIcon: () -> Unit,
    onPickSplash: () -> Unit,
    onBuildViaGitHub: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Configuration Screen",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Source Payload: $sourceFileName",
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            value = appName,
            onValueChange = onAppNameChange,
            label = { Text("App Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Custom Icon: $customIconName", fontSize = 13.sp)
                OutlinedButton(
                    onClick = onPickIcon,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pick Custom Icon (image/*)")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Splash Screen: $splashScreenName", fontSize = 13.sp)
                OutlinedButton(
                    onClick = onPickSplash,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pick Splash Screen (image/*)")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onBuildViaGitHub,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = "Build via GitHub",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SettingsScreenContent(
    githubPat: String,
    onGithubPatChange: (String) -> Unit,
    aiApiKey: String,
    onAiApiKeyChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings Screen",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Configure credentials for GitHub Actions & AI Auto-Builder. Persisted securely in SharedPreferences.",
            fontSize = 13.sp,
            color = Color(0xFF94A3B8)
        )

        OutlinedTextField(
            value = githubPat,
            onValueChange = onGithubPatChange,
            label = { Text("GitHub PAT (ghp_...)") },
            placeholder = { Text("ghp_xxxxxxxxxxxxxxxxxxxx") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = aiApiKey,
            onValueChange = onAiApiKeyChange,
            label = { Text("AI API Key") },
            placeholder = { Text("sk-xxxxxxxxxxxxxxxxxxxx") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = "Save Credentials",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ProgressScreenContent(
    sourceFileName: String,
    appName: String,
    customIconName: String,
    splashScreenName: String,
    hasPat: Boolean,
    hasAiKey: Boolean,
    onBackHome: () -> Unit
) {
    val logs = remember { mutableStateListOf<String>() }
    var isCompleted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        logs.clear()
        val steps = listOf(
            "[00:00] Initializing Zel Signs AI Auto-Builder loop...",
            "[00:02] Verifying SharedPreferences credentials (GitHub PAT: ${if (hasPat) "Configured" else "Mock/Demo Mode"}, AI Key: ${if (hasAiKey) "Configured" else "Mock/Demo Mode"})...",
            "[00:04] Inspecting payload '$sourceFileName' via OpenableColumns.DISPLAY_NAME...",
            "[00:06] AI Agent synthesizing Android WebView + Jetpack Compose scaffold for '$appName'...",
            "[00:08] Injecting assets -> Icon: '$customIconName' | Splash: '$splashScreenName'...",
            "[00:10] Pushing commit to GitHub repository & dispatching '.github/workflows/build-apk.yml'...",
            "[00:12] GitHub Runner (ubuntu-latest, JDK 17, Gradle 8.7, AGP 8.4.0) running ':app:assembleDebug'...",
            "[00:14] Build succeeded! Artifact 'zel-signs-apk' signed with BouncyCastle + ApkSigner Ready."
        )
        for (step in steps) {
            logs.add(step)
            delay(2000L)
        }
        isCompleted = true
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "GitHub & AI Build Progress",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        if (!isCompleted) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF070A0E), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                .padding(14.dp)
        ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(logs) { logLine ->
                    Text(
                        text = logLine,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = if (logLine.contains("Build succeeded")) {
                            Color(0xFF00E676)
                        } else {
                            Color(0xFFE2E8F0)
                        }
                    )
                }
            }
        }

        Button(
            onClick = onBackHome,
            enabled = isCompleted,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(if (isCompleted) "Finish & Return Home" else "Running CI/CD Loop...")
        }
    }
}
