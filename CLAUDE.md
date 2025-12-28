# SSH Terminal for Android - 開発ドキュメント

## プロジェクト概要

Pixel 9aで動作する公開鍵認証対応のSSHターミナルアプリ。
日本語キーボード（12キー/ローマ字）完全対応。

## 技術スタック

- **言語**: Kotlin 1.9.22
- **UI**: Jetpack Compose (Material 3)
- **SSH**: sshlib 2.2.21 (ConnectBot派生)
- **DB**: Room 2.6.1
- **DI**: Koin 3.5.3
- **非同期**: Kotlin Coroutines + Flow
- **設定**: DataStore Preferences
- **鍵管理**: Android Keystore + BouncyCastle 1.77
- **最小SDK**: 26 (Android 8.0)
- **ターゲットSDK**: 34 (Android 14)

## アーキテクチャ概要

本プロジェクトはClean Architectureに基づいた3層構造を採用：

```
┌─────────────────────────────────────────────┐
│            Presentation Layer               │
│  (ui/, viewmodel/)                          │
│  - Jetpack Compose UI                       │
│  - ViewModel (状態管理)                      │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│              Data Layer                     │
│  (data/)                                    │
│  - Repository (データアクセス抽象化)          │
│  - DAO (Room Database)                      │
│  - SSH関連 (SessionManager, SSHClient)      │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│              Domain Layer                   │
│  (domain/model/)                            │
│  - Entity (Host, SSHKey, etc.)              │
│  - ビジネスモデル                            │
└─────────────────────────────────────────────┘
```

詳細は [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) を参照。

## 現在の実装状況

### 完了済み

**コア機能**
- [x] プロジェクト基本構造 (Clean Architecture)
- [x] Gradle設定 (Kotlin DSL)
- [x] Koin DIモジュール

**データベース・永続化**
- [x] Room Database (Host, PortForward, ActiveSession)
- [x] DataStore設定管理 (SettingsRepository)
- [x] Ed25519鍵の暗号化永続化

**SSH接続**
- [x] KeyStoreManager (RSA/Ed25519/ECDSA鍵生成)
- [x] SSHClient Wrapper (sshlib)
- [x] ホスト鍵検証ダイアログ（新規/変更の区別、詳細表示）
- [x] ポート転送 (Local/Remote/Dynamic)

**マルチセッション・バックグラウンド**
- [x] SessionManager (マルチセッション管理)
- [x] SSHConnectionService (Foreground Service)
- [x] Keep-Alive機能
- [x] 自動再接続
- [x] セッション永続化・復帰

**ターミナル**
- [x] TerminalEmulator (VT100エミュレーション)
- [x] TerminalView + 日本語IME対応
- [x] 特殊キーツールバー（Ctrl, Tab, 記号等）
- [x] パフォーマンス最適化 (dirty region tracking, hardware acceleration)

**UI画面**
- [x] ホスト一覧・編集画面
- [x] 鍵管理画面
- [x] ターミナル画面
- [x] マルチセッションターミナル画面（タブUI）
- [x] ポート転送設定画面
- [x] 設定画面

**テーマ・カスタマイズ**
- [x] アプリテーマ (Light/Dark/System)
- [x] ターミナルカラースキーム (7種類)
- [x] Auto-tmux機能

**テスト**
- [x] ユニットテスト (TerminalEmulator, ThemeSettings, SettingsViewModel)
- [x] UIテスト (HostListScreen, SettingsScreen)

### 未実装・要改善

- [ ] SSHエージェント転送
- [ ] SFTPファイル転送
- [ ] カスタムフォント設定

## ディレクトリ構造

```
app/src/main/java/com/example/sshterminal/
├── MainActivity.kt                 # アプリエントリーポイント
├── SSHTerminalApplication.kt       # Applicationクラス (Koin初期化)
│
├── di/
│   └── AppModule.kt                # Koin DI設定
│       └── ServiceConnectionHolder # SSHConnectionServiceへの参照保持
│
├── domain/
│   └── model/
│       ├── Host.kt                 # ホスト情報エンティティ
│       ├── SSHKey.kt               # SSH鍵情報
│       ├── PortForward.kt          # ポート転送設定
│       ├── ThemeSettings.kt        # テーマ設定 (ThemeMode, TerminalColorScheme)
│       └── ActiveSession.kt        # アクティブセッション永続化用エンティティ
│
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt          # Room Database定義
│   │   └── dao/
│   │       ├── HostDao.kt          # ホストDAO
│   │       ├── PortForwardDao.kt   # ポート転送DAO
│   │       └── ActiveSessionDao.kt # アクティブセッションDAO
│   │
│   ├── repository/
│   │   └── SettingsRepository.kt   # DataStore設定管理
│   │
│   └── ssh/
│       ├── KeyStoreManager.kt      # 鍵生成・管理
│       ├── SSHClient.kt            # SSH接続ラッパー
│       ├── SSHConnectionService.kt # Foreground Service (接続維持)
│       └── SessionManager.kt       # マルチセッション管理
│
├── viewmodel/
│   ├── HostViewModel.kt            # ホスト一覧ViewModel
│   ├── KeyViewModel.kt             # 鍵管理ViewModel
│   ├── TerminalViewModel.kt        # ターミナルViewModel
│   ├── PortForwardViewModel.kt     # ポート転送ViewModel
│   ├── SettingsViewModel.kt        # 設定画面ViewModel
│   └── MultiSessionViewModel.kt    # マルチセッションViewModel
│
└── ui/
    ├── SSHTerminalApp.kt           # Navigation Compose
    │
    ├── theme/
    │   └── Theme.kt                # Material 3テーマ定義
    │
    ├── components/
    │   ├── TerminalEmulator.kt     # VT100ターミナルエミュレーション
    │   └── TerminalView.kt         # IME対応カスタムView
    │
    └── screens/
        ├── HostListScreen.kt       # ホスト一覧
        ├── HostEditScreen.kt       # ホスト編集
        ├── KeyManagerScreen.kt     # 鍵管理
        ├── TerminalScreen.kt       # シングルセッションターミナル
        ├── MultiSessionTerminalScreen.kt  # マルチセッションターミナル
        ├── PortForwardScreen.kt    # ポート転送設定
        └── SettingsScreen.kt       # 設定

app/src/test/java/com/example/sshterminal/
├── TerminalEmulatorTest.kt         # ターミナルエミュレータテスト
├── ThemeSettingsTest.kt            # テーマ設定テスト
└── SettingsViewModelTest.kt        # 設定ViewModelテスト

app/src/androidTest/java/com/example/sshterminal/
├── HostListScreenTest.kt           # ホスト一覧UIテスト
└── SettingsScreenTest.kt           # 設定画面UIテスト
```

## 日本語入力対応のポイント

`TerminalView.kt` の `TerminalInputConnection`:

```kotlin
// IMEからの確定文字列
override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
    text?.let { sendText(it.toString()) }
    return true
}

// 変換中の文字列（12キー/ローマ字）
override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
    composingText = text?.toString() ?: ""
    invalidate()  // 画面に変換中テキストを表示
    return true
}
```

## 注意事項

### ライセンス
- sshlib: Apache 2.0
- BouncyCastle: MIT
- Termuxコンポーネントを参考にする場合: GPL 3.0に注意

### セキュリティ
- 秘密鍵はAndroid Keystoreに保存
- Ed25519はBouncyCastleで生成（Keystore非対応のため）
- バックアップから秘密鍵を除外 (`backup_rules.xml`)

## Claude Code作業ルール

### 開発環境
- **adbパス**: `/home/mhirao/android-sdk/platform-tools/adb`
- Android SDKは `/home/mhirao/android-sdk/` にインストール済み

### デバッグファイルの整理
- スクリーンショットは `debug/screenshots/` フォルダに保存
- UIダンプは `debug/ui_dumps/` フォルダに保存

### スクリーンショット保存時の制限
- **重要**: スクリーンショットは長辺2000ピクセル以下に縮小してから保存すること
- API制限により2000px超の画像はエラーになり復帰不可能になる
  ```bash
  adb exec-out screencap -p > debug/screenshots/screenshot_raw.png
  convert debug/screenshots/screenshot_raw.png -resize '2000x2000>' debug/screenshots/screenshot.png
  rm debug/screenshots/screenshot_raw.png
  ```

### UIダンプ
  ```bash
  adb shell uiautomator dump /sdcard/ui_dump.xml
  adb pull /sdcard/ui_dump.xml debug/ui_dumps/
  ```

### 自動実行
- コマンド実行やファイル作成で都度確認は不要
- 重大な意思決定・設計の妥協が必要な場合のみ確認を求める

## トラブルシューティング

### ビルドエラー: termux-terminal-view が見つからない
JitPackの代替パッケージを使用するか、ターミナルエミュレーションを自前実装（`TerminalEmulator.kt`）に切り替え。

### 日本語入力が動作しない
`EditorInfo.inputType` と `imeOptions` の設定を確認。
`TYPE_TEXT_VARIATION_VISIBLE_PASSWORD` が重要。

### SSH接続タイムアウト
`Connection.connect()` のタイムアウト値を調整（現在30秒）。

### sshlibとTinkのクラス競合
EncryptedSharedPreferencesの代わりにAndroid Keystoreを直接使用。
