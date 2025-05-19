package moonDriver.console;

import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import dotnet4j.io.BufferedStream;
import dotnet4j.io.File;
import dotnet4j.io.FileAccess;
import dotnet4j.io.FileMode;
import dotnet4j.io.FileShare;
import dotnet4j.io.FileStream;
import dotnet4j.io.IOException;
import dotnet4j.io.MemoryStream;
import dotnet4j.io.Path;
import dotnet4j.io.Stream;
import dotnet4j.io.StreamReader;
import dotnet4j.util.compat.StringUtilities;
import dotnet4j.util.compat.Tuple;
import moonDriver.common.Common;
import moonDriver.common.Environment;
import moonDriver.compiler.Compiler;
import musicDriverInterface.MmlDatum;
import vavi.util.serdes.Serdes;

import static java.lang.System.getLogger;
import static moonDriver.common.Common.charset;


class Program {

    private static final Logger logger = getLogger(Program.class.getName());

    static final ResourceBundle rb = ResourceBundle.getBundle("lang/message");
    private static String srcFile;
    //private static String  ffFile;
    private static String desFile;
    private static boolean isXml = false;
    private static boolean isSrc = false;
    private static boolean doPackPcm = false;
    private static String pcmFileName = "";

    private static Environment env = null;

    public static void main(String[] args) {
        int fnIndex = analyzeOption(args);

        if (args == null || args.length - fnIndex < 1) {
            logger.log(Level.ERROR, rb.getString("E0600"));
            return;
        }

        try {

            Compile(args, fnIndex);

        } catch (Exception ex) {
            logger.log(Level.ERROR, ex.getMessage());
            logger.log(Level.ERROR, ex.getStackTrace());
        }
    }

    private static void Compile(String[] args, int argIndex) {
        try {
            // Create a list of arguments for mc
            List<String> lstArg = new ArrayList<>();
            for (int i = argIndex; i < args.length; i++)
                lstArg.add(args[i]);

            Compiler compiler = new Compiler();
            compiler.init();
            compiler.args = lstArg.toArray(String[]::new);

            env = new Environment();
//            env.AddEnv("arranger");
//            env.AddEnv("composer");
//            env.AddEnv("user");
//            env.AddEnv("opt");
//            env.AddEnv("moondriver");
            compiler.env = env.GetEnv();

            // Get various file names
            int s = 0;
            for (String arg : compiler.args) {
                if (StringUtilities.isNullOrEmpty(arg)) continue;
                if (arg.charAt(0) == '-' || arg.charAt(0) == '/') continue;
                if (s == 0) srcFile = arg;
                else if (s == 1) desFile = arg;
                s++;
            }

            if (StringUtilities.isNullOrEmpty(srcFile)) {
                logger.log(Level.ERROR, rb.getString("E0601"));
                return;
            }


            // Setting DotNET Options
            if (isSrc) compiler.setCompileSwitch("SRC");
            if (doPackPcm) compiler.setCompileSwitch("PCMPACK", pcmFileName);
//#if DEBUG
            compiler.setCompileSwitch("IDE");
            //compiler.SetCompileSwitch("SkipPoint=R60:C13");
//#endif

            if (!isXml) {
                // The default is the source file name with the extension changed to .MDR.
                String destFileName = "";
                if (!StringUtilities.isNullOrEmpty(srcFile)) {
                    destFileName = Path.combine(Path.getDirectoryName(Path.getFullPath(srcFile)),
                            String.format("%s%s", Path.getFileNameWithoutExtension(srcFile), Common.objExtension)
                    );
                }

                compiler.work.in_name = srcFile;
                compiler.work.out_name = Path.combine(Path.getDirectoryName(Path.getFullPath(srcFile)),
                        String.format("%s%s", Path.getFileNameWithoutExtension(srcFile), ".h"));
                compiler.work.ef_name = Path.combine(Path.getDirectoryName(Path.getFullPath(srcFile)), compiler.work.ef_name);
                compiler.work.inc_name = Path.combine(Path.getDirectoryName(Path.getFullPath(srcFile)), compiler.work.inc_name);

                // Get Filename from Tag
                String srcText;
                try (FileStream sourceMML = new FileStream(srcFile, FileMode.Open, FileAccess.Read, FileShare.Read)) {
                    try (StreamReader sr = new StreamReader(sourceMML, charset)) {
                        srcText = sr.readToEnd();
                    }
                }

                String outFileName = "";
                Tuple<String, String>[] tags = compiler.getTags(srcText, Program::appendFileReaderCallback);
                if (tags != null && tags.length > 0) {
                    for (Tuple<String, String> tag : tags) {
//#if DEBUG
                        logger.log(Level.TRACE, String.format("%s\t: %s", tag.getItem1(), tag.getItem2()));
//#endif
                        // Get the output file name
                        //if (tag.getItem1().toUpperCase().indexOf("#FI") != 0) continue; // Because mc is judged up to three characters
                        //outFileName = tag.getItem2();
                    }
                }

                // If the tag specifies a FileName, that is applied.
                if (!StringUtilities.isNullOrEmpty(outFileName)) {
                    if (outFileName.charAt(0) != '.') {
                        // When specifying a file name
                        destFileName = Path.combine(Path.getDirectoryName(Path.getFullPath(srcFile)), outFileName);
                    } else {
                        // When specifying the extension only
                        destFileName = Path.combine(
                                Path.getDirectoryName(Path.getFullPath(srcFile)),
                                String.format("%s%s", Path.getFileNameWithoutExtension(srcFile), outFileName));
                    }
                }

                // If desFile is specified finally, it takes precedence.
                if (desFile != null) {
                    destFileName = desFile;
                }

                boolean isSuccess = false;
                try (FileStream sourceMML = new FileStream(srcFile, FileMode.Open, FileAccess.Read, FileShare.Read)) {
                    //try (FileStream destCompiledBin = new FileStream(destFileName, FileMode.Create, FileAccess.Write))
                    try (MemoryStream destCompiledBin = new MemoryStream()) {
                        try (Stream bufferedDestStream = new BufferedStream(destCompiledBin)) {
                            isSuccess = compiler.compile(sourceMML, bufferedDestStream, Program::appendFileReaderCallback);

                            if (isSuccess) {
                                bufferedDestStream.flush();
                                byte[] destbuf = destCompiledBin.toArray();
                                File.writeAllBytes(destFileName, destbuf);
//                                if (compiler.outFFFileBuf != null) {
//                                    String outfn = Path.combine(Path.getDirectoryName(destFileName), compiler.outFFFileName);
//                                    File.WriteAllBytes(outfn, compiler.outFFFileBuf);
//                                }
                            }
                        }
                    }
                }
            } else {
                String destFileName = Path.combine(Path.getDirectoryName(Path.getFullPath(srcFile)),
                        String.format("%s.xml", Path.getFileNameWithoutExtension(srcFile)));
                if (desFile != null) {
                    destFileName = desFile;
                }
                MmlDatum[] dest = null;

                // When using xml, compile in IDE mode
                compiler.setCompileSwitch("IDE");

                try (FileStream sourceMML = new FileStream(srcFile, FileMode.Open, FileAccess.Read, FileShare.Read)) {
                    dest = compiler.compile(sourceMML, Program::appendFileReaderCallback);
                }

                try (OutputStream sw = Files.newOutputStream(java.nio.file.Path.of(destFileName))) {
                    Serdes.Util.serialize(sw, dest);
                }
            }
        } catch (Exception ex) {
            logger.log(Level.ERROR, ex.getMessage(), ex);
        }
    }

    private static Stream appendFileReaderCallback(String arg) {

        String fn = Path.combine(Path.getDirectoryName(srcFile), arg);

        String[] envPaths = env.GetEnvVal("moondriver");
        if (envPaths != null) {
            int i = 0;
            while (!File.exists(fn) && i < envPaths.length) {
                fn = Path.combine(envPaths[i++], arg);
            }
        }

        FileStream strm;
        try {
            strm = new FileStream(fn, FileMode.Open, FileAccess.Read, FileShare.Read);
        } catch (IOException e) {
            strm = null;
        }

        return strm;
    }

    private static int analyzeOption(String[] args) {
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
