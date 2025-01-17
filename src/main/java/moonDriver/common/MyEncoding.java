package moonDriver.common;

import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicReference;


public class MyEncoding implements IEncoding {

    private static AtomicReference<MyEncoding> defaultEncoding;
    private Charset sjis;

    static {
        defaultEncoding = new AtomicReference<MyEncoding>(new MyEncoding());
    }

    public MyEncoding() {
        sjis = Charset.forName("shift_jis");
    }

    public static IEncoding Default() {
        return defaultEncoding.get();
    }

    public byte[] GetSjisArrayFromString(String utfString) {
        return utfString.getBytes(sjis);
    }

    public String GetStringFromSjisArray(byte[] sjisArray) {
        return new String(sjisArray, sjis);
    }

    public String GetStringFromSjisArray(byte[] sjisArray, int index, int count) {
        return new String(sjisArray, index, count, sjis);
    }

    public String GetStringFromUtfArray(byte[] utfArray) {
        return new String(utfArray);
    }

    public byte[] GetUtfArrayFromString(String utfString) {
        return utfString.getBytes();
    }
}
