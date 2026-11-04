package com.zelsigns.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.Security
import java.util.Date

enum class Screen { MAIN, CONFIG, SETTINGS, PROGRESS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF00E676), background = Color(0xFF0A0D12), surface = Color(0xFF121721),
                onBackground = Color(0xFFECEFF4), onSurface = Color(0xFFECEFF4)
            )) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ZelSignsApp()
                }
            }
        }
    }
}

fun resolveFileName(context: Context, uri: Uri): String {
    var displayName = "unknown_file"
    context.contentResolver.query(uri, null, null, null, null)?.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1) displayName = it.getString(idx) ?: "unknown_file"
        }
    }
    return displayName
}

suspend fun signApkLocally(context: Context, sourceUri: Uri, originalFileName: String): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
        val tempInputFile = File(context.cacheDir, "input.apk")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(tempInputFile).use { output -> input.copyTo(output) }
        } ?: throw Exception("Unable to read stream")

        val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
        val signedOutputFile = File(outputDir, "${originalFileName.removeSuffix(".apk")}_zel_signed.apk")
        
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val keyPair = kpg.generateKeyPair()

        val now = System.currentTimeMillis()
        val dnName = X500Name("CN=ZelSigns, O=Zel, C=US")
        val certBuilder = JcaX509v3CertificateBuilder(
            dnName, BigInteger.valueOf(now), Date(now), Date(now + 31536000000L), dnName, keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val cert = JcaX509CertificateConverter().setProvider("BC").getCertificate(certBuilder.build(signer))

        val signerConfig = ApkSigner.SignerConfig.Builder("ZelKey", keyPair.private, listOf(cert)).build()
        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(tempInputFile)
            .setOutputApk(signedOutputFile)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()

        signedOutputFile
    }
}

@Composable
fun ZelSignsApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("zel_prefs", Context.MODE_PRIVATE) }

    var currentScreen by remember { mutableStateOf(Screen.MAIN) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("Ready.") }
    var isSigning by remember { mutableStateOf(false) }
    
    // Config State
    var appName by remember { mutableStateOf("MyApp") }
    
    // Settings State
    var githubPat by remember { mutableStateOf(prefs.getString("pat", "") ?: "") }
    var aiApiKey by remember { mutableStateOf(prefs.getString("ai_key", "") ?: "") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val name = resolveFileName(context, uri)
            selectedUri = uri
            selectedFileName = name
            
            if (name.lowercase().endsWith(".apk")) {
                isSigning = true
                statusMessage = "Signing locally..."
                scope.launch {
                    val result = signApkLocally(context, uri, name)
                    isSigning = false
                    result.onSuccess { statusMessage = "Success: ${it.absolutePath}" }
                          .onFailure { statusMessage = "Failed: ${it.message}" }
                }
            } else if (name.lowercase().endsWith(".zip") || name.lowercase().endsWith(".html")) {
                appName = name.substringBeforeLast(".")
                currentScreen = Screen.CONFIG
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("ZEL SIGNS", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row {
                if (currentScreen != Screen.MAIN) OutlinedButton(onClick = { currentScreen = Screen.MAIN }) { Text("Home") }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = { currentScreen = Screen.SETTINGS }) { Text("Settings") }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        when (currentScreen) {
            Screen.MAIN -> {
                Button(onClick = { filePicker.launch("*/*") }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text("Select File (APK / ZIP / HTML)")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(statusMessage, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                if (isSigning) CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            }
            
            Screen.CONFIG -> {
                Text("Configure App", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = appName, onValueChange = { appName = it }, label = { Text("App Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { 
                        if (githubPat.isBlank()) {
                            Toast.makeText(context, "Please set GitHub PAT in Settings", Toast.LENGTH_LONG).show()
                        } else if (selectedUri != null) {
                            currentScreen = Screen.PROGRESS 
                        }
                    }, 
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Build via GitHub")
                }
            }

            Screen.SETTINGS -> {
                Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = githubPat, onValueChange = { githubPat = it }, label = { Text("GitHub PAT (ghp_...)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = aiApiKey, onValueChange = { aiApiKey = it }, label = { Text("AI API Key") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { 
                    prefs.edit().putString("pat", githubPat).putString("ai_key", aiApiKey).apply()
                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                    currentScreen = Screen.MAIN
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Save")
                }
            }

            Screen.PROGRESS -> {
                // REAL GITHUB API LOGIC STARTS HERE
                var log by remember { mutableStateOf("Initializing GitHub API push...") }
                
                LaunchedEffect(Unit) {
                    if (selectedUri != null) {
                        log = "Uploading $selectedFileName to GitHub..."
                        val result = pushToGitHub(context, selectedUri!!, selectedFileName, githubPat)
                        log = result
                    }
                }
                
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(log, fontFamily = FontFamily.Monospace, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { currentScreen = Screen.MAIN }) { Text("Back to Home") }
                }
            }
        }
    }
}

// --- REAL GITHUB API NETWORKING ---
suspend fun pushToGitHub(context: Context, zipUri: Uri, fileName: String, pat: String): String = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient()
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()

        // 1. Get User Info
        val userReq = Request.Builder().url("https://api.github.com/user")
            .header("Authorization", "Bearer $pat").build()
        val userRes = client.newCall(userReq).execute()
        if (!userRes.isSuccessful) return@withContext "Auth Failed: Check your GitHub PAT"
        
        val username = JSONObject(userRes.body?.string() ?: "{}").getString("login")
        val repoName = "zel-build-${System.currentTimeMillis()}"

        // 2. Create Repo
        val repoBody = JSONObject().put("name", repoName).put("private", false).toString().toRequestBody(jsonMediaType)
        val repoReq = Request.Builder().url("https://api.github.com/user/repos")
            .header("Authorization", "Bearer $pat").post(repoBody).build()
        client.newCall(repoReq).execute()

        // 3. Upload ZIP
        val bytes = context.contentResolver.openInputStream(zipUri)?.readBytes() ?: return@withContext "File Read Error"
        val base64Zip = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val zipBody = JSONObject().put("message", "Upload source zip").put("content", base64Zip).toString().toRequestBody(jsonMediaType)
        val zipReq = Request.Builder().url("https://api.github.com/repos/$username/$repoName/contents/source.zip")
            .header("Authorization", "Bearer $pat").put(zipBody).build()
        val zipRes = client.newCall(zipReq).execute()
        if (!zipRes.isSuccessful) return@withContext "Zip Upload Failed: ${zipRes.code}"

        // 4. Upload the Workflow File to the NEW repo!
        val workflowYaml = """
            name: Build APK
            on:
              push:
                branches: [main, master]
              workflow_dispatch:
            jobs:
              build:
                runs-on: ubuntu-latest
                steps:
                  - uses: actions/checkout@v4
                  - name: Unzip Source Code
                    run: unzip source.zip -d build_dir
                  - uses: actions/setup-java@v4
                    with:
                      distribution: temurin
                      java-version: "17"
                  - uses: android-actions/setup-android@v4
                    with:
                      packages: 'platform-tools'
                  - run: yes | sdkmanager --licenses > /dev/null || true; sdkmanager "platforms;android-34" "build-tools;34.0.0"
                  - name: Build APK
                    working-directory: build_dir
                    run: |
                      wget -q https://services.gradle.org/distributions/gradle-8.7-bin.zip
                      unzip -q gradle-8.7-bin.zip
                      ./gradle-8.7/bin/gradle :app:assembleDebug --stacktrace --no-daemon
                  - uses: actions/upload-artifact@v4
                    with:
                      name: zel-game-apk
                      path: build_dir/app/build/outputs/apk/debug/*.apk
        """.trimIndent()

        val base64Workflow = Base64.encodeToString(workflowYaml.toByteArray(), Base64.NO_WRAP)
        val workflowBody = JSONObject().put("message", "Add workflow").put("content", base64Workflow).toString().toRequestBody(jsonMediaType)
        
        // The URL needs the exact path to the workflow
        val workflowReq = Request.Builder().url("https://api.github.com/repos/$username/$repoName/contents/.github/workflows/build-apk.yml")
            .header("Authorization", "Bearer $pat").put(workflowBody).build()
        val workflowRes = client.newCall(workflowReq).execute()
        
        if (!workflowRes.isSuccessful) {
            return@withContext "Workflow Upload Failed: ${workflowRes.code} - ${workflowRes.body?.string()}"
        }

        "Success! Game ZIP and Workflow uploaded.\nGo to github.com/$username/$repoName to watch the build."
    } catch (e: Exception) {
        "Network Error: ${e.message}"
    }
}
