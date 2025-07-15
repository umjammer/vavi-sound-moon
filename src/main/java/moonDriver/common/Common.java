package moonDriver.common;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dotnet4j.io.MemoryStream;
import dotnet4j.io.Stream;
import musicDriverInterface.MmlDatum;
import vavi.util.ByteUtil;

import static java.lang.System.getLogger;


public class Common {

    private static final Logger logger = getLogger(Common.class.getName());

    public static Charset charset = Charset.forName("ms932");

    public static final String mmlExtension = ".mdm";
    public static final String objExtension = ".mdr";

    public static short getLe16(MmlDatum[] md, int adr) {
        return (short) (md[adr].dat + md[adr + 1].dat * 0x100);
    }

    public static byte[] getPCMDataFromFile(String fnPcm, Function<String, Stream> appendFileReaderCallback) {
        try {
            try (Stream pd = appendFileReaderCallback.apply(fnPcm)) {
                return readAllBytes(pd);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Read binary from a stream in bulk
     */
    public static byte[] readAllBytes(Stream stream) {
        if (stream == null) return null;

        var buf = new byte[8192];
        try (var ms = new MemoryStream()) {
            while (true) {
                var r = stream.read(buf, 0, buf.length);
                if (r < 1) {
                    break;
                }
                ms.write(buf, 0, r);
            }
            return ms.toArray();
        }
    }

    public static String getNRDString(MmlDatum[] buf, /* ref */ int[] index) {
        if (buf == null || buf.length < 1 || index[0] < 0 || index[0] >= buf.length) return "";

        try {
            List<Byte> lst = new ArrayList<>();
            for (; buf[index[0]].dat != 0; index[0]++) {
                lst.add((byte) buf[index[0]].dat);
            }

            String n = new String(ByteUtil.toByteArray(lst), charset);
            index[0]++;

            return n;
        } catch (Exception e) {
            logger.log(Level.ERROR, e.getMessage(), e);
        }
        return "";
    }

    public static String getNRDString(byte[] buf, /* ref */ int[] index) {
        if (buf == null || buf.length < 1 || index[0] < 0 || index[0] >= buf.length) return "";

        try {
            List<Byte> lst = new ArrayList<>();
            for (; buf[index[0]] != 0; index[0]++) {
                lst.add((byte) buf[index[0]]);
            }

            String n = new String(ByteUtil.toByteArray(lst), charset);
            index[0]++;

            return n;
        } catch (Exception e) {
            logger.log(Level.ERROR, e.getMessage(), e);
        }
        return "";
    }
}
