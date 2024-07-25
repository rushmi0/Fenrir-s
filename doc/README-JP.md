# Fenrir-s

[ภาษาไทย](README.md), [日本語](doc/README-JP.md)

**Fenrir-s**は [Nostr Protocol](https://github.com/nostr-protocol/nostr)の規定に準拠したNostr RelayでKotlin/JVMで開発されました。

このプロジェクトは個人的の使用、または、グループでの使用を目的としています。それに、Relayのポリシーを思う通りに設定できて、簡単にインストールすることができます。

## 📋 目次

- [Fenrir-s](#fenrir-s)
    - [📋 目次](#-目次)
    - [🚀 対応機能 (NIPs)](#-対応機能-nips)
    - [⚙️ Relayの設定](#️-Relayの設定)
        - [1. Relayの具体的な詳細の設定](#1-Relayの具体的な詳細の設定)
        - [2. ポリシーの設定](#2-ポリシーの設定)
        - [3. Relayの特別なサービスの選択肢](#3-Relayの特別なサービスの選択肢)
    - [🛠 インストールと使用の手順](#-インストールと使用の手順)
        - [インストール](#インストール)
        - [Cloudflare Tunnelの設定 (Optional)](#Cloudflare Tunnelの設定(Optional))
        - [Relayへのアクセス](#Relayへのアクセス)
    - [🔧 基本的な問題解決](#-基本的な問題解決)
    - [🔄 アップデート](#-アップデート)
    - [👥 プロジェクトへの参加](#-プロジェクトへの参加)
    - [📚 関連資料](#-関連資料)
    - [💬 お問い合わせとサポート](#-お問い合わせとサポート)

## 🚀 対応機能 (NIPs)

- ✅ NIP-01 Basic protocol flow
- ✅ NIP-02 Follow List
- ✅ NIP-04 Encrypted Direct Message
- ✅ NIP-09 Event Deletion
- ✅ NIP-11 Relay Information
- ✅ NIP-13 Proof of Work
- ✅ NIP-15 Marketplace
- ✅ NIP-28 Public Chat
- ⬜ NIP-40 Expiration Timestamp
- ⬜ NIP-42 Authentication of clients to relays
- ✅ NIP-50 Search Capability

## ⚙️ Relayの設定

### 1. Relayの具体的な詳細の設定

設定ファイルは [`src/main/resources/application.toml`](src/main/resources/application.toml)

```toml
[nostr.relay.info]
name = "lnwza007"
description = "นึกแล้ว มึงต้องอ่าน"
npub = "npub1ujevvncwfe22hv6d2cjv6pqwqhkvwlcvge7vgm3vcn2max9tu03sgze8ry"
contact = "rushmi0@getalby.com"
```

| パラメータ | 説明                             |
|-------------|--------------------------------|
| name        | Relayの名前                  |
| description | Relayについての説明        |
| npub        | Relayの所有者のnpub          |
| contact     | 連絡先のメールアドレス |

### 2. ポリシーの設定

設定されていない場合はデフォルトで誰でも使用できるPublic Relayになります。

```toml
[nostr.relay.policy]
all-pass = true
follows-pass = false

[nostr.relay.policy.proof-of-work]
enabled = false
difficulty-minimum = 32
```

| パラメータ                      | 説明                                             | デフォルト | 優先度 |
|----------------------------------|------------------------------------------------------|-------------|----------------|
| all-pass                         | 誰からでもEventを受信                                   | true        | 中        |
| follows-pass                     | Relayの所有者がフォローしている人だけのEventを受信(NIP-02) | false       | 高い            |
| proof-of-work.enabled            | Proof of Workの確認を有効にする                   | false       | 高い            |
| proof-of-work.difficulty-minimum | Proof of Workの難易度の最小限                | 32          | -              |

> [!WARNING]\
> 難易度レベル32はかなり高いので、厳しさを下げたい場合、より低く設定するか閉じることをおすすめです。

### 3. Relayの特別なサービスの選択肢

```toml
[nostr.relay.database.backup]
enabled = false
sync = ["wss://relay.damus.io", "wss://relay.snort.social", "wss://relay.siamstr.com", "wss://relay.notoshi.win"]
```

| พารามิเตอร์ | คำอธิบาย                                              | ค่าเริ่มต้น |
|-------------|-------------------------------------------------------|-------------|
| enabled     | Relay (NIP-02) の所有者をフォローしている人のデータを他のRelayから取る機能を有効にする | false       |
| sync        | 他のRelayを取るリスト                   | -           |

## 🛠 インストールと使用の手順

> [!IMPORTANT]\
> インストールする前に、 [Docker](https://www.docker.com/products/docker-desktop/)をインストールしていることと有効にしていることをきちんと確認してください。


### インストール

1. プロジェクトをクローンしてこのディレクトリへ移動する。

```shell
git clone https://github.com/rushmi0/Fenrir-s.git
cd Fenrir-s
```

2. 適当に`application.toml`ファイルをカスタマイズする。

3. Docker Composeを実行する:

```shell
docker compose up relay-db relay-cache relay-app
```

### Cloudflare Tunnelの設定(Optional)

1. Cloudflare Tunnel を作ってTokenを受ける。
2. [docker-compose.yml](docker-compose.yml) ファイルを直してcloudflare-tunnel serviceの部分にTokenを入れる。

### Relayへのアクセス

Dockerを完全に実行した後、ここからRelayへアクセスできます:

- ws://localhost:6724 (パソコン内)
- wss://your-domain.com (Cloudflare Tunnelを使用する、設定している場合)

## 🔧 基本的な問題の解説

- **問題**: Dockerを実行できない
  **解説**: Dockerが動作していることと権限が十分あることを確認する

- **問題**: Relayに接続できない
  **解説**: firewallの設定と使用しているポートを確認する

## 🔄 アップデート

Fenrir-sの最新バージョンにアップデートするためには:

1. Docker containersを終了させる
2. Githubから最新のコードをPullする
3. Containersをリビルドしてリスタートする:

```shell
git pull
docker compose down
docker compose up --build -d
```

## 👥 การมีส่วนร่วมในโปรเจค

1. 問題の報告　→ Github上のOpen Issue
2. 変更内容の説明と一緒にPull Requestを送信


## 📚 関連資料

- [Nostr Protocol Specification](https://github.com/nostr-protocol/nips)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)

## 💬 お問い合わせとサポート

- Nostr : npub1ujevvncwfe22hv6d2cjv6pqwqhkvwlcvge7vgm3vcn2max9tu03sgze8ry
- Zap : ⚡rushmi0@getalby.com

---

質問や提案があれば、ぜひOpen issueでお願いします！
