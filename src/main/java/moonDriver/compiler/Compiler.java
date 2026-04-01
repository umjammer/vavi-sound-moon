


package moonDriver.compiler;

import java.awt.Point;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dotnet4j.io.FileStream;
import dotnet4j.io.MemoryStream;
import dotnet4j.io.SeekOrigin;
import dotnet4j.io.Stream;
import dotnet4j.io.StreamReader;
import dotnet4j.util.compat.Tuple;
import musicDriverInterface.CompilerInfo;
import musicDriverInterface.GD3Tag;
import musicDriverInterface.ICompiler;
import musicDriverInterface.MmlDatum;

import static moonDriver.common.Common.charset;


public class Compiler implements ICompiler {

    public String[] args = null;
    public String[] env;
    private String[] getEnv() { return env; }
    private void setEnv(String[] value) { env = value; }
    public boolean isSrc = false;
    public boolean doPackPCM = false;
    public String pcmFileName = "";

    // internal
    private String srcBuf = null;
    public String origpath = null;
    private boolean isIDE = false;
    private Point skipPoint = new Point(0, 0);
    private Function<String, Stream> appendFileReaderCallback;
    public final Work work = new Work();
    public Mck mck = null;

    public Compiler() {
    }

    public void init() {
        this.isIDE = false;
        this.skipPoint = new Point(0, 0);
        this.args = null;
    }

    public MmlDatum[] compile(Stream sourceMML, Function<String, Stream> appendFileReaderCallback) {
        try (var ms = readAllBytesToMemoryStream(sourceMML)) {
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

            try (StreamReader sr = new StreamReader(ms, charset)) {
                srcBuf = sr.readToEnd();
            } catch (IOException e) {
                throw new dotnet4j.io.IOException(e);
            }
        }

        //logger.log(Level.DEBUG, srcBuf);

        this.appendFileReaderCallback = appendFileReaderCallback;

        work.srcBuf = srcBuf;

        mck = new Mck();
        List<MmlDatum> ret = new ArrayList<>();

//        if (isIDE) {
//            args = new String[] {"-i", "dummy.mdl"};
//        }

        MmlDatum2[] dest = mck.main(this, args, work, env);
        if (dest == null || dest.length < 1) return null;
        // What we want is mmlDatumn, so we cast(?) it and recreate it.
        for (MmlDatum2 md2 : dest) {
            ret.add(md2 == null ? null : md2.ToMmlDatumn());
        }

        return ret.toArray(MmlDatum[]::new);
    }

    public boolean compile(FileStream sourceMML, Stream destCompiledBin, Function<String, Stream> appendFileReaderCallback) {
        var dat = compile(sourceMML, appendFileReaderCallback);
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

    public CompilerInfo getCompilerInfo() {
        if (mck == null) return null;
        return mck.getCompilerInfo();
    }

    public GD3Tag getGD3TagInfo(byte[] srcBuf) {
        return null;
    }

    public void setCompileSwitch(Object... param) {
        if (param == null) return;

        for (Object prm : param) {
            if (prm instanceof Function) // <String, Stream>
            {
                appendFileReaderCallback = (Function<String, Stream>) prm;
                continue;
            }

            if (!(prm instanceof String)) continue;

            if (prm.equals("SRC")) {
                this.isSrc = true;
            }

            // Must be specified alone when PCMPACK is specified
            if (prm.equals("PCMPACK")) {
                this.doPackPCM = true;
                this.pcmFileName = (String) param[1];
                return;
            }

            // IDE Flag On
            if (prm.equals("IDE")) {
                this.isIDE = true;
            }

            // Skip playback specification
            if (((String) prm).indexOf("SkipPoint=") == 0) {
                try {
                    String[] p = ((String) prm).split("=")[1].split(":");
                    int r = Integer.parseInt(p[0].substring(1));
                    int c = Integer.parseInt(p[1].substring(1));
                    this.skipPoint = new Point(c, r);
                } catch (Exception e) {
                    continue;
                }
            }

            // Original file location
            if (((String) prm).indexOf("ORIGPATH=") == 0) {
                try {
                    this.origpath = ((String) prm).split("=")[1];
                } catch (Exception e) {
                    continue;
                }
            }

            // MoonDriver itself options
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

    public Tuple<String, String>[] getTags(String srcText, Function<String, Stream> appendFileReaderCallback) {
        return null;
    }

    private static MemoryStream readAllBytesToMemoryStream(Stream stream) {
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
