package moonDriver.compiler;

import java.awt.Point;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import musicDriverInterface.CompilerInfo;
import musicDriverInterface.ICompiler;
import musicDriverInterface.MetaData;
import musicDriverInterface.MmlDatum;
import vavi.util.compat.Tuple;

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
    private Function<String, InputStream> appendFileReaderCallback;
    public final Work work = new Work();
    public Mck mck = null;

    public Compiler() {
    }

    public void init() {
        this.isIDE = false;
        this.skipPoint = new Point(0, 0);
        this.args = null;
    }

    /**
     * @return null compile error
     */
    public MmlDatum[] compile(InputStream sourceMML, Function<String, InputStream> appendFileReaderCallback) {
        try {
            byte[] b  = sourceMML.readAllBytes();
            var ms = new ByteArrayInputStream(b);
            int c = 0;
            int offset = 0;
            while ((c = ms.read()) >= 0) {
                if (c == 0x1a) {
                    break;
                }
                offset++;
            }

            var sr = new InputStreamReader(new ByteArrayInputStream(b, 0, offset), charset);
            StringBuilder sb = new StringBuilder();
            int ch;
            while ((ch = sr.read()) != -1) {
                sb.append((char) ch);
            }
            srcBuf = sb.toString();

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
                ret.add(md2 == null ? null : md2.toMmlDatumn());
            }

            return ret.toArray(MmlDatum[]::new);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean compile(InputStream sourceMML, ByteArrayOutputStream destCompiledBin, Function<String, InputStream> appendFileReaderCallback) {
        var dat = compile(sourceMML, appendFileReaderCallback);
        if (dat == null) {
            return false;
        }
        for (MmlDatum md : dat) {
            if (md == null) {
                destCompiledBin.write((byte) 0);
            } else {
                destCompiledBin.write((byte) md.dat);
            }
        }
        return true;
    }

    public CompilerInfo getCompilerInfo() {
        if (mck == null) return null;
        return mck.getCompilerInfo();
    }

    public MetaData getMetaData(byte[] srcBuf) {
        return null;
    }

    public void setCompileSwitch(Object... param) {
        if (param == null) return;

        for (Object prm : param) {
            if (prm instanceof Function) { // <String, Stream>
                appendFileReaderCallback = (Function<String, InputStream>) prm;
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

    public Tuple<String, String>[] getTags(String srcText, Function<String, InputStream> appendFileReaderCallback) {
        return null;
    }
}
