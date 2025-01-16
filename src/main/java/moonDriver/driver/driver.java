package moonDriver.driver;

import java.nio.charset.Charset;
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
import dotnet4j.io.StreamReader;
import dotnet4j.util.compat.Tuple;
import moonDriver.common.Common;
import moonDriver.common.iEncoding;
import musicDriverInterface.ChipDatum;
import musicDriverInterface.GD3Tag;
import musicDriverInterface.IDriver;
import musicDriverInterface.MmlDatum;


public class Driver extends IDriver
    {
        private iEncoding enc = null;
        public Exception renderingException = null;
        private MoonDriver md = null;
        static MmlDatum[] srcBuf = null;
        private Consumer<ChipDatum> WriteOPL4;

        public Driver(Charset enc /* = null */)
        {
            this.enc = enc ?? myEncoding.Default;
        }

        public void fadeOut()
        {
            throw new UnsupportedOperationException();
        }

        public MmlDatum[] getDATA()
        {
            throw new UnsupportedOperationException();
        }

        public GD3Tag getGD3TagInfo(byte[] srcBuf)
        {
            throw new UnsupportedOperationException();
        }

        public int getNowLoopCounter()
        {
            throw new UnsupportedOperationException();
        }

        public byte[] getPCMFromSrcBuf()
        {
            throw new UnsupportedOperationException();
        }

        public ChipDatum[] getPCMSendData()
        {
            throw new UnsupportedOperationException();
        }

        public Tuple<String, Short[]>[] getPCMTable()
        {
            throw new UnsupportedOperationException();
        }

        public int getStatus()
        {
            return 1;
        }

        public List<Tuple<String, String>> getTags()
        {
            return null;
        }

        public Object getWork()
        {
            return null;
        }


        public void init(String  fileName, Consumer<ChipDatum> oPNAWrite, double sampleRate,
            moonDriver.Driver.MoonDriverDotNETOption dop, String[] vs, Function<String, Stream> appendFileReaderCallback)
        {
            if (Path.getExtension(fileName).toLowerCase() != ".xml")
            {
                byte[] srcBuf = File.readAllBytes(fileName);
                if (srcBuf == null || srcBuf.length < 1) return;
                Init(fileName, srcBuf, oPNAWrite, sampleRate,
                    dop, vs,
                    appendFileReaderCallback ?? CreateAppendFileReaderCallback(Path.getDirectoryName(fileName))
                    );
            }
            else
            {
                XmlSerializer serializer = new XmlSerializer(typeof(MmlDatum[]), typeof(MmlDatum[]).GetNestedTypes());
                try (StreamReader sr = new StreamReader(fileName, new UTF8Encoding(false)))
                {
                    try
                    {
                        MmlDatum[] s = (MmlDatum[])serializer.Deserialize(sr);
                        Init(fileName, s, oPNAWrite, sampleRate,
                            dop, vs, appendFileReaderCallback ?? CreateAppendFileReaderCallback(Path.getDirectoryName(fileName))
                            );
                    }
                    catch (System.InvalidOperationException e)
                    {
                    }
                }

            }
        }

        public void init(
            String  fileName,
            byte[] srcBuf,
            Consumer<ChipDatum> opnaWrite,
            double sampleRate,
            Driver.MoonDriverDotNETOption addtionalPMDDotNETOption, String[] addtionalPMDOption,
            Function<String, Stream> appendFileReaderCallback
            )
        {
            if (srcBuf == null || srcBuf.length < 1) return;
            List<MmlDatum> bl = new ArrayList<>();
            for (byte b : srcBuf) bl.add(new MmlDatum(b));
            init(fileName, bl.toArray(), opnaWrite,sampleRate,
                addtionalPMDDotNETOption, addtionalPMDOption, appendFileReaderCallback);
        }

        public void init(
            String  fileName,
            MmlDatum[] srcBuf,
            Consumer<ChipDatum> opl4Write,
            double sampleRate,
            moonDriver.Driver.MoonDriverDotNETOption addtionalPMDDotNETOption, String[] addtionalPMDOption,
            Function<String, Stream> appendFileReaderCallback
            )
        {
            if (srcBuf == null || srcBuf.length < 1) return;

            Driver.srcBuf = srcBuf;

            WriteOPL4 = opl4Write;

            //work = new PW();
            getTags();
            //addtionalPMDDotNETOption.PPCHeader = CheckPPC(appendFileReaderCallback);

            //work.SetOption(addtionalPMDDotNETOption, addtionalPMDOption);
            //work.timer = new OPNATimer(44100, 7987200);

            String  pcmFn = Path.combine(Path.getDirectoryName(fileName), Path.getFileNameWithoutExtension(fileName)+".pcm");
            byte[] pcmData = null;
            try (Stream s = appendFileReaderCallback(pcmFn))
            {
                pcmData = Common.ReadAllBytes(s);
            }

            //

            md = new MoonDriver();
            if (pcmData != null) md.ExtendFile = new Tuple<String, byte[]>(pcmFn, pcmData);
            md.init(srcBuf, WriteRegister, sampleRate);

            //if (!StringUtilities.isNullOrEmpty(pmd.pw.ppz1File) || !StringUtilities.isNullOrEmpty(pmd.pw.ppz2File)) pmd.pcmload.ppz_load(pmd.pw.ppz1File, pmd.pw.ppz2File);

        }

        public void init(String  fileName
            , Consumer<ChipDatum> chipWriteRegister
            , BiConsumer<Long, Integer> chipWaitSend
            , MmlDatum[] srcBuf
            , Object additionalOption)
        {
            if (srcBuf == null || srcBuf.length < 1) return;

            Driver.srcBuf = srcBuf;
            WriteOPL4 = chipWriteRegister;
            Object[] addOp = (Object[])additionalOption;
            Function<String, Stream> appendFileReaderCallback = (Function<String, Stream>)addOp[1];
            double sampleRate = (double)addOp[2];

            getTags();

            String  pcmFn = Path.combine(Path.getDirectoryName(fileName), Path.getFileNameWithoutExtension(fileName) + ".pcm");
            byte[] pcmData = null;
            try (Stream s = appendFileReaderCallback(pcmFn))
            {
                pcmData = Common.ReadAllBytes(s);
            }

            //

            md = new MoonDriver();
            if (pcmData != null) md.ExtendFile = new Tuple<String, byte[]>(pcmFn, pcmData);
            md.init(srcBuf, WriteRegister, sampleRate);
        }

        public void MusicSTART(int musicNumber)
        {
        }

        public void MusicSTOP()
        {
        }

        public void Rendering()
        {
            md.oneFrameProc();
        }

        public void SetDriverSwitch(Object... param)
        {
            throw new UnsupportedOperationException();
        }

        public int SetLoopCount(int loopCounter)
        {
            throw new UnsupportedOperationException();
        }

        public void ShotEffect()
        {
            throw new UnsupportedOperationException();
        }

        public void StartRendering(int renderingFreq, Tuple<String, Integer>[] chipsMasterClock)
        {
        }

        public void StopRendering()
        {
        }

        public void WriteRegister(ChipDatum reg)
        {
            WriteOPL4(reg);
        }

        public void dispStatus()
        {
        }

        private static Function<String, Stream> CreateAppendFileReaderCallback(String  dir)
        {
            return fname =>
            {
                if (!StringUtilities.isNullOrEmpty(dir))
                {
                    var path = Path.combine(dir, fname);
                    if (File.Exists(path))
                    {
                        return new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read);
                    }
                }
                if (File.Exists(fname))
                {
                    return new FileStream(fname, FileMode.Open, FileAccess.Read, FileShare.Read);
                }
                return null;
            };
        }

    }
}
