# lcmodchecker

Lobotomy Corporation の MOD データを走査し、ID 一覧・重複一覧・MOD 一覧・解析ログを生成する Java ツールです。

## 概要
このツールは `BaseMods` 配下の MOD を解析し、以下の情報を整理して出力します。

- アブノーマリティ / 武器 / 防具 / ギフトの ID 一覧
- 各カテゴリの重複 ID 一覧
- Cross-category duplicates
- CustomEffect duplicates
- valid / invalid MOD の分類一覧
- 解析ログ / ターミナルログ

LcID もあわせて扱えるため、単純な ID 重複だけでなく、**同一 LcID を伴う重複確認**にも利用できます。

---

## 主な機能

### 1. BaseMods の自動探索
- `BaseMods` フォルダを自動探索
- 探索成功時はキャッシュ保存
- 次回起動時はキャッシュを優先利用

### 2. Project_Moon\\Lobotomy の自動探索
- 主な探索先: `C:\Users\<ユーザー名>\AppData\LocalLow`
- 見つからない場合は全ドライブ探索
- 探索成功時はキャッシュ保存

### 3. LcID 抽出
以下の優先順で LcID を探索します。

1. `Info/GlobalInfo.xml`
2. `Info/GlobalInfo.txt`
3. `Info/en/*.xml|txt`
4. `Info/cn/*.xml|txt`
5. `Info/jp/*.xml|txt`

取得できない場合は `None` として扱います。

### 4. valid / invalid 判定
`BaseModList_v2.xml` の `Useit` を参照し、MOD を valid / invalid に分類します。

### 5. 各種重複検出
- カテゴリ別 Duplicate ID
- LcID 一致条件付き Duplicate ID
- Cross-category duplicates
- CustomEffect duplicates

### 6. CustomEffect 重複確認
CustomEffect の同名フォルダ重複についても、以下の条件に対応しています。

- 通常重複
- `valid`
- `LcIDadd`
- `LcIDadd,valid`

### 7. Childname.txt の DOM 解析スキップ
`Childname.txt` は DOM 解析を行いません。  
また、スキップしたログは**同一 MOD 内で1回のみ**解析ログへ記録します。

### 8. 文字コード制御
- **ターミナル表示**: ターミナル側の文字コードを自動利用
- **ターミナルログ保存**: 起動時に選択した文字コード
- **解析ログ保存**: 起動時に選択した文字コード
- **各種出力ファイル保存**: 起動時に選択した文字コード

---

## 実行時の選択項目
起動時に以下を選択できます。

1. 出力文字コード
   - UTF-8
   - Shift_JIS (MS932)
2. ログレベル
   - 1: 最小限
   - 2: 標準
   - 3: 詳細
3. 解析ログ作成の有無
   - y / n

---

## 出力フォルダ構成
出力先は `Project_Moon\\Lobotomy` 配下に自動作成されます。

```text
Project_Moon\Lobotomy
└─ ModAnalyzer
   ├─ Log
   │  ├─ yyyyMMdd_HHmmss_LobotomyModAnalyzer_analysis_log.txt
   │  └─ yyyyMMdd_HHmmss_LobotomyModAnalyzer_terminal_log.txt
   └─ Various_lists
      ├─ ID_list
      │  ├─ ID_list.txt
      │  └─ ID_list(valid).txt
      ├─ DuplicateID_list
      │  ├─ DuplicateID_list.txt
      │  ├─ DuplicateID_list(valid).txt
      │  ├─ DuplicateID_list(LcIDadd).txt
      │  └─ DuplicateID_list(LcIDadd,valid).txt
      └─ mod_list
         ├─ mods_list_01_(valid).txt
         ├─ mods_list_02_(invalid).txt
         └─ mods_list_03_(all).txt
