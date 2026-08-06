// ================================================================================
// LobotomyModAnalyzer.java
// 統合版 MOD 管理・ID重複検出ツール
// 
// 機能:
// - BaseModsフォルダの自動探索
// - アブノーマリティ・武器・防具・ギフトのID管理
// - LcIDの抽出と表示
// - ID重複検出 (ID単体 / ID+LcID両方)
// - valid/invalid フィルタリング
// - Cross-category重複検出
// - CustomEffectフォルダ重複検出
// 
// Version: 2.0-beta1.6
// Date: 2026-06-01
// ================================================================================

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class LobotomyModAnalyzer {

    // ========================================
    // 設定・定数
    // ========================================
    
    private static String BASE_PATH = null;
    private static String OUTPUT_BASE_PATH = null;
    private static File LOG_OUTPUT_DIR = null;
    private static File LIST_OUTPUT_DIR = null;
    private static File ID_LIST_OUTPUT_DIR = null;
    private static File DUPLICATE_LIST_OUTPUT_DIR = null;
    private static File MOD_LIST_OUTPUT_DIR = null;
    private static final String BASE_XML_RELATIVE = "BaseModList_v2.xml";
    private static final String BASEMODS_CACHE_FILENAME = "BaseModsPathCache.txt";
    private static final String PROJECT_LOBOTOMY_CACHE_FILENAME = "ProjectMoonLobotomyPathCache.txt";
    private static final Charset CACHE_CHARSET = StandardCharsets.UTF_8;
    
    // 探索設定
    private static final int MAX_DEPTH = 8;
    private static int logLevel = 1;
    
    // ログ設定
    private static volatile boolean ENABLE_ANALYSIS_LOG = false;
    private static volatile BufferedWriter ANALYSIS_WRITER = null;
    private static File ANALYSIS_TEMP_FILE = null;
    private static File TERMINAL_TEMP_FILE = null;
    private static volatile PrintStream TERMINAL_OUT_STREAM = null;
    private static volatile PrintStream TERMINAL_ERR_STREAM = null;
    private static final PrintStream ORIGINAL_OUT = System.out;
    private static final PrintStream ORIGINAL_ERR = System.err;
    private static final DateTimeFormatter LOG_TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static volatile Charset OUTPUT_CHARSET = StandardCharsets.UTF_8;
    private static volatile Charset CONSOLE_CHARSET = Charset.defaultCharset();
    private static volatile FileOutputStream TERMINAL_OUT_FILE_STREAM = null;
    private static volatile FileOutputStream TERMINAL_ERR_FILE_STREAM = null;
    
    // 言語優先順位
    private static final String[] LANG_ORDER = new String[] {"jp","en","cn","kr","ru"};
    
    // ========================================
    // バニラデータ埋め込み
    // ========================================
    
    private static final Map<String, VanillaEntry> VANILLA_ABN = new LinkedHashMap<>();
    private static final Map<String, String> VANILLA_WEP = new LinkedHashMap<>();
    private static final Map<String, String> VANILLA_ARM = new LinkedHashMap<>();
    private static final Map<String, String> VANILLA_GFT = new LinkedHashMap<>();
    
    // バニラエントリ (アブノーマリティ用にコードNoも保持)
    private static class VanillaEntry {
        String name;
        String codeNo;
        VanillaEntry(String name, String codeNo) {
            this.name = name;
            this.codeNo = codeNo;
        }
    }
    
    static {
        // アブノーマリティ (ID, 名前, コードNo)
        VANILLA_ABN.put("100000", new VanillaEntry("教育用ウサギロボ", "O-00-00"));
        VANILLA_ABN.put("100001", new VanillaEntry("マッチガール", "F-01-02"));
        VANILLA_ABN.put("100002", new VanillaEntry("幸せなテディ", "T-04-06"));
        VANILLA_ABN.put("100003", new VanillaEntry("赤い靴", "O-04-08"));
        VANILLA_ABN.put("100004", new VanillaEntry("憎しみの女王", "O-01-04"));
        VANILLA_ABN.put("100005", new VanillaEntry("何もない", "O-06-20"));
        VANILLA_ABN.put("100006", new VanillaEntry("歌う機械", "O-05-30"));
        VANILLA_ABN.put("100007", new VanillaEntry("1.76MHz", "T-06-27"));
        VANILLA_ABN.put("100008", new VanillaEntry("大鳥", "O-02-40"));
        VANILLA_ABN.put("100009", new VanillaEntry("たった一つの罪と何百もの善", "O-03-03"));
        VANILLA_ABN.put("100011", new VanillaEntry("オールアラウンドヘルパー", "T-05-41"));
        VANILLA_ABN.put("100012", new VanillaEntry("母なるクモ", "T-02-43"));
        VANILLA_ABN.put("100013", new VanillaEntry("美女と野獣", "F-02-44"));
        VANILLA_ABN.put("100014", new VanillaEntry("ペスト医師", "O-01-45"));
        VANILLA_ABN.put("100015", new VanillaEntry("白夜", "T-03-46"));
        VANILLA_ABN.put("100016", new VanillaEntry("そりのルドル・タ", "F-02-49"));
        VANILLA_ABN.put("100017", new VanillaEntry("無名の胎児", "O-01-15"));
        VANILLA_ABN.put("100018", new VanillaEntry("捨てられた殺人者", "T-01-54"));
        VANILLA_ABN.put("100019", new VanillaEntry("静かなオーケストラ", "T-01-31"));
        VANILLA_ABN.put("100020", new VanillaEntry("罰鳥", "O-02-56"));
        VANILLA_ABN.put("100021", new VanillaEntry("オールドレディ", "O-01-12"));
        VANILLA_ABN.put("100022", new VanillaEntry("壁に向かう女", "F-01-18"));
        VANILLA_ABN.put("100023", new VanillaEntry("白雪姫の林檎", "F-04-42"));
        VANILLA_ABN.put("100024", new VanillaEntry("触れてはならない", "O-05-47"));
        VANILLA_ABN.put("100026", new VanillaEntry("女王蜂", "T-04-50"));
        VANILLA_ABN.put("100027", new VanillaEntry("血の風呂", "T-05-51"));
        VANILLA_ABN.put("100028", new VanillaEntry("蓋の開いたウェルチアース", "F-05-52"));
        VANILLA_ABN.put("100029", new VanillaEntry("アルリウネ", "T-04-53"));
        VANILLA_ABN.put("100031", new VanillaEntry("銀河の子", "O-01-55"));
        VANILLA_ABN.put("100032", new VanillaEntry("赤ずきんの傭兵", "F-01-57"));
        VANILLA_ABN.put("100033", new VanillaEntry("大きくて悪いオオカミ", "F-02-58"));
        VANILLA_ABN.put("100034", new VanillaEntry("お前、ハゲだよ...", "Bald-Is-Awesome!"));
        VANILLA_ABN.put("100035", new VanillaEntry("審判鳥", "O-02-62"));
        VANILLA_ABN.put("100036", new VanillaEntry("宇宙の欠片", "O-03-60"));
        VANILLA_ABN.put("100037", new VanillaEntry("壊れかけの甲冑", "O-05-61"));
        VANILLA_ABN.put("100038", new VanillaEntry("終末鳥", "O-02-63"));
        VANILLA_ABN.put("100039", new VanillaEntry("貪欲の王", "O-01-64"));
        VANILLA_ABN.put("100040", new VanillaEntry("小さな王子", "O-04-66"));
        VANILLA_ABN.put("100041", new VanillaEntry("レティシア", "O-01-67"));
        VANILLA_ABN.put("100042", new VanillaEntry("笑う死体の山", "T-01-75"));
        VANILLA_ABN.put("100043", new VanillaEntry("死んだ蝶の葬儀", "T-01-68"));
        VANILLA_ABN.put("100044", new VanillaEntry("地中の天国", "O-04-72"));
        VANILLA_ABN.put("100045", new VanillaEntry("黒鳥の夢", "F-02-70"));
        VANILLA_ABN.put("100046", new VanillaEntry("裸の巣", "O-02-74"));
        VANILLA_ABN.put("100047", new VanillaEntry("夢見る流れ", "T-02-71"));
        VANILLA_ABN.put("100048", new VanillaEntry("絶望の騎士", "O-01-73"));
        VANILLA_ABN.put("100049", new VanillaEntry("シャーデンフロイデ", "O-05-76"));
        VANILLA_ABN.put("100050", new VanillaEntry("知恵を欲する案山子", "F-01-87"));
        VANILLA_ABN.put("100051", new VanillaEntry("暖かい心の木こり", "F-05-32"));
        VANILLA_ABN.put("100052", new VanillaEntry("今日は恥ずかしがり屋", "O-01-92"));
        VANILLA_ABN.put("100053", new VanillaEntry("妖精の祭典", "F-04-83"));
        VANILLA_ABN.put("100054", new VanillaEntry("肉の灯籠", "O-04-84"));
        VANILLA_ABN.put("100055", new VanillaEntry("次元屈折変異体", "O-03-88"));
        VANILLA_ABN.put("100056", new VanillaEntry("規制済み", "O-03-89"));
        VANILLA_ABN.put("100057", new VanillaEntry("ポーキュバス", "O-02-98"));
        VANILLA_ABN.put("100058", new VanillaEntry("蒼星", "O-03-93"));
        VANILLA_ABN.put("100059", new VanillaEntry("墓穴の桜", "O-04-100"));
        VANILLA_ABN.put("100060", new VanillaEntry("空虚な夢", "T-02-99"));
        VANILLA_ABN.put("100061", new VanillaEntry("火の鳥", "O-02-101"));
        VANILLA_ABN.put("100062", new VanillaEntry("寄生樹", "D-04-108"));
        VANILLA_ABN.put("100063", new VanillaEntry("溶ける愛", "D-03-109"));
        VANILLA_ABN.put("100064", new VanillaEntry("黒の兵隊", "D-01-106"));
        VANILLA_ABN.put("100065", new VanillaEntry("ラ・ルナ", "D-01-105"));
        VANILLA_ABN.put("100102", new VanillaEntry("雪の女王", "F-01-37"));
        VANILLA_ABN.put("100103", new VanillaEntry("魔弾の射手", "F-01-69"));
        VANILLA_ABN.put("100104", new VanillaEntry("陰", "O-05-102"));
        VANILLA_ABN.put("100105", new VanillaEntry("風雲僧", "D-01-110"));
        VANILLA_ABN.put("100106", new VanillaEntry("キュートちゃん", "D-02-107"));
        VANILLA_ABN.put("300001", new VanillaEntry("熱望する心臓", "T-09-77"));
        VANILLA_ABN.put("300002", new VanillaEntry("狂研究者のノート", "T-09-78"));
        VANILLA_ABN.put("300003", new VanillaEntry("テレジア", "T-09-09"));
        VANILLA_ABN.put("300004", new VanillaEntry("肉の偶像", "T-09-79"));
        VANILLA_ABN.put("300005", new VanillaEntry("巨木の樹液", "T-09-80"));
        VANILLA_ABN.put("300006", new VanillaEntry("3月27日のシェルター", "T-09-82"));
        VANILLA_ABN.put("300007", new VanillaEntry("調整の鏡", "O-09-81"));
        VANILLA_ABN.put("300101", new VanillaEntry("何でも変えて差し上げます", "T-09-85"));
        VANILLA_ABN.put("300102", new VanillaEntry("地獄の急行列車", "T-09-86"));
        VANILLA_ABN.put("300103", new VanillaEntry("皮膚の予言", "T-09-90"));
        VANILLA_ABN.put("300104", new VanillaEntry("異界の肖像", "O-09-91"));
        VANILLA_ABN.put("300105", new VanillaEntry("あなたは幸せでなければならない", "T-09-94"));
        VANILLA_ABN.put("300106", new VanillaEntry("輝く腕輪", "O-09-95"));
        VANILLA_ABN.put("300107", new VanillaEntry("行動矯正", "O-09-96"));
        VANILLA_ABN.put("300108", new VanillaEntry("古い信念と約束", "T-09-97"));
        VANILLA_ABN.put("300109", new VanillaEntry("陽", "O-07-103"));
        VANILLA_ABN.put("300110", new VanillaEntry("逆行時計", "D-09-104"));
        
        // 武器
        VANILLA_WEP.put("200000", "教育用疑似E.G.O");
        VANILLA_WEP.put("200001", "4本目のマッチの火");
        VANILLA_WEP.put("200002", "クマの手");
        VANILLA_WEP.put("200003", "鮮血");
        VANILLA_WEP.put("200004", "愛と憎しみの名のもとに");
        VANILLA_WEP.put("200005", "ミミック");
        VANILLA_WEP.put("200006", "ハーモニー");
        VANILLA_WEP.put("200008", "ランプ");
        VANILLA_WEP.put("200009", "懺悔");
        VANILLA_WEP.put("200011", "グラインダー Mk4");
        VANILLA_WEP.put("200012", "赤い目");
        VANILLA_WEP.put("200013", "角");
        VANILLA_WEP.put("200015", "失楽園");
        VANILLA_WEP.put("200016", "クリスマス");
        VANILLA_WEP.put("200017", "泣き虫");
        VANILLA_WEP.put("200018", "後悔");
        VANILLA_WEP.put("200019", "ダ・カーポ");
        VANILLA_WEP.put("200020", "くちばし");
        VANILLA_WEP.put("200021", "孤独");
        VANILLA_WEP.put("200022", "悲鳴");
        VANILLA_WEP.put("200023", "緑の幹");
        VANILLA_WEP.put("200026", "ホーネット");
        VANILLA_WEP.put("200027", "リストカッター");
        VANILLA_WEP.put("200028", "ソーダ");
        VANILLA_WEP.put("200029", "残り香");
        VANILLA_WEP.put("200031", "銀河");
        VANILLA_WEP.put("200032", "紅の傷跡");
        VANILLA_WEP.put("200033", "蒼の傷跡");
        VANILLA_WEP.put("200034", "タフ");
        VANILLA_WEP.put("200035", "ジャスティティア");
        VANILLA_WEP.put("200036", "彼方の欠片");
        VANILLA_WEP.put("200037", "決死の一生");
        VANILLA_WEP.put("200038", "黄昏");
        VANILLA_WEP.put("200039", "黄金狂");
        VANILLA_WEP.put("200040", "胞子");
        VANILLA_WEP.put("200041", "レティシア");
        VANILLA_WEP.put("200042", "笑顔");
        VANILLA_WEP.put("200043", "崇高な誓い");
        VANILLA_WEP.put("200044", "天国");
        VANILLA_WEP.put("200045", "ブラック・スワン");
        VANILLA_WEP.put("200046", "抜け殻");
        VANILLA_WEP.put("200047", "夢中");
        VANILLA_WEP.put("200048", "鋭利な涙の剣");
        VANILLA_WEP.put("200049", "視線");
        VANILLA_WEP.put("200050", "収穫");
        VANILLA_WEP.put("200051", "伐採斧");
        VANILLA_WEP.put("200052", "今日の表情");
        VANILLA_WEP.put("200053", "ウィングビート");
        VANILLA_WEP.put("200054", "灯篭");
        VANILLA_WEP.put("200055", "回折");
        VANILLA_WEP.put("200056", "規制済み");
        VANILLA_WEP.put("200057", "喜び");
        VANILLA_WEP.put("200058", "星の音");
        VANILLA_WEP.put("200059", "桜");
        VANILLA_WEP.put("200060", "耽美な夢");
        VANILLA_WEP.put("200061", "名誉の羽根");
        VANILLA_WEP.put("200062", "偽善");
        VANILLA_WEP.put("200063", "ラブ");
        VANILLA_WEP.put("200064", "ピンク");
        VANILLA_WEP.put("200065", "月光");
        VANILLA_WEP.put("200102", "氷のかけら");
        VANILLA_WEP.put("200103", "魔法の弾丸");
        VANILLA_WEP.put("200104", "不調和");
        VANILLA_WEP.put("200105", "阿弥陀");
        VANILLA_WEP.put("200106", "ｶﾜｲｲ！！");
        
        // 防具
        VANILLA_ARM.put("300000", "教育用疑似E.G.O");
        VANILLA_ARM.put("300001", "4本目のマッチの火");
        VANILLA_ARM.put("300002", "クマの手");
        VANILLA_ARM.put("300003", "鮮血");
        VANILLA_ARM.put("300004", "愛と憎しみの名のもとに");
        VANILLA_ARM.put("300005", "ミミック");
        VANILLA_ARM.put("300007", "ノイズ");
        VANILLA_ARM.put("300008", "ランプ");
        VANILLA_ARM.put("300009", "懺悔");
        VANILLA_ARM.put("300011", "グラインダー Mk4");
        VANILLA_ARM.put("300012", "赤い目");
        VANILLA_ARM.put("300013", "角");
        VANILLA_ARM.put("300015", "失楽園");
        VANILLA_ARM.put("300016", "クリスマス");
        VANILLA_ARM.put("300017", "泣き虫");
        VANILLA_ARM.put("300018", "後悔");
        VANILLA_ARM.put("300019", "ダ・カーポ");
        VANILLA_ARM.put("300020", "くちばし");
        VANILLA_ARM.put("300021", "孤独");
        VANILLA_ARM.put("300022", "悲鳴");
        VANILLA_ARM.put("300023", "緑の幹");
        VANILLA_ARM.put("300026", "ホーネット");
        VANILLA_ARM.put("300027", "リストカッター");
        VANILLA_ARM.put("300028", "ソーダ");
        VANILLA_ARM.put("300029", "残り香");
        VANILLA_ARM.put("300031", "銀河");
        VANILLA_ARM.put("300032", "紅の傷跡");
        VANILLA_ARM.put("300033", "蒼の傷跡");
        VANILLA_ARM.put("300034", "タフ");
        VANILLA_ARM.put("300035", "ジャスティティア");
        VANILLA_ARM.put("300036", "彼方の欠片");
        VANILLA_ARM.put("300037", "決死の一生");
        VANILLA_ARM.put("300038", "黄昏");
        VANILLA_ARM.put("300039", "黄金狂");
        VANILLA_ARM.put("300040", "胞子");
        VANILLA_ARM.put("300041", "レティシア");
        VANILLA_ARM.put("300042", "笑顔");
        VANILLA_ARM.put("300043", "崇高な誓い");
        VANILLA_ARM.put("300044", "天国");
        VANILLA_ARM.put("300045", "ブラック・スワン");
        VANILLA_ARM.put("300046", "抜け殻");
        VANILLA_ARM.put("300047", "夢中");
        VANILLA_ARM.put("300048", "鋭利な涙の剣");
        VANILLA_ARM.put("300049", "視線");
        VANILLA_ARM.put("300050", "収穫");
        VANILLA_ARM.put("300051", "伐採斧");
        VANILLA_ARM.put("300052", "今日の表情");
        VANILLA_ARM.put("300053", "ウィングビート");
        VANILLA_ARM.put("300054", "灯篭");
        VANILLA_ARM.put("300057", "喜び");
        VANILLA_ARM.put("300058", "星の音");
        VANILLA_ARM.put("300059", "桜");
        VANILLA_ARM.put("300060", "耽美な夢");
        VANILLA_ARM.put("300061", "名誉の羽根");
        VANILLA_ARM.put("300062", "偽善");
        VANILLA_ARM.put("300063", "ラブ");
        VANILLA_ARM.put("300064", "ピンク");
        VANILLA_ARM.put("300065", "月光");
        VANILLA_ARM.put("300102", "氷のかけら");
        VANILLA_ARM.put("300103", "魔法の弾丸");
        VANILLA_ARM.put("300104", "不調和");
        VANILLA_ARM.put("300105", "阿弥陀");
        VANILLA_ARM.put("300106", "ｶﾜｲｲ！！");
        
        // ギフト
        VANILLA_GFT.put("400000", "教育用疑似E.G.O");
        VANILLA_GFT.put("400001", "4本目のマッチの火");
        VANILLA_GFT.put("400002", "クマの手");
        VANILLA_GFT.put("400003", "鮮血");
        VANILLA_GFT.put("400004", "愛と憎しみの名のもとに");
        VANILLA_GFT.put("400005", "ミミック");
        VANILLA_GFT.put("400006", "ハーモニー");
        VANILLA_GFT.put("400007", "ノイズ");
        VANILLA_GFT.put("400008", "ランプ");
        VANILLA_GFT.put("400009", "懺悔");
        VANILLA_GFT.put("400011", "グラインダー Mk4");
        VANILLA_GFT.put("400012", "赤い目");
        VANILLA_GFT.put("400013", "角");
        VANILLA_GFT.put("400014", "祝福");
        VANILLA_GFT.put("400015", "失楽園");
        VANILLA_GFT.put("400016", "クリスマス");
        VANILLA_GFT.put("400017", "泣き虫");
        VANILLA_GFT.put("400018", "後悔");
        VANILLA_GFT.put("400019", "ダ・カーポ");
        VANILLA_GFT.put("400020", "くちばし");
        VANILLA_GFT.put("400021", "孤独");
        VANILLA_GFT.put("400022", "悲鳴");
        VANILLA_GFT.put("400023", "緑の幹");
        VANILLA_GFT.put("400026", "ホーネット");
        VANILLA_GFT.put("400027", "リストカッター");
        VANILLA_GFT.put("400028", "ソーダ");
        VANILLA_GFT.put("400029", "残り香");
        VANILLA_GFT.put("400031", "銀河");
        VANILLA_GFT.put("400032", "紅の傷跡");
        VANILLA_GFT.put("400033", "蒼の傷跡");
        VANILLA_GFT.put("1033", "羊の皮");
        VANILLA_GFT.put("400034", "タフ");
        VANILLA_GFT.put("400035", "ジャスティティア");
        VANILLA_GFT.put("400036", "彼方の欠片");
        VANILLA_GFT.put("400037", "決死の一生");
        VANILLA_GFT.put("400038", "黄昏");
        VANILLA_GFT.put("400039", "黄金狂");
        VANILLA_GFT.put("400040", "胞子");
        VANILLA_GFT.put("400041", "レティシア");
        VANILLA_GFT.put("400042", "笑顔");
        VANILLA_GFT.put("400043", "崇高な誓い");
        VANILLA_GFT.put("400044", "天国");
        VANILLA_GFT.put("400045", "ブラック・スワン");
        VANILLA_GFT.put("400046", "抜け殻");
        VANILLA_GFT.put("400047", "夢中");
        VANILLA_GFT.put("400048", "鋭利な涙の剣");
        VANILLA_GFT.put("400049", "視線");
        VANILLA_GFT.put("400050", "収穫");
        VANILLA_GFT.put("400051", "伐採斧");
        VANILLA_GFT.put("400052", "今日の表情");
        VANILLA_GFT.put("400053", "ウィングビート");
        VANILLA_GFT.put("400054", "灯篭");
        VANILLA_GFT.put("400055", "回折");
        VANILLA_GFT.put("400056", "規制済み");
        VANILLA_GFT.put("400057", "喜び");
        VANILLA_GFT.put("400058", "星の音");
        VANILLA_GFT.put("400059", "桜");
        VANILLA_GFT.put("400060", "耽美な夢");
        VANILLA_GFT.put("400061", "名誉の羽根");
        VANILLA_GFT.put("400062", "偽善");
        VANILLA_GFT.put("400063", "ラブ");
        VANILLA_GFT.put("400064", "ピンク");
        VANILLA_GFT.put("400065", "月光");
        VANILLA_GFT.put("400102", "氷のかけら");
        VANILLA_GFT.put("1023", "冬の残酷さとバラの香りを知るもの");
        VANILLA_GFT.put("400103", "魔法の弾丸");
        VANILLA_GFT.put("400104", "不調和");
        VANILLA_GFT.put("400105", "阿弥陀");
        VANILLA_GFT.put("400106", "ｶﾜｲｲ！！");
    }
    
    // ========================================
    // データ構造
    // ========================================
    
    // アイテム情報 (拡張版 - LcID、コードNo、valid/invalidフラグを含む)
    private static class ItemInfo {
        Map<String, ItemData> modData = new LinkedHashMap<>();  // modName → ItemData
        
        void add(String modName, String itemName, String filePath, String lcId, String codeNo, boolean isValid) {
            if (modName == null) modName = "(unknown)";
            ItemData data = new ItemData();
            data.modName = modName;
            data.filePath = filePath;
            data.name = (itemName == null) ? "(null)" : itemName;
            data.files = new ArrayList<>();
            data.files.add(filePath);
            data.lcId = (lcId == null) ? "None" : lcId;
            data.codeNo = (codeNo == null) ? "" : codeNo;
            data.isValid = isValid;
            modData.put(modName, data);
        }
    }
    
    private static class ItemData {
        String modName;
        String filePath;
        String name;
        List<String> files;
        String lcId;
        String codeNo;
        boolean isValid;
    }
    
    // カテゴリ別マップ
    private static final Map<String, ItemInfo> ABN_MAP = new TreeMap<>(new MixedComparatorBigInt());
    private static final Map<String, ItemInfo> WEP_MAP = new TreeMap<>(new MixedComparatorBigInt());
    private static final Map<String, ItemInfo> ARM_MAP = new TreeMap<>(new MixedComparatorBigInt());
    private static final Map<String, ItemInfo> GFT_MAP = new TreeMap<>(new MixedComparatorBigInt());
    
    // CustomEffect フォルダマップ
    private static final Map<String, List<CustomEffectData>> CUSTOM_EFFECT_MAP = new TreeMap<>();
    private static final Set<String> CHILDNAME_DOM_SKIP_LOGGED_MODS = new HashSet<>();
    
    // ========================================
    // ユーティリティクラス
    // ========================================
    
    // 数値・混合IDソート用コンパレータ
    private static class MixedComparatorBigInt implements Comparator<String> {
        @Override
        public int compare(String a, String b) {
            boolean an = a.chars().allMatch(Character::isDigit);
            boolean bn = b.chars().allMatch(Character::isDigit);
            if (an && bn) {
                return new BigInteger(a).compareTo(new BigInteger(b));
            }
            if (an) return -1;
            if (bn) return 1;
            return a.compareTo(b);
        }
    }
    
    // ========================================
    // ログヘルパー
    // ========================================
    
    private static void log(int level, String msg) {
        if (logLevel >= level) {
            System.out.println(msg);
        }
    }
    
    private static void analysisLog(String msg) {
        if (!ENABLE_ANALYSIS_LOG) return;
        try {
            if (ANALYSIS_WRITER != null) {
                ANALYSIS_WRITER.write("[" + LOG_TS_FMT.format(LocalDateTime.now()) + "] " + msg + "\n");
                ANALYSIS_WRITER.flush();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    private static class ReEncodingTeeOutputStream extends OutputStream {
        private final OutputStream consoleStream;
        private final BufferedWriter logWriter;
        private final Charset consoleCharset;
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
        
        ReEncodingTeeOutputStream(OutputStream consoleStream, OutputStream logStream, Charset consoleCharset, Charset logCharset) {
            this.consoleStream = consoleStream;
            this.logWriter = new BufferedWriter(new OutputStreamWriter(logStream, logCharset));
            this.consoleCharset = consoleCharset;
        }
        
        @Override
        public synchronized void write(int b) throws IOException {
            consoleStream.write(b);
            pending.write(b);
            if (b == '\n'||b=='\r') {
                flushCompletedLines();
            }
        }
        
        @Override
        public synchronized void write(byte[] buf, int off, int len) throws IOException {
            consoleStream.write(buf, off, len);
            pending.write(buf, off, len);
            flushCompletedLines();
        }
        
        private void flushCompletedLines() throws IOException {
            byte[] data = pending.toByteArray();
            int lastNewline = -1;
            for (int i = 0; i < data.length; i++) {
                if (data[i] == (byte) '\n'||data[i] == (byte) '\r') {
                    lastNewline = i;
                }
            }
            if (lastNewline >= 0) {
                logWriter.write(new String(data, 0, lastNewline + 1, consoleCharset));
                logWriter.flush();
                pending.reset();
                if (lastNewline + 1 < data.length) {
                    pending.write(data, lastNewline + 1, data.length - (lastNewline + 1));
                }
            }
        }
        
        private void flushPendingAll() throws IOException {
            byte[] data = pending.toByteArray();
            if (data.length > 0) {
                logWriter.write(new String(data, consoleCharset));
                pending.reset();
            }
        }
        
        @Override
        public synchronized void flush() throws IOException {
            consoleStream.flush();
            flushPendingAll();
            logWriter.flush();
        }
        
        @Override
        public synchronized void close() throws IOException {
            flush();
            logWriter.close();
        }
    }
    
    private static Charset detectConsoleCharset() {
        String[] candidates = new String[] {
            System.getProperty("sun.stdout.encoding"),
            System.getProperty("native.encoding"),
            System.getProperty("sun.jnu.encoding"),
            System.getProperty("file.encoding")
        };
        for (String candidate : candidates) {
            if (candidate != null && !candidate.trim().isEmpty()) {
                try {
                    return Charset.forName(candidate.trim());
                } catch (Exception ignore) {
                }
            }
        }
        return Charset.defaultCharset();
    }
    
    private static void initializeTerminalLog(String ts, Charset logCharset) {
        FileOutputStream outFileStream = null;
        FileOutputStream errFileStream = null;
        try {
            TERMINAL_TEMP_FILE = new File(ts + "_LMA_terminal_log.txt");
            CONSOLE_CHARSET = detectConsoleCharset();
            Charset actualLogCharset = (logCharset != null ? logCharset : StandardCharsets.UTF_8);
            outFileStream = new FileOutputStream(TERMINAL_TEMP_FILE, false);
            errFileStream = new FileOutputStream(TERMINAL_TEMP_FILE, true);
            TERMINAL_OUT_FILE_STREAM = outFileStream;
            TERMINAL_ERR_FILE_STREAM = errFileStream;
            TERMINAL_OUT_STREAM = new PrintStream(
                new ReEncodingTeeOutputStream(ORIGINAL_OUT, outFileStream, CONSOLE_CHARSET, actualLogCharset),
                true,
                CONSOLE_CHARSET.name()
            );
            TERMINAL_ERR_STREAM = new PrintStream(
                new ReEncodingTeeOutputStream(ORIGINAL_ERR, errFileStream, CONSOLE_CHARSET, actualLogCharset),
                true,
                CONSOLE_CHARSET.name()
            );
            System.setOut(TERMINAL_OUT_STREAM);
            System.setErr(TERMINAL_ERR_STREAM);
        } catch (Exception ex) {
            try { if (TERMINAL_OUT_STREAM != null) TERMINAL_OUT_STREAM.close(); } catch (Exception ignore) {}
            try { if (TERMINAL_ERR_STREAM != null) TERMINAL_ERR_STREAM.close(); } catch (Exception ignore) {}
            try { if (outFileStream != null) outFileStream.close(); } catch (Exception ignore) {}
            try { if (errFileStream != null) errFileStream.close(); } catch (Exception ignore) {}
            TERMINAL_OUT_STREAM = null;
            TERMINAL_ERR_STREAM = null;
            TERMINAL_OUT_FILE_STREAM = null;
            TERMINAL_ERR_FILE_STREAM = null;
            ORIGINAL_ERR.println("[WARN] ターミナルログの初期化に失敗: " + ex.getMessage());
        }
    }
    
    private static void closeLogsAndRestoreConsole() {
        try {
            if (ANALYSIS_WRITER != null) {
                ANALYSIS_WRITER.close();
                ANALYSIS_WRITER = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        try { if (System.out != null) System.out.flush(); } catch (Exception ignore) {}
        try { if (System.err != null) System.err.flush(); } catch (Exception ignore) {}
        System.setOut(ORIGINAL_OUT);
        System.setErr(ORIGINAL_ERR);
        try { if (TERMINAL_OUT_STREAM != null) TERMINAL_OUT_STREAM.close(); } catch (Exception ignore) {}
        try { if (TERMINAL_ERR_STREAM != null) TERMINAL_ERR_STREAM.close(); } catch (Exception ignore) {}
        try { if (TERMINAL_OUT_FILE_STREAM != null) TERMINAL_OUT_FILE_STREAM.close(); } catch (Exception ignore) {}
        try { if (TERMINAL_ERR_FILE_STREAM != null) TERMINAL_ERR_FILE_STREAM.close(); } catch (Exception ignore) {}
        TERMINAL_OUT_STREAM = null;
        TERMINAL_ERR_STREAM = null;
        TERMINAL_OUT_FILE_STREAM = null;
        TERMINAL_ERR_FILE_STREAM = null;
    }
    
    private static void moveLogFilesToOutputDirectory() {
        moveTempLogToDirectory(ANALYSIS_TEMP_FILE, LOG_OUTPUT_DIR, "解析ログ");
        moveTempLogToDirectory(TERMINAL_TEMP_FILE, LOG_OUTPUT_DIR, "ターミナルログ");
    }
    
    private static void moveTempLogToDirectory(File tempFile, File targetDir, String label) {
        if (tempFile == null || targetDir == null || !tempFile.exists()) return;
        try {
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                throw new IOException("出力フォルダを作成できません: " + targetDir.getAbsolutePath());
            }
            File target = new File(targetDir, tempFile.getName());
            if (!tempFile.getCanonicalPath().equals(target.getCanonicalPath())) {
                Files.move(tempFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ex) {
            ORIGINAL_ERR.println("[WARN] " + label + "の移動に失敗: " + ex.getMessage());
        }
    }
    
    private static String previewText(String text, int maxLen) {
        if (text == null) return "";
        String s = text.replace("\r", " ").replace("\n", " ").replace("\t", " ").trim();
        if (s.length() > maxLen) {
            return s.substring(0, maxLen) + "...";
        }
        return s;
    }
    
    private static String extractXmlErrorSnippet(String xml, int lineNumber) {
        if (xml == null || xml.isEmpty()) return "";
        try {
            String[] lines = xml.split("\\r?\\n", -1);
            if (lineNumber >= 1 && lineNumber <= lines.length) {
                return previewText(lines[lineNumber - 1], 240);
            }
        } catch (Exception ignore) {}
        return previewText(xml, 240);
    }
    
    private static String buildXmlErrorShortMessage(String sourceLabel, Exception ex) {
        if (ex instanceof SAXParseException) {
            SAXParseException sx = (SAXParseException) ex;
            return "[XML ERROR] " + sourceLabel + " (line " + sx.getLineNumber() + ", col " + sx.getColumnNumber() + "): " + sx.getMessage();
        }
        return "[XML ERROR] " + sourceLabel + ": " + ex;
    }
    
    private static String buildXmlErrorDetailMessage(String sourceLabel, String xml, Exception ex) {
        StringBuilder sb = new StringBuilder();
        sb.append("DOM解析失敗: file=").append(sourceLabel);
        sb.append(" , xml length=").append(xml == null ? 0 : xml.length());
        if (ex instanceof SAXParseException) {
            SAXParseException sx = (SAXParseException) ex;
            sb.append(" , line=").append(sx.getLineNumber());
            sb.append(" , column=").append(sx.getColumnNumber());
            sb.append(" , message=").append(sx.getMessage());
            String snippet = extractXmlErrorSnippet(xml, sx.getLineNumber());
            if (!snippet.isEmpty()) {
                sb.append(" , snippet=").append(snippet);
            }
        } else {
            sb.append(" , ex=").append(ex);
        }
        return sb.toString();
    }

    // ========================================
    // LcID抽出機能 (新規追加)
    // ========================================
    
    private static String extractLcId(String modName) {
        // 優先順位: GlobalInfo.xml/txt → en/*.xml/txt → cn/*.xml/txt → jp/*.xml/txt
        File infoBase = new File(BASE_PATH + File.separator + modName + File.separator + "Info");
        if (!infoBase.exists() || !infoBase.isDirectory()) {
            return "None";
        }
        
        // 1. GlobalInfo.xml or GlobalInfo.txt
        for (String ext : new String[]{"xml", "txt"}) {
            File globalInfo = new File(infoBase, "GlobalInfo." + ext);
            if (globalInfo.exists()) {
                String lcId = parseLcIdFromFile(globalInfo);
                if (lcId != null && !lcId.isEmpty()) {
                    return lcId;
                }
            }
        }
        
        // 2-4. en, cn, jp サブフォルダ
        for (String lang : new String[]{"en", "cn", "jp"}) {
            File langDir = new File(infoBase, lang);
            if (langDir.exists() && langDir.isDirectory()) {
                File[] files = langDir.listFiles((dir, name) -> 
                    name.toLowerCase().endsWith(".xml") || name.toLowerCase().endsWith(".txt"));
                if (files != null) {
                    for (File f : files) {
                        String lcId = parseLcIdFromFile(f);
                        if (lcId != null && !lcId.isEmpty()) {
                            return lcId;
                        }
                    }
                }
            }
        }
        
        return "None";
    }
    
    private static String parseLcIdFromFile(File f) {
        try {
            String content = tryReadAllTextAndSanitize(f);
            if (content == null) return null;
            
            // DOM解析を試行
            try {
                Document doc = parseXmlString(content, f);
                if (doc != null) {
                    NodeList infoNodes = doc.getElementsByTagName("info");
                    for (int i = 0; i < infoNodes.getLength(); i++) {
                        Element infoEl = (Element) infoNodes.item(i);
                        NodeList idNodes = infoEl.getElementsByTagName("ID");
                        if (idNodes.getLength() > 0) {
                            String id = idNodes.item(0).getTextContent().trim();
                            if (!id.isEmpty()) {
                                return id;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                analysisLog("LcID DOM解析失敗: " + f.getAbsolutePath());
            }
            
            // 正規表現フォールバック
            Pattern pattern = Pattern.compile("<info>.*?<ID>(.*?)</ID>.*?</info>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                String id = matcher.group(1).trim();
                if (!id.isEmpty()) {
                    return id;
                }
            }
            
        } catch (Exception e) {
            analysisLog("LcID抽出エラー: " + f.getAbsolutePath() + " -> " + e.getMessage());
        }
        
        return null;
    }
    
    // ========================================
    // XML処理ヘルパー
    // ========================================
    
    private static String tryReadAllTextAndSanitize(File f) {
        try {
            byte[] bytes = Files.readAllBytes(f.toPath());
            String content = null;
            try { content = new String(bytes, StandardCharsets.UTF_8); } catch (Exception e) { content = null; }
            if (content == null) {
                try { content = new String(bytes, Charset.forName("MS932")); } catch (Exception e) { content = new String(bytes); }
            }
            if (content.startsWith("\uFEFF")) content = content.substring(1);
            content = content.replace("\uFFFE", "").replace("\uFEFF", "");
            content = content.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
            int idx = content.indexOf('<');
            if (idx > 0) content = content.substring(idx);
            return content;
        } catch (IOException e) {
            analysisLog("ファイル読み込み失敗: " + f.getAbsolutePath() + " -> " + e.getMessage());
            return null;
        }
    }
    
    private static Document parseXmlString(String xml, File sourceFile) {
        String sourceLabel = (sourceFile == null) ? "(unknown)" : sourceFile.getAbsolutePath();
        if (shouldSkipDomParse(sourceFile)) {
            logChildnameDomSkipOnce(sourceFile);
            return null;
        }
        try {
            DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
            fac.setNamespaceAware(false);
            fac.setIgnoringComments(true);
            fac.setIgnoringElementContentWhitespace(true);
            DocumentBuilder b = fac.newDocumentBuilder();
            b.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) throws SAXException {
                    throw exception;
                }
                @Override
                public void error(SAXParseException exception) throws SAXException {
                    throw exception;
                }
                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                    throw exception;
                }
            });
            return b.parse(new InputSource(new StringReader(xml)));
        } catch (Exception ex) {
            System.err.println(buildXmlErrorShortMessage(sourceLabel, ex));
            analysisLog(buildXmlErrorDetailMessage(sourceLabel, xml, ex));
            return null;
        }
    }
    
    private static boolean shouldSkipDomParse(File sourceFile) {
        return sourceFile != null && "Childname.txt".equalsIgnoreCase(sourceFile.getName());
    }
    
    private static void logChildnameDomSkipOnce(File sourceFile) {
        String modName = extractModNameForLog(sourceFile);
        synchronized (CHILDNAME_DOM_SKIP_LOGGED_MODS) {
            if (CHILDNAME_DOM_SKIP_LOGGED_MODS.add(modName)) {
                analysisLog("Childname.txt はDOM解析対象外のためスキップ: mod=" + modName);
            }
        }
    }
    
    private static String extractModNameForLog(File sourceFile) {
        if (sourceFile == null) return "(unknown)";
        try {
            if (BASE_PATH != null) {
                Path base = Paths.get(BASE_PATH).toAbsolutePath().normalize();
                Path file = sourceFile.toPath().toAbsolutePath().normalize();
                if (file.startsWith(base)) {
                    Path rel = base.relativize(file);
                    if (rel.getNameCount() >= 1) {
                        return rel.getName(0).toString();
                    }
                }
            }
        } catch (Exception ignore) {
        }
        File parent = sourceFile.getParentFile();
        return parent != null ? parent.getName() : "(unknown)";
    }
    
    // ========================================
    // main() - 次のメッセージで実装
    // ========================================
    
    public static void main(String[] args) {
        String runTs = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        Scanner scanner = new Scanner(System.in);
        
        // エンコーディング選択
        ORIGINAL_OUT.print("出力ファイルのエンコーディングを選択 [1:UTF-8, 2:Shift_JIS] (デフォルト:1): ");
        String encChoice = scanner.nextLine().trim();
        Charset outCharset = StandardCharsets.UTF_8;
        if ("2".equals(encChoice)) {
            outCharset = Charset.forName("MS932");
        }
        OUTPUT_CHARSET = outCharset;
        initializeTerminalLog(runTs, OUTPUT_CHARSET);

        System.out.println();
        System.out.println("======================================");
        System.out.println("  Lobotomy MOD Analyzer v2.0-beta1.6");
        System.out.println("======================================");
        System.out.println();
        if ("2".equals(encChoice)) {
            System.out.println("→ ファイル出力文字コード: Shift_JIS (MS932)");
        } else {
            System.out.println("→ ファイル出力文字コード: UTF-8");
        }
        System.out.println("→ ターミナル表示文字コード: " + CONSOLE_CHARSET.name());
        System.out.println("→ ターミナル表示はターミナル側文字コード、ターミナルログ保存は選択した文字コードを使用します");
        System.out.println();
        
        // ログレベル選択
        System.out.print("ログレベルを選択してください [1:最小限, 2:標準, 3:詳細] (デフォルト:2): ");
        String logChoice = scanner.nextLine().trim();
        try {
            logLevel = Integer.parseInt(logChoice);
            if (logLevel < 1) logLevel = 1;
            if (logLevel > 3) logLevel = 3;
        } catch (Exception e) {
            logLevel = 2;
        }
        System.out.println("→ ログレベル: " + logLevel);
        System.out.println();
        
        // 解析ログ作成の確認
        System.out.print("解析ログファイルを作成しますか? [y/n] (デフォルト:n): ");
        String createLogChoice = scanner.nextLine().trim();
        ENABLE_ANALYSIS_LOG = createLogChoice.equalsIgnoreCase("y");
        
        try {
            if (ENABLE_ANALYSIS_LOG) {
                ANALYSIS_TEMP_FILE = new File(runTs + "_LMA_analysis_log.txt");
                ANALYSIS_WRITER = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(ANALYSIS_TEMP_FILE), outCharset));
                System.out.println("→ 解析ログ: " + ANALYSIS_TEMP_FILE.getName());
            } else {
                System.out.println("→ 解析ログ: 作成しない");
            }
            System.out.println();
            
            // BaseModsフォルダの探索
            System.out.println("BaseMods フォルダを探索中...");
            BASE_PATH = findBaseModsFolder();
            if (BASE_PATH == null) {
                System.err.println("ERROR: BaseModsフォルダが見つかりませんでした");
                return;
            }
            System.out.println("✓ BaseMods: " + BASE_PATH);
            
            OUTPUT_BASE_PATH = findProjectMoonLobotomyFolder();
            if (OUTPUT_BASE_PATH == null) {
                System.err.println("ERROR: Project_Moon\\Lobotomy フォルダが見つかりませんでした");
                return;
            }
            initializeOutputDirectories();
            System.out.println("✓ 出力ベース: " + OUTPUT_BASE_PATH);
            System.out.println();
            
            // BaseModList_v2.xml の読み込み
            File baseXml = new File(BASE_PATH, BASE_XML_RELATIVE);
            if (!baseXml.exists()) {
                System.err.println("ERROR: " + BASE_XML_RELATIVE + " が見つかりません");
                return;
            }
            
            List<ModInfo> modInfoList = parseBaseModList(baseXml);
            System.out.println("✓ MOD数: " + modInfoList.size());
            System.out.println();
            
            // 各MODの解析
            System.out.println("MODを解析中...");
            for (ModInfo mod : modInfoList) {
                if (logLevel >= 2) {
                    System.out.println("  - " + mod.modFolderName + (mod.useIt ? " [VALID]" : " [INVALID]"));
                }
                
                // LcIDの抽出
                String lcId = extractLcId(mod.modFolderName);
                
                // 各カテゴリの解析
                scanAbnormality(mod.modFolderName, lcId, mod.useIt);
                scanEquipment(mod.modFolderName, lcId, mod.useIt);
                scanCustomEffect(mod.modFolderName, lcId, mod.useIt);
            }
            System.out.println();
            
            // バニラデータの統合
            integrateVanillaData();
            
            // 出力ファイルの生成
            System.out.println("出力ファイルを生成中...");
            generateAllOutputs(modInfoList, outCharset);
            
            System.out.println();
            System.out.println("======================================");
            System.out.println("  処理が完了しました");
            System.out.println("======================================");
            System.out.println("ログ保存先: " + LOG_OUTPUT_DIR.getAbsolutePath());
            System.out.println("各種リスト保存先: " + LIST_OUTPUT_DIR.getAbsolutePath());
            System.out.println("  - ID_list: " + ID_LIST_OUTPUT_DIR.getAbsolutePath());
            System.out.println("  - DuplicateID_list: " + DUPLICATE_LIST_OUTPUT_DIR.getAbsolutePath());
            System.out.println("  - mod_list: " + MOD_LIST_OUTPUT_DIR.getAbsolutePath());
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeLogsAndRestoreConsole();
            moveLogFilesToOutputDirectory();
            scanner.close();
        }
    }
    
    // ========================================
    // XML/ファイル探索ヘルパー
    // ========================================
    
    private static void findXmlOrTxtFiles(File dir, List<File> result) {
        if (!dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile()) {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".xml") || name.endsWith(".txt")) {
                    result.add(f);
                }
            } else if (f.isDirectory()) {
                findXmlOrTxtFiles(f, result);
            }
        }
    }
    
    // ========================================
    // アブノーマリティ解析
    // ========================================
    
    private static void scanAbnormality(String modName, String lcId, boolean isValid) {
        File creatureGen = new File(BASE_PATH + File.separator + modName + File.separator + "Creature" + File.separator + "CreatureGen");
        if (!creatureGen.exists() || !creatureGen.isDirectory()) return;
        
        List<File> genFiles = new ArrayList<>();
        findXmlOrTxtFiles(creatureGen, genFiles);
        
        // Extract IDs
        Map<String, String> idToCodeNo = new HashMap<>();
        Map<String, String> idToName = new HashMap<>();
        
        for (File f : genFiles) {
            List<String> ids = extractAddValues(f);
            for (String id : ids) {
                if (!idToCodeNo.containsKey(id)) {
                    idToCodeNo.put(id, "");
                    idToName.put(id, "");
                }
            }
        }
        
        // Extract names from CreatureInfo
        File creatureInfo = new File(BASE_PATH + File.separator + modName + File.separator + "Creature" + File.separator + "CreatureInfo");
        if (creatureInfo.exists() && creatureInfo.isDirectory()) {
            // 言語フォルダの優先順位: jp → en → cn → kr → ru → (その他)
            String[] langPriority = {"jp", "en", "cn", "kr", "ru"};
            Map<String, AbnormalityDetails> allDetails = new HashMap<>();
            
            // 優先順位に従って言語フォルダを探索
            for (String lang : langPriority) {
                File langFolder = new File(creatureInfo, lang);
                if (langFolder.exists() && langFolder.isDirectory()) {
                    List<File> langFiles = new ArrayList<>();
                    findXmlOrTxtFiles(langFolder, langFiles);
                    
                    for (File f : langFiles) {
                        Map<String, AbnormalityDetails> details = extractAbnormalityDetails(f);
                        for (Map.Entry<String, AbnormalityDetails> e : details.entrySet()) {
                            // まだ取得していないIDのみ追加
                            if (idToName.containsKey(e.getKey()) && !allDetails.containsKey(e.getKey())) {
                                allDetails.put(e.getKey(), e.getValue());
                            }
                        }
                    }
                }
            }
            
            // 優先順位フォルダで見つからなかったIDを、その他のフォルダから探索
            if (allDetails.size() < idToName.size()) {
                List<File> otherFiles = new ArrayList<>();
                findXmlOrTxtFiles(creatureInfo, otherFiles);
                
                for (File f : otherFiles) {
                    // 既に処理した言語フォルダのファイルはスキップ
                    boolean isLangFolder = false;
                    for (String lang : langPriority) {
                        if (f.getAbsolutePath().contains(File.separator + lang + File.separator)) {
                            isLangFolder = true;
                            break;
                        }
                    }
                    if (isLangFolder) continue;
                    
                    Map<String, AbnormalityDetails> details = extractAbnormalityDetails(f);
                    for (Map.Entry<String, AbnormalityDetails> e : details.entrySet()) {
                        // まだ取得していないIDのみ追加
                        if (idToName.containsKey(e.getKey()) && !allDetails.containsKey(e.getKey())) {
                            allDetails.put(e.getKey(), e.getValue());
                        }
                    }
                }
            }
            
            // 取得した詳細情報を適用
            for (Map.Entry<String, AbnormalityDetails> e : allDetails.entrySet()) {
                AbnormalityDetails d = e.getValue();
                idToName.put(e.getKey(), d.name);
                idToCodeNo.put(e.getKey(), d.codeNo);
            }
        }
        
        // Add to map
        for (String id : idToCodeNo.keySet()) {
            String name = idToName.getOrDefault(id, "(名前不明)");
            String codeNo = idToCodeNo.getOrDefault(id, "");
            String filePath = modName + "\\Creature\\CreatureGen";
            
            ItemInfo info = ABN_MAP.computeIfAbsent(id, k -> new ItemInfo());
            info.add(modName, name, filePath, lcId, codeNo, isValid);
        }
        
        analysisLog("アブノーマリティ解析完了: " + modName + " (" + idToCodeNo.size() + " 件)");
    }
    
    private static class AbnormalityDetails {
        String name;
        String codeNo;
    }
    
    private static List<String> extractAddValues(File f) {
        List<String> result = new ArrayList<>();
        String content = tryReadAllTextAndSanitize(f);
        if (content == null) return result;
        
        try {
            Document doc = parseXmlString(content, f);
            if (doc != null) {
                NodeList nodes = doc.getElementsByTagName("add");
                for (int i = 0; i < nodes.getLength(); i++) {
                    String v = nodes.item(i).getTextContent().trim();
                    if (!v.isEmpty() && !result.contains(v)) result.add(v);
                }
                return result;
            }
        } catch (Exception ex) {
            analysisLog("add抽出DOM失敗: " + f.getAbsolutePath());
        }
        
        // Regex fallback
        Matcher m = Pattern.compile("<add[^>]*>(.*?)</add>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(content);
        while (m.find()) {
            String v = m.group(1).trim();
            if (!v.isEmpty() && !result.contains(v)) result.add(v);
        }
        return result;
    }
    
    private static Map<String, AbnormalityDetails> extractAbnormalityDetails(File f) {
        Map<String, AbnormalityDetails> result = new HashMap<>();
        String content = tryReadAllTextAndSanitize(f);
        if (content == null) return result;
        
        try {
            Document doc = parseXmlString(content, f);
            if (doc != null) {
                NodeList creatures = doc.getElementsByTagName("creature");
                for (int i = 0; i < creatures.getLength(); i++) {
                    Element creature = (Element) creatures.item(i);
                    
                    // Get ID from info element
                    NodeList infoNodes = creature.getElementsByTagName("info");
                    if (infoNodes.getLength() == 0) continue;
                    Element info = (Element) infoNodes.item(0);
                    String id = info.getAttribute("id").trim();
                    if (id.isEmpty()) continue;
                    
                    // Extract name (highest openLevel)
                    String name = "";
                    int maxOpenLevel = -1;
                    NodeList nameNodes = creature.getElementsByTagName("name");
                    for (int j = 0; j < nameNodes.getLength(); j++) {
                        Element nameEl = (Element) nameNodes.item(j);
                        String openLevelStr = nameEl.getAttribute("openLevel");
                        int openLevel = 0;
                        try {
                            openLevel = Integer.parseInt(openLevelStr);
                        } catch (Exception e) {}
                        
                        if (openLevel > maxOpenLevel) {
                            maxOpenLevel = openLevel;
                            name = nameEl.getTextContent().trim();
                        }
                    }
                    
                    // Extract codeNo
                    String codeNo = "";
                    NodeList codeNodes = creature.getElementsByTagName("codeNo");
                    if (codeNodes.getLength() > 0) {
                        codeNo = codeNodes.item(0).getTextContent().trim();
                    }
                    
                    AbnormalityDetails details = new AbnormalityDetails();
                    details.name = name.isEmpty() ? "(名前不明)" : name;
                    details.codeNo = codeNo;
                    result.put(id, details);
                }
            }
        } catch (Exception ex) {
            analysisLog("アブノーマリティ詳細抽出失敗: " + f.getAbsolutePath());
        }
        
        return result;
    }
    
    // ========================================
    // 装備品解析
    // ========================================
    
    private static void scanEquipment(String modName, String lcId, boolean isValid) {
        File equipTxts = new File(BASE_PATH + File.separator + modName + File.separator + "Equipment" + File.separator + "txts");
        if (!equipTxts.exists() || !equipTxts.isDirectory()) return;
        
        List<File> txtFiles = new ArrayList<>();
        findXmlOrTxtFiles(equipTxts, txtFiles);
        
        Map<String, EquipmentEntry> entries = new HashMap<>();
        
        for (File f : txtFiles) {
            List<EquipmentEntry> eqs = extractEquipmentEntries(f);
            for (EquipmentEntry eq : eqs) {
                entries.put(eq.id + "_" + eq.type, eq);
            }
        }
        
        // Load localization
        Map<String, String> locMap = loadLocalization(modName);
        
        // Add to maps
        for (EquipmentEntry eq : entries.values()) {
            String name = locMap.getOrDefault(eq.nameKey, eq.nameKey);
            String filePath = modName + "\\Equipment\\txts";
            
            Map<String, ItemInfo> targetMap = null;
            if (eq.type.equals("weapon")) {
                targetMap = WEP_MAP;
            } else if (eq.type.equals("armor")) {
                targetMap = ARM_MAP;
            } else if (eq.type.equals("special")) {
                targetMap = GFT_MAP;
            }
            
            if (targetMap != null) {
                ItemInfo info = targetMap.computeIfAbsent(eq.id, k -> new ItemInfo());
                info.add(modName, name, filePath, lcId, "", isValid);
            }
        }
        
        analysisLog("装備品解析完了: " + modName + " (" + entries.size() + " 件)");
    }
    
    private static class EquipmentEntry {
        String id;
        String nameKey;
        String type;
    }
    
    private static List<EquipmentEntry> extractEquipmentEntries(File f) {
        List<EquipmentEntry> result = new ArrayList<>();
        String content = tryReadAllTextAndSanitize(f);
        if (content == null) return result;
        
        try {
            Document doc = parseXmlString(content, f);
            if (doc != null) {
                NodeList nodes = doc.getElementsByTagName("equipment");
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element el = (Element) nodes.item(i);
                    String id = el.getAttribute("id").trim();
                    String type = el.getAttribute("type").trim().toLowerCase();
                    if (type.isEmpty()) type = "special";
                    
                    String nameKey = "";
                    NodeList nameNodes = el.getElementsByTagName("name");
                    if (nameNodes.getLength() > 0) {
                        nameKey = nameNodes.item(0).getTextContent().trim();
                    }
                    
                    if (!id.isEmpty()) {
                        EquipmentEntry e = new EquipmentEntry();
                        e.id = id;
                        e.nameKey = nameKey;
                        e.type = type;
                        result.add(e);
                    }
                }
            }
        } catch (Exception ex) {
            analysisLog("装備品抽出DOM失敗: " + f.getAbsolutePath());
        }
        
        return result;
    }
    
    private static Map<String, String> loadLocalization(String modName) {
        Map<String, String> loc = new HashMap<>();
        File xmlsBase = new File(BASE_PATH + File.separator + modName + File.separator + "Equipment" + File.separator + "xmls");
        if (!xmlsBase.exists() || !xmlsBase.isDirectory()) return loc;
        
        // Try language folders first
        for (String lang : LANG_ORDER) {
            File langDir = new File(xmlsBase, lang);
            if (langDir.exists() && langDir.isDirectory()) {
                List<File> files = new ArrayList<>();
                findXmlOrTxtFiles(langDir, files);
                for (File f : files) {
                    loc.putAll(parseLocalizeFile(f));
                }
                if (!loc.isEmpty()) return loc;
            }
        }
        
        // Fallback: all files in base
        List<File> files = new ArrayList<>();
        findXmlOrTxtFiles(xmlsBase, files);
        for (File f : files) {
            loc.putAll(parseLocalizeFile(f));
        }
        
        return loc;
    }
    
    private static Map<String, String> parseLocalizeFile(File f) {
        Map<String, String> result = new HashMap<>();
        String content = tryReadAllTextAndSanitize(f);
        if (content == null) return result;
        
        try {
            Document doc = parseXmlString(content, f);
            if (doc != null) {
                NodeList nodes = doc.getElementsByTagName("text");
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element el = (Element) nodes.item(i);
                    String id = el.getAttribute("id").trim();
                    String val = el.getTextContent().trim();
                    if (!id.isEmpty() && !val.isEmpty()) {
                        result.put(id, val);
                    }
                }
            }
        } catch (Exception ex) {
            analysisLog("ローカライズ解析失敗: " + f.getAbsolutePath());
        }
        
        return result;
    }
    
    // ========================================
    // CustomEffect解析
    // ========================================
    
    private static void scanCustomEffect(String modName, String lcId, boolean isValid) {
        File ceDir = new File(BASE_PATH + File.separator + modName + File.separator + "CustomEffect");
        if (!ceDir.exists() || !ceDir.isDirectory()) return;
        
        File[] subs = ceDir.listFiles();
        if (subs == null) return;
        
        for (File sub : subs) {
            if (sub.isDirectory()) {
                String folderName = sub.getName();
                CustomEffectData data = new CustomEffectData();
                data.modName = modName;
                data.lcId = (lcId == null) ? "None" : lcId;
                data.isValid = isValid;
                CUSTOM_EFFECT_MAP.computeIfAbsent(folderName, k -> new ArrayList<>()).add(data);
            }
        }
    }
    
    // ========================================
    // バニラデータ統合
    // ========================================
    
    private static void integrateVanillaData() {
        for (Map.Entry<String, VanillaEntry> e : VANILLA_ABN.entrySet()) {
            ItemInfo info = ABN_MAP.computeIfAbsent(e.getKey(), k -> new ItemInfo());
            info.add("バニラ", e.getValue().name, "バニラ", "None", e.getValue().codeNo, true);
        }
        
        for (Map.Entry<String, String> e : VANILLA_WEP.entrySet()) {
            ItemInfo info = WEP_MAP.computeIfAbsent(e.getKey(), k -> new ItemInfo());
            info.add("バニラ", e.getValue(), "バニラ", "None", "", true);
        }
        
        for (Map.Entry<String, String> e : VANILLA_ARM.entrySet()) {
            ItemInfo info = ARM_MAP.computeIfAbsent(e.getKey(), k -> new ItemInfo());
            info.add("バニラ", e.getValue(), "バニラ", "None", "", true);
        }
        
        for (Map.Entry<String, String> e : VANILLA_GFT.entrySet()) {
            ItemInfo info = GFT_MAP.computeIfAbsent(e.getKey(), k -> new ItemInfo());
            info.add("バニラ", e.getValue(), "バニラ", "None", "", true);
        }
        
        log(2, "バニラデータ統合完了");
    }
    
    // ========================================
    // main()関数
    // ========================================
    

    /**
     * MOD一覧ファイルの出力
     */
    private static void generateModListFiles(List<ModInfo> modInfoList, Charset cs) throws IOException {
        if (MOD_LIST_OUTPUT_DIR == null) return;
        
        List<String> validMods = new ArrayList<>();
        List<String> invalidMods = new ArrayList<>();
        List<String> allMods = new ArrayList<>();
        
        for (ModInfo mod : modInfoList) {
            allMods.add(mod.modFolderName);
            if (mod.useIt) {
                validMods.add(mod.modFolderName);
            } else {
                invalidMods.add(mod.modFolderName);
            }
        }
        
        // mods_list_01_(valid).txt (10個区切り + 総計)
        File validFile = new File(MOD_LIST_OUTPUT_DIR, "mods_list_01_(valid).txt");
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(validFile), cs))) {
            w.write("=== Valid MODs (Useit=true) ===\n\n");
            writeModsWithGrouping(w, validMods);
        }
        System.out.println("  ✓ " + validFile.getName());
        
        // mods_list_02_(invalid).txt (10個区切り + 総計)
        File invalidFile = new File(MOD_LIST_OUTPUT_DIR, "mods_list_02_(invalid).txt");
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(invalidFile), cs))) {
            w.write("=== Invalid MODs (Useit=false) ===\n\n");
            writeModsWithGrouping(w, invalidMods);
        }
        System.out.println("  ✓ " + invalidFile.getName());
        
        // mods_list_03_(all).txt (10個区切り + 総計)
        File allFile = new File(MOD_LIST_OUTPUT_DIR, "mods_list_03_(all).txt");
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(allFile), cs))) {
            w.write("=== All MODs ===\n\n");
            writeModsWithGrouping(w, allMods);
        }
        System.out.println("  ✓ " + allFile.getName());
    }
    
    /**
     * MODリストを10個ごとに区切って出力し、最後に総計を表示
     */
    private static void writeModsWithGrouping(BufferedWriter w, List<String> mods) throws IOException {
        for (int i = 0; i < mods.size(); i++) {
            w.write(mods.get(i) + "\n");
            
            // 10個ごとに空行を挿入
            if ((i + 1) % 10 == 0 && (i + 1) < mods.size()) {
                w.write("\n");
            }
        }
        
        // 最後に総計を表示
        w.write("\n");
        w.write("総計: " + mods.size() + " MODs\n");
    }


    /**
     * BaseModsフォルダを探索
     */
    private static String findBaseModsFolder() {
        // キャッシュファイルの確認
        File cacheFile = new File(BASEMODS_CACHE_FILENAME);
        if (cacheFile.exists()) {
            try (BufferedReader br = Files.newBufferedReader(cacheFile.toPath(), CACHE_CHARSET)) {
                String path = br.readLine();
                if (path != null && new File(path).exists()) {
                    return path;
                }
            } catch (IOException e) {
                // キャッシュ読み込み失敗、通常探索へ
            }
        }
        
        // 優先候補パス
        String[] candidates = {
            "D:\\SteamLibrary\\steamapps\\common\\LobotomyCorp\\LobotomyCorp_Data\\BaseMods",
            "C:\\Program Files (x86)\\Steam\\steamapps\\common\\LobotomyCorp\\LobotomyCorp_Data\\BaseMods",
            "C:\\Program Files\\Steam\\steamapps\\common\\LobotomyCorp\\LobotomyCorp_Data\\BaseMods"
        };
        
        for (String candidate : candidates) {
            File f = new File(candidate);
            if (f.exists() && f.isDirectory()) {
                File baseXml = new File(f, BASE_XML_RELATIVE);
                if (baseXml.exists()) {
                    savePathToCache(candidate);
                    return candidate;
                }
            }
        }
        
        // 全探索
        File[] roots = File.listRoots();
        for (File root : roots) {
            String found = searchRecursive(root, 0);
            if (found != null) {
                savePathToCache(found);
                return found;
            }
        }
        
        return null;
    }
    
    private static String searchRecursive(File dir, int depth) {
        if (depth > MAX_DEPTH) return null;
        if (!dir.exists() || !dir.isDirectory()) return null;
        
        File baseXml = new File(dir, BASE_XML_RELATIVE);
        if (baseXml.exists()) {
            return dir.getAbsolutePath();
        }
        
        File[] files = dir.listFiles();
        if (files == null) return null;
        
        for (File f : files) {
            if (f.isDirectory()) {
                String result = searchRecursive(f, depth + 1);
                if (result != null) return result;
            }
        }
        
        return null;
    }
    
    private static void savePathToCache(String path) {
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(BASEMODS_CACHE_FILENAME), CACHE_CHARSET)) {
            bw.write(path);
        } catch (IOException e) {
            // キャッシュ保存失敗は無視
        }
    }

    private static String findProjectMoonLobotomyFolder() {
        File cacheFile = new File(PROJECT_LOBOTOMY_CACHE_FILENAME);
        if (cacheFile.exists()) {
            try (BufferedReader br = Files.newBufferedReader(cacheFile.toPath(), CACHE_CHARSET)) {
                String path = br.readLine();
                if (path != null) {
                    File cached = new File(path);
                    if (cached.exists() && cached.isDirectory()) {
                        return cached.getAbsolutePath();
                    }
                }
            } catch (IOException e) {
                // キャッシュ読み込み失敗、通常探索へ
            }
        }
        
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.trim().isEmpty()) {
            File localLow = Paths.get(userHome, "AppData", "LocalLow").toFile();
            String found = searchProjectMoonLobotomy(localLow, 0);
            if (found != null) {
                saveProjectMoonLobotomyPathToCache(found);
                return found;
            }
        }
        
        File[] roots = File.listRoots();
        for (File root : roots) {
            String found = searchProjectMoonLobotomy(root, 0);
            if (found != null) {
                saveProjectMoonLobotomyPathToCache(found);
                return found;
            }
        }
        return null;
    }
    
    private static String searchProjectMoonLobotomy(File dir, int depth) {
        if (dir == null || depth > MAX_DEPTH || !dir.exists() || !dir.isDirectory()) return null;
        
        if ("Project_Moon".equalsIgnoreCase(dir.getName())) {
            File lobotomy = new File(dir, "Lobotomy");
            if (lobotomy.exists() && lobotomy.isDirectory()) {
                return lobotomy.getAbsolutePath();
            }
        }
        
        File directProjectMoon = new File(dir, "Project_Moon");
        if (directProjectMoon.exists() && directProjectMoon.isDirectory()) {
            File lobotomy = new File(directProjectMoon, "Lobotomy");
            if (lobotomy.exists() && lobotomy.isDirectory()) {
                return lobotomy.getAbsolutePath();
            }
        }
        
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isDirectory()) {
                String result = searchProjectMoonLobotomy(f, depth + 1);
                if (result != null) return result;
            }
        }
        return null;
    }
    
    private static void saveProjectMoonLobotomyPathToCache(String path) {
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(PROJECT_LOBOTOMY_CACHE_FILENAME), CACHE_CHARSET)) {
            bw.write(path);
        } catch (IOException e) {
            // キャッシュ保存失敗は無視
        }
    }
    
    private static void initializeOutputDirectories() throws IOException {
        if (OUTPUT_BASE_PATH == null) {
            throw new IOException("出力ベースパスが未設定です");
        }
        File modAnalyzerDir = new File(OUTPUT_BASE_PATH, "ModAnalyzer");
        File logDir = new File(modAnalyzerDir, "Log");
        File listDir = new File(modAnalyzerDir, "Various_lists");
        File idListDir = new File(listDir, "ID_list");
        File duplicateListDir = new File(listDir, "DuplicateID_list");
        File modListDir = new File(listDir, "mod_list");
        if (!logDir.exists() && !logDir.mkdirs()) {
            throw new IOException("Log フォルダを作成できません: " + logDir.getAbsolutePath());
        }
        if (!listDir.exists() && !listDir.mkdirs()) {
            throw new IOException("Various_lists フォルダを作成できません: " + listDir.getAbsolutePath());
        }
        if (!idListDir.exists() && !idListDir.mkdirs()) {
            throw new IOException("ID_list フォルダを作成できません: " + idListDir.getAbsolutePath());
        }
        if (!duplicateListDir.exists() && !duplicateListDir.mkdirs()) {
            throw new IOException("DuplicateID_list フォルダを作成できません: " + duplicateListDir.getAbsolutePath());
        }
        if (!modListDir.exists() && !modListDir.mkdirs()) {
            throw new IOException("mod_list フォルダを作成できません: " + modListDir.getAbsolutePath());
        }
        LOG_OUTPUT_DIR = logDir;
        LIST_OUTPUT_DIR = listDir;
        ID_LIST_OUTPUT_DIR = idListDir;
        DUPLICATE_LIST_OUTPUT_DIR = duplicateListDir;
        MOD_LIST_OUTPUT_DIR = modListDir;
    }

    /**
     * BaseModList_v2.xml を解析
     */
    private static List<ModInfo> parseBaseModList(File xmlFile) throws Exception {
        List<ModInfo> result = new ArrayList<>();
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) throws SAXException { throw exception; }
            @Override
            public void error(SAXParseException exception) throws SAXException { throw exception; }
            @Override
            public void fatalError(SAXParseException exception) throws SAXException { throw exception; }
        });
        Document doc;
        try {
            doc = builder.parse(xmlFile);
        } catch (Exception ex) {
            analysisLog(buildXmlErrorDetailMessage(xmlFile.getAbsolutePath(), tryReadAllTextAndSanitize(xmlFile), (Exception) ex));
            throw ex;
        }
        
        NodeList modNodes = doc.getElementsByTagName("ModInfoXml");
        for (int i = 0; i < modNodes.getLength(); i++) {
            Element modElement = (Element) modNodes.item(i);
            
            ModInfo info = new ModInfo();
            
            NodeList nameNodes = modElement.getElementsByTagName("modfoldername");
            if (nameNodes.getLength() > 0) {
                info.modFolderName = nameNodes.item(0).getTextContent();
            }
            
            NodeList useItNodes = modElement.getElementsByTagName("Useit");
            if (useItNodes.getLength() > 0) {
                String useItText = useItNodes.item(0).getTextContent();
                info.useIt = "true".equalsIgnoreCase(useItText);
            }
            
            result.add(info);
        }
        
        return result;
    }
    
    private static class ModInfo {
        String modFolderName;
        boolean useIt;
    }

    /**
     * 全出力ファイルを生成
     */
    private static void generateAllOutputs(List<ModInfo> modInfoList, Charset cs) throws IOException {
        // ID_list.txt
        generateIDList(cs, false);
        System.out.println("  ✓ ID_list.txt");
        
        // ID_list(valid).txt
        generateIDList(cs, true);
        System.out.println("  ✓ ID_list(valid).txt");
        
        // DuplicateID_list.txt
        generateDuplicateList(cs, false, false);
        System.out.println("  ✓ DuplicateID_list.txt");
        
        // DuplicateID_list(valid).txt
        generateDuplicateList(cs, true, false);
        System.out.println("  ✓ DuplicateID_list(valid).txt");
        
        // DuplicateID_list(LcIDadd).txt
        generateDuplicateList(cs, false, true);
        System.out.println("  ✓ DuplicateID_list(LcIDadd).txt");
        
        // DuplicateID_list(LcIDadd,valid).txt
        generateDuplicateList(cs, true, true);
        System.out.println("  ✓ DuplicateID_list(LcIDadd,valid).txt");
        
        // MOD一覧
        generateModListFiles(modInfoList, cs);
    }
    
    private static void generateIDList(Charset cs, boolean validOnly) throws IOException {
        String filename = validOnly ? "ID_list(valid).txt" : "ID_list.txt";
        File outFile = new File(ID_LIST_OUTPUT_DIR, filename);
        
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), cs))) {
            w.write("=== ID 一覧 ===\n\n");
            
            // Abnormality
            w.write("=== Abnormality ===\n");
            writeCategory(w, ABN_MAP, validOnly, true);
            
            // Weapon
            w.write("\n=== Weapon ===\n");
            writeCategory(w, WEP_MAP, validOnly, false);
            
            // Armor
            w.write("\n=== Armor ===\n");
            writeCategory(w, ARM_MAP, validOnly, false);
            
            // Gift
            w.write("\n=== Gift ===\n");
            writeCategory(w, GFT_MAP, validOnly, false);
        }
    }
    
    private static void writeCategory(BufferedWriter w, Map<String, ItemInfo> map, boolean validOnly, boolean includeCodeNo) throws IOException {
        for (Map.Entry<String, ItemInfo> entry : map.entrySet()) {
            String id = entry.getKey();
            ItemInfo info = entry.getValue();
            
            // 同じIDを持つ全MODのデータを収集
            List<ItemData> dataList = new ArrayList<>();
            for (ItemData data : info.modData.values()) {
                if (!validOnly || data.isValid) {
                    dataList.add(data);
                }
            }
            
            if (dataList.isEmpty()) continue;
            
            // ID : の部分を出力
            StringBuilder line = new StringBuilder();
            line.append(id).append(" : ");
            
            // 各MODのデータをカンマ区切りで出力
            for (int i = 0; i < dataList.size(); i++) {
                ItemData data = dataList.get(i);
                
                if (includeCodeNo && data.codeNo != null && !data.codeNo.isEmpty()) {
                    line.append(data.codeNo).append(" ");
                }
                
                line.append(data.name).append(" (").append(data.modName);
                if (data.lcId != null && !data.lcId.equals("None")) {
                    line.append("/").append(data.lcId);
                }
                line.append(")");
                
                // 最後以外はカンマとスペースを追加
                if (i < dataList.size() - 1) {
                    line.append(" , ");
                }
            }
            
            line.append("\n");
            w.write(line.toString());
        }
    }
    
    
    private static void generateDuplicateList(Charset cs, boolean validOnly, boolean requireLcIdMatch) throws IOException {
        String filename;
        if (validOnly && requireLcIdMatch) {
            filename = "DuplicateID_list(LcIDadd,valid).txt";
        } else if (validOnly) {
            filename = "DuplicateID_list(valid).txt";
        } else if (requireLcIdMatch) {
            filename = "DuplicateID_list(LcIDadd).txt";
        } else {
            filename = "DuplicateID_list.txt";
        }
        
        File outFile = new File(DUPLICATE_LIST_OUTPUT_DIR, filename);
        
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), cs))) {
            w.write("=== カテゴリ別 重複一覧 ===\n\n");
            
            // 各カテゴリの重複
            w.write("=== Abnormality ===\n");
            writeDuplicates(w, ABN_MAP, validOnly, requireLcIdMatch, true);
            
            w.write("\n=== Weapon ===\n");
            writeDuplicates(w, WEP_MAP, validOnly, requireLcIdMatch, false);
            
            w.write("\n=== Armor ===\n");
            writeDuplicates(w, ARM_MAP, validOnly, requireLcIdMatch, false);
            
            w.write("\n=== Gift ===\n");
            writeDuplicates(w, GFT_MAP, validOnly, requireLcIdMatch, false);
            
            // Cross-category duplicates
            w.write("\n=== Cross-category duplicates ===\n");
            writeCrossCategoryDuplicates(w, validOnly);
            
            // CustomEffect duplicates
            w.write("\n=== CustomEffect duplicates ===\n");
            writeCustomEffectDuplicates(w, validOnly, requireLcIdMatch);
        }
    }
    
    private static void writeDuplicates(BufferedWriter w, Map<String, ItemInfo> map, boolean validOnly, boolean requireLcIdMatch, boolean includeCodeNo) throws IOException {
        int duplicateCount = 0;
        
        for (Map.Entry<String, ItemInfo> entry : map.entrySet()) {
            String id = entry.getKey();
            ItemInfo info = entry.getValue();
            
            List<ItemData> validData = new ArrayList<>();
            for (ItemData data : info.modData.values()) {
                if (!validOnly || data.isValid) {
                    // "Remake" と "Girls' Edition" を除外
                    if (data.modName.contains("Remake") || data.modName.contains("Girls' Edition")) {
                        continue;
                    }
                    validData.add(data);
                }
            }
            
            if (validData.size() < 2) continue;
            
            // LcID重複チェック
            if (requireLcIdMatch) {
                Map<String, Integer> lcIdCount = new HashMap<>();
                for (ItemData data : validData) {
                    String lcId = data.lcId != null ? data.lcId : "None";
                    lcIdCount.put(lcId, lcIdCount.getOrDefault(lcId, 0) + 1);
                }
                
                boolean hasDuplicate = false;
                for (Integer count : lcIdCount.values()) {
                    if (count > 1) {
                        hasDuplicate = true;
                        break;
                    }
                }
                
                if (!hasDuplicate) continue;
            }
            
            // 重複を出力
            w.write(id + " が複数MODに存在:\n");
            for (ItemData data : validData) {
                w.write("  - " + data.modName);
                if (data.lcId != null && !data.lcId.equals("None")) {
                    w.write("/" + data.lcId);
                }
                w.write(" : ");
                if (includeCodeNo && data.codeNo != null && !data.codeNo.isEmpty()) {
                    w.write(data.codeNo + " ");
                }
                w.write(data.name + " : " + data.filePath + "\n");
            }
            w.write("\n");
            duplicateCount++;
        }
        
        if (duplicateCount == 0) {
            w.write("重複は検出されませんでした。\n");
        }
    }

    /**
     * Cross-category duplicates の出力
     */
    private static void writeCrossCategoryDuplicates(BufferedWriter w, boolean validOnly) throws IOException {
        // 全カテゴリのマップを配列にまとめる
        Map<String, ItemInfo>[] allMaps = new Map[] {ABN_MAP, WEP_MAP, ARM_MAP, GFT_MAP};
        String[] categoryNames = {"Abnormality", "Weapon", "Armor", "Gift"};
        
        // IDごとに存在するカテゴリを収集
        Map<String, List<CategoryEntry>> crossCategoryMap = new TreeMap<>(new MixedComparatorBigInt());
        
        for (int catIdx = 0; catIdx < allMaps.length; catIdx++) {
            String categoryName = categoryNames[catIdx];
            Map<String, ItemInfo> map = allMaps[catIdx];
            
            for (Map.Entry<String, ItemInfo> entry : map.entrySet()) {
                String id = entry.getKey();
                ItemInfo info = entry.getValue();
                
                // このカテゴリのこのIDに有効なデータがあるか確認
                for (ItemData data : info.modData.values()) {
                    if (!validOnly || data.isValid) {
                        // Remake と Girls' Edition を除外
                        if (data.modName.contains("Remake") || data.modName.contains("Girls' Edition")) {
                            continue;
                        }
                        
                        CategoryEntry ce = new CategoryEntry();
                        ce.category = categoryName;
                        ce.modName = data.modName;
                        ce.lcId = data.lcId;
                        ce.name = data.name;
                        ce.filePath = data.filePath;
                        
                        crossCategoryMap.computeIfAbsent(id, k -> new ArrayList<>()).add(ce);
                        break; // このカテゴリでは1つだけ追加
                    }
                }
            }
        }
        
        // 複数カテゴリに存在するIDのみ出力
        int count = 0;
        for (Map.Entry<String, List<CategoryEntry>> entry : crossCategoryMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                String id = entry.getKey();
                w.write(id + " が複数カテゴリに存在:\n");
                for (CategoryEntry ce : entry.getValue()) {
                    w.write("  - " + ce.category + "/" + ce.modName);
                    if (ce.lcId != null && !ce.lcId.equals("None")) {
                        w.write("/" + ce.lcId);
                    }
                    w.write(" : " + ce.name + " : " + ce.filePath + "\n");
                }
                w.write("\n");
                count++;
            }
        }
        
        if (count == 0) {
            w.write("Cross-category の重複は検出されませんでした。\n");
        }
    }
    
    /**
     * CustomEffect duplicates の出力
     */
    private static void writeCustomEffectDuplicates(BufferedWriter w, boolean validOnly, boolean requireLcIdMatch) throws IOException {
        int count = 0;
        
        for (Map.Entry<String, List<CustomEffectData>> entry : CUSTOM_EFFECT_MAP.entrySet()) {
            String folderName = entry.getKey();
            List<CustomEffectData> filtered = new ArrayList<>();
            for (CustomEffectData data : entry.getValue()) {
                if (!validOnly || data.isValid) {
                    if (data.modName.contains("Remake") || data.modName.contains("Girls' Edition")) {
                        continue;
                    }
                    filtered.add(data);
                }
            }
            
            if (filtered.size() < 2) continue;
            
            if (requireLcIdMatch) {
                Map<String, Integer> lcIdCount = new HashMap<>();
                for (CustomEffectData data : filtered) {
                    String lcId = data.lcId != null ? data.lcId : "None";
                    lcIdCount.put(lcId, lcIdCount.getOrDefault(lcId, 0) + 1);
                }
                boolean hasDuplicateLcId = false;
                for (Integer lcCount : lcIdCount.values()) {
                    if (lcCount >= 2) {
                        hasDuplicateLcId = true;
                        break;
                    }
                }
                if (!hasDuplicateLcId) continue;
            }
            
            w.write(folderName + " が複数のMODに存在:\n");
            for (CustomEffectData data : filtered) {
                w.write("  - " + data.modName);
                if (data.lcId != null && !data.lcId.equals("None")) {
                    w.write("/" + data.lcId);
                }
                w.write("\n");
            }
            w.write("\n");
            count++;
        }
        
        if (count == 0) {
            w.write("CustomEffect の重複は検出されませんでした。\n");
        }
    }
    
    /**
     * Cross-category エントリ
     */
    private static class CategoryEntry {
        String category;
        String modName;
        String lcId;
        String name;
        String filePath;
    }
    
    private static class CustomEffectData {
        String modName;
        String lcId;
        boolean isValid;
    }

}
