package moonDriver.compiler;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;

import dotnet4j.io.File;
import dotnet4j.io.Path;
import dotnet4j.util.compat.StringUtilities;
import moonDriver.common.MyEncoding;

import static java.lang.System.getLogger;


public class PcmPack {

    private static final Logger logger = getLogger(PcmPack.class.getName());
    //
    // pcmpack.c
    //

    private static final String PRG_NAME = "PCMPACK";
    private static final String PRG_VER = "Ver 0.1";
    private static final String PRG_AUTHOR = "BouKiCHi";

    // MDR File Definition
    private static class _mdr {

        public int fp;
        public int size;
        public int header = 0;
        public String pcmname; // [PATH_MAX]; // pos:0x40 pcmname
        public int pcm_packed; // pos: 0x2a 1:pcm is packed
        public int pcm_startadrs; // pos:0x30 start address of PCM RAM(* 0x10000)
        public int pcm_startbank; // pos:0x31 start bank (* 8192)
        public int pcm_banks; // pos:0x32 number of PCM banks (* 8192)
        public int pcm_lastsize; // pos:0x32 size of last bank (* 0x100)

        // actual size = (pcm_banks * 0x2000) + (pcm_lastsize * 0x100)
    }

    // MDR file reading
    private int readMDRHeader(List<MmlDatum2> destBuf, String file, /* ref */ _mdr m) {
        try {
            m.size = destBuf.size();

            // PCM String Position
            m.pcmname = "";
            int pcmpos = destBuf.get(m.header + 0x2c).dat + destBuf.get(m.header + 0x2d).dat * 0x100;

            // PCM Position
            if (pcmpos == 0x8040) {
                byte[] fn = new byte[0x40];
                for (int i = 0; i < 0x40; i++) {
                    fn[i] = (byte) destBuf.get(m.header + i).dat;
                }
                MyEncoding enc = new MyEncoding();
                m.pcmname = enc.GetStringFromSjisArray(fn);
            }

            // PCM setting value
            m.pcm_packed = destBuf.get(m.header + 0x2a).dat;
            m.pcm_startadrs = destBuf.get(m.header + 0x30).dat;
            m.pcm_startbank = destBuf.get(m.header + 0x31).dat;
            m.pcm_banks = destBuf.get(m.header + 0x32).dat;
            m.pcm_lastsize = destBuf.get(m.header + 0x33).dat;

            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    // MDR Header Reconstruction
    private void writeMDRHeader(List<MmlDatum2> destBuf, _mdr m) {
        // PCM setting value
        destBuf.get(m.header + 0x2a).dat = m.pcm_packed;
        destBuf.get(m.header + 0x30).dat = m.pcm_startadrs;
        destBuf.get(m.header + 0x31).dat = m.pcm_startbank;
        destBuf.get(m.header + 0x32).dat = m.pcm_banks;
        destBuf.get(m.header + 0x33).dat = m.pcm_lastsize;
    }

    private static final int BANK_SIZE = 0x2000;

    // MDR file reading
    private List<MmlDatum2> packPCMintoMDR(List<MmlDatum2> destBuf, String file, String pcm, /* ref */ _mdr m) {
        if (StringUtilities.isNullOrEmpty(file)) return destBuf;
        if (StringUtilities.isNullOrEmpty(pcm)) return destBuf;

        byte[] bank = new byte[BANK_SIZE];
        logger.log(Level.INFO, "packing...");

        int start_pos = m.size;

        // If packed, calculate from the first bang of the PCM
        if (m.pcm_packed != 0) {
            start_pos = m.pcm_startbank * BANK_SIZE;
        }

        byte[] pcmBuf;

        try {
            if (File.exists(pcm)) pcmBuf = File.readAllBytes(pcm);
            else {
                pcm = Path.combine(Path.getDirectoryName(file), pcm);
                pcmBuf = File.readAllBytes(pcm);
            }
        } catch (Exception e) {
            logger.log(Level.ERROR, String.format("File open error!:%s", pcm));
            return null;
        }

        // PCM data output position
        logger.log(Level.INFO, String.format("PCM Start:%08xh", start_pos));
        m.fp = start_pos;

        int block_len = 0;
        int pcm_blocks = 1;

        int i = 0;
        while (i < pcmBuf.length) {
            MmlDatum2 md = new MmlDatum2("", pcmBuf[i]);
            destBuf.add(m.fp + i, md);
            i++;
            block_len++;
            if ((i % BANK_SIZE) == 0) {
                pcm_blocks++;
                block_len = 0;
            }
        }
        if (pcmBuf.length > 0 && block_len == 0) pcm_blocks--;

        m.pcm_packed = 1;
        m.pcm_startadrs = 0x20; // SRAM Start Address
        m.pcm_startbank = start_pos / BANK_SIZE; // Starting Bank
        m.pcm_banks = pcm_blocks - 1; // Number of blocks
        m.pcm_lastsize = (block_len + 0xff) / 0x100; // Last block size

        logger.log(Level.INFO, String.format("PCM StartAdrs:%02xh", m.pcm_startadrs));
        logger.log(Level.INFO, String.format("PCM StartBank:%02xh", m.pcm_startbank));
        logger.log(Level.INFO, String.format("PCM Banks:%02xh", m.pcm_banks));
        logger.log(Level.INFO, String.format("PCM LastSize:%02xh", m.pcm_lastsize));

        writeMDRHeader(destBuf, m);

        logger.log(Level.INFO, "ok!");
        return destBuf;
    }

    public List<MmlDatum2> Pack(List<MmlDatum2> destBuf, String mdrFn, String pcmFn /* = "" */) {
        String pcmfile = null;
        String mdrfile;

        // title
        logger.log(Level.INFO, String.format("%s %s by %s", PRG_NAME, PRG_VER, PRG_AUTHOR));

        mdrfile = mdrFn;
        logger.log(Level.INFO, String.format("File:%s", mdrfile));
        if (!StringUtilities.isNullOrEmpty(pcmFn)) pcmfile = pcmFn;

        _mdr m = new _mdr();
        readMDRHeader(destBuf, mdrfile, /* ref */ m);

        logger.log(Level.INFO, String.format("Size:%d", m.size));

        if (StringUtilities.isNullOrEmpty(pcmfile)) {
            if (!StringUtilities.isNullOrEmpty(m.pcmname)) {
                pcmfile = Path.getDirectoryName(mdrfile);
                pcmfile = Path.combine(pcmfile, m.pcmname);
            }
        }

        if (StringUtilities.isNullOrEmpty(pcmfile)) {
            logger.log(Level.INFO, "PCM filename is not defined!");
            return destBuf;
        }

        logger.log(Level.INFO, String.format("PCM File:%s", pcmfile));

        // Stuffing the PCM
        return packPCMintoMDR(destBuf, mdrfile, pcmfile, /* ref */ m);
    }
}
