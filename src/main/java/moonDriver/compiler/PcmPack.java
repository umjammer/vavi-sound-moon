package moonDriver.compiler;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.lang.System.getLogger;
import static moonDriver.common.Common.charset;
import static vavi.util.compat.Util.isNullOrEmpty;


//
// pcmpack.c
//
class PcmPack {

    private static final Logger logger = getLogger(PcmPack.class.getName());

    private static final String PRG_NAME = "PCMPACK";
    private static final String PRG_VER = "Ver 0.1";
    private static final String PRG_AUTHOR = "BouKiCHi";

    /** MDR File Definition */
    private static class Mdr {

        int fp;
        int size;
        int header = 0;
        /** pos:0x40 pcmName */
        String pcmName;
        /** pos: 0x2a 1:pcm is packed */
        int pcmPacked;
        /** pos:0x30 start address of PCM RAM(* 0x10000) */
        int pcmStartAdrs;
        /** pos:0x31 start bank (* 8192) */
        int pcmStartBank;
        /** pos:0x32 number of PCM banks (* 8192) */
        int pcmBanks;
        /** pos:0x32 size of last bank (* 0x100) */
        int pcmLastSize;

        // actual size = (pcmBanks * 0x2000) + (pcmLastSize * 0x100)
    }

    /** MDR file reading */
    private static int readMDRHeader(List<MmlDatum2> destBuf, String file, Mdr m) {
        try {
            m.size = destBuf.size();

            // PCM String Position
            m.pcmName = "";
            int pcmpos = destBuf.get(m.header + 0x2c).dat + destBuf.get(m.header + 0x2d).dat * 0x100;

            // PCM Position
            if (pcmpos == 0x8040) {
                byte[] fn = new byte[0x40];
                for (int i = 0; i < 0x40; i++) {
                    fn[i] = (byte) destBuf.get(m.header + i).dat;
                }
                m.pcmName = new String(fn, charset);
            }

            // PCM setting value
            m.pcmPacked = destBuf.get(m.header + 0x2a).dat;
            m.pcmStartAdrs = destBuf.get(m.header + 0x30).dat;
            m.pcmStartBank = destBuf.get(m.header + 0x31).dat;
            m.pcmBanks = destBuf.get(m.header + 0x32).dat;
            m.pcmLastSize = destBuf.get(m.header + 0x33).dat;

            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /** MDR Header Reconstruction */
    private static void writeMDRHeader(List<MmlDatum2> destBuf, Mdr m) {
        // PCM setting value
        destBuf.get(m.header + 0x2a).dat = m.pcmPacked;
        destBuf.get(m.header + 0x30).dat = m.pcmStartAdrs;
        destBuf.get(m.header + 0x31).dat = m.pcmStartBank;
        destBuf.get(m.header + 0x32).dat = m.pcmBanks;
        destBuf.get(m.header + 0x33).dat = m.pcmLastSize;
    }

    private static final int BANK_SIZE = 0x2000;

    /** MDR file reading */
    private static List<MmlDatum2> packPCMintoMDR(List<MmlDatum2> destBuf, String file, String pcm, Mdr m) {
        if (isNullOrEmpty(file)) return destBuf;
        if (isNullOrEmpty(pcm)) return destBuf;

        byte[] bank = new byte[BANK_SIZE];
        logger.log(Level.INFO, "packing...");

        int start_pos = m.size;

        // If packed, calculate from the first bang of the PCM
        if (m.pcmPacked != 0) {
            start_pos = m.pcmStartBank * BANK_SIZE;
        }

        byte[] pcmBuf;

        try {
            Path p = Path.of(pcm);
            if (Files.exists(p)) pcmBuf = Files.readAllBytes(p);
            else {
                p = Path.of(file).getParent().resolve(pcm);
                pcmBuf = Files.readAllBytes(p);
            }
        } catch (Exception e) {
            logger.log(Level.ERROR, "File open error!:%s".formatted(pcm));
            return null;
        }

        // PCM data output position
        logger.log(Level.INFO, "PCM Start:%08xh".formatted(start_pos));
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

        m.pcmPacked = 1;
        m.pcmStartAdrs = 0x20; // SRAM Start Address
        m.pcmStartBank = start_pos / BANK_SIZE; // Starting Bank
        m.pcmBanks = pcm_blocks - 1; // Number of blocks
        m.pcmLastSize = (block_len + 0xff) / 0x100; // Last block size

        logger.log(Level.INFO, "PCM StartAdrs:%02xh".formatted(m.pcmStartAdrs));
        logger.log(Level.INFO, "PCM StartBank:%02xh".formatted(m.pcmStartBank));
        logger.log(Level.INFO, "PCM Banks:%02xh".formatted(m.pcmBanks));
        logger.log(Level.INFO, "PCM LastSize:%02xh".formatted(m.pcmLastSize));

        writeMDRHeader(destBuf, m);

        logger.log(Level.INFO, "ok!");
        return destBuf;
    }

    public List<MmlDatum2> pack(List<MmlDatum2> destBuf, String mdrFn, String pcmFn /* = "" */) {
        String pcmfile = null;
        String mdrfile;

        // title
        logger.log(Level.INFO, "%s %s by %s".formatted(PRG_NAME, PRG_VER, PRG_AUTHOR));

        mdrfile = mdrFn;
        logger.log(Level.INFO, "File:%s".formatted(mdrfile));
        if (!isNullOrEmpty(pcmFn)) pcmfile = pcmFn;

        Mdr m = new Mdr();
        readMDRHeader(destBuf, mdrfile, m);

        logger.log(Level.INFO, "Size:%d".formatted(m.size));

        if (isNullOrEmpty(pcmfile)) {
            if (!isNullOrEmpty(m.pcmName)) {
                pcmfile = Path.of(mdrfile).getParent().resolve(m.pcmName).toString();
            }
        }

        if (isNullOrEmpty(pcmfile)) {
            logger.log(Level.INFO, "PCM filename is not defined!");
            return destBuf;
        }

        logger.log(Level.INFO, "PCM File:%s".formatted(pcmfile));

        // Stuffing the PCM
        return packPCMintoMDR(destBuf, mdrfile, pcmfile, m);
    }
}
