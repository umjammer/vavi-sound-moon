


package moonDriver.compiler;

import java.awt.Point;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dotnet4j.io.FileStream;
import dotnet4j.io.MemoryStream;
import dotnet4j.io.SeekOrigin;
import dotnet4j.io.Stream;
import dotnet4j.io.StreamReader;
import dotnet4j.util.compat.Tuple;
import moonDriver.common.iEncoding;
import moonDriver.common.myEncoding;
import musicDriverInterface.CompilerInfo;
import musicDriverInterface.GD3Tag;
import musicDriverInterface.MmlDatum;


public class Compiler implements iCompiler {

    public iEncoding enc = null;
    public String[] args = null;
    public String[] env

    {
        get;
        set;
    }

    public boolean isSrc = false;
    public boolean doPackPCM = false;
    public String pcmFileName = "";

    //内部
    private String srcBuf = null;
    public String origpath = null;
    private boolean isIDE = false;
    private Point skipPoint = new Point(0, 0);
    private Function<String, Stream> appendFileReaderCallback;
    public work work = new work();
    public mck mck = null;


    public Compiler(iEncoding enc /* = null */) {
        this.enc = enc ? ? myEncoding.Default;
    }

    public void Init() {
        this.isIDE = false;
        this.skipPoint = new Point(0, 0);
        this.args = null;
    }

    public MmlDatum[] Compile(Stream sourceMML, Function<String, Stream> appendFileReaderCallback) {
        try (var ms = ReadAllBytesToMemoryStream(sourceMML)) {
            ms.seek(0, SeekOrigin.Begin);
            int c = 0;
            int offset = 0;
            while ((c = ms.readByte()) >= 0) {
                if (c == 0x1a) {
                    ms.setLength(offset);
                    break;
                }
                offset++;
            }
            ms.seek(0, SeekOrigin.Begin);

            try (StreamReader sr = new StreamReader(ms, Charset.forName("Shift_JIS"))) {
                srcBuf = sr.readToEnd();
            } catch (IOException e) {
                throw new dotnet4j.io.IOException(e);
            }
        }

        //Console.WriteLine(srcBuf);

        this.appendFileReaderCallback = appendFileReaderCallback;

        work.srcBuf = srcBuf;

        mck = new mck();
        List<MmlDatum> ret = new ArrayList<>();

        //if (isIDE)
        //{
        //    args = new String[] { "-i", "dummy.mdl" };
        //}

        MmlDatum2[] dest = mck.main(this, args, work, env);
        if (dest == null || dest.length < 1) return null;
        //ほしいのはmmlDatumnなのでキャスト(?)して作り直す
        for (MmlDatum2 md2 : dest) {
            ret.add(md2 == null ? null : md2.ToMmlDatumn());
        }

        return ret.toArray();
    }

    public boolean Compile(FileStream sourceMML, Stream destCompiledBin, Function<String, Stream> appendFileReaderCallback) {
        var dat = Compile(sourceMML, appendFileReaderCallback);
        if (dat == null) {
            return false;
        }
        for (MmlDatum md : dat) {
            if (md == null) {
                destCompiledBin.writeByte((byte) 0);
            } else {
                destCompiledBin.writeByte((byte) md.dat);
            }
        }
        return true;
    }

    public CompilerInfo GetCompilerInfo() {
        if (mck == null) return null;
        return mck.GetCompilerInfo();
    }

    public GD3Tag GetGD3TagInfo(byte[] srcBuf) {
        return null;
    }

    public void SetCompileSwitch(Object... param) {
        if (param == null) return;

        for (Object prm : param) {
            if (prm instanceof Function) // <String, Stream>
            {
                appendFileReaderCallback = (Function<String, Stream>) prm;
                continue;
            }

            if (!(prm instanceof String)) continue;

            if (((String) prm).equals("SRC")) {
                this.isSrc = true;
            }

            //PCMPACK指定の場合は単独で指定する必要あり
            if (((String) prm).equals("PCMPACK")) {
                this.doPackPCM = true;
                this.pcmFileName = (String) param[1];
                return;
            }

            //IDEフラグオン
            if ((String) prm == "IDE") {
                this.isIDE = true;
            }

            //スキップ再生指定
            if (((String) prm).indexOf("SkipPoint=") == 0) {
                try {
                    String[] p = ((String) prm).split("=")[1].split(":");
                    int r = Integer.parseInt(p[0].substring(1));
                    int c = Integer.parseInt(p[1].substring(1));
                    this.skipPoint = new Point(c, r);
                } catch
                {
                    continue;
                }
            }

            //オリジナルファイルの所在
            if (((String) prm).indexOf("ORIGPATH=") == 0) {
                try {
                    this.origpath = ((String) prm).split("=")[1];
                } catch
                {
                    continue;
                }
            }

            //MoonDriver自体のオプション
            if (((String) prm).indexOf("MoonDriverOption=") == 0) {
                try {
                    String p = ((String) prm).split("=")[1];
                    List<String> larg;
                    if (args != null) {
                        larg = new ArrayList<>(List.of(args));
                    } else {
                        larg = new ArrayList<>();
                    }
                    larg.add(p);
                    args = larg.toArray(String[]::new);
                } catch (Exception e) {
                    continue;
                }
            }

        }
    }

    public Tuple<String, String>[] GetTags(String srcText, Function<String, Stream> appendFileReaderCallback) {
        return null;
    }

    private MemoryStream ReadAllBytesToMemoryStream(Stream stream) {
        if (stream == null) return null;

        var buf = new byte[8192];
        var ms = new MemoryStream();
        while (true) {
            var r = stream.read(buf, 0, buf.length);
            if (r < 1) {
                break;
            }
            ms.write(buf, 0, r);
        }
        return ms;
    }
}
