# SSH Terminal for Android

Android向け公開鍵認証対応SSHターミナルアプリ

## 特徴

- **公開鍵認証**: RSA、Ed25519、ECDSA鍵に対応
- **日本語キーボード対応**: 12キー入力、ローマ字入力に完全対応
- **マルチセッション**: タブUIで複数のSSH接続を同時管理
- **ポート転送**: Local/Remote/Dynamic転送に対応
- **Auto-tmux**: SSH接続時に自動的にtmuxセッションをアタッチ/作成
- **バックグラウンド接続維持**: Foreground Serviceで接続を維持
- **カラーテーマ**: 7種類のターミナルカラースキーム

## スクリーンショット

（準備中）

## 動作環境

- Android 8.0 (API 26) 以上
- 推奨: Pixel 9a / Android 15

## 技術スタック

| カテゴリ | 技術 |
|---------|------|
| 言語 | Kotlin |
| UI | Jetpack Compose |
| SSH | sshlib (ConnectBot派生) |
| データベース | Room |
| DI | Koin |
| 鍵管理 | Android Keystore + BouncyCastle |
| 設定保存 | DataStore |

## ビルド方法

```bash
# リポジトリをクローン
git clone https://github.com/MuneyukiHirao/SSH-client-android.git
cd SSH-client-android

# ビルド
./gradlew assembleDebug

# APKは app/build/outputs/apk/debug/ に生成されます
```

## 使い方

### 1. SSH鍵の生成

1. アプリを起動し、下部の「鍵管理」タブを選択
2. 「+」ボタンをタップ
3. 鍵の種類（RSA/Ed25519/ECDSA）を選択して生成
4. 公開鍵をサーバーの `~/.ssh/authorized_keys` に追加

### 2. ホストの登録

1. 「ホスト」タブで「+」ボタンをタップ
2. ホスト名、IPアドレス、ポート、ユーザー名を入力
3. 認証方法（公開鍵/パスワード）を選択
4. 保存

### 3. 接続

1. ホスト一覧から接続先をタップ
2. 初回接続時はホスト鍵の確認ダイアログが表示されます
3. 承認すると接続完了

### 4. ポート転送（オプション）

1. ホスト編集画面で「ポート転送」を設定
2. Local/Remote/Dynamicから転送タイプを選択
3. ポート番号を設定

## セキュリティ

- 秘密鍵はAndroid Keystoreに安全に保存
- Ed25519鍵はBouncyCastleで生成し、EncryptedSharedPreferencesで暗号化保存
- バックアップから秘密鍵を除外

## 今後の予定

- [ ] SSHエージェント転送
- [ ] SFTPファイル転送
- [ ] カスタムフォント設定

## ライセンス

Apache License 2.0

### 使用ライブラリ

- [sshlib](https://github.com/AkshayAgarwal007/sshlib) - Apache 2.0
- [BouncyCastle](https://www.bouncycastle.org/) - MIT

## 作者

Muneyuki Hirao
