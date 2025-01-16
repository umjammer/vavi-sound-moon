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

    public static final String mmlExtension = ".mdm";
    public static final String objExtension = ".mdr";

    public static short GetLe16(MmlDatum[] md, int adr) {
        return (short) (md[adr].dat + md[adr + 1].dat * 0x100);
    }


    public static byte[] GetPCMDataFromFile(String fnPcm, Function<String, Stream> appendFileReaderCallback) {
        try {
            try (Stream pd = appendFileReaderCallback.apply(fnPcm)) {
                return ReadAllBytes(pd);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /// <summary>
    /// ストリームから一括でバイナリを読み込む
    /// </summary>
    public static byte[] ReadAllBytes(Stream stream) {
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

    public static String getNRDString(MmlDatum[] buf, /* ref */ int index) {
        if (buf == null || buf.length < 1 || index < 0 || index >= buf.length) return "";

        try {
            List<Byte> lst = new ArrayList<>();
            for (; buf[index].dat != 0; index++) {
                lst.add((byte) buf[index].dat);
            }

            String n = new String(ByteUtil.toByteArray(lst), Charset.forName("cp932"));
            index++;

            return n;
        } catch (Exception e) {
            logger.log(Level.ERROR, String.format("Exception\r\nMessage\r\n%d\r\nStackTrace\r\n%d\r\n", e.getMessage(), e.getStackTrace()));
        }
        return "";
    }
}
