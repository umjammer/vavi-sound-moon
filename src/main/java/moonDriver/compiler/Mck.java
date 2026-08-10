package moonDriver.compiler;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.ResourceBundle;

import musicDriverInterface.CompilerInfo;

import static vavi.util.compat.Util.changeExtension;


class Mck {

    private static final Logger logger = System.getLogger(Mck.class.getName());

    private static final ResourceBundle rb = ResourceBundle.getBundle("moonDriver/message");

    private Compiler compiler = null;
    private Work wk = null;
    private DataMaker datamake = null;

    /**
     * Help display
     */
    private static void dispHelpMessage() {
        System.err.println(rb.getString("usage"));
        //exit(1);
    }

    /**
     * entry point
     * ¥
     * @return null compile error
     */
    public MmlDatum2[] main(Compiler compiler, String[] mckArgs, Work work, String[] env) throws IOException {
        this.compiler = compiler;
        wk = work;

        int i, in, out;
        String path, name, ext;// 256size
        int multiple_song_nsf = 0;

        in = out = 0;

        // Title Display
        System.err.printf("MML to MCK Data Converter Ver %d.%02d by Manbow-J%n",
                (Work.VersionNo / 100), (Work.VersionNo % 100));

        // Subtitle Display
        System.err.printf("%s%n", Version.moon_verstr);
        //logger.log(Level.INFO, "patches by [OK] and 2ch mck thread people");
        System.err.printf("DATE: %s%n", "2020/11/27"); // __DATE__);
        System.err.printf("%s%n", Version.patchstr);
        System.err.printf("%s%n", Version.hogereleasestr);

        // Command Line Parsing
        if (mckArgs == null || mckArgs.length < 1) {
            dispHelpMessage();
            return null;
        }

        for (i = 0; i < mckArgs.length; i++) {
            // switch?
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
                        System.err.printf(rb.getString("message.0"));
                        dispHelpMessage();
                        return null;
                }
                // Storage of input/output files
            } else {
                if (in < Work.MML_MAX) {
                    wk.mml_names[in] = mckArgs[i];
                    wk.mml_short_names[in] = Path.of(mckArgs[i]).getFileName().toString();
                    in++;
                } else {
                    System.err.printf(rb.getString("message.1"));
                    dispHelpMessage();
                    return null;
                }
            }
        }

        if (in == 0) {
            dispHelpMessage();
            return null;
        }

        if (multiple_song_nsf != 0) {
            wk.out_name = changeExtension(wk.mml_names[0], ".h");
            wk.mdr_name = changeExtension(wk.mml_names[0], ".mdr");
        } else {
            if (in == 1) {
                wk.out_name = changeExtension(wk.mml_names[0], ".h");
                wk.mdr_name = changeExtension(wk.mml_names[0], ".mdr");
            } else if (in == 2) {
                wk.out_name = wk.mml_names[1];
                in--;
            } else {
                System.err.printf(rb.getString("message.1"));
                dispHelpMessage();
                return null;
            }
        }

        wk.mml_num = in;
        for (i = 0; i < in - 1; i++) {
            System.err.printf("%s + ".formatted(wk.mml_names[i]));
        }
        System.err.printf("%s -> %s%n".formatted(wk.mml_names[i], wk.out_name));

        // Convert
        datamake = new DataMaker(compiler, wk);
        int ret = datamake.data_make();
        // end

        for (i = 0; i < in; i++)
            wk.mml_short_names[i] = "";

        if (ret == 0) {
            System.err.println();
            System.err.println(rb.getString("message.2"));
            return wk.destBuf;
        }

logger.log(Level.ERROR , "compile result: " + ret);
        System.err.println();
        System.err.println(rb.getString("message.3"));
        //return work.EXIT_FAILURE;
        return null;
    }

    public CompilerInfo getCompilerInfo() {
        if (datamake == null) return null;
        CompilerInfo ci = datamake.getCompilerInfo();
        return ci;
    }
}
