package moonDriver.driver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import dotnet4j.io.File;
import dotnet4j.io.FileAccess;
import dotnet4j.io.FileMode;
import dotnet4j.io.FileShare;
import dotnet4j.io.FileStream;
import dotnet4j.io.Path;
import dotnet4j.io.Stream;
import dotnet4j.util.compat.StringUtilities;
import dotnet4j.util.compat.Tuple;
import moonDriver.common.Common;
import musicDriverInterface.ChipAction;
import musicDriverInterface.ChipDatum;
import musicDriverInterface.GD3Tag;
import musicDriverInterface.IDriver;
import musicDriverInterface.MmlDatum;
import musicDriverInterface.Tag;
import vavi.util.serdes.Serdes;

import static java.lang.System.getLogger;


public class Driver implements IDriver {

    private static final Logger logger = getLogger(Driver.class.getName());

    public final Exception renderingException = null;
    private MoonDriver md = null;
    static MmlDatum[] srcBuf = null;
    private Consumer<ChipDatum> writeOPL4;

    public Driver() {
    }

    public void fadeOut() {
        throw new UnsupportedOperationException();
    }

    public MmlDatum[] getDATA() {
        throw new UnsupportedOperationException();
    }

    public GD3Tag getGD3TagInfo(byte[] srcBuf) {
        GD3Tag gd3 = new GD3Tag();

        int[] adrTag = new int[1];
        adrTag[0] = (srcBuf[0x2e] & 0xff) + (srcBuf[0x2f] & 0xff) * 0x100;
        if (adrTag[0] != 0) {
            adrTag[0] -= 0x8000;
            gd3.items.put(Tag.Title, new String[] {Common.getNRDString(srcBuf, /* ref */ adrTag)});
            gd3.items.put(Tag.TitleJ, new String[] {Common.getNRDString(srcBuf, /* ref */ adrTag)});
            gd3.items.put(Tag.GameTitle, new String[] {Common.getNRDString(srcBuf, /* ref */ adrTag)});
            gd3.items.put(Tag.GameTitleJ, new String[] {Common.getNRDString(srcBuf, /* ref */ adrTag)});
            gd3.items.put(Tag.GameSystem, new String[] {Common.getNRDString(srcBuf, /* ref */ adrTag)});
            gd3.items.put(Tag.GameSystemJ, new String[] {Common.getNRDString(srcBuf, /* ref */ adrTag)});
            gd3.items.put(Tag.Composer, new String[] {Common.getNRDString(srcBuf, /* ref */ adrTag)}); // Track author
            gd3.items.put(Tag.ComposerJ, new String[] {Common.getNRDString(srcBuf, /* ref */ adrTag)}); // Track author(jp)
            gd3.items.put(Tag.BuildCompilerVersion, new String[] {Common.getNRDString(srcBuf, /* ref */ adrTag)}); // Release date
            gd3.items.put(Tag.Converter, new String[] {Common.getNRDString(srcBuf, /* ref */ adrTag)}); // Programmer
            gd3.items.put(Tag.Note, new String[] {Common.getNRDString(srcBuf, /* ref */ adrTag)}); // Notes
        }

        return gd3;
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

    public Object getWork() {
        return null;
    }

    public void init(String fileName, Consumer<ChipDatum> oPNAWrite, double sampleRate,
                     MoonDriverJavaOption dop, String[] vs, Function<String, Stream> appendFileReaderCallback) {
        if (!Path.getExtension(fileName).equalsIgnoreCase(".xml")) {
            byte[] srcBuf = File.readAllBytes(fileName);
            if (srcBuf == null || srcBuf.length < 1) {
                logger.log(Level.WARNING, "empty source");
                return;
            }
            init(fileName, srcBuf, oPNAWrite, sampleRate,
                    dop, vs,
                    appendFileReaderCallback == null ? createAppendFileReaderCallback(Path.getDirectoryName(fileName)) : appendFileReaderCallback
            );
        } else {
            try (InputStream sr = Files.newInputStream(java.nio.file.Path.of(fileName))) {
                List<MmlDatum> s = Serdes.Util.deserialize(sr, new ArrayList<>());
                init(fileName, s.toArray(MmlDatum[]::new), oPNAWrite, sampleRate,
                        dop, vs, appendFileReaderCallback == null ? createAppendFileReaderCallback(Path.getDirectoryName(fileName)) : appendFileReaderCallback
                );
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Override
    public void init(List<ChipAction> list, MmlDatum[] mmlData, Function<String, Stream> function, Object... objects) {
        if (mmlData != null && mmlData.length >= 1) {
            Driver.srcBuf = mmlData;
            writeOPL4 = list.getFirst()::writeRegister;
            String path = (String) objects[0];
            double sampleRate = (double) objects[1];
            int dummy = (int) objects[2];
            getTags();
            String text = Path.combine(Path.getDirectoryName(path), Path.getFileNameWithoutExtension(path) + ".pcm");
            byte[] array = null;
            try (Stream stream = function.apply(text)) {
                array = Common.readAllBytes(stream);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            md = new MoonDriver();
            if (array != null) {
                md.ExtendFile = new Tuple<>(text, array);
            }
            md.init(srcBuf, this::writeRegister, sampleRate);
        } else {
            logger.log(Level.WARNING, "empty source");
        }
    }

    public void init(
            String fileName,
            byte[] srcBuf,
            Consumer<ChipDatum> opnaWrite,
            double sampleRate,
            MoonDriverJavaOption additionalPMDDotNETOption, String[] additionalPMDOption,
            Function<String, Stream> appendFileReaderCallback
    ) {
        if (srcBuf == null || srcBuf.length < 1) {
logger.log(Level.WARNING, "empty source");
            return;
        }
        List<MmlDatum> bl = new ArrayList<>();
        for (byte b : srcBuf) bl.add(new MmlDatum(b & 0xff));
        init(fileName, bl.toArray(MmlDatum[]::new), opnaWrite, sampleRate,
                additionalPMDDotNETOption, additionalPMDOption, appendFileReaderCallback);
    }

    public void init(
            String fileName,
            MmlDatum[] srcBuf,
            Consumer<ChipDatum> opl4Write,
            double sampleRate,
            MoonDriverJavaOption addtionalPMDDotNETOption, String[] addtionalPMDOption,
            Function<String, Stream> appendFileReaderCallback
    ) {
        if (srcBuf == null || srcBuf.length == 0) {
logger.log(Level.WARNING, "empty source");
            return;
        }

        Driver.srcBuf = srcBuf;

        writeOPL4 = opl4Write;

        //work = new PW();
        getTags();
        //addtionalPMDDotNETOption.PPCHeader = CheckPPC(appendFileReaderCallback);

        //work.SetOption(addtionalPMDDotNETOption, addtionalPMDOption);
        //work.timer = new OPNATimer(44100, 7987200);

        String pcmFn = Path.combine(Path.getDirectoryName(fileName), Path.getFileNameWithoutExtension(fileName) + ".pcm");
        byte[] pcmData = null;
        try (Stream s = appendFileReaderCallback.apply(pcmFn)) {
            pcmData = Common.readAllBytes(s);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        //

        md = new MoonDriver();
        if (pcmData != null) md.ExtendFile = new Tuple<>(pcmFn, pcmData);
        md.init(srcBuf, this::writeRegister, sampleRate);

        //if (!StringUtilities.isNullOrEmpty(pmd.pw.ppz1File) || !StringUtilities.isNullOrEmpty(pmd.pw.ppz2File)) pmd.pcmload.ppz_load(pmd.pw.ppz1File, pmd.pw.ppz2File);
    }

    public void init(String fileName,
                     Consumer<ChipDatum> chipWriteRegister,
                     BiConsumer<Long, Integer> chipWaitSend,
                     MmlDatum[] srcBuf,
                     Object additionalOption) {
        if (srcBuf == null || srcBuf.length < 1) {
logger.log(Level.WARNING, "empty source");
            return;
        }

        Driver.srcBuf = srcBuf;
        writeOPL4 = chipWriteRegister;
        Object[] addOp = (Object[]) additionalOption;
        Function<String, Stream> appendFileReaderCallback = (Function<String, Stream>) addOp[1];
        double sampleRate = (double) addOp[2];

        getTags();

        String pcmFn = Path.combine(Path.getDirectoryName(fileName), Path.getFileNameWithoutExtension(fileName) + ".pcm");
        byte[] pcmData = null;
        try (Stream s = appendFileReaderCallback.apply(pcmFn)) {
            pcmData = Common.readAllBytes(s);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        //

        md = new MoonDriver();
        if (pcmData != null) md.ExtendFile = new Tuple<>(pcmFn, pcmData);
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

    private static Function<String, Stream> createAppendFileReaderCallback(String dir) {
        return fname -> {
            if (!StringUtilities.isNullOrEmpty(dir)) {
                var path = Path.combine(dir, fname);
                if (File.exists(path)) {
                    return new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read);
                }
            }
            if (File.exists(fname)) {
                return new FileStream(fname, FileMode.Open, FileAccess.Read, FileShare.Read);
            }
            return null;
        };
    }
}
