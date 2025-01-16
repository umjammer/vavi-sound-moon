



package moonDriver.common;

import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicReference;
import javax.sound.sampled.AudioFormat.Encoding;


public class myEncoding extends IEncoding
    {
        private static AtomicReference<myEncoding> defaultEncoding;
        private Charset sjis;

        myEncoding() {
            defaultEncoding = new AtomicReference<myEncoding>(new myEncoding());
        }

        public myEncoding() {
            sjis = Charset.forName("shift_jis");
        }

        public static iEncoding Default() { return defaultEncoding.Value; }

        public byte[] GetSjisArrayFromString(String  utfString) { return sjis.GetBytes(utfString); }
        public String  GetStringFromSjisArray(byte[] sjisArray) { return sjis.GetString(sjisArray); }
        public String  GetStringFromSjisArray(byte[] sjisArray, int index, int count) { return sjis.GetString(sjisArray, index, count); }
        public String  GetStringFromUtfArray(byte[] utfArray) { return Encoding.UTF8.GetString(utfArray); }
        public byte[] GetUtfArrayFromString(String  utfString) { return Encoding.UTF8.GetBytes(utfString); }
    }
}
