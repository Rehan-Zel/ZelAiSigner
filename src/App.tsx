import React, { useState, useEffect, useRef } from 'react';
import JSZip from 'jszip';
import {
  ShieldCheck,
  FileCode2,
  Smartphone,
  Copy,
  Check,
  Download,
  CheckCircle2,
  Upload,
  Image as ImageIcon,
  GitBranch,
  KeyRound,
  RefreshCw,
  Code2
} from 'lucide-react';
import {
  ANDROID_PROJECT_FILES,
  VERSION_MATRIX,
  getAllFormattedOutput,
  AndroidProjectFile
} from './data/androidProjectFiles';

type AppScreen = 'MAIN' | 'CONFIG' | 'SETTINGS' | 'PROGRESS';

interface SignedApkArtifact {
  originalName: string;
  signedName: string;
  androidPath: string;
  sha256: string;
  timestamp: string;
  sizeBytes: number;
  v1Signed: boolean;
  v2Signed: boolean;
  v3Signed: boolean;
  subjectDn: string;
}

export function App() {
  // Active workspace mode
  const [activeTab, setActiveTab] = useState<'workbench' | 'files' | 'raw-bundle'>('workbench');
  const [selectedCodeFile, setSelectedCodeFile] = useState<AndroidProjectFile>(
    ANDROID_PROJECT_FILES[7] // MainActivity.kt default in inspector
  );
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [isExportingZip, setIsExportingZip] = useState(false);

  // Simulated Android App State (mirrors MainActivity.kt 1:1)
  const [currentScreen, setCurrentScreen] = useState<AppScreen>('MAIN');
  const [selectedFileName, setSelectedFileName] = useState<string>('');
  const [simulatedContentUri, setSimulatedContentUri] = useState<string>('');
  const [statusMessage, setStatusMessage] = useState<string>(
    'Ready. Select an APK to sign locally, or ZIP/HTML to auto-build.'
  );
  const [isSigning, setIsSigning] = useState<boolean>(false);
  const [androidToast, setAndroidToast] = useState<string | null>(null);
  const [signedArtifact, setSignedArtifact] = useState<SignedApkArtifact | null>(null);

  // Config Screen State
  const [appName, setAppName] = useState<string>('My Zel WebApp');
  const [customIconName, setCustomIconName] = useState<string>('Default (ic_launcher.xml)');
  const [customIconPreview, setCustomIconPreview] = useState<string | null>(null);
  const [splashScreenName, setSplashScreenName] = useState<string>('None selected');
  const [splashPreview, setSplashPreview] = useState<string | null>(null);

  // Settings Screen State (backed by localStorage mirroring SharedPreferences)
  const [githubPat, setGithubPat] = useState<string>(() => {
    return localStorage.getItem('zel_signs_github_pat') || '';
  });
  const [aiApiKey, setAiApiKey] = useState<string>(() => {
    return localStorage.getItem('zel_signs_ai_api_key') || '';
  });

  // Progress Screen State (2-second step loop)
  const [progressLogs, setProgressLogs] = useState<string[]>([]);
  const [isBuildCompleted, setIsBuildCompleted] = useState<boolean>(false);

  // Hidden file inputs for real browser file picking
  const mainFileInputRef = useRef<HTMLInputElement | null>(null);
  const iconFileInputRef = useRef<HTMLInputElement | null>(null);
  const splashFileInputRef = useRef<HTMLInputElement | null>(null);

  const showToast = (msg: string) => {
    setAndroidToast(msg);
    setTimeout(() => {
      setAndroidToast((prev) => (prev === msg ? null : prev));
    }, 4200);
  };

  // Progress Screen 2-second coroutine simulation
  useEffect(() => {
    if (currentScreen !== 'PROGRESS') return;

    setProgressLogs([]);
    setIsBuildCompleted(false);

    const hasPat = githubPat.trim().length > 0;
    const hasAiKey = aiApiKey.trim().length > 0;
    const steps = [
      `[00:00] Initializing Zel Signs AI Auto-Builder loop...`,
      `[00:02] Verifying SharedPreferences credentials (GitHub PAT: ${
        hasPat ? 'Configured' : 'Mock/Demo Mode'
      }, AI Key: ${hasAiKey ? 'Configured' : 'Mock/Demo Mode'})...`,
      `[00:04] Inspecting payload '${
        selectedFileName || 'webapp.zip'
      }' via OpenableColumns.DISPLAY_NAME...`,
      `[00:06] AI Agent synthesizing Android WebView + Jetpack Compose scaffold for '${appName}'...`,
      `[00:08] Injecting assets -> Icon: '${customIconName}' | Splash: '${splashScreenName}'...`,
      `[00:10] Pushing commit to GitHub repository & dispatching '.github/workflows/build-apk.yml'...`,
      `[00:12] GitHub Runner (ubuntu-latest, JDK 17, Gradle 8.7, AGP 8.4.0) running ':app:assembleDebug'...`,
      `[00:14] Build succeeded! Artifact 'zel-signs-apk' signed with BouncyCastle + ApkSigner Ready.`
    ];

    const timers: number[] = [];
    steps.forEach((step, idx) => {
      const t = window.setTimeout(() => {
        setProgressLogs((prev) => [...prev, step]);
        if (idx === steps.length - 1) {
          setIsBuildCompleted(true);
          showToast(`GitHub build finished for ${appName}!`);
        }
      }, idx * 2000);
      timers.push(t);
    });

    return () => {
      timers.forEach((t) => clearTimeout(t));
    };
  }, [currentScreen, selectedFileName, appName, customIconName, splashScreenName, githubPat, aiApiKey]);

  // Simulate ContentResolver + OpenableColumns.DISPLAY_NAME + APK/ZIP/HTML routing
  const handleFileResolved = async (fileName: string, byteSize = 4829184) => {
    const encodedDocId = encodeURIComponent(`primary:Download/${fileName}`);
    const contentUri = `content://com.android.externalstorage.documents/document/${encodedDocId}`;
    setSimulatedContentUri(contentUri);
    setSelectedFileName(fileName);

    const lower = fileName.toLowerCase();
    if (lower.endsWith('.apk')) {
      setIsSigning(true);
      setSignedArtifact(null);
      setStatusMessage(`Signing ${fileName} locally with ApkSigner + BouncyCastle...`);

      // Simulate BouncyCastle 2048-bit RSA + X509v3 + ApkSigner v1/v2/v3
      setTimeout(() => {
        const baseName = fileName.replace(/\.apk$/i, '');
        const signedName = `${baseName}_zel_signed.apk`;
        const androidPath = `/storage/emulated/0/Android/data/com.zelsigns.app/files/${signedName}`;
        const randomHex = Array.from({ length: 32 }, () =>
          Math.floor(Math.random() * 256)
            .toString(16)
            .padStart(2, '0')
            .toUpperCase()
        ).join(':');

        const artifact: SignedApkArtifact = {
          originalName: fileName,
          signedName,
          androidPath,
          sha256: randomHex,
          timestamp: new Date().toISOString(),
          sizeBytes: byteSize + 16384,
          v1Signed: true,
          v2Signed: true,
          v3Signed: true,
          subjectDn: 'CN=Zel Signs AutoSigner, OU=Android DevOps, O=ZelSigns, C=US'
        };

        setIsSigning(false);
        setSignedArtifact(artifact);
        const msg = `Signed APK saved to: ${androidPath}`;
        setStatusMessage(msg);
        showToast(msg);
      }, 1400);
    } else if (lower.endsWith('.zip') || lower.endsWith('.html')) {
      const derivedAppName = fileName
        .replace(/\.(zip|html)$/i, '')
        .replace(/[-_]/g, ' ')
        .replace(/\b\w/g, (c) => c.toUpperCase());
      setAppName(derivedAppName || 'My Zel WebApp');
      setStatusMessage(`Loaded archive/web source: ${fileName}`);
      setCurrentScreen('CONFIG');
    } else {
      const unsupported = `Unsupported file (${fileName}). Choose .apk, .zip, or .html`;
      setStatusMessage(unsupported);
      showToast(unsupported);
    }
  };

  const handleBrowserFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      handleFileResolved(file.name, file.size);
    }
    e.target.value = '';
  };

  const handleIconSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setCustomIconName(file.name);
      const reader = new FileReader();
      reader.onload = () => setCustomIconPreview(reader.result as string);
      reader.readAsDataURL(file);
      showToast(`Custom icon resolved via OpenableColumns: ${file.name}`);
    }
    e.target.value = '';
  };

  const handleSplashSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setSplashScreenName(file.name);
      const reader = new FileReader();
      reader.onload = () => setSplashPreview(reader.result as string);
      reader.readAsDataURL(file);
      showToast(`Splash image resolved via OpenableColumns: ${file.name}`);
    }
    e.target.value = '';
  };

  const handleSaveSettings = () => {
    localStorage.setItem('zel_signs_github_pat', githubPat.trim());
    localStorage.setItem('zel_signs_ai_api_key', aiApiKey.trim());
    showToast('Credentials saved to SharedPreferences');
    setCurrentScreen('MAIN');
  };

  const handleCopyText = async (text: string, id: string) => {
    await navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleDownloadAndroidProjectZip = async () => {
    setIsExportingZip(true);
    try {
      const zip = new JSZip();
      for (const file of ANDROID_PROJECT_FILES) {
        zip.file(file.path, file.content);
      }
      const blob = await zip.generateAsync({ type: 'blob' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'zel-signs-native-android-project.zip';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } finally {
      setIsExportingZip(false);
    }
  };

  const handleDownloadSignedApkDemo = () => {
    if (!signedArtifact) return;
    const manifestReport = [
      `Manifest-Version: 1.0`,
      `Created-By: Zel Signs ApkSigner 8.4.0 + BouncyCastle 1.78.1`,
      `Original-APK: ${signedArtifact.originalName}`,
      `Signed-APK: ${signedArtifact.signedName}`,
      `Output-Path: ${signedArtifact.androidPath}`,
      `Subject-DN: ${signedArtifact.subjectDn}`,
      `SHA256-Certificate-Digest: ${signedArtifact.sha256}`,
      `Signature-Schemes: v1=${signedArtifact.v1Signed}, v2=${signedArtifact.v2Signed}, v3=${signedArtifact.v3Signed}`,
      `Timestamp: ${signedArtifact.timestamp}`
    ].join('\n');

    const blob = new Blob([manifestReport], { type: 'application/vnd.android.package-archive' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = signedArtifact.signedName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="min-h-screen bg-[#07090D] text-[#ECEFF4] flex flex-col">
      {/* Hidden File Inputs for Real Browser Picking */}
      <input
        ref={mainFileInputRef}
        type="file"
        accept="*/*"
        onChange={handleBrowserFileSelect}
        className="hidden"
      />
      <input
        ref={iconFileInputRef}
        type="file"
        accept="image/*"
        onChange={handleIconSelect}
        className="hidden"
      />
      <input
        ref={splashFileInputRef}
        type="file"
        accept="image/*"
        onChange={handleSplashSelect}
        className="hidden"
      />

      {/* Top Navigation & Telemetry Header */}
      <header className="border-b border-[#1A2230] bg-[#0B0F17]/90 backdrop-blur-md sticky top-0 z-30">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-3.5 flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3.5">
            {/* Exact Render of ic_launcher.xml (Green 'Z' on Black Background) */}
            <div
              title="app/src/main/res/drawable/ic_launcher.xml"
              className="w-11 h-11 rounded-xl bg-black border border-[#1E293B] flex items-center justify-center shadow-lg shadow-[#00E676]/10 shrink-0"
            >
              <svg viewBox="0 0 108 108" className="w-9 h-9">
                <path fill="#000000" d="M0,0h108v108h-108z" />
                <path fill="#121820" d="M14,14h80v80h-80z" />
                <path
                  fill="#00E676"
                  d="M28,28 H80 V40 L45,68 H80 V80 H28 V68 L63,40 H28 Z"
                />
              </svg>
            </div>

            <div>
              <div className="flex items-center gap-2.5">
                <h1 className="text-lg font-extrabold tracking-tight text-white">
                  ZEL SIGNS
                </h1>
                <span className="px-2 py-0.5 text-[11px] font-mono font-semibold rounded-full bg-[#00E676]/15 text-[#00E676] border border-[#00E676]/30">
                  Kotlin 1.9.24 • AGP 8.4.0
                </span>
                <span className="hidden sm:inline-flex px-2 py-0.5 text-[11px] font-mono rounded-full bg-[#00B0FF]/15 text-[#00B0FF] border border-[#00B0FF]/30">
                  Gradle 8.7 • JDK 17
                </span>
              </div>
              <p className="text-xs text-[#94A3B8]">
                Native Android APK Signer (ApkSigner + BouncyCastle 1.78.1) &amp; GitHub AI Auto-Builder
              </p>
            </div>
          </div>

          {/* Mode Switcher & Quick Export */}
          <div className="flex items-center gap-2 flex-wrap">
            <div className="inline-flex rounded-lg bg-[#111722] p-1 border border-[#1E293B]">
              <button
                onClick={() => setActiveTab('workbench')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold transition ${
                  activeTab === 'workbench'
                    ? 'bg-[#00E676] text-black shadow-sm'
                    : 'text-[#94A3B8] hover:text-white'
                }`}
              >
                <Smartphone className="w-3.5 h-3.5" />
                Live Android App + Studio
              </button>
              <button
                onClick={() => setActiveTab('files')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold transition ${
                  activeTab === 'files'
                    ? 'bg-[#00E676] text-black shadow-sm'
                    : 'text-[#94A3B8] hover:text-white'
                }`}
              >
                <FileCode2 className="w-3.5 h-3.5" />
                8 Required Files ({ANDROID_PROJECT_FILES.length}/8)
              </button>
              <button
                onClick={() => setActiveTab('raw-bundle')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold transition ${
                  activeTab === 'raw-bundle'
                    ? 'bg-[#00E676] text-black shadow-sm'
                    : 'text-[#94A3B8] hover:text-white'
                }`}
              >
                <Code2 className="w-3.5 h-3.5" />
                Full === Header === Output
              </button>
            </div>

            <button
              onClick={handleDownloadAndroidProjectZip}
              disabled={isExportingZip}
              className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-xs font-bold bg-[#162030] hover:bg-[#1E2C42] text-[#00E676] border border-[#00E676]/40 transition cursor-pointer"
            >
              <Download className="w-3.5 h-3.5" />
              {isExportingZip ? 'Packaging...' : 'Download Android Project (.zip)'}
            </button>
          </div>
        </div>

        {/* Strict Version Matrix Ribbon */}
        <div className="bg-[#080B10] border-t border-[#141B26] px-4 sm:px-6 py-2 overflow-x-auto">
          <div className="max-w-7xl mx-auto flex items-center gap-4 text-[11px] font-mono whitespace-nowrap">
            <span className="text-[#00E676] font-bold flex items-center gap-1">
              <ShieldCheck className="w-3.5 h-3.5" />
              LOCKED MATRIX:
            </span>
            {VERSION_MATRIX.map((item) => (
              <div key={item.name} className="flex items-center gap-1.5 text-[#94A3B8]">
                <span className="text-[#64748B]">{item.name}:</span>
                <span className="text-[#E2E8F0] font-semibold">{item.version}</span>
              </div>
            ))}
          </div>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 py-6">
        {activeTab === 'workbench' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
            {/* Left Column: Authentic Jetpack Compose Android Device Simulator */}
            <div className="lg:col-span-5 flex flex-col items-center">
              <div className="w-full max-w-[410px] rounded-[36px] bg-[#0B0F16] border-[3px] border-[#263244] shadow-2xl shadow-black/80 overflow-hidden relative">
                {/* Android Status Bar */}
                <div className="px-6 pt-3 pb-2 bg-[#0A0D12] flex items-center justify-between text-[11px] font-mono text-[#94A3B8] border-b border-[#161D2A]">
                  <span>14:20 • Android 14 (API 34)</span>
                  <div className="flex items-center gap-2">
                    <span className="text-[#00E676]">5G</span>
                    <span>100%</span>
                  </div>
                </div>

                {/* Jetpack Compose Surface (MainActivity.kt UI) */}
                <div className="p-5 min-h-[610px] bg-[#0A0D12] flex flex-col justify-between relative">
                  <div>
                    {/* App Top Bar */}
                    <div className="flex items-center justify-between pb-4 mb-4 border-b border-[#17202E]">
                      <div className="flex items-center gap-2.5">
                        <div className="w-8 h-8 rounded-lg bg-black border border-[#263244] flex items-center justify-center">
                          <svg viewBox="0 0 108 108" className="w-6 h-6">
                            <path fill="#000000" d="M0,0h108v108h-108z" />
                            <path
                              fill="#00E676"
                              d="M28,28 H80 V40 L45,68 H80 V80 H28 V68 L63,40 H28 Z"
                            />
                          </svg>
                        </div>
                        <div>
                          <div className="text-lg font-extrabold tracking-tight text-[#00E676] leading-none">
                            ZEL SIGNS
                          </div>
                          <div className="text-[11px] text-[#94A3B8] mt-0.5">
                            APK Signer &amp; AI Auto-Builder
                          </div>
                        </div>
                      </div>

                      <div className="flex items-center gap-1.5">
                        {currentScreen !== 'MAIN' && (
                          <button
                            onClick={() => setCurrentScreen('MAIN')}
                            className="px-2.5 py-1.5 rounded-lg border border-[#2D3B4E] hover:border-[#00E676] text-xs font-medium text-[#ECEFF4] transition cursor-pointer"
                          >
                            Home
                          </button>
                        )}
                        <button
                          onClick={() => setCurrentScreen('SETTINGS')}
                          className={`px-2.5 py-1.5 rounded-lg border text-xs font-medium transition cursor-pointer ${
                            currentScreen === 'SETTINGS'
                              ? 'border-[#00E676] bg-[#00E676]/15 text-[#00E676]'
                              : 'border-[#2D3B4E] hover:border-[#00E676] text-[#ECEFF4]'
                          }`}
                        >
                          Settings
                        </button>
                      </div>
                    </div>

                    {/* SCREEN 1: MAIN SCREEN */}
                    {currentScreen === 'MAIN' && (
                      <div className="space-y-4">
                        <div className="rounded-xl bg-[#121721] border border-[#1E293B] p-4 space-y-3">
                          <div className="text-base font-bold text-white">
                            Universal Build &amp; Sign Pipeline
                          </div>
                          <p className="text-xs text-[#B0BEC5] leading-relaxed">
                            • Pick an <code className="text-[#00E676]">.apk</code> file to perform local cryptographic V1/V2/V3 signing via BouncyCastle + ApkSigner.
                            <br />• Pick a <code className="text-[#00B0FF]">.zip</code> or{' '}
                            <code className="text-[#00B0FF]">.html</code> file to configure your Android wrapper and trigger the AI + GitHub Actions Auto-Builder.
                          </p>

                          <button
                            onClick={() => mainFileInputRef.current?.click()}
                            disabled={isSigning}
                            className="w-full h-13 rounded-xl bg-[#00E676] hover:bg-[#00C853] disabled:opacity-50 text-black font-bold text-sm flex items-center justify-center gap-2 shadow-lg shadow-[#00E676]/20 transition cursor-pointer"
                          >
                            <Upload className="w-4 h-4" />
                            {isSigning ? 'Signing APK...' : 'Select File (APK/ZIP/HTML)'}
                          </button>
                        </div>

                        {/* Quick Instant Test Presets for Simulator Convenience */}
                        <div className="rounded-xl bg-[#0E131B] border border-[#1A2332] p-3.5 space-y-2.5">
                          <div className="flex items-center justify-between">
                            <span className="text-[11px] font-mono uppercase tracking-wider text-[#94A3B8]">
                              Instant Simulator Test Payloads
                            </span>
                            <span className="text-[10px] font-mono text-[#00E676]">
                              ActivityResultContracts.GetContent("*/*")
                            </span>
                          </div>
                          <div className="grid grid-cols-3 gap-2">
                            <button
                              onClick={() => handleFileResolved('unsigned-release.apk', 6412032)}
                              disabled={isSigning}
                              className="px-2.5 py-2 rounded-lg bg-[#141C28] hover:bg-[#1B2638] border border-[#243247] text-left transition cursor-pointer"
                            >
                              <div className="text-xs font-bold text-[#00E676]">.APK File</div>
                              <div className="text-[10px] text-[#94A3B8] truncate">
                                unsigned-release.apk
                              </div>
                            </button>
                            <button
                              onClick={() => handleFileResolved('zel-webapp-bundle.zip', 2194304)}
                              disabled={isSigning}
                              className="px-2.5 py-2 rounded-lg bg-[#141C28] hover:bg-[#1B2638] border border-[#243247] text-left transition cursor-pointer"
                            >
                              <div className="text-xs font-bold text-[#00B0FF]">.ZIP Archive</div>
                              <div className="text-[10px] text-[#94A3B8] truncate">
                                zel-webapp-bundle.zip
                              </div>
                            </button>
                            <button
                              onClick={() => handleFileResolved('index.html', 48120)}
                              disabled={isSigning}
                              className="px-2.5 py-2 rounded-lg bg-[#141C28] hover:bg-[#1B2638] border border-[#243247] text-left transition cursor-pointer"
                            >
                              <div className="text-xs font-bold text-amber-400">.HTML Page</div>
                              <div className="text-[10px] text-[#94A3B8] truncate">
                                index.html
                              </div>
                            </button>
                          </div>
                        </div>

                        {isSigning && (
                          <div className="w-full bg-[#192230] h-1.5 rounded-full overflow-hidden">
                            <div className="bg-[#00E676] h-full w-2/3 animate-pulse" />
                          </div>
                        )}

                        {/* Telemetry & Output Box */}
                        <div className="rounded-xl bg-[#0E131B] border border-[#1E293B] p-3.5 space-y-2">
                          <div className="text-[11px] font-bold tracking-wider text-[#00E676]">
                            TELEMETRY &amp; OUTPUT
                          </div>
                          <div className="text-xs font-mono text-[#E2E8F0] break-all">
                            Selected File: {selectedFileName || 'None'}
                          </div>
                          {simulatedContentUri && (
                            <div className="text-[11px] font-mono text-[#64748B] break-all">
                              OpenableColumns.DISPLAY_NAME → &quot;{selectedFileName}&quot;
                            </div>
                          )}
                          <div className="text-xs font-mono text-[#80CBC4] break-all">
                            {statusMessage}
                          </div>

                          {signedArtifact && (
                            <div className="pt-2 mt-2 border-t border-[#1E293B] space-y-2">
                              <div className="flex items-center justify-between text-[11px] font-mono text-[#00E676]">
                                <span>BouncyCastle RSA-2048 • V1+V2+V3</span>
                                <span>PASS</span>
                              </div>
                              <div className="text-[10px] font-mono text-[#94A3B8] break-all">
                                SHA-256: {signedArtifact.sha256.slice(0, 47)}...
                              </div>
                              <button
                                onClick={handleDownloadSignedApkDemo}
                                className="w-full py-1.5 rounded-lg bg-[#00E676]/15 hover:bg-[#00E676]/25 border border-[#00E676]/40 text-[#00E676] text-xs font-mono font-semibold flex items-center justify-center gap-1.5 transition cursor-pointer"
                              >
                                <Download className="w-3.5 h-3.5" />
                                Download {signedArtifact.signedName}
                              </button>
                            </div>
                          )}
                        </div>
                      </div>
                    )}

                    {/* SCREEN 2: CONFIGURATION SCREEN (.zip / .html) */}
                    {currentScreen === 'CONFIG' && (
                      <div className="space-y-3.5">
                        <div>
                          <h2 className="text-lg font-bold text-white">Configuration Screen</h2>
                          <p className="text-xs font-mono text-[#00E676] mt-0.5">
                            Source Payload: {selectedFileName}
                          </p>
                        </div>

                        <div className="space-y-1">
                          <label className="text-xs font-medium text-[#94A3B8]">App Name</label>
                          <input
                            type="text"
                            value={appName}
                            onChange={(e) => setAppName(e.target.value)}
                            className="w-full px-3.5 py-2.5 rounded-lg bg-[#121721] border border-[#2D3B4E] focus:border-[#00E676] focus:outline-none text-sm text-white"
                            placeholder="Enter Android App Name"
                          />
                        </div>

                        <div className="rounded-xl bg-[#121721] border border-[#1E293B] p-3.5 space-y-2.5">
                          <div className="flex items-center justify-between">
                            <span className="text-xs text-[#E2E8F0]">
                              Custom Icon: <span className="font-mono text-[#00E676]">{customIconName}</span>
                            </span>
                            {customIconPreview && (
                              <img
                                src={customIconPreview}
                                alt="Custom Icon"
                                className="w-7 h-7 rounded-md object-cover border border-[#00E676]"
                              />
                            )}
                          </div>
                          <button
                            onClick={() => iconFileInputRef.current?.click()}
                            className="w-full py-2 rounded-lg border border-[#2D3B4E] hover:border-[#00E676] text-xs font-semibold text-[#ECEFF4] flex items-center justify-center gap-1.5 transition cursor-pointer"
                          >
                            <ImageIcon className="w-3.5 h-3.5 text-[#00E676]" />
                            Pick Custom Icon (image/*)
                          </button>
                        </div>

                        <div className="rounded-xl bg-[#121721] border border-[#1E293B] p-3.5 space-y-2.5">
                          <div className="flex items-center justify-between">
                            <span className="text-xs text-[#E2E8F0]">
                              Splash Screen:{' '}
                              <span className="font-mono text-[#00B0FF]">{splashScreenName}</span>
                            </span>
                            {splashPreview && (
                              <img
                                src={splashPreview}
                                alt="Splash Preview"
                                className="w-7 h-7 rounded-md object-cover border border-[#00B0FF]"
                              />
                            )}
                          </div>
                          <button
                            onClick={() => splashFileInputRef.current?.click()}
                            className="w-full py-2 rounded-lg border border-[#2D3B4E] hover:border-[#00B0FF] text-xs font-semibold text-[#ECEFF4] flex items-center justify-center gap-1.5 transition cursor-pointer"
                          >
                            <ImageIcon className="w-3.5 h-3.5 text-[#00B0FF]" />
                            Pick Splash Screen (image/*)
                          </button>
                        </div>

                        <button
                          onClick={() => setCurrentScreen('PROGRESS')}
                          className="w-full h-12 rounded-xl bg-[#00E676] hover:bg-[#00C853] text-black font-bold text-sm flex items-center justify-center gap-2 shadow-lg shadow-[#00E676]/20 transition cursor-pointer"
                        >
                          <GitBranch className="w-4 h-4" />
                          Build via GitHub
                        </button>
                      </div>
                    )}

                    {/* SCREEN 3: SETTINGS SCREEN (SharedPreferences) */}
                    {currentScreen === 'SETTINGS' && (
                      <div className="space-y-4">
                        <div>
                          <h2 className="text-lg font-bold text-white">Settings Screen</h2>
                          <p className="text-xs text-[#94A3B8] mt-0.5">
                            Configure credentials for GitHub Actions &amp; AI Auto-Builder. Persisted securely in SharedPreferences.
                          </p>
                        </div>

                        <div className="space-y-1.5">
                          <label className="text-xs font-medium text-[#94A3B8]">
                            GitHub PAT (ghp_...)
                          </label>
                          <input
                            type="password"
                            value={githubPat}
                            onChange={(e) => setGithubPat(e.target.value)}
                            placeholder="ghp_xxxxxxxxxxxxxxxxxxxx"
                            className="w-full px-3.5 py-2.5 rounded-lg bg-[#121721] border border-[#2D3B4E] focus:border-[#00E676] focus:outline-none text-sm font-mono text-white"
                          />
                        </div>

                        <div className="space-y-1.5">
                          <label className="text-xs font-medium text-[#94A3B8]">
                            AI API Key
                          </label>
                          <input
                            type="password"
                            value={aiApiKey}
                            onChange={(e) => setAiApiKey(e.target.value)}
                            placeholder="sk-xxxxxxxxxxxxxxxxxxxx"
                            className="w-full px-3.5 py-2.5 rounded-lg bg-[#121721] border border-[#2D3B4E] focus:border-[#00E676] focus:outline-none text-sm font-mono text-white"
                          />
                        </div>

                        <button
                          onClick={handleSaveSettings}
                          className="w-full h-12 rounded-xl bg-[#00E676] hover:bg-[#00C853] text-black font-bold text-sm flex items-center justify-center gap-2 transition cursor-pointer"
                        >
                          <KeyRound className="w-4 h-4" />
                          Save Credentials
                        </button>

                        {/* SharedPreferences Live XML Preview */}
                        <div className="rounded-xl bg-[#0E131B] border border-[#1E293B] p-3 space-y-1.5">
                          <div className="text-[10px] font-mono text-[#00E676] uppercase">
                            /data/data/com.zelsigns.app/shared_prefs/zel_signs_prefs.xml
                          </div>
                          <pre className="text-[11px] font-mono text-[#94A3B8] overflow-x-auto">
{`<map>
  <string name="github_pat">${githubPat ? githubPat.slice(0, 7) + '••••••' : ''}</string>
  <string name="ai_api_key">${aiApiKey ? aiApiKey.slice(0, 6) + '••••••' : ''}</string>
</map>`}
                          </pre>
                        </div>
                      </div>
                    )}

                    {/* SCREEN 4: PROGRESS SCREEN (GitHub / AI Loop) */}
                    {currentScreen === 'PROGRESS' && (
                      <div className="space-y-3.5 flex flex-col h-[460px]">
                        <div>
                          <h2 className="text-lg font-bold text-white">
                            GitHub &amp; AI Build Progress
                          </h2>
                          <p className="text-xs text-[#94A3B8]">
                            2-second coroutine step telemetry for <span className="text-[#00E676] font-mono">{appName}</span>
                          </p>
                        </div>

                        {!isBuildCompleted && (
                          <div className="w-full bg-[#192230] h-1.5 rounded-full overflow-hidden">
                            <div className="bg-[#00E676] h-full w-3/4 animate-pulse" />
                          </div>
                        )}

                        <div className="flex-1 rounded-xl bg-[#070A0E] border border-[#1E293B] p-3.5 overflow-y-auto space-y-2.5 font-mono text-xs">
                          {progressLogs.map((line, i) => (
                            <div
                              key={i}
                              className={
                                line.includes('Build succeeded')
                                  ? 'text-[#00E676] font-bold'
                                  : 'text-[#E2E8F0]'
                              }
                            >
                              {line}
                            </div>
                          ))}
                          {!isBuildCompleted && (
                            <div className="flex items-center gap-2 text-[#00B0FF] animate-pulse">
                              <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                              <span>Awaiting next GitHub / AI agent step (2s delay)...</span>
                            </div>
                          )}
                        </div>

                        <button
                          onClick={() => setCurrentScreen('MAIN')}
                          disabled={!isBuildCompleted}
                          className="w-full h-11 rounded-xl bg-[#00E676] disabled:bg-[#1E293B] disabled:text-[#64748B] text-black font-bold text-sm transition cursor-pointer"
                        >
                          {isBuildCompleted ? 'Finish & Return Home' : 'Running CI/CD Loop...'}
                        </button>
                      </div>
                    )}
                  </div>

                  {/* Authentic Android Toast Overlay */}
                  {androidToast && (
                    <div className="absolute bottom-12 left-4 right-4 bg-[#222B38]/95 border border-[#38465B] text-white text-xs px-4 py-2.5 rounded-2xl shadow-xl flex items-center gap-2 z-20 animate-fade-in">
                      <CheckCircle2 className="w-4 h-4 text-[#00E676] shrink-0" />
                      <span className="truncate font-mono text-[11px]">{androidToast}</span>
                    </div>
                  )}

                  {/* Android Gesture Navigation Pill */}
                  <div className="pt-4 flex justify-center">
                    <div className="w-32 h-1 rounded-full bg-[#2D3B4E]" />
                  </div>
                </div>
              </div>
            </div>

            {/* Right Column: Native Kotlin & Gradle Source Code Explorer */}
            <div className="lg:col-span-7 space-y-4">
              {/* Architecture Verification Cards */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div className="rounded-xl bg-[#0D121B] border border-[#1A2436] p-3.5">
                  <div className="flex items-center justify-between text-xs font-semibold text-[#00E676] mb-1">
                    <span>OpenableColumns Fix</span>
                    <CheckCircle2 className="w-4 h-4" />
                  </div>
                  <p className="text-[11px] text-[#94A3B8]">
                    Queries <code className="text-white">ContentResolver</code> for{' '}
                    <code className="text-white">OpenableColumns.DISPLAY_NAME</code>. Never calls{' '}
                    <code className="text-rose-400">uri.lastPathSegment</code>.
                  </p>
                </div>

                <div className="rounded-xl bg-[#0D121B] border border-[#1A2436] p-3.5">
                  <div className="flex items-center justify-between text-xs font-semibold text-[#00B0FF] mb-1">
                    <span>BouncyCastle Packaging</span>
                    <CheckCircle2 className="w-4 h-4" />
                  </div>
                  <p className="text-[11px] text-[#94A3B8]">
                    Excludes <code className="text-white">META-INF/versions/9/OSGI-INF/MANIFEST.MF</code> &amp; LICENSE/NOTICE in <code className="text-white">app/build.gradle.kts</code>.
                  </p>
                </div>

                <div className="rounded-xl bg-[#0D121B] border border-[#1A2436] p-3.5">
                  <div className="flex items-center justify-between text-xs font-semibold text-amber-400 mb-1">
                    <span>CI License Bypass</span>
                    <CheckCircle2 className="w-4 h-4" />
                  </div>
                  <p className="text-[11px] text-[#94A3B8]">
                    Exact <code className="text-white">build-apk.yml</code> workflow with manual SDK license acceptance &amp; Gradle 8.7 binary.
                  </p>
                </div>
              </div>

              {/* Code Explorer Card */}
              <div className="rounded-2xl bg-[#0B0F17] border border-[#1B2434] overflow-hidden shadow-xl">
                <div className="px-4 py-3 bg-[#101622] border-b border-[#1B2434] flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <FileCode2 className="w-4 h-4 text-[#00E676]" />
                    <span className="text-xs font-bold uppercase tracking-wider text-white">
                      Required Project Files (Click to Inspect)
                    </span>
                  </div>

                  <button
                    onClick={() =>
                      handleCopyText(
                        `${selectedCodeFile.header}\n${selectedCodeFile.content}`,
                        selectedCodeFile.id
                      )
                    }
                    className="flex items-center gap-1.5 px-3 py-1 rounded-lg bg-[#182232] hover:bg-[#223046] text-xs font-mono text-[#00E676] border border-[#00E676]/30 transition cursor-pointer"
                  >
                    {copiedId === selectedCodeFile.id ? (
                      <>
                        <Check className="w-3.5 h-3.5" />
                        Copied!
                      </>
                    ) : (
                      <>
                        <Copy className="w-3.5 h-3.5" />
                        Copy {selectedCodeFile.path}
                      </>
                    )}
                  </button>
                </div>

                {/* File Pills Selector */}
                <div className="p-2.5 bg-[#0D121B] border-b border-[#1B2434] flex flex-wrap gap-1.5">
                  {ANDROID_PROJECT_FILES.map((file) => {
                    const active = selectedCodeFile.id === file.id;
                    return (
                      <button
                        key={file.id}
                        onClick={() => setSelectedCodeFile(file)}
                        className={`px-2.5 py-1.5 rounded-lg text-xs font-mono transition flex items-center gap-1.5 cursor-pointer ${
                          active
                            ? 'bg-[#00E676] text-black font-bold'
                            : 'bg-[#131B28] text-[#94A3B8] hover:text-white hover:bg-[#1A2536]'
                        }`}
                      >
                        <span className="opacity-60">{file.index}.</span>
                        <span>{file.path}</span>
                      </button>
                    );
                  })}
                </div>

                {/* Selected File Header & Description */}
                <div className="px-4 py-2.5 bg-[#090D14] border-b border-[#161F2E] flex items-center justify-between text-xs">
                  <span className="font-mono text-[#00E676] font-semibold">
                    {selectedCodeFile.header}
                  </span>
                  <span className="text-[#94A3B8] hidden sm:inline">
                    {selectedCodeFile.description}
                  </span>
                </div>

                {/* Code Viewer */}
                <div className="p-4 max-h-[480px] overflow-auto bg-[#070A0F]">
                  <pre className="text-xs font-mono text-[#E2E8F0] leading-relaxed whitespace-pre">
                    {selectedCodeFile.content}
                  </pre>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: ALL 8 FILES STACKED LIST */}
        {activeTab === 'files' && (
          <div className="space-y-6">
            <div className="flex flex-wrap items-center justify-between gap-4 bg-[#0D121B] border border-[#1B2434] rounded-xl p-4">
              <div>
                <h2 className="text-base font-bold text-white">
                  All 8 Required Native Android Project Files
                </h2>
                <p className="text-xs text-[#94A3B8]">
                  Every file adheres strictly to JDK 17, Gradle 8.7, AGP 8.4.0, Kotlin 1.9.24, Compose Compiler 1.5.14, and BouncyCastle 1.78.1.
                </p>
              </div>
              <button
                onClick={() => handleCopyText(getAllFormattedOutput(), 'all-files')}
                className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[#00E676] hover:bg-[#00C853] text-black font-bold text-xs transition cursor-pointer"
              >
                {copiedId === 'all-files' ? (
                  <>
                    <Check className="w-4 h-4" />
                    Copied Complete 8-File Output!
                  </>
                ) : (
                  <>
                    <Copy className="w-4 h-4" />
                    Copy All 8 Files with === Headers ===
                  </>
                )}
              </button>
            </div>

            {ANDROID_PROJECT_FILES.map((file) => (
              <div
                key={file.id}
                className="rounded-xl bg-[#0B0F17] border border-[#1B2434] overflow-hidden"
              >
                <div className="px-4 py-3 bg-[#101622] border-b border-[#1B2434] flex items-center justify-between">
                  <div>
                    <div className="text-sm font-mono font-bold text-[#00E676]">
                      {file.header}
                    </div>
                    <div className="text-xs text-[#94A3B8] mt-0.5">{file.description}</div>
                  </div>
                  <button
                    onClick={() =>
                      handleCopyText(`${file.header}\n${file.content}`, file.id)
                    }
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[#182232] hover:bg-[#223046] text-xs font-mono text-[#00E676] border border-[#00E676]/30 transition cursor-pointer"
                  >
                    {copiedId === file.id ? (
                      <>
                        <Check className="w-3.5 h-3.5" />
                        Copied
                      </>
                    ) : (
                      <>
                        <Copy className="w-3.5 h-3.5" />
                        Copy File
                      </>
                    )}
                  </button>
                </div>
                <div className="p-4 overflow-x-auto bg-[#070A0F]">
                  <pre className="text-xs font-mono text-[#E2E8F0] leading-relaxed">
                    {file.content}
                  </pre>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* TAB 3: FULL RAW BUNDLE WITH === HEADERS === */}
        {activeTab === 'raw-bundle' && (
          <div className="rounded-xl bg-[#0B0F17] border border-[#1B2434] overflow-hidden">
            <div className="px-4 py-3 bg-[#101622] border-b border-[#1B2434] flex items-center justify-between">
              <div className="text-sm font-mono font-bold text-[#00E676]">
                Complete 8-File Deliverable (=== file === Header Format)
              </div>
              <button
                onClick={() => handleCopyText(getAllFormattedOutput(), 'raw-bundle-btn')}
                className="flex items-center gap-1.5 px-4 py-1.5 rounded-lg bg-[#00E676] text-black font-bold text-xs transition cursor-pointer"
              >
                {copiedId === 'raw-bundle-btn' ? (
                  <>
                    <Check className="w-3.5 h-3.5" />
                    Copied All!
                  </>
                ) : (
                  <>
                    <Copy className="w-3.5 h-3.5" />
                    Copy Full Output
                  </>
                )}
              </button>
            </div>
            <div className="p-4 overflow-x-auto bg-[#070A0F]">
              <pre className="text-xs font-mono text-[#E2E8F0] leading-relaxed">
                {getAllFormattedOutput()}
              </pre>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
export default App;
