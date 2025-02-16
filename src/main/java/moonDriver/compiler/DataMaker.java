
package moonDriver.compiler;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dotnet4j.io.File;
import dotnet4j.io.Path;
import dotnet4j.util.compat.StringUtilities;
import moonDriver.common.MyEncoding;
import musicDriverInterface.CompilerInfo;
import net.sf.saxon.functions.Count;

import static java.lang.System.getLogger;


public class DataMaker {

    private static final Logger logger = getLogger(DataMaker.class.getName());

    private final Compiler compiler;
    private final Work wk;
    private final Strings str = new Strings();

    public DataMaker(Compiler compiler, Work wk) {
        this.compiler = compiler;
        this.wk = wk;

        for (int i = 0; i < tone_tbl.length; i++) tone_tbl[i] = new int[1024];
        for (int i = 0; i < envelope_tbl.length; i++) envelope_tbl[i] = new int[1024];
        for (int i = 0; i < pitch_env_tbl.length; i++) pitch_env_tbl[i] = new int[1024];
        for (int i = 0; i < pitch_mod_tbl.length; i++) pitch_mod_tbl[i] = new int[5];
        for (int i = 0; i < arpeggio_tbl.length; i++) arpeggio_tbl[i] = new int[1024];
        for (int i = 0; i < hard_effect_tbl.length; i++) hard_effect_tbl[i] = new int[5];
        for (int i = 0; i < effect_wave_tbl.length; i++) effect_wave_tbl[i] = new int[33];

        for (int i = 0; i < tonetbl_tbl.length; i++) tonetbl_tbl[i] = new int[1024 + 2];
        for (int i = 0; i < opl3op_tbl.length; i++) opl3op_tbl[i] = new int[1024 + 2];

        track_count = new LEN[Work.MML_MAX][][];
        for (int i = 0; i < Work.MML_MAX; i++) {
            track_count[i] = new LEN[_TRACK_MAX][];
            for (int j = 0; j < _TRACK_MAX; j++) {
                track_count[i][j] = new LEN[2];
                for (int k = 0; k < 2; k++) {
                    track_count[i][j][k] = new LEN();
                }
            }
        }
    }

    private int mml_idx = 0;
    private String songlabel; // [64];

    //extern int getFileSize(char* ptr);
    //extern int Asc2Int(char* ptr, int* cnt);
    //extern void strupper(char* ptr);
    //extern char* readTextFile(char* filename);
    //extern FILE* openDmc(char* name);
    //extern char* skipSpaceOld(char* ptr);
    //extern char* skipSpace(char* ptr);

    //extern char* skipQuote(char* ptr);
    //extern char* skipComment(char* ptr);
    //extern int isComment(char* ptr);

    //void putBankOrigin(FILE* fp, int bank);
    //int checkBankRange(int bank);
    //int double2int(double d);

//#define arraysizeof(x) ( sizeof(x) / sizeof(x[0]) )

    private int error_flag;                 // If an error occurs, the value is non-zero.
    private int octave;                     // Octave being converted
    private double length;                      // Length of note being converted
    private int octave_flag = 0;            // Octave switch ("<" ">" processing)
    private int gate_denom = 8;             // Denominator of q command
    private int pitch_correction = 0;       // Detune extended sounds, pitch envelopes, and LFO direction correction

    private int loop_flag;                  // If there is a channel loop, the value is other than 0.
    private int putAsm_pos;                 //

    private String mml_file_name;                // Current mml file name (used when outputting to assembler)
    private int mml_line_pos;               //
    private int mml_trk;                    //

    private int nest;                       // Repeat nesting level
    private LEN[][][] track_count; // [MML_MAX][_TRACK_MAX] [2];			// Total note length storage location (note length/frame/loop note length/loop frame)
    private int volume_flag;                // Volume Status
    private double tbase = 0.625;               // [frame/count] rate during conversion

    private int transpose;                  // The current transpose value

    // for MoonDriver
    private static final int ALLTRACK = 0xffff_ffff;

    private static final int BOPL4FLAG = 0x01;
    private static final int BOPL3FLAG = 0x02;

    private static final int OPL4_MAX = 24;
    private static final int OPL3_MAX = 18;

    private int BOPL3TRACK() {
        return BTRACK(24);
    }

    private static final int MAX_VOLUME = 127;

    private static final int HUSIC_EXT = 1;

    public enum enmEFTBL {
        END(0xffff),
        LOOP(0xfffe);
        final int v;

        enmEFTBL(int v) {
            this.v = v;
        }
    }

    public enum enmMCK {
        MCK_REPEAT_END(0xa0),
        MCK_REPEAT_ESC(0xa1),

        MDR_OPBASE(0xe6),
        MDR_LDOP2(0xe5),
        MDR_TVP(0xe4),
        MDR_DRUM(0xf2),
        MDR_FBS(0xe3),
        MDR_OPMODE(0xeb),

        MCK_SLAR(0xe9),

        MDR_REVERB(0xea),
        MDR_DAMP(0xeb),

        MDR_JUMP(0xe2),
        MDR_DRUM_BIT(0xe1),
        MDR_DRUM_NOTE(0xe0),

        MCK_GOTO(0xee),
        MCK_SET_SHIFT_AMOUNT(0xef),
        MCK_SET_FDS_HWENV(0xf0),
        MCK_SET_SUN5B_NOISE_FREQ(0xf1),
        MCK_SET_SUN5B_HARD_SPEED(0xf2),
        MCK_SET_FDS_HWEFFECT(0xf3),
        MCK_WAIT(0xf4),
        MCK_DATA_WRITE(0xf5),
        MCK_DIRECT_FREQ(0xf6),
        MCK_SET_NOTEENV(0xf7),
        MCK_SET_PITCHENV(0xf8),
        MCK_SET_HWSWEEP(0xf9),
        MCK_SET_DETUNE(0xfa),
        MCK_SET_LFO(0xfb),
        MCK_REST(0xfc),
        MCK_SET_VOL(0xfd), MCK_SUN5B_HARD_ENV(0xfd),
        MCK_SET_TONE(0xfe),
        MCK_DATA_END(0xff);
        final int v;

        enmMCK(int v) {
            this.v = v;
        }
    }

    private int sndgen_flag = BOPL4FLAG;    // Extended Sound Source Flag
    // Track Permission Flag
    private long track_allow_flag = ALLTRACK;
    // The actual track used
    private long actual_track_flag = 0;
    private int dpcm_track_num = 1;         // DPCM Track
    private int fds_track_num = 0;          // FDS Track
    private int vrc7_track_num = 0;         // VRC7 Track
    private int vrc6_track_num = 0;         // VRC6 Track
    private int n106_track_num = 0;         // Number of tracks using expansion sound source (namco106)
    private int fme7_track_num = 0;         // FME7 Track
    private int mmc5_track_num = 0;         // MMC5 Track

    private int opl4_track_num = OPL4_MAX;
    private int opl3_track_num = 0;

    /** DPCM Buffer */
    public static class DPCMTBL {

        public int flag;                       // Tone use/unuse flag
        public int index;                      // The index number that is actually written to the file.
        // If this is not -1, the filename is ignored and the DPCM of the index number is used (when sorting).
        public String fname;
        public int freq;
        public int start_adr;
        public int size;
        public int delta_init;
        public int bank_ofs;                    // 16KB(0x4000)
    }

    private byte[] dpcm_data;   // DPCM expansion data
    private int dpcm_size = 0;
    private int dpcm_reststop = 0;

    // HuSIC
    private int panvol = 0;                 // Current Pan Volume
    private int xpcm_size = 0;

    private int use_jump = 0; // Do a jump

    private String song_name = "Song Name"; // [1024]
    private String composer = "Artist"; // [1024]
    private String maker = "Maker"; // [1024]

    private String programer_buf = ""; // [1024]
    private String programer = null;

    // MoonDriver
    // PCM File
    private String pcm_name = ""; // [1024]
    private int use_pcm = 0;
    private boolean pcm_pack = false;

    private static final String str_track = "ABCDEFGHIJKLMNOPQRSTUVWXabcdefghijklmnopqr";// _TRACK_STR;

    // Error number
    private enum enmErrNum {
        COMMAND_NOT_DEFINED,
        DATA_ENDED_BY_LOOP_DEPTH_EXCEPT_0,
        DEFINITION_IS_WRONG,
        TONE_DEFINITION_IS_WRONG,
        ENVELOPE_DEFINITION_IS_WRONG,
        PITCH_ENVELOPE_DEFINITION_IS_WRONG,
        NOTE_ENVELOPE_DEFINITION_IS_WRONG,
        LFO_DEFINITION_IS_WRONG,
        DPCM_DEFINITION_IS_WRONG,
        DPCM_PARAMETER_IS_LACKING,
        FM_TONE_DEFINITION_IS_WRONG,
        ABNORMAL_PARAMETERS_OF_FM_TONE,
        N106_TONE_DEFINITION_IS_WRONG,
        ABNORMAL_PARAMETERS_OF_N106_TONE,
        ABNORMAL_VALUE_OF_REPEAT_COUNT,
        ABNORMAL_TONE_NUMBER,
        ABNORMAL_ENVELOPE_NUMBER,
        ABNORMAL_ENVELOPE_VALUE,
        ABNORMAL_PITCH_ENVELOPE_NUMBER,
        ABNORMAL_NOTE_ENVELOPE_NUMBER,
        ABNORMAL_LFO_NUMBER,
        ABNORMAL_PITCH_VALUE,
        ABNORMAL_VOLUME_VALUE,
        ABNORMAL_TEMPO_VALUE,
        ABNORMAL_QUANTIZE_VALUE,
        ABNORMAL_SHUFFLE_QUANTIZE_VALUE,
        ABNORMAL_SWEEP_VALUE,
        ABNORMAL_DETUNE_VALUE,
        ABNORMAL_SHIFT_AMOUNT,
        ABNORMAL_NOTE_AFTER_COMMAND,
        RELATIVE_VOLUME_WAS_USED_WITHOUT_SPECIFYING_VOLUME,
        VOLUME_RANGE_OVER_OF_RELATIVE_VOLUME,
        VOLUME_RANGE_UNDER_OF_RELATIVE_VOLUME,
        DATA_ENDED_BY_CONTINUATION_NOTE,
        DPCM_FILE_NOT_FOUND,
        DPCM_FILE_SIZE_OVER,
        DPCM_FILE_TOTAL_SIZE_OVER,
        INVALID_TRACK_HEADER,
        HARD_EFFECT_DEFINITION_IS_WRONG,
        EFFECT_WAVE_DEFINITION_IS_WRONG,
        ABNORMAL_HARD_EFFECT_NUMBER,
        ABNORMAL_TRANSPOSE_VALUE,
        TUPLET_BRACE_EMPTY,
        BANK_IDX_OUT_OF_RANGE,
        FRAME_LENGTH_LESSTHAN_0,
        ABNORMAL_NOTE_LENGTH_VALUE,
        PARAMETER_IS_LACKING,
        ABNORMAL_SELFDELAY_VALUE,
        CANT_USE_BANK_2_OR_3_WITH_DPCMBANKSWITCH,
        CANT_USE_SHIFT_AMOUNT_WITHOUT_PITCH_CORRECTION,
        UNUSE_COMMAND_IN_THIS_TRACK,

        /* for HuSIC */
        WTB_TONE_DEFINITION_IS_WRONG,
        ABNORMAL_PARAMETERS_OF_WTB_TONE,
        XPCM_DEFINITION_IS_WRONG,
        XPCM_PARAMETER_IS_LACKING,
        XPCM_FILE_NOT_FOUND,
        XPCM_FILE_SIZE_OVER,
        XPCM_FILE_TOTAL_SIZE_OVER,
        FMLFO_PARAM_IS_WRONG,

        TONETBL_DEFINITION_IS_WRONG,
        ABNORMAL_PARAMETERS_OF_TONETBL,

        ABNORMAL_PARAMETERS,

        /* MoonDriver */
        COMMAND_REDUNDANT,
    }

    // Error String
    // TODO make it resource bundle, vavi created properties already, see src/main/resources/lang
    private String[] ErrorlMessage = {
            "指定のコマンドはありません", "Command not defined",
            "ループ深度が0以外でデータが終了しました", "Data ended by loop depth except 0",
            "設定に誤りがあります", "Definition is wrong",
            "PSG音色設定に誤りがあります", "PSG Tone definition is wrong",
            "エンベロープ設定に誤りがあります", "Envelope definition is wrong",
            "ピッチエンベロープ設定に誤りがあります", "Pitch envelope definition is wrong",
            "ノートエンベロープ設定に誤りがあります", "Note envelope definition is wrong",
            "LFO設定に誤りがあります", "LFO definition is wrong",
            "DPCM設定に誤りがあります", "DPCM definition is wrong",
            "DPCM設定のパラメータが足りません", "DPCM parameter is lacking",
            "FM音色設定に誤りがあります", "FM tone definition is wrong",
            "FM用音色のパラメータが異常です", "Abnormal parameters of FM tone",
            "namco106音色設定に誤りがあります", "namco106 tone definition is wrong",
            "namco106用音色のパラメータが異常です", "Abnormal parameters of namco106 tone",
            "繰り返し回数の値が異常です", "Abnormal value of repeat count",
            "音色番号が異常です", "Abnormal tone number",
            "エンベロープ番号が異常です", "Abnormal envelope number",
            "エンベロープの値が異常です", "Abnormal envelope value",
            "ピッチエンベロープ番号の値が異常です", "Abnormal pitch envelope number",
            "ノートエンベロープ番号の値が異常です", "Abnormal note envelope number",
            "LFO番号の値が異常です", "Abnormal LFO number",
            "音程の値が異常です", "Abnormal pitch value",
            "音量の値が異常です", "Abnormal volume value",
            "テンポの値が異常です", "Abnormal tempo value",
            "クォンタイズの値が異常です", "Abnormal quantize value",
            "シャッフルクォンタイズの値が異常です", "Abnormal shuffle quantize value",
            "スイープの値が異常です", "Abnormal sweep value",
            "ディチューンの値が異常です", "Abnormal detune value",
            "ピッチシフト量の値が異常です", "Abnormal pitch shift amount value",

            "コマンド後のノートが異常です", "Abnormal note after command",

            "音量が指定されていない状態で相対音量を使用しました", "Relative volume was used without specifying volume",
            "相対音量(+)で音量の範囲を超えました", "Volume range over(+) of relative volume",
            "相対音量(-)で音量の範囲を超えました", "Volume range under(-) of relative volume",
            "連符処理の途中でデータが終了しました", "Data ended by Continuation note",
            "DPCMファイルがありません", "DPCM file not found",
            "DPCMデータのサイズが4081byteを超えました", "DPCM file size over",
            "DPCMデータのサイズが規定のサイズを超えました", "DPCM file total size over",
            "指定のトラックヘッダは無効です", "Invalid track header",
            "ハードウェアエフェクト設定に誤りがあります。", "Hardware effect definition is wrong",
            "エフェクト波形設定に誤りがあります。", "Effect wavetable definition is wrong",
            "ハードウェアエフェクト番号の値が異常です。", "Abnormal hardware effect number",
            "トランスポーズの値が異常です", "Abnormal transpose value",
            "連符の{}の中に音符がありません", "Tuplet {} empty",
            "バンクが範囲を超えました", "Bank index out of range",
            "音長が負の値です(unexpected error)", "Frame length is negative value (unexpected error)",
            "音長の値が異常です", "Abnormal note length value",
            "設定のパラメータが足りません", "Parameter is lacking",
            "セルフディレイの値が異常です", "Abnormal self-delay value",
            "DPCMサイズが0x4000を超える場合はバンク2と3は使用できません", "Cannot use bank 2 or 3 if DPCM size is greater than 0x4000",
            "#PITCH-CORRECTIONを指定しない限りピッチシフト量コマンドは使用できません", "Cannot use SA<num> without #PITCH-CORRECTION",
            "このトラックでは使用できないコマンドです", "Unuse command in this track",

            "WaveTable音色設定に誤りがあります", "WaveTable tone definition is wrong",
            "WaveTable用音色のパラメータが異常です", "Abnormal parameters of WaveTable tone",
            "XPCM設定に誤りがあります", "XPCM definition is wrong",
            "XPCM設定のパラメータが足りません", "XPCM parameter is lacking",
            "XPCMファイルがありません", "XPCM file not found",
            "XPCMデータのサイズが8192byteを超えました", "XPCM file size over",
            "XPCMデータのサイズが規定のサイズを超えました", "XPCM file total size over",
            "FMLFOパラメータに誤りがあります", "FMLFO parameter is wrong",

            "ToneTable設定に誤りがあります", "ToneTable definition is wrong",
            "ToneTableパラメータが異常です", "Abnormal parameters of ToneTable",

            "パラメータが異常です", "Abnormal parameters",

            "コマンドが重複しています", "Command is redundant",
    };

    private enum enmSys {
        TOO_MANY_INCLUDE_FILES,
        FRAME_LENGTH_IS_0,
        REPEAT2_FRAME_ERROR_OVER_3,
        IGNORE_PMCKC_BANK_CHANGE,
        THIS_NUMBER_IS_ALREADY_USED,
        DPCM_FILE_SIZE_ERROR
    }

    private String[] WarningMessage = new String[] {
            "インクルードファイルの数が多すぎます", "Too many include files",
            "フレーム音長が0になりました。", "frame length is 0",
            "リピート2のフレーム誤差が3フレームを超えています。", "Repeat2 frame error over 3 frames",
            "#BANK-CHANGE使用時は#SETBANK, NBは無視します", "Ignoring #SETBANK and NB if #BANK-CHANGE used",
            "定義番号が重複しています", "This definition number is already used",
            "DPCMサイズ mod 16 が1ではありません", "DPCM size mod 16 is not 1",
    };

    /**
     * Track Mask Functions
     */
    private static int isAllTrack(int trk) {
        return 1;
    }

    // Tempo-based to Frame-based Conversion Parameters
    private static final double _BASE = 192.0;
    private static final int _BASETEMPO = 75;

    public static class LEN {

        public double cnt;
        public int frm;
    }

    public static class GATE_Q {

        public int rate;
        public int adjust;
        // gate length = delta * rate/gate_denom + adjust
    }

    public static class HEAD {

        public String str;
        public int status;

        public HEAD(String str, int status) {
            this.str = str;
            this.status = status;
        }
    }

    private static final int _HEADER = 1;
    private static final int _TITLE = 2;
    private static final int _COMPOSER = 3;
    private static final int _MAKER = 4;
    private static final int _PROGRAMER = 5;
    private static final int _OCTAVE_REV = 6;
    private static final int _EX_DISKFM = 7;
    private static final int _EX_NAMCO106 = 8;
    private static final int _INCLUDE = 9;
    private static final int _BANK_CHANGE = 10;
    private static final int _EFFECT_INCLUDE = 11;
    private static final int _SET_SBANK = 12;
    private static final int _EX_VRC7 = 13;
    private static final int _EX_VRC6 = 14;
    private static final int _EX_FME7 = 15;
    private static final int _EX_MMC5 = 16;
    private static final int _NO_BANKSWITCH = 17;
    private static final int _DPCM_RESTSTOP = 18;
    private static final int _GATE_DENOM = 19;
    private static final int _AUTO_BANKSWITCH = 20;
    private static final int _PITCH_CORRECTION = 21;

    private static final int _SET_EFFECT = 0x20;
    private static final int _SET_TONE = 0x21;
    private static final int _SET_ENVELOPE = 0x22;
    private static final int _SET_PITCH_MOD = 0x23;
    private static final int _SET_PITCH_ENV = 0x24;
    private static final int _SET_ARPEGGIO = 0x25;
    private static final int _SET_DPCM_DATA = 0x26;
    private static final int _SET_FM_TONE = 0x27;
    private static final int _SET_N106_TONE = 0x28;
    private static final int _SET_VRC7_TONE = 0x29;
    private static final int _SET_HARD_EFFECT = 0x2A;
    private static final int _SET_EFFECT_WAVE = 0x2B;
    // MoonDriver
    private static final int _SET_TONETBL = 0x2E;
    private static final int _SET_FMOP = 0x2F;
    private static final int _SET_FMOP_FOUR = 0x30;
    private static final int _EX_OPL3 = 0x31;
    private static final int _OPL4_NOUSE = 0x32;
    private static final int _PCM_FILE = 0x33;
    private static final int _PCM_PACK = 0x1000;

    private static final int _TRACK = 0x40;
    private static final int _SAME_LINE = 0x8000_0000;

    private static final int _TRACK_MAX = (24 + 18);

    //                                        012345678901234567890123012345678901234567
    private static final String _TRACK_STR = "ABCDEFGHIJKLMNOPQRSTUVWXabcdefghijklmnopqr";

    private int BTRACK(int a) {
        return a;
    }

    private int BNOISETRACK() {
        return BTRACK(3);
    }

    private int BDPCMTRACK() {
        return BTRACK(4);
    }

    private int BFMTRACK() {
        return BTRACK(5);
    }

    private int BVRC7TRACK() {
        return BTRACK(6);
    }

    private int BVRC6TRACK() {
        return BTRACK(12);
    }

    private int BVRC6SAWTRACK() {
        return BTRACK(14);
    }

    private int BN106TRACK() {
        return BTRACK(15);
    }

    private int BFME7TRACK() {
        return BTRACK(23);
    }

    private int BMMC5TRACK() {
        return BTRACK(26);
    }

//    private int ALLTRACK = 0xffffffff;

    private static final int _PITCH_MOD_MAX = 64;
    private static final int _PITCH_ENV_MAX = 128;
    private static final int _ENVELOPE_MAX = 128;
    private static final int _TONE_MAX = 128;
    private static final int _DPCM_MAX = 64;
    private static final int _ARPEGGIO_MAX = 128;
    private static final int _FM_TONE_MAX = 128;
    private static final int _N106_TONE_MAX = 128;
    private static final int _VRC7_TONE_MAX = 64;
    private static final int _HARD_EFFECT_MAX = 16;
    private static final int _EFFECT_WAVE_MAX = 8;

    // MoonSound
    private static final int _TONETBL_MAX = 256;
    private static final int _OPL3TBL_MAX = 256;

    /* Command Status */
    private static final int PARAM_MAX = 8;

    public static class CMD {

        public String filename;
        public int line;
        public double cnt;    // The count is the number of times the track has elapsed since the beginning of the track.
        public int frm;    // ↑ in frame units
        public double lcnt;   // The loop point of the track (L command) is set as 0, and the number of counts that have elapsed since then (however, before L is 0)
        public int lfrm;   // ↑ in frame units
        public int cmd;
        public double len;    // Unit: count
        public int[] param = new int[PARAM_MAX];
    }

    private static final long PARAM_OMITTED = 0x8000_0000L;

    private static final int _NOTE_C = 0;
    private static final int _NOTE_D = 2;
    private static final int _NOTE_E = 4;
    private static final int _NOTE_F = 5;
    private static final int _NOTE_G = 7;
    private static final int _NOTE_A = 9;
    private static final int _NOTE_B = 11;
    private static final int MIN_NOTE = -3;
    private static final int MAX_NOTE = 0x8f;

    private int[] bank_sel = new int[_TRACK_MAX];   // 0 to 127 = Bank switch, 0xFF = No change
    private int allow_bankswitching = 1;
    private int dpcm_bankswitch = 0;
    private int auto_bankswitch = 0;
    private int curr_bank = 0x00;
    private int[] bank_usage = new int[128];        // bank_usage[0] is currently meaningless
    private int bank_maximum = 0;       // 8KB
    private int dpcm_extra_bank_num = 0;    // 8KB

    private int[][] tone_tbl = new int[_TONE_MAX][]; // [1024];	// Tone
    private int[][] envelope_tbl = new int[_ENVELOPE_MAX][]; // [1024];	// Envelope
    private int[][] pitch_env_tbl = new int[_PITCH_ENV_MAX][]; // [1024];	// Pitch Envelope
    private int[][] pitch_mod_tbl = new int[_PITCH_MOD_MAX][]; // [   5];	// LFO
    private int[][] arpeggio_tbl = new int[_ARPEGGIO_MAX][]; // [1024];	// Arpeggio
    private int[][] fm_tone_tbl; // [_FM_TONE_MAX][2+64];	// FM Tone
    private int[][] vrc7_tone_tbl; // [_VRC7_TONE_MAX][2+64];	// VRC7 Tone(The number of arrays depends on the function used.)
    private int[][] n106_tone_tbl; // [_N106_TONE_MAX][2+64];	// NAMCO106 Tone
    private int[][] hard_effect_tbl = new int[_HARD_EFFECT_MAX][]; // [5];	// FDS Hardware Effect
    private int[][] effect_wave_tbl = new int[_EFFECT_WAVE_MAX][]; // [33];	// Effect Wave (4088) Data

    private int[][] wtb_tone_tbl; // [_WTB_TONE_MAX][2+64];		// HuSIC WaveTable Tone

    private int[][] tonetbl_tbl = new int[_TONETBL_MAX][]; // [1024+2];
    private int[][] opl3op_tbl = new int[_OPL3TBL_MAX][]; // [1024+2];
    private int[] opl3op_flag = new int[_OPL3TBL_MAX]; // operator flag

    private DPCMTBL[] dpcm_tbl = new DPCMTBL[64]; // [_DPCM_MAX];                // DPCM
    private DPCMTBL[] xpcm_tbl = new DPCMTBL[64]; // [_DPCM_MAX];                // XPCM(for HuSIC)

    private enum enmMML {
        _TEMPO(MAX_NOTE + 1),
        _OCTAVE(MAX_NOTE + 2),
        _OCT_UP(MAX_NOTE + 3),
        _OCT_DW(MAX_NOTE + 4),
        _LENGTH(MAX_NOTE + 5),
        _ENVELOPE(MAX_NOTE + 6),
        _REL_ENV(MAX_NOTE + 7),
        _VOLUME(MAX_NOTE + 8),
        _VOL_PLUS(0x98), //
        _VOL_MINUS(0x99),
        _HARD_ENVELOPE(0x9a),
        _TONE(0x9b),
        _ORG_TONE(0x9c),
        _REL_ORG_TONE(0x9d),
        _SWEEP(0x9e),
        _SLAR(0x9f),
        _SONG_LOOP(0xa0), //
        _REPEAT_ST(0xa1),
        _REPEAT_END(0xa2),
        _REPEAT_ESC(0xa3),
        _CONT_NOTE(0xa4),
        _CONT_END(0xa5),
        _QUONTIZE(0xa6),
        _QUONTIZE2(0xa7),

        _TIE(0xa8), // 0xa8
        _DETUNE(0xa9),
        _LFO_ON(0xaa),
        _LFO_OFF(0xab),
        _EP_ON(0xac),
        _EP_OFF(0xad),
        _EN_ON(0xae),
        _EN_OFF(0xaf),
        _NOTE(0xb0), // 0xb0
        _KEY(0xb1),
        _WAIT(0xb2),
        _DATA_BREAK(0xb3),
        _DATA_WRITE(0xb4),
        _DATA_THRUE(0xb5),

        _NEW_BANK(0xb6),

        _REPEAT_ST2(0xb7),
        _REPEAT_END2(0xb8), // 0xb8
        _REPEAT_ESC2(0xb9),

        _TEMPO2(0xba),
        _TRANSPOSE(0xbb),
        _MH_ON(0xbc),
        _MH_OFF(0xbd),

        _SHUFFLE_QUONTIZE(0xbe),
        _SHUFFLE_QUONTIZE_RESET(0xbf),
        _SHUFFLE_QUONTIZE_OFF(0xc0), // 0xc0

        //_ARTICULATION_ADJUST,
        _KEY_OFF(0xc1),
        _SELF_DELAY_OFF(0xc2),
        _SELF_DELAY_ON(0xc3),
        _SELF_DELAY_QUEUE_RESET(0xc4),
        _XX_COMMAND(0xc5),
        _VRC7_TONE(0xc6),
        _SUN5B_HARD_SPEED(0xc7),
        _SUN5B_HARD_ENV(0xc8), // 0xc8
        _SUN5B_NOISE_FREQ(0xc9),
        _SHIFT_AMOUNT(0xca),

        // HuSIC

        _NOISE_SW(0xcb),
        _PAN(0xcc),
        _L_PAN(0xcd),
        _R_PAN(0xce),
        _C_PAN(0xcf),
        _WAVE_CHG(0xd0), // 0xd0
        _MODE_CHG(0xd1),
        _FMLFO_SET(0xd2),
        _FMLFO_OFF(0xd3),
        _FMLFO_FRQ(0xd4),

        // MoonDriver
        _REVERB_SET(0xd5),
        _DAMP_SET(0xd6),

        _SET_OPBASE(0xd7),
        _LOAD_OP2(0xd8), // 0xd8
        _SET_TVP(0xd9),
        _DRUM_SW(0xda),
        _SET_FBS(0xdb),
        _SET_OPM(0xdc),

        _JUMP_FLAG(0xdd),

        _DRUM_BIT(0xde),
        _DRUM_NOTE(0xdf),

        _DATA_WRITE_OFS(0xe0),

        _REST(0xfc),
        _NOP(0xfe),
        _TRACK_END(0xff);
        final int v;

        enmMML(int v) {
            this.v = v;
        }
    }

    private static final int SELF_DELAY_MAX = 8;

    /** header */
    private HEAD[] head = {
            new HEAD("#TITLE", _TITLE),
            new HEAD("#COMPOSER", _COMPOSER),
            new HEAD("#MAKER", _MAKER),
            new HEAD("#PROGRAMER", _PROGRAMER),
            new HEAD("#OCTAVE-REV", _OCTAVE_REV),
            new HEAD("#GATE-DENOM", _GATE_DENOM),
            new HEAD("#INCLUDE", _INCLUDE),
            //{"#EX-DISKFM", _EX_DISKFM},
            //{"#EX-NAMCO106", _EX_NAMCO106},
            //{"#EX-VRC7", _EX_VRC7},
            //{"#EX-VRC6", _EX_VRC6},
            //{"#EX-FME7", _EX_FME7},
            //{"#EX-MMC5", _EX_MMC5},
            new HEAD("#NO-BANKSWITCH", _NO_BANKSWITCH),
            new HEAD("#AUTO-BANKSWITCH", _AUTO_BANKSWITCH),
            new HEAD("#PITCH-CORRECTION", _PITCH_CORRECTION),
            new HEAD("#BANK-CHANGE", _BANK_CHANGE),
            new HEAD("#SETBANK", _SET_SBANK),
            new HEAD("#EFFECT-INCLUDE", _EFFECT_INCLUDE),
            new HEAD("#DPCM-RESTSTOP", _DPCM_RESTSTOP),
            // for HuSIC
            //{"@XPCM", _SET_XPCM_DATA},
            //{"@WT", _SET_WTB_TONE},
            // for MoonDriver
            new HEAD("@TONE", _SET_TONETBL),
            new HEAD("@OPF", _SET_FMOP_FOUR),
            new HEAD("@OPL", _SET_FMOP),
            new HEAD("#EX-OPL3", _EX_OPL3),
            new HEAD("#OPL4-NOUSE", _OPL4_NOUSE),
            new HEAD("#PCMFILE", _PCM_FILE),
            new HEAD("@DPCM", _SET_DPCM_DATA),
            new HEAD("@MP", _SET_PITCH_MOD),
            new HEAD("@EN", _SET_ARPEGGIO),
            new HEAD("@EP", _SET_PITCH_ENV),
            new HEAD("@FM", _SET_FM_TONE),
            new HEAD("@MH", _SET_HARD_EFFECT),
            new HEAD("@MW", _SET_EFFECT_WAVE),
            new HEAD("@OP", _SET_VRC7_TONE),
            new HEAD("@N", _SET_N106_TONE),
            new HEAD("@V", _SET_ENVELOPE),
            new HEAD("@", _SET_TONE),
            new HEAD("#PCMPACK", _PCM_PACK),
            new HEAD("", -1),
    };

    public static class MML {

        public String cmd;
        public int num;
        public Function<Integer, Integer> check; // (int trk);

        //		unsigned long		enable;
        public MML(String cmd, int num, Function<Integer, Integer> check) {
            this.cmd = cmd;
            this.num = num;
            this.check = check;
        }
    }

    // MML Commands
    private MML[] mml = {
            new MML("`", enmMML._DRUM_BIT.ordinal(), DataMaker::isAllTrack),
            new MML("c", _NOTE_C, DataMaker::isAllTrack),
            new MML("d", _NOTE_D, DataMaker::isAllTrack),
            new MML("e", _NOTE_E, DataMaker::isAllTrack),
            new MML("f", _NOTE_F, DataMaker::isAllTrack),
            new MML("g", _NOTE_G, DataMaker::isAllTrack),
            new MML("a", _NOTE_A, DataMaker::isAllTrack),
            new MML("b", _NOTE_B, DataMaker::isAllTrack),
            new MML("@n", enmMML._KEY.ordinal(), DataMaker::isAllTrack),
            new MML("n", enmMML._NOTE.ordinal(), DataMaker::isAllTrack),
            new MML("w", enmMML._WAIT.ordinal(), DataMaker::isAllTrack),
            new MML("@t", enmMML._TEMPO2.ordinal(), DataMaker::isAllTrack),
            new MML("t", enmMML._TEMPO.ordinal(), DataMaker::isAllTrack),
            new MML("o", enmMML._OCTAVE.ordinal(), DataMaker::isAllTrack),
            new MML(">", enmMML._OCT_UP.ordinal(), DataMaker::isAllTrack),
            new MML("<", enmMML._OCT_DW.ordinal(), DataMaker::isAllTrack),
            new MML("l", enmMML._LENGTH.ordinal(), DataMaker::isAllTrack),
            new MML("v+", enmMML._VOL_PLUS.ordinal(), DataMaker::isAllTrack),
            new MML("v-", enmMML._VOL_MINUS.ordinal(), DataMaker::isAllTrack),
            new MML("v", enmMML._VOLUME.ordinal(), DataMaker::isAllTrack),
            new MML("NB", enmMML._NEW_BANK.ordinal(), DataMaker::isAllTrack),
            new MML("EPOF", enmMML._EP_OFF.ordinal(), DataMaker::isAllTrack),
            new MML("EP", enmMML._EP_ON.ordinal(), DataMaker::isAllTrack),
            new MML("ENOF", enmMML._EN_OFF.ordinal(), DataMaker::isAllTrack),
            new MML("EN", enmMML._EN_ON.ordinal(), DataMaker::isAllTrack),
            new MML("MPOF", enmMML._LFO_OFF.ordinal(), DataMaker::isAllTrack),
            new MML("MP", enmMML._LFO_ON.ordinal(), DataMaker::isAllTrack),

            // for HuSIC
            //{"FSOF", _FMLFO_OFF, HULFO_TRK},
            //{"FS", _FMLFO_SET, HULFO_TRK},
            //{"FF", _FMLFO_FRQ, HULFO_TRK},
            //{"N", _NOISE_SW, HUNOISE_TRK},

            // for MoonDriver

            new MML("j", enmMML._JUMP_FLAG.ordinal(), DataMaker::isAllTrack),
            new MML("VOP", enmMML._REVERB_SET.ordinal(), DataMaker::isAllTrack),
            new MML("RV", enmMML._REVERB_SET.ordinal(), DataMaker::isAllTrack),
            new MML("DA", enmMML._DAMP_SET.ordinal(), DataMaker::isAllTrack),

            new MML("OPB", enmMML._SET_OPBASE.ordinal(), DataMaker::isAllTrack),
            new MML("WX", enmMML._LOAD_OP2.ordinal(), DataMaker::isAllTrack),
            new MML("TVP", enmMML._SET_TVP.ordinal(), DataMaker::isAllTrack),
            new MML("DR", enmMML._DRUM_SW.ordinal(), DataMaker::isAllTrack),
            new MML("DN", enmMML._DRUM_NOTE.ordinal(), DataMaker::isAllTrack),
            new MML("FB", enmMML._SET_FBS.ordinal(), DataMaker::isAllTrack),
            new MML("OPM", enmMML._SET_OPM.ordinal(), DataMaker::isAllTrack),

            new MML("PL", enmMML._L_PAN.ordinal(), DataMaker::isAllTrack),
            new MML("PR", enmMML._R_PAN.ordinal(), DataMaker::isAllTrack),
            new MML("PC", enmMML._C_PAN.ordinal(), DataMaker::isAllTrack),
            new MML("P", enmMML._PAN.ordinal(), DataMaker::isAllTrack),
            new MML("W", enmMML._WAVE_CHG.ordinal(), DataMaker::isAllTrack),
            new MML("M", enmMML._MODE_CHG.ordinal(), DataMaker::isAllTrack),

            new MML("SDQR", enmMML._SELF_DELAY_QUEUE_RESET.ordinal(), DataMaker::isAllTrack),
            new MML("SDOF", enmMML._SELF_DELAY_OFF.ordinal(), DataMaker::isAllTrack),
            new MML("SD", enmMML._SELF_DELAY_ON.ordinal(), DataMaker::isAllTrack),

            new MML("D", enmMML._DETUNE.ordinal(), DataMaker::isAllTrack),
            new MML("K", enmMML._TRANSPOSE.ordinal(), DataMaker::isAllTrack),

            new MML("@q", enmMML._QUONTIZE2.ordinal(), DataMaker::isAllTrack),
            new MML("@vr", enmMML._REL_ENV.ordinal(), DataMaker::isAllTrack),
            new MML("@v", enmMML._ENVELOPE.ordinal(), DataMaker::isAllTrack),

            //{"@@r", _REL_ORG_TONE, (TRACK(0)|TRACK(1)|FMTRACK|VRC7TRACK|VRC6PLSTRACK|N106TRACK|MMC5PLSTRACK)},
            //{"@@", _ORG_TONE, (TRACK(0)|TRACK(1)|FMTRACK|VRC7TRACK|VRC6PLSTRACK|N106TRACK|MMC5PLSTRACK)},

            new MML("@", enmMML._TONE.ordinal(), DataMaker::isAllTrack),
            new MML("&", enmMML._SLAR.ordinal(), DataMaker::isAllTrack),

            new MML("yo", enmMML._DATA_WRITE_OFS.ordinal(), DataMaker::isAllTrack),

            new MML("y", enmMML._DATA_WRITE.ordinal(), DataMaker::isAllTrack),
            new MML("x", enmMML._DATA_THRUE.ordinal(), DataMaker::isAllTrack),

            new MML("|:", enmMML._REPEAT_ST2.ordinal(), DataMaker::isAllTrack),
            new MML(":|", enmMML._REPEAT_END2.ordinal(), DataMaker::isAllTrack),
            new MML("\\", enmMML._REPEAT_ESC2.ordinal(), DataMaker::isAllTrack),

            new MML("k", enmMML._KEY_OFF.ordinal(), DataMaker::isAllTrack),

            new MML("L", enmMML._SONG_LOOP.ordinal(), DataMaker::isAllTrack),
            new MML("[", enmMML._REPEAT_ST.ordinal(), DataMaker::isAllTrack),
            new MML("]", enmMML._REPEAT_END.ordinal(), DataMaker::isAllTrack),
            new MML("|", enmMML._REPEAT_ESC.ordinal(), DataMaker::isAllTrack),
            new MML("{", enmMML._CONT_NOTE.ordinal(), DataMaker::isAllTrack),
            new MML("}", enmMML._CONT_END.ordinal(), DataMaker::isAllTrack),
            new MML("q", enmMML._QUONTIZE.ordinal(), DataMaker::isAllTrack),
            new MML("r", enmMML._REST.ordinal(), DataMaker::isAllTrack),
            new MML("^", enmMML._TIE.ordinal(), DataMaker::isAllTrack),
            new MML("!", enmMML._DATA_BREAK.ordinal(), DataMaker::isAllTrack),
            new MML("", enmMML._TRACK_END.ordinal(), DataMaker::isAllTrack),
    };

    /**
     * Error display
     * Input:
     * <p>
     * Output:
     * none
     */
    private void dispError(int no, String file, int line) {
        no = no * 2;
        if (wk.message_flag != 0) {
            no++;
        }
        if (!StringUtilities.isNullOrEmpty(file)) {
            logger.log(Level.ERROR, "%d {1:D6}: %d\r\n", file, line, ErrorlMessage[no]);
        } else {
            logger.log(Level.ERROR, "%d\n", ErrorlMessage[no]);
        }
        error_flag = 1;
    }


    /**
     * Warning Display
     * Input:
     * <p>
     * Output:
     * none
     */
    private void dispWarning(int no, String file, int line) {
        if (wk.warning_flag != 0) {
            no = no * 2;
            if (wk.message_flag != 0) {
                no++;
            }
            if (!StringUtilities.isNullOrEmpty(file)) {
                logger.log(Level.WARNING, "%d {1:D6}: %d\r\n", file, line, WarningMessage[no]);
            } else {
                logger.log(Level.WARNING, "%d\r\n", WarningMessage[no]);
            }
        }
    }


    /**
     * Removed C-type remarks
     * Input:
     * char	*ptr		:Data Storage Pointer
     * Output:
     * none
     */
    private void deleteCRemark(/* ref */ String buf) {
        StringBuilder sb = new StringBuilder(buf);
        int ptr = 0;

        int within_com = 0;
        while (sb.length() > ptr) {
            if (sb.charAt(ptr) == '/' && sb.charAt(ptr + 1) == '*') {
                within_com = 1;
                sb.setCharAt(ptr++, ' ');
                sb.setCharAt(ptr++, ' ');
                while (sb.length() > ptr) {
                    if (sb.charAt(ptr) == '*' && sb.charAt(ptr + 1) == '/') {
                        sb.setCharAt(ptr++, ' ');
                        sb.setCharAt(ptr++, ' ');
                        within_com = 0;
                        break;
                    }
                    if (sb.charAt(ptr) != '\n') {
                        sb.setCharAt(ptr, ' ');
                    }
                    ptr++;
                }
            } else if (ptr + 1 < sb.length() && sb.charAt(ptr) == '\r' && sb.charAt(ptr + 1) == '\n') {
                sb.setCharAt(ptr++, ' ');
            } else {
                ++ptr;
            }
        }
        if (within_com != 0) {
            logger.log(Level.WARNING, wk.message_flag != 0 ? "Reached EOF in comment" : "End of file reached with unclosed comment");
        }

        buf = sb.toString();
    }


    /**
     * Count the number of lines in a file
     * Input:
     * char	*data		:Data Storage Pointer
     * Output:
     * none
     */
    private int getLineCount(/* ref */ int ptr, String buf) {
        int line;

        line = 0;

        while (ptr < buf.length()) {
            if (buf.charAt(ptr) == '\n') {
                line++;
            }
            ptr++;
        }
        if (buf.charAt(ptr - 1) != '\n') {
            line++;
        }
        return line;
    }

    /**  */
    private LINE[] readMmlFile(String fname, String fname_short) {
        LINE[] lbuf;
        int line_count;
        int i;
        String filestr;
        filestr = wk.srcBuf; // System.IO.File.ReadAllText(fname);

        if (StringUtilities.isNullOrEmpty(filestr)) {
            error_flag = 1;
            return null;
        }

        int filestrPtr = 0;
        deleteCRemark(/* ref */ filestr);

        line_count = getLineCount(/* ref */ filestrPtr, filestr);
        lbuf = new LINE[(line_count + 1)];  /* Allocate a line buffer */

        lbuf[0] = new LINE();
        lbuf[0].status = _HEADER;       /* LINE status[0] was malloc'd	*/
        lbuf[0].str = filestr;      /* The pointer and size are stored */
        lbuf[0].ostr = filestr;      /* The pointer and size are stored */
        lbuf[0].line = line_count;
        lbuf[0].filename = fname;
        lbuf[0].shortname = fname_short;

        filestrPtr = 0;

        for (i = 1; i <= line_count; i++) {
            lbuf[i] = new LINE();
            lbuf[i].filename = fname;
            lbuf[i].shortname = fname_short;
            lbuf[i].line = i;

            int nextPtr = filestr.indexOf("\n", filestrPtr);
            lbuf[i].str = filestr.substring(filestrPtr, nextPtr - filestrPtr);
            lbuf[i].ostr = lbuf[i].str;
            filestrPtr = nextPtr + 1;
        }

        return lbuf;
    }

    //typedef struct st_line
    public static class LINE {

        public String filename;/* File name */
        public String shortname;/* Short file name */
        public int line;        /* Line number */
        public int status;      /* Line status (see define below) */
        public int param;       /* Parameter (tone/track number etc.) */
        public String str;      /* Line String */
        public LINE[] inc_ptr;    /* Include File Data Pointer */

        public String ostr;     /* Original line string */
    }

    /**
     * Set newline/EOF to 0 (NULL) (split buffer into lines)
     * Input:
     * char	*ptr	:Data Storage Pointer
     * Output:
     * none
     */
    private int changeNULL(int ptr, /* ref */ String buf) {
        StringBuilder sb = new StringBuilder(buf);

        while (ptr < sb.length() && sb.charAt(ptr) != '\n') {
            if (sb.charAt(ptr) == '\0') break;
            ptr++;
        }

        if (ptr < sb.length()) {
            sb.setCharAt(ptr, '\0');
            ptr++;
        }

        buf = sb.toString();
        return ptr;
    }


    /**
     * @hoge123 = Processing { ag ae aeag g}
     * @HOGE¥s*(¥d+)¥s*(=|)¥s*{.*?(}.*|)$
     */
    private int setEffectSub(LINE[] lptr, int line, /* ref */ int ptr_status_end_flag, int min, int max, int error_no) {
        int param, cnt = 0;
        String temp;
        int tempPtr = 0;
        temp = lptr[line].str;
        tempPtr = str.skipSpace(lptr[line].str, 0);
        param = str.Asc2Int(temp, tempPtr, /* ref */ cnt);
on_error:
        {
            if (cnt == 0)
                break on_error;
            if (param < min || max <= param)
                break on_error;

            lptr[line].param = param;
            tempPtr = str.skipSpace(temp, tempPtr + cnt);

            if (temp.charAt(tempPtr) == '=') {
                tempPtr++;
                tempPtr = str.skipSpace(temp, tempPtr);
            }

            if (temp.charAt(tempPtr) != '{')
                throw new IllegalStateException();

            lptr[line].str = temp.substring(tempPtr);
            ptr_status_end_flag = 1;


            while (tempPtr < temp.length() && temp.charAt(tempPtr) != '\0') {
                if (temp.charAt(tempPtr) == '}') {
                    ptr_status_end_flag = 0;
                }
                if (temp.charAt(tempPtr) == '\"') {
                    tempPtr = str.skipQuote(temp, tempPtr);
                } else if (str.isComment(temp, tempPtr))
                    tempPtr = str.skipComment(temp, tempPtr);
                else
                    tempPtr++;

            }

            return 1;
        }
        lptr[line].status = 0;
        dispError(error_no, lptr[line].filename, line);
        return 0;
    }

    private int skipTrackHeader(String st, int ptr) {
        if (ptr >= st.length() || st.charAt(ptr) == '\0')
            return 0;

        while (ptr < st.length() && st.charAt(ptr) != 0 && str_track.indexOf(st.charAt(ptr)) >= 0) ptr++;

        return str.skipSpace(st, ptr);
    }

    private int isTrackNum(String str, int ptr, int trk) {
        int temp;
        if (str.charAt(ptr) == 0)
            return 0;

        while ((temp = str_track.indexOf(str.charAt(ptr))) >= 0) {
            if (temp == trk)
                return 1;

            ptr++;
        }

        return 0;
    }

    /**
     * Request a header
     * Input:
     * char	*ptr	:Data Storage Pointer
     * Output:
     * none
     */
    private void getLineStatus(LINE[] lbuf, int inc_nest) {

        int line, i, param, cnt, track_flag, status_end_flag, bank, bank_ch;
        String temp, temp2;
        int tempPtr;
        int temp2Ptr;
        String ln;
        int ptr;


        int lptr = 0;
        status_end_flag = 0;

        for (line = 1; line <= lbuf[lptr].line; line++) {
            ln = lbuf[line].str;
            ptr = 0;

            ptr = str.skipSpace(ln, ptr);
            // Was the previous line the effect definition process?
            if (((lbuf[lptr + line - 1].status & _SET_EFFECT) != 0) && (status_end_flag != 0)) {
                lbuf[lptr + line].status = lbuf[lptr + line - 1].status | _SAME_LINE;
                lbuf[lptr + line].param = lbuf[lptr + line - 1].param;
                lbuf[lptr + line].str = ln;
                temp = ln;
                tempPtr = ptr;
                ptr = changeNULL(ptr, /* ref */ ln);
                temp = ln;
                status_end_flag = 1;

                while (tempPtr < temp.length() && temp.charAt(tempPtr) != '\0') {
                    if (temp.charAt(tempPtr) == '}') {
                        status_end_flag = 0;
                    }
                    if (temp.charAt(tempPtr) == '\"')
                        tempPtr = str.skipQuote(temp, tempPtr);
                    else if (str.isComment(temp, tempPtr))
                        tempPtr = str.skipComment(temp, tempPtr);
                    else
                        tempPtr++;
                }

                // If there is nothing at the beginning of a line, it is considered an invalid line.
            } else if (ptr == ln.length() || ln.charAt(ptr) == '\n' || ln.charAt(ptr) == '\0') {
                lbuf[lptr + line].status = 0;
                lbuf[lptr + line].str = ln;
                ptr = changeNULL(ptr, /* ref */ ln);
            } else {
                // Make the header string uppercase when using #/@ headers
                if (ln.charAt(ptr) == '#' || ln.charAt(ptr) == '@') {
                    StringBuilder sb = new StringBuilder(ln);
                    i = 1;
                    while ((sb.charAt(ptr + i) != ' ') && (sb.charAt(ptr + i) != '\t') && (sb.charAt(ptr + i) != '\n')) {
                        sb.setCharAt(ptr + i, String.valueOf(Character.toUpperCase(sb.charAt(ptr + i))).charAt(0));
                        i++;
                    }
                    // Compare the header string to the table string
                    for (i = 0; head[i].status != -1; i++) {
                        if (sb.substring(ptr).indexOf(head[i].str) >= 0) {
                            break;
                        }
                    }
                    lbuf[lptr + line].status = head[i].status;
                    lbuf[lptr + line].str = lbuf[lptr + line].str.substring(str.skipSpaceOld(ln, ptr + head[i].str.length())); // Header + whitespace to start
                } else if (str_track.indexOf(ln.charAt(ptr)) >= 0) {
                    track_flag = 0;
                    temp = ln;
                    tempPtr = ptr;
                    while (tempPtr < ln.length() && temp.charAt(tempPtr) != ' ' && temp.charAt(tempPtr) != '\t') {
                        temp2Ptr = str_track.indexOf(temp.charAt(tempPtr));
                        if (temp2Ptr < 0) {
                            dispError(enmErrNum.INVALID_TRACK_HEADER.ordinal(), lbuf[lptr + line].filename, line);
                        } else {
                            track_flag = 1;
                        }
                        tempPtr++;
                    }

                    if (track_flag != 0) {
                        lbuf[lptr + line].status = _TRACK;
                        lbuf[lptr + line].param = track_flag;
                        lbuf[lptr + line].str = ln.substring(ptr);
                    } else {
                        lbuf[lptr + line].status = 0;
                        lbuf[lptr + line].param = 0;
                    }
                } else {
                    lbuf[lptr + line].status = -1;
                    lbuf[lptr + line].str = ln.substring(str.skipSpace(ln, ptr));
                }

                ptr = changeNULL(ptr, /* ref */ ln);

                switch (lbuf[lptr + line].status) {
                    // Processing the Include command
                    case _INCLUDE:
                        if (inc_nest > 16) {
                            // Nesting is limited to 16 levels (if it is called recursively it will not terminate)
                            dispWarning(enmSys.TOO_MANY_INCLUDE_FILES.ordinal(), lbuf[lptr + line].filename, line);
                            lbuf[lptr + line].status = 0;
                        } else {
                            LINE[] ltemp;
                            temp = lbuf[lptr + line].str;
                            tempPtr = str.skipSpaceOld(lbuf[line].str, lptr); // Try not to skip the '/'
                            ltemp = readMmlFile(temp, temp);
                            if (ltemp != null) {
                                lbuf[lptr + line].inc_ptr = ltemp;
                                ++inc_nest;
                                getLineStatus(lbuf[lptr + line].inc_ptr, inc_nest);
                                --inc_nest;
                            } else {
                                lbuf[lptr + line].status = 0; // File read failure error
                                error_flag = 1;
                            }
                        }
                        break;
                    // LFO Commands
                    case _SET_PITCH_MOD:
                        setEffectSub(lbuf, line, /* ref */ status_end_flag, 0, _PITCH_MOD_MAX, enmErrNum.LFO_DEFINITION_IS_WRONG.ordinal());
                        break;
                    // Pitch Envelope Commands
                    case _SET_PITCH_ENV:
                        setEffectSub(lbuf, line, /* ref */ status_end_flag, 0, _PITCH_ENV_MAX, enmErrNum.PITCH_ENVELOPE_DEFINITION_IS_WRONG.ordinal());
                        break;
                    // Volume Envelope Commands
                    case _SET_ENVELOPE:
                        setEffectSub(lbuf, line, /* ref */ status_end_flag, 0, _ENVELOPE_MAX, enmErrNum.ENVELOPE_DEFINITION_IS_WRONG.ordinal());
                        break;
                    // Original Sounds
                    case _SET_TONE:
                        setEffectSub(lbuf, line, /* ref */ status_end_flag, 0, _TONE_MAX, enmErrNum.TONE_DEFINITION_IS_WRONG.ordinal());
                        break;
                    // arpeggio
                    case _SET_ARPEGGIO:
                        setEffectSub(lbuf, line, /* ref */ status_end_flag, 0, _ARPEGGIO_MAX, enmErrNum.NOTE_ENVELOPE_DEFINITION_IS_WRONG.ordinal());
                        break;
                    // DPCM Registration Command
                    case _SET_DPCM_DATA:
                        setEffectSub(lbuf, line, /* ref */ status_end_flag, 0, _DPCM_MAX, enmErrNum.DPCM_DEFINITION_IS_WRONG.ordinal());
                        break;
                    // VRC7 Tone
                    case _SET_VRC7_TONE:
                        setEffectSub(lbuf, line, /* ref */ status_end_flag, 0, _VRC7_TONE_MAX, enmErrNum.FM_TONE_DEFINITION_IS_WRONG.ordinal());
                        break;
                    // FM Tones
                    case _SET_FM_TONE:
                        setEffectSub(lbuf, line, /* ref */ status_end_flag, 0, _DPCM_MAX, enmErrNum.FM_TONE_DEFINITION_IS_WRONG.ordinal());
                        break;
//                    // HuSIC XPCM
//                    case _SET_XPCM_DATA:
//                        setEffectSub(lbuf, line, /* ref */ status_end_flag, 0, _FM_TONE_MAX, enmErrNum.XPCM_DEFINITION_IS_WRONG.ordinal());
//                        break;
//                    // HuSIC WTB
//                    case _SET_WTB_TONE:
//                        setEffectSub(lbuf, line, /* ref */ status_end_flag, 0, _WTB_TONE_MAX, enmErrNum.WTB_TONE_DEFINITION_IS_WRONG.ordinal());
//                        break;
                    // MoonDriver
                    // Waveform sound source tone
                    case _SET_TONETBL:
                        setEffectSub(lbuf, line, /* ref */ status_end_flag, 0, _TONETBL_MAX, enmErrNum.TONETBL_DEFINITION_IS_WRONG.ordinal());
                        break;
                    // FM sound source
                    case _SET_FMOP:
                    case _SET_FMOP_FOUR:
                        setEffectSub(lbuf, line, /* ref */ status_end_flag, 0, _OPL3TBL_MAX, enmErrNum.FM_TONE_DEFINITION_IS_WRONG.ordinal());
                        break;
                    // MoonDriver OPL3 FM
                    case _EX_OPL3:
                        sndgen_flag |= BOPL3FLAG;
                        opl3_track_num = (OPL3_MAX);
                        break;
                    // MoonDriver OPL4 no use
                    case _OPL4_NOUSE:
                        sndgen_flag &= (~BOPL4FLAG);
                        opl4_track_num = 0;
                        break;


//                    // Namco106 sound source
//                    case _SET_N106_TONE:
//                        setEffectSub(lptr, line, & status_end_flag, 0, _N106_TONE_MAX, N106_TONE_DEFINITION_IS_WRONG.ordinal());
//                        break;
                    // Hardware Effects
                    case _SET_HARD_EFFECT:
                        setEffectSub(lbuf, line, /* ref */ status_end_flag, 0, _HARD_EFFECT_MAX, enmErrNum.HARD_EFFECT_DEFINITION_IS_WRONG.ordinal());
                        break;
                    // Effect Waveform
                    case _SET_EFFECT_WAVE:
                        setEffectSub(lbuf, line, /* ref */ status_end_flag, 0, _EFFECT_WAVE_MAX, enmErrNum.EFFECT_WAVE_DEFINITION_IS_WRONG.ordinal());
                        break;
//                    // DISKSYSTEM FM sound source use flag
//                    case _EX_DISKFM:
//                        sndgen_flag |= BDISKFM;
//                        track_allow_flag |= FMTRACK;
//                        fds_track_num = 1;
//                        break;
//                    // VRC7 FM sound source use flag
//                    case _EX_VRC7:
//                        sndgen_flag |= BVRC7;
//                        track_allow_flag |= VRC7TRACK;
//                        vrc7_track_num = 6;
//                        break;
//                    // VRC6 sound source usage flag
//                    case _EX_VRC6:
//                        sndgen_flag |= BVRC6;
//                        track_allow_flag |= VRC6TRACK;
//                        vrc6_track_num = 3;
//                        break;
//                    // FME7 sound source usage flag
//                    case _EX_FME7:
//                        sndgen_flag |= BFME7;
//                        track_allow_flag |= FME7TRACK;
//                        fme7_track_num = 3;
//                        break;
//                    // MMC5 sound source usage flag
//                    case _EX_MMC5:
//                        sndgen_flag |= BMMC5;
//                        track_allow_flag |= MMC5TRACK;
//                        mmc5_track_num = 2;
//                        break;
//                    // namco106 Extended sound source usage flag
//                    case _EX_NAMCO106:
//                        temp = skipSpace(lptr[line].str);
//                        param = Asc2Int(temp, & cnt);
//                        if (cnt != 0 && (0 <= param && param <= 8)) {
//                            if (param == 0) {
//                                param = 1;
//                            }
//                            lptr[line].param = param;
//                            n106_track_num = param;
//                            sndgen_flag |= BNAMCO106;
//                            for (i = 0; i < param; i++) {
//                                track_allow_flag |= TRACK(BN106TRACK + i);
//                            }
//                        } else {
//                            dispError(DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
//                            lptr[line].status = 0;
//                        }
//                        break;
//                    // DPCM sound stops on 'r' command
//                    case _DPCM_RESTSTOP:
//                        dpcm_reststop = 1;
//                        break;
//                    // Disable bankswitching in NSF mapper
//                    case _NO_BANKSWITCH:
//                        allow_bankswitching = 0;
//                        break;
                    // Automatic Bank Switching
                    case _AUTO_BANKSWITCH:
                        temp = lbuf[lptr + line].str;
                        tempPtr = str.skipSpaceOld(lbuf[line].str, lptr); // Try not to skip the '/'
                        cnt = 0;
                        param = str.Asc2Int(temp, tempPtr, /* ref */ cnt);
                        if (cnt != 0 && (0 <= param && param <= 8192)) {
                            // Only activate the first time
                            if (auto_bankswitch == 0) {
                                bank_usage[0] = 8192 - param;
                            }
                            auto_bankswitch = 1;
                        } else {
                            dispError(enmErrNum.DEFINITION_IS_WRONG.ordinal(), lbuf[lptr + line].filename, line);
                        }
                        break;
//                    // Bank switching embedding (provisional compatibility measure)
//                    case _BANK_CHANGE:
//                            /*
//                                #BANK-CHANGE <num0>,<num1>
//                                This is an extended format for the bank switching above.
//                                <num0> is the bank number and can have a value between 0 and 2.
//                                <num1> is the track number and can have a value between 1 and 14,
//                                with 1 corresponding to track A, 2=B, 3=C, ...P=7. Incidentally,
//                                the following do the same thing:
//                                #BANK-CHANGE	n
//                                #BANK-CHANGE	0,n
//
//                                If you use #BANK-CHANGE to bring tracks to the same bank,
//                                only the last one specified is valid. This specification was not well understood.
//                                Since ppmck considers all of them to be valid,
//                                there is an incompatibility in this respect.
//
//                                To compile old MML for mckc, delete all but the last one.
//
//                            */
//                            /*
//                                Numbers and tracks are not compatible.
//
//                                mckc
//                                A B C D E | F | P Q R	S	T	U	V	W
//                                1 2 3 4 5 | 6 | 7 8 9 10 11 12 13 14
//                                pmckc
//                                A B C D E | F | G H I	J	K	L |	P	Q	R	S	T	U	V	W
//                                1 2 3 4 5 | 6 | 7 8 9 10 11 12 | 13 14 15 16 17 18 19 20
//                                ppmckc
//                                A B C D E | F | G H I	J	K	L |	M	N	O |	P	Q	R	S	T	U	V	W |	X	Y	Z |	a	b
//                                1 2 3 4 5 | 6 | 7 8 9 10 11 12 | 13 14 15 | 16 17 18 19 20 21 22 23 | 24 25 26 | 27 28
//
//                                To compile old MML for mckc, just add 9 manually after P.
//                                (It is better not to do it automatically.)
//                                I mean, it's wrong to have to look at a table like this.
//                            */
//                        temp = skipSpace(lptr[line].str);
//                        param = Asc2Int(temp, & cnt);
//                        if (cnt != 0) {
//                            temp += cnt;
//                            temp = skipSpace(temp);
//                            if (*temp == ',') {
//                                // Extended Format
//                                temp++;
//                                if ((0 <= param) && (param <= 2)) {
//                                    bank = param; // 0,1,2 correspond to 1,2,3
//                                    //logger.log(Level.TRACE, "bank: %d\n", bank );
//                                    temp = skipSpace(temp);
//                                    param = Asc2Int(temp, & cnt); // 1,2,3 corresponds to ABC, so 0,1,2 corresponds to
//                                    if (cnt != 0 && (1 <= param && param <= _TRACK_MAX)) {
//                                        //bank_change[bank] = param-1;
//                                        bank_sel[param - 1] = bank + 1;
//                                    } else {
//                                        dispError(DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
//                                        lptr[line].status = 0;
//                                        //bank_change[bank] = 0xff;
//                                    }
//                                } else {
//                                    dispError(DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
//                                    lptr[line].status = 0;
//                                }
//                            } else {
//                                // Non-extended format, put in bank 1
//                                if (cnt != 0 && (1 <= param && param <= _TRACK_MAX)) {
//                                    //bank_change[0] = param-1;
//                                    bank_sel[param - 1] = 1;
//                                } else {
//                                    dispError(DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
//                                    lptr[line].status = 0;
//                                    //bank_change[0] = 0xff;
//                                }
//                            }
//                        } else {
//                            dispError(DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
//                            lptr[line].status = 0;
//                        }
//                        break;
//                    // Bank Switching
//                    case _SET_SBANK:
//                        temp = skipSpace(lptr[line].str);
//
//                        if ((temp2 = strchr(str_track, * temp)) !=NULL) {
//                        // Track designation by ABC...
//                        param = (int) ((temp2 - str_track) + 1);
//                        temp++;
//                        } else {
//                            // Numeric track designation
//                            param = Asc2Int(temp, & cnt);
//                            if (cnt == 0) {
//                                dispError(DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
//                                lptr[line].status = 0;
//                                break;
//                            } else {
//                                temp += cnt;
//                            }
//                        }
//
//                        temp = skipSpace(temp);
//                        if (*temp == ',') { // Bank Expansion
//                            temp++;
//                            if ((1 <= param) && (param <= _TRACK_MAX)) {
//                                bank_ch = param;
//                                // logger.log(Level.TRACE, "bank: %d\n", bank );
//                                temp = skipSpace(temp);
//                                param = Asc2Int(temp, & cnt);
//                                if (cnt != 0) {
//                                    if (checkBankRange(param) == 0) {
//                                        dispError(BANK_IDX_OUT_OF_RANGE, lptr[line].filename, line);
//                                        break;
//                                    }
//                                    bank_sel[bank_ch - 1] = param;
//                                } else {
//                                    dispError(DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
//                                    lptr[line].status = 0;
//                                }
//                            } else {
//                                dispError(DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
//                                lptr[line].status = 0;
//                            }
//                        } else {
//                            dispError(DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
//                            lptr[line].status = 0;
//                        }
//                    break;
//
//                    //
//                    case _EFFECT_INCLUDE:
//                        include_flag = 1;
//                        break;
                    // title
                    case _TITLE:
                        //tempPtr = str.skipSpaceOld(lbuf[lptr + line].str, 0);
                        song_name = lbuf[lptr + line].str.replaceFirst("^\\s+", "");
                        if (!song_name.isEmpty() && song_name.charAt(song_name.length() - 1) == ' ')
                            song_name = song_name.substring(0, song_name.length() - 1);
                        song_name = song_name.substring(0, Math.min(song_name.length(), 1023));
                        break;
                    // Composer
                    case _COMPOSER:
                        //temp = skipSpaceOld(lptr[line].str);
                        composer = lbuf[lptr + line].str.replaceFirst("^\\s+", "");
                        if (!composer.isEmpty() && composer.charAt(composer.length() - 1) == ' ')
                            composer = composer.substring(0, composer.length() - 1);
                        composer = composer.substring(0, Math.min(composer.length(), 1023));
                        break;
                    // Manufacturer
                    case _MAKER:
                        //temp = skipSpaceOld(lptr[line].str);
                        maker = lbuf[lptr + line].str.replaceFirst("^\\s+", "");
                        if (!maker.isEmpty() && maker.charAt(maker.length() - 1) == ' ')
                            maker = maker.substring(0, maker.length() - 1);
                        maker = maker.substring(0, Math.min(maker.length(), 1023));
                        break;
                    // Dedicated
                    case _PROGRAMER:
                        //temp = skipSpaceOld(lptr[line].str);
                        programer_buf = lbuf[lptr + line].str.replaceFirst("^\\s+", "");
                        if (!programer_buf.isEmpty() && programer_buf.charAt(programer_buf.length() - 1) == ' ')
                            programer_buf = programer_buf.substring(0, programer_buf.length() - 1);
                        programer_buf = programer_buf.substring(0, Math.min(programer_buf.length(), 1023));
                        programer = programer_buf;
                        break;
                    // PCM File
                    case _PCM_FILE:
                        temp = lbuf[line].str;
                        tempPtr = str.skipSpaceOld(lbuf[line].str, lptr);
                        pcm_name = temp.substring(tempPtr); // , 1023);
                        use_pcm = 1;
                        break;
                    // PCMPACK
                    case _PCM_PACK:
                        temp = lbuf[line].str;
                        tempPtr = str.skipSpaceOld(lbuf[line].str, lptr);
                        temp = temp.substring(tempPtr).trim().toUpperCase();
                        pcm_pack = false;
                        if (temp == "ON") {
                            pcm_pack = true;
                        }
                        break;

                    // Inverting Octave Symbols
                    case _OCTAVE_REV:
                        temp = lbuf[line].str;
                        tempPtr = str.skipSpace(lbuf[line].str, lptr);
                        cnt = 0;
                        param = str.Asc2Int(temp, tempPtr, /* ref */ cnt);
                        if (cnt != 0) {
                            if (param == 0) {
                                octave_flag = 0;
                            } else {
                                octave_flag = 1;
                            }
                        } else {
                            octave_flag = 1;
                        }
                        break;
                    // q command denominator change
                    case _GATE_DENOM:
                        temp = lbuf[line].str;
                        tempPtr = str.skipSpace(lbuf[line].str, lptr);
                        cnt = 0;
                        param = str.Asc2Int(temp, tempPtr, /* ref */ cnt);
                        if (cnt != 0 && param > 0) {
                            gate_denom = param;
                        } else {
                            dispError(enmErrNum.DEFINITION_IS_WRONG.ordinal(), lbuf[lptr + line].filename, line);
                            lbuf[lptr + line].status = 0;
                        }
                        break;
                    // Detune, pitch envelope, and LFO direction correction
                    case _PITCH_CORRECTION:
                        pitch_correction = 1;
                        break;
                    // No header
                    case -1:
                        if ((lbuf[lptr + line - 1].status & _SET_EFFECT) != 0) {
                            lbuf[lptr + line].status = lbuf[lptr + line - 1].status | _SAME_LINE;
                            lbuf[lptr + line].str = ln; // ptr;
                        } else {
                            // Error Checking
                            dispError(enmErrNum.COMMAND_NOT_DEFINED.ordinal(), lbuf[lptr + line].filename, line);
                            lbuf[lptr + line].status = 0;
                            lbuf[lptr + line].str = ln; // ptr;
                        }
                        break;
                    case _TRACK:
                        break;
                    default:
                        throw new UnsupportedOperationException();
                }
            }
        }
    }

    /**
     * Acquiring a tone
     * Input:
     * <p>
     * Output:
     * none
     */
    private void getTone(LINE[] lptr) {
        int line, i, no, end_flag, offset, num, cnt;
        String ptrs;
        int ptr;

        cnt = 0;

        for (line = 1; line < lptr.length; line++) { // lptr[line].line; line++)
            // It's a tone definition, but an error occurs when using _SAME_LINE.
            if (lptr[line].status == (_SET_TONE | _SAME_LINE)) {
                dispError(enmErrNum.TONE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
                continue;
            }

            // Include File Processing
            if (lptr[line].status == _INCLUDE) {
                getTone(lptr[line].inc_ptr);
                continue;
            }

            // Tone data found?
            if (lptr[line].status != _SET_TONE)
                continue;

            no = lptr[line].param; // Get tone number
            ptrs = lptr[line].str;
            ptr = 0;
            ptr++; // Skip the '{'
            if (tone_tbl[no][0] != 0) {
                dispWarning(enmSys.THIS_NUMBER_IS_ALREADY_USED.ordinal(), lptr[line].filename, line);
            }
            tone_tbl[no][0] = 0;
            offset = 0;
            i = 1;
            end_flag = 0;

            while (end_flag == 0) {
                ptr = str.skipSpace(ptrs, ptr);
                switch (ptrs.charAt(ptr)) {
                    case '}':
                        if (tone_tbl[no][0] >= 1) {
                            tone_tbl[no][i] = enmEFTBL.END.v;
                            tone_tbl[no][0]++;
                        } else {
                            dispError(enmErrNum.PARAMETER_IS_LACKING.ordinal(), lptr[line].filename, line);
                            tone_tbl[no][0] = 0;
                        }
                        end_flag = 1;
                        line += offset;
                        break;
                    case '|':
                        tone_tbl[no][i] = enmEFTBL.LOOP.v;
                        tone_tbl[no][0]++;
                        i++;
                        ptr++;
                        break;
                    case '\0':
                        offset++;
                        if (line + offset <= lptr[line].line) {
                            if ((lptr[line + offset].status & _SAME_LINE) == _SAME_LINE) {
                                ptrs = lptr[line + offset].str;
                                ptr = 0;
                            }
                        } else {
                            dispError(enmErrNum.TONE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
                            tone_tbl[no][0] = 0;
                            end_flag = 1;
                        }
                        break;
                    default:
                        num = str.Asc2Int(ptrs, ptr, /* ref */ cnt);
                        // Remove restrictions for vrc6 (built-in square wave, MMC5 up to 3)
                        //if( cnt != 0 && (0 <= num && num <= 3) ) {
                        if (cnt != 0 && (0 <= num && num <= 7)) {
                            tone_tbl[no][i] = num;
                            tone_tbl[no][0]++;
                            ptr += cnt;
                            i++;
                        } else {
                            dispError(enmErrNum.TONE_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
                            tone_tbl[no][0] = 0;
                            end_flag = 1;
                        }
                        break;
                }
                ptr = str.skipSpace(ptrs, ptr);
                if (ptrs.charAt(ptr) == ',') {
                    ptr++;
                }
            }
        }
    }

    /**
     * Obtaining an envelope
     * Input:
     * <p>
     * Output:
     * none
     */
    void getEnvelope(LINE[] lptr) {
        int line, i, no, end_flag, offset, num, cnt;
        String buf;
        int ptr;

        cnt = 0;

        for (line = 1; line < lptr.length; line++) {
            // Envelope definition, but _SAME_LINE causes an error
            if (lptr[line].status == (_SET_ENVELOPE | _SAME_LINE)) {
                dispError(enmErrNum.ENVELOPE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
                continue;
            }

            // Include File Processing
            if (lptr[line].status == _INCLUDE) {
                getEnvelope(lptr[line].inc_ptr);
                continue;
            }

            // Envelope data discovery?
            if (lptr[line].status == _SET_ENVELOPE) {
                no = lptr[line].param; // Obtain envelope number
                buf = lptr[line].str;
                ptr = 0;
                ptr++; // Skip the '{'
                if (envelope_tbl[no][0] != 0) {
                    dispWarning(enmSys.THIS_NUMBER_IS_ALREADY_USED.ordinal(), lptr[line].filename, line);
                }
                envelope_tbl[no][0] = 0;
                offset = 0;
                i = 1;
                end_flag = 0;
                while (end_flag == 0) {
                    ptr = str.skipSpace(buf, ptr);
                    char c = (ptr < buf.length()) ? buf.charAt(ptr) : '\0';
                    switch (c) {
                        case '}':
                            if (envelope_tbl[no][0] >= 1) {
                                envelope_tbl[no][i] = enmEFTBL.END.v;
                                envelope_tbl[no][0]++;
                            } else {
                                dispError(enmErrNum.PARAMETER_IS_LACKING.ordinal(), lptr[line].filename, line);
                                envelope_tbl[no][0] = 0;
                            }
                            end_flag = 1;
                            line += offset;
                            break;
                        case '|':
                            envelope_tbl[no][i] = enmEFTBL.LOOP.v;
                            envelope_tbl[no][0]++;
                            i++;
                            ptr++;
                            break;
                        case '\0':
                            offset++;
                            if (line + offset < lptr.length) {
                                if ((lptr[line + offset].status & _SAME_LINE) == _SAME_LINE) {
                                    buf = lptr[line + offset].str;
                                    ptr = 0;
                                }
                            } else {
                                dispError(enmErrNum.ENVELOPE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
                                envelope_tbl[no][0] = 0;
                                end_flag = 1;
                            }
                            break;
                        default:
                            num = str.Asc2Int(buf, ptr, /* ref */ cnt);
                            if (cnt != 0 && (0 <= num && num <= 127)) {
                                envelope_tbl[no][i] = num;
                                envelope_tbl[no][0]++;
                                ptr += cnt;
                                i++;
                            } else {
                                dispError(enmErrNum.ENVELOPE_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
                                envelope_tbl[no][0] = 0;
                                end_flag = 1;
                            }
                            break;
                    }
                    ptr = str.skipSpace(buf, ptr);
                    c = (ptr < buf.length()) ? buf.charAt(ptr) : '\0';
                    if (c == ',') {
                        ptr++;
                    }
                }
            }
        }
    }

    /**
     * Getting the Pitch Envelope
     * Input:
     * <p>
     * Output:
     * none
     */
    private void getPitchEnv(LINE[] lptr) {
        int line, i, no, end_flag, offset, num, cnt;
        String buf;
        int ptr;

        cnt = 0;

        for (line = 1; line < lptr.length; line++) {
            // Pitch envelope definition, but _SAME_LINE causes an error
            if (lptr[line].status == (_SET_PITCH_ENV | _SAME_LINE)) {
                dispError(enmErrNum.PITCH_ENVELOPE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
                continue;
            }

            // Include File Processing
            if (lptr[line].status == _INCLUDE) {
                getPitchEnv(lptr[line].inc_ptr);
                continue;
            }

            // Pitch envelope data found?
            if (lptr[line].status != _SET_PITCH_ENV)
                continue;

            no = lptr[line].param; // Get pitch envelope number
            buf = lptr[line].str;
            ptr = 0;
            ptr++; // Skip the '{'
            if (pitch_env_tbl[no][0] != 0) {
                dispWarning(enmSys.THIS_NUMBER_IS_ALREADY_USED.ordinal(), lptr[line].filename, line);
            }
            pitch_env_tbl[no][0] = 0;
            offset = 0;
            i = 1;
            end_flag = 0;
            while (end_flag == 0) {
                ptr = str.skipSpace(buf, ptr);
                char c = (ptr < buf.length()) ? buf.charAt(ptr) : '\0';

                switch (c) {
                    case '}':
                        if (pitch_env_tbl[no][0] >= 1) {
                            pitch_env_tbl[no][i] = enmEFTBL.END.v;
                            pitch_env_tbl[no][0]++;
                        } else {
                            dispError(enmErrNum.PARAMETER_IS_LACKING.ordinal(), lptr[line].filename, line);
                            pitch_env_tbl[no][0] = 0;
                        }
                        end_flag = 1;
                        line += offset;
                        break;
                    case '|':
                        pitch_env_tbl[no][i] = enmEFTBL.LOOP.v;
                        pitch_env_tbl[no][0]++;
                        i++;
                        ptr++;
                        break;
                    case '\0':
                        offset++;
                        if (line + offset < lptr.length) {
                            if ((lptr[line + offset].status & _SAME_LINE) == _SAME_LINE) {
                                buf = lptr[line + offset].str;
                                ptr = 0;
                            }
                        } else {
                            dispError(enmErrNum.PITCH_ENVELOPE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
                            pitch_env_tbl[no][0] = 0;
                            end_flag = 1;
                        }
                        break;
                    default:
                        num = str.Asc2Int(buf, ptr, /* ref */ cnt);

                        // Pitch direction correction
                        if (pitch_correction != 0)
                            num = 0 - num;

                        if (cnt != 0 && (-127 <= num && num <= 126)) {
                            pitch_env_tbl[no][i] = num;
                            pitch_env_tbl[no][0]++;
                            ptr += cnt;
                            i++;
                        } else {
                            dispError(enmErrNum.PITCH_ENVELOPE_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
                            pitch_env_tbl[no][0] = 0;
                            end_flag = 1;
                        }
                        break;
                }

                ptr = str.skipSpace(buf, ptr);
                c = (ptr < buf.length()) ? buf.charAt(ptr) : '\0';

                if (c == ',') {
                    ptr++;
                }
            }
        }
    }

    /**
     * Getting pitch modulation
     * Input:
     * <p>
     * Output:
     * none
     */
    private void getPitchMod(LINE[] lptr) {
        int line, i, no, end_flag, offset, num, cnt;
        String buf;
        int ptr;

        cnt = 0;

        for (line = 1; line < lptr.length; line++) {
            // It's a tone definition, but an error occurs when using _SAME_LINE.
            if (lptr[line].status == (_SET_PITCH_MOD | _SAME_LINE)) {
                dispError(enmErrNum.LFO_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
            }

            // Include File Processing
            if (lptr[line].status == _INCLUDE) {
                getPitchMod(lptr[line].inc_ptr);
            }

            // Tone data found?
            if (lptr[line].status == _SET_PITCH_MOD) {
                no = lptr[line].param; // Get LFO number
                buf = lptr[line].str;
                ptr = 0;
                ptr++; // Skip the '{'
                if (pitch_mod_tbl[no][0] != 0) {
                    dispWarning(enmSys.THIS_NUMBER_IS_ALREADY_USED.ordinal(), lptr[line].filename, line);
                }
                pitch_mod_tbl[no][0] = 0;
                offset = 0;
                i = 1;
                end_flag = 0;
                while (end_flag == 0) {
                    ptr = str.skipSpace(buf, ptr);
                    char c = (ptr < buf.length()) ? buf.charAt(ptr) : '\0';
                    switch (c) {
                        case '}':
                            if (pitch_mod_tbl[no][0] >= 3) {
                                //OK.
                            } else {
                                dispError(enmErrNum.PARAMETER_IS_LACKING.ordinal(), lptr[line].filename, line);
                                pitch_mod_tbl[no][0] = 0;
                            }
                            end_flag = 1;
                            line += offset;
                            break;
                        case '\0':
                            offset++;
                            if (line + offset < lptr.length) {
                                if ((lptr[line + offset].status & _SAME_LINE) == _SAME_LINE) {
                                    buf = lptr[line + offset].str;
                                    ptr = 0;
                                }
                            } else {
                                dispError(enmErrNum.LFO_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
                                pitch_mod_tbl[no][0] = 0;
                                end_flag = 1;
                            }
                            break;
                        default:
                            num = str.Asc2Int(buf, ptr, /* ref */ cnt);
                            if (cnt != 0) {
                                switch (i) {
                                    case 1:
                                    case 2:
                                    case 3:
                                        if (0 <= num && num <= 255) {
                                            pitch_mod_tbl[no][i] = num;
                                            pitch_mod_tbl[no][0]++;
                                            ptr += cnt;
                                            i++;
                                        } else {
                                            dispError(enmErrNum.LFO_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
                                            pitch_mod_tbl[no][0] = 0;
                                            end_flag = 1;
                                        }
                                        break;
                                    case 4:
                                        if (0 <= num && num <= 255) {
                                            pitch_mod_tbl[no][i] = num;
                                            pitch_mod_tbl[no][0]++;
                                            ptr += cnt;
                                            i++;
                                        } else {
                                            dispError(enmErrNum.LFO_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
                                            pitch_mod_tbl[no][0] = 0;
                                            end_flag = 1;
                                        }
                                        break;
                                    default:
                                        dispError(enmErrNum.LFO_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
                                        pitch_mod_tbl[no][0] = 0;
                                        end_flag = 1;
                                        break;
                                }
                            } else {
                                dispError(enmErrNum.LFO_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
                                pitch_mod_tbl[no][0] = 0;
                                end_flag = 1;
                            }
                            break;
                    }
                    ptr = str.skipSpace(buf, ptr);
                    c = (ptr < buf.length()) ? buf.charAt(ptr) : '\0';
                    if (c == ',') {
                        ptr++;
                    }
                }
            }
        }
    }

    /**
     * Getting Note Envelopes
     * Input:
     * <p>
     * Output:
     * none
     */
    private void getArpeggio(LINE[] lptr) {
        int line, i, no, end_flag, offset, num, cnt;
        String buf;
        int ptr;

        cnt = 0;

        for (line = 1; line < lptr.length; line++) {
            // Arpeggio data found?
            if (lptr[line].status == _SET_ARPEGGIO) {
                no = lptr[line].param; // Obtain envelope number
                buf = lptr[line].str;
                ptr = 0;
                ptr++; // Skip the '{'
                if (arpeggio_tbl[no][0] != 0) {
                    dispWarning(enmSys.THIS_NUMBER_IS_ALREADY_USED.ordinal(), lptr[line].filename, line);
                }
                arpeggio_tbl[no][0] = 0;
                offset = 0;
                i = 1;
                end_flag = 0;
                while (end_flag == 0) {
                    ptr = str.skipSpace(buf, ptr);
                    char c = (ptr < buf.length()) ? buf.charAt(ptr) : '\0';
                    switch (c) {
                        case '}':
                            if (arpeggio_tbl[no][0] >= 1) {
                                arpeggio_tbl[no][i] = enmEFTBL.END.v;
                                arpeggio_tbl[no][0]++;
                            } else {
                                dispError(enmErrNum.PARAMETER_IS_LACKING.ordinal(), lptr[line].filename, line);
                                arpeggio_tbl[no][0] = 0;
                            }
                            end_flag = 1;
                            line += offset;
                            break;
                        case '|':
                            arpeggio_tbl[no][i] = enmEFTBL.LOOP.v;
                            arpeggio_tbl[no][0]++;
                            i++;
                            ptr++;
                            break;
                        case '\0':
                            offset++;
                            if (line + offset < lptr.length) {
                                if ((lptr[line + offset].status & _SAME_LINE) == _SAME_LINE) {
                                    buf = lptr[line + offset].str;
                                    ptr = 0;
                                }
                            } else {
                                dispError(enmErrNum.NOTE_ENVELOPE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
                                arpeggio_tbl[no][0] = 0;
                                end_flag = 1;
                            }
                            break;
                        default:
                            num = str.Asc2Int(buf, ptr, /* ref */ cnt);
                            if (cnt != 0) {
                                if (num >= 0) {
                                    arpeggio_tbl[no][i] = num;
                                } else {
                                    arpeggio_tbl[no][i] = (-num) | 0x80;
                                }
                                arpeggio_tbl[no][0]++;
                                ptr += cnt;
                                i++;
                            } else {
                                dispError(enmErrNum.NOTE_ENVELOPE_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
                                arpeggio_tbl[no][0] = 0;
                                end_flag = 1;
                            }
                            break;
                    }
                    ptr = str.skipSpace(buf, ptr);
                    c = (ptr < buf.length()) ? buf.charAt(ptr) : '\0';
                    if (c == ',') {
                        ptr++;
                    }
                }
                // Arpeggio definition but error when using _SAME_LINE
            } else if (lptr[line].status == (_SET_ARPEGGIO | _SAME_LINE)) {
                dispError(enmErrNum.NOTE_ENVELOPE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
                // Include File Processing
            } else if (lptr[line].status == _INCLUDE) {
                getArpeggio(lptr[line].inc_ptr);
            }
        }
    }

//    /**
//     * Acquiring DPCM
//     * Input:
//     * <p>
//     * Output:
//     * none
//     */
//    void getDPCM(LINE*lptr) {
//        int line, i, no, offset, end_flag, num, cnt;
//        char*ptr;
//        FILE * fp;
//        DPCMTBL * tbl;
//
//        cnt = 0;
//
//        for (line = 1; line <= lptr.line; line++) {
//            // DPCM data found?
//            if (lptr[line].status == _SET_DPCM_DATA) {
//                no = lptr[line].param;              // DPCM number acquisition
//                ptr = lptr[line].str;
//                ptr++;                              // Skip the '{'
//                tbl = & dpcm_tbl[no];
//                if (tbl.flag != 0) {
//                    dispWarning(THIS_NUMBER_IS_ALREADY_USED, lptr[line].filename, line);
//                }
//                tbl.flag = 1;                      // When using flags
//                tbl.index = -1;
//                tbl.fname = NULL;
//                tbl.freq = 0;
//                tbl.size = 0;
//                tbl.delta_init = 0;
//                offset = 0;
//                i = 0;
//                end_flag = 0;
//                while (end_flag == 0) {
//                    ptr = skipSpace(ptr);
//                    switch (*ptr) {
//                        // Data End
//                        case '}':
//                            switch (i) {
//                                case 0:
//                                case 1:
//                                    dispError(DPCM_PARAMETER_IS_LACKING, lptr[line].filename, line);
//                                    tbl.flag = 0;
//                                    break;
//                                default:
//                                    line += offset;
//                                    break;
//                            }
//                            end_flag = 1;
//                            break;
//                        // Line breaks
//                        case '\0':
//                            offset++;
//                            if (line + offset <= lptr.line) {
//                                if ((lptr[line + offset].status & _SAME_LINE) == _SAME_LINE) {
//                                    ptr = lptr[line + offset].str;
//                                }
//                            } else {
//                                dispError(DPCM_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
//                                tbl.flag = 0;
//                                end_flag = 1;
//                            }
//                            break;
//                        default:
//                            switch (i) {
//                                // Register the file name
//                                case 0:
//                                    // Is the file name enclosed in "..."?
//                                    if (*ptr == '\"') {
//                                        ptr++;
//                                        //ptr = skipSpace( ptr );
//                                        // "file.dmc" is OK. " file.dmc" is NG.
//                                        tbl.fname = ptr;
//                                        while (*ptr != '\"' && * ptr != '\0') {
//                                            ptr++;
//                                        }
//                                    } else {
//                                        tbl.fname = ptr;
//                                        // The file name up to the space
//                                        // Do not skip '/'';'
//                                        while (*ptr != ' ' && * ptr != '\t' && * ptr != '\0') {
//                                            ptr++;
//                                        }
//                                    }
//                                    *ptr = '\0';
//                                    ptr++;
//                                    // File existence check/size check
//                                    if ((fp = openDmc(tbl.fname)) == NULL) {
//                                        dispError(DPCM_FILE_NOT_FOUND, lptr[line + offset].filename, line);
//                                        tbl.flag = 0;
//                                        end_flag = 1;
//                                    } else {
//                                        fseek(fp, 0, SEEK_END);
//                                        tbl.size = (int) ftell(fp);
//                                        fseek(fp, 0, SEEK_SET);
//                                        fclose(fp);
//                                    }
//                                    i++;
//                                    break;
//                                // Register the playback frequency
//                                case 1:
//                                    num = Asc2Int(ptr, & cnt);
//                                    if (cnt != 0 && (0 <= num && num <= 15)) {
//                                        tbl.freq = num;
//                                    } else {
//                                        dispError(DPCM_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
//                                        tbl.flag = 0;
//                                        end_flag = 1;
//                                    }
//                                    ptr += cnt;
//                                    i++;
//                                    break;
//                                // Register playback size
//                                case 2:
//                                    num = Asc2Int(ptr, & cnt);
//                                    if (cnt != 0 && num == 0) {
//                                        // A value of 0 is the same as omission.
//                                        ptr += cnt;
//                                        i++;
//                                        break;
//                                    }
//                                    if (cnt != 0 && (0 < num && num < 16384)) {
//                                        tbl.size = num;
//                                    } else {
//                                        dispError(DPCM_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
//                                        tbl.flag = 0;
//                                        end_flag = 1;
//                                    }
//                                    ptr += cnt;
//                                    i++;
//                                    break;
//                                // Register the initial value of the delta counter ($4011)
//                                case 3:
//                                    num = Asc2Int(ptr, & cnt);
//                                    if (cnt != 0 && ((0 <= num && num <= 0x7f) || num == 0xff)) {
//                                        tbl.delta_init = num;
//                                    } else {
//                                        dispError(DPCM_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
//                                        tbl.flag = 0;
//                                        end_flag = 1;
//                                    }
//                                    ptr += cnt;
//                                    i++;
//                                    break;
//                                // Register loop information (bits 7 and 6 of $4010)
//                                case 4:
//                                    num = Asc2Int(ptr, & cnt);
//                                    if (cnt != 0 && (0 <= num && num <= 2)) {
//                                        tbl.freq |= (num << 6);
//                                    } else {
//                                        dispError(DPCM_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
//                                        tbl.flag = 0;
//                                        end_flag = 1;
//                                    }
//                                    ptr += cnt;
//                                    i++;
//                                    break;
//                                default:
//                                    dispError(DPCM_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
//                                    tbl.flag = 0;
//                                    end_flag = 1;
//                                    break;
//                            }
//                            break;
//                    }
//                    ptr = skipSpace(ptr);
//                    if (*ptr == ',') {
//                        ptr++;
//                    }
//                }
//                if (tbl.size > (0xff) * 16 + 1) {
//                    dispError(DPCM_FILE_SIZE_OVER, lptr[line + offset].filename, line);
//                    tbl.flag = 0;
//                } else if ((tbl.size % 16) != 1) {
//                    dispWarning(DPCM_FILE_SIZE_ERROR, lptr[line + offset].filename, line);
//                }
//                // DPCM definition but _SAME_LINE gives an error
//            } else if (lptr[line].status == (_SET_DPCM_DATA | _SAME_LINE)) {
//                dispError(DPCM_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
//                // Include File Processing
//            } else if (lptr[line].status == _INCLUDE) {
//                getDPCM(lptr[line].inc_ptr);
//            }
//        }
//    }
//
//    /**
//     * Obtaining XPCM
//     * Input:
//     * <p>
//     * Output:
//     * none
//     */
//    void getXPCM(LINE*lptr) {
//        int line, i, no, offset, end_flag, num, cnt;
//        char*ptr;
//        FILE * fp;
//        DPCMTBL * tbl;
//
//        cnt = 0;
//
//        for (line = 1; line <= lptr.line; line++) {
//            // DPCM data found?
//            if (lptr[line].status == _SET_XPCM_DATA) {
//                no = lptr[line].param;              // DPCM number acquisition
//                ptr = lptr[line].str;
//                ptr++;                              // Skip the '{'
//                tbl = & xpcm_tbl[no];
//                if (tbl.flag != 0) {
//                    dispWarning(THIS_NUMBER_IS_ALREADY_USED, lptr[line].filename, line);
//                }
//                tbl.flag = 1;                      // When using flags
//                tbl.index = -1;
//                tbl.fname = NULL;
//                tbl.freq = 0;
//                tbl.size = 0;
//                tbl.delta_init = 0;
//                offset = 0;
//                i = 0;
//                end_flag = 0;
//                while (end_flag == 0) {
//                    ptr = skipSpace(ptr);
//                    switch (*ptr) {
//                        // Data End
//                        case '}':
//                            switch (i) {
//                                case 0:
//                                    dispError(XPCM_PARAMETER_IS_LACKING, lptr[line].filename, line);
//                                    tbl.flag = 0;
//                                    break;
//                                default:
//                                    line += offset;
//                                    break;
//                            }
//                            end_flag = 1;
//                            break;
//                        // Line breaks
//                        case '\0':
//                            offset++;
//                            if (line + offset <= lptr.line) {
//                                if ((lptr[line + offset].status & _SAME_LINE) == _SAME_LINE) {
//                                    ptr = lptr[line + offset].str;
//                                }
//                            } else {
//                                dispError(XPCM_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
//                                tbl.flag = 0;
//                                end_flag = 1;
//                            }
//                            break;
//                        default:
//                            switch (i) {
//                                // Register the file name
//                                case 0:
//                                    // Is the file name enclosed in "..."?
//                                    if (*ptr == '\"') {
//                                    ptr++;
//                                    //ptr = skipSpace( ptr );
//                                    // "file.dmc" is OK. " file.dmc" is NG.
//                                    tbl.fname = ptr;
//                                    while (*ptr != '\"' && * ptr != '\0') {
//                                        ptr++;
//                                    }
//                                } else {
//                                    tbl.fname = ptr;
//                                    // The file name up to the space
//                                    // Do not skip '/'';'
//                                    while (*ptr != ' ' && * ptr != '\t' && * ptr != '\0')
//                                    {
//                                        ptr++;
//                                    }
//                                }
//                                        *ptr = '\0';
//                                ptr++;
//                                // File existence check/size check
//                                if ((fp = openDmc(tbl.fname)) == NULL) {
//                                    dispError(XPCM_FILE_NOT_FOUND, lptr[line + offset].filename, line);
//                                    tbl.flag = 0;
//                                    end_flag = 1;
//                                } else {
//                                    fseek(fp, 0, SEEK_END);
//                                    tbl.size = (int) ftell(fp);
//                                    fseek(fp, 0, SEEK_SET);
//                                    fclose(fp);
//                                }
//                                i++;
//                                break;
//                                // Register the playback frequency
//                                case 1:
//                                    num = Asc2Int(ptr, & cnt);
//                                    if (cnt != 0 && (0 <= num && num <= 15)) {
//                                        tbl.freq = num;
//                                    } else {
//                                        dispError(XPCM_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
//                                        tbl.flag = 0;
//                                        end_flag = 1;
//                                    }
//                                    ptr += cnt;
//                                    i++;
//                                    break;
//                                // Register playback size
//                                case 2:
//                                    num = Asc2Int(ptr, & cnt);
//                                    if (cnt != 0 && num == 0) {
//                                        // A value of 0 is the same as omission.
//                                        ptr += cnt;
//                                        i++;
//                                        break;
//                                    }
//                                    if (cnt != 0 && (0 < num && num < 16384)) {
//                                        tbl.size = num;
//                                    } else {
//                                        dispError(XPCM_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
//                                        tbl.flag = 0;
//                                        end_flag = 1;
//                                    }
//                                    ptr += cnt;
//                                    i++;
//                                    break;
//                                default:
//                                    dispError(XPCM_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
//                                    tbl.flag = 0;
//                                    end_flag = 1;
//                                    break;
//                            }
//                            break;
//                    }
//                    ptr = skipSpace(ptr);
//                    if (*ptr == ',') {
//                        ptr++;
//                    }
//                }
//                if (tbl.size > 0xffff) {
//                    dispError(XPCM_FILE_SIZE_OVER, lptr[line + offset].filename, line);
//                    tbl.flag = 0;
//                }
//                // DPCM definition but _SAME_LINE gives an error
//            } else if (lptr[line].status == (_SET_DPCM_DATA | _SAME_LINE)) {
//                dispError(XPCM_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
//                // Include File Processing
//            } else if (lptr[line].status == _INCLUDE) {
//                getXPCM(lptr[line].inc_ptr);
//            }
//        }
//    }
//
//    /**
//     * Acquiring FDS FM tones
//     * Input:
//     * <p>
//     * Output:
//     * none
//     */
//    void getFMTone(LINE*lptr) {
//        int line, i, no, end_flag, offset, num, cnt;
//        char*ptr;
//
//        cnt = 0;
//
//        for (line = 1; line <= lptr.line; line++) {
//            /* Tone data found? */
//            if (lptr[line].status == _SET_FM_TONE) {
//                no = lptr[line].param;              // Get tone number
//                ptr = lptr[line].str;
//                ptr++;                              // Skip the '{'
//                if (fm_tone_tbl[no][0] != 0) {
//                    dispWarning(THIS_NUMBER_IS_ALREADY_USED, lptr[line].filename, line);
//                }
//                fm_tone_tbl[no][0] = 0;
//                offset = 0;
//                i = 1;
//                end_flag = 0;
//                while (end_flag == 0) {
//                    ptr = skipSpace(ptr);
//                    switch (*ptr) {
//                        case '}':
//                            if (fm_tone_tbl[no][0] == 64) {
//                                // OK.
//                            } else {
//                                dispError(PARAMETER_IS_LACKING, lptr[line].filename, line);
//                                fm_tone_tbl[no][0] = 0;
//                            }
//                            end_flag = 1;
//                            line += offset;
//                            break;
//                        case '\0':
//                            offset++;
//                            if (line + offset <= lptr.line) {
//                                if ((lptr[line + offset].status & _SAME_LINE) == _SAME_LINE) {
//                                    ptr = lptr[line + offset].str;
//                                }
//                            } else {
//                                dispError(FM_TONE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line + offset);
//                                fm_tone_tbl[no][0] = 0;
//                                line += offset;
//                                end_flag = 1;
//                            }
//                            break;
//                        default:
//                            num = Asc2Int(ptr, & cnt);
//                            if (cnt != 0 && (0 <= num && num <= 0x3f)) {
//                                fm_tone_tbl[no][i] = num;
//                                fm_tone_tbl[no][0]++;
//                                ptr += cnt;
//                                i++;
//                                if (i > 65) {
//                                    dispError(ABNORMAL_PARAMETERS_OF_FM_TONE, lptr[line + offset].filename, line + offset);
//                                    fm_tone_tbl[no][0] = 0;
//                                    line += offset;
//                                    end_flag = 1;
//                                }
//                            } else {
//                                dispError(FM_TONE_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line + offset);
//                                fm_tone_tbl[no][0] = 0;
//                                line += offset;
//                                end_flag = 1;
//                            }
//                            break;
//                    }
//                    ptr = skipSpace(ptr);
//                    if (*ptr == ',') {
//                        ptr++;
//                    }
//                }
//                // It's a tone definition, but an error occurs when using _SAME_LINE.
//            } else if (lptr[line].status == (_SET_FM_TONE | _SAME_LINE)) {
//                dispError(FM_TONE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
//                // Include File Processing
//            } else if (lptr[line].status == _INCLUDE) {
//                getFMTone(lptr[line].inc_ptr);
//            }
//        }
//    }
//
//    /**
//     * Get WaveTable sounds
//     * Input:
//     * <p>
//     * Output:
//     * none
//     */
//    void getWTBTone(LINE*lptr) {
//        int line, i, no, end_flag, offset, num, cnt;
//        char*ptr;
//
//        cnt = 0;
//
//        for (line = 1; line <= lptr.line; line++) {
//            /* Tone data found? */
//            if (lptr[line].status == _SET_WTB_TONE) {
//                no = lptr[line].param;              // Get tone number
//                ptr = lptr[line].str;
//                ptr++;                              // Skip the '{'
//                wtb_tone_tbl[no][0] = 0;
//                offset = 0;
//                i = 1;
//                end_flag = 0;
//                while (end_flag == 0) {
//                    ptr = skipSpace(ptr);
//                    switch (*ptr) {
//                        case '}':
//                            end_flag = 1;
//                            line += offset;
//                            break;
//                        case '\0':
//                            offset++;
//                            if (line + offset <= lptr.line) {
//                                if ((lptr[line + offset].status & _SAME_LINE) == _SAME_LINE) {
//                                    ptr = lptr[line + offset].str;
//                                }
//                            } else {
//                                dispError(WTB_TONE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line + offset);
//                                wtb_tone_tbl[no][0] = 0;
//                                line += offset;
//                                end_flag = 1;
//                            }
//                            break;
//                        default:
//                            num = Asc2Int(ptr, & cnt);
//                            if (cnt != 0 && (0 <= num && num <= 0x1f)) {
//                                wtb_tone_tbl[no][i] = num;
//                                wtb_tone_tbl[no][0]++;
//                                ptr += cnt;
//                                i++;
//                                if (i > 33) {
//                                    dispError(ABNORMAL_PARAMETERS_OF_WTB_TONE, lptr[line + offset].filename, line + offset);
//                                    wtb_tone_tbl[no][0] = 0;
//                                    line += offset;
//                                    end_flag = 1;
//                                }
//                            } else {
//                                dispError(WTB_TONE_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line + offset);
//                                wtb_tone_tbl[no][0] = 0;
//                                line += offset;
//                                end_flag = 1;
//                            }
//                            break;
//                    }
//                    ptr = skipSpace(ptr);
//                    if (*ptr == ',')
//                    {
//                        ptr++;
//                    }
//                }
//                if (i != 33) {
//                    if (!error_flag) {
//                        dispError(ABNORMAL_PARAMETERS_OF_WTB_TONE, lptr[line].filename, line);
//                        wtb_tone_tbl[no][0] = 0;
//                    }
//                }
//
//                // It's a tone definition, but an error occurs when using _SAME_LINE.
//            } else if (lptr[line].status == (_SET_WTB_TONE | _SAME_LINE)) {
//                dispError(WTB_TONE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
//                // Include File Processing
//            } else if (lptr[line].status == _INCLUDE) {
//                getWTBTone(lptr[line].inc_ptr);
//            }
//        }
//    }

    /**
     * Getting ToneTable
     * Input:
     * <p>
     * Output:
     * none
     */
    void getToneTable(LINE[] lptr) {
        int line, i, no, end_flag, offset, num, cnt;
        String buf;
        int ptr;

        cnt = 0;

        for (line = 1; line < lptr.length; line++) {
            // Tone data found?
            if (lptr[line].status == _SET_TONETBL) {
                no = lptr[line].param;              // Get tone number
                buf = lptr[line].str;
                ptr = 0;
                ptr++;                              // Skip the '{'
                tonetbl_tbl[no][0] = 0;
                offset = 0;
                i = 1;
                end_flag = 0;
                while (end_flag == 0) {
                    ptr = str.skipSpace(buf, ptr);
                    char c = (ptr < buf.length()) ? buf.charAt(ptr) : '\0';

                    switch (c) {
                        case '}':
                            end_flag = 1;
                            line += offset;
                            break;
                        case '\0':
                            offset++;
                            if (line + offset <= lptr.length) // .line)
                            {
                                if ((lptr[line + offset].status & _SAME_LINE) == _SAME_LINE) {
                                    buf = lptr[line + offset].str;
                                    ptr = 0;
                                }
                            } else {
                                dispError(enmErrNum.TONETBL_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line + offset);
                                tonetbl_tbl[no][0] = 0;
                                line += offset;
                                end_flag = 1;
                            }
                            break;
                        default:
                            num = str.Asc2Int(buf, ptr, /* ref */ cnt);
                            if (cnt != 0) {
                                tonetbl_tbl[no][i] = num;
                                tonetbl_tbl[no][0]++;
                                ptr += cnt;
                                i++;
                                if (i > 1024 + 1) {
                                    dispError(enmErrNum.ABNORMAL_PARAMETERS_OF_TONETBL.ordinal(), lptr[line + offset].filename, line + offset);
                                    tonetbl_tbl[no][0] = 0;
                                    line += offset;
                                    end_flag = 1;
                                }
                            } else {
                                dispError(enmErrNum.TONETBL_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line + offset);
                                tonetbl_tbl[no][0] = 0;
                                line += offset;
                                end_flag = 1;
                            }
                            break;
                    }

                    ptr = str.skipSpace(buf, ptr);
                    c = (ptr < buf.length()) ? buf.charAt(ptr) : '\0';

                    if (c == ',') {
                        ptr++;
                    }
                }
                if ((i % 9) != 1) {
                    if (error_flag == 0) {
                        dispError(enmErrNum.ABNORMAL_PARAMETERS_OF_TONETBL.ordinal(), lptr[line].filename, line);
                        tonetbl_tbl[no][0] = 0;
                    }
                }


                // It's a tone definition, but an error occurs when using _SAME_LINE.
            } else if (lptr[line].status == (_SET_TONETBL | _SAME_LINE)) {
                dispError(enmErrNum.TONETBL_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
                // Include File Processing
            } else if (lptr[line].status == _INCLUDE) {
                getToneTable(lptr[line].inc_ptr);
            }
        }
    }

    /**
     * Obtaining OPL3OP
     * Input:
     * <p>
     * Output:
     * none
     */
    void getOPL3tbl(LINE[] lptr) {
        int line, i, no, end_flag, offset, num, cnt;
        String buf;
        int ptr;

        cnt = 0;

        int op_flag = 0;

        for (line = 1; line < lptr.length; line++) {
            // It's a tone definition, but an error occurs when using _SAME_LINE.
            if (lptr[line].status == (_SET_FMOP | _SAME_LINE) ||
                    lptr[line].status == (_SET_FMOP_FOUR | _SAME_LINE)) {
                dispError(enmErrNum.FM_TONE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
                continue;
            }

            // Include File Processing
            if (lptr[line].status == _INCLUDE) {
                getOPL3tbl(lptr[line].inc_ptr);
                continue;
            }

            // Tone data found?
            if (lptr[line].status != _SET_FMOP &&
                    lptr[line].status != _SET_FMOP_FOUR)
                continue;

            // 4op mode
            if (lptr[line].status == _SET_FMOP_FOUR)
                op_flag = 1;
            else
                op_flag = 0;

            no = lptr[line].param; // Get tone number
            buf = lptr[line].str;
            ptr = 0;
            ptr++; // Skip the '{'

            opl3op_flag[no] = op_flag; // op_flag
            opl3op_tbl[no][0] = 0;
            offset = 0;
            i = 1;
            end_flag = 0;
            while (end_flag == 0) {
                ptr = str.skipSpace(buf, ptr);
                char c = (ptr < buf.length()) ? buf.charAt(ptr) : '\0';

                switch (c) {
                    case '}':
                        end_flag = 1;
                        line += offset;
                        break;
                    case '\0':
                        offset++;
                        if (line + offset < lptr.length) {
                            if ((lptr[line + offset].status & _SAME_LINE) == _SAME_LINE) {
                                buf = lptr[line + offset].str;
                                ptr = 0;
                            }
                        } else {
                            dispError(enmErrNum.FM_TONE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line + offset);
                            opl3op_tbl[no][0] = 0;
                            line += offset;
                            end_flag = 1;
                        }
                        break;
                    default:
                        num = str.Asc2Int(buf, ptr, /* ref */ cnt);
                        if (cnt != 0) {
                            opl3op_tbl[no][i] = num;
                            opl3op_tbl[no][0]++;
                            ptr += cnt;
                            i++;
                            if (i > 1024 + 1) {
                                dispError(enmErrNum.ABNORMAL_PARAMETERS_OF_FM_TONE.ordinal(), lptr[line + offset].filename, line + offset);
                                opl3op_tbl[no][0] = 0;
                                line += offset;
                                end_flag = 1;
                            }
                        } else {
                            dispError(enmErrNum.FM_TONE_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line + offset);
                            opl3op_tbl[no][0] = 0;
                            line += offset;
                            end_flag = 1;
                        }
                        break;
                }

                ptr = str.skipSpace(buf, ptr);
                c = (ptr < buf.length()) ? buf.charAt(ptr) : '\0';

                if (c == ',') {
                    ptr++;
                }
            }

            if ((i % 12) != 6) {
                if (error_flag == 0) {
                    dispError(enmErrNum.ABNORMAL_PARAMETERS_OF_FM_TONE.ordinal(), lptr[line].filename, line);
                    opl3op_tbl[no][0] = 0;
                }
            }
        }
    }

//    /**
//     * Acquisition of VRC7 tone
//     * Input:
//     * <p>
//     * Output:
//     * none
//     */
//    void getVRC7Tone(LINE*lptr) {
//        int line, i, no, end_flag, offset, num, cnt;
//        char*ptr;
//
//        cnt = 0;
//
//        for (line = 1; line <= lptr.line; line++) {
//            /* Tone data found? */
//            if (lptr[line].status == _SET_VRC7_TONE) {
//                no = lptr[line].param;              // Get tone number
//                ptr = lptr[line].str;
//                ptr++;                              // Skip the '{'
//                if (vrc7_tone_tbl[no][0] != 0) {
//                    dispWarning(THIS_NUMBER_IS_ALREADY_USED, lptr[line].filename, line);
//                }
//                vrc7_tone_tbl[no][0] = 0;
//                offset = 0;
//                i = 1;
//                end_flag = 0;
//                while (end_flag == 0) {
//                    ptr = skipSpace(ptr);
//                    switch (*ptr){
//                        case '}':
//                            if (vrc7_tone_tbl[no][0] == 8) {
//                                // OK.
//                            } else {
//                                dispError(PARAMETER_IS_LACKING, lptr[line].filename, line);
//                                vrc7_tone_tbl[no][0] = 0;
//                            }
//                            end_flag = 1;
//                            line += offset;
//                            break;
//                        case '\0':
//                            offset++;
//                            if (line + offset <= lptr.line) {
//                                if ((lptr[line + offset].status & _SAME_LINE) == _SAME_LINE) {
//                                    ptr = lptr[line + offset].str;
//                                }
//                            } else {
//                                dispError(FM_TONE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line + offset);
//                                vrc7_tone_tbl[no][0] = 0;
//                                line += offset;
//                                end_flag = 1;
//                            }
//                            break;
//                        default:
//                            num = Asc2Int(ptr, & cnt);
//                            if (cnt != 0 && (0 <= num && num <= 0xff)) {
//                                vrc7_tone_tbl[no][i] = num;
//                                vrc7_tone_tbl[no][0]++;
//                                ptr += cnt;
//                                i++;
//                                if (i > 9) {
//                                    dispError(ABNORMAL_PARAMETERS_OF_FM_TONE, lptr[line + offset].filename, line + offset);
//                                    vrc7_tone_tbl[no][0] = 0;
//                                    line += offset;
//                                    end_flag = 1;
//                                }
//                            } else {
//                                dispError(FM_TONE_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line + offset);
//                                vrc7_tone_tbl[no][0] = 0;
//                                line += offset;
//                                end_flag = 1;
//                            }
//                            break;
//                    }
//                    ptr = skipSpace(ptr);
//                    if (*ptr == ','){
//                        ptr++;
//                    }
//                }
//                if (i != 9) {
//                    if (!error_flag) {
//                        dispError(ABNORMAL_PARAMETERS_OF_FM_TONE, lptr[line].filename, line);
//                        vrc7_tone_tbl[no][0] = 0;
//                    }
//                }
//
//                // It's a tone definition, but an error occurs when using _SAME_LINE.
//            } else if (lptr[line].status == (_SET_VRC7_TONE | _SAME_LINE)) {
//                dispError(FM_TONE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
//                // Include File Processing
//            } else if (lptr[line].status == _INCLUDE) {
//                getVRC7Tone(lptr[line].inc_ptr);
//            }
//        }
//    }

//    /**
//     * Acquiring namco106 tones
//     * Input:
//     * <p>
//     * Output:
//     * none
//     */
//    void getN106Tone(LINE*lptr) {
//        int line, i, no, end_flag, offset, num, cnt;
//        char*ptr;
//        //		 16 14 12 10	8	6	4	2
//        int n106_tone_max[] = {4, 4, 5, 6, 8, 10, 16, 32};
//        int n106_tone_num;
//
//        cnt = 0;
//        for (line = 1; line <= lptr.line; line++) {
//            // Tone data found?
//            if (lptr[line].status == _SET_N106_TONE) {
//                no = lptr[line].param;              // Get tone number
//                ptr = lptr[line].str;
//                ptr++;                              // Skip the '{'
//                if (n106_tone_tbl[no][0] != 0) {
//                    dispWarning(THIS_NUMBER_IS_ALREADY_USED, lptr[line].filename, line);
//                }
//                n106_tone_tbl[no][0] = 0;
//                offset = 0;
//                i = 1;
//                end_flag = 0;
//                while (end_flag == 0) {
//                    ptr = skipSpace(ptr);
//                    switch (*ptr){
//                        case '}':
//                            // The number of elements is checked after exiting while
//                            end_flag = 1;
//                            line += offset;
//                            break;
//                        case '\0':
//                            offset++;
//                            if (line + offset <= lptr.line) {
//                                if ((lptr[line + offset].status & _SAME_LINE) == _SAME_LINE) {
//                                    ptr = lptr[line + offset].str;
//                                }
//                            } else {
//                                dispError(N106_TONE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line + offset);
//                                n106_tone_tbl[no][0] = 0;
//                                line += offset;
//                                end_flag = 1;
//                            }
//                            break;
//                        default:
//                            num = Asc2Int(ptr, & cnt);
//                            if (i == 1) { // Registration buffer (0 to 5)
//                                if (cnt != 0 && (0 <= num && num <= 32)) {
//                                    n106_tone_tbl[no][1] = num;
//                                    n106_tone_tbl[no][0]++;
//                                    ptr += cnt;
//                                    i++;
//                                } else {
//                                    dispError(N106_TONE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line + offset);
//                                    n106_tone_tbl[no][0] = 0;
//                                    line += offset;
//                                    end_flag = 1;
//                                }
//                            } else {
//                                if (cnt != 0 && (0 <= num && num <= 15)) {
//                                    n106_tone_tbl[no][i] = num;
//                                    n106_tone_tbl[no][0]++;
//                                    ptr += cnt;
//                                    i++;
//                                    if (i > 2 + 32) {
//                                        dispError(ABNORMAL_PARAMETERS_OF_N106_TONE, lptr[line + offset].filename, line + offset);
//                                        n106_tone_tbl[no][0] = 0;
//                                        line += offset;
//                                        end_flag = 1;
//                                    }
//                                } else {
//                                    dispError(N106_TONE_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line + offset);
//                                    n106_tone_tbl[no][0] = 0;
//                                    line += offset;
//                                    end_flag = 1;
//                                }
//                            }
//                            break;
//                    }
//                    ptr = skipSpace(ptr);
//                    if (*ptr == ','){
//                        ptr++;
//                    }
//                }
//                switch (n106_tone_tbl[no][0]) {
//                    case 16 * 2 + 1:
//                        n106_tone_num = 0;
//                        break;
//                    case 14 * 2 + 1:
//                        n106_tone_num = 1;
//                        break;
//                    case 12 * 2 + 1:
//                        n106_tone_num = 2;
//                        break;
//                    case 10 * 2 + 1:
//                        n106_tone_num = 3;
//                        break;
//                    case 8 * 2 + 1:
//                        n106_tone_num = 4;
//                        break;
//                    case 6 * 2 + 1:
//                        n106_tone_num = 5;
//                        break;
//                    case 4 * 2 + 1:
//                        n106_tone_num = 6;
//                        break;
//                    case 2 * 2 + 1:
//                        n106_tone_num = 7;
//                        break;
//                    default:
//                        n106_tone_num = -1;
//                        break;
//                }
//                if (n106_tone_num == -1) {
//                    dispError(ABNORMAL_PARAMETERS_OF_N106_TONE, lptr[line].filename, line);
//                    n106_tone_tbl[no][0] = 0;
//                }
//                if (n106_tone_tbl[no][1] >= n106_tone_max[n106_tone_num]) {
//                    dispError(N106_TONE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
//                    n106_tone_tbl[no][0] = 0;
//                }
//                // It's a tone definition, but an error occurs when using _SAME_LINE.
//            } else if (lptr[line].status == (_SET_N106_TONE | _SAME_LINE)) {
//                dispError(N106_TONE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
//                // Include File Processing
//            } else if (lptr[line].status == _INCLUDE) {
//                getN106Tone(lptr[line].inc_ptr);
//            }
//        }
//    }

    /**
     * Get Hardware Effects
     * Input:
     * <p>
     * Output:
     * none
     */
    private void getHardEffect(LINE[] lptr) {
        int line, i, no, end_flag, offset, num, cnt;
        String buf;
        int ptr;

        cnt = 0;

        for (line = 1; line < lptr.length; line++) {
            // Tone data found?
            if (lptr[line].status == _SET_HARD_EFFECT) {
                no = lptr[line].param; // Get effect number
                buf = lptr[line].str;
                ptr = 0;
                ptr++; // Skip the '{'
                if (hard_effect_tbl[no][0] != 0) {
                    dispWarning(enmSys.THIS_NUMBER_IS_ALREADY_USED.ordinal(), lptr[line].filename, line);
                }
                hard_effect_tbl[no][0] = 0;
                offset = 0;
                i = 1;
                end_flag = 0;
                while (end_flag == 0) {
                    ptr = str.skipSpace(buf, ptr);
                    char c = (ptr < buf.length()) ? buf.charAt(ptr) : '\0';
                    switch (c) {
                        case '}':
                            if (hard_effect_tbl[no][0] == 4) {
                                // OK.
                            } else {
                                dispError(enmErrNum.PARAMETER_IS_LACKING.ordinal(), lptr[line].filename, line);
                                hard_effect_tbl[no][0] = 0;
                            }
                            end_flag = 1;
                            line += offset;
                            break;
                        case '\0':
                            offset++;
                            if (line + offset < lptr.length) {
                                if ((lptr[line + offset].status & _SAME_LINE) == _SAME_LINE) {
                                    buf = lptr[line + offset].str;
                                    ptr = 0;
                                }
                            } else {
                                dispError(enmErrNum.LFO_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
                                hard_effect_tbl[no][0] = 0;
                                end_flag = 1;
                            }
                            break;
                        default:
                            num = str.Asc2Int(buf, ptr, /* ref */ cnt);
                            if (cnt != 0) {
                                switch (i) {
                                    case 1:
                                        if (0 <= num && num <= 255) {
                                            hard_effect_tbl[no][i] = num;
                                            hard_effect_tbl[no][0]++;
                                            ptr += cnt;
                                            i++;
                                        } else {
                                            dispError(enmErrNum.LFO_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
                                            hard_effect_tbl[no][0] = 0;
                                            end_flag = 1;
                                        }
                                        break;
                                    case 2:
                                        if (0 <= num && num <= 4095) {
                                            hard_effect_tbl[no][i] = num;
                                            hard_effect_tbl[no][0]++;
                                            ptr += cnt;
                                            i++;
                                        } else {
                                            dispError(enmErrNum.LFO_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
                                            hard_effect_tbl[no][0] = 0;
                                            end_flag = 1;
                                        }
                                        break;
                                    case 3:
                                        if (0 <= num && num <= 255) {
                                            hard_effect_tbl[no][i] = num;
                                            hard_effect_tbl[no][0]++;
                                            ptr += cnt;
                                            i++;
                                        } else {
                                            dispError(enmErrNum.LFO_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
                                            hard_effect_tbl[no][0] = 0;
                                            end_flag = 1;
                                        }
                                        break;
                                    case 4:
                                        if (0 <= num && num <= 7) {
                                            hard_effect_tbl[no][i] = num;
                                            hard_effect_tbl[no][0]++;
                                            ptr += cnt;
                                            i++;
                                        } else {
                                            dispError(enmErrNum.LFO_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
                                            hard_effect_tbl[no][0] = 0;
                                            end_flag = 1;
                                        }
                                        break;
                                    default:
                                        dispError(enmErrNum.LFO_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
                                        hard_effect_tbl[no][0] = 0;
                                        end_flag = 1;
                                        break;
                                }
                            } else {
                                dispError(enmErrNum.LFO_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line);
                                hard_effect_tbl[no][0] = 0;
                                end_flag = 1;
                            }
                            break;
                    }
                    ptr = str.skipSpace(buf, ptr);
                    c = (ptr < buf.length()) ? buf.charAt(ptr) : '\0';
                    if (c == ',') {
                        ptr++;
                    }
                }
                // It's a tone definition, but an error occurs when using _SAME_LINE.
            } else if (lptr[line].status == (_SET_HARD_EFFECT | _SAME_LINE)) {
                dispError(enmErrNum.LFO_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
                // Include File Processing
            } else if (lptr[line].status == _INCLUDE) {
                getHardEffect(lptr[line].inc_ptr);
            }
        }
    }

    /**
     * Getting effect waveforms
     * Input:
     * <p>
     * Output:
     * none
     */
    private void getEffectWave(LINE[] lptr) {
        int line, i, no, end_flag, offset, num, cnt;
        String buf;
        int ptr;

        cnt = 0;

        for (line = 1; line < lptr.length; line++) {
            // Tone data found?
            if (lptr[line].status == _SET_EFFECT_WAVE) {
                no = lptr[line].param; // Get waveform number
                buf = lptr[line].str;
                ptr = 0;
                ptr++; // Skip the '{'
                if (effect_wave_tbl[no][0] != 0) {
                    dispWarning(enmSys.THIS_NUMBER_IS_ALREADY_USED.ordinal(), lptr[line].filename, line);
                }
                effect_wave_tbl[no][0] = 0;
                offset = 0;
                i = 1;
                end_flag = 0;
                while (end_flag == 0) {
                    ptr = str.skipSpace(buf, ptr);
                    char c = (ptr < buf.length()) ? buf.charAt(ptr) : '\0';
                    switch (c) {
                        case '}':
                            if (effect_wave_tbl[no][0] == 32) {
                                // OK.
                            } else {
                                dispError(enmErrNum.PARAMETER_IS_LACKING.ordinal(), lptr[line].filename, line);
                                effect_wave_tbl[no][0] = 0;
                            }
                            end_flag = 1;
                            line += offset;
                            break;
                        case '\0':
                            offset++;
                            if (line + offset < lptr.length) {
                                if ((lptr[line + offset].status & _SAME_LINE) == _SAME_LINE) {
                                    buf = lptr[line + offset].str;
                                    ptr = 0;
                                }
                            } else {
                                dispError(enmErrNum.EFFECT_WAVE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line + offset);
                                effect_wave_tbl[no][0] = 0;
                                line += offset;
                                end_flag = 1;
                            }
                            break;
                        default:
                            num = str.Asc2Int(buf, ptr, /* ref */ cnt);
                            if (cnt != 0 && (0 <= num && num <= 7)) {
                                effect_wave_tbl[no][i] = num;
                                effect_wave_tbl[no][0]++;
                                ptr += cnt;
                                i++;
                                if (i > 33) {
                                    dispError(enmErrNum.EFFECT_WAVE_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line + offset);
                                    effect_wave_tbl[no][0] = 0;
                                    line += offset;
                                    end_flag = 1;
                                }
                            } else {
                                dispError(enmErrNum.EFFECT_WAVE_DEFINITION_IS_WRONG.ordinal(), lptr[line + offset].filename, line + offset);
                                effect_wave_tbl[no][0] = 0;
                                line += offset;
                                end_flag = 1;
                            }
                            break;
                    }
                    ptr = str.skipSpace(buf, ptr);
                    c = (ptr < buf.length()) ? buf.charAt(ptr) : '\0';
                    if (c == ',') {
                        ptr++;
                    }
                }
                // It's a tone definition, but an error occurs when using _SAME_LINE.
            } else if (lptr[line].status == (_SET_EFFECT_WAVE | _SAME_LINE)) {
                dispError(enmErrNum.EFFECT_WAVE_DEFINITION_IS_WRONG.ordinal(), lptr[line].filename, line);
                // Include File Processing
            } else if (lptr[line].status == _INCLUDE) {
                getEffectWave(lptr[line].inc_ptr);
            }
        }
    }

//    /**
//     * Remove duplicate DPCM data
//     * Input:
//     * none
//     * Output:
//     * none
//     */
//    void sortDPCM(DPCMTBL dpcm_tbl[_DPCM_MAX]) {
//        int i, j;
//
//        for (i = 0; i < _DPCM_MAX; i++) {
//            if (dpcm_tbl[i].flag == 0 || dpcm_tbl[i].index != -1) continue;
//            for (j = 0; j < _DPCM_MAX; j++) {
//                if (i == j) continue;
//                if (dpcm_tbl[j].flag == 0) continue;
//                // Are the file names the same?
//                if (strcmp(dpcm_tbl[i].fname, dpcm_tbl[j].fname) == 0
//                        && dpcm_tbl[i].size >= dpcm_tbl[j].size) {
//                    dpcm_tbl[j].index = i;
//                }
//            }
//        }
//    }

//    /**
//     * DPCM size correction (corrected to 16 byte boundary)
//     * Input:
//     * <p>
//     * Output:
//     * int: DPCM size
//     */
//    int checkDPCMSize(DPCMTBL dpcm_tbl[_DPCM_MAX]) {
//        int i;
//        int adr = 0;
//        int size = 0;
//        int bank = 0; // Increments by 0x4000
//        for (i = 0; i < _DPCM_MAX; i++) {
//            if (dpcm_tbl[i].flag != 0) {
//                    /*
//                         $4013 * 16 + 1 = size
//                         $4013 = (size - 1) / 16
//
//                         Adjust so that newsize % 16 == 1 holds.
//                         size%16	(size-1)%16	diff(floor)	diff(ceil)
//                         1		0		0		0
//                         2		1		-1		+15
//                         3		2		-2		+14
//                         4		3		-3		+13
//                         15		14		-14		+2
//                         0		15		-15		+1
//                    */
//                //printf("%s size $%x\n", dpcm_tbl[i].fname, dpcm_tbl[i].size);
//                if ((dpcm_tbl[i].size % 16) != 1) {
//                    int diff;
//                    diff = (16 - ((dpcm_tbl[i].size - 1) % 16)) % 16; //ceil
//                    //diff =		- ((dpcm_tbl[i].size - 1) % 16); //floor
//                    dpcm_tbl[i].size += diff;
//                }
//                //printf("%s fixed size $%x\n", dpcm_tbl[i].fname, dpcm_tbl[i].size);
//                // Set the start address
//                if (dpcm_tbl[i].index == -1) {
//                    if (((adr % 0x4000 + dpcm_tbl[i].size) > 0x4000) || (adr % 0x4000 == 0 && adr != 0)) {
//                        // If it crosses a 16KB boundary, or if the previous address rounded up to a new 16KB area
//                        adr += (0x4000 - (adr % 0x4000)) % 0x4000;
//                        bank++;
//                        dpcm_bankswitch = 1;
//                    }
//                    //printf("%s bank %d a %x s %x\n", dpcm_tbl[i].fname, bank, adr, size);
//
//                    dpcm_tbl[i].start_adr = adr;
//                    dpcm_tbl[i].bank_ofs = bank;
//                    adr += dpcm_tbl[i].size;
//                    size = adr;
//                    // Round up so that adr % 64 == 0 holds
//                    adr += (64 - (adr % 64)) % 64;
//                }
//            }
//        }
//        return size;
//    }

//    /**
//     * DPCM size correction (corrected to 16 byte boundary)
//     * Input:
//     * <p>
//     * Output:
//     * int: DPCM size
//     */
//    int checkXPCMSize(DPCMTBL xpcm_tbl[_DPCM_MAX]) {
//        int size = 0;
//
//        for (int i = 0; i < _DPCM_MAX; i++) {
//            if (xpcm_tbl[i].flag != 0)
//                size += xpcm_tbl[i].size;
//        }
//        return size;
//    }

//    /**
//     * DPCM data loading
//     * Input:
//     * <p>
//     * Output:
//     */
//    void readDPCM(DPCMTBL dpcm_tbl[_DPCM_MAX]) {
//        FILE * fp;
//
//        for (int i = 0; i < dpcm_size; i++) {
//            dpcm_data[i] = 0xaa;
//        }
//
//        for (int i = 0; i < _DPCM_MAX; i++) {
//            if (dpcm_tbl[i].flag != 0 && dpcm_tbl[i].index == -1) {
//                fp = openDmc(dpcm_tbl[i].fname);
//                if (fp == NULL) {
//                    //				disperror( DPCM_FILE_NOT_FOUND, 0 );
//                } else {
//                    fread( & dpcm_data[dpcm_tbl[i].start_adr], 1, dpcm_tbl[i].size, fp);
//                    fclose(fp);
//                }
//            }
//        }
//#if 0
//        for (int i = 0; i < _DPCM_TOTAL_SIZE; i++) {
//            if ((i & 0x0f) != 0x0f) {
//                printf("%02x,", dpcm_data[i]);
//            } else {
//                printf("%02x\n", dpcm_data[i]);
//            }
//        }
//#endif
//    }

    /**
     * Tone/Envelope Loop Check
     * Input:
     * <p>
     * Output:
     * int	: The largest tone number
     */
    private int checkLoop(int[][] ptr, int max) { //[128][1024]
        int j, lp_flag, ret;

        ret = 0;

        for (int i = 0; i < max; i++) {
            if (ptr[i][0] != 0) {
                lp_flag = 0;
                for (j = 1; j <= ptr[i][0]; j++) {
                    if (ptr[i][j] == enmEFTBL.LOOP.v) lp_flag = 1;
                }
                if (lp_flag == 0) {
                    j = ptr[i][0];
                    ptr[i][j + 1] = ptr[i][j];
                    ptr[i][j] = ptr[i][j - 1];
                    ptr[i][j - 1] = enmEFTBL.LOOP.v;
                    ptr[i][0]++;
                }
                ret = i + 1;
            }
        }
        return ret;
    }

    /**
     * Returns the number of tones used
     * Input:
     * <p>
     * Output:
     * int	: The largest tone number
     */
    private int getMaxTone(int[][] ptr, int max) { // [128][66]
        int ret;
        ret = 0;
        for (int i = 0; i < max; i++) {
            if (ptr[i][0] != 0) {
                ret = i + 1;
            }
        }
        return ret;
    }

    /**
     * Returns the number of ToneTables in use.
     * Input:
     * <p>
     * Output:
     * int	: The largest tone number
     */
    private int getMaxToneTable(int[][] ptr, int max) { // [_TONETBL_MAX][1024+2]
        int i, ret;
        ret = 0;
        for (i = 0; i < max; i++) {
            if (ptr[i][0] != 0) {
                ret = i + 1;
            }
        }
        return ret;
    }

    /**
     * Returns the number of Opl3tbls used
     * Input:
     * <p>
     * Output:
     * int	: The largest tone number
     */
    private int getMaxOpl3tbl(int[][] ptr, int max) { // [_OPL3TBL_MAX][1024+2]
        int ret;
        ret = 0;
        for (int i = 0; i < max; i++) {
            if (ptr[i][0] != 0) {
                ret = i + 1;
            }
        }
        return ret;
    }

    /**
     * Returns the number of LFOs used
     * Input:
     * <p>
     * Output:
     * int	: The largest LFO number
     */
    private int getMaxLFO(int[][] ptr, int max) { // [_PITCH_MOD_MAX][5]
        int i, ret;
        ret = 0;
        for (i = 0; i < max; i++) {
            if (ptr[i][0] != 0) {
                ret = i + 1;
            }
        }
        return ret;
    }

//    /**
//     * Returns the number of DPCMs used
//     * Input:
//     * <p>
//     * Output:
//     * int	: The largest tone number
//     */
//    int getMaxDPCM(DPCMTBL dpcm_tbl[_DPCM_MAX]) {
//        int ret = 0;
//
//        for (int i = 0; i < _DPCM_MAX; i++) {
//            if (dpcm_tbl[i].flag != 0) {
//                ret = i + 1;
//            }
//        }
//        return ret;
//    }

    /**
     * Returns the number of hardware effects in use.
     * Input:
     * <p>
     * Output:
     * int	: The largest tone number
     */
    private int getMaxHardEffect(int[][] ptr, int max) { // [_HARD_EFFECT_MAX][5]
        int ret;

        ret = 0;

        for (int i = 0; i < max; i++) {
            if (ptr[i][0] != 0) {
                ret = i + 1;
            }
        }
        return ret;
    }

    /**
     * Returns the number of effect waveforms used
     * Input:
     * <p>
     * Output:
     * int	: The largest tone number
     */
    private int getMaxEffectWave(int[][] ptr, int max) { // [_EFFECT_WAVE_MAX][33]
        int i, ret;

        ret = 0;

        for (i = 0; i < max; i++) {
            if (ptr[i][0] != 0) {
                ret = i + 1;
            }
        }
        return ret;
    }

    /**
     * Writing Tones/Envelopes
     * Input:
     * <p>
     * Output:
     * none
     */
    private void writeTone(List<MmlDatum2> fp, int[][] tbl, String str, int max) { // [128][1024]
        int i, j, x;
        String t;

        t = String.format("%d_table:", str);
        fp.add(new MmlDatum2(t + "\n", -2, t));

        if (max != 0) {
            for (i = 0; i < max; i++) {
                if (tbl[i][0] != 0) {
                    t = String.format("%d_%03d", str, i);
                    fp.add(new MmlDatum2(String.format("\tdw\t%d\n", t), -3, t));
                } else {
                    fp.add(new MmlDatum2("\tdw\t0\n", -1, 0, -1, 0));
                }
            }
        }

        t = String.format("%d_lp_table:", str);
        fp.add(new MmlDatum2(t + "\n", -2, t));

        if (max != 0) {
            for (i = 0; i < max; i++) {
                if (tbl[i][0] != 0) {
                    t = String.format("%d_lp_%03d", str, i);
                    fp.add(new MmlDatum2(String.format("\tdw\t%d\n", t), -3, t));
                } else {
                    fp.add(new MmlDatum2("\tdw\t0\n", -1, 0, -1, 0));
                }
            }

            for (i = 0; i < max; i++) {
                if (tbl[i][0] != 0) {
                    t = String.format("%d_%03d:", str, i);
                    fp.add(new MmlDatum2(String.format("\n%d\n", t), -2, t));
                    x = 0;
                    for (j = 1; j <= tbl[i][0]; j++) {
                        if (tbl[i][j] == enmEFTBL.LOOP.v) {
                            if (x != 0) fp.add(new MmlDatum2("\n", 0));
                            t = String.format("%d_lp_%03d:", str, i);
                            fp.add(new MmlDatum2(String.format("%d\n", t), -2, t));
                            x = 0;
                        } else if (x == 0) {
                            fp.add(new MmlDatum2(String.format("\tdb\t%02x", tbl[i][j] & 0xff), -1, tbl[i][j] & 0xff));
                            x++;
                        } else if (x == 7) {
                            fp.add(new MmlDatum2(String.format(",%02x\n", tbl[i][j] & 0xff), -1, tbl[i][j] & 0xff));
                            x = 0;
                        } else {
                            fp.add(new MmlDatum2(String.format(",%02x", tbl[i][j] & 0xff), -1, tbl[i][j] & 0xff));
                            x++;
                        }
                    }
                }
            }
        }

        fp.add(new MmlDatum2("\n\n", 0));
    }

//    /**
//     * Writing FM tones
//     * Input:
//     * <p>
//     * Output:
//     * none
//     */
//    void writeToneFM(FILE*fp, int tbl[_FM_TONE_MAX][66], char*str, int max) {
//        fprintf(fp, "%s_data_table:\n", str);
//        if (max != 0) {
//            for (int i = 0; i < max; i++) {
//                if (tbl[i][0] != 0) {
//                    fprintf(fp, "\tdw\t%s_%03d\n", str, i);
//                } else {
//                    fprintf(fp, "\tdw\t0\n");
//                }
//            }
//
//            for (int i = 0; i < max; i++) {
//                if (tbl[i][0] != 0) {
//                    fprintf(fp, "\n%s_%03d:\n", str, i);
//                    int x = 0;
//                    for (int j = 1; j <= tbl[i][0]; j++) { // tbl[i][0] = Data volume(byte)
//                        if (x == 0) {
//                            fprintf(fp, "\tdb\t$%02x", tbl[i][j] & 0xff);
//                            x++;
//                        } else if (x == 7) {
//                            fprintf(fp, ",$%02x\n", tbl[i][j] & 0xff);
//                            x = 0;
//                        } else {
//                            fprintf(fp, ",$%02x", tbl[i][j] & 0xff);
//                            x++;
//                        }
//                    }
//                }
//            }
//        }
//    }

//    /**
//     * Writing WTB tones
//     * Input:
//     * <p>
//     * Output:
//     * none
//     */
//    void writeToneWTB(FILE*fp, int tbl[_WTB_TONE_MAX][66], char*str, int max) {
//        fprintf(fp, "%s_data_table:\n", str);
//        if (max != 0) {
//            for (int i = 0; i < max; i++) {
//                if (tbl[i][0] != 0) {
//                    fprintf(fp, "\tdw\t%s_%03d\n", str, i);
//                } else {
//                    fprintf(fp, "\tdw\t0\n");
//                }
//            }
//
//            for (int i = 0; i < max; i++) {
//                if (tbl[i][0] != 0) {
//                    fprintf(fp, "\n%s_%03d:\n", str, i);
//                    int x = 0;
//                    for (int j = 1; j <= tbl[i][0]; j++) { // tbl[i][0] = Data volume(byte)
//                        if (x == 0) {
//                            fprintf(fp, "\tdb\t$%02x", tbl[i][j] & 0xff);
//                            x++;
//                        } else if (x == 7) {
//                            fprintf(fp, ",$%02x\n", tbl[i][j] & 0xff);
//                            x = 0;
//                        } else {
//                            fprintf(fp, ",$%02x", tbl[i][j] & 0xff);
//                            x++;
//                        }
//                    }
//                }
//            }
//        }
//
//        fprintf(fp, "\n\n");
//    }

    /**
     * Writing ToneTable
     * Input:
     * <p>
     * Output:
     * none
     */
    private void writeToneTable(List<MmlDatum2> fp, int[][] tbl, String str, int max) { // [_TONETBL_MAX][1024+2]
        String t = String.format("%d_data_table:", str);
        fp.add(new MmlDatum2(String.format("%d\n", t), -2, t));
        if (max != 0) {
            for (int i = 0; i < max; i++) {
                if (tbl[i][0] != 0) {
                    t = String.format("%d_%03d", str, i);
                    fp.add(new MmlDatum2(String.format("\tdw\t%d\n", t), -3, t));
                } else {
                    fp.add(new MmlDatum2("\tdw\t0\n", -1, 0, -1, 0));
                }
            }

            for (int i = 0; i < max; i++) {
                if (tbl[i][0] != 0) {
                    t = String.format("%d_%03d:", str, i);
                    fp.add(new MmlDatum2(String.format("\n%d\n", t), -2, t));
                    int x = 0;
                    for (int j = 0, k = 1; j < tbl[i][0] / 9; k += 9, j++) {
                        byte b = (byte) (tbl[i][k] & 0xff);
                        byte b2 = (byte) (tbl[i][k + 1] & 0xff);
                        fp.add(new MmlDatum2(String.format("\tdb\t%02x,%02x\n", b, b2), -1, b, -1, b2));
                        short s = (short) (tbl[i][k + 2] & 0xffff);
                        fp.add(new MmlDatum2(String.format("\tdw\t${0:x04}\n", s), -1, (byte) s, -1, (byte) (s >> 8)));
                        s = (short) (tbl[i][k + 3] & 0xffff);
                        fp.add(new MmlDatum2(String.format("\tdw\t%d\n", s), -1, (byte) s, -1, (byte) (s >> 8)));
                        fp.add(new MmlDatum2(String.format("\tdb\t%02x,%02x,%02x,%02x,%02x\n"
                                , tbl[i][k + 4] & 0xff
                                , tbl[i][k + 5] & 0xff
                                , tbl[i][k + 6] & 0xff
                                , tbl[i][k + 7] & 0xff
                                , tbl[i][k + 8] & 0xff
                        )
                                , -1
                                , tbl[i][k + 4] & 0xff
                                , -1
                                , tbl[i][k + 5] & 0xff
                                , -1
                                , tbl[i][k + 6] & 0xff
                                , -1
                                , tbl[i][k + 7] & 0xff
                                , -1
                                , tbl[i][k + 8] & 0xff
                        ));
                    }
                }
            }
        }

        fp.add(new MmlDatum2("\n\n", 0));
    }

    /**
     * Opl3tbl writing
     * Input:
     * <p>
     * Output:
     * none
     */
    private void writeOPL3tbl(List<MmlDatum2> fp, String str, int max) {
        String t = String.format("%d_data_table:", str);
        fp.add(new MmlDatum2(String.format("%d\n", t), -2, t));
        if (max != 0) {
            for (int i = 0; i < max; i++) {
                if (opl3op_tbl[i][0] != 0) {
                    t = String.format("%d_%03d", str, i);
                    fp.add(new MmlDatum2(String.format("\tdw\t%d\n", t), -3, t));
                } else {
                    fp.add(new MmlDatum2("\tdw\t0\n", -1, 0, -1, 0));
                }
            }

            for (int i = 0; i < max; i++) {
                if (opl3op_tbl[i][0] != 0) {
                    t = String.format("%d_%03d:", str, i);
                    fp.add(new MmlDatum2(String.format("\n%d\n", t), -2, t));
                    int x = 0;

                    int cnt_val = opl3op_tbl[i][2] & 0x03;

                    if (opl3op_flag[i] != 0) {
                        // 4OP mode
                        int[] opf_table = new int[] {0, 2, 1, 3};
                        cnt_val = opf_table[cnt_val];
                    }

                    // Reg.$C0
                    byte b = (byte) (((opl3op_tbl[i][1] & 0x07) << 1) | ((cnt_val & 0x01)));
                    fp.add(new MmlDatum2(String.format("\tdb\t%02x\n", b), -1, b));

                    // Reg.$C0 + 3
                    b = (byte) ((cnt_val & 0x02) >> 1);
                    fp.add(new MmlDatum2(String.format("\tdb\t%02x\n", b), -1, b));

                    // Reg.$BD
                    fp.add(new MmlDatum2("\tdb\t$00\n\n", -1, (byte) 0));

                    for (int j = 0, k = 6; j < (opl3op_tbl[i][0] - 5) / 12; k += 12, j++) {
                        // Reg.$20
                        b = (byte) (((opl3op_tbl[i][k] & 0x1) << 7) |
                                ((opl3op_tbl[i][k + 1] & 0x1) << 6) |
                                ((opl3op_tbl[i][k + 2] & 0x1) << 5) |
                                ((opl3op_tbl[i][k + 3] & 0x1) << 4) |
                                (opl3op_tbl[i][k + 4] & 0xf));
                        fp.add(new MmlDatum2(String.format("\tdb\t%02x\n", b), -1, b));

                        // Reg.$40
                        b = (byte) (((opl3op_tbl[i][k + 5] & 0x3) << 6) |
                                ((opl3op_tbl[i][k + 6] & 0x3f)));
                        fp.add(new MmlDatum2(String.format("\tdb\t%02x\n", b), -1, b));

                        // Reg.$60
                        b = (byte) (((opl3op_tbl[i][k + 7] & 0x0f) << 4) |
                                ((opl3op_tbl[i][k + 8] & 0x0f)));
                        fp.add(new MmlDatum2(String.format("\tdb\t%02x\n", b), -1, b));

                        // Reg.$80
                        b = (byte) (((opl3op_tbl[i][k + 9] & 0x0f) << 4) |
                                ((opl3op_tbl[i][k + 10] & 0x0f)));
                        fp.add(new MmlDatum2(String.format("\tdb\t%02x\n", b), -1, b));

                        // Reg.$E0
                        b = (byte) (opl3op_tbl[i][k + 11] & 0x07);
                        fp.add(new MmlDatum2(String.format("\tdb\t%02x\n\n", b), -1, b));

                    }
                }
            }
        }

        fp.add(new MmlDatum2("\n\n", 0));
    }

//    /**
//     * Writing VRC7 tones
//     * Input:
//     * <p>
//     * Output:
//     * none
//     */
//    void writeToneVRC7(FILE*fp, int tbl[_VRC7_TONE_MAX][66], char*str, int max) {
//        fprintf(fp, "%s_data_table:\n", str);
//        if (max != 0) {
//            for (int i = 0; i < max; i++) {
//                if (tbl[i][0] != 0) {
//                    fprintf(fp, "\tdw\t%s_%03d\n", str, i);
//                } else {
//                    fprintf(fp, "\tdw\t0\n");
//                }
//            }
//
//            for (int i = 0; i < max; i++) {
//                if (tbl[i][0] != 0) {
//                    fprintf(fp, "\n%s_%03d:\n", str, i);
//                    int x = 0;
//                    for (int j = 1; j <= tbl[i][0]; j++) { // tbl[i][0] = Data volume(byte)
//                        if (x == 0) {
//                            fprintf(fp, "\tdb\t$%02x", tbl[i][j] & 0xff);
//                            x++;
//                        } else if (x == 7) {
//                            fprintf(fp, ",$%02x\n", tbl[i][j] & 0xff);
//                            x = 0;
//                        } else {
//                            fprintf(fp, ",$%02x", tbl[i][j] & 0xff);
//                            x++;
//                        }
//                    }
//                }
//            }
//        }
//
//        fprintf(fp, "\n\n");
//    }

    /**
     * Writing Hardware Effects
     * Input:
     * <p>
     * Output:
     * none
     */
    private void writeHardEffect(List<MmlDatum2> fp, int[][] tbl, String str, int max) { // [_HARD_EFFECT_MAX][5]
        String t = String.format("%d_effect_select:", str);
        fp.add(new MmlDatum2(String.format("%d\n", t), -2, t));
        for (int i = 0; i < max; i++) {
            byte b1 = (byte) tbl[i][1];
            byte b2 = (byte) (tbl[i][3] | 0x80);
            fp.add(new MmlDatum2(String.format("\tdb\t%02x,$84,%02x,$85,$00,$87,$80,$88\n", b1, b2)
                    , -1, b1, -1, 0x84, -1, b2, -1, 0x85, -1, 0x00, -1, 0x87, -1, 0x80, -1, 0x88));
            b1 = (byte) tbl[i][4];
            b2 = (byte) (tbl[i][2] & 0x00ff);
            byte b3 = (byte) ((tbl[i][2] % 0x0f00) >> 8);
            fp.add(new MmlDatum2(String.format("\tdb\t%02x,$86,%02x,$87,%02x,$ff,$00,$00\n", b1, b2, b3)
                    , -1, b1, -1, 0x86, -1, b2, -1, 0x87, -1, b3, -1, 0xff, -1, 0x00, -1, 0x00));
        }
    }

    /**
     * Writing effect waveforms
     * Input:
     * <p>
     * Output:
     * none
     */
    private void writeEffectWave(List<MmlDatum2> fp, int[][] tbl, String str, int max) { // [_EFFECT_WAVE_MAX][33]
        String t = String.format("%d_4088_data:", str);
        fp.add(new MmlDatum2(String.format("%d\n", t), -2, t));
        for (int i = 0; i < max; i++) {
            if (tbl[i][0] != 0) {
                int x = 0;
                for (int j = 1; j <= tbl[i][0]; j++) { // tbl[i][0] = Data volume(byte)
                    byte b1;
                    if (x == 0) {
                        b1 = (byte) (tbl[i][j] & 0xff);
                        fp.add(new MmlDatum2(String.format("\tdb\t%02x", b1), -1, b1));
                        x++;
                    } else if (x == 7) {
                        b1 = (byte) (tbl[i][j] & 0xff);
                        fp.add(new MmlDatum2(String.format(",%02x\n", b1), -1, b1));
                        x = 0;
                    } else {
                        b1 = (byte) (tbl[i][j] & 0xff);
                        fp.add(new MmlDatum2(String.format(",%02x", b1), -1, b1));
                        x++;
                    }
                }
            } else {
                // Output dummy data
                for (int j = 0; j < 4; j++) {
                    fp.add(new MmlDatum2("\tdb\t$00,$00,$00,$00,$00,$00,$00,$00\n"
                            , -1, 0x00, -1, 0x00, -1, 0x00, -1, 0x00, -1, 0x00, -1, 0x00, -1, 0x00, -1, 0x00));
                }
            }
        }

        fp.add(new MmlDatum2("\n\n", 0));
    }

//    /**
//     * N106 Writing tone
//     * Input:
//     * <p>
//     * Output:
//     * none
//     */
//    void writeToneN106(FILE*fp, int tbl[_N106_TONE_MAX][2+64], char*str, int max) {
//        // Write channel used
//        fprintf(fp, "%s_channel:\n", str);
//        fprintf(fp, "\tdb\t%d\n", n106_track_num);
//        // Parameter Write
//        fprintf(fp, "%s_wave_init:\n", str);
//        if (max != 0) {
//            for (int i = 0; i < max; i++) {
//                int x;
//                switch (tbl[i][0]) {
//                    case 2 * 2 + 1:
//                        j = 7;
//                        x = tbl[i][1] * 2 * 2;
//                        break;
//                    case 4 * 2 + 1:
//                        j = 6;
//                        x = tbl[i][1] * 4 * 2;
//                        break;
//                    case 6 * 2 + 1:
//                        j = 5;
//                        x = tbl[i][1] * 6 * 2;
//                        break;
//                    case 8 * 2 + 1:
//                        j = 4;
//                        x = tbl[i][1] * 8 * 2;
//                        break;
//                    case 10 * 2 + 1:
//                        j = 3;
//                        x = tbl[i][1] * 10 * 2;
//                        break;
//                    case 12 * 2 + 1:
//                        j = 2;
//                        x = tbl[i][1] * 12 * 2;
//                        break;
//                    case 14 * 2 + 1:
//                        j = 1;
//                        x = tbl[i][1] * 14 * 2;
//                        break;
//                    case 16 * 2 + 1:
//                        j = 0;
//                        x = tbl[i][1] * 16 * 2;
//                        break;
//                    default:
//                        j = 0;
//                        x = 0;
//                        break;
//                }
//                fprintf(fp, "\tdb\t$%02x,$%02x\n", j, x);
//            }
//        }
//        // Parameter Write
//        fprintf(fp, "%s_wave_table:\n", str);
//        if (max != 0) {
//            for (int i = 0; i < max; i++) {
//                if (tbl[i][0] != 0) {
//                    fprintf(fp, "\tdw\t%s_wave_%03d\n", str, i);
//                } else {
//                    fprintf(fp, "\tdw\t0\n");
//                }
//            }
//        }
//        if (max != 0) {
//            for (int i = 0; i < max; i++) {
//                if (tbl[i][0] != 0) {
//                    fprintf(fp, "%s_wave_%03d:\n", str, i);
//                    fprintf(fp, "\tdb\t");
//                    for (int j = 0; j < tbl[i][0] / 2 - 1; j++) {
//                        fprintf(fp, "$%02x,", (tbl[i][2 + (j * 2) + 1] << 4) + tbl[i][2 + (j * 2) + 0]);
//                    }
//                    fprintf(fp, "$%02x\n", (tbl[i][2 + (j * 2) + 1] << 4) + tbl[i][2 + (j * 2) + 0]);
//                }
//            }
//        }
//        fprintf(fp, "\n\n");
//    }

//    /**
//     * Write DPCM table
//     * Input:
//     * <p>
//     * Output:
//     * none
//     */
//    void writeDPCM(FILE*fp, DPCMTBL dpcm_tbl[_DPCM_MAX], char*str, int max) {
//        fprintf(fp, "%s:\n", str);
//        for (int i = 0; i < max; i++) {
//            char* fname;
//            if (dpcm_tbl[i].flag != 0) {
//                    /*
//                         $4013 * 16 + 1 = size
//                         $4013 = (size - 1) / 16
//                         $4012 * 64 = adr
//                         adr = $4012 / 64
//                    */
//                int freq = dpcm_tbl[i].freq;
//                int size = (dpcm_tbl[i].size - 1) / 16;
//                int delta_init = dpcm_tbl[i].delta_init;
//                if (dpcm_tbl[i].index == -1) {
//                    adr = dpcm_tbl[i].start_adr / 64;
//                    fname = dpcm_tbl[i].fname;
//                } else {
//                    adr = dpcm_tbl[dpcm_tbl[i].index].start_adr / 64;
//                    fname = dpcm_tbl[dpcm_tbl[i].index].fname;
//                }
//                fprintf(fp, "\tdb\t$%02x,$%02x,$%02x,$%02x\t;%s\n", freq, delta_init, adr % 0x100, size, fname);
//            } else {
//                fprintf(fp, "\tdb\t$00,$00,$00,$00\t;unused\n");
//            }
//        }
//
//        if (dpcm_bankswitch) {
//            fprintf(fp, "%s_bank:\n", str);
//            for (i = 0; i < max; i++) {
//                int bank_ofs = 0;
//                if (dpcm_tbl[i].flag != 0) {
//                    if (dpcm_tbl[i].index == -1) {
//                        bank_ofs = dpcm_tbl[i].bank_ofs;
//                        fname = dpcm_tbl[i].fname;
//                    } else {
//                        bank_ofs = dpcm_tbl[dpcm_tbl[i].index].bank_ofs;
//                        fname = dpcm_tbl[dpcm_tbl[i].index].fname;
//                    }
//                    if (bank_ofs == 0) {
//                        fprintf(fp, "\tdb\t2*2\t;%s\n", fname);
//                    } else {
//                        bank_ofs -= 1;
//                        fprintf(fp, "\tdb\t(DPCM_EXTRA_BANK_START + %d*2)*2\t;%s\n", bank_ofs, fname);
//                    }
//                } else {
//                    fprintf(fp, "\tdb\t0\t;unused\n");
//                }
//            }
//        }
//
//        fprintf(fp, "\n");
//    }

//    void writeXPCM(FILE*fp, DPCMTBL xpcm_tbl[_DPCM_MAX], char*str, int max) {
//        int i;
//        int freq, adr, size;
//        //	int		cur_bank=0;
//
//        fprintf(fp, "%s:\n", str);
//        if (max != 0) {
//            for (i = 0; i < max; i++) {
//                if (xpcm_tbl[i].flag != 0) {
//                    freq = xpcm_tbl[i].freq;
//                    size = xpcm_tbl[i].size;
//                    if (xpcm_tbl[i].index == -1) {
//                        fprintf(fp, "\tdw\t_xpcm%03d,$%04x\n", i, size);
//                        fprintf(fp, "\tdb\tbank(_xpcm%03d),$00,$00,$00\n", i);
//                    } else {
//                        fprintf(fp, "\tdw\t_xpcm%03d,$%04x\n", xpcm_tbl[i].index, size);
//                        fprintf(fp, "\tdb\tbank(_xpcm%03d),$00,$00,$00\n", xpcm_tbl[i].index);
//                    }
//
//                    //				flogger.log(Level.TRACE, fp, "\tdb\t$%02x,$%02x,$%02x,$%02x\n", freq, 0, adr, size );
//                } else {
//                    fprintf(fp, "\tdw\t$0000,$0000\n\tdb\t$00,$00,$00,$00\n");
//                    //				flogger.log(Level.TRACE, fp, "\tdb\t$00,$00,$00,$00\n", freq, 0, adr, size );
//                }
//            }
//
//            if (xpcm_size != 0) {
//                fprintf(fp, "\n\t.bank\tDATA_BANK+%1d\n", curr_bank);
//                fprintf(fp, "\t.org\t$%04x\n\n", 0x6000);
//                adr = 0;
//                for (i = 0; i < max; i++) {
//                    if (xpcm_tbl[i].flag != 0 && xpcm_tbl[i].index == -1) {
//                        if (adr + xpcm_tbl[i].size > 0x1FFF) {
//                            curr_bank++;
//                            fprintf(fp, "\t.bank\tDATA_BANK+%1d\n", curr_bank);
//                            fprintf(fp, "\t.org\t$%04x\n\n", 0x6000);
//                            adr = 0;
//                        }
//                        fprintf(fp, "_xpcm%03d:\n", i);
//                        fprintf(fp, "\t.incbin \"%s\"\n", xpcm_tbl[i].fname);
//                        adr += xpcm_tbl[i].size;
//                    }
//                }
//                fprintf(fp, "\n\t.bank\tCONST_BANK\n");
//                curr_bank++;
//            }
//        }
//        fprintf(fp, "\n\n");
//    }

//    /**
//     *
//     */
//    static void writeDPCMSampleSub(FILE*fp) {
//
//        fprintf(fp, "\t.org\t$FFFA\n");
//        fprintf(fp, "\t.dw\tDMC_NMI\n");
//        fprintf(fp, "\t.dw\tDMC_RESET\n");
//        fprintf(fp, "\t.dw\tDMC_IRQ\n");
//    }
//
//    /**
//     * Write DPCM data
//     * Input:
//     * <p>
//     * Output:
//     * none
//     */
//    void writeDPCMSample(FILE*fp) {
//        int nes_bank = 1; // 8KB
//        int bank_ofs = 0; // 16KB
//
//        fprintf(fp, "; begin DPCM samples\n");
//        for (int i = 0; i < dpcm_size; i++) {
//            if (i % 0x2000 == 0) {
//                nes_bank++;
//                if (nes_bank == 4) {
//                    nes_bank = 2;
//                    bank_ofs++;
//                }
//                if (bank_ofs == 0) {
//                    fprintf(fp, "\t.bank\t%1d\n", nes_bank);
//                    putBankOrigin(fp, nes_bank);
//                } else {
//                    fprintf(fp, "\t.bank\tDPCM_EXTRA_BANK_START + %d*2 + %d - 2\n", bank_ofs - 1, nes_bank);
//                    dpcm_extra_bank_num++;
//                    fprintf(fp, "\t.org\t$%04x\n", 0x8000 + 0x2000 * nes_bank);
//                }
//            }
//            if ((i & 0x0f) == 0x00) {
//                fprintf(fp, "\tdb\t$%02x", dpcm_data[i]);
//            } else if ((i & 0x0f) != 0x0f) {
//                fprintf(fp, ",$%02x", dpcm_data[i]);
//            } else {
//                fprintf(fp, ",$%02x\n", dpcm_data[i]);
//            }
//            if (bank_ofs == 0) {
//                bank_usage[nes_bank]++;
//            }
//        }
//        fprintf(fp, "\n");
//        fprintf(fp, "; end DPCM samples\n\n");
//
//        if (dpcm_extra_bank_num) {
//            int x;
//            fprintf(fp, "; begin DPCM vectors\n");
//            fprintf(fp, "\t.bank\t3\n");
//            writeDPCMSampleSub(fp);
//            for (x = 2; x <= dpcm_extra_bank_num; x += 2) {
//                fprintf(fp, "\t.bank\tDPCM_EXTRA_BANK_START + %d\n", x - 1);
//                writeDPCMSampleSub(fp);
//            }
//            fprintf(fp, "; end DPCM vectors\n");
//        }
//        fprintf(fp, "\n");
//    }

    /**
     * Enter the title/composer/maker/recorder as a comment
     * Input:
     * <p>
     * Output:
     * none
     */
    private void writeSongInfo(List<MmlDatum2> fp) {
        fp.add(new MmlDatum2(String.format("; Title: %d\n", song_name), -1));
        fp.add(new MmlDatum2(String.format("; Composer: %d\n", composer), -1));
        fp.add(new MmlDatum2(String.format("; Maker: %d\n", maker), -1));

        if (programer != null) {
            fp.add(new MmlDatum2(String.format("; Programer: %d\n", programer), -1));
        }
        fp.add(new MmlDatum2("\n", -1));
    }

    /**
     * Input: Output max bytes of string data as db (fill with 0 after the end)
     * <p>
     * Output:
     */
    private void printStrDb(List<MmlDatum2> fp, String str, int max) {
        MyEncoding enc = new MyEncoding();
        byte[] ary = enc.GetSjisArrayFromString(str);

        String des = "";
        List<Integer> lstInt = new ArrayList<>();
        List<int[]> aryInt = new ArrayList<>();

        for (int i = 0; i < max; i++) {
            if (i < ary.length) {
                des += String.format("%02x", ary[i]);
                lstInt.add(-1);
                lstInt.add(ary[i] & 0xff);
            } else {
                des += "$00";
                lstInt.add(-1);
                lstInt.add(0);
            }

            if ((i + 1) % 8 == 0) {
                des += "@@";
                aryInt.add(lstInt.stream().mapToInt(x -> x).toArray());
                lstInt.clear();
            } else des += ", ";
        }

        //des += "$00";
        lstInt.add(-1);
        lstInt.add(0);
        aryInt.add(lstInt.stream().mapToInt(x -> x).toArray());

        String[] sDes = des.split("@@");

        for (int i = 0; i < sDes.length; i++) {

            fp.add(new MmlDatum2(String.format("\tdb\t%s\n", sDes[i]), aryInt.get(i)));
        }
    }

    /**
     * Write title/composer/maker as macro
     * Input:
     * <p>
     * Output:
     * none
     */
    private void writeSongInfoMacro(List<MmlDatum2> fp) {
        String t = "TITLE\t.macro";
        fp.add(new MmlDatum2(String.format("%d\n", t), -4, t));
        printStrDb(fp, song_name, 32);
        t = "\t.endm";
        fp.add(new MmlDatum2(String.format("%d\n", t), -4, t));
        t = "COMPOSER\t.macro";
        fp.add(new MmlDatum2(String.format("%d\n", t), -4, t));
        printStrDb(fp, composer, 32);
        t = "\t.endm";
        fp.add(new MmlDatum2(String.format("%d\n", t), -4, t));
        t = "MAKER\t.macro";
        fp.add(new MmlDatum2(String.format("%d\n", t), -4, t));
        printStrDb(fp, maker, 32);
        t = "\t.endm";
        fp.add(new MmlDatum2(String.format("%d\n", t), -4, t));

        // text
        t = "TITLE_TEXT\t.macro";
        fp.add(new MmlDatum2(String.format("%d\n", t), -4, t));
        fp.add(new MmlDatum2(String.format("\tdb\t\"%d\",$00\n", song_name), -1, String.format("\"%d\",$00", song_name)));
        t = "\t.endm";
        fp.add(new MmlDatum2(String.format("%d\n", t), -4, t));

        t = "COMPOSER_TEXT\t.macro";
        fp.add(new MmlDatum2(String.format("%d\n", t), -4, t));
        fp.add(new MmlDatum2(String.format("\tdb\t\"%d\",$00\n", composer), -1, String.format("\"%d\",$00", composer)));
        t = "\t.endm";
        fp.add(new MmlDatum2(String.format("%d\n", t), -4, t));

        t = "MAKER_TEXT\t.macro";
        fp.add(new MmlDatum2(String.format("%d\n", t), -4, t));
        fp.add(new MmlDatum2(String.format("\tdb\t\"%d\",$00\n", maker), -1, String.format("\"%d\",$00", maker)));
        t = "\t.endm";
        fp.add(new MmlDatum2(String.format("%d\n", t), -4, t));

        if (use_pcm != 0) {
            t = "PCMFILE\t.macro";
            fp.add(new MmlDatum2(String.format("%d\n", t), -4, t));
            fp.add(new MmlDatum2(String.format("\tdb\t\"%d\",$00\n", pcm_name), -1, String.format("\"%d\",$00", pcm_name)));
            t = "\t.endm";
            fp.add(new MmlDatum2(String.format("%d\n", t), -4, t));
        }
    }

    /**
     * Processing a command with n parameters
     * Input:
     * <p>
     * Output:
     * none
     */
    private int setCommandBuf(int n, CMD[] cmd, int cmdPtr, int com_no, String buf, int ptr, int line, int enable) {
        int cnt = 0, i;
        int[] param = new int[PARAM_MAX];

        for (i = 0; i < PARAM_MAX; i++) {
            param[i] = 0;
        }

        if (n != 0) {
            for (i = 0; i < n; i++) {
                cnt = 0;
                param[i] = str.Asc2Int(buf, ptr, /* ref */ cnt);
                if (cnt == 0) { // If there is no parameter, replace it with a number that will cause an error.
                    param[i] = (int) PARAM_OMITTED;
                }
                ptr += cnt;

                if (i < n - 1) { // If n is 2 or more, "," is inserted.
                    ptr = str.skipSpace(buf, ptr);
                    if (ptr < buf.length() && buf.charAt(ptr) == ',') {
                        ptr++;
                        ptr = str.skipSpace(buf, ptr);
                    } else { // If there is no separator ",", the parameter is omitted.
                        for (i++; i < n; i++) // Omit from next parameter
                            param[i] = (int) PARAM_OMITTED;
                    }
                }
            }
        }

        if (enable != 0) {
            cmd[cmdPtr].cnt = 0;
            cmd[cmdPtr].line = line;
            cmd[cmdPtr].cmd = com_no;
            cmd[cmdPtr].len = 0;
            if (n != 0) {
                for (i = 0; i < n; i++) {
                    cmd[cmdPtr].param[i] = param[i];
                }
            }
        }

        return ptr;
    }

    /**
     * Get note length parameters
     * Input:
     * <p>
     * Output:
     * none
     */
    private int getLengthSub(String buf, int ptr, /* ref */ double len, double def) {
        int cnt = 0;
        double temp;

        // Frame specification
        if (buf.charAt(ptr) == '#') {
            ptr++;
            len = str.Asc2Int(buf, ptr, /* ref */ cnt);
            if (cnt != 0) {
                ptr += cnt;
                len = len / tbase;
            } else {
                len = -1;
            }
            // Count specification
        } else if (buf.charAt(ptr) == '%') {
            ptr++;
            len = str.Asc2Int(buf, ptr, /* ref */ cnt);
            if (cnt != 0) {
                ptr += cnt;
            } else {
                len = -1;
            }
            // Musical note length designation
        } else {
            len = str.Asc2Int(buf, ptr, /* ref */ cnt);
            if (cnt != 0) {
                ptr += cnt;
                if (len > 0)
                    len = _BASE / (len);
            } else {
                // If there is no parameter, replace it with a number that will cause an error.
                len = def;
            }
            // Do not process on error /l command
            if (len != -1) {
                // Processing of dots (multiple possible)
                temp = len;
                while (buf.charAt(ptr) == '.') {
                    temp /= 2;
                    len += temp;
                    ptr++;
                }
            }
        }
        return ptr;
    }

    /**
     * Get note length
     * Output:
     * len:
     */
    private int getLength(String buf, int ptr, /* ref */ double len, double def) {
        ptr = getLengthSub(buf, ptr, /* ref */ len, def);
        // Note length subtraction (only possible once)
        if (buf.charAt(ptr) == '-' || buf.charAt(ptr) == '~') {
            double len_adjust = 0;
            ptr++;
            ptr = getLengthSub(buf, ptr, /* ref */ len_adjust, def);
            if (len - len_adjust > 0) {
                len = len - len_adjust;
            } else {
                //dispError(); // Catching errors at the caller
                len = len - len_adjust;
            }
        }
        return ptr;
    }

    /**
     * Processing commands with one parameter (note length)
     * Input:
     * <p>
     * Output:
     * none
     */
    private int setCommandBufL(CMD[] cmd, int cmdPtr, int com_no, String buf, int ptr, int line, int enable) {
        cmd[cmdPtr].cnt = 0;
        cmd[cmdPtr].line = line;
        cmd[cmdPtr].cmd = com_no;

        ptr = getLength(buf, ptr, /* ref */ cmd[cmdPtr].len, -1);
        if (cmd[cmdPtr].len > 0) {
            if (enable != 0) {
                length = cmd[cmdPtr].len;
            }
        } else {
            dispError(enmErrNum.ABNORMAL_NOTE_LENGTH_VALUE.ordinal(), cmd[cmdPtr].filename, line);
        }

        return ptr;
    }

    /**
     * Processing commands with one parameter (scale/length)
     * Input:
     * <p>
     * Output:
     * none
     */
    private int setCommandBufN(CMD[] cmd, int cmdPtr, int com_no, String buf, int ptr, int line, int enable) {
        int oct_ofs, note;
        double len = 0;

        com_no += transpose;

        // Take measures to allow things like c+-++-++-- (not something that's usually done)
        while (true) {
            if (buf.charAt(ptr) == '+') {
                com_no++;
                ptr++;
            } else if (buf.charAt(ptr) == '-') {
                com_no--;
                ptr++;
            } else {
                break;
            }
        }
        // Octave correction
        oct_ofs = 0;
        while (com_no < _NOTE_C) {
            com_no += 12;
            oct_ofs--;
        }
        while (com_no > _NOTE_B) {
            com_no -= 12;
            oct_ofs++;
        }

        note = ((octave + oct_ofs) << 4) + com_no;
        // Scale range check
        if (note < 0) {
            switch (note) {
                case -5:
                    note = 15;
                    break;
                case -6:
                    note = 14;
                    break;
                case -7:
                    note = 13;
                    break;
                default:
                    note = 0;
                    break;
            }
        } else if (note > MAX_NOTE) {
            note = MAX_NOTE;
        }

        ptr = getLength(buf, ptr, /* ref */ len, length);
        if (len <= 0) {
            dispError(enmErrNum.ABNORMAL_NOTE_LENGTH_VALUE.ordinal(), cmd[cmdPtr].filename, line);
            len = 0.0;
        }
        if (enable != 0) {
            cmd[cmdPtr].cnt = 0;
            cmd[cmdPtr].line = line;
            cmd[cmdPtr].cmd = note;
            cmd[cmdPtr].len = len;
        }

        return ptr;
    }

    // Processing Drum Flag Commands
    private int setCommandBufD(CMD[] cmd, int cmdPtr, int com_no, String buf, int ptr, int line, int enable) {
        double len = 0;
        int bit = 0x00;

        int loop_end = 0;

        while (loop_end == 0) {
            switch (buf.charAt(ptr)) {
                case 'H':
                    bit |= (1 << 0);
                    break;
                case 'C':
                    bit |= (1 << 1);
                    break;
                case 'M':
                    bit |= (1 << 2);
                    break;
                case 'S':
                    bit |= (1 << 3);
                    break;
                case 'B':
                    bit |= (1 << 4);
                    break;
                default:
                    loop_end = 1;
                    break;
            }
            if (loop_end == 0)
                ptr++;
        }

        // The default duration is 0
        ptr = getLength(buf, ptr, /* ref */ len, 0);
        if (len < 0) {
            dispError(enmErrNum.ABNORMAL_NOTE_LENGTH_VALUE.ordinal(), cmd[cmdPtr].filename, line);
            len = 0.0;
        }

        if (enable != 0) {
            cmd[cmdPtr].cnt = 0;
            cmd[cmdPtr].line = line;
            cmd[cmdPtr].cmd = com_no;
            cmd[cmdPtr].len = len;
            cmd[cmdPtr].param[0] = bit;
        }

        return ptr;
    }

    /**
     * Processing commands with one parameter (scale (direct specification)/note length)
     * Input:
     * <p>
     * Output:
     * none
     */
    private int setCommandBufN0(CMD[] cmd, int cmdPtr, String buf, int ptr, int line, int enable) {
        int cnt, note;
        double len = 0;

        cnt = 0;
        note = str.Asc2Int(buf, ptr, /* ref */ cnt);
        if (cnt == 0) {
            dispError(enmErrNum.ABNORMAL_PITCH_VALUE.ordinal(), cmd[cmdPtr].filename, line);
            return ptr + 1;
        }
        ptr += cnt;

        // Scale range check
        if (note < 0) {
            note = 0;
        } else if (note > MAX_NOTE) {
            note = MAX_NOTE;
        }

        ptr = str.skipSpace(buf, ptr); // Skip extra spaces
        // When there is a ",", there is a sound length.
        if (buf.charAt(ptr) == ',') {
            ptr++;
            ptr = str.skipSpace(buf, ptr); // Skip extra spaces

            ptr = getLength(buf, ptr, /* ref */ len, length);
            if (len <= 0) {
                dispError(enmErrNum.ABNORMAL_NOTE_LENGTH_VALUE.ordinal(), cmd[cmdPtr].filename, line);
                len = 0.0;
            }
            // Use default duration when "," is not present
        } else {
            len = length;
        }

        if (enable != 0) {
            cmd[cmdPtr].cnt = 0;
            cmd[cmdPtr].line = line;
            cmd[cmdPtr].cmd = note;
            cmd[cmdPtr].len = len;
        }

        return ptr;
    }

    /**
     * Processing commands with one parameter (frequency (direct specification)/tone length)
     * Input:
     * <p>
     * Output:
     * none
     */
    private int setCommandBufN1(CMD[] cmd, int cmdPtr, int com_no, String buf, int ptr, int line, int enable) {
        int cnt, freq;
        double len = 0;

        cnt = 0;
        freq = str.Asc2Int(buf, ptr, /* ref */ cnt);
        // Character count check
        if (cnt == 0) {
            dispError(enmErrNum.ABNORMAL_PITCH_VALUE.ordinal(), cmd[cmdPtr].filename, line);
            return ptr + 1;
        }
        ptr += cnt;
        // Parameter Range Checking
        if (0x0008 <= freq || freq >= 0x07f2) {
            dispError(enmErrNum.ABNORMAL_PITCH_VALUE.ordinal(), cmd[cmdPtr].filename, line);
            return ptr + 1;
        }
        // If there is a ",", get the note length
        ptr = str.skipSpace(buf, ptr);
        if (buf.charAt(ptr) == ',') {
            ptr++;
            ptr = str.skipSpace(buf, ptr);
            ptr = getLength(buf, ptr, /* ref */ len, length);
            if (len <= 0) {
                dispError(enmErrNum.ABNORMAL_NOTE_LENGTH_VALUE.ordinal(), cmd[cmdPtr].filename, line);
                len = 0.0;
            }
            // If there is no ",", it will default to the default length.
        } else {
            len = length;
        }

        if (enable != 0) {
            cmd[cmdPtr].cnt = 0;
            cmd[cmdPtr].line = line;
            cmd[cmdPtr].cmd = com_no;
            cmd[cmdPtr].len = len;
            cmd[cmdPtr].param[0] = freq;
        }

        return ptr;
    }

    /**
     * Processing commands with one parameter (rest/note length)
     * Input:
     * <p>
     * Output:
     * none
     */
    private int setCommandBufR(CMD[] cmd, int cmdPtr, int com_no, String buf, int ptr, int line, int enable) {
        double len = 0;

        ptr = getLength(buf, ptr, /* ref */ len, length);
        if (len <= 0) {
            dispError(enmErrNum.ABNORMAL_NOTE_LENGTH_VALUE.ordinal(), cmd[cmdPtr].filename, line);
            len = 0.0;
        }

        if (enable != 0) {
            cmd[cmdPtr].cnt = 0;
            cmd[cmdPtr].line = line;
            cmd[cmdPtr].cmd = com_no;
            cmd[cmdPtr].len = len;
        }

        return ptr;
    }

    /**
     * Processing commands with one parameter (key-off/note length)
     * Input:
     * <p>
     * Output:
     * none
     */
    private int setCommandBufK(CMD[] cmd, int cmdPtr, int com_no, String buf, int ptr, int line, int enable) {
        double len = 0;

        ptr = getLength(buf, ptr, /* ref */ len, length);
        if (len < 0) { // With sound length 0
            dispError(enmErrNum.ABNORMAL_NOTE_LENGTH_VALUE.ordinal(), cmd[cmdPtr].filename, line);
            len = 0.0;
        }

        if (enable != 0) {
            cmd[cmdPtr].cnt = 0;
            cmd[cmdPtr].line = line;
            cmd[cmdPtr].cmd = com_no;
            cmd[cmdPtr].len = len;
        }

        return ptr;
    }

    /**
     * Input:
     * <p>
     * Output:
     * none
     */
    private CMD[] analyzeData(int trk, CMD[] cmd, /* ref */ int cmdPtr, LINE[] lptr) {
        int i, line, com, cnt;
        String buf;
        int ptr;


        cnt = 0;

        transpose = 0;

        for (line = 1; line < lptr.length; line++) {
            if ((lptr[line].status == _TRACK) && (isTrackNum(lptr[line].str, 0, trk) != 0)) {
                ptr = skipTrackHeader(lptr[line].str, 0);
                buf = lptr[line].str; // .substring(ptr);

                while (ptr < buf.length() && buf.charAt(ptr) != '\0') {
                    ptr = str.skipSpace(buf, ptr); // Skip extra spaces
                    if (ptr == buf.length() || buf.charAt(ptr) == '\0') break; // Is this the end of the line?
                    // Search for a command
                    for (i = 0; mml[i].num != enmMML._TRACK_END.v; i++) {
                        int n = mml[i].cmd.length();
                        n = Math.min(n, buf.length() - ptr);
                        if (mml[i].cmd.equals(buf.substring(ptr, n))) break;
                    }

                    ptr += mml[i].cmd.length(); // Skip the number of characters in the command
                    cmd[cmdPtr].filename = lptr[line].shortname; // Get file name when error occurs

                    switch (mml[i].num) {
                        // note
                        case _NOTE_C:
                        case _NOTE_D:
                        case _NOTE_E:
                        case _NOTE_F:
                        case _NOTE_G:
                        case _NOTE_A:
                        case _NOTE_B:
                            ptr = setCommandBufN(cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                            if ((mml[i].check.apply(trk)) == 0) {
                                dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                            }
                            break;
                        default:
                            switch (enmMML.values()[mml[i].num]) {
                                // octave
                                case _OCTAVE:
                                    com = str.Asc2Int(buf, ptr, /* ref */ cnt);
                                    if (cnt != 0) {
                                        // When the command is valid, it registers the action.
                                        if ((mml[i].check.apply(trk)) != 0) {
                                            if (trk == BTRACK(0) || trk == BTRACK(1) || trk == BTRACK(2)) {
                                                octave = com;
                                            } else {
                                                octave = com;
                                            }
                                        }
                                        ptr += cnt;
                                    }
                                    break;
                                // Octave up
                                case _OCT_UP:
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (octave_flag == 0) {
                                            octave++;
                                        } else {
                                            octave--;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                // Octave Down
                                case _OCT_DW:
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (octave_flag == 0) {
                                            octave--;
                                        } else {
                                            octave++;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                // Sound length setting
                                case _LENGTH:
                                    ptr = setCommandBufL(cmd, cmdPtr, enmMML._LENGTH.v, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) == 0) {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                // Notes (n command)
                                case _NOTE:
                                    ptr = setCommandBufN0(cmd, cmdPtr, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) == 0) {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                // Notes (@n command)
                                case _KEY:
                                    ptr = setCommandBufN1(cmd, cmdPtr, enmMML._KEY.v, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) == 0) {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                // Drum Bits
                                // Drum Notes
                                case _DRUM_BIT:
                                case _DRUM_NOTE:
                                    ptr = setCommandBufD(cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) == 0) {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;

                                // Rests/Tuplets
                                case _REST:
                                case _CONT_END:
                                case _TIE:
                                case _WAIT:
                                    ptr = setCommandBufR(cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) == 0) {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                // Key Off
                                case _KEY_OFF:
                                    ptr = setCommandBufK(cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) == 0) {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                // Command parameters are 0
                                case _SLAR:         // Slurs
                                case _SONG_LOOP:    // Song Loop
                                case _REPEAT_ST:    // Repeat (currently deployed)
                                case _REPEAT_ESC:   // Repeat interruption
                                case _CONT_NOTE:    // Tuplet start
                                case _LFO_OFF:
                                case _EP_OFF:
                                case _EN_OFF:
                                case _MH_OFF:
                                case _REPEAT_ST2:   // Repeat 2
                                case _REPEAT_ESC2:  // Repeat interruption2
//                                case _SHUFFLE_QUONTIZE_RESET:
//                                case _SHUFFLE_QUONTIZE_OFF:
                                case _SELF_DELAY_OFF:
                                case _SELF_DELAY_QUEUE_RESET:
                                    setCommandBuf(0, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) == 0) {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _JUMP_FLAG:
                                    use_jump = 1;
                                    setCommandBuf(0, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) == 0) {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;


                                // Commands with one parameter
                                case _TEMPO: // tempo
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] <= 0) {
                                            dispError(enmErrNum.ABNORMAL_TEMPO_VALUE.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = enmMML._NOP.v;
                                        } else {
                                            tbase = (double) _BASETEMPO / (double) cmd[cmdPtr].param[0];
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _TONE: // Tone switching
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        // Remove restrictions for vrc6 (built-in square wave, MMC5 up to @3)
                                        //if (cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > 3) {
                                        if (cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > 127) {
                                            dispError(enmErrNum.ABNORMAL_TONE_NUMBER.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _REL_ORG_TONE: // Release tone
                                case _ORG_TONE:     // Tone switching
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if ((mml[i].num == enmMML._REL_ORG_TONE.v) && (cmd[cmdPtr].param[0] == 255)) {
                                            // ok
                                        } else if (cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > 127) {
                                            dispError(enmErrNum.ABNORMAL_TONE_NUMBER.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _ENVELOPE: // Envelope Specification
                                    cmd[cmdPtr].filename = lptr[line].filename;
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] == 255) {
                                            volume_flag = 0x0000;
                                        } else if (0 <= cmd[cmdPtr].param[0] && cmd[cmdPtr].param[0] <= 127) {
                                            volume_flag = 0x8000;
                                        } else {
                                            dispError(enmErrNum.ABNORMAL_ENVELOPE_NUMBER.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _REL_ENV: // Release envelope specification
                                    cmd[cmdPtr].filename = lptr[line].filename;
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] == 255) {
                                            volume_flag = 0x0000;
                                        } else if (0 <= cmd[cmdPtr].param[0] && cmd[cmdPtr].param[0] <= 127) {
                                            volume_flag = 0x8000;
                                        } else {
                                            dispError(enmErrNum.ABNORMAL_ENVELOPE_NUMBER.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _VOL_PLUS: // Volume setting
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] == PARAM_OMITTED) {
                                            cmd[cmdPtr].param[0] = 1;
                                        }
                                        if ((0 <= volume_flag && volume_flag <= MAX_VOLUME)) {
                                            cmd[cmdPtr].cmd = enmMML._VOLUME.v;
                                            cmd[cmdPtr].param[0] = volume_flag + cmd[cmdPtr].param[0];
                                            if (((cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > MAX_VOLUME))) {
                                                dispError(enmErrNum.VOLUME_RANGE_OVER_OF_RELATIVE_VOLUME.ordinal(), lptr[line].filename, line);
                                                cmd[cmdPtr].cmd = 0;
                                                cmd[cmdPtr].line = 0;
                                            } else {
                                                volume_flag = cmd[cmdPtr].param[0];
                                            }
                                        } else {
                                            dispError(enmErrNum.RELATIVE_VOLUME_WAS_USED_WITHOUT_SPECIFYING_VOLUME.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _VOL_MINUS: // Volume setting
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] == PARAM_OMITTED) {
                                            cmd[cmdPtr].param[0] = 1;
                                        }
                                        if ((0 <= volume_flag && volume_flag <= MAX_VOLUME)) {
                                            cmd[cmdPtr].cmd = enmMML._VOLUME.v;
                                            cmd[cmdPtr].param[0] = volume_flag - cmd[cmdPtr].param[0];
                                            if (cmd[cmdPtr].param[0] < 0) {
                                                dispError(enmErrNum.VOLUME_RANGE_UNDER_OF_RELATIVE_VOLUME.ordinal(), lptr[line].filename, line);
                                                cmd[cmdPtr].cmd = 0;
                                                cmd[cmdPtr].line = 0;
                                            } else {
                                                volume_flag = cmd[cmdPtr].param[0];
                                            }
                                        } else {
                                            dispError(enmErrNum.RELATIVE_VOLUME_WAS_USED_WITHOUT_SPECIFYING_VOLUME.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _VOLUME: // Volume setting HuSIC
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (((cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > MAX_VOLUME))) {
                                            dispError(enmErrNum.ABNORMAL_VOLUME_VALUE.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        } else {
                                            volume_flag = cmd[cmdPtr].param[0];
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _HARD_ENVELOPE:
                                    ptr = setCommandBuf(2, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if ((cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > 1)
                                                && (cmd[cmdPtr].param[1] < 0 || cmd[cmdPtr].param[1] > 63)) {
                                            dispError(enmErrNum.ABNORMAL_ENVELOPE_VALUE.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        } else {
                                            volume_flag = 0x8000;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _QUONTIZE: // Quantize(length*n/gate_denom)
                                    ptr = setCommandBuf(2, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[1] == (int) PARAM_OMITTED) {
                                            cmd[cmdPtr].param[1] = 0;
                                        }
                                        if (cmd[cmdPtr].param[0] < 0
                                                || cmd[cmdPtr].param[0] > gate_denom
                                                || (cmd[cmdPtr].param[0] == 0 && cmd[cmdPtr].param[1] <= 0)
                                                || (cmd[cmdPtr].param[0] == gate_denom && cmd[cmdPtr].param[1] > 0)) {
                                            dispError(enmErrNum.ABNORMAL_QUANTIZE_VALUE.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _QUONTIZE2: // Quantize(length-n)
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) == 0) {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
//#if false
//                                case _SHUFFLE_QUONTIZE: // Shuffle Quantize Settings
//                                    ptr = setCommandBuf(3, cmd, mml[i].num, ptr, line, mml[i].check.apply(trk));
//                                    if ((mml[i].check.apply(trk)) != 0) {
//                                        if (cmd[cmdPtr].param[0] <= 0
//                                                || cmd[cmdPtr].param[1] <= 0
//                                                || cmd[cmdPtr].param[2] <= 0
//                                                || cmd[cmdPtr].param[0] == PARAM_OMITTED
//                                                || cmd[cmdPtr].param[1] == PARAM_OMITTED
//                                                || cmd[cmdPtr].param[2] == PARAM_OMITTED) {
//                                            dispError(enmErrNum.ABNORMAL_SHUFFLE_QUANTIZE_VALUE.ordinal(), lptr[line].filename, line);
//                                            cmd[cmdPtr].cmd = enmMML._NOP.v;
//                                            cmd[cmdPtr].line = 0;
//                                        }
//                                    } else {
//                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
//                                    }
//                                    break;
//#endif
                                case _LFO_ON: // Soft LFO
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if ((cmd[cmdPtr].param[0] != 255)
                                                && (cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > 63)) {
                                            dispError(enmErrNum.ABNORMAL_LFO_NUMBER.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;

                                // HuSIC
                                case _FMLFO_SET: // LFO Trig Command
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > 255) {
                                            dispError(enmErrNum.FMLFO_PARAM_IS_WRONG.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispWarning(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _FMLFO_FRQ: // LFO Freq Command
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > 255) {
                                            dispError(enmErrNum.FMLFO_PARAM_IS_WRONG.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispWarning(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;

                                case _NOISE_SW: // Noise Command
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > 1) {
                                            dispError(enmErrNum.ABNORMAL_TONE_NUMBER.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispWarning(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _MODE_CHG: // Mode Change Command
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] < 0) {
                                            dispError(enmErrNum.ABNORMAL_TONE_NUMBER.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispWarning(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _WAVE_CHG: // Wave Change Command
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] < 0) {
                                            dispError(enmErrNum.ABNORMAL_TONE_NUMBER.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispWarning(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;

                                case _PAN: // PAN Command
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > 255) {
                                            dispError(enmErrNum.ABNORMAL_VOLUME_VALUE.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispWarning(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;

                                case _L_PAN: // Left PAN Command
                                case _R_PAN: // Right PAN Command
                                case _C_PAN: // Center PAN Command

                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > 15) {
                                            dispError(enmErrNum.ABNORMAL_VOLUME_VALUE.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispWarning(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                // ----

                                case _REVERB_SET: // Reverb command
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] < 0) {
                                            dispError(enmErrNum.ABNORMAL_PARAMETERS.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispWarning(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;

                                case _DAMP_SET: // Damp command
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] < 0) {
                                            dispError(enmErrNum.ABNORMAL_PARAMETERS.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispWarning(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;

                                case _SET_OPBASE: // opbase command
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] < 0) {
                                            dispError(enmErrNum.ABNORMAL_PARAMETERS.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispWarning(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;

                                case _LOAD_OP2: // Load op2 command
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] < 0) {
                                            dispError(enmErrNum.ABNORMAL_PARAMETERS.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispWarning(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;

                                case _SET_TVP: // TVP command
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] < 0) {
                                            dispError(enmErrNum.ABNORMAL_PARAMETERS.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispWarning(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _DRUM_SW: // drum command
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] < 0) {
                                            dispError(enmErrNum.ABNORMAL_PARAMETERS.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispWarning(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _SET_FBS: // FBS command
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] < 0) {
                                            dispError(enmErrNum.ABNORMAL_PARAMETERS.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispWarning(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _SET_OPM: // opmode command
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] < 0) {
                                            dispError(enmErrNum.ABNORMAL_PARAMETERS.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispWarning(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;

                                // ----

                                case _EP_ON: // Pitch Envelope
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if ((cmd[cmdPtr].param[0] != 255)
                                                && (cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > 127)) {
                                            dispError(enmErrNum.ABNORMAL_PITCH_ENVELOPE_NUMBER.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _EN_ON: // Note Envelope
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if ((cmd[cmdPtr].param[0] != 255)
                                                && (cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > 127)) {
                                            dispError(enmErrNum.ABNORMAL_NOTE_ENVELOPE_NUMBER.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _MH_ON: // Hardware Effects
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if ((cmd[cmdPtr].param[0] != 255)
                                                && (cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > 15)) {
                                            dispError(enmErrNum.ABNORMAL_HARD_EFFECT_NUMBER.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _DETUNE: // Detune
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        // Pitch direction correction
                                        if (cmd[cmdPtr].param[0] != 255 && pitch_correction != 0)
                                            cmd[cmdPtr].param[0] = 0 - cmd[cmdPtr].param[0];

                                        if ((cmd[cmdPtr].param[0] != 255)
                                                && (cmd[cmdPtr].param[0] < -127 || cmd[cmdPtr].param[0] > 126)) {
                                            dispError(enmErrNum.ABNORMAL_DETUNE_VALUE.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _TRANSPOSE: // Transpose
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if ((cmd[cmdPtr].param[0] != 255)
                                                && (cmd[cmdPtr].param[0] < -127 || cmd[cmdPtr].param[0] > 126)) {
                                            dispError(enmErrNum.ABNORMAL_TRANSPOSE_VALUE.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                        transpose = cmd[cmdPtr].param[0];
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _REPEAT_END: // Repeat End
                                case _REPEAT_END2: // Repeat End
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] < 2) {
                                            dispError(enmErrNum.ABNORMAL_VALUE_OF_REPEAT_COUNT.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].param[0] = 2;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _VRC7_TONE: // VRC7 User Tone Switching
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > 63) {
                                            dispError(enmErrNum.ABNORMAL_TONE_NUMBER.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _SUN5B_HARD_SPEED: // PSG Hardware Envelope Speed
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > 65535) {
                                            dispError(enmErrNum.ABNORMAL_ENVELOPE_VALUE.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _SUN5B_HARD_ENV: // PSG Hardware Envelope Selection
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > 15) {
                                            dispError(enmErrNum.ABNORMAL_ENVELOPE_VALUE.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        } else {
                                            volume_flag = 0x8000;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _SUN5B_NOISE_FREQ: // PSG Noise Frequency
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if (cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > 31) {
                                            dispError(enmErrNum.ABNORMAL_PITCH_VALUE.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        } else {
                                            volume_flag = 0x8000;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _TEMPO2: // Frame Based Tempo
                                    ptr = setCommandBuf(2, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if ((cmd[cmdPtr].param[0] <= 0) || (cmd[cmdPtr].param[1] <= 0)) {
                                            dispError(enmErrNum.ABNORMAL_TEMPO_VALUE.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = enmMML._NOP.v;
                                        } else {
                                            tbase = (double) cmd[cmdPtr].param[0] * (double) cmd[cmdPtr].param[1] / _BASE;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _SWEEP: // Sweep
                                    ptr = setCommandBuf(2, cmd, cmdPtr, enmMML._SWEEP.v, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if ((cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > 15)
                                                || (cmd[cmdPtr].param[1] < 0 || cmd[cmdPtr].param[1] > 15)) {
                                            dispError(enmErrNum.ABNORMAL_SWEEP_VALUE.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _DATA_WRITE: // Write data (register)
                                    ptr = setCommandBuf(2, cmd, cmdPtr, enmMML._DATA_WRITE.v, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) == 0) {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _DATA_WRITE_OFS: // Write register with offset
                                    ptr = setCommandBuf(2, cmd, cmdPtr, enmMML._DATA_WRITE_OFS.v, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) == 0) {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;

                                case _DATA_THRUE: // Direct data writing
                                    ptr = setCommandBuf(2, cmd, cmdPtr, enmMML._DATA_THRUE.v, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) == 0) {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
//#if false
//                        case _XX_COMMAND: // For debugging
//                            ptr = setCommandBuf(2, cmd, _XX_COMMAND, ptr, line, mml[i].check.apply(trk));
//                            if ((mml[i].check.apply(trk)) == 0) {
//                                dispError(UNUSE_COMMAND_IN_THIS_TRACK, lptr[line].filename, line);
//                            }
//                            break;
//#endif
                                case _SELF_DELAY_ON: // Self Delay
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) != 0) {
                                        if ((cmd[cmdPtr].param[0] != 255)
                                                && (cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > SELF_DELAY_MAX)) {
                                            dispError(enmErrNum.ABNORMAL_SELFDELAY_VALUE.ordinal(), lptr[line].filename, line);
                                            cmd[cmdPtr].cmd = 0;
                                            cmd[cmdPtr].line = 0;
                                        }
                                    } else {
                                        cmd[cmdPtr].cmd = enmMML._NOP.v;
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;
                                case _DATA_BREAK: // Data conversion stopped
                                    setCommandBuf(0, cmd, cmdPtr, enmMML._TRACK_END.v, buf, ptr, line, mml[i].check.apply(trk));
                                    if ((mml[i].check.apply(trk)) == 0) {
                                        dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                    }
                                    break;

                                case _NEW_BANK:
                                    // Even if ignored, ptr will continue to read
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if (auto_bankswitch == 0) {
                                        if ((mml[i].check.apply(trk)) != 0) {
                                            if (cmd[cmdPtr].param[0] == PARAM_OMITTED) {
                                                // There are cases like that.
                                            }
                                        } else {
                                            dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                        }
                                    } else {
                                        cmd[cmdPtr].cmd = enmMML._NOP.v;
                                    }
                                    break;

                                case _SHIFT_AMOUNT: // Pitch shift amount (0 to 8)
                                    ptr = setCommandBuf(1, cmd, cmdPtr, mml[i].num, buf, ptr, line, mml[i].check.apply(trk));
                                    if (pitch_correction != 0) {
                                        if ((mml[i].check.apply(trk)) != 0) {
                                            if ((cmd[cmdPtr].param[0] < 0 || cmd[cmdPtr].param[0] > 8)) {
                                                dispError(enmErrNum.ABNORMAL_SHIFT_AMOUNT.ordinal(), lptr[line].filename, line);
                                                cmd[cmdPtr].cmd = 0;
                                                cmd[cmdPtr].line = 0;
                                            }
                                        } else {
                                            dispError(enmErrNum.UNUSE_COMMAND_IN_THIS_TRACK.ordinal(), lptr[line].filename, line);
                                        }
                                    } else {
                                        dispError(enmErrNum.CANT_USE_SHIFT_AMOUNT_WITHOUT_PITCH_CORRECTION.ordinal(), lptr[line].filename, line);
                                    }
                                    break;

                                default: // Other (Error)
                                    dispError(enmErrNum.COMMAND_NOT_DEFINED.ordinal(), lptr[line].filename, line);
                                    ptr++;
                                    break;
                            }
                            break;
                    }
                    if (cmd[cmdPtr].line != 0) {
                        cmdPtr++;
                    }
                }
            } else if (lptr[line].status == _INCLUDE) {
                cmd = analyzeData(trk, cmd, /* ref */ cmdPtr, lptr[line].inc_ptr);
            }
        }
        return cmd;
    }

//#if false
//    public class SHFL_Q {
//
//        public int flag;
//        public double diff;
//        public double _base; // Shuffle count length of Nth note
//    }
//
//    /**
//     * Shuffle Quantize
//     */
//    private void shuffleQuontizeSub(CMD[] ptr, int ptrPtr, SHFL_Q shf, double count) {
//        if (shf.flag != 0) {
//            double noteoff_time;
//            if (double2int(count / shf._base) % 2 == 1) {
//                // Note-on time is on the backbeat
//                ptr[ptrPtr].len = ptr[ptrPtr].len - shf.diff;
//            }
//            noteoff_time = count + ptr[ptrPtr].len;
//            if (double2int(noteoff_time / shf._base) % 2 == 1) {
//                // Note-off time is offbeat
//                ptr[ptrPtr].len = ptr[ptrPtr].len + shf.diff;
//            }
//        }
//    }
//
//    private void shuffleQuontize(CMD[] ptr, int ptrPtr) {
//        double count = 0.0; // Accumulation of sound duration, i.e. event occurrence time (in count units)
//        SHFL_Q shuffle = new SHFL_Q() {{
//            flag = 0;
//            diff = 0.0;
//            _base = 192.0;
//        }};
//        while (true) {
//            if (ptr[ptrPtr].cmd == enmMML._SHUFFLE_QUONTIZE.v) {
//                shuffle.flag = 1;
//                shuffle._base = _BASE / ptr[ptrPtr].param[0];
//                logger.log(Level.INFO, String.format("shfl %d\n", shuffle._base));
//                shuffle.diff = shuffle._base * 2 * ptr[ptrPtr].param[1] / (ptr[ptrPtr].param[2] + ptr[ptrPtr].param[1]) - shuffle._base;
//                    /*
//                    For example, if you divide a 16th note into 2:1,
//                    shuffle.base = 192/16 = 12; In other words, l16=l%12
//                    shuffle.diff = 24 * 2/3 - 12
//                                             = 16 - 12 = 4
//                    So, we divide the eighth note (%24) into %12+4 and %12-4, that is, %16 and %8.
//                    */
//                ptr[ptrPtr].cmd = enmMML._NOP.v;
//                ptrPtr++;
//            } else if (ptr[ptrPtr].cmd == enmMML._SHUFFLE_QUONTIZE_RESET.v) {
//                count = 0.0;
//                ptr[ptrPtr].cmd = enmMML._NOP.v;
//                ptrPtr++;
//            } else if (ptr[ptrPtr].cmd == enmMML._SHUFFLE_QUONTIZE_OFF.v) {
//                shuffle.flag = 0;
//                ptr[ptrPtr].cmd = enmMML._NOP.v;
//                ptrPtr++;
//            } else if (ptr[ptrPtr].cmd == enmMML._CONT_NOTE.v) {
//                // Does not involve the contents of the tuplets, but regards them as a whole
//                while (true) {
//                    if (ptr[ptrPtr].cmd == enmMML._TRACK_END.v) {
//                        // Ending in the middle of a tuplet
//                        // No error here
//                        return;
//                    } else if (ptr[ptrPtr].cmd == enmMML._CONT_END.v) {
//                        // Quantize the note length of this command
//                        shuffleQuontizeSub(ptr, ptrPtr, shuffle, count);
//                        count += ptr[ptrPtr].len;
//                        ptrPtr++;
//                        break;
//                    } else if (
//                            ptr[ptrPtr].cmd <= MAX_NOTE ||
//                                    ptr[ptrPtr].cmd == enmMML._DRUM_BIT.v ||
//                                    ptr[ptrPtr].cmd == enmMML._DRUM_NOTE.v ||
//                                    ptr[ptrPtr].cmd == enmMML._REST.v ||
//                                    ptr[ptrPtr].cmd == enmMML._KEY.v ||
//                                    ptr[ptrPtr].cmd == enmMML._NOTE.v ||
//                                    ptr[ptrPtr].cmd == enmMML._WAIT.v ||
//                                    ptr[ptrPtr].cmd == enmMML._TIE.v ||
//                                    temp -> { cmd == enmMML._KEY_OFF.v }) {
//                        // Ignore the contents
//                        ptrPtr++;
//                    } else {
//                        ptrPtr++;
//                    }
//                }
//            } else if (
//                    ptr[ptrPtr].cmd <= MAX_NOTE ||
//                            ptr[ptrPtr].cmd == enmMML._DRUM_BIT.v ||
//                            ptr[ptrPtr].cmd == enmMML._DRUM_NOTE.v ||
//                            ptr[ptrPtr].cmd == enmMML._REST.v ||
//                            ptr[ptrPtr].cmd == enmMML._KEY.v ||
//                            ptr[ptrPtr].cmd == enmMML._NOTE.v ||
//                            ptr[ptrPtr].cmd == enmMML._WAIT.v ||
//                            ptr[ptrPtr].cmd == enmMML._TIE.v ||
//                            temp -> { cmd == enmMML._KEY_OFF.v }) {
//                shuffleQuontizeSub(ptr, ptrPtr, shuffle, count);
//                count += ptr[ptrPtr].len;
//                ptrPtr++;
//            } else if (ptr[ptrPtr].cmd == enmMML._TRACK_END.v) {
//                break;
//            } else {
//                // Ignore the rest
//                ptrPtr++;
//            }
//        }
//    }
//#endif

    /**
     * Loop/Tuplet Evolution
     * Input:
     * ptr
     * Output:
     * *cmd
     */
    private int translateData(CMD[] cmd, /* ref */ int cmdPtr, CMD[] ptr, int ptrPtr) {
        CMD[] top, end, temp;
        int topPtr, endPtr, tempPtr;
        int cnt, i, loop;
        double len, gate;

        loop = 0;
        gate = 0;
        top = ptr;
        topPtr = ptrPtr;
        end = null;
        endPtr = -1;

        while (true) {
            switch (enmMML.values()[ptr[ptrPtr].cmd]) {
                case _REPEAT_ST:
                    ptrPtr++;
                    nest++;
                    ptrPtr = translateData(cmd, /* ref */ cmdPtr, ptr, ptrPtr);
                    if (ptrPtr == -1) {
                        // '[' is not closed
                        return -1;
                    }
                    nest--;
                    break;
                case _REPEAT_END:
                    if (nest <= 0) {
                        dispError(enmErrNum.DATA_ENDED_BY_LOOP_DEPTH_EXCEPT_0.ordinal(), ptr[ptrPtr].filename, ptr[ptrPtr].line);
                        ptr[ptrPtr].cmd = enmMML._NOP.v;
                        ptrPtr++;
                        break;
                    }
                    if (loop == 0) {
                        loop = ptr[ptrPtr].param[0];
                        end = ptr;
                        endPtr = ptrPtr + 1;
                    }
                    if (loop == 1) {
                        return endPtr;
                    }
                    ptr = top;
                    ptrPtr = topPtr;
                    loop--;
                    break;
                case _REPEAT_ESC:
                    if (nest <= 0) {
                        dispError(enmErrNum.DATA_ENDED_BY_LOOP_DEPTH_EXCEPT_0.ordinal(), ptr[ptrPtr].filename, ptr[ptrPtr].line);
                        ptr[ptrPtr].cmd = enmMML._NOP.v;
                        ptrPtr++;
                        break;
                    }
                    if (loop == 1) {
                        if (endPtr != -1) {
                            return endPtr;
                        }
                    }
                    ptrPtr++;
                    break;
                case _CONT_NOTE:
                    ptrPtr++;
                    temp = ptr;
                    tempPtr = ptrPtr;
                    // How many '[cdefgab]|n|@n|r|w' are there in '{}'?
                    cnt = 0;
                    len = 0;
                    while (true) {
                        if (temp[tempPtr].cmd == enmMML._TRACK_END.v) {
                            dispError(enmErrNum.DATA_ENDED_BY_CONTINUATION_NOTE.ordinal(), wk.mml_names[mml_idx], ptr[(ptrPtr - 1)].line);
                            setCommandBuf(0, cmd, cmdPtr, enmMML._TRACK_END.v, null, 0, ptr[ptrPtr].line, 1);
                            break;
                        } else if (temp[tempPtr].cmd == enmMML._CONT_END.v) {
                            if (cnt == 0) {
                                dispError(enmErrNum.TUPLET_BRACE_EMPTY.ordinal(), wk.mml_names[mml_idx], ptr[(ptrPtr - 1)].line);
                                len = 0;
                            } else {
                                // All contents of '{}' will be this length
                                len = temp[tempPtr].len / (double) cnt;
                            }
                            break;
                        } else if (
                                temp[tempPtr].cmd <= MAX_NOTE ||
                                        temp[tempPtr].cmd == enmMML._DRUM_BIT.v ||
                                        temp[tempPtr].cmd == enmMML._DRUM_NOTE.v ||
                                        temp[tempPtr].cmd == enmMML._REST.v ||
                                        temp[tempPtr].cmd == enmMML._KEY.v ||
                                        temp[tempPtr].cmd == enmMML._NOTE.v ||
                                        temp[tempPtr].cmd == enmMML._WAIT.v ||
                                        temp[tempPtr].cmd == enmMML._KEY_OFF.v) {
                            cnt++;
                        }
                        tempPtr++;
                    }
                    if (temp[tempPtr].cmd != enmMML._TRACK_END.v) {
                        while (ptr[ptrPtr].cmd != enmMML._TRACK_END.v) {
                            if (ptr[ptrPtr].cmd == enmMML._CONT_END.v) {
                                ptrPtr++;
                                break;
                            } else if (
                                    ptr[ptrPtr].cmd <= MAX_NOTE ||
                                            ptr[ptrPtr].cmd == enmMML._DRUM_BIT.v ||
                                            ptr[ptrPtr].cmd == enmMML._DRUM_NOTE.v ||
                                            ptr[ptrPtr].cmd == enmMML._REST.v ||
                                            ptr[ptrPtr].cmd == enmMML._KEY.v ||
                                            ptr[ptrPtr].cmd == enmMML._NOTE.v ||
                                            ptr[ptrPtr].cmd == enmMML._WAIT.v ||
                                            temp[tempPtr].cmd == enmMML._KEY_OFF.v) {
                                gate += len;
                                if (cmd[cmdPtr] == null) cmd[cmdPtr] = new CMD();
                                cmd[cmdPtr].filename = ptr[ptrPtr].filename;
                                cmd[cmdPtr].cnt = ptr[ptrPtr].cnt;
                                cmd[cmdPtr].frm = ptr[ptrPtr].frm;
                                cmd[cmdPtr].line = ptr[ptrPtr].line;
                                cmd[cmdPtr].cmd = ptr[ptrPtr].cmd;
                                cmd[cmdPtr].len = len;
                                for (i = 0; i < 8; i++) {
                                    cmd[cmdPtr].param[i] = ptr[ptrPtr].param[i];
                                }
                                gate -= cmd[cmdPtr].len;
                            } else if (ptr[ptrPtr].cmd == enmMML._TIE.v) {
                                // Remove ties within tuplets
                                if (cmd[cmdPtr] == null) cmd[cmdPtr] = new CMD();
                                cmd[cmdPtr].filename = ptr[ptrPtr].filename;
                                cmd[cmdPtr].cnt = 0;
                                cmd[cmdPtr].frm = 0;
                                cmd[cmdPtr].line = ptr[ptrPtr].line;
                                cmd[cmdPtr].cmd = enmMML._NOP.v;
                                cmd[cmdPtr].len = 0;
                            } else {
                                if (cmd[cmdPtr] == null) cmd[cmdPtr] = new CMD();
                                cmd[cmdPtr].filename = ptr[ptrPtr].filename;
                                cmd[cmdPtr].cnt = ptr[ptrPtr].cnt;
                                cmd[cmdPtr].frm = ptr[ptrPtr].frm;
                                cmd[cmdPtr].line = ptr[ptrPtr].line;
                                cmd[cmdPtr].cmd = ptr[ptrPtr].cmd;
                                cmd[cmdPtr].len = ptr[ptrPtr].len;
                                for (i = 0; i < 8; i++) {
                                    cmd[cmdPtr].param[i] = ptr[ptrPtr].param[i];
                                }
                            }
                            cmdPtr++;
                            ptrPtr++;
                        }
                    }
                    break;
                case _TRACK_END:
                    if (cmd[cmdPtr] == null) cmd[cmdPtr] = new CMD();
                    cmd[cmdPtr].filename = ptr[ptrPtr].filename;
                    cmd[cmdPtr].cnt = ptr[ptrPtr].cnt;
                    cmd[cmdPtr].frm = ptr[ptrPtr].frm;
                    cmd[cmdPtr].line = ptr[ptrPtr].line;
                    cmd[cmdPtr].cmd = ptr[ptrPtr].cmd;
                    cmd[cmdPtr].len = ptr[ptrPtr].len;
                    for (i = 0; i < 8; i++) {
                        cmd[cmdPtr].param[i] = ptr[ptrPtr].param[i];
                    }
                    cmdPtr++;
                    ptrPtr++;
                    if (nest != 0) {
                        dispError(enmErrNum.DATA_ENDED_BY_LOOP_DEPTH_EXCEPT_0.ordinal(), wk.mml_names[mml_idx], ptr[(ptrPtr - 1)].line);
                    }
                    return -1;
                default:
                    if (cmd[cmdPtr] == null) cmd[cmdPtr] = new CMD();
                    cmd[cmdPtr].filename = ptr[ptrPtr].filename;
                    cmd[cmdPtr].cnt = ptr[ptrPtr].cnt;
                    cmd[cmdPtr].frm = ptr[ptrPtr].frm;
                    cmd[cmdPtr].line = ptr[ptrPtr].line;
                    cmd[cmdPtr].cmd = ptr[ptrPtr].cmd;
                    cmd[cmdPtr].len = ptr[ptrPtr].len;
                    for (i = 0; i < 8; i++) {
                        cmd[cmdPtr].param[i] = ptr[ptrPtr].param[i];
                    }
                    cmdPtr++;
                    ptrPtr++;
                    break;
            }
        }
    }

    /**
     * Input:
     * <p>
     * Output:
     * none
     */
    String fn = "";
    int ln = 0;

    private void putAsm(List<MmlDatum2> fp, int data) {
        String t;
        int b;

        if (putAsm_pos == 0) {
            fn = mml_file_name;
            ln = mml_line_pos;
            b = data & 0xff;
            fp.add(new MmlDatum2(String.format("\tdb\t%02x", b), -1, b));
        } else {
            b = data & 0xff;
            fp.add(new MmlDatum2(String.format(",%02x", b), -1, b));
        }

        if (putAsm_pos == 7) {
            fp.add(new MmlDatum2(String.format("\t;Trk %d; %d: %d", str_track.charAt(mml_trk), fn, ln), 0));
            fp.add(new MmlDatum2("\n", 0));
        }
        if (++putAsm_pos > 7) {
            putAsm_pos = 0;
        }
        bank_usage[curr_bank]++;
    }

    private void putAsmFlash(List<MmlDatum2> fp) {
        if (putAsm_pos > 0) {
            fp.add(new MmlDatum2(String.format("\t;Trk %d; %d: %d", str_track.charAt(mml_trk), mml_file_name, mml_line_pos), 0));
            fp.add(new MmlDatum2("\n", 0));
            putAsm_pos = 0;
        }
    }

    /**
     *
     */
    private int[] bank_org_written_flag = new int[128];// = { 1 };
    private CompilerInfo compilerInfo;

    private void putBankOrigin(List<MmlDatum2> fp, int bank) {
        int org;
        if (bank > 127) {
            //assert(0);
            return;
        }
        if (bank_org_written_flag[bank] == 0) {
            switch (bank) {
                case 0:
                    org = 0x8000;
                    //assert(0);
                    break;
                case 1:
                    org = 0xa000;
                    break;
                case 2:
                    org = 0xc000;
                    break;
                case 3:
                    org = 0xe000;
                    break;
                default:
                    org = 0xa000;
                    break;
            }
            String t = String.format(".org\t${0:x04}", org);
            fp.add(new MmlDatum2(String.format("\t%d\n", t), 4, t));
            bank_org_written_flag[bank] = 1;
            if (bank > bank_maximum) {
                bank_maximum = bank;
            }
        }
    }

    /**
     * !=0: OK,	==0: out of range
     */
    private int checkBankRange(int bank) {
        if (allow_bankswitching != 0) {
            if (bank < 0 || bank > 127) {
                return 0;
            }
        } else {
            if (bank < 0 || bank > 3) {
                return 0;
            }
        }
        return 1;
    }

    /**
     * Input:
     * <p>
     * Output:
     * none
     */
    private int double2int(double d) {
        return (int) Math.round(d); // (d + 0.5);
    }

    /*
     *
     * ↓ pronunciation	  ↓ Key Off
     *   _			        _
     *  | ＼		       | ＼   Next sound (or event)
     * |	＼_________    |	＼_________
     * |			   	＼ |			   ＼
     * |		          |			         ＼
     * <------------------> delta_time From pronunciation to the next event
     * <--------------->	gate_time  From pronunciation to key-off
     *					<-> left_time  Time remaining from key off to the next event
     *
     */

    /**
     * Get delta time with slur ties
     * Input:
     * CMD *cmd; Position of command to start reading delta time
     * int allow_slur = 1; Slurs allowed (for notes)
     * = 0; No slurs (rests, etc.)
     * Output:
     * int *delta; Delta Time
     * Return:
     * CMD *cmd; Since cmd has been read in this function, return the new cmd position.
     */
    private int getDeltaTime(CMD[] cmd, int cmdPtr, /* ref */ int delta, int allow_slur) {
        delta = 0;
        while (true) {
            if (loop_flag == 0) {
                delta += (cmd[(cmdPtr + 1)].frm - cmd[cmdPtr].frm);
            } else {
                delta += (cmd[(cmdPtr + 1)].lfrm - cmd[cmdPtr].lfrm);
            }
            cmdPtr++;
//            if (cmd.cmd == _SLAR && allow_slur) {
//                cmd++;
//            } else
            if (cmd[cmdPtr].cmd != enmMML._TIE.v) {
                break;
            }
        }
        return cmdPtr;
    }

    /**
     * Calculate gate time from q and note length
     * Input:
     * <p>
     * Output:
     * <p>
     * Return:
     * int gate;
     */
    private int calcGateTime(int delta_time, /* ref */ GATE_Q gate_q) {
        int gate;
        gate = (delta_time * gate_q.rate) / gate_denom + gate_q.adjust;
        if (gate > delta_time) {
            gate = delta_time;
        } else if (gate < 0) {
            gate = 0;
        }
        if (delta_time != 0 && gate <= 0) {
            gate = 1;
        }
        return gate;
    }

    /**
     * Output of the sound length part of commands with sound length (processing when the frame length is 256 or more)
     * Input:
     * int wait_com_no; Command to connect when 256 frames or more (w or r)
     * int len; Frame Length
     * Output:
     */
    private void putLengthAndWait(List<MmlDatum2> fp, int wait_com_no, int len, /* ref */ CMD cmd) {
        int len_nokori = len; // Remaining sound length to be output (number of frames)

        if (len == 0) {
            dispWarning(enmSys.FRAME_LENGTH_IS_0.ordinal(), cmd.filename, cmd.line);
            return;
        } else if (len < 0) {
            dispError(enmErrNum.FRAME_LENGTH_LESSTHAN_0.ordinal(), cmd.filename, cmd.line);
            return;
        }

        if (len_nokori > 0xff) {
            putAsm(fp, 0xff);
            len_nokori -= 0xff;
        } else {
            putAsm(fp, len_nokori);
            len_nokori = 0;
        }
        while (len_nokori != 0) { // Repeat until the number of frames remaining to be output is 0
            if (len_nokori > 0xff) {
                // When there are 256 or more frames remaining
                putAsm(fp, wait_com_no);
                putAsm(fp, 0xff); // 255 Frame Output
                len_nokori -= 0xff;
            } else {
                // When the frame rate is 255 or less
                putAsm(fp, wait_com_no);
                putAsm(fp, len_nokori); // Output all remaining
                len_nokori = 0;
            }
        }
    }

    public static class PLAYSTATE {

        public GATE_Q gate_q;
        public int env;            // Current normal (key-on) envelope number or volume
        public int rel_env;        // Current release envelope number (-1: unused)
        public int last_written_env;   // Last written envelope number or volume
        public int tone;           //
        public int rel_tone;       //
        public int last_written_tone;  //
        public int key_pressed;        // Key on/off status
        public int[] last_note = new int[SELF_DELAY_MAX + 1];      // Last note I wrote (ignore @n)
        public int[] last_note_keep = new int[SELF_DELAY_MAX + 1]; // last_note state when using '¥' command
        public int self_delay;     // How many previous notes to use? (no self-delay if negative)
    }

    private void defaultPlayState(PLAYSTATE[] ps, int psPtr) {
        int i;
        if (ps[psPtr].gate_q == null) ps[psPtr].gate_q = new GATE_Q();
        ps[psPtr].gate_q.rate = gate_denom;
        ps[psPtr].gate_q.adjust = 0;
        ps[psPtr].env = -1;
        ps[psPtr].rel_env = -1;
        ps[psPtr].last_written_env = -1;
        ps[psPtr].tone = -1;
        ps[psPtr].rel_tone = -1;
        ps[psPtr].last_written_tone = -1;
        ps[psPtr].key_pressed = 0;
        for (i = 0; i < ps[psPtr].last_note.length; i++) {
            ps[psPtr].last_note[i] = -1;
            ps[psPtr].last_note_keep[i] = -1;
        }
        ps[psPtr].self_delay = -1;
    }

    /**
     * Release envelope & tone output, fill remaining time with r or w
     * Input:
     * It exists only to display an error message for cmd putLengthWait
     */
    private void putReleaseEffect(List<MmlDatum2> fp, int left_time, /* ref */ CMD cmd, /* ref */ PLAYSTATE ps) {
        int note = enmMCK.MCK_REST.v; // The default is to connect the remaining time with rests.

        // Double key off check
        if (ps.key_pressed == 0) {
            putAsm(fp, note);
            putLengthAndWait(fp, enmMCK.MCK_WAIT.v, left_time, /* ref */ cmd);
            return;
        }

        if ((ps.rel_env != -1) // Release envelope in progress
                && (ps.last_written_env != ps.rel_env)) { // The current envelope and the envelope being converted are different
            putAsm(fp, enmMCK.MCK_SET_VOL.v); // Release Envelope Output
            putAsm(fp, ps.rel_env);
            ps.last_written_env = ps.rel_env;
            note = enmMCK.MCK_WAIT.v; // Remaining time is waiting
        }
        if ((ps.rel_tone != -1) // Release tone active
                && (ps.last_written_tone != ps.rel_tone)) { // The current envelope and the tone being converted are different
            putAsm(fp, enmMCK.MCK_SET_TONE.v); // Release tone output
            putAsm(fp, ps.rel_tone);
            ps.last_written_tone = ps.rel_tone;
            note = enmMCK.MCK_WAIT.v; //Remaining time is waiting
        }
        if (note == enmMCK.MCK_WAIT.v && ps.self_delay >= 0 && ps.last_note[ps.self_delay] >= 0) {
            // Self Delay
            note = ps.last_note[ps.self_delay];
        }
        if (left_time != 0) {
            putAsm(fp, note);
            putLengthAndWait(fp, note, left_time, /* ref */ cmd);
        }
    }

    private void doNewBank(List<MmlDatum2> fp, int trk, /* ref */ CMD cmd) {
        int banktemp = curr_bank;
        if (cmd.param[0] == (int) PARAM_OMITTED) {
            // Defaults
            banktemp++;
        } else {
            banktemp = cmd.param[0];
        }

        if (checkBankRange(banktemp) == 0) {
            dispError(enmErrNum.BANK_IDX_OUT_OF_RANGE.ordinal(), cmd.filename, cmd.line);
            return;
        }
        if ((banktemp == 2 || banktemp == 3) && dpcm_bankswitch != 0) {
            dispError(enmErrNum.CANT_USE_BANK_2_OR_3_WITH_DPCMBANKSWITCH.ordinal(), cmd.filename, cmd.line);
            return;
        }
        putAsm(fp, enmMCK.MCK_GOTO.v);
        String t = String.format("bank(%s_%02d_bnk%03d)", songlabel, trk, banktemp);
        fp.add(new MmlDatum2(String.format("\n\tdb\t%s\n", t), -3, t));
        bank_usage[curr_bank]++;
        t = String.format("%s_%02d_bnk%03d", songlabel, trk, banktemp);
        fp.add(new MmlDatum2(String.format("\tdw\t%s\n", t), -3, t));
        bank_usage[curr_bank] += 2;

        t = String.format(".bank\tDATA_BANK+%d", banktemp);
        fp.add(new MmlDatum2(String.format("\n\t%s\n", t), -4, ".bank", "DATA_BANK", "+", banktemp));
        if ((banktemp & 1) != 0) {
            t = ".org\t$A000";
            fp.add(new MmlDatum2(String.format("\n\t%s\n", t), -4, t));
        } else {
            t = ".org\t$8000";
            fp.add(new MmlDatum2(String.format("\n\t%s\n", t), -4, t));
        }

        //	flogger.log(Level.TRACE, fp,"\n\t.bank\t%d\n",banktemp);
        curr_bank = banktemp;
        //	putBankOrigin(fp, curr_bank);
        t = String.format("%s_%02d_bnk%03d:", songlabel, trk, curr_bank);
        fp.add(new MmlDatum2(String.format("%s\n", t), -2, t));
        putAsm_pos = 0; // Clear output position
        return;
    }

    private int isCmdNotOutput(CMD[] cmd, int cmdPtr) {
        switch (enmMML.values()[cmd[cmdPtr].cmd]) {
            case _NOP:
            case _TEMPO:
            case _TEMPO2:
            case _OCTAVE:
            case _OCT_UP:
            case _OCT_DW:
            case _LENGTH:
            case _TRANSPOSE:
                return 1;
        }
        return 0;
    }

    private int isNextSlar(CMD[] cmd, int cmdPtr) {
        while (cmd[cmdPtr].cmd != enmMML._TRACK_END.v
                && isCmdNotOutput(cmd, cmdPtr) != 0) cmdPtr++;

        if (cmd[cmdPtr].cmd == enmMML._SLAR.v)
            return 1;

        return 0;
    }

    // Setting VOP when setting the tone
    private void putVOPData(List<MmlDatum2> fp, int trk, int param) {
        int opl3_head = opl4_track_num;

        // Not an OPL3 truck
        if (trk < opl3_head && trk >= opl3_head + OPL3_MAX)
            return;

        // Use the third number as a VOP
        int vop = opl3op_tbl[param][3];

        // Do not set if VOP is 0
        if (vop == 0)
            return;

        putAsm(fp, enmMCK.MDR_REVERB.v);
        putAsm(fp, vop & 0xff);
    }

    /**
     * Input:
     * <p>
     * Output:
     * none
     */
    private void developeData(List<MmlDatum2> fp, int trk, CMD[] cmdtop, LINE[] lptr) {
        tbase = 0.625;
        length = 48;
        volume_flag = -1;

        // Initialization
        {
            // Create a temporary work
            CMD[] cmd = cmdtop;
            int cmdPtr = 0;
            CMD[] temp = new CMD[32 * 1024]; // malloc(sizeof(CMD) * 32 * 1024);
            int tempPtr = 0;
            CMD[] tempback = temp;
            int tempbackPtr = 0;

            int i, j;
            for (i = 0; i < 32 * 1024; i++) {
                temp[tempPtr] = new CMD();
                temp[tempPtr].cmd = 0;
                temp[tempPtr].cnt = 0;
                temp[tempPtr].frm = 0;
                temp[tempPtr].line = 0;
                for (j = 0; j < 8; j++) {
                    temp[tempPtr].param[0] = 0;
                }
                tempPtr++;
            }

            tempPtr = tempbackPtr;
            // Analyze commands from the beginning of the channel data and store them in a buffer
            temp = analyzeData(trk, temp, /* ref */ tempPtr, lptr);
            setCommandBuf(0, temp, tempPtr, enmMML._TRACK_END.v, null, 0, 0, 1);
            tempPtr = tempbackPtr;
            //shuffleQuontize(temp, tempPtr);
            nest = 0;
            translateData(cmd, /* ref */ cmdPtr, temp, tempPtr);
            cmd = cmdtop;
            tempback = null;
        }

        tbase = 0.625;

        // MML Parsing
        {
            CMD[] cmd = cmdtop;
            int cmdPtr = 0;
            double count, lcount, count_t;
            int frame, lframe, frame_p, frame_d;
            double tbase_p;

            // Counts to Frames
            // Start from a round point as often as possible
            loop_flag = 0;

            count = 0; // Count from start of track
            frame = 0; // Number of frames elapsed since the start of the track
            lcount = 0; // The count from the start of the loop
            lframe = 0; // Number of frames elapsed since the start of the loop
                /*
                    The count is added regardless of the tempo.
                    The frame is
                                A t120 l4 c	d	 e	 f	t240	 g	 a	 b	 c	 !
                    count:					0 48	96 144	192	192 240 288 336 384
                    frame:					0 30	60	90	120	120 135 150 165 180
                    tbase:			0.625					 0.3125
                    count_t:				0 48	96 144	192	384 432 480 528 576
                                B t240 l4 cc dd ee ff					g	 a	 b	 c	 !
                */
            count_t = 0; //The number of counts required to elapse the same amount of time as the current state, assuming that the tempo has been the same from the beginning to the present
            do {
                cmd[cmdPtr].cnt = count;
                cmd[cmdPtr].frm = frame;
                cmd[cmdPtr].lcnt = lcount;
                cmd[cmdPtr].lfrm = lframe;

//logger.log(Level.TRACE, "%s:%d:%4x %f %d %f\n".formatted(cmd.filename, cmd.line, cmd.cmd, cmd.cnt, cmd.frm, cmd.len));

                if (cmd[cmdPtr].cmd == enmMML._REPEAT_ST2.v) {
                    double rcount = 0;
                    double rcount_esc = 0; // Up to just before '¥'
                    double rcount_t = 0;
                    double rcount_esc_t = 0;
                    int rframe = 0;
                    int rframe_esc = 0;
                    int rframe_err;
                    int repeat_esc_flag = 0;
                    CMD[] repeat_esc2_cmd_ptr = null;
                    int repeat_esc2_cmd_ptrPtr = -1;

                    cmdPtr++;
                    while (true) {
                        cmd[cmdPtr].cnt = count;
                        cmd[cmdPtr].frm = frame;
                        cmd[cmdPtr].lcnt = lcount;
                        cmd[cmdPtr].lfrm = lframe;
                        if (cmd[cmdPtr].cmd == enmMML._REPEAT_END2.v) {
                            count_t += rcount_t * (cmd[cmdPtr].param[0] - 2) + rcount_esc_t;
                            count += rcount * (cmd[cmdPtr].param[0] - 2) + rcount_esc;
                            frame += rframe * (cmd[cmdPtr].param[0] - 2) + rframe_esc;
                            if (loop_flag != 0) {
                                lcount += rcount * (cmd[cmdPtr].param[0] - 2) + rcount_esc;
                                lframe += rframe * (cmd[cmdPtr].param[0] - 2) + rframe_esc;
                            }
                            // Frame Compensation
                            rframe_err = double2int(count_t * tbase) - frame; // (count_t * tbase)-frame
                            //logger.log(Level.TRACE, "frame-error: %d frame\n", rframe_err );
                            if (rframe_err > 0) {
                                //logger.log(Level.TRACE, "frame-correct: %d frame\n", rframe_err );
                                if (rframe_err >= 3) {
                                    dispWarning(enmSys.REPEAT2_FRAME_ERROR_OVER_3.ordinal(), cmd[cmdPtr].filename, cmd[cmdPtr].line);
                                }
//                                // 2004.09.02 stop after all
//                                cmd.param[1] = rframe_err;
//                                frame += rframe_err;
//                                if (loop_flag != 0) {
//                                    lframe += rframe_err;
//                                }
                            } else {
                                cmd[cmdPtr].param[1] = 0;
                            }
                            if (repeat_esc_flag != 0) {
                                // Repeat count is also set to the corresponding '¥¥' command.
                                repeat_esc2_cmd_ptr[repeat_esc2_cmd_ptrPtr].param[0] = cmd[cmdPtr].param[0];
                            }
                            break;

                        } else if (cmd[cmdPtr].cmd == enmMML._REPEAT_ESC2.v) {
                            repeat_esc_flag = 1;
                            repeat_esc2_cmd_ptr = cmd;
                            repeat_esc2_cmd_ptrPtr = cmdPtr;
                        } else if (
                                cmd[cmdPtr].cmd <= MAX_NOTE ||
                                        cmd[cmdPtr].cmd == enmMML._REST.v ||
                                        cmd[cmdPtr].cmd == enmMML._DRUM_BIT.v ||
                                        cmd[cmdPtr].cmd == enmMML._DRUM_NOTE.v ||
                                        cmd[cmdPtr].cmd == enmMML._TIE.v ||
                                        cmd[cmdPtr].cmd == enmMML._KEY.v ||
                                        cmd[cmdPtr].cmd == enmMML._NOTE.v ||
                                        cmd[cmdPtr].cmd == enmMML._WAIT.v ||
                                        cmd[cmdPtr].cmd == enmMML._KEY_OFF.v) {
                            count_t += cmd[cmdPtr].len;
                            rcount_t += cmd[cmdPtr].len;
                            frame_p = rframe;
                            rframe = double2int(rcount_t * tbase);
                            //(rcount_t * tbase);
                            frame_d = rframe - frame_p;
                            count += cmd[cmdPtr].len;
                            frame += frame_d;
                            // Measures against loop misalignment
                            if (loop_flag != 0) {
                                lcount += cmd[cmdPtr].len;
                                lframe += frame_d;
                            }
                            rcount += cmd[cmdPtr].len;
                            if (repeat_esc_flag == 0) {
                                rcount_esc_t += cmd[cmdPtr].len;
                                rcount_esc += cmd[cmdPtr].len;
                                rframe_esc += frame_d;
                            }
                        } else if (cmd[cmdPtr].cmd == enmMML._TEMPO.v) {
                            tbase_p = tbase;
                            tbase = (double) _BASETEMPO / (double) cmd[cmdPtr].param[0];
                            count_t = count_t * tbase / tbase_p;
                            rcount_t = rcount_t * tbase / tbase_p;
                            rcount_esc_t = rcount_esc_t * tbase / tbase_p;
                        } else if (cmd[cmdPtr].cmd == enmMML._TEMPO2.v) {
                            tbase_p = tbase;
                            tbase = (double) cmd[cmdPtr].param[0] * (double) cmd[cmdPtr].param[1] / _BASE;
                            count_t = count_t * tbase / tbase_p;
                            rcount_t = rcount_t * tbase / tbase_p;
                            rcount_esc_t = rcount_esc_t * tbase / tbase_p;
                        } else if (cmd[cmdPtr].cmd == enmMML._SONG_LOOP.v) {
                            loop_flag = 1;
                        }
                        cmdPtr++;
                    }
                } else if (
                        cmd[cmdPtr].cmd <= MAX_NOTE ||
                                cmd[cmdPtr].cmd == enmMML._DRUM_BIT.v ||
                                cmd[cmdPtr].cmd == enmMML._DRUM_NOTE.v ||
                                cmd[cmdPtr].cmd == enmMML._REST.v ||
                                cmd[cmdPtr].cmd == enmMML._TIE.v ||
                                cmd[cmdPtr].cmd == enmMML._KEY.v ||
                                cmd[cmdPtr].cmd == enmMML._NOTE.v ||
                                cmd[cmdPtr].cmd == enmMML._WAIT.v ||
                                cmd[cmdPtr].cmd == enmMML._KEY_OFF.v) {
                    count_t += cmd[cmdPtr].len;
                    frame_p = frame;
                    frame = double2int(count_t * tbase);
                    frame_d = frame - frame_p;
                    count += cmd[cmdPtr].len;
                    // Measures against loop misalignment
                    if (loop_flag != 0) {
                        lcount += cmd[cmdPtr].len;
                        lframe += frame_d;
                    }
                } else if (cmd[cmdPtr].cmd == enmMML._TEMPO.v) {
                    tbase_p = tbase;
                    tbase = (double) _BASETEMPO / (double) cmd[cmdPtr].param[0];
                    count_t = count_t * tbase_p / tbase;
                } else if (cmd[cmdPtr].cmd == enmMML._TEMPO2.v) {
                    tbase_p = tbase;
                    tbase = (double) cmd[cmdPtr].param[0] * (double) cmd[cmdPtr].param[1] / _BASE;
                    count_t = count_t * tbase_p / tbase;
                } else if (cmd[cmdPtr].cmd == enmMML._SONG_LOOP.v) {
                    loop_flag = 1;
                }
            } while (cmd[cmdPtr++].cmd != enmMML._TRACK_END.v);
        }

        // Expand
        {
            CMD[] cmd = cmdtop;
            int cmdPtr = 0;
            PLAYSTATE ps = new PLAYSTATE();
            int repeat_depth = 0;
            int repeat_index = 0;
            int repeat_esc_flag = 0;
            int i;
            String loop_point_label; // =new char[256];

            int drum_note_flag = 0;
            int drum_note_count = 0;

            defaultPlayState(new PLAYSTATE[] {ps}, 0);

            cmd = cmdtop;
            putAsm_pos = 0;
            loop_flag = 0;

            loop_point_label = String.format("%d_%02d_lp", songlabel, trk);

            mml_trk = trk;
            String t = String.format("%d_%02d:", songlabel, trk);
            fp.add(new MmlDatum2(String.format("\n%d\t;Trk %d\n", t, str_track.charAt(trk)), -2, t));

            mml_file_name = cmd[cmdPtr].filename;
            mml_line_pos = cmd[cmdPtr].line;

            // Jump
            if (use_jump != 0) {
                fp.add(new MmlDatum2("\n;jump\n", 0));

                putAsm(fp, enmMCK.MDR_JUMP.v);
                putAsm(fp, 0x01);
                use_jump = 0;
            }
//#if !HUSIC_EXT
//                // Triangular wave/noise track countermeasures
//                if ((trk == BTRACK(2)) || (trk == BTRACK(3))) {
//                    putAsm(fp, enmMCK.MCK_SET_TONE.v);
//                    putAsm(fp, 0x8f);
//                }
//#endif

            do {
                int cmdtempPtr = cmdPtr; // Since the cmd pointer may advance within each switch, save it once.
                mml_file_name = cmd[cmdPtr].filename;
                mml_line_pos = cmd[cmdPtr].line;

                // Automatic Bank Switching
                if (auto_bankswitch != 0) {
                    int bank_limit = 8192 - 20; // Allow adequate leeway
                    if (bank_usage[curr_bank] > bank_limit) {
                        CMD nbcmd = new CMD();
                        nbcmd.param[0] = curr_bank;
                        while (bank_usage[nbcmd.param[0]] > bank_limit) {
                            nbcmd.param[0]++;
                        }
                        nbcmd.filename = cmd[cmdPtr].filename;
                        nbcmd.line = cmd[cmdPtr].line;
                        doNewBank(fp, trk, /* ref */ nbcmd);
                    }
                }

                switch (enmMML.values()[cmd[cmdtempPtr].cmd]) {
                    case _NOP:
                    case _TEMPO:
                    case _TEMPO2:
                    case _OCTAVE:
                    case _OCT_UP:
                    case _OCT_DW:
                    case _LENGTH:
                    case _TRANSPOSE:
                        cmdPtr++;
                        break;
                    case _SLAR:
                        putAsm(fp, enmMCK.MCK_SLAR.v);
                        cmdPtr++;
                        break;
                    case _ENVELOPE:
                        putAsm(fp, enmMCK.MCK_SET_VOL.v);
                        ps.env = cmd[cmdPtr].param[0] & 0x7f;
                        ps.last_written_env = ps.env;
                        putAsm(fp, ps.env);
                        ps.last_written_env = ps.env;
                        cmdPtr++;
                        break;
                    case _REL_ENV:
                        if (cmd[cmdPtr].param[0] == 255) {
                            ps.rel_env = -1;
                        } else {
                            ps.rel_env = cmd[cmdPtr].param[0] & 0x7f;
                        }
                        cmdPtr++;
                        break;
                    case _VOLUME:
                        putAsm(fp, enmMCK.MCK_SET_VOL.v);
                        ps.env = (cmd[cmdPtr].param[0] & 0x7f) | 0x80;
                        putAsm(fp, ps.env);
                        ps.last_written_env = ps.env;
                        cmdPtr++;
                        break;
                    case _HARD_ENVELOPE:
                        putAsm(fp, enmMCK.MCK_SET_FDS_HWENV.v);
                        ps.env = ((cmd[cmdPtr].param[0] & 1) << 6) | (cmd[cmdPtr].param[1] & 0x3f);
                        putAsm(fp, (ps.env & 0xff));
                        ps.last_written_env = ps.env;
                        cmdPtr++;
                        break;
                    case _TONE:
                        ps.tone = cmd[cmdPtr].param[0] | 0x80;
                        putAsm(fp, enmMCK.MCK_SET_TONE.v);
                        putAsm(fp, ps.tone);
                        ps.last_written_tone = ps.tone;
                        cmdPtr++;
                        break;
                    case _ORG_TONE:
                        ps.tone = cmd[cmdPtr].param[0] & 0x7f;
                        putAsm(fp, enmMCK.MCK_SET_TONE.v);
                        putAsm(fp, ps.tone);
                        ps.last_written_tone = ps.tone;
                        cmdPtr++;
                        break;
                    case _REL_ORG_TONE:
                        if (cmd[cmdPtr].param[0] == 255) {
                            ps.rel_tone = -1;
                        } else {
                            ps.rel_tone = cmd[cmdPtr].param[0] & 0x7f;
                        }
                        cmdPtr++;
                        break;
                    case _SONG_LOOP:
                        //loop_count.cnt = cmd.cnt; // LEN
                        //loop_count.frm = cmd.frm;
                        t = String.format("%s:", loop_point_label);
                        fp.add(new MmlDatum2(String.format("\n%s\n", t), -2, t));
                        loop_flag = 1;
                        putAsm_pos = 0;
                        cmdPtr++;
                        break;
                    case _QUONTIZE:
                        ps.gate_q.rate = cmd[cmdPtr].param[0];
                        ps.gate_q.adjust = cmd[cmdPtr].param[1];
                        cmdPtr++;
                        break;
                    case _QUONTIZE2:
                        ps.gate_q.rate = gate_denom;
                        ps.gate_q.adjust = -cmd[cmdPtr].param[0];
                        cmdPtr++;
                        break;
                    case _DRUM_NOTE: {
                        if (drum_note_flag != 0) {
                            dispError(enmErrNum.COMMAND_REDUNDANT.ordinal(), cmd[cmdtempPtr].filename, cmd[cmdtempPtr].line);
                            cmdPtr++;
                            break;

                        }
                        drum_note_flag = 1;
                        drum_note_count = 0;
                        putAsm(fp, enmMCK.MDR_DRUM_NOTE.v);
                        putAsm(fp, cmd[cmdPtr].param[0] & 0x1f);
                        cmdPtr++;
                    }
                    break;
                    case _DRUM_BIT: {
                        if (cmd[cmdPtr].len == 0) {
                            putAsm(fp, enmMCK.MDR_DRUM_BIT.v);
                            putAsm(fp, cmd[cmdPtr].param[0] & 0x1f);
                            cmdPtr++;
                        } else {
                            int param = cmd[cmdPtr].param[0];
                            int delta_time = 0;
                            cmdPtr = getDeltaTime(cmd, cmdPtr, /* ref */ delta_time, 0);
                            if (delta_time == 0) {
                                dispWarning(enmSys.FRAME_LENGTH_IS_0.ordinal(), cmd[cmdtempPtr].filename, cmd[cmdtempPtr].line);
                                break;
                            }
                            putAsm(fp, enmMCK.MDR_DRUM_BIT.v);
                            putAsm(fp, (param & 0x1f) | 0x80);
                            putLengthAndWait(fp, enmMCK.MCK_WAIT.v, delta_time, /* ref */ cmd[cmdtempPtr]);
                        }
                    }
                    break;

                    case _REST: {
                        int delta_time = 0;
                        cmdPtr = getDeltaTime(cmd, cmdPtr, /* ref */ delta_time, 0);
                        if (delta_time == 0) {
                            dispWarning(enmSys.FRAME_LENGTH_IS_0.ordinal(), cmd[cmdtempPtr].filename, cmd[cmdtempPtr].line);
                            break;
                        }
                        putAsm(fp, enmMCK.MCK_REST.v);
                        putLengthAndWait(fp, enmMCK.MCK_REST.v, delta_time, /* ref */ cmd[cmdtempPtr]);
                        ps.key_pressed = 0;
                    }
                    break;
                    case _WAIT: {
                        int delta_time = 0;
                        cmdPtr = getDeltaTime(cmd, cmdPtr, /* ref */ delta_time, 0);
                        if (delta_time == 0) {
                            dispWarning(enmSys.FRAME_LENGTH_IS_0.ordinal(), cmd[cmdtempPtr].filename, cmd[cmdtempPtr].line);
                            break;
                        }
                        putAsm(fp, enmMCK.MCK_WAIT.v);
                        putLengthAndWait(fp, enmMCK.MCK_WAIT.v, delta_time, /* ref */ cmd[cmdtempPtr]);
                    }
                    break;
                    case _KEY_OFF:  { // Key off with length
                        int delta_time = 0;
                        cmdPtr = getDeltaTime(cmd, cmdPtr, /* ref */ delta_time, 0);
                        if (delta_time == 0) {
                            // Allow note length 0
                        }
                        putReleaseEffect(fp, delta_time, /* ref */ cmd[cmdtempPtr], /* ref */ ps);
                        ps.key_pressed = 0;
                    }
                    break;
                    case _LFO_ON:
                        putAsm(fp, enmMCK.MCK_SET_LFO.v);
                        if ((cmd[cmdPtr].param[0] & 0xff) == 0xff) {
                            putAsm(fp, 0xff);
                        } else {
                            putAsm(fp, cmd[cmdPtr].param[0] & 0x7f);
                        }
                        cmdPtr++;
                        break;
                    case _LFO_OFF:
                        putAsm(fp, enmMCK.MCK_SET_LFO.v);
                        putAsm(fp, 0xff);
                        cmdPtr++;
                        break;
                    case _DETUNE:
                        putAsm(fp, enmMCK.MCK_SET_DETUNE.v);
                        if (cmd[cmdPtr].param[0] >= 0) {
                            putAsm(fp, (cmd[cmdPtr].param[0] & 0x7f) | 0x80);
                        } else {
                            putAsm(fp, (-cmd[cmdPtr].param[0]) & 0x7f);
                        }
                        cmdPtr++;
                        break;
                    case _SWEEP:
                        putAsm(fp, enmMCK.MCK_SET_HWSWEEP.v);
                        putAsm(fp, ((cmd[cmdPtr].param[0] & 0xf) << 4) + (cmd[cmdPtr].param[1] & 0xf));
                        cmdPtr++;
                        break;
                    case _EP_ON:
                        putAsm(fp, enmMCK.MCK_SET_PITCHENV.v);
                        putAsm(fp, cmd[cmdPtr].param[0] & 0xff);
                        cmdPtr++;
                        break;
                    case _EP_OFF:
                        putAsm(fp, enmMCK.MCK_SET_PITCHENV.v);
                        putAsm(fp, 0xff);
                        cmdPtr++;
                        break;
                    case _EN_ON:
                        putAsm(fp, enmMCK.MCK_SET_NOTEENV.v);
                        putAsm(fp, cmd[cmdPtr].param[0] & 0xff);
                        cmdPtr++;
                        break;
                    case _EN_OFF:
                        putAsm(fp, enmMCK.MCK_SET_NOTEENV.v);
                        putAsm(fp, 0xff);
                        cmdPtr++;
                        break;
                    case _MH_ON:
                        putAsm(fp, enmMCK.MCK_SET_FDS_HWEFFECT.v);
                        putAsm(fp, cmd[cmdPtr].param[0] & 0xff);
                        cmdPtr++;
                        break;
                    case _MH_OFF:
                        putAsm(fp, enmMCK.MCK_SET_FDS_HWEFFECT.v);
                        putAsm(fp, 0xff);
                        cmdPtr++;
                        break;
                    case _VRC7_TONE:
                        putAsm(fp, enmMCK.MCK_SET_TONE.v);
                        putAsm(fp, cmd[cmdPtr].param[0] | 0x40);
                        cmdPtr++;
                        break;

                    // HuSIC
                    case _FMLFO_FRQ:
                        putAsm(fp, 0xec);
                        putAsm(fp, cmd[cmdPtr].param[0] & 0xff);
                        cmdPtr++;
                        break;
                    case _FMLFO_SET:
                        putAsm(fp, 0xed);
                        putAsm(fp, cmd[cmdPtr].param[0] & 0xff);
                        cmdPtr++;
                        break;
                    case _FMLFO_OFF:
                        putAsm(fp, 0xed);
                        putAsm(fp, 0xff);
                        cmdPtr++;
                        break;
                    case _L_PAN:
                        panvol = (panvol & 0x0f) | ((cmd[cmdPtr].param[0] & 0x0f) << 4);
                        putAsm(fp, 0xf0);
                        putAsm(fp, panvol);
                        cmdPtr++;
                        break;
                    case _R_PAN:
                        panvol = (panvol & 0xf0) | (cmd[cmdPtr].param[0] & 0x0f);
                        putAsm(fp, 0xf0);
                        putAsm(fp, panvol);
                        cmdPtr++;
                        break;
                    case _C_PAN:
                        panvol = (cmd[cmdPtr].param[0] & 0x0f) | ((cmd[cmdPtr].param[0] & 0x0f) << 4);
                        putAsm(fp, 0xf0);
                        putAsm(fp, panvol);
                        cmdPtr++;
                        break;
                    case _PAN:
                        panvol = cmd[cmdPtr].param[0];
                        putAsm(fp, 0xf0);
                        putAsm(fp, panvol);
                        cmdPtr++;
                        break;
                    case _NOISE_SW:
                        putAsm(fp, 0xf2);
                        putAsm(fp, cmd[cmdPtr].param[0]);
                        cmdPtr++;
                        break;
                    case _WAVE_CHG:
                        putAsm(fp, 0xf1);
                        putAsm(fp, cmd[cmdPtr].param[0]);
                        // Exporting VOP Settings
                        putVOPData(fp, trk, cmd[cmdPtr].param[0] & 0xff);
                        cmdPtr++;

                        break;
                    case _MODE_CHG:
                        putAsm(fp, 0xef);
                        putAsm(fp, cmd[cmdPtr].param[0]);
                        cmdPtr++;
                        break;

                    // MoonDriver
                    case _JUMP_FLAG:
                        putAsm(fp, enmMCK.MDR_JUMP.v);
                        putAsm(fp, 0x00);
                        cmdPtr++;
                        break;
                    case _REVERB_SET:
                        putAsm(fp, enmMCK.MDR_REVERB.v);
                        putAsm(fp, cmd[cmdPtr].param[0] & 0xff);
                        cmdPtr++;
                        break;
                    case _DAMP_SET:
                        putAsm(fp, enmMCK.MDR_DAMP.v);
                        putAsm(fp, cmd[cmdPtr].param[0] & 0xff);
                        cmdPtr++;
                        break;

                    case _SET_OPBASE:
                        putAsm(fp, enmMCK.MDR_OPBASE.v);
                        putAsm(fp, cmd[cmdPtr].param[0] & 0xff);
                        cmdPtr++;
                        break;
                    case _LOAD_OP2:
                        putAsm(fp, enmMCK.MDR_LDOP2.v);
                        putAsm(fp, cmd[cmdPtr].param[0] & 0xff);
                        // Exporting VOP Settings
                        putVOPData(fp, trk, cmd[cmdPtr].param[0] & 0xff);
                        cmdPtr++;

                        break;
                    case _SET_TVP:
                        putAsm(fp, enmMCK.MDR_TVP.v);
                        putAsm(fp, cmd[cmdPtr].param[0] & 0xff);
                        cmdPtr++;
                        break;
                    case _DRUM_SW:
                        putAsm(fp, enmMCK.MDR_DRUM.v);
                        putAsm(fp, cmd[cmdPtr].param[0] & 0xff);
                        cmdPtr++;
                        break;
                    case _SET_FBS:
                        putAsm(fp, enmMCK.MDR_FBS.v);
                        putAsm(fp, cmd[cmdPtr].param[0] & 0xff);
                        cmdPtr++;
                        break;
                    case _SET_OPM:
                        putAsm(fp, enmMCK.MDR_OPMODE.v);
                        putAsm(fp, cmd[cmdPtr].param[0] & 0xff);
                        cmdPtr++;
                        break;


                    case _SUN5B_HARD_SPEED:
                        putAsm(fp, enmMCK.MCK_SET_SUN5B_HARD_SPEED.v);
                        putAsm(fp, cmd[cmdPtr].param[0] & 0xff);
                        putAsm(fp, (cmd[cmdPtr].param[0] >> 8) & 0xff);
                        cmdPtr++;
                        break;
                    case _SUN5B_HARD_ENV:
                        putAsm(fp, enmMCK.MCK_SUN5B_HARD_ENV.v);
                        ps.env = (cmd[cmdPtr].param[0] & 0x0f) | 0x10 | 0x80;
                        putAsm(fp, ps.env);
                        cmdPtr++;
                        break;
                    case _SUN5B_NOISE_FREQ:
                        putAsm(fp, enmMCK.MCK_SET_SUN5B_NOISE_FREQ.v);
                        putAsm(fp, cmd[cmdPtr].param[0] & 0x1f);
                        cmdPtr++;
                        break;
                    case _NEW_BANK:
                        doNewBank(fp, trk, /* ref */ cmd[cmdPtr]);
                        cmdPtr++;
                        break;
                    case _DATA_WRITE:
                        putAsm(fp, enmMCK.MCK_DATA_WRITE.v);
                        putAsm(fp, cmd[cmdPtr].param[0] & 0xff);
                        putAsm(fp, (cmd[cmdPtr].param[0] >> 8) & 0xff);
                        putAsm(fp, cmd[cmdPtr].param[1] & 0xff);
                        cmdPtr++;
                        break;
                    case _DATA_WRITE_OFS: {
                        int sel = 0x00;
                        int addr = (cmd[cmdPtr].param[0] & 0xff);
                        int opl3_head = opl4_track_num;
                        int data = cmd[cmdPtr].param[1];

                        // Within OPL3 track range
                        if (trk >= opl3_head && trk < opl3_head + OPL3_MAX) {
                            // tmptrk = 0 - 17
                            int tmptrk = trk - opl3_head;

                            int opl_half = (OPL3_MAX / 2);

                            if (tmptrk < opl_half) {
                                sel = 0x01; // first half
                            } else {
                                sel = 0x02; // second half
                                tmptrk -= opl_half;
                            }

                            // Certain addresses undergo special translations
                            if ((addr >= 0x20 && addr < 0xa0) ||
                                    (addr >= 0xe0 && addr < 0x100)) {
                                tmptrk = ((tmptrk / 3) * 8) + (tmptrk % 3);
                            }
                            addr += tmptrk;

                        }

                        putAsm(fp, enmMCK.MCK_DATA_WRITE.v);
                        putAsm(fp, addr & 0xff);
                        putAsm(fp, sel & 0xff);
                        putAsm(fp, data & 0xff);
                        cmdPtr++;
                    }
                    break;

                    case _DATA_THRUE:
                        putAsm(fp, cmd[cmdPtr].param[0] & 0xff);
                        putAsm(fp, cmd[cmdPtr].param[1] & 0xff);
                        cmdPtr++;
                        break;
                    case _REPEAT_ST2:
                        t = String.format("%s_%02d_lp_%04d:", songlabel, trk, repeat_index);
                        fp.add(new MmlDatum2(String.format("\n%s\n", t), -2, t));
                        repeat_depth++;
                        putAsm_pos = 0;
                        cmdPtr++;
                        break;
                    case _REPEAT_END2:
                        if (--repeat_depth < 0) {
                            dispError(enmErrNum.DATA_ENDED_BY_LOOP_DEPTH_EXCEPT_0.ordinal(), cmd[cmdPtr].filename, cmd[cmdPtr].line);
                        } else {
                            if (repeat_esc_flag != 0) {
                                // Always back
                                putAsm(fp, enmMCK.MCK_GOTO.v);
                            } else {
                                putAsm(fp, enmMCK.MCK_REPEAT_END.v);
                                putAsm(fp, cmd[cmdPtr].param[0] & 0x7f);
                            }
                            t = String.format("bank(%s_%02d_lp_%04d)", songlabel, trk, repeat_index);
                            fp.add(new MmlDatum2(String.format("\n\tdb\t%s\n", t), -3, t));
                            bank_usage[curr_bank]++;
                            t = String.format("%s_%02d_lp_%04d", songlabel, trk, repeat_index);
                            fp.add(new MmlDatum2(String.format("\tdw\t%s\n", t), -3, t));
                            bank_usage[curr_bank] += 2;

                            t = String.format("%s_%02d_lp_exit_%04d:", songlabel, trk, repeat_index);
                            fp.add(new MmlDatum2(String.format("%s\n", t), -2, t));
                            repeat_index++;
                            putAsm_pos = 0;
//                            // 2004.09.02 stop after all
//                            if (cmd.param[1] > 0) {
//                                putAsm(fp, MCK_WAIT);
//                                putAsm(fp, cmd.param[1] & 0xFF);
//                            }
                            if (repeat_esc_flag != 0) {
                                for (i = 0; i < ps.last_note.length; i++) {
                                    ps.last_note[i] = ps.last_note_keep[i];
                                }
                                repeat_esc_flag = 0;
                            }
                        }
                        cmdPtr++;
                        break;
                    case _REPEAT_ESC2:
                        if ((repeat_depth - 1) < 0) {
                            dispError(enmErrNum.DATA_ENDED_BY_LOOP_DEPTH_EXCEPT_0.ordinal(), cmd[cmdPtr].filename, cmd[cmdPtr].line);
                        } else {
                            putAsm(fp, enmMCK.MCK_REPEAT_ESC.v);
                            putAsm(fp, cmd[cmdPtr].param[0] & 0x7f);
                            t = String.format("bank(%s_%02d_lp_exit_%04d)", songlabel, trk, repeat_index);
                            fp.add(new MmlDatum2(String.format("\n\tdb\t%s\n", t), -3, t));
                            bank_usage[curr_bank]++;
                            t = String.format("%s_%02d_lp_exit_%04d", songlabel, trk, repeat_index);
                            fp.add(new MmlDatum2(String.format("\tdw\t%s\n", t), -3, t));
                            bank_usage[curr_bank] += 2;
                            putAsm_pos = 0;
                            repeat_esc_flag = 1;
                            for (i = 0; i < ps.last_note.length; i++) {
                                ps.last_note_keep[i] = ps.last_note[i];
                            }
                        }
                        cmdPtr++;
                        break;
                    case _SELF_DELAY_ON:
                        if (cmd[cmdPtr].param[0] == 255) {
                            ps.self_delay = -1;
                        } else {
                            ps.self_delay = cmd[cmdPtr].param[0];
                        }
                        cmdPtr++;
                        break;
                    case _SELF_DELAY_OFF:
                        ps.self_delay = -1;
                        cmdPtr++;
                        break;
                    case _SELF_DELAY_QUEUE_RESET:
                        for (i = 0; i < ps.last_note.length; i++) {
                            ps.last_note[i] = -1;
                            ps.last_note_keep[i] = -1;
                        }
                        cmdPtr++;
                        break;
                    case _SHIFT_AMOUNT:
                        putAsm(fp, enmMCK.MCK_SET_SHIFT_AMOUNT.v);
                        putAsm(fp, cmd[cmdPtr].param[0] & 0xff);
                        cmdPtr++;
                        break;
                    case _TRACK_END:
                        break;
                    case _KEY:
                    default: {
                        int note;
                        int delta_time; // Number of frames from the sound to the next event
                        int gate_time; // Number of frames from onset to key-off
                        int left_time; // Number of frames remaining from key-off to the next event
                        GATE_Q temp_gate = new GATE_Q();

                        if (cmd[cmdtempPtr].cmd == enmMML._KEY.v) {
                            note = cmd[cmdPtr].param[0] & 0xffff;
                        } else {
                            note = cmd[cmdtempPtr].cmd;
                            if (note < MIN_NOTE || MAX_NOTE < note) {
                                dispError(enmErrNum.COMMAND_NOT_DEFINED.ordinal(), cmd[cmdPtr].filename, cmd[cmdPtr].line);
                                cmdPtr++;
                                break;
                            }
                        }

                        delta_time = 0;
                        cmdPtr = getDeltaTime(cmd, cmdPtr, /* ref */ delta_time, 1);

                        // Ignore gate times for slurs
                        if (isNextSlar(cmd, cmdPtr) != 0) {
                            temp_gate.rate = 8;
                            temp_gate.adjust = 0;
                            gate_time = calcGateTime(delta_time, /* ref */ temp_gate);
                        } else
                            gate_time = calcGateTime(delta_time, /* ref */ ps.gate_q);

                        left_time = delta_time - gate_time;

                        if (delta_time == 0) {
                            dispWarning(enmSys.FRAME_LENGTH_IS_0.ordinal(), cmd[cmdtempPtr].filename, cmd[cmdtempPtr].line);
                            break;
                        }


                        if (ps.last_written_env != ps.env) { // The last written envelope or volume is different from the current normal envelope or volume.
//                            if ((trk == BFMTRACK) && (ps.env > 0xff)) {
//                                putAsm(fp, MCK_SET_FDS_HWENV); // Hard Envelope Output
//                                putAsm(fp, (ps.env & 0xff));
//                            } else
                            {
                                putAsm(fp, enmMCK.MCK_SET_VOL.v); // Envelope Output
                                putAsm(fp, ps.env);
                            }
                            ps.last_written_env = ps.env;
                        }

                        if (ps.last_written_tone != ps.tone) { // The last tone I wrote is different from the current default tone
                            putAsm(fp, enmMCK.MCK_SET_TONE.v); // Tone Output
                            putAsm(fp, ps.tone);
                            ps.last_written_tone = ps.tone;
                        }

                        if ((ps.tone == -1) &&
                                ((trk == BTRACK(0)) || (trk == BTRACK(1)) ||
                                        (trk == BMMC5TRACK()) || (trk == BMMC5TRACK() + 1))) {
                            // Built-in square wave & MMC5 are @0 when no tone is specified
                            putAsm(fp, enmMCK.MCK_SET_TONE.v);
                            ps.tone = 0x80;
                            putAsm(fp, ps.tone);
                            ps.last_written_tone = ps.tone;
                        }

                        if (cmd[cmdtempPtr].cmd == enmMML._KEY.v) {
                            putAsm(fp, enmMCK.MCK_DIRECT_FREQ.v);
                            putAsm(fp, note & 0xff);
                            if (((trk >= BVRC6TRACK()) && (trk <= BVRC6SAWTRACK())) ||
                                    ((trk >= BFME7TRACK()) && (trk <= BFME7TRACK() + 2))) {
                                // VRC6 & SUN5B are 12bit
                                putAsm(fp, (note >> 8) & 0x0f);
                            } else {
                                // 2A03 & MMC5 is 11bit
                                putAsm(fp, (note >> 8) & 0x07);
                            }
                        } else {
                            if (note < 0) { // Measures for the lowest note
                                note += 16;
                            }
                            putAsm(fp, note);

                            for (i = ps.last_note.length - 1; i > 0; i--) {
                                ps.last_note[i] = ps.last_note[i - 1];
                            }
                            ps.last_note[0] = note;
                        }

                        putLengthAndWait(fp, enmMCK.MCK_WAIT.v, gate_time, /* ref */ cmd[cmdtempPtr]);
                        ps.key_pressed = 1;

                        // Quantize Processing
                        if (left_time != 0) {
                            putReleaseEffect(fp, left_time, /* ref */ cmd[cmdtempPtr], /* ref */ ps);
                            ps.key_pressed = 0;
                        }

                        drum_note_flag = 0;
                    }
                    break;
                } // switch (cmdtemp.cmd)

                if (drum_note_flag != 0)
                    drum_note_count++;

                putAsmFlash(fp);

            } while (cmd[cmdPtr].cmd != enmMML._TRACK_END.v);

            track_count[mml_idx][trk][0].cnt = cmd[cmdPtr].cnt;
            track_count[mml_idx][trk][0].frm = cmd[cmdPtr].frm;

            if (loop_flag == 0) {
                track_count[mml_idx][trk][1].cnt = 0;
                track_count[mml_idx][trk][1].frm = 0;

                t = String.format("%s:", loop_point_label);
                fp.add(new MmlDatum2(String.format("\n%s\n", t), -2, t));
                putAsm_pos = 0;
                putAsm(fp, enmMCK.MCK_REST.v);
                putAsm(fp, 0xff);
            } else {
                track_count[mml_idx][trk][1].cnt = cmd[cmdPtr].lcnt;
                track_count[mml_idx][trk][1].frm = cmd[cmdPtr].lfrm;
            }

            // putAsm( fp, MCK_DATA_END );
            putAsm(fp, enmMCK.MCK_GOTO.v);
            fp.add(new MmlDatum2(String.format("\n\tdb\tbank(%s)\n", loop_point_label), -3, String.format("bank(%d)", loop_point_label)));
            bank_usage[curr_bank]++;
            fp.add(new MmlDatum2(String.format("\tdw\t%s\n", loop_point_label), -3, loop_point_label));
            bank_usage[curr_bank] += 2;
            fp.add(new MmlDatum2("\n", 0));
        }
    }

    /** */
    private void setSongLabel() {
        songlabel = String.format("song_%03d", mml_idx);
    }

    /**
     * Result display routine
     * i:trk number
     * trk: track symbol
     */
    private void display_counts_sub(int i, char trk) {
        String msg = "";
        msg = String.format("   %s   |", trk);
        if (track_count[mml_idx][i][0].cnt != 0) {
            msg += String.format(" %6d   %5d|", double2int(track_count[mml_idx][i][0].cnt), track_count[mml_idx][i][0].frm);
        } else {
            msg += "               |";
        }
        if (track_count[mml_idx][i][1].cnt != 0) {
            msg += String.format(" %6d   %5d|", double2int(track_count[mml_idx][i][1].cnt), track_count[mml_idx][i][1].frm);
        } else {
            msg += "               |";
        }
        logger.log(Level.INFO, msg);
    }

    /**
     * Data Creation Routine
     * Input:
     * none
     * Return:
     * ==0: Normal !=0: Abnormal
     */
    public int data_make() {
        int i, j, track_ptr;
        int tone_max, envelope_max, pitch_env_max, pitch_mod_max;
        int arpeggio_max, fm_tone_max, dpcm_max, n106_tone_max, vrc7_tone_max;
        int hard_effect_max, effect_wave_max;

        int wtb_tone_max, xpcm_max; // HuSIC
        int tonetbl_max;
        int opl3tbl_max;

        String t;
        byte b;
        List<MmlDatum2> efFp = new ArrayList<>();
        List<MmlDatum2> oufp = new ArrayList<>();
        List<MmlDatum2> infp = new ArrayList<>();

        LINE[][] line_ptr = new LINE[Work.MML_MAX][];
        CMD[] cmd_buf;
        int[] trk_flag = new int[_TRACK_MAX];

        for (i = 0; i < _TRACK_MAX; i++) {
            bank_sel[i] = -1; // The initial state is none
        }

        for (i = 0; i < _DPCM_MAX; i++) {
            dpcm_tbl[i] = new DPCMTBL();
            dpcm_tbl[i].flag = 0;
            dpcm_tbl[i].index = -1;
        }

        // Load effects from all MML
        for (mml_idx = 0; mml_idx < wk.mml_num; mml_idx++) {
            line_ptr[mml_idx] = readMmlFile(wk.mml_names[mml_idx], wk.mml_short_names[mml_idx]);
            if (line_ptr[mml_idx] == null) return -1;
            getLineStatus(line_ptr[mml_idx], 0);

            //for (i = 1; i < line_ptr[mml_idx].length; i++) {
            //    logger.log(Level.TRACE, String.format("%4d : {1:X04}", i, line_ptr[mml_idx][i].status));
            //}

            getTone(line_ptr[mml_idx]);
            getEnvelope(line_ptr[mml_idx]);
            getPitchEnv(line_ptr[mml_idx]);
            getPitchMod(line_ptr[mml_idx]);
            getArpeggio(line_ptr[mml_idx]);
            //getDPCM(line_ptr[mml_idx]);
            //getXPCM(line_ptr[mml_idx]);
            //getFMTone(line_ptr[mml_idx]);
            //getWTBTone(line_ptr[mml_idx]);
            getToneTable(line_ptr[mml_idx]);
            getOPL3tbl(line_ptr[mml_idx]);
            //getVRC7Tone(line_ptr[mml_idx]);
            //getN106Tone(line_ptr[mml_idx]);
            getHardEffect(line_ptr[mml_idx]);
            getEffectWave(line_ptr[mml_idx]);
        }

        tone_max = checkLoop(tone_tbl, _TONE_MAX);
        envelope_max = checkLoop(envelope_tbl, _ENVELOPE_MAX);
        pitch_env_max = checkLoop(pitch_env_tbl, _PITCH_ENV_MAX);
        pitch_mod_max = getMaxLFO(pitch_mod_tbl, _PITCH_MOD_MAX);
        arpeggio_max = checkLoop(arpeggio_tbl, _ARPEGGIO_MAX);
        //    dpcm_max = getMaxDPCM(dpcm_tbl);
        //    fm_tone_max = getMaxTone(fm_tone_tbl, _FM_TONE_MAX);
        //    n106_tone_max = getMaxTone(n106_tone_tbl, _N106_TONE_MAX);
        //    vrc7_tone_max = getMaxTone(vrc7_tone_tbl, _VRC7_TONE_MAX);
        hard_effect_max = getMaxHardEffect(hard_effect_tbl, _HARD_EFFECT_MAX);
        effect_wave_max = getMaxEffectWave(effect_wave_tbl, _EFFECT_WAVE_MAX);

        //    xpcm_max = getMaxDPCM(xpcm_tbl);
        //    wtb_tone_max = getMaxTone(wtb_tone_tbl, _WTB_TONE_MAX);

        tonetbl_max = getMaxToneTable(tonetbl_tbl, _TONETBL_MAX);
        opl3tbl_max = getMaxOpl3tbl(opl3op_tbl, _OPL3TBL_MAX);

        //    xpcm_size = checkXPCMSize(xpcm_tbl);


        //    sortDPCM(dpcm_tbl); // Remove duplicate tones
        //    dpcm_size = checkDPCMSize(dpcm_tbl);
        //    //printf("dpcmsize $%x\n",dpcm_size);
        //    if (!allow_bankswitching && (dpcm_size > _DPCM_TOTAL_SIZE)) {	// Check size
        //        dispError(DPCM_FILE_TOTAL_SIZE_OVER, NULL, 0);
        //        dpcm_size = 0;
        //    } else {
        //        dpcm_data = malloc(dpcm_size);
        //        readDPCM(dpcm_tbl);
        //    }

        // Modifying pitch envelope parameters
        for (i = 0; i < pitch_env_max; i++) {
            if (pitch_env_tbl[i][0] != 0) {
                for (j = 1; j <= pitch_env_tbl[i][0]; j++) {
                    if (0 < pitch_env_tbl[i][j] && pitch_env_tbl[i][j] < 127) {
                        pitch_env_tbl[i][j] = pitch_env_tbl[i][j] | 0x80;
                    } else if (0 >= pitch_env_tbl[i][j] && pitch_env_tbl[i][j] >= -127) {
                        pitch_env_tbl[i][j] = (0 - pitch_env_tbl[i][j]);
                    }
                }
            }
        }

        {
            // Writing sounds
            writeTone(efFp, tone_tbl, "dutyenve", tone_max);
            // Envelope Writing
            writeTone(efFp, envelope_tbl, "softenve", envelope_max);
            // Pitch envelope writing
            writeTone(efFp, pitch_env_tbl, "pitchenve", pitch_env_max);
            // Note envelope writing
            writeTone(efFp, arpeggio_tbl, "arpeggio", arpeggio_max);
            // LFO Write
            efFp.add(new MmlDatum2("lfo_data:\n", -2, "lfo_data:"));
            if (pitch_mod_max != 0) {
                for (i = 0; i < pitch_mod_max; i++) {
                    if (pitch_mod_tbl[i][0] != 0) {
                        byte b1 = (byte) pitch_mod_tbl[i][1];
                        byte b2 = (byte) pitch_mod_tbl[i][2];
                        byte b3 = (byte) pitch_mod_tbl[i][3];
                        byte b4 = (byte) pitch_mod_tbl[i][4];
                        efFp.add(new MmlDatum2(String.format("\tdb\t$%02x,$%02x,$%02x,$%02x\n", b1, b2, b3, b4)
                                , -1, b1, -1, b2, -1, b3, -1, b4));
                    } else {
                        efFp.add(new MmlDatum2("\tdb\t$00,$00,$00,$00\n", -1, 0x00, -1, 0x00, -1, 0x00, -1, 0x00));
                    }
                }
                efFp.add(new MmlDatum2("\n", 0));
            }
            //// FM tone writing
            //writeToneFM(fp, fm_tone_tbl, "fds", fm_tone_max);
            writeHardEffect(efFp, hard_effect_tbl, "fds", hard_effect_max);
            writeEffectWave(efFp, effect_wave_tbl, "fds", effect_wave_max);
            //// Namco106 tone writing
            //writeToneN106(fp, n106_tone_tbl, "n106", n106_tone_max);
            efFp.add(new MmlDatum2("db 0;dummy N106_channel\n", -1, 0));
            //// VRC7 tone writing
            //writeToneVRC7(fp, vrc7_tone_tbl, "vrc7", vrc7_tone_max);
            //// DPCM writing
            //writeDPCM(fp, dpcm_tbl, "dpcm_data", dpcm_max);
            //writeDPCMSample(fp);

            //// HuSIC
            //// WTB tone writing
            //writeToneWTB(fp, wtb_tone_tbl, "pce", wtb_tone_max);

            //// ToneTable
            writeToneTable(efFp, tonetbl_tbl, "ttbl", tonetbl_max);

            // OPL3 FM Tone
            writeOPL3tbl(efFp, "opl3tbl", opl3tbl_max);

            //// XPCM writing
            //writeXPCM(fp, xpcm_tbl, "xpcm_data", xpcm_max);


            // Write MML file
            if (wk.include_flag != 0) {
                t = String.format("\t.include\t\"%s\"", wk.out_name);
                efFp.add(new MmlDatum2(t, -4, t));
            }
        }

        // MML->ASM data conversion

        // Write title/composer/editor information as comments to output files
        writeSongInfo(oufp);

        logger.log(Level.DEBUG, String.format(" test info:vrc7:%d vrc6:%d n106:%d", vrc7_track_num, vrc6_track_num, n106_track_num));

        track_ptr = 0;

        for (i = 0; i < _TRACK_MAX; i++)
            trk_flag[i] = 0;

        if (opl4_track_num != 0) {
            for (i = track_ptr; i < track_ptr + opl4_track_num; i++)
                trk_flag[i] = 1;

            track_ptr += opl4_track_num;
        }

        if (opl3_track_num != 0) {
            for (i = track_ptr; i < track_ptr + opl3_track_num; i++)
                trk_flag[i] = 1;

            track_ptr += opl3_track_num;
        }

        oufp.add(new MmlDatum2("\t.if TOTAL_SONGS > 1\n", -4, ".if TOTAL_SONGS > 1"));
        oufp.add(new MmlDatum2("song_addr_table:\n", -2, "song_addr_table:"));
        for (mml_idx = 0; mml_idx < wk.mml_num; mml_idx++) {
            setSongLabel();
            t = String.format("%s_track_table", songlabel);
            oufp.add(new MmlDatum2(String.format("\tdw\t%s\n", t), -3, t));
        }

        oufp.add(new MmlDatum2("\t.if (ALLOW_BANK_SWITCH)\n", -4, ".if (ALLOW_BANK_SWITCH)"));
        oufp.add(new MmlDatum2("song_bank_table:\n", -2, "song_bank_table:"));
        for (mml_idx = 0; mml_idx < wk.mml_num; mml_idx++) {
            setSongLabel();
            t = String.format("%s_bank_table", songlabel);
            oufp.add(new MmlDatum2(String.format("\tdw\t%s\n", t), -3, t));
        }
        oufp.add(new MmlDatum2("\t.endif ; ALLOW_BANK_SWITCH\n", -4, ".endif"));
        oufp.add(new MmlDatum2("\t.endif ; TOTAL_SONGS > 1\n", -4, ".endif"));

        for (mml_idx = 0; mml_idx < wk.mml_num; mml_idx++) {
            setSongLabel();
            oufp.add(new MmlDatum2("sound_data_table:\n", -2, "sound_data_table:"));


            for (i = 0; i < _TRACK_MAX; i++) {
                t = String.format("%s_%02d", songlabel, i);
                if (trk_flag[i] != 0) oufp.add(new MmlDatum2(String.format("\tdw\t%s\n", t), -3, t));
            }

            oufp.add(new MmlDatum2("\t.if (ALLOW_BANK_SWITCH)\n", -4, ".if (ALLOW_BANK_SWITCH)"));
            oufp.add(new MmlDatum2("sound_data_bank:\n", -2, "sound_data_bank:"));
            //fp.add(String.format("{0}_bank_table:\n", songlabel));
            for (i = 0; i < _TRACK_MAX; i++) {
                t = String.format("bank(%s_%02d)", songlabel, i);
                if (trk_flag[i] != 0) oufp.add(new MmlDatum2(String.format("\tdb\t%s\n", t), -3, t));
            }

            oufp.add(new MmlDatum2("loop_point_table:\n", -2, "loop_point_table:"));
            for (i = 0; i < _TRACK_MAX; i++) {
                t = String.format("%s_%02d_lp", songlabel, i);
                if (trk_flag[i] != 0) oufp.add(new MmlDatum2(String.format("\tdw\t%s\n", t), -3, t));
            }

            oufp.add(new MmlDatum2("loop_point_bank:\n", -2, "loop_point_bank:"));
            for (i = 0; i < _TRACK_MAX; i++) {
                t = String.format("bank(%s_%02d_lp)", songlabel, i);
                if (trk_flag[i] != 0) oufp.add(new MmlDatum2(String.format("\tdb\t%s\n", t), -3, t));
            }

            oufp.add(new MmlDatum2("\n", 0));
            oufp.add(new MmlDatum2("\t.endif\n", -4, ".endif"));
        }

        curr_bank = 0x00;

        // All about MML
        for (mml_idx = 0; mml_idx < wk.mml_num; mml_idx++) {
            setSongLabel();
            // Data conversion on a track-by-track basis
            for (i = 0; i < _TRACK_MAX; i++) {
                if (bank_sel[i] != -1 && auto_bankswitch == 0) {
                    if (trk_flag[i] == 0) {
                        if (wk.message_flag == 0) {
                            logger.log(Level.WARNING, String.format("Warning: #SETBANK for unused track (%c) ignored", str_track.charAt(i)));
                        } else {
                            logger.log(Level.WARNING, String.format("Warning: Ignored #SETBANK on unused track(%c)", str_track.charAt(i)));
                        }
                    } else if ((bank_sel[i] == 2 || bank_sel[i] == 3) && dpcm_bankswitch != 0) {
                        dispError(enmErrNum.CANT_USE_BANK_2_OR_3_WITH_DPCMBANKSWITCH.ordinal(), null, 0);
                    } else {
                        curr_bank = bank_sel[i];
                        oufp.add(new MmlDatum2("\n\n", 0));
                        t = String.format(".bank\t%d", bank_sel[i]);
                        oufp.add(new MmlDatum2(String.format("\t%d\n", t), -4, t));
                        putBankOrigin(oufp, bank_sel[i]);
                    }
                }

                if (trk_flag[i] != 0) {
                    cmd_buf = new CMD[32 * 1024];// malloc(sizeof(CMD) * 32 * 1024);
                    developeData(oufp, i, cmd_buf, line_ptr[mml_idx]);
                    cmd_buf = null;
                }
            }
        }

        {
            t = String.format("TOTAL_SONGS\tequ\t$%02x", wk.mml_num);
            infp.add(new MmlDatum2(String.format("%s\n", t), -5, t));
            t = String.format("SOUND_GENERATOR\tequ\t$%02x", sndgen_flag);
            infp.add(new MmlDatum2(String.format("%s\n", t), -5, t));
            t = String.format("SOUND_USERPCM\tequ\t$%02x", use_pcm);
            infp.add(new MmlDatum2(String.format("%s\n", t), -5, t));

            t = String.format("USE_OPL3_TRACK\t\tequ\t%2d", opl3_track_num);
            infp.add(new MmlDatum2(String.format("%s\n", t), -5, t));
            t = String.format("OPL3_BASETRACK\t\tequ\t%2d", BOPL3TRACK());
            infp.add(new MmlDatum2(String.format("%s\n", t), -5, t));

            //flogger.log(Level.TRACE, fp, "INITIAL_WAIT_FRM\t\tequ\t%2d\n", 0x26);
            t = String.format("PITCH_CORRECTION\t\tequ\t%d", pitch_correction);
            infp.add(new MmlDatum2(String.format("%s\n", t), -5, t));
            t = String.format("DPCM_RESTSTOP\t\tequ\t%d", dpcm_reststop);
            infp.add(new MmlDatum2(String.format("%s\n", t), -5, t));
            t = String.format("DPCM_BANKSWITCH\t\tequ\t%d", dpcm_bankswitch);
            infp.add(new MmlDatum2(String.format("%s\n", t), -5, t));
            t = String.format("DPCM_EXTRA_BANK_START\t\tequ\t%d", bank_maximum + 1);
            infp.add(new MmlDatum2(String.format("%s\n", t), -5, t));
            t = String.format("BANK_MAX_IN_4KB\t\tequ\t(%d + %d)*2+1", bank_maximum, dpcm_extra_bank_num);
            infp.add(new MmlDatum2(String.format("%s\n", t), -5, t));

            // (!dpcm_bankswitch && (bank_maximum + dpcm_extra_bank_num <= 3))
            if (allow_bankswitching == 0) {
                t = "ALLOW_BANK_SWITCH\t\tequ\t0";
                infp.add(new MmlDatum2(String.format("%s\n", t), -5, t));
            } else {
                t = "ALLOW_BANK_SWITCH\t\tequ\t1";
                infp.add(new MmlDatum2(String.format("%s\n", t), -5, t));
                t = "BANKSWITCH_INIT_MACRO\t.macro";
                infp.add(new MmlDatum2(String.format("%s\n", t), -4, t));
                switch (bank_maximum) {
                    case 0:
                        infp.add(new MmlDatum2("\tdb\t0,1,0,0,0,0,0,0\n", -1, 0, -1, 1, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0));
                        break;
                    case 1:
                        infp.add(new MmlDatum2("\tdb\t0,1,2,3,0,0,0,0\n", -1, 0, -1, 1, -1, 2, -1, 3, -1, 0, -1, 0, -1, 0, -1, 0));
                        break;
                    case 2:
                        infp.add(new MmlDatum2("\tdb\t0,1,2,3,4,5,0,0\n", -1, 0, -1, 1, -1, 2, -1, 3, -1, 4, -1, 5, -1, 0, -1, 0));
                        break;
                    case 3:
                    default:
                        infp.add(new MmlDatum2("\tdb\t0,1,2,3,4,5,6,7\n", -1, 0, -1, 1, -1, 2, -1, 3, -1, 4, -1, 5, -1, 6, -1, 7));
                        break;
                }
                t = "\t.endm";
                infp.add(new MmlDatum2(String.format("%s\n", t), -4, t));
            }

            // Write title/composer/editor information to output file as macro
            writeSongInfoMacro(infp);

            infp.add(new MmlDatum2("\n\n", -1));
            //fclose(fp);
        }

        if (error_flag != 0) {
            wk.out_name = "";  // If an error occurs, delete the output file.
            wk.ef_name = "";
            return -1;
        }

        // File output / Buffer output
        if (compiler.isSrc) {
            StringBuilder sb = new StringBuilder();
            for (MmlDatum2 s : efFp) sb.append(s.code);
            File.writeAllText(wk.ef_name, sb.toString());

            sb = new StringBuilder();
            for (MmlDatum2 s : oufp) sb.append(s.code);
            File.writeAllText(wk.out_name, sb.toString());

            sb = new StringBuilder();
            for (MmlDatum2 s : infp) sb.append(s.code);
            File.writeAllText(wk.inc_name, sb.toString());
        }
        //else
        {
            // Assemble
            Assemble asm = new Assemble();
            List<List<MmlDatum2>> dest = asm.build(wk, efFp, oufp, infp);
            List<MmlDatum2> des = new ArrayList<>();
            for (int m = 0; m < dest.size(); m++) {
                int org = (m & 1) == 0 ? 0x8000 : 0xa000;
                for (int k = org; k < dest.get(m).size(); k++) {
                    MmlDatum2 md = dest.get(m).get(k);
                    des.add(md);
                }
                for (int k = dest.get(m).size(); k < org + 0x2000; k++) {
                    MmlDatum2 md = new MmlDatum2();
                    md.dat = 0xff;
                    des.add(md);
                }
            }

            wk.destBuf = des.toArray(MmlDatum2[]::new);
        }

        // PCM Pack
        if (compiler.doPackPCM) {
            PcmPack pk = new PcmPack();
            wk.destBuf = pk.Pack(new ArrayList<>(List.of(wk.destBuf)), wk.in_name, compiler.pcmFileName).toArray(MmlDatum2[]::new);
        } else if (pcm_pack) {
            String pcmFn = pcm_name;
            if (!File.exists(pcmFn)) {
                if (compiler.origpath != null) pcmFn = Path.combine(compiler.origpath, pcm_name);
            }
            if (!File.exists(pcmFn)) {
                if (wk.in_name != null) pcmFn = Path.combine(Path.getDirectoryName(wk.in_name), pcm_name);
            }
            if (!File.exists(pcmFn)) {
                if (wk.mdr_name != null) pcmFn = Path.combine(Path.getDirectoryName(wk.mdr_name), pcm_name);
            }
            if (File.exists(pcmFn)) {
                PcmPack pk = new PcmPack();
                wk.destBuf = pk.Pack(new ArrayList<>(List.of(wk.destBuf)), pcmFn, pcm_name).toArray(MmlDatum2[]::new);
            }
        }

        compilerInfo = new CompilerInfo();
        compilerInfo.partName = new ArrayList<>();
        compilerInfo.partNumber = new ArrayList<>();
        compilerInfo.partType = new ArrayList<>();
        compilerInfo.totalCount = new ArrayList<>();
        compilerInfo.loopCount = new ArrayList<>();
        compilerInfo.errorList = new ArrayList<>();
        compilerInfo.warningList = new ArrayList<>();

        // All about MML
        for (mml_idx = 0; mml_idx < wk.mml_num; mml_idx++) {
            logger.log(Level.INFO, "");
            if (wk.mml_num > 1) {
                logger.log(Level.INFO, String.format("Song %d: %s", mml_idx + 1, wk.mml_names[mml_idx]));
            }
            logger.log(Level.INFO, "-------+---------------+---------------+");
            logger.log(Level.INFO, "Track  |    Total      |    Loop       |");
            logger.log(Level.INFO, " Symbol|(count)|(frame)|(count)|(frame)|");
            logger.log(Level.INFO, "-------+-------+-------+-------+-------+");
            for (i = 0; i < _TRACK_MAX; i++) {
                if (trk_flag[i] != 0) {
                    display_counts_sub(i, str_track.charAt(i));

                    compilerInfo.partNumber.add(i);
                    compilerInfo.partName.add(String.valueOf(str_track.charAt(i)));
                    compilerInfo.partType.add("FM");
                    compilerInfo.totalCount.add(double2int(track_count[mml_idx][i][0].cnt));
                    compilerInfo.loopCount.add(double2int(track_count[mml_idx][i][1].cnt));
                }
            }
            logger.log(Level.INFO, "-------+-------+-------+-------+-------+");
        }

        return 0;

    }

    public CompilerInfo GetCompilerInfo() {
        return compilerInfo;
    }
}
