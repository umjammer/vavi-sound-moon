package moonDriver.driver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import moonDriver.common.Common;
import musicDriverInterface.ChipAction;
import musicDriverInterface.ChipDatum;
import musicDriverInterface.IDriver;
import musicDriverInterface.MetaData;
import musicDriverInterface.MetaData.Tag;
import musicDriverInterface.MmlDatum;
import vavi.util.compat.Tuple;

import static java.lang.System.getLogger;
import static vavi.util.compat.Util.changeExtension;
import static vavi.util.compat.Util.getFileNameWithoutExtension;


public class Driver implements IDriver {

    private static final Logger logger = getLogger(Driver.class.getName());

    public final Exception renderingException = null;
    private MoonDriver md = null;
    private MmlDatum[] srcBuf = null;
    private Consumer<ChipDatum> writeOPL4;

    public Driver() {
    }

    public void fadeOut() {
        throw new UnsupportedOperationException();
    }

    public MmlDatum[] getData() {
        throw new UnsupportedOperationException();
    }

    public MetaData getMetaData(byte[] srcBuf) {
        MetaData metaData = new MetaData();

        int[] adrTag = new int[1];
        adrTag[0] = (srcBuf[0x2e] & 0xff) + (srcBuf[0x2f] & 0xff) * 0x100;
        if (adrTag[0] != 0) {
            adrTag[0] -= 0x8000;
            metaData.add(Tag.Title, Common.getNRDString(srcBuf, /* ref */ adrTag));
            metaData.add(Tag.TitleJ, Common.getNRDString(srcBuf, /* ref */ adrTag));
            metaData.add(Tag.GameTitle, Common.getNRDString(srcBuf, /* ref */ adrTag));
            metaData.add(Tag.GameTitleJ, Common.getNRDString(srcBuf, /* ref */ adrTag));
            metaData.add(Tag.GameSystem, Common.getNRDString(srcBuf, /* ref */ adrTag));
            metaData.add(Tag.GameSystemJ, Common.getNRDString(srcBuf, /* ref */ adrTag));
            metaData.add(Tag.Composer, Common.getNRDString(srcBuf, /* ref */ adrTag)); // Track author
            metaData.add(Tag.ComposerJ, Common.getNRDString(srcBuf, /* ref */ adrTag)); // Track author(jp)
            metaData.add(Tag.BuildCompilerVersion, Common.getNRDString(srcBuf, /* ref */ adrTag)); // Release date
            metaData.add(Tag.Converter, Common.getNRDString(srcBuf, /* ref */ adrTag)); // Programmer
            metaData.add(Tag.Note, Common.getNRDString(srcBuf, /* ref */ adrTag)); // Notes
        }

        return metaData;
    }

    public int getNowLoopCounter() {
        return 0;
    }

    public byte[] getPCMFromSrcBuf() {
        throw new UnsupportedOperationException();
    }

    public ChipDatum[] getPCMSendData() {
        throw new UnsupportedOperationException();
    }

    public Tuple[] getPCMTable() {
        throw new UnsupportedOperationException();
    }

    public int getStatus() {
        return 1;
    }

    public List<Tuple<String, String>> getTags() {
        return null;
    }

    public Map<String, Object> getWork() {
        return null; // TODO for visualizer
    }

    /**
     * @param objects 0: path, 1: sampleRate, 2: int?
     */
    @Override
    public void init(List<ChipAction> list, MmlDatum[] mmlData, Function<String, InputStream> function, Object... objects) {
        if (mmlData == null || mmlData.length < 1) throw new IllegalArgumentException("empty source");

        this.srcBuf = mmlData;
        writeOPL4 = list != null ? list.getFirst()::writeRegister : null;
        String path = (String) objects[0];
        double sampleRate = (double) objects[1];
        int _ = (int) objects[2];
        if (writeOPL4 == null) writeOPL4 = (Consumer<ChipDatum>) objects[3];
        getTags();
        String text = changeExtension(path, ".pcm");
        byte[] array;
        try (InputStream stream = function.apply(text)) {
            array = stream != null ? stream.readAllBytes() : null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        md = new MoonDriver();
        if (array != null) {
            md.extendFile = new Tuple<>(text, array);
        }
        md.init(srcBuf, this::writeRegister, sampleRate);
    }

    public void startMusic(int musicNumber) {
    }

    public void stopMusic() {
    }

    public void render() {
        md.oneFrameProc();
    }

    public void setDriverSwitch(Object... param) {
        throw new UnsupportedOperationException();
    }

    public int setLoopCount(int loopCounter) {
        throw new UnsupportedOperationException();
    }

    public void shotEffect() {
        throw new UnsupportedOperationException();
    }

    public void startRendering(int renderingFreq, Tuple<String, Integer>[] chipsMasterClock) {
    }

    public void stopRendering() {
    }

    public void writeRegister(ChipDatum reg) {
        writeOPL4.accept(reg);
    }

    public void dispStatus() {
    }
}
