package moonDriver.player;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import dotnet4j.io.File;
import dotnet4j.io.FileAccess;
import dotnet4j.io.FileMode;
import dotnet4j.io.FileShare;
import dotnet4j.io.FileStream;
import dotnet4j.io.IOException;
import dotnet4j.io.Path;
import dotnet4j.io.Stream;
import dotnet4j.util.compat.StopWatch;
import dotnet4j.util.compat.StringUtilities;
import dotnet4j.util.compat.TriFunction;
import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.MDSound;
import mdsound.instrument.YmF278bInst;
import moonDriver.common.Common;
import moonDriver.common.Environment;
import moonDriver.driver.Driver;
import moonDriver.driver.MoonDriverJavaOption;
import musicDriverInterface.ChipDatum;
import musicDriverInterface.MmlDatum;

import static java.lang.System.getLogger;
import static vavi.sound.SoundUtil.volume;


public class Program {

    private static final Logger logger = getLogger(Program.class.getName());

    static class KeyboardHook {

        static AtomicBoolean typed = new AtomicBoolean();

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

    private static SourceDataLine audioOutput = null;

    public interface naudioCallBack extends TriFunction<short[], Integer, Integer, Integer> {

    }

    private static Program.naudioCallBack callBack = null;
    private static Thread trdMain = null;
    private static StopWatch sw = null;
    private static double swFreq = 0;
    public static boolean trdClosed = false;
    private static final Object lockObj = new Object();
    private static boolean _trdStopped = true;
    private static boolean trdStopped;

    private static boolean getTrdStopped() {
        synchronized (lockObj) {
            return _trdStopped;
        }
    }

    private static void setTrdStopped(boolean value) {
        synchronized (lockObj) {
            _trdStopped = value;
        }
    }

    private static final int SamplingRate = 55467; // 44100;
    private static final int samplingBuffer = 1024;
    private static short[] frames = new short[samplingBuffer * 4];
    private static mdsound.MDSound mds = null;
    private static short[] emuRenderBuf = new short[2];
    private static musicDriverInterface.IDriver drv = null;
    private static final int opl4MasterClock = 33868800;
    private static int device = 0;
    private static int loop = 0;
//    private static NScci.NScci nScci;
//    private static Nc86ctl.Nc86ctl nc86ctl;
//    private static RSoundChip rsc;

    private static boolean isGimicOPNA = false;
    private static String[] envMoonDriver = null;
    private static String[] envMoonDriverOpt = null;
    private static String srcFile = null;

    static int Main(String[] args) {
        int fnIndex = analyzeOption(args);
        int mIndex = -1;

        if (args != null) {
            for (int i = fnIndex; i < args.length; i++) {
                if ((Path.getExtension(args[i]).toLowerCase().indexOf(Common.objExtension) < 0)
                        && (Path.getExtension(args[i]).toUpperCase().indexOf(".XML") < 0)
                ) continue;
                mIndex = i;
                break;
            }
        }

        if (mIndex < 0) {
            logger.log(Level.INFO, String.format("引数(%dファイル)１個欲しいよぉ...", Common.objExtension));
            return -1;
        }

        srcFile = args[mIndex];

        if (!File.exists(args[mIndex])) {
            logger.log(Level.ERROR, String.format("ファイル[%d]が見つかりません", args[mIndex]));
            return -1;
        }

//        rsc = CheckDevice();

        try {

            int latency = 1000;

            switch (device) {
                case 0:
//                    waveProvider = new SineWaveProvider16();
//                    waveProvider.SetWaveFormat((int)SamplingRate, 2);
                    callBack = Program::emuCallback;
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

            Instrument ymf278b = Instrument.getInstrument(YmF278bInst.class);
            MDSound.Chip chip = new MDSound.Chip();
            chip.id = 0;
            chip.instrument = ymf278b;
            chip.samplingRate = SamplingRate;
            chip.clock = opl4MasterClock;
            chip.volume = 0;
            chip.option = new Object[] {getApplicationFolder()};

            mds = new MDSound(SamplingRate, samplingBuffer, new MDSound.Chip[] {chip});
            //ppz8em = new PPZ8em(SamplingRate);
            //ppsdrv = new PPSDRV(SamplingRate);

            Environment env = new Environment();
            env.AddEnv("moondriver");
            env.AddEnv("moondriveropt");
            envMoonDriver = env.GetEnvVal("moondriver");
            envMoonDriverOpt = env.GetEnvVal("moondriveropt");

            List<String> opt = envMoonDriverOpt == null ? new ArrayList<>() : List.of(envMoonDriverOpt);
            for (int i = fnIndex; i < args.length; i++) {
                opt.add(args[i]);
            }
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

            ((Driver) drv).init(
                    srcFile
                    , Program::OPL4Write
                    , SamplingRate
                    , dop
                    , pop.toArray(String[]::new)
                    , Program::appendFileReaderCallback
            );

//            //AUTO指定の場合に構成が変わるので、構成情報を受け取ってから音量設定を行う
//            isNRM = dop.isNRM;
//            isSPB = dop.isSPB;
//            isVA = dop.isVA;
//            usePPS = dop.usePPS;
//            usePPZ = dop.usePPZ;
//            String[] pmdOptionVol = SetVolume();
//            //ユーザーがコマンドラインでDオプションを指定していない場合はpmdVolを適用させる
//            if (!pmdvolFound && pmdOptionVol != null && pmdOptionVol.length > 0) {
//                ((Driver.Driver) drv).resetOption(pmdOptionVol);//
//            }

            List<Tuple<String, String>> tags = drv.getTags();
            if (tags != null) {
                for (Tuple<String, String> tag : tags) {
                    if (Objects.equals(tag.getItem1(), "")) continue;
                    logger.log(Level.INFO, String.format("{0,-16} : %d", tag.getItem1(), tag.getItem2()), 16 + 3);
                }
            }

            logger.log(Level.INFO, "");

            drv.startRendering((int) SamplingRate, new Tuple[] {new Tuple<>("YMF278B", opl4MasterClock)});

            drv.startMusic(0);

            switch (device) {
                case 0:
                    audioOutput.start();
                    break;
                case 1:
                case 2:
                    trdMain.start();
                    break;
            }

            logger.log(Level.INFO, "演奏を終了する場合は何かキーを押してください(実chip時は特に。)");

            while (true) {
                Thread.sleep(1);
                if (KeyboardHook.kbhit()) {
                    break;
                }
                //ステータスが0(終了)又は0未満(エラー)の場合はループを抜けて終了
                if (drv.getStatus() <= 0) {
                    if (drv.getStatus() == 0) {
                        Thread.sleep((int) (latency * 2.0)); // 実際の音声が発音しきるまでlatency*2の分だけ待つ
                    }
                    break;
                }

                if (loop != 0 && drv.getNowLoopCounter() > loop) {
                    Thread.sleep((int) (latency * 2.0)); // 実際の音声が発音しきるまでlatency*2の分だけ待つ
                    break;
                }
            }

            drv.stopMusic();
            drv.stopRendering();
            ((Driver) drv).dispStatus();
//        } catch (MoonDriverException pe) {
//            logger.log(Level.ERROR, pe.getMessage());
        } catch (Exception ex) {
            logger.log(Level.ERROR, "演奏失敗: " + ex.getMessage(), ex);
        } finally {
            if (((Driver) drv).renderingException != null) {
                logger.log(Level.ERROR, "演奏失敗: " + ((Driver) drv).renderingException.getMessage(), ((Driver) drv).renderingException);
            }

            if (audioOutput != null) {
                audioOutput.drain();
                audioOutput.stop();
                audioOutput.close();
                audioOutput = null;
            }
            if (trdMain != null) {
                trdClosed = true;
                while (!trdStopped) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                    }
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

        return 0;
    }

    public static String getApplicationFolder() {
        String path = Path.getDirectoryName(System.getProperty("user.dir"));
        if (!StringUtilities.isNullOrEmpty(path)) {
            path += (path.charAt(path.length() - 1) == '/' ? "" : "/");
        }
        return path;
    }

    private static Stream appendFileReaderCallback(String arg) {
        String fn;
        fn = Path.combine(
                Path.getDirectoryName(srcFile)
                , arg
        );

        if (envMoonDriver != null) {
            int i = 0;
            while (!File.exists(fn) && i < envMoonDriver.length) {
                fn = Path.combine(
                        envMoonDriver[i++]
                        , arg
                );
            }
        }

        if (!File.exists(fn)) return null;

        FileStream strm;
        try {
            strm = new FileStream(fn, FileMode.Open, FileAccess.Read, FileShare.Read);
        } catch (IOException e) {
            strm = null;
        }

        return strm;
    }

    private static int analyzeOption(String[] args) {
        if (args == null || args.length < 1) return 0;

        int i = 0;
        device = 0;
        loop = 0;

        while (i < args.length && args[i] != null && args[i].length() > 0 && (args[i].charAt(0) == '-' || args[i].charAt(0) == '/')) {
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

        if (device == 3 && loop == 0) loop = 1; // wave出力の場合、無限ループは1に変更
        return i;
    }

//    private static RSoundChip CheckDevice() {
//        moonDriver.Player.SChipType ct = null;
//        int iCount = 0;
//
//        switch (device) {
//            case 1: { //GIMIC存在チェック
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
//                case 2: // SCCI存在チェック
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

    private static int emuCallback(short[] buffer, int offset, int count) {
        try {
            long bufCnt = count / 2;

            for (int i = 0; i < bufCnt; i++) {
                mds.update(emuRenderBuf, 0, 2, Program::OneFrame);

                buffer[offset + i * 2 + 0] = emuRenderBuf[0];
                buffer[offset + i * 2 + 1] = emuRenderBuf[1];

            }
        } catch (Exception ex) {
            logger.log(Level.ERROR, String.format("%d %d", ex.getMessage(), ex.getStackTrace()));
        }

        return count;
    }

//        private static void RealCallback () {
//
//            double o = sw.ElapsedTicks / swFreq;
//            double step = 1 / (double) SamplingRate;
//
//            trdStopped = false;
//            try {
//                while (!trdClosed) {
//                    Thread.Sleep(0);
//
//                    double el1 = sw.ElapsedTicks / swFreq;
//                    if (el1 - o >= step) {
//                        if (el1 - o >= step * SamplingRate / 100.0) // 閾値10ms
//                        {
//                            do {
//                                o += step;
//                            } while (el1 - o >= step);
//                        } else {
//                            o += step;
//                        }
//
//                        OneFrame();
//                    }
//
//                }
//            } catch (Exception e) {
//            }
//            trdStopped = true;
//        }

    private static void OneFrame() {
        drv.render();
    }

    private static void OPL4Write(ChipDatum dat) {
        if (dat != null && dat.additionalData != null) {
            MmlDatum md = (MmlDatum) dat.additionalData;
            if (md.linePos != null) {
                logger.log(Level.TRACE, String.format("! r%d c%d"
                        , md.linePos.row
                        , md.linePos.col
                ));
            }
        }

//#if DEBUG
        //if (dat.address == 0x29)
        logger.log(Level.INFO, String.format("FM P%d Out:Adr[{0:x02}] val[{1:x02}]", (int) dat.address, (int) dat.data, dat.port));
//#endif

        switch (device) {
            case 0:
                mds.write(YmF278bInst.class, 0, (byte) dat.port, (byte) dat.address, (byte) dat.data);
                break;
            case 1:
            case 2:
//                    rsc.setRegister(dat.port * 0x100 + dat.address, dat.data);
                break;
        }
    }

//        private static void OPNAWaitSend ( long elapsed, int size){
//            switch (device) {
//                case 0: // EMU
//                    return;
//                case 1: // GIMIC
//
//                    //サイズと経過時間から、追加でウエイトする。
//                    int m = Math.max((int) (size / 20 - elapsed), 0); // 20 閾値(magic number)
//                    Thread.Sleep(m);
//
//                    //ポートも一応見る
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

//    public static class SineWaveProvider16 extends WaveProvider16 {
//
//        public SineWaveProvider16() {
//        }
//
//        @Override public int Read(short[] buffer, int offset, int sampleCount) {
//                return callBack(buffer, offset, sampleCount);
//        }
//    }
}
