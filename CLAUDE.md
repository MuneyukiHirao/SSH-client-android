# SSH Terminal for Android - 開発ドキュメント

## プロジェクト概要

Pixel 9aで動作する公開鍵認証対応のSSHターミナルアプリ。
日本語キーボード（12キー/ローマ字）完全対応。

## 技術スタック

- **言語**: Kotlin
- **UI**: Jetpack Compose
- **SSH**: sshlib (ConnectBot派生)
- **DB**: Room
- **DI**: Koin
- **鍵管理**: Android Keystore + BouncyCastle

## 現在の実装状況

### 完了済み

- [x] プロジェクト基本構造
- [x] Gradle設定 (Kotlin DSL)
- [x] Room Database (Host, PortForward)
- [x] KeyStoreManager (RSA/Ed25519/ECDSA鍵生成)
- [x] SSHClient Wrapper (sshlib)
- [x] TerminalView + 日本語IME対応
- [x] 特殊キーツールバー（Ctrl, Tab, 記号等）
- [x] UI画面 (ホスト一覧, 鍵管理, ターミナル, 設定)
- [x] ポート転送UI (Local/Remote/Dynamic)
- [x] 設定画面の永続化（DataStore） - SettingsRepository + SettingsViewModel
- [x] Ed25519鍵の暗号化永続化（EncryptedSharedPreferences）
- [x] ホスト鍵検証ダイアログの改善（新規/変更の区別、詳細表示）
- [x] マルチセッション対応（SessionManager, タブUI, セッション切り替え）

### 未実装・要改善
- [ ] SSHエージェント転送
- [ ] SFTPファイル転送
- [x] セッション復帰（バックグラウンド接続維持）- Foreground Service, Keep-Alive, 自動再接続, 永続化
- [ ] カスタムフォント設定
- [x] カラーテーマ設定 - アプリテーマ(Light/Dark/System)、ターミナルカラースキーム(7種類)
- [x] Auto-tmux機能 - SSH接続時に自動的にtmuxセッションをアタッチ/作成
- [x] ユニットテスト・UIテスト追加
- [x] パフォーマンス最適化 (dirty region tracking, hardware acceleration)

## 次のステップ

### Phase 1: 実機テスト（Pixel 9a）
1. 日本語12キー入力テスト
2. ローマ字入力テスト
3. 特殊キー（Ctrl-C, Tab等）テスト
4. SSH接続テスト
5. tmux自動接続テスト

### Phase 2: 追加機能
1. SSHエージェント転送
2. SFTPファイル転送
3. カスタムフォント設定

## ディレクトリ構造

```
app/src/main/java/com/example/sshterminal/
├── MainActivity.kt
├── SSHTerminalApplication.kt
├── di/
│   └── AppModule.kt              # Koin DI設定
├── domain/model/
│   ├── Host.kt                   # ホスト情報
│   ├── SSHKey.kt                 # 鍵情報
│   ├── PortForward.kt            # ポート転送設定
│   └── ThemeSettings.kt          # テーマ設定（ThemeMode, TerminalColorScheme）
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt        # Room DB
│   │   └── dao/
│   │       ├── HostDao.kt
│   │       └── PortForwardDao.kt
│   ├── repository/
│   │   └── SettingsRepository.kt # DataStore設定管理
│   └── ssh/
│       ├── KeyStoreManager.kt    # 鍵生成・管理
│       ├── SSHClient.kt          # SSH接続
│       ├── SSHConnectionService.kt
│       └── SessionManager.kt     # マルチセッション管理
├── viewmodel/
│   ├── HostViewModel.kt
│   ├── KeyViewModel.kt
│   ├── TerminalViewModel.kt
│   ├── PortForwardViewModel.kt
│   ├── SettingsViewModel.kt      # 設定画面ViewModel
│   └── MultiSessionViewModel.kt  # マルチセッションViewModel
└── ui/
    ├── SSHTerminalApp.kt         # ナビゲーション
    ├── theme/
    │   └── Theme.kt
    ├── components/
    │   ├── TerminalEmulator.kt   # VT100エミュレーション
    │   └── TerminalView.kt       # IME対応View
    └── screens/
        ├── HostListScreen.kt
        ├── HostEditScreen.kt
        ├── KeyManagerScreen.kt
        ├── TerminalScreen.kt
        ├── MultiSessionTerminalScreen.kt # マルチセッション対応ターミナル
        ├── PortForwardScreen.kt
        └── SettingsScreen.kt
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
