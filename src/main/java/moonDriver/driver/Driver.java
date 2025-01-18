package moonDriver.driver;

import java.io.IOException;
import java.io.InputStream;
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
import moonDriver.common.IEncoding;
import moonDriver.common.MyEncoding;
import musicDriverInterface.ChipAction;
import musicDriverInterface.ChipDatum;
import musicDriverInterface.GD3Tag;
import musicDriverInterface.IDriver;
import musicDriverInterface.MmlDatum;
import vavi.util.serdes.Serdes;


public class Driver implements IDriver {

    private IEncoding enc = null;
    public Exception renderingException = null;
    private MoonDriver md = null;
    static MmlDatum[] srcBuf = null;
    private Consumer<ChipDatum> WriteOPL4;

    public Driver() {
        this(null);
    }

    public Driver(IEncoding enc /* = null */) {
        this.enc = enc != null ? enc : MyEncoding.Default();
    }

    public void fadeOut() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void init(List<ChipAction> list, MmlDatum[] mmlData, Function<String, Stream> function, Object... objects) {
        // TODO
    }

    public MmlDatum[] getDATA() {
        throw new UnsupportedOperationException();
    }

    public GD3Tag getGD3TagInfo(byte[] srcBuf) {
        throw new UnsupportedOperationException();
    }

    public int getNowLoopCounter() {
        throw new UnsupportedOperationException();
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
            if (srcBuf == null || srcBuf.length < 1) return;
            init(fileName, srcBuf, oPNAWrite, sampleRate,
                    dop, vs,
                    appendFileReaderCallback == null ? createAppendFileReaderCallback(Path.getDirectoryName(fileName)) : appendFileReaderCallback
            );
        } else {
            try (InputStream sr = Files.newInputStream(java.nio.file.Path.of(fileName))) {
                List<MmlDatum> s = Serdes.Util.deserialize(sr, new ArrayList<>());
                init(fileName, s, oPNAWrite, sampleRate,
                        dop, vs, appendFileReaderCallback == null ? createAppendFileReaderCallback(Path.getDirectoryName(fileName)) : appendFileReaderCallback
                );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void init(
            String fileName,
            byte[] srcBuf,
            Consumer<ChipDatum> opnaWrite,
            double sampleRate,
            MoonDriverJavaOption addtionalPMDDotNETOption, String[] addtionalPMDOption,
            Function<String, Stream> appendFileReaderCallback
    ) {
        if (srcBuf == null || srcBuf.length < 1) return;
        List<MmlDatum> bl = new ArrayList<>();
        for (byte b : srcBuf) bl.add(new MmlDatum(b));
        init(fileName, bl, opnaWrite, sampleRate,
                addtionalPMDDotNETOption, addtionalPMDOption, appendFileReaderCallback);
    }

    public void init(
            String fileName,
            List<MmlDatum> srcBuf,
            Consumer<ChipDatum> opl4Write,
            double sampleRate,
            MoonDriverJavaOption addtionalPMDDotNETOption, String[] addtionalPMDOption,
            Function<String, Stream> appendFileReaderCallback
    ) {
        if (srcBuf == null || srcBuf.isEmpty()) return;

        Driver.srcBuf = srcBuf.toArray(MmlDatum[]::new);

        WriteOPL4 = opl4Write;

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
            throw new dotnet4j.io.IOException(e);
        }

        //

        md = new MoonDriver();
        if (pcmData != null) md.ExtendFile = new Tuple<String, byte[]>(pcmFn, pcmData);
        md.init(srcBuf.toArray(MmlDatum[]::new), this::writeRegister, sampleRate);

        //if (!StringUtilities.isNullOrEmpty(pmd.pw.ppz1File) || !StringUtilities.isNullOrEmpty(pmd.pw.ppz2File)) pmd.pcmload.ppz_load(pmd.pw.ppz1File, pmd.pw.ppz2File);
    }

    public void init(String fileName
            , Consumer<ChipDatum> chipWriteRegister
            , BiConsumer<Long, Integer> chipWaitSend
            , MmlDatum[] srcBuf
            , Object additionalOption) {
        if (srcBuf == null || srcBuf.length < 1) return;

        Driver.srcBuf = srcBuf;
        WriteOPL4 = chipWriteRegister;
        Object[] addOp = (Object[]) additionalOption;
        Function<String, Stream> appendFileReaderCallback = (Function<String, Stream>) addOp[1];
        double sampleRate = (double) addOp[2];

        getTags();

        String pcmFn = Path.combine(Path.getDirectoryName(fileName), Path.getFileNameWithoutExtension(fileName) + ".pcm");
        byte[] pcmData = null;
        try (Stream s = appendFileReaderCallback.apply(pcmFn)) {
            pcmData = Common.readAllBytes(s);
        } catch (IOException e) {
            throw new dotnet4j.io.IOException(e);
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
        WriteOPL4.accept(reg);
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
