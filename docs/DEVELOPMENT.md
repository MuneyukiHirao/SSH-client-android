# SSH Terminal for Android - 開発ガイド

## 開発環境のセットアップ

### 必要な環境

- Android Studio Hedgehog (2023.1.1) 以降
- JDK 17
- Android SDK 34
- Kotlin 1.9.22

### クローンとセットアップ

```bash
git clone <repository-url>
cd ssh-android

# Gradleラッパーを使用してビルド
./gradlew assembleDebug
```

## ビルド手順

### デバッグビルド

```bash
./gradlew assembleDebug
```

### リリースビルド

```bash
./gradlew assembleRelease
```

### APKの場所

- デバッグ: `app/build/outputs/apk/debug/app-debug.apk`
- リリース: `app/build/outputs/apk/release/app-release.apk`

## テストの実行

### ユニットテスト

```bash
./gradlew test
```

### Instrumentationテスト (UIテスト)

```bash
# デバイスが接続されている状態で実行
./gradlew connectedDebugAndroidTest
```

### 特定のテストクラスを実行

```bash
./gradlew test --tests "com.example.sshterminal.TerminalEmulatorTest"
```

## デバッグ方法

### ADB接続

```bash
# ADBパス（このプロジェクトの場合）
/home/mhirao/android-sdk/platform-tools/adb

# デバイス一覧
adb devices

# アプリをインストール
adb install app/build/outputs/apk/debug/app-debug.apk

# アプリを起動
adb shell am start -n com.example.sshterminal/.MainActivity
```

### スクリーンショットの取得

```bash
# 重要: 2000px以下にリサイズする必要があります
adb exec-out screencap -p > debug/screenshots/screenshot_raw.png
convert debug/screenshots/screenshot_raw.png -resize '2000x2000>' debug/screenshots/screenshot.png
rm debug/screenshots/screenshot_raw.png
```

### UIダンプの取得

```bash
adb shell uiautomator dump /sdcard/ui_dump.xml
adb pull /sdcard/ui_dump.xml debug/ui_dumps/
```

### ログの確認

```bash
# アプリのログのみ表示
adb logcat --pid=$(adb shell pidof com.example.sshterminal)

# SSHクライアント関連のログ
adb logcat -s SSHClient:* SessionManager:*
```

## コーディング規約

### パッケージ構成

```
com.example.sshterminal/
├── di/              # 依存性注入
├── domain/
│   ├── model/       # ドメインモデル
│   ├── repository/  # リポジトリインターフェース
│   └── usecase/     # ユースケース
├── data/
│   ├── local/       # ローカルデータソース (Room)
│   ├── repository/  # リポジトリ実装
│   └── ssh/         # SSH関連
├── viewmodel/       # ViewModels
├── ui/
│   ├── screens/     # Compose画面
│   ├── components/  # 再利用可能なコンポーネント
│   └── theme/       # テーマ定義
└── core/
    └── extensions/  # 拡張関数
```

### 命名規則

- **クラス**: PascalCase (`HostViewModel`)
- **関数/変数**: camelCase (`getAllHosts`)
- **定数**: UPPER_SNAKE_CASE (`ANDROID_KEYSTORE`)
- **パッケージ**: lowercase (`domain.model`)

### コードスタイル

```kotlin
// ViewModel の State 公開
private val _state = MutableStateFlow<State>(State.Initial)
val state: StateFlow<State> = _state.asStateFlow()

// UseCase のパターン
class GetAllHostsUseCase(
    private val hostRepository: HostRepository
) {
    operator fun invoke(): Flow<List<Host>> = hostRepository.getAllHosts()
}

// 拡張関数の使用
fun ViewModel.launchWithLoading(
    isLoading: MutableStateFlow<Boolean>,
    error: MutableStateFlow<String?>,
    block: suspend CoroutineScope.() -> Unit
) {
    // ...
}
```

## よくある問題と解決策

### ビルドエラー: termux-terminal-view が見つからない

自前実装の `TerminalEmulator.kt` を使用しているため、通常は発生しません。
JitPackの依存関係が問題を起こす場合は、`build.gradle.kts` を確認してください。

### 日本語入力が動作しない

`TerminalView.kt` の以下の設定を確認:

```kotlin
override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
    outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
                         InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
    outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
    // ...
}
```

### SSH接続タイムアウト

`SSHClient.kt` の `connect()` メソッドのタイムアウト値を調整:

```kotlin
val info = connection.connect(
    serverHostKeyVerifier,
    30000,  // 接続タイムアウト (ms)
    30000   // キーエクスチェンジタイムアウト (ms)
)
```

### sshlibとTinkのクラス競合

EncryptedSharedPreferencesを使用せず、Android Keystoreを直接使用しています。
`KeyStoreManager.kt` を参照してください。

## デバッグ用ディレクトリ

```
debug/
├── screenshots/     # スクリーンショット保存先
└── ui_dumps/        # UIダンプ保存先
```

これらのディレクトリは `.gitignore` で除外されています。

## リリースチェックリスト

1. [ ] 全テストがパス
2. [ ] ProGuard/R8ルールの確認
3. [ ] 署名設定の確認
4. [ ] バージョン番号の更新
5. [ ] リリースノートの作成
