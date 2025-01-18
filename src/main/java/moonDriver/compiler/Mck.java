


package moonDriver.compiler;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import dotnet4j.io.Path;
import musicDriverInterface.CompilerInfo;

import static java.lang.System.getLogger;


public class Mck {

    private static final Logger logger = getLogger(Mck.class.getName());

    private Compiler compiler = null;
    private Work wk = null;
    private DataMaker datamake = null;

    //extern void splitPath( final char* ptr, char* path, char* name, char* ext );
    //extern void makePath(char* ptr, final char* path, final char* name, final char* ext );
    //extern char* skipSpace(char* ptr);
    //extern char* patchstr;
    //extern char* hogereleasestr;

    //extern char* moon_verstr;

    //char* mml_names[MML_MAX];
    //char* mml_short_names[MML_MAX];
    //extern int data_make(void );
    //extern int message_flag;            // 表示メッセージの出力設定( 0:Jp 1:En )

    /// *--------------------------------------------------------------
    //    ヘルプ表示
    // Input:

    // Output:

    //--------------------------------------------------------------*/
    private void dispHelpMessage() {
        if (wk.message_flag == 0) {

            logger.log(Level.INFO, """
                    使用方法:mmckc [switch] InputFile.mml [OutputFile.h]
                    もしくは:mmckc [switch] -u InputFile1.mml InputFile2.mml ...
                        [switch]
                        -h -?   : ヘルプを表示
                        -i      : 音色/エンベロープファイルに曲データを追加する
                        -m<num> : エラー/ワーニング表示の選択(0:Jpn 1:Eng)
                        -o<str> : 音色/エンベロープファイルのファイル名を<str>にする
                        -w      : Warningメッセージを表示しません
                        -u      : 複数曲登録NSF作成"
                                        );
                    
                                }
                                else
                                {
                                    logger.log(Level.INFO,
                                        @"Usage:mmckc [switch] InputFile.mml [OutputFile.h]
                      or :mmckc [switch] -u InputFile1.mml InputFile2.mml ...
                        [switch]
                        -h -?    : Display this help message
                        -i       : Including song data in tone/envelope file
                        -m<num>  : Select message language(0:Jpn 1:Eng)
                        -o<str>  : Output tone/envelope file name is <str>
                        -w       : Don't display warning message
                        -u       : Multiple song NSF creation
                    """
            );
        }
        //exit(1);
    }


    /*--------------------------------------------------------------
        メインルーチン
     Input:
        int	argc		: コマンドライン引数の個数
        char *argv[]	: コマンドライン引数のポインタ
     Output:
        0:正常終了 0:以外以上終了
    --------------------------------------------------------------*/
    public MmlDatum2[] main(Compiler compiler, String[] mckArgs, Work work, String[] env) {
        this.compiler = compiler;
        wk = work;

        int i, _in, _out;
        String path, name, ext;// 256size
        int multiple_song_nsf = 0;

        _in = _out = 0;

        // タイトル表示
        logger.log(Level.INFO, String.format("MML to MCK Data Converter Ver %d.{1:d02} by Manbow-J",
                (work.VersionNo / 100), (work.VersionNo % 100)));

        // サブタイトル表示
        logger.log(Level.INFO, String.format("%d", Version.moon_verstr));
        //printf("patches by [OK] and 2ch mck thread people\n");
        logger.log(Level.INFO, String.format("DATE: %d", "2020/11/27"));// __DATE__);
        logger.log(Level.INFO, String.format("%d", Version.patchstr));
        logger.log(Level.INFO, String.format("%d", Version.hogereleasestr));

        // コマンドライン解析
        if (mckArgs == null || mckArgs.length < 1) {
            dispHelpMessage();
            return null;
        }

        for (i = 0; i < mckArgs.length; i++) {
            // スイッチ？
            if (mckArgs[i].charAt(0) == '-') {
                switch (mckArgs[i].toUpperCase().charAt(1)) {
                    case 'H':
                    case '?':
                        dispHelpMessage();
                        return null;
                    case 'X':
                        wk.debug_flag = 1;
                        break;
                    case 'I':
                        wk.include_flag = 1;
                        break;
                    case 'M':
                        int res = 0;
                        try {
                            res = Integer.parseInt(mckArgs[i].substring(2));
                            wk.message_flag = res;
                        } catch (NumberFormatException ignore) {
                        }
                        if (wk.message_flag > 1) {
                            dispHelpMessage();
                            return null;
                        }
                        break;
                    case 'N':
                        //obsolete
                        break;
                    case 'O':
                        wk.ef_name = mckArgs[i].substring(2).trim();
                        break;
                    case 'W':
                        wk.warning_flag = 0;
                        break;
                    case 'U':
                        multiple_song_nsf = 1;
                        break;
                    default:
                        if (wk.message_flag == 0) {
                            logger.log(Level.ERROR, "スイッチの指定が違います");
                        } else {
                            logger.log(Level.ERROR, "Invalid switch!");
                        }
                        dispHelpMessage();
                        return null;
                }
                // 入力/出力ファイルの格納
            } else {
                if (_in < work.MML_MAX) {
                    wk.mml_names[_in] = mckArgs[i];
                    wk.mml_short_names[_in] = Path.getFileName(mckArgs[i]);
                    _in++;
                } else {
                    if (wk.message_flag == 0) {
                        logger.log(Level.ERROR, "パラメータが多すぎます");
                    } else {
                        logger.log(Level.ERROR, "Too many parameters!");
                    }
                    dispHelpMessage();
                    return null;
                }
            }
        }

        if (_in == 0) {
            dispHelpMessage();
            return null;
        }

        if (multiple_song_nsf != 0) {
            wk.out_name = Path.changeExtension(wk.mml_names[0], ".h");
            wk.mdr_name = Path.changeExtension(wk.mml_names[0], ".mdr");
        } else {
            if (_in == 1) {
                wk.out_name = Path.changeExtension(wk.mml_names[0], ".h");
                wk.mdr_name = Path.changeExtension(wk.mml_names[0], ".mdr");
            } else if (_in == 2) {
                wk.out_name = wk.mml_names[1];
                _in--;
            } else {
                if (wk.message_flag == 0) {
                    logger.log(Level.ERROR, "パラメータが多すぎます");
                } else {
                    logger.log(Level.ERROR, "Too many parameters!");
                }
                dispHelpMessage();
                return null;
            }
        }

        wk.mml_num = _in;
        for (i = 0; i < _in - 1; i++) {
            logger.log(Level.INFO, String.format("%d + ", wk.mml_names[i]));
        }
        logger.log(Level.INFO, String.format("%d -> %d", wk.mml_names[i], wk.out_name));

        // コンバート
        datamake = new DataMaker(compiler, wk);
        int ret = datamake.data_make();
        // 終了

        for (i = 0; i < _in; i++)
            wk.mml_short_names[i] = "";

        if (ret == 0) {
            if (wk.message_flag == 0) {
                logger.log(Level.INFO, "");
                logger.log(Level.INFO, "終了しました");
            } else {
                logger.log(Level.INFO, "");
                logger.log(Level.INFO, "Compleated!");
            }
            return wk.destBuf;
        }

        if (wk.message_flag == 0) {
            logger.log(Level.INFO, "");
            logger.log(Level.ERROR, "コンパイルに失敗しました");
        } else {
            logger.log(Level.INFO, "");
            logger.log(Level.ERROR, "Compilation failed!");
        }
        //return work.EXIT_FAILURE;
        return null;
    }

    public CompilerInfo GetCompilerInfo() {
        if (datamake == null) return null;
        CompilerInfo ci = datamake.GetCompilerInfo();
        return ci;
    }
}
