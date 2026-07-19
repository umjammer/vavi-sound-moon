package moonDriver.player;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import mdsound.Instrument;
import mdsound.MDSound;
import mdsound.instrument.YmF278BInst;
import moonDriver.common.Common;
import moonDriver.driver.Driver;
import moonDriver.driver.MoonDriverJavaOption;
import musicDriverInterface.ChipDatum;
import musicDriverInterface.IDriver;
import musicDriverInterface.MmlDatum;
import vavi.util.compat.Tuple;
import vavi.util.serdes.Serdes;

import static java.lang.System.getLogger;
import static vavi.sound.SoundUtil.volume;
import static vavi.util.compat.Util.getExtension;
import static vavi.util.compat.Util.isNullOrEmpty;


/**
 * System properties
 * <li>{@code moonDriver.opt} ... </li>
 * <li>{@code moonDriver.moonDriver} ... </li>
 */
public class Program {

    private static final Logger logger = getLogger(Program.class.getName());

    static class KeyboardHook {

        static final AtomicBoolean typed = new AtomicBoolean();

        static {
            try {
                GlobalScreen.registerNativeHook();
            } catch (NativeHookException e) {
                throw new IllegalStateException("There was a problem registering the native hook.", e);
            }
            GlobalScreen.addNativeKeyListener(new NativeKeyListener() {
                @Override
                public void nativeKeyTyped(NativeKeyEvent nativeEvent) {
                    typed.set(true);
                }
            });
        }

        static boolean kbhit() {
            return typed.get();
        }
    }

    private SourceDataLine audioOutput = null;

    private Thread trdMain = null;
    public boolean trdClosed = false;

    private static final int SamplingRate = 55467; // 44100;
    private static final int samplingBuffer = 1024;
    private final short[] frames = new short[samplingBuffer * 4];
    private mdsound.MDSound mds = null;
    private final short[] emuRenderBuf = new short[2];
    private IDriver drv = null;
    private static final int opl4MasterClock = 33868800;
    private int device = 0;
    private int loop = 0;
//    private NScci.NScci nScci;
//    private Nc86ctl nc86ctl;
//    private RSoundChip rsc;

    private boolean isGimicOPNA = false;
    private String[] envMoonDriver = null;
    private String[] envMoonDriverOpt = null;
    private String srcFile = null;

    public static void main(String[] args) {
        Program app = new Program();
        int fnIndex = app.analyzeOption(args);
        int mIndex = -1;

        if (args != null) {
            for (int i = fnIndex; i < args.length; i++) {
                if (!getExtension(args[i]).toLowerCase().contains(Common.objExtension) &&
                        !getExtension(args[i]).toUpperCase().contains(".XML")
                ) continue;
                mIndex = i;
                break;
            }
        }

        if (mIndex < 0) {
            System.err.printf("at least one argument is needed (%s file)...".formatted(Common.objExtension));
            System.exit(-1);
        }

        app.srcFile = args[mIndex];

        if (!Files.exists(Path.of(args[mIndex]))) {
            System.err.printf("File [%s] not found".formatted(args[mIndex]));
            System.exit(-1);
        }

        app.play(args, mIndex, fnIndex);
    }

    /** */
    void play(String[] args, int mIndex, int fnIndex) {
//        rsc = checkDevice();

        try {
            int latency = 1000;

            switch (device) {
                case 0:
                    audioOutput = AudioSystem.getSourceDataLine(new AudioFormat(SamplingRate, 16, 2, true, false));
                    audioOutput.open();
                    volume(audioOutput, Double.parseDouble(System.getProperty("moon.volume", "0.2")));
                    audioOutput.start();
                    break;
                case 1:
                case 2:
//                    trdMain = new Thread(Program::RealCallback);
//                    trdMain.setPriority(Thread.MAX_PRIORITY);
//                    trdMain.setDaemon(true);
//                    trdMain.setName("trdVgmReal");
//                    sw = StopWatch.startNew();
//                    swFreq = StopWatch.Frequency;
                    break;
            }

            Instrument ymf278b = Instrument.getInstrument(YmF278BInst.class);
            MDSound.Chip chip = new MDSound.Chip();
            chip.id = 0;
            chip.instrument = ymf278b;
            chip.samplingRate = SamplingRate;
            chip.clock = opl4MasterClock;
            chip.volume = 0;
            chip.option = new Object[] {getApplicationFolder()};

            mds = new MDSound();
            mds.init(SamplingRate, 1024, List.of(chip));
            //ppz8em = new PPZ8em(SamplingRate);
            //ppsdrv = new PPSDRV(SamplingRate);

            envMoonDriver = System.getProperty("moonDriver.moonDriver", "").split(";");
            envMoonDriverOpt = System.getProperty("moonDriver.opt", "").split(";");

            List<String> opt = new ArrayList<>(envMoonDriverOpt == null ? new ArrayList<>() : List.of(envMoonDriverOpt));
            opt.addAll(Arrays.asList(args).subList(fnIndex, args.length));
            mIndex += (envMoonDriverOpt == null ? 0 : envMoonDriverOpt.length) - fnIndex;

            drv = new Driver();
            MoonDriverJavaOption dop = new MoonDriverJavaOption();
//            dop.isAUTO = isAUTO;
//            dop.isNRM = isNRM;
//            dop.isSPB = isSPB;
//            dop.isVA = isVA;
//            dop.usePPS = usePPS;
//            dop.usePPZ = usePPZ;
//            dop.isLoadADPCM = false;
//            dop.loadADPCMOnly = false;
//            dop.ppz8em = ppz8em;
//            dop.ppsdrv = ppsdrv;
//            dop.envPmd = envPmd;
//            dop.srcFile = srcFile;
//            dop.jumpIndex = -1;// 112;// -1;
            List<String> pop = new ArrayList<>();
//            boolean pmdvolFound = false;
            for (int i = 0; i < opt.size(); i++) {
                if (i == mIndex) continue;
                String op = opt.get(i).toUpperCase().trim();
                pop.add(op);
//                if (op.indexOf("-D") >= 0 || op.indexOf("/D") >= 0)
//                    pmdvolFound = true;
            }

            logger.log(Level.INFO, "");

            Consumer<ChipDatum> opnaWrite = this::writeOPL4;
            List<MmlDatum> s;
            if (!getExtension(srcFile).equalsIgnoreCase(".xml")) {
                byte[] srcBuf = Files.readAllBytes(Path.of(srcFile));
                if (srcBuf.length < 1) throw new IllegalArgumentException("empty source");

                s = new ArrayList<>();
                for (byte b : srcBuf) s.add(new MmlDatum(b & 0xff));
            } else {
                try (InputStream sr = Files.newInputStream(Path.of(srcFile))) {
                    s = Serdes.Util.deserialize(sr, new ArrayList<>());
                } catch (java.io.IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            drv.init(null, s.toArray(MmlDatum[]::new), this::appendFileReaderCallback,
                    srcFile,
                    (double) SamplingRate,
                    0,
                    opnaWrite
            );

            //if (!StringUtilities.isNullOrEmpty(pmd.pw.ppz1File) || !StringUtilities.isNullOrEmpty(pmd.pw.ppz2File)) pmd.pcmload.ppz_load(pmd.pw.ppz1File, pmd.pw.ppz2File);

//            // When AUTO is specified, the configuration will change, so the volume will be set after receiving the configuration information.
//            isNRM = dop.isNRM;
//            isSPB = dop.isSPB;
//            isVA = dop.isVA;
//            usePPS = dop.usePPS;
//            usePPZ = dop.usePPZ;
//            String[] pmdOptionVol = setVolume();
//            // Apply pmdVol if user does not specify D option on command line
//            if (!pmdvolFound && pmdOptionVol != null && pmdOptionVol.length > 0) {
//                ((Driver) drv).resetOption(pmdOptionVol); //
//            }

            List<Tuple<String, String>> tags = drv.getTags();
            if (tags != null) {
                for (Tuple<String, String> tag : tags) {
                    if (Objects.equals(tag.getItem1(), "")) continue;
                    logger.log(Level.INFO, "%-16s : %s".formatted(tag.getItem1(), tag.getItem2()), 16 + 3);
                }
            }

            logger.log(Level.INFO, "");

            drv.startRendering(SamplingRate, new Tuple<>("YMF278B", opl4MasterClock));

            drv.startMusic(0);

            switch (device) {
                case 0:
                    trdMain = new Thread(this::audioLoop);
                    trdMain.start();
                    audioOutput.start();
                    break;
                case 1:
                case 2:
                    trdMain.start();
                    break;
            }

            logger.log(Level.INFO, "To end the playback, press any key (especially when playing a real chip).");

            while (true) {
                Thread.sleep(1);
                if (KeyboardHook.kbhit()) {
                    break;
                }
                // If the status is 0 (finished) or less than 0 (error), exit the loop and
                if (drv.getStatus() <= 0) {
                    if (drv.getStatus() == 0) {
                        Thread.sleep((int) (latency * 2.0)); // Wait for latency*2 until the actual voice is fully pronounced
                    }
                    break;
                }

                if (loop != 0 && drv.getNowLoopCounter() > loop) {
                    Thread.sleep((int) (latency * 2.0)); // Wait for latency*2 until the actual voice is fully pronounced
                    break;
                }
            }

            drv.stopMusic();
            drv.stopRendering();
            ((Driver) drv).dispStatus();
//        } catch (MoonDriverException pe) {
//            logger.log(Level.ERROR, pe.getMessage());
        } catch (Exception ex) {
            logger.log(Level.ERROR, "Failed to play: " + ex.getMessage(), ex);
        } finally {
            if (((Driver) drv).renderingException != null) {
                logger.log(Level.ERROR, "Failed to play: " + ((Driver) drv).renderingException.getMessage(), ((Driver) drv).renderingException);
            }

            if (audioOutput != null) {
                audioOutput.drain();
                audioOutput.stop();
                audioOutput.close();
                audioOutput = null;
            }
            if (trdMain != null) {
                trdClosed = true;
                try {
                    trdMain.join();
                } catch (InterruptedException _) {
                }
            }
//            if (nc86ctl != null) {
//                nc86ctl.deinitialize();
//                nc86ctl = null;
//            }
//            if (nScci != null) {
//                nScci.Dispose();
//                nScci = null;
//            }
        }
    }

    private static Function<String, InputStream> createAppendFileReaderCallback(String dir) {
        return fname -> {
            try {
                if (!isNullOrEmpty(dir)) {
                    var path = Path.of(dir, fname);
                    if (Files.exists(path)) {
                        return Files.newInputStream(path);
                    }
                }
                if (Files.exists(Path.of(fname))) {
                    return Files.newInputStream(Path.of(fname));
                }
            } catch (IOException e) {
                logger.log(Level.INFO, e.toString());
            }
            return null;
        };
    }

    public static String getApplicationFolder() {
        String path = System.getProperty("user.dir");
        if (!isNullOrEmpty(path)) {
            path += path.charAt(path.length() - 1) == '/' ? "" : "/";
        }
        return path;
    }

    private InputStream appendFileReaderCallback(String arg) {
        Path fn = Path.of(srcFile).getParent().resolve(arg);

        if (envMoonDriver != null) {
            int i = 0;
            while (!Files.exists(fn) && i < envMoonDriver.length) {
                fn = Path.of(envMoonDriver[i++], arg);
            }
        }

        if (!Files.exists(fn)) {
            // the original works on a case-insensitive filesystem (e.g. "TIMESUP.PCM" is found as "TIMESUP.pcm")
            fn = resolveIgnoreCase(Path.of(arg));
        }

        if (!Files.exists(fn)) {
logger.log(Level.INFO, "file not found: " + fn);
            return null;
        }

        InputStream strm;
        try {
            strm = Files.newInputStream(fn);
        } catch (java.io.IOException e) {
logger.log(Level.ERROR, e.getMessage(), e);
            strm = null;
        }

        return strm;
    }

    /** finds an existing file whose name matches ignoring case, for case-sensitive filesystems */
    private static Path resolveIgnoreCase(Path path) {
        Path dir = path.getParent() != null ? path.getParent() : Path.of(".");
        if (!Files.isDirectory(dir)) return path;
        String name = path.getFileName().toString();
        try (var files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().equalsIgnoreCase(name)).findFirst().orElse(path);
        } catch (IOException e) {
            return path;
        }
    }

    private int analyzeOption(String[] args) {
        if (args == null || args.length < 1) return 0;

        int i = 0;
        device = 0;
        loop = 0;

        while (i < args.length && args[i] != null && !args[i].isEmpty() && (args[i].charAt(0) == '-' || args[i].charAt(0) == '/')) {
            String op = args[i].substring(1).toUpperCase();
            if (op.equals("D=EMU")) device = 0;
            else if (op.equals("D=GIMIC")) device = 1;
            else if (op.equals("D=SCCI")) device = 2;
            else if (op.equals("D=WAVE")) device = 3;
                //else if (op.length > 2 && op.substring(0, 2) == "L=") OptionSetLoop(op);
                //else if (op == "H" || op == "?") OptionDispHelp();
            else break;

            i++;
        }

        if (device == 3 && loop == 0) loop = 1; // For wave output, change infinite loop to 1
        return i;
    }

//    private RSoundChip checkDevice() {
//        moonDriver.Player.SChipType ct = null;
//        int iCount = 0;
//
//        switch (device) {
//            case 1: { // GIMIC existence check
//                nc86ctl = new Nc86ctl.Nc86ctl();
//                try {
//                    nc86ctl.initialize();
//                    iCount = nc86ctl.getNumberOfChip();
//                } catch (Exception e) {
//                    iCount = 0;
//                }
//                if (iCount == 0) {
//                    try {
//                        nc86ctl.deinitialize();
//                    } catch (Exception e) {
//                    }
//                    nc86ctl = null;
//                    logger.log(Level.ERROR, "Not found G.I.M.I.C");
//                    device = 0;
//                    break;
//                }
//                for (int i = 0; i < iCount; i++) {
//                    NIRealChip rc = nc86ctl.getChipInterface(i);
//                    NIGimic2 gm = rc.QueryInterface();
//                    ChipType cct = gm.getModuleType();
//                    int o = -1;
//                    if (cct == ChipType.CHIP_OPL3) {
//                        ct = new moonDriver.Player.SChipType();
//                        ct.SoundLocation = -1;
//                        ct.BusID = i;
//                        String seri = gm.getModuleInfo().Serial;
//                        try {
//                            o = Integer.parseInt(seri);
//                        } catch (NumberFormatException e) {
//                            o = -1;
//                            ct = null;
//                            continue;
//                        }
//                        ct.SoundChip = o;
//                        ct.ChipName = gm.getModuleInfo().Devname;
//                        ct.InterfaceName = gm.getMBInfo().Devname;
//                        //isGimicOPNA = (ct.ChipName == "GMC-OPNA");
//                        break;
//                    }
//                }
//                RC86ctlSoundChip rsc = null;
//                if (ct == null) {
//                    nc86ctl.deinitialize();
//                    nc86ctl = null;
//                    logger.log(Level.ERROR, "Not found G.I.M.I.C(OPNA module)");
//                    device = 0;
//                } else {
//                    rsc = new RC86ctlSoundChip(-1, ct.BusID, ct.SoundChip);
//                    rsc.c86ctl = nc86ctl;
//                    rsc.init();
//
//                    rsc.SetMasterClock(opl4MasterClock); // SoundBoardII
//                    //rsc.setSSGVolume(63); // PC-8801
//                }
//                return rsc;
//                case 2: // SCCI Presence Check
//                    nScci = new NScci.NScci();
//                    iCount = NScci.NSoundInterfaceManager().getInterfaceCount();
//                    if (iCount == 0) {
//                        nScci.Dispose();
//                        nScci = null;
//                        logger.log(Level.ERROR, "Not found SCCI.");
//                        device = 0;
//                        break;
//                    }
//scciExit:
//                    for (int i = 0; i < iCount; i++) {
//                        NSoundInterface iIntfc = NScci.NSoundInterfaceManager().getInterface(i);
//                        NSCCI_INTERFACE_INFO iInfo = NScci.NSoundInterfaceManager().getInterfaceInfo(i);
//                        int sCount = iIntfc.getSoundChipCount();
//                        for (int s = 0; s < sCount; s++) {
//                            NSoundChip sc = iIntfc.getSoundChip(s);
//                            int t = sc.getSoundChipType();
//                            if (t == 1) {
//                                ct = new moonDriver.Player.SChipType();
//                                ct.SoundLocation = 0;
//                                ct.BusID = i;
//                                ct.SoundChip = s;
//                                ct.ChipName = sc.getSoundChipInfo().cSoundChipName;
//                                ct.InterfaceName = iInfo.cInterfaceName;
//                                break scciExit;
//                            }
//                        }
//                    }
//
//                    RScciSoundChip rssc = null;
//                    if (ct == null) {
//                        nScci.Dispose();
//                        nScci = null;
//                        logger.log(Level.ERROR, "Not found SCCI(OPNA module).");
//                        device = 0;
//                    } else {
//                        rssc = new RScciSoundChip(0, ct.BusID, ct.SoundChip);
//                        rssc.scci = nScci;
//                        rssc.init();
//                    }
//                    return rssc;
//            }
//
//            return null;
//        }

    private int emuCallback(short[] buffer, int offset, int count) {
        try {
            long bufCnt = count / 2;

            for (int i = 0; i < bufCnt; i++) {
                mds.update(emuRenderBuf, 0, 2, this::oneFrame);

                buffer[offset + i * 2 + 0] = emuRenderBuf[0];
                buffer[offset + i * 2 + 1] = emuRenderBuf[1];

            }
        } catch (Exception ex) {
            logger.log(Level.ERROR, ex.getMessage(), ex);
        }

        return count;
    }

//        private void realCallback() {
//
//            double o = sw.elapsedTicks / swFreq;
//            double step = 1 / (double) samplingRate;
//
//            trdStopped = false;
//            try {
//                while (!trdClosed) {
//                    Thread.sleep(0);
//
//                    double el1 = sw.ElapsedTicks / swFreq;
//                    if (el1 - o >= step) {
//                        if (el1 - o >= step * SamplingRate / 100.0) { // Threshold 10ms
//                            do {
//                                o += step;
//                            } while (el1 - o >= step);
//                        } else {
//                            o += step;
//                        }
//
//                        oneFrame();
//                    }
//
//                }
//            } catch (Exception e) {
//            }
//            trdStopped = true;
//        }

    private void audioLoop() {
        byte[] b = new byte[frames.length * 2];
        try {
            while (!trdClosed) {
                if (audioOutput.available() < frames.length * 2) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        break;
                    }
                    continue;
                }
                int count = frames.length;
                int ret = emuCallback(frames, 0, count);
                for (int i = 0; i < ret; i++) {
                    short s = frames[i];
                    b[i * 2] = (byte) (s & 0xff);
                    b[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
                }
                audioOutput.write(b, 0, ret * 2);
            }
        } catch (Exception e) {
            logger.log(Level.ERROR, e.getMessage(), e);
        }
    }

    private void oneFrame() {
        drv.render();
    }

    private void writeOPL4(ChipDatum dat) {
        if (dat != null && dat.additionalData != null) {
            MmlDatum md = (MmlDatum) dat.additionalData;
            if (md.linePos != null) {
                logger.log(Level.TRACE, "! r%d c%d".formatted(md.linePos.row, md.linePos.col));
            }
        }

//#if DEBUG
        //if (dat.address == 0x29)
        logger.log(Level.TRACE, "FM P%d Out:Adr[%02x] val[%02x]".formatted(dat.address, dat.data, dat.port));
//#endif

        switch (device) {
            case 0:
                mds.write(YmF278BInst.class, 0, dat.port, dat.address, dat.data);
                break;
            case 1:
            case 2:
//                    rsc.setRegister(dat.port * 0x100 + dat.address, dat.data);
                break;
        }
    }

//        private void OPNAWaitSend ( long elapsed, int size){
//            switch (device) {
//                case 0: // EMU
//                    return;
//                case 1: // GIMIC
//
//                    // Add additional weight based on size and elapsed time.
//                    int m = Math.max((int) (size / 20 - elapsed), 0); // 20 Threshold(magic number)
//                    Thread.Sleep(m);
//
//                    // Check the port as well
//                    int n = nc86ctl.getNumberOfChip();
//                    for (int i = 0; i < n; i++) {
//                        NIRealChip rc = nc86ctl.getChipInterface(i);
//                        if (rc != null) {
//                            while ((rc. @in(0x0) &0x83) !=0)
//                            Thread.Sleep(0);
//                            while ((rc. @in(0x100) &0xbf) !=0)
//                            Thread.Sleep(0);
//                        }
//                    }
//
//                    break;
//                case 2: // SCCI
//                    NScci.NSoundInterfaceManager().sendData();
//                    while (!NScci.NSoundInterfaceManager().isBufferEmpty()) {
//                        Thread.Sleep(0);
//                    }
//                    break;
//            }
//        }
//    }
}
