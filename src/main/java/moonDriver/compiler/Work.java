package moonDriver.compiler;

public class Work {

    public static final int EXIT_SUCCESS = 0;
    public static final int EXIT_FAILURE = -1;

    public static final int VersionNo = 27;
    public static final int MML_MAX = 128;
    public static final int MML_MAX_NAME = 512;

    public String[] mml_names = new String[MML_MAX];
    public String[] mml_short_names = new String[MML_MAX];
    public int debug_flag = 0;

    public String ef_name = "effect.h";
    public String inc_name = "define.inc";
    public String in_name;
    public String out_name;
    public String mdr_name;

    public int warning_flag = 1;
    public int include_flag = 0;
    public int mml_num = 0;

    // Moved from version.c
    public int message_flag = Version.LANGUAGE; // Display message output setting (0:Jp 1:En)

    String srcBuf;

    public String getSrcBuf() {
        return srcBuf;
    }

    MmlDatum2[] destBuf;

    public MmlDatum2[] getSestBuf() {
        return destBuf;
    }
}
