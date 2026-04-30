package moonDriver.console;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import moonDriver.common.Common;
import moonDriver.compiler.Compiler;
import musicDriverInterface.MmlDatum;
import vavi.util.compat.Tuple;
import vavi.util.serdes.Serdes;

import static java.lang.System.getLogger;
import static moonDriver.common.Common.charset;
import static vavi.util.compat.Util.getFileNameWithoutExtension;
import static vavi.util.compat.Util.isNullOrEmpty;


/**
 * System properties
 * <li>{@code moonDriver.arranger} ... </li>
 * <li>{@code moonDriver.composer} ... </li>
 * <li>{@code moonDriver.user} ... </li>
 * <li>{@code moonDriver.opt} ... </li>
 * <li>{@code moonDriver.moonDriver} ... separated by {@code ;}</li>
 */
public class Program {

    private static final Logger logger = getLogger(Program.class.getName());

    static final ResourceBundle rb = ResourceBundle.getBundle("moonDriver/message");
    private String srcFile;
    //private String ffFile;
    private String desFile;
    private boolean isXml = false;
    private boolean isSrc = false;
    private boolean doPackPcm = false;
    private String pcmFileName = "";
    public static boolean isTest = false;

    public static void main(String[] args) throws Exception {
        Program app = new Program();
        int fnIndex = app.analyzeOption(args);

        if (args == null || args.length - fnIndex < 1) {
            logger.log(Level.ERROR, rb.getString("E0600"));
            return;
        }

        try {

            app.compile(args, fnIndex);

        } catch (IOException ex) {
            logger.log(Level.ERROR, ex.getMessage(), ex);
            if (isTest) throw ex;
        }
    }

    private void compile(String[] args, int argIndex) throws IOException {
        // Create a list of arguments for mc
        List<String> lstArg = new ArrayList<>(Arrays.asList(args).subList(argIndex, args.length));

        Compiler compiler = new Compiler();
        compiler.init();
        compiler.args = lstArg.toArray(String[]::new);

        compiler.env = new String[] {
                System.getProperty("moonDriver.arranger"),
                System.getProperty("moonDriver.composer"),
                System.getProperty("moonDriver.user"),
                System.getProperty("moonDriver.opt"),
                System.getProperty("moonDriver.moonDriver"),
        };

        // Get various file names
        int s = 0;
        for (String arg : compiler.args) {
            if (isNullOrEmpty(arg)) continue;
            if (arg.charAt(0) == '-' || arg.charAt(0) == '/') continue;
            if (s == 0) srcFile = arg;
            else if (s == 1) desFile = arg;
            s++;
        }

        if (isNullOrEmpty(srcFile)) {
            logger.log(Level.ERROR, rb.getString("E0601"));
            return;
        }

        // Setting DotNET Options
        if (isSrc) compiler.setCompileSwitch("SRC");
        if (doPackPcm) compiler.setCompileSwitch("PCMPACK", pcmFileName);
//#if DEBUG
        compiler.setCompileSwitch("IDE");
        //compiler.setCompileSwitch("SkipPoint=R60:C13");
//#endif

        if (!isXml) {
            // The default is the source file name with the extension changed to .MDR.
            String destFileName = "";
            if (!isNullOrEmpty(srcFile)) {
                destFileName = Path.of(srcFile).toAbsolutePath().getParent().resolve(
                        String.format("%s%s", getFileNameWithoutExtension(srcFile), Common.objExtension)
                ).toString();
            }

            compiler.work.in_name = srcFile;
            compiler.work.out_name = Path.of(srcFile).toAbsolutePath().getParent().resolve(
                    "%s%s".formatted(getFileNameWithoutExtension(srcFile), ".h")).toString();
            compiler.work.ef_name = Path.of(srcFile).toAbsolutePath().getParent().resolve(compiler.work.ef_name).toString();
            compiler.work.inc_name = Path.of(srcFile).toAbsolutePath().getParent().resolve(compiler.work.inc_name).toString();

            // Get Filename from Tag
            String srcText = Files.readString(Path.of(srcFile), charset);

            String outFileName = "";
            Tuple<String, String>[] tags = compiler.getTags(srcText, this::appendFileReaderCallback);
            if (tags != null && tags.length > 0) {
                for (Tuple<String, String> tag : tags) {
//#if DEBUG
                    logger.log(Level.TRACE, "%s\t: %s".formatted(tag.getItem1(), tag.getItem2()));
//#endif
                    // Get the output file name
                    //if (tag.getItem1().toUpperCase().indexOf("#FI") != 0) continue; // Because mc is judged up to three characters
                    //outFileName = tag.getItem2();
                }
            }

            // If the tag specifies a FileName, that is applied.
            if (!isNullOrEmpty(outFileName)) {
                if (outFileName.charAt(0) != '.') {
                    // When specifying a file name
                    destFileName = Path.of(srcFile).toAbsolutePath().getParent().resolve(outFileName).toString();
                } else {
                    // When specifying the extension only
                    destFileName = Path.of(srcFile).toAbsolutePath().getParent().resolve(
                            "%s%s".formatted(getFileNameWithoutExtension(srcFile), outFileName)).toString();
                }
            }

            // If desFile is specified finally, it takes precedence.
            if (desFile != null) {
                destFileName = desFile;
            }

            boolean isSuccess = false;
            try (var sourceMML = Files.newInputStream(Path.of(srcFile));
                 var destCompiledBin = new ByteArrayOutputStream()) {
                isSuccess = compiler.compile(sourceMML, destCompiledBin, this::appendFileReaderCallback);

                if (isSuccess) {
                    destCompiledBin.flush();
                    byte[] destbuf = destCompiledBin.toByteArray();
                    destFileName = destFileName.replace('\\', java.io.File.separatorChar).replace("//", java.io.File.separator);
logger.log(Level.TRACE, destFileName);
                    Files.write(Path.of(destFileName), destbuf);
//                            if (compiler.outFFFileBuf != null) {
//                                String outfn = Path.combine(Path.getDirectoryName(destFileName), compiler.outFFFileName);
//                                File.WriteAllBytes(outfn, compiler.outFFFileBuf);
//                            }
                } else {
                    if (isTest) throw new IllegalStateException("compile failed");
                }
            }
        } else {
            String destFileName = Path.of(srcFile).toAbsolutePath().getParent().resolve(
                    "%s.xml".formatted(getFileNameWithoutExtension(srcFile))).toString();
            if (desFile != null) {
                destFileName = desFile;
            }
            MmlDatum[] dest;

            // When using xml, compile in IDE mode
            compiler.setCompileSwitch("IDE");

            try (InputStream sourceMML = Files.newInputStream(Path.of(srcFile))) {
                dest = compiler.compile(sourceMML, this::appendFileReaderCallback);
                if (isTest && dest == null) throw new IllegalStateException("compile failed");
            }

            try (OutputStream sw = Files.newOutputStream(Path.of(destFileName))) {
                Serdes.Util.serialize(sw, dest);
            }
        }
    }

    private InputStream appendFileReaderCallback(String arg) {

        Path fn = Path.of(srcFile).getParent().resolve(arg);

        String[] envPaths = System.getProperty("moonDriver.moonDriver", "").split(";");
        if (envPaths[0] != null) {
            int i = 0;
            while (!Files.exists(fn) && i < envPaths.length) {
                fn = Path.of(envPaths[i++], arg);
            }
        }

        InputStream strm;
        try {
            strm = Files.newInputStream(fn);
        } catch (IOException e) {
            strm = null;
        }

        return strm;
    }

    private int analyzeOption(String[] args) {
        if (args == null) return 0;
        if (args.length < 1) return 0;

        int i = 0;
        while (args.length > i && args[i] != null && !args[i].isEmpty() && args[i].charAt(0) == '-') {
            String op = args[i].substring(1).toUpperCase();
            if (op.equals("XML")) {
                isXml = true;
            } else if (op.equals("SRC")) {
                isSrc = true;
            } else if (op.contains("PCMPACK")) {
                try {
                    pcmFileName = op.split(":")[1];
                } catch (Exception e) {
                    pcmFileName = "";
                }
                doPackPcm = true;
            } else {
                break;
            }

            i++;
        }

        return i;
    }
}
