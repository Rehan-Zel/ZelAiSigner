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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

enum class Screen { MAIN, CONFIG, SETTINGS, PROGRESS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF00E676), background = Color(0xFF0A0D12), surface = Color(0xFF121721)
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

@Composable
fun ZelSignsApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("zel_prefs", Context.MODE_PRIVATE) }

    var currentScreen by remember { mutableStateOf(Screen.MAIN) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("Ready.") }
    
    var githubPat by remember { mutableStateOf(prefs.getString("pat", "") ?: "") }
    
    // Config State
    var appName by remember { mutableStateOf("MyApp") }
    var repoNameInput by remember { mutableStateOf("") } // NEW: Repo name state

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val name = resolveFileName(context, uri)
            selectedUri = uri
            selectedFileName = name
            if (name.lowercase().endsWith(".zip") || name.lowercase().endsWith(".html")) {
                appName = name.substringBeforeLast(".")
                // Auto-fill the repo name based on the file, but lowercase and with hyphens
                repoNameInput = name.substringBeforeLast(".").lowercase().replace(" ", "-").replace("_", "-")
                currentScreen = Screen.CONFIG
            } else {
                Toast.makeText(context, "Please pick a ZIP or HTML file", Toast.LENGTH_SHORT).show()
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
                    Text("Select File (ZIP / HTML)")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(statusMessage, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            }
            
            Screen.CONFIG -> {
                Text("Configure App", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(value = appName, onValueChange = { appName = it }, label = { Text("App Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
                
                // NEW: Repo Name Text Field
                OutlinedTextField(
                    value = repoNameInput, 
                    onValueChange = { repoNameInput = it }, 
                    label = { Text("GitHub Repo Name") }, 
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = { 
                        if (githubPat.isBlank()) {
                            Toast.makeText(context, "Please set GitHub PAT in Settings", Toast.LENGTH_LONG).show()
                        } else if (repoNameInput.isBlank()) {
                            Toast.makeText(context, "Please enter a Repository Name", Toast.LENGTH_LONG).show()
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
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { 
                    prefs.edit().putString("pat", githubPat).apply()
                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                    currentScreen = Screen.MAIN
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Save")
                }
            }

            Screen.PROGRESS -> {
                var log by remember { mutableStateOf("Initializing GitHub API push...") }
                
                LaunchedEffect(Unit) {
                    if (selectedUri != null) {
                        log = "Uploading $selectedFileName to GitHub as repo: $repoNameInput..."
                        // Pass repoNameInput to the function
                        val result = pushToGitHub(context, selectedUri!!, selectedFileName, githubPat, repoNameInput)
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

// --- REAL GITHUB API NETWORKING (Updated to accept custom repo name) ---
suspend fun pushToGitHub(context: Context, zipUri: Uri, fileName: String, pat: String, repoName: String): String = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient()
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()

        // 1. Get User Info
        val userReq = Request.Builder().url("https://api.github.com/user")
            .header("Authorization", "Bearer $pat").build()
        val userRes = client.newCall(userReq).execute()
        if (!userRes.isSuccessful) return@withContext "Auth Failed: Check your GitHub PAT"
        
        val username = JSONObject(userRes.body?.string() ?: "{}").getString("login")

        // 2. Create Repo with custom name
        val repoBody = JSONObject().put("name", repoName).put("private", false).toString().toRequestBody(jsonMediaType)
        val repoReq = Request.Builder().url("https://api.github.com/user/repos")
            .header("Authorization", "Bearer $pat").post(repoBody).build()
        
        val repoRes = client.newCall(repoReq).execute()
        // If repo already exists (422), we can ignore it and just upload to it, or fail gracefully.
        if (!repoRes.isSuccessful && repoRes.code != 422) {
            return@withContext "Repo Creation Failed: ${repoRes.code} - ${repoRes.body?.string()}"
        }

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
        
        val workflowReq = Request.Builder().url("https://api.github.com/repos/$username/$repoName/contents/.github/workflows/build-apk.yml")
            .header("Authorization", "Bearer $pat").put(workflowBody).build()
        val workflowRes = client.newCall(workflowReq).execute()
        
        if (!workflowRes.isSuccessful) {
            return@withContext "Workflow Upload Failed: ${workflowRes.code} - ${workflowRes.body?.string()}"
        }

        "Success! Repo '$repoName' created, ZIP and Workflow uploaded.\nGo to github.com/$username/$repoName to watch the build."
    } catch (e: Exception) {
        "Network Error: ${e.message}"
    }
}
