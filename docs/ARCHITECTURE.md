# SSH Terminal for Android - アーキテクチャ設計

## 概要

本プロジェクトはClean Architectureに基づいた3層構造を採用しています。
各層は明確な責務を持ち、依存関係は内側に向かうように設計されています。

## レイヤー構成

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                        │
│  ┌─────────────────┐  ┌─────────────────────────────────┐   │
│  │    ViewModel    │  │        Jetpack Compose UI        │   │
│  │  (状態管理)      │  │  (Screen, Component, Theme)     │   │
│  └─────────────────┘  └─────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                       Data Layer                             │
│  ┌───────────────┐  ┌───────────────┐  ┌────────────────┐  │
│  │  Repository   │  │      DAO      │  │  SSH Manager   │  │
│  │(データ抽象化)  │  │ (Room DB)     │  │(SSHClient etc) │  │
│  └───────────────┘  └───────────────┘  └────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                      Domain Layer                            │
│  ┌────────────────────────────────────────────────────────┐ │
│  │                      Entity                             │ │
│  │     Host, SSHKey, PortForward, ThemeSettings, etc.     │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## 各レイヤーの詳細

### Domain Layer (domain/)

ビジネスロジックとエンティティを定義する層。他のレイヤーへの依存がない純粋なKotlinコード。

#### エンティティ

| クラス | 説明 |
|--------|------|
| `Host` | SSH接続先ホストの情報（ホスト名、ポート、ユーザー名、認証方法等） |
| `SSHKey` | SSH鍵の情報（鍵タイプ、公開鍵、フィンガープリント等） |
| `PortForward` | ポート転送設定（タイプ、ローカル/リモートポート） |
| `ThemeSettings` | テーマ設定（ThemeMode, TerminalColorScheme） |
| `ActiveSession` | アクティブセッション情報（セッション復帰用） |

```kotlin
// Host.kt
@Entity(tableName = "hosts")
data class Host(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val authMethod: AuthMethod,
    val keyId: Long? = null,
    // ...
)
```

### Data Layer (data/)

データアクセスとビジネスロジックの実装を担当する層。

#### Repository

| クラス | 責務 |
|--------|------|
| `SettingsRepository` | DataStoreを使用したアプリ設定の永続化 |

```kotlin
// SettingsRepository.kt
class SettingsRepository(private val context: Context) {
    val fontSize: Flow<Float>
    val themeMode: Flow<ThemeMode>
    val terminalColorScheme: Flow<TerminalColorScheme>
    // ...

    suspend fun setFontSize(size: Float)
    suspend fun setThemeMode(mode: ThemeMode)
    // ...
}
```

#### DAO (Data Access Object)

| クラス | 責務 |
|--------|------|
| `HostDao` | ホスト情報のCRUD操作 |
| `PortForwardDao` | ポート転送設定のCRUD操作 |
| `ActiveSessionDao` | アクティブセッションの永続化 |

#### SSH関連

| クラス | 責務 |
|--------|------|
| `KeyStoreManager` | SSH鍵の生成・管理（RSA, Ed25519, ECDSA） |
| `SSHClient` | sshlibをラップしたSSH接続クライアント |
| `SessionManager` | マルチセッション管理、Keep-Alive、自動再接続 |
| `SSHConnectionService` | Foreground Serviceによるバックグラウンド接続維持 |

### Presentation Layer (viewmodel/, ui/)

UIとユーザーインタラクションを担当する層。

#### ViewModel

| クラス | 責務 |
|--------|------|
| `HostViewModel` | ホスト一覧の状態管理 |
| `KeyViewModel` | 鍵管理の状態管理 |
| `TerminalViewModel` | シングルセッションターミナルの状態管理 |
| `MultiSessionViewModel` | マルチセッションターミナルの状態管理 |
| `PortForwardViewModel` | ポート転送設定の状態管理 |
| `SettingsViewModel` | アプリ設定の状態管理 |

#### UI Components

| カテゴリ | コンポーネント | 説明 |
|----------|----------------|------|
| Screen | `HostListScreen` | ホスト一覧表示 |
| Screen | `HostEditScreen` | ホスト編集フォーム |
| Screen | `KeyManagerScreen` | SSH鍵管理 |
| Screen | `TerminalScreen` | シングルセッションターミナル |
| Screen | `MultiSessionTerminalScreen` | マルチセッションターミナル（タブUI） |
| Screen | `PortForwardScreen` | ポート転送設定 |
| Screen | `SettingsScreen` | アプリ設定 |
| Component | `TerminalView` | IME対応ターミナルビュー |
| Component | `TerminalEmulator` | VT100ターミナルエミュレーション |

## 依存関係

### Dependency Injection (Koin)

```kotlin
// AppModule.kt
val appModule = module {
    // Database
    single { AppDatabase.getInstance(androidContext()) }
    single { get<AppDatabase>().hostDao() }
    single { get<AppDatabase>().portForwardDao() }
    single { get<AppDatabase>().activeSessionDao() }

    // Repositories
    single { SettingsRepository(androidContext()) }

    // Managers
    single { KeyStoreManager(androidContext()) }
    single { ServiceConnectionHolder() }

    // ViewModels
    viewModel { HostViewModel(get()) }
    viewModel { KeyViewModel(get()) }
    viewModel { TerminalViewModel(get(), get(), get()) }
    viewModel { PortForwardViewModel(get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { MultiSessionViewModel(get(), get(), get()) }
}
```

### 依存関係の方向

```
ViewModel → Repository → DAO → Entity
     ↓
ViewModel → SSH Manager → SSHClient
     ↓
Screen → ViewModel (状態を観察)
```

## 主要コンポーネントの詳細

### SessionManager

マルチセッション管理の中核コンポーネント。

**主要機能:**
- 複数SSH接続の管理（最大10セッション）
- Keep-Aliveによる接続維持（30秒/60秒間隔）
- 接続断時の自動再接続（最大3回）
- セッション状態の永続化と復帰
- ネットワーク状態監視との連携

```kotlin
class SessionManager(
    private val keyStoreManager: KeyStoreManager,
    private val activeSessionDao: ActiveSessionDao? = null,
    private val settingsRepository: SettingsRepository? = null
) {
    val sessionList: StateFlow<List<TerminalSession>>
    val activeSessionId: StateFlow<String?>
    val terminalData: SharedFlow<Pair<String, ByteArray>>
    val sessionStateChanged: SharedFlow<Pair<String, SessionState>>

    fun createSession(hostId: Long, hostName: String): TerminalSession?
    suspend fun connectSession(sessionId: String, host: Host, ...): Result<Unit>
    fun switchToSession(sessionId: String)
    fun closeSession(sessionId: String)
    // ...
}
```

### SSHConnectionService

Foreground Serviceとしてバックグラウンドでセッションを維持。

**主要機能:**
- Foreground Service通知
- WakeLockによるCPU維持
- ネットワーク状態監視
- SessionManagerのライフサイクル管理

```kotlin
class SSHConnectionService : Service() {
    val sessionManager: SessionManager
    val isInForeground: StateFlow<Boolean>
    val isNetworkAvailable: StateFlow<Boolean>

    fun initializeSessionManager(...)
    fun onAppForeground()
    fun onAppBackground()
}
```

### TerminalEmulator

VT100互換のターミナルエミュレーション。

**主要機能:**
- ANSIエスケープシーケンス処理
- スクロールバック対応
- カーソル制御
- 色・属性サポート
- dirty region tracking（パフォーマンス最適化）

### TerminalView

日本語IME完全対応のカスタムビュー。

**主要機能:**
- InputConnection実装によるIME連携
- 12キー/ローマ字入力対応
- 変換中テキストの表示
- 特殊キーツールバー（Ctrl, Tab, 記号等）

## データフロー

### SSH接続フロー

```
1. HostListScreen
   ↓ ホスト選択
2. MultiSessionViewModel.createAndConnectSession()
   ↓
3. SessionManager.createSession()
   ↓
4. SessionManager.connectSession()
   ↓
5. SSHClient.connect()
   ↓
6. sshlib (Connection)
   ↓
7. SessionState.Connected
   ↓
8. 自動tmuxコマンド送信（有効時）
```

### 設定変更フロー

```
1. SettingsScreen
   ↓ ユーザー操作
2. SettingsViewModel.updateXxx()
   ↓
3. SettingsRepository.setXxx()
   ↓
4. DataStore (永続化)
   ↓
5. Flow更新
   ↓
6. UI自動更新
```

## セキュリティ設計

### 鍵管理

- **RSA鍵**: Android Keystoreに直接保存
- **Ed25519/ECDSA鍵**: BouncyCastleで生成、暗号化して保存
- バックアップから秘密鍵を除外（`backup_rules.xml`）

### ホスト鍵検証

- 初回接続時: フィンガープリント表示、ユーザー確認
- 鍵変更時: 警告表示、変更理由の確認

## パフォーマンス最適化

### ターミナル描画

- dirty region tracking: 変更部分のみ再描画
- hardware acceleration: GPUによる描画高速化
- スクロールバック制限: メモリ使用量の制御

### バックグラウンド動作

- Keep-Alive間隔の調整（フォアグラウンド: 30秒、バックグラウンド: 60秒）
- WakeLockの適切な取得・解放
- セッション状態の定期永続化（10秒間隔）
