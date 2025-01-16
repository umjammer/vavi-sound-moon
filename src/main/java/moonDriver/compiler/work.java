package moonDriver.compiler;

    public class work {
        public final int EXIT_SUCCESS = 0;
        public final int EXIT_FAILURE = -1;

        public final int VersionNo = 27;
        public static final int MML_MAX = 128;
        public final int MML_MAX_NAME = 512;

        public String[] mml_names = new String[MML_MAX];
        public String[] mml_short_names = new String[MML_MAX];
        public int debug_flag = 0;

        public String  ef_name = "effect.h";
        public String  inc_name = "define.inc";
        public String  in_name;
        public String  out_name;
        public String  mdr_name;

        public int warning_flag = 1;
        public int include_flag = 0;
        public int mml_num = 0;

        //version.cから移動
        public int message_flag = version.LANGUAGE;         // 表示メッセージの出力設定( 0:Jp 1:En )

        public String  srcBuf { get; internal set; }
        public MmlDatum2[] destBuf { get; internal set; }
    }

