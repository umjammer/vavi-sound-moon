package moonDriver.console;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import dotnet4j.io.BufferedStream;
import dotnet4j.io.FileAccess;
import dotnet4j.io.FileMode;
import dotnet4j.io.FileShare;
import dotnet4j.io.FileStream;
import dotnet4j.io.MemoryStream;
import dotnet4j.io.Path;
import dotnet4j.io.Stream;
import dotnet4j.io.StreamReader;
import dotnet4j.io.StreamWriter;
import dotnet4j.util.compat.StringUtilities;
import dotnet4j.util.compat.Tuple;
import moonDriver.common.Common;
import moonDriver.common.Environment;
import moonDriver.compiler.Compiler;
import musicDriverInterface.MmlDatum;

import static java.lang.System.getLogger;


class Program    {
    private static final Logger logger = getLogger(Program.class.getName());

    static final ResourceBundle rb = ResourceBundle.getBundle("lang/message");
        private static String  srcFile;
        //private static String  ffFile;
        private static String  desFile;
        private static boolean isXml = false;
        private static boolean isSrc = false;
        private static boolean doPackPcm = false;
        private static String  pcmFileName = "";

        private static Environment env = null;

        public static void main(String[] args) {
            int fnIndex = AnalyzeOption(args);

            if (args == null || args.length - fnIndex < 1)
            {
                logger.log(Level.ERROR, rb.getString("E0600"));
                return;
            }

            try
            {

                Compile(args, fnIndex);

            }
            catch (Exception ex)
            {
                logger.log(Level.ERROR, ex.getMessage());
                logger.log(Level.ERROR, ex.getStackTrace());
            }
        }

        private static void Compile(String[] args, int argIndex)
        {
            try
            {
                //mc向け引数のリストを作る
                List<String> lstArg = new ArrayList<>();
                for (int i = argIndex; i < args.length; i++)
                    lstArg.add(args[i]);

                Compiler compiler = new Compiler(null);
                compiler.init();
                compiler.args = lstArg.toArray(String[]::new);

                env = new Common.Environment();
                //env.AddEnv("arranger");
                //env.AddEnv("composer");
                //env.AddEnv("user");
                //env.AddEnv("opt");
                //env.AddEnv("moondriver");
                compiler.env = env.GetEnv();

                //各種ファイルネームを得る
                int s = 0;
                for (String arg : compiler.args)
                {
                    if (StringUtilities.isNullOrEmpty(arg)) continue;
                    if (arg.charAt(0) == '-' || arg.charAt(0) == '/') continue;
                    if (s == 0) srcFile = arg;
                    else if (s == 1) desFile = arg;
                    s++;
                }

                if (StringUtilities.isNullOrEmpty(srcFile))
                {
                    logger.log(Level.ERROR, rb.getString("E0601"));
                    return;
                }


                //DotNETオプションの設定
                if (isSrc) compiler.SetCompileSwitch("SRC");
                if (doPackPcm) compiler.SetCompileSwitch("PCMPACK", pcmFileName);
//#if DEBUG
                compiler.SetCompileSwitch("IDE");
                //compiler.SetCompileSwitch("SkipPoint=R60:C13");
//#endif

                if (!isXml)
                {
                    //デフォルトはソースファイル名の拡張子を.MDRに変更したものにする
                    String  destFileName = "";
                    if (!StringUtilities.isNullOrEmpty(srcFile))
                    {
                        destFileName = Path.combine(Path.getDirectoryName(Path.getFullPath(srcFile))
                            , String.format("%d%d"
                                , Path.getFileNameWithoutExtension(srcFile)
                                , Common.objExtension)
                            );
                    }

                    compiler.work.in_name = srcFile;
                    compiler.work.out_name = Path.combine(Path.getDirectoryName(Path.getFullPath(srcFile)), String.format("%d%d"
                                , Path.getFileNameWithoutExtension(srcFile)
                                , ".h"));
                    compiler.work.ef_name = Path.combine(Path.getDirectoryName(Path.getFullPath(srcFile)), compiler.work.ef_name);
                    compiler.work.inc_name = Path.combine(Path.getDirectoryName(Path.getFullPath(srcFile)), compiler.work.inc_name);

                    //TagからFilenameを得る
                    String  srcText;
                    try (FileStream sourceMML = new FileStream(srcFile, FileMode.Open, FileAccess.Read, FileShare.Read))
                    try (StreamReader sr = new StreamReader(sourceMML, Encoding.GetEncoding("Shift_JIS")))
                        srcText = sr.ReadToEnd();

                    String  outFileName = "";
                    Tuple<String, String>[] tags = compiler.GetTags(srcText, AppendFileReaderCallback);
                    if (tags != null && tags.length > 0)
                    {
                        for (Tuple<String, String> tag : tags)
                        {
//#if DEBUG
                            Log.WriteLine(LogLevel.TRACE, String.format("%d\t: %d", tag.getItem1(), tag.getItem2()));
//#endif
                            //出力ファイル名を得る
                            //if (tag.getItem1().toUpperCase().indexOf("#FI") != 0) continue;//mcは3文字まで判定している為
                            //outFileName = tag.getItem2();
                        }
                    }

                    //TagにFileName指定がある場合はそちらを適用する
                    if (!StringUtilities.isNullOrEmpty(outFileName))
                    {
                        if (outFileName[0] != '.')
                        {
                            //ファイル名指定の場合
                            destFileName = Path.combine(
                                Path.getDirectoryName(Path.getFullPath(srcFile))
                                , outFileName);
                        }
                        else
                        {
                            //拡張子のみの指定の場合
                            destFileName = Path.combine(
                                Path.getDirectoryName(Path.getFullPath(srcFile))
                                , String.format("%d%d"
                                , Path.getFileNameWithoutExtension(srcFile)
                                , outFileName));
                        }
                    }

                    //最終的にdesFileの指定がある場合は、そちらを優先する
                    if (desFile != null)
                    {
                        destFileName = desFile;
                    }

                    boolean isSuccess = false;
                    try (FileStream sourceMML = new FileStream(srcFile, FileMode.Open, FileAccess.Read, FileShare.Read))
                    //try (FileStream destCompiledBin = new FileStream(destFileName, FileMode.Create, FileAccess.Write))
                    try (MemoryStream destCompiledBin = new MemoryStream())
                    try (Stream bufferedDestStream = new BufferedStream(destCompiledBin))
                    {
                        isSuccess = compiler.Compile(sourceMML, bufferedDestStream, AppendFileReaderCallback);

                        if (isSuccess)
                        {
                            bufferedDestStream.Flush();
                            byte[] destbuf = destCompiledBin.toArray();
                            File.WriteAllBytes(destFileName, destbuf);
                            //if (compiler.outFFFileBuf != null)
                            //{
                            //    String  outfn = Path.combine(Path.getDirectoryName(destFileName), compiler.outFFFileName);
                            //    File.WriteAllBytes(outfn, compiler.outFFFileBuf);
                            //}
                        }
                    }

                }
                else
                {

                    String  destFileName = Path.combine(Path.getDirectoryName(Path.getFullPath(srcFile)), String.format("%d.xml", Path.getFileNameWithoutExtension(srcFile)));
                    if (desFile != null)
                    {
                        destFileName = desFile;
                    }
                    MmlDatum[] dest = null;

                    //xmlの時はIDEモードでコンパイル
                    compiler.SetCompileSwitch("IDE");

                    try (FileStream sourceMML = new FileStream(srcFile, FileMode.Open, FileAccess.Read, FileShare.Read))
                    {
                        dest = compiler.Compile(sourceMML, AppendFileReaderCallback);
                    }

                    XmlSerializer serializer = new XmlSerializer(typeof(MmlDatum[]), typeof(MmlDatum[]).GetNestedTypes());
                    try StreamWriter sw = new StreamWriter(destFileName, false, Encoding.UTF8);
                    serializer.Serialize(sw, dest);

                }


            }
            catch (Exception ex)
            {
                logger.log(Level.ERROR, ex.getMessage());
                logger.log(Level.ERROR, ex.getStackTrace());
            }
            finally
            {
            }

        }

        private static Stream AppendFileReaderCallback(String  arg)
        {

            String  fn;
            fn = Path.combine(
                Path.getDirectoryName(srcFile)
                , arg
                );

            String[] envPaths = env.GetEnvVal("moondriver");
            if (envPaths != null)
            {
                int i = 0;
                while (!File.Exists(fn) && i < envPaths.length)
                {
                    fn = Path.combine(
                        envPaths[i++]
                        , arg
                        );
                }
            }

            FileStream strm;
            try
            {
                strm = new FileStream(fn, FileMode.Open, FileAccess.Read, FileShare.Read);
            }
            catch (IOException)
            {
                strm = null;
            }

            return strm;
        }

        private static int AnalyzeOption(String[] args)
        {
            if (args == null) return 0;
            if (args.length < 1) return 0;

            int i = 0;
            while (args.length > i && args[i] != null && args[i].length > 0 && args[i][0] == '-')
            {
                String  op = args[i][1..].toUpperCase();
                if (op == "LOGLEVEL=FATAL")
                {
                    Log.level = LogLevel.FATAL;
                }
                else if (op == "LOGLEVEL=ERROR")
                {
                    Log.level = LogLevel.ERROR;
                }
                else if (op == "LOGLEVEL=WARNING")
                {
                    Log.level = LogLevel.WARNING;
                }
                else if (op == "LOGLEVEL=INFO")
                {
                    Log.level = LogLevel.INFO;
                }
                else if (op == "LOGLEVEL=DEBUG")
                {
                    Log.level = LogLevel.DEBUG;
                }
                else if (op == "LOGLEVEL=TRACE")
                {
                    Log.level = LogLevel.TRACE;
                }
                //else if (op == "OFFLOG=WARNING")
                //{
                //    Log.off = (int)LogLevel.WARNING;
                //}
                else if (op == "XML")
                {
                    isXml = true;
                }
                else if (op == "SRC")
                {
                    isSrc = true;
                }
                else if (op.indexOf("PCMPACK") >= 0)
                {
                    try
                    {
                        pcmFileName = op.split(":")[1];
                    }
                    catch
                    {
                        pcmFileName = "";
                    }
                    doPackPcm = true;
                }
                else
                {
                    break;
                }

                i++;
            }

            return i;
        }

        private static void WriteLine(LogLevel level, String  msg)
        {
            System.Console.WriteLine("[{0,-7}] %d", level, msg);
        }


    }
}
