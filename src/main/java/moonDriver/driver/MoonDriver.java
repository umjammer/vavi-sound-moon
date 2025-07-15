package moonDriver.driver;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.function.Consumer;

import dotnet4j.util.compat.Tuple;
import moonDriver.common.Common;
import moonDriver.common.GD3;
import moonDriver.driver.MoonDriver.Work.Ch;
import musicDriverInterface.ChipDatum;
import musicDriverInterface.MmlDatum;

import static java.lang.System.getLogger;


public class MoonDriver {

    private static final Logger logger = getLogger(MoonDriver.class.getName());

    public Consumer<ChipDatum> WriteOPL4Register = null;
    public GD3 gd3 = null;
    private double sampleRate;
    private boolean stopped;
    private int vgmCurLoop;
    private int vgmFrameCounter;
    private int vgmSpeed;
    private int counter;
    private int totalCounter;
    private int loopCounter;
    private double vgmSpeedCounter;
    private MmlDatum[] vgmBuf;

    public GD3 getGD3Info(MmlDatum[] buf, int vgmGd3) {

        GD3 gd3 = new GD3();

        int[] adrTag = new int[1];
        adrTag[0] = (buf[0x2e].dat + buf[0x2f].dat * 0x100) & 0xffff;
        if (adrTag[0] != 0) {
            adrTag[0] -= 0x8000;
            gd3.TrackName = Common.getNRDString(buf, /* ref */ adrTag);
            gd3.TrackNameJ = Common.getNRDString(buf, /* ref */ adrTag);
            gd3.GameName = Common.getNRDString(buf, /* ref */ adrTag);
            gd3.GameNameJ = Common.getNRDString(buf, /* ref */ adrTag);
            gd3.SystemName = Common.getNRDString(buf, /* ref */ adrTag);
            gd3.SystemNameJ = Common.getNRDString(buf, /* ref */ adrTag);
            gd3.Composer = Common.getNRDString(buf, /* ref */ adrTag); // Track author
            gd3.ComposerJ = Common.getNRDString(buf, /* ref */ adrTag); // Track author(jp)
            gd3.Version = Common.getNRDString(buf, /* ref */ adrTag); // Release date
            gd3.Converted = Common.getNRDString(buf, /* ref */ adrTag); // Programmer
            gd3.Notes = Common.getNRDString(buf, /* ref */ adrTag); // Notes
        }

        return gd3;
    }

    public boolean init(MmlDatum[] vgmBuf, Consumer<ChipDatum> WriteOPL4Register, double SampleRate) {
        logger.log(Level.INFO, "MoonDriver  Orig. %s Programed by BouKiCHi".formatted(version));
        logger.log(Level.INFO, "MoonDriverDotNET  VER yymmdd Programed by Kuma");

        this.vgmBuf = vgmBuf;
        this.WriteOPL4Register = WriteOPL4Register;
        this.sampleRate = SampleRate;

        gd3 = getGD3Info(vgmBuf, 0);
        counter = 0;
        totalCounter = 0;
        loopCounter = 0;
        vgmCurLoop = 0;
        stopped = false;
        vgmFrameCounter = 0;
        vgmSpeed = 1;

        try {
            a = 0;
            for (int i = 0; i < vgmBuf.length; i++) {
                if (i % 0x4000 == 0) {
                    byte af = a;
                    change_page3();
                    a = af;
                    a += 2;
                }
                writeMemory((short) (0x8000 + (i % 0x4000)), vgmBuf[i] == null ? new MmlDatum() : vgmBuf[i]);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Driver initialization failed.", ex);
        }

        a = 0;
        change_page3();
        a = (byte) readMemory(MDR_PACKED).dat;
        if (a == 0) {
            if (ExtendFile != null) {
                d = 0x05;
                e = 0x03;
                moon_fm2_out();
                // memory write mode
                d = 0x02;
                e = 0x11;
                moon_wave_out();
                d = 0x03;
                e = 0x20;
                moon_wave_out();
                d = 0x04;
                e = 0x00;
                moon_wave_out();
                d = 0x05;
                e = 0x00;
                moon_wave_out();

                for (byte dat : ExtendFile.getItem2()) {
                    d = 0x06;
                    e = dat;
                    moon_wave_out();
                }

                // normal mode
                d = 0x02;
                e = 0x10;
                moon_wave_out();
            }
        } else {
            // LoadPackedPCM
            entryPoints((short) 0x4013);
        }

        // Initializing the Driver
        entryPoints((short) 0x4000);

        return true;
    }

    public void oneFrameProc() {

        vgmSpeedCounter += vgmSpeed;
        while (vgmSpeedCounter >= 1.0) {
            vgmSpeedCounter -= 1.0;
            if (vgmFrameCounter > -1) {
                oneFrameMain();
            } else {
                vgmFrameCounter++;
            }
        }
    }

    public MoonDriver() {
        seq_jmptable = new dlgSeqFunc[] {
                this::seq_drumnote,   // $e0 : Set drum note
                this::seq_drumbit,    // $e1 : Set drum bits
                this::seq_jump,       // $e2 :
                this::seq_fbs,        // $e3 : Set FBS
                this::seq_tvp,        // $e4 : Set TVP
                this::seq_ld2ops,     // $e5 : Load 2OP Instrument
                this::seq_setop,      // $e6 : Set opbase
                this::seq_nop,        // $e7 : Pitch shift
                this::seq_nop,        // $e8 :
                this::seq_slar,       // $e9 : Slar switch
                this::seq_revbsw,     // $ea : Reverb switch / VolumeOP
                this::seq_damp,       // $eb : Damp switch / OPMODE
                this::seq_nop,        // $ec : LFO freq
                this::seq_nop,        // $ed : LFO mode
                this::seq_bank,       // $ee : Bank change
                this::seq_lfosw,      // $ef : Mode change
                this::seq_pan,        // $f0 : Set Pan
                this::seq_inst,       // $f1 : Load Instrument(4OP or OPL4)
                this::seq_drum,       // $f2 : Set Drum
                this::seq_nop,        // $f3 :
                this::seq_wait,       // $f4 : Wait
                this::seq_data_write, // $f5 : Data Write
                this::seq_nop,        // $f6
                this::seq_nenv,       // $f7 : Note envelope
                this::seq_penv,       // $f8 : Pitch envelope
                this::seq_skip_1,     // $f9
                this::seq_detune,     // $fa : Detune
                this::seq_nop,        // $fb : LFO
                this::seq_rest,       // $fc : Rest
                this::seq_volume,     // $fd : Volume
                this::seq_skip_1,     // $fe : Not used
                this::seq_loop        // $ff : Loop
        };
    }

    private double ntscStep = 0.0;
    private double ntscCounter = 0.0;
    private boolean nextFlg = false;
    public Tuple<String, byte[]> ExtendFile = null;
    private int[] pcmKeyon = {
            -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1
    };
    private int[] pcmKeyonB = new int[24];

    public int[] getPCMKeyOn() {
        for (int i = 0; i < pcmKeyonB.length; i++) {
            pcmKeyonB[i] = pcmKeyon[i];
            pcmKeyon[i] = -2;
        }
        return pcmKeyonB;
    }

    private void oneFrameMain() {
        counter++;
        vgmFrameCounter++;
        ntscCounter--;
        if (ntscCounter <= 0) {
            entryPoints((short) 0x4003);
            ntscStep = sampleRate / 60.0;
            ntscCounter += ntscStep;
        }
    }

    private static final short MOON_VERNUM = 0x0002;

    private static final short MOON_BASE = 0x00C4;
    private final short MOON_REG1 = MOON_BASE;
    private final short MOON_DAT1 = MOON_BASE + 1;
    private final short MOON_REG2 = MOON_BASE + 2;
    private final short MOON_DAT2 = MOON_BASE + 3;
    private final short MOON_STAT = MOON_BASE;

    // I/O
    private static final short MOON_WREG = 0x7E;
    private final short MOON_WDAT = MOON_WREG + 1;

    private static final byte RAM_PAGE3 = (byte) 0xFE;

    private static final short USE_CH = 24 + 18;
    private static final short FM_BASECH = 24;

    //
    // MDR file format
    //

    private static final short MDR_ID = (short) 0x8000;
    private static final short MDR_PACKED = (short) 0x802A; // 1 if packed

    private static final short MDR_DSTPCM = (short) 0x8030; // destination address of PCM
    private static final short MDR_STPCM = (short) 0x8031; // PCM start bank
    private static final short MDR_PCMBANKS = (short) 0x8032; // PCM banks
    private static final short MDR_LASTS = (short) 0x8033; // PCM size of lastbank

    private static final short S_DEVICE_FLAGS = (short) 0x8007;

    private static final short S_TRACK_TABLE = (short) 0x8010;
    private final short S_TRACK_BANK = S_TRACK_TABLE + 2;
    private final short S_LOOP_TABLE = S_TRACK_TABLE + 4;
    private final short S_LOOP_BANK = S_TRACK_TABLE + 6;
    private final short S_VENV_TABLE = S_TRACK_TABLE + 8;
    private final short S_VENV_LOOP = S_TRACK_TABLE + 10;
    private final short S_PENV_TABLE = S_TRACK_TABLE + 12;
    private final short S_PENV_LOOP = S_TRACK_TABLE + 14;
    private final short S_NENV_TABLE = S_TRACK_TABLE + 16;
    private final short S_NENV_LOOP = S_TRACK_TABLE + 18;
    private final short S_LFO_TABLE = S_TRACK_TABLE + 20;
    private final short S_INST_TABLE = S_TRACK_TABLE + 22;
    private final short S_OPL3_TABLE = S_TRACK_TABLE + 24;

    public static class Work {

        public byte seq_cur_ch = 0x00;
        public byte seq_use_ch = 0x00;
        public byte seq_start_fm = 0x00;
        public byte seq_cur_bank = 0x00;
        public byte seq_opsel = 0x00;
        public byte seq_reg_bd = 0x00;
        public byte seq_jump_flag = 0x00;
        public byte seq_tmp_note = 0x00;
        public byte seq_tmp_ch = 0x00;
        public byte seq_tmp_oct = 0x00;
        public short seq_tmp_fnum = 0x0000;

        //
        // Workarea for channels in the driver
        //

//seq_work:
        public static class Ch {

            public byte dsel = 0x00;
            public byte opsel = 0x00;
            public byte synth = 0x00;
            public byte efx1 = 0x00;
            public byte cnt = 0x00;
            public byte loop = 0x00;
            public int loopCnt = 0x00;
            public byte bank = 0x00;
            public short addr = 0x0000;
            public byte stBank = 0x00;
            public short stAddr = 0x0000;
            public boolean endFlg = false;
            public short tadr = 0x0000;
            public short tone = 0x0000;
            public byte key = 0x00;
            public byte damp = 0x00;
            public byte lfo = 0x00;
            public byte lfo_vib = 0x00;
            public byte[] ol = new byte[4];
            //public byte ar_d1r = 0x00;
            //public byte dl_d2r = 0x00;
            //public byte rc_rr = 0x00;
            //public byte am = 0x00;
            public byte note = 0x00;
            public short pitch = 0x0000;
            public short p_ofs = 0x0000;
            public byte oct = 0x00;
            public short fnum = 0x0000;
            public byte reverb = 0x00;
            public byte vol = 0x00;
            public byte pan = 0x00;
            public byte detune = 0x00;
            public byte venv = 0x00;
            public byte nenv = 0x00;
            public byte penv = 0x00;
            public short nenv_adr = 0x0000;
            public short penv_adr = 0x0000;
            public short venv_adr = 0x0000;
        }

//seq_work_end:

        public Ch[] ch;

        //public int IDX_DSEL = 0; // equ(seq_ch1_dsel    - seq_work); Device Select
        //public int IDX_OPSEL = 1; // equ(seq_ch1_opsel   - seq_work); Operator Select
        //public int IDX_SYNTH = 2; // equ(seq_ch1_synth   - seq_work); FeedBack,Synth and OpMode
        //public int IDX_EFX1 = 3; // equ(seq_ch1_efx1    - seq_work); Effect flags
        //public int IDX_CNT = 4; // equ(seq_ch1_cnt     - seq_work); counter
        //public int IDX_LOOP = 5; // equ(seq_ch1_loop    - seq_work); Loop
        //public int IDX_BANK = 6; // equ(seq_ch1_bank    - seq_work); Which bank
        //public int IDX_ADDR = 7; // equ(seq_ch1_addr    - seq_work); Address to data
        //public int IDX_TADR = 9; // equ(seq_ch1_tadr    - seq_work); Address of a Tone Table
        //public int IDX_TONE = 11; // equ(seq_ch1_tone    - seq_work); Tone number in OPL4
        //public int IDX_KEY = 13; // equ(seq_ch1_key     - seq_work); data in Key register
        //public int IDX_DAMP = 14; // equ(seq_ch1_damp    - seq_work); Damp switch
        //public int IDX_LFO = 15; // equ(seq_ch1_lfo     - seq_work); LFO switch
        //public int IDX_LFO_VIB = 16; // equ(seq_ch1_lfo_vib - seq_work); LFO and VIB
        //public int IDX_AR_D1R = 17; // equ(seq_ch1_ar_d1r  - seq_work); AR and D1R
        //public int IDX_DL_D2R = 18; // equ(seq_ch1_dl_d2r  - seq_work); DL and D2R
        //public int IDX_RC_RR = 19; // equ(seq_ch1_rc_rr   - seq_work); RC and RR
        //public int IDX_AM = 20; // equ(seq_ch1_am      - seq_work); AM
        //public int IDX_NOTE = 21; // equ(seq_ch1_note    - seq_work); Note data
        //public int IDX_PITCH = 22; // equ(seq_ch1_pitch   - seq_work); Pitch data
        //public int IDX_P_OFS = 24; // equ(seq_ch1_p_ofs   - seq_work); Offset for pitch
        //public int IDX_OCT = 26; // equ(seq_ch1_oct     - seq_work); Octave in OPL4
        //public int IDX_FNUM = 27; // equ(seq_ch1_fnum    - seq_work); F-number in OPL4
        //public int IDX_REVERB = 29; // equ(seq_ch1_reverb  - seq_work); Pseudo reverb
        //public int IDX_VOL = 30; // equ(seq_ch1_vol     - seq_work); Volume in OPL4
        //public int IDX_PAN = 31; // equ(seq_ch1_pan     - seq_work); Pan in OPL4
        //public int IDX_DETUNE = 32; // equ(seq_ch1_detune  - seq_work); Detune
        //public int IDX_VENV = 33; // equ(seq_ch1_venv    - seq_work); Volume envelope in data
        //public int IDX_NENV = 34; // equ(seq_ch1_nenv    - seq_work); Vote envelope  in data
        //public int IDX_PENV = 35; // equ(seq_ch1_penv    - seq_work); Pitch envelope in data
        //public int IDX_NENV_ADR = 36; // equ(seq_ch1_nenv_adr - seq_work)
        //public int IDX_PENV_ADR = 38; // equ(seq_ch1_penv_adr - seq_work)
        //public int IDX_VENV_ADR = 40; // equ(seq_ch1_venv_adr - seq_work)

        //public int IDX_VOLOP = 29; // equ(seq_ch1_reverb  - seq_work); Volume Operator in connect
        //public int IDX_OLDAT1 = 17; // equ(seq_ch1_ar_d1r  - seq_work); Volume Data for 1stOP

        //;
        //; Note : IDX_SYNTH OxxFFFSS
        //;
        //; O : 4OP mode
        //; F : FeedBack
        //; S : SynthType(bit0 for 1st&2nd bit1 for 3rd&4th)
        //;

        //SEQ_WORKSIZE: equ(seq_work_end - seq_work)

        //ds(SEQ_WORKSIZE* (USE_CH-1))
    }

    private Work work = null;

    private static final short[] fm_fnumtbl = {
            345,  // C 523.300000
            365,  // C+ 554.400000
            387,  // D 587.300000
            410,  // D+ 622.300000
            435,  // E 659.300000
            460,  // F 698.500000
            488,  // F+ 740.000000
            517,  // G 784.000000
            547,  // G+ 830.600000
            580,  // A 880.000000
            614,  // A+ 932.300000
            651,  // B 987.800000
            690   // C 1046.500000
    };

    private static final byte[] fm_testtone = {
            0x00,  // FBS
            0x00,  // FBS2
            0x00,  // BD

            0x01,  // TREMOLO VIB SUS KSR MUL
            0x00,  // KSL OL
            0x11,  // AR DR
            0x13,  // SL RR
            0x01,  // WF

            0x04,  //
            0x00,  //
            0x11,  //
            0x18,  //
            0x00,  // WF

            0x01,
            0x3f,
            0x55,
            0x55,
            0x00,

            0x01,
            0x3f,
            0x55,
            0x55,
            0x00
    };

    private static final byte[] fm_op2reg_tbl = {
            0x00,  // 0
            0x01,  //
            0x02,  // 2
            0x03,  //
            0x04,  // 4
            0x05,  //
            0x08,  // 6
            0x09,  //
            0x0A,  // 8
            0x0B,  //
            0x0C,  // 10
            0x0D,  //
            0x10,  // 12
            0x11,  //
            0x12,  // 14
            0x13,  //
            0x14,  // 16
            0x15   // 17
    };

    private static final byte[] fm_opbtbl = {
            0x00,  // CH0
            0x01,  // CH1
            0x02,  // CH2
            0x06,  // CH3
            0x07,  // CH4
            0x08,  // CH5
            0x0c,  // CH6
            0x0d,  // CH7
            0x0e,  // CH8
            0x12,  // CH9
            0x13,  // CH10
            0x14,  // CH11
            0x18,  // CH12
            0x19,  // CH13
            0x1a,  // CH14
            0x1e,  // CH15
            0x1f,  // CH16
            0x20 // CH17
    };

    //
    // BSMCH
    private final short[] fm_drum_fnum = {
            0x0120,  // B
            0x0150,  // S
            0x01c0,  // M
            0x01c0,  // C
            0x0150   // H
    };

    private static final byte[] fm_drum_fnum_map = {
            0x06,  // B
            0x07,  // S
            0x08,  // M
            0x08,  // C
            0x07   // H
    };

    private final byte[] fm_drum_oct = {
            0x02,  // B
            0x02,  // S
            0x00,  // M
            0x00,  // C
            0x02   // H
    };

    private static final short[] freq_table = {
            0x0000, 0x0000, 0x0000, 0x0001, 0x0001, 0x0002, 0x0002, 0x0003,  // $00
            0x0003, 0x0004, 0x0004, 0x0005, 0x0005, 0x0006, 0x0006, 0x0006,  // $08
            0x0007, 0x0007, 0x0008, 0x0008, 0x0009, 0x0009, 0x000a, 0x000a,  // $10
            0x000b, 0x000b, 0x000c, 0x000c, 0x000d, 0x000d, 0x000d, 0x000e,  // $18
            0x000e, 0x000f, 0x000f, 0x0010, 0x0010, 0x0011, 0x0011, 0x0012,  // $20
            0x0012, 0x0013, 0x0013, 0x0014, 0x0014, 0x0015, 0x0015, 0x0015,  // $28
            0x0016, 0x0016, 0x0017, 0x0017, 0x0018, 0x0018, 0x0019, 0x0019,  // $30
            0x001a, 0x001a, 0x001b, 0x001b, 0x001c, 0x001c, 0x001d, 0x001d,  // $38
            0x001e, 0x001e, 0x001e, 0x001f, 0x001f, 0x0020, 0x0020, 0x0021,  // $40
            0x0021, 0x0022, 0x0022, 0x0023, 0x0023, 0x0024, 0x0024, 0x0025,  // $48
            0x0025, 0x0026, 0x0026, 0x0027, 0x0027, 0x0028, 0x0028, 0x0029,  // $50
            0x0029, 0x0029, 0x002a, 0x002a, 0x002b, 0x002b, 0x002c, 0x002c,  // $58
            0x002d, 0x002d, 0x002e, 0x002e, 0x002f, 0x002f, 0x0030, 0x0030,  // $60
            0x0031, 0x0031, 0x0032, 0x0032, 0x0033, 0x0033, 0x0034, 0x0034,  // $68
            0x0035, 0x0035, 0x0036, 0x0036, 0x0037, 0x0037, 0x0038, 0x0038,  // $70
            0x0038, 0x0039, 0x0039, 0x003a, 0x003a, 0x003b, 0x003b, 0x003c,  // $78
            0x003c, 0x003d, 0x003d, 0x003e, 0x003e, 0x003f, 0x003f, 0x0040,  // $80
            0x0040, 0x0041, 0x0041, 0x0042, 0x0042, 0x0043, 0x0043, 0x0044,  // $88
            0x0044, 0x0045, 0x0045, 0x0046, 0x0046, 0x0047, 0x0047, 0x0048,  // $90
            0x0048, 0x0049, 0x0049, 0x004a, 0x004a, 0x004b, 0x004b, 0x004c,  // $98
            0x004c, 0x004d, 0x004d, 0x004e, 0x004e, 0x004f, 0x004f, 0x0050,  // $a0
            0x0050, 0x0051, 0x0051, 0x0052, 0x0052, 0x0053, 0x0053, 0x0054,  // $a8
            0x0054, 0x0055, 0x0055, 0x0056, 0x0056, 0x0057, 0x0057, 0x0058,  // $b0
            0x0058, 0x0059, 0x0059, 0x005a, 0x005a, 0x005b, 0x005b, 0x005c,  // $b8
            0x005c, 0x005d, 0x005d, 0x005e, 0x005e, 0x005f, 0x005f, 0x0060,  // $c0
            0x0060, 0x0061, 0x0061, 0x0062, 0x0062, 0x0063, 0x0063, 0x0064,  // $c8
            0x0064, 0x0065, 0x0065, 0x0066, 0x0066, 0x0067, 0x0067, 0x0068,  // $d0
            0x0068, 0x0069, 0x0069, 0x006a, 0x006a, 0x006b, 0x006b, 0x006c,  // $d8
            0x006c, 0x006d, 0x006d, 0x006e, 0x006e, 0x006f, 0x006f, 0x0070,  // $e0
            0x0071, 0x0071, 0x0072, 0x0072, 0x0073, 0x0073, 0x0074, 0x0074,  // $e8
            0x0075, 0x0075, 0x0076, 0x0076, 0x0077, 0x0077, 0x0078, 0x0078,  // $f0
            0x0079, 0x0079, 0x007a, 0x007a, 0x007b, 0x007b, 0x007c, 0x007c,  // $f8
            0x007d, 0x007d, 0x007e, 0x007e, 0x007f, 0x007f, 0x0080, 0x0081,  // $100
            0x0081, 0x0082, 0x0082, 0x0083, 0x0083, 0x0084, 0x0084, 0x0085,  // $108
            0x0085, 0x0086, 0x0086, 0x0087, 0x0087, 0x0088, 0x0088, 0x0089,  // $110
            0x0089, 0x008a, 0x008a, 0x008b, 0x008c, 0x008c, 0x008d, 0x008d,  // $118
            0x008e, 0x008e, 0x008f, 0x008f, 0x0090, 0x0090, 0x0091, 0x0091,  // $120
            0x0092, 0x0092, 0x0093, 0x0093, 0x0094, 0x0094, 0x0095, 0x0096,  // $128
            0x0096, 0x0097, 0x0097, 0x0098, 0x0098, 0x0099, 0x0099, 0x009a,  // $130
            0x009a, 0x009b, 0x009b, 0x009c, 0x009c, 0x009d, 0x009e, 0x009e,  // $138
            0x009f, 0x009f, 0x00a0, 0x00a0, 0x00a1, 0x00a1, 0x00a2, 0x00a2,  // $140
            0x00a3, 0x00a3, 0x00a4, 0x00a4, 0x00a5, 0x00a6, 0x00a6, 0x00a7,  // $148
            0x00a7, 0x00a8, 0x00a8, 0x00a9, 0x00a9, 0x00aa, 0x00aa, 0x00ab,  // $150
            0x00ab, 0x00ac, 0x00ad, 0x00ad, 0x00ae, 0x00ae, 0x00af, 0x00af,  // $158
            0x00b0, 0x00b0, 0x00b1, 0x00b1, 0x00b2, 0x00b3, 0x00b3, 0x00b4,  // $160
            0x00b4, 0x00b5, 0x00b5, 0x00b6, 0x00b6, 0x00b7, 0x00b7, 0x00b8,  // $168
            0x00b8, 0x00b9, 0x00ba, 0x00ba, 0x00bb, 0x00bb, 0x00bc, 0x00bc,  // $170
            0x00bd, 0x00bd, 0x00be, 0x00bf, 0x00bf, 0x00c0, 0x00c0, 0x00c1,  // $178
            0x00c1, 0x00c2, 0x00c2, 0x00c3, 0x00c3, 0x00c4, 0x00c5, 0x00c5,  // $180
            0x00c6, 0x00c6, 0x00c7, 0x00c7, 0x00c8, 0x00c8, 0x00c9, 0x00ca,  // $188
            0x00ca, 0x00cb, 0x00cb, 0x00cc, 0x00cc, 0x00cd, 0x00cd, 0x00ce,  // $190
            0x00cf, 0x00cf, 0x00d0, 0x00d0, 0x00d1, 0x00d1, 0x00d2, 0x00d2,  // $198
            0x00d3, 0x00d4, 0x00d4, 0x00d5, 0x00d5, 0x00d6, 0x00d6, 0x00d7,  // $1a0
            0x00d7, 0x00d8, 0x00d9, 0x00d9, 0x00da, 0x00da, 0x00db, 0x00db,  // $1a8
            0x00dc, 0x00dc, 0x00dd, 0x00de, 0x00de, 0x00df, 0x00df, 0x00e0,  // $1b0
            0x00e0, 0x00e1, 0x00e2, 0x00e2, 0x00e3, 0x00e3, 0x00e4, 0x00e4,  // $1b8
            0x00e5, 0x00e5, 0x00e6, 0x00e7, 0x00e7, 0x00e8, 0x00e8, 0x00e9,  // $1c0
            0x00e9, 0x00ea, 0x00eb, 0x00eb, 0x00ec, 0x00ec, 0x00ed, 0x00ed,  // $1c8
            0x00ee, 0x00ef, 0x00ef, 0x00f0, 0x00f0, 0x00f1, 0x00f1, 0x00f2,  // $1d0
            0x00f3, 0x00f3, 0x00f4, 0x00f4, 0x00f5, 0x00f5, 0x00f6, 0x00f7,  // $1d8
            0x00f7, 0x00f8, 0x00f8, 0x00f9, 0x00f9, 0x00fa, 0x00fb, 0x00fb,  // $1e0
            0x00fc, 0x00fc, 0x00fd, 0x00fd, 0x00fe, 0x00ff, 0x00ff, 0x0100,  // $1e8
            0x0100, 0x0101, 0x0102, 0x0102, 0x0103, 0x0103, 0x0104, 0x0104,  // $1f0
            0x0105, 0x0106, 0x0106, 0x0107, 0x0107, 0x0108, 0x0108, 0x0109,  // $1f8
            0x010a, 0x010a, 0x010b, 0x010b, 0x010c, 0x010d, 0x010d, 0x010e,  // $200
            0x010e, 0x010f, 0x010f, 0x0110, 0x0111, 0x0111, 0x0112, 0x0112,  // $208
            0x0113, 0x0114, 0x0114, 0x0115, 0x0115, 0x0116, 0x0117, 0x0117,  // $210
            0x0118, 0x0118, 0x0119, 0x0119, 0x011a, 0x011b, 0x011b, 0x011c,  // $218
            0x011c, 0x011d, 0x011e, 0x011e, 0x011f, 0x011f, 0x0120, 0x0121,  // $220
            0x0121, 0x0122, 0x0122, 0x0123, 0x0124, 0x0124, 0x0125, 0x0125,  // $228
            0x0126, 0x0127, 0x0127, 0x0128, 0x0128, 0x0129, 0x0129, 0x012a,  // $230
            0x012b, 0x012b, 0x012c, 0x012c, 0x012d, 0x012e, 0x012e, 0x012f,  // $238
            0x012f, 0x0130, 0x0131, 0x0131, 0x0132, 0x0132, 0x0133, 0x0134,  // $240
            0x0134, 0x0135, 0x0135, 0x0136, 0x0137, 0x0137, 0x0138, 0x0138,  // $248
            0x0139, 0x013a, 0x013a, 0x013b, 0x013c, 0x013c, 0x013d, 0x013d,  // $250
            0x013e, 0x013f, 0x013f, 0x0140, 0x0140, 0x0141, 0x0142, 0x0142,  // $258
            0x0143, 0x0143, 0x0144, 0x0145, 0x0145, 0x0146, 0x0146, 0x0147,  // $260
            0x0148, 0x0148, 0x0149, 0x0149, 0x014a, 0x014b, 0x014b, 0x014c,  // $268
            0x014d, 0x014d, 0x014e, 0x014e, 0x014f, 0x0150, 0x0150, 0x0151,  // $270
            0x0151, 0x0152, 0x0153, 0x0153, 0x0154, 0x0155, 0x0155, 0x0156,  // $278
            0x0156, 0x0157, 0x0158, 0x0158, 0x0159, 0x0159, 0x015a, 0x015b,  // $280
            0x015b, 0x015c, 0x015d, 0x015d, 0x015e, 0x015e, 0x015f, 0x0160,  // $288
            0x0160, 0x0161, 0x0162, 0x0162, 0x0163, 0x0163, 0x0164, 0x0165,  // $290
            0x0165, 0x0166, 0x0167, 0x0167, 0x0168, 0x0168, 0x0169, 0x016a,  // $298
            0x016a, 0x016b, 0x016c, 0x016c, 0x016d, 0x016d, 0x016e, 0x016f,  // $2a0
            0x016f, 0x0170, 0x0171, 0x0171, 0x0172, 0x0172, 0x0173, 0x0174,  // $2a8
            0x0174, 0x0175, 0x0176, 0x0176, 0x0177, 0x0177, 0x0178, 0x0179,  // $2b0
            0x0179, 0x017a, 0x017b, 0x017b, 0x017c, 0x017d, 0x017d, 0x017e,  // $2b8
            0x017e, 0x017f, 0x0180, 0x0180, 0x0181, 0x0182, 0x0182, 0x0183,  // $2c0
            0x0184, 0x0184, 0x0185, 0x0185, 0x0186, 0x0187, 0x0187, 0x0188,  // $2c8
            0x0189, 0x0189, 0x018a, 0x018b, 0x018b, 0x018c, 0x018c, 0x018d,  // $2d0
            0x018e, 0x018e, 0x018f, 0x0190, 0x0190, 0x0191, 0x0192, 0x0192,  // $2d8
            0x0193, 0x0194, 0x0194, 0x0195, 0x0195, 0x0196, 0x0197, 0x0197,  // $2e0
            0x0198, 0x0199, 0x0199, 0x019a, 0x019b, 0x019b, 0x019c, 0x019d,  // $2e8
            0x019d, 0x019e, 0x019f, 0x019f, 0x01a0, 0x01a0, 0x01a1, 0x01a2,  // $2f0
            0x01a2, 0x01a3, 0x01a4, 0x01a4, 0x01a5, 0x01a6, 0x01a6, 0x01a7,  // $2f8
            0x01a8, 0x01a8, 0x01a9, 0x01aa, 0x01aa, 0x01ab, 0x01ac, 0x01ac,  // $300
            0x01ad, 0x01ae, 0x01ae, 0x01af, 0x01b0, 0x01b0, 0x01b1, 0x01b1,  // $308
            0x01b2, 0x01b3, 0x01b3, 0x01b4, 0x01b5, 0x01b5, 0x01b6, 0x01b7,  // $310
            0x01b7, 0x01b8, 0x01b9, 0x01b9, 0x01ba, 0x01bb, 0x01bb, 0x01bc,  // $318
            0x01bd, 0x01bd, 0x01be, 0x01bf, 0x01bf, 0x01c0, 0x01c1, 0x01c1,  // $320
            0x01c2, 0x01c3, 0x01c3, 0x01c4, 0x01c5, 0x01c5, 0x01c6, 0x01c7,  // $328
            0x01c7, 0x01c8, 0x01c9, 0x01c9, 0x01ca, 0x01cb, 0x01cb, 0x01cc,  // $330
            0x01cd, 0x01cd, 0x01ce, 0x01cf, 0x01cf, 0x01d0, 0x01d1, 0x01d1,  // $338
            0x01d2, 0x01d3, 0x01d3, 0x01d4, 0x01d5, 0x01d5, 0x01d6, 0x01d7,  // $340
            0x01d7, 0x01d8, 0x01d9, 0x01da, 0x01da, 0x01db, 0x01dc, 0x01dc,  // $348
            0x01dd, 0x01de, 0x01de, 0x01df, 0x01e0, 0x01e0, 0x01e1, 0x01e2,  // $350
            0x01e2, 0x01e3, 0x01e4, 0x01e4, 0x01e5, 0x01e6, 0x01e6, 0x01e7,  // $358
            0x01e8, 0x01e8, 0x01e9, 0x01ea, 0x01eb, 0x01eb, 0x01ec, 0x01ed,  // $360
            0x01ed, 0x01ee, 0x01ef, 0x01ef, 0x01f0, 0x01f1, 0x01f1, 0x01f2,  // $368
            0x01f3, 0x01f3, 0x01f4, 0x01f5, 0x01f5, 0x01f6, 0x01f7, 0x01f8,  // $370
            0x01f8, 0x01f9, 0x01fa, 0x01fa, 0x01fb, 0x01fc, 0x01fc, 0x01fd,  // $378
            0x01fe, 0x01fe, 0x01ff, 0x0200, 0x0201, 0x0201, 0x0202, 0x0203,  // $380
            0x0203, 0x0204, 0x0205, 0x0205, 0x0206, 0x0207, 0x0207, 0x0208,  // $388
            0x0209, 0x020a, 0x020a, 0x020b, 0x020c, 0x020c, 0x020d, 0x020e,  // $390
            0x020e, 0x020f, 0x0210, 0x0211, 0x0211, 0x0212, 0x0213, 0x0213,  // $398
            0x0214, 0x0215, 0x0215, 0x0216, 0x0217, 0x0218, 0x0218, 0x0219,  // $3a0
            0x021a, 0x021a, 0x021b, 0x021c, 0x021d, 0x021d, 0x021e, 0x021f,  // $3a8
            0x021f, 0x0220, 0x0221, 0x0221, 0x0222, 0x0223, 0x0224, 0x0224,  // $3b0
            0x0225, 0x0226, 0x0226, 0x0227, 0x0228, 0x0229, 0x0229, 0x022a,  // $3b8
            0x022b, 0x022b, 0x022c, 0x022d, 0x022e, 0x022e, 0x022f, 0x0230,  // $3c0
            0x0230, 0x0231, 0x0232, 0x0233, 0x0233, 0x0234, 0x0235, 0x0235,  // $3c8
            0x0236, 0x0237, 0x0238, 0x0238, 0x0239, 0x023a, 0x023a, 0x023b,  // $3d0
            0x023c, 0x023d, 0x023d, 0x023e, 0x023f, 0x0240, 0x0240, 0x0241,  // $3d8
            0x0242, 0x0242, 0x0243, 0x0244, 0x0245, 0x0245, 0x0246, 0x0247,  // $3e0
            0x0247, 0x0248, 0x0249, 0x024a, 0x024a, 0x024b, 0x024c, 0x024d,  // $3e8
            0x024d, 0x024e, 0x024f, 0x024f, 0x0250, 0x0251, 0x0252, 0x0252,  // $3f0
            0x0253, 0x0254, 0x0255, 0x0255, 0x0256, 0x0257, 0x0258, 0x0258,  // $3f8
            0x0259, 0x025a, 0x025a, 0x025b, 0x025c, 0x025d, 0x025d, 0x025e,  // $400
            0x025f, 0x0260, 0x0260, 0x0261, 0x0262, 0x0263, 0x0263, 0x0264,  // $408
            0x0265, 0x0266, 0x0266, 0x0267, 0x0268, 0x0268, 0x0269, 0x026a,  // $410
            0x026b, 0x026b, 0x026c, 0x026d, 0x026e, 0x026e, 0x026f, 0x0270,  // $418
            0x0271, 0x0271, 0x0272, 0x0273, 0x0274, 0x0274, 0x0275, 0x0276,  // $420
            0x0277, 0x0277, 0x0278, 0x0279, 0x027a, 0x027a, 0x027b, 0x027c,  // $428
            0x027d, 0x027d, 0x027e, 0x027f, 0x0280, 0x0280, 0x0281, 0x0282,  // $430
            0x0283, 0x0283, 0x0284, 0x0285, 0x0286, 0x0286, 0x0287, 0x0288,  // $438
            0x0289, 0x0289, 0x028a, 0x028b, 0x028c, 0x028c, 0x028d, 0x028e,  // $440
            0x028f, 0x028f, 0x0290, 0x0291, 0x0292, 0x0292, 0x0293, 0x0294,  // $448
            0x0295, 0x0296, 0x0296, 0x0297, 0x0298, 0x0299, 0x0299, 0x029a,  // $450
            0x029b, 0x029c, 0x029c, 0x029d, 0x029e, 0x029f, 0x029f, 0x02a0,  // $458
            0x02a1, 0x02a2, 0x02a2, 0x02a3, 0x02a4, 0x02a5, 0x02a6, 0x02a6,  // $460
            0x02a7, 0x02a8, 0x02a9, 0x02a9, 0x02aa, 0x02ab, 0x02ac, 0x02ac,  // $468
            0x02ad, 0x02ae, 0x02af, 0x02b0, 0x02b0, 0x02b1, 0x02b2, 0x02b3,  // $470
            0x02b3, 0x02b4, 0x02b5, 0x02b6, 0x02b7, 0x02b7, 0x02b8, 0x02b9,  // $478
            0x02ba, 0x02ba, 0x02bb, 0x02bc, 0x02bd, 0x02be, 0x02be, 0x02bf,  // $480
            0x02c0, 0x02c1, 0x02c1, 0x02c2, 0x02c3, 0x02c4, 0x02c5, 0x02c5,  // $488
            0x02c6, 0x02c7, 0x02c8, 0x02c8, 0x02c9, 0x02ca, 0x02cb, 0x02cc,  // $490
            0x02cc, 0x02cd, 0x02ce, 0x02cf, 0x02d0, 0x02d0, 0x02d1, 0x02d2,  // $498
            0x02d3, 0x02d3, 0x02d4, 0x02d5, 0x02d6, 0x02d7, 0x02d7, 0x02d8,  // $4a0
            0x02d9, 0x02da, 0x02db, 0x02db, 0x02dc, 0x02dd, 0x02de, 0x02df,  // $4a8
            0x02df, 0x02e0, 0x02e1, 0x02e2, 0x02e3, 0x02e3, 0x02e4, 0x02e5,  // $4b0
            0x02e6, 0x02e7, 0x02e7, 0x02e8, 0x02e9, 0x02ea, 0x02eb, 0x02eb,  // $4b8
            0x02ec, 0x02ed, 0x02ee, 0x02ef, 0x02ef, 0x02f0, 0x02f1, 0x02f2,  // $4c0
            0x02f3, 0x02f3, 0x02f4, 0x02f5, 0x02f6, 0x02f7, 0x02f7, 0x02f8,  // $4c8
            0x02f9, 0x02fa, 0x02fb, 0x02fb, 0x02fc, 0x02fd, 0x02fe, 0x02ff,  // $4d0
            0x02ff, 0x0300, 0x0301, 0x0302, 0x0303, 0x0303, 0x0304, 0x0305,  // $4d8
            0x0306, 0x0307, 0x0308, 0x0308, 0x0309, 0x030a, 0x030b, 0x030c,  // $4e0
            0x030c, 0x030d, 0x030e, 0x030f, 0x0310, 0x0310, 0x0311, 0x0312,  // $4e8
            0x0313, 0x0314, 0x0315, 0x0315, 0x0316, 0x0317, 0x0318, 0x0319,  // $4f0
            0x0319, 0x031a, 0x031b, 0x031c, 0x031d, 0x031e, 0x031e, 0x031f,  // $4f8
            0x0320, 0x0321, 0x0322, 0x0323, 0x0323, 0x0324, 0x0325, 0x0326,  // $500
            0x0327, 0x0327, 0x0328, 0x0329, 0x032a, 0x032b, 0x032c, 0x032c,  // $508
            0x032d, 0x032e, 0x032f, 0x0330, 0x0331, 0x0331, 0x0332, 0x0333,  // $510
            0x0334, 0x0335, 0x0336, 0x0336, 0x0337, 0x0338, 0x0339, 0x033a,  // $518
            0x033b, 0x033b, 0x033c, 0x033d, 0x033e, 0x033f, 0x0340, 0x0340,  // $520
            0x0341, 0x0342, 0x0343, 0x0344, 0x0345, 0x0345, 0x0346, 0x0347,  // $528
            0x0348, 0x0349, 0x034a, 0x034b, 0x034b, 0x034c, 0x034d, 0x034e,  // $530
            0x034f, 0x0350, 0x0350, 0x0351, 0x0352, 0x0353, 0x0354, 0x0355,  // $538
            0x0356, 0x0356, 0x0357, 0x0358, 0x0359, 0x035a, 0x035b, 0x035b,  // $540
            0x035c, 0x035d, 0x035e, 0x035f, 0x0360, 0x0361, 0x0361, 0x0362,  // $548
            0x0363, 0x0364, 0x0365, 0x0366, 0x0367, 0x0367, 0x0368, 0x0369,  // $550
            0x036a, 0x036b, 0x036c, 0x036d, 0x036d, 0x036e, 0x036f, 0x0370,  // $558
            0x0371, 0x0372, 0x0373, 0x0373, 0x0374, 0x0375, 0x0376, 0x0377,  // $560
            0x0378, 0x0379, 0x0379, 0x037a, 0x037b, 0x037c, 0x037d, 0x037e,  // $568
            0x037f, 0x0380, 0x0380, 0x0381, 0x0382, 0x0383, 0x0384, 0x0385,  // $570
            0x0386, 0x0386, 0x0387, 0x0388, 0x0389, 0x038a, 0x038b, 0x038c,  // $578
            0x038d, 0x038d, 0x038e, 0x038f, 0x0390, 0x0391, 0x0392, 0x0393,  // $580
            0x0394, 0x0394, 0x0395, 0x0396, 0x0397, 0x0398, 0x0399, 0x039a,  // $588
            0x039b, 0x039b, 0x039c, 0x039d, 0x039e, 0x039f, 0x03a0, 0x03a1,  // $590
            0x03a2, 0x03a2, 0x03a3, 0x03a4, 0x03a5, 0x03a6, 0x03a7, 0x03a8,  // $598
            0x03a9, 0x03aa, 0x03aa, 0x03ab, 0x03ac, 0x03ad, 0x03ae, 0x03af,  // $5a0
            0x03b0, 0x03b1, 0x03b2, 0x03b2, 0x03b3, 0x03b4, 0x03b5, 0x03b6,  // $5a8
            0x03b7, 0x03b8, 0x03b9, 0x03ba, 0x03ba, 0x03bb, 0x03bc, 0x03bd,  // $5b0
            0x03be, 0x03bf, 0x03c0, 0x03c1, 0x03c2, 0x03c3, 0x03c3, 0x03c4,  // $5b8
            0x03c5, 0x03c6, 0x03c7, 0x03c8, 0x03c9, 0x03ca, 0x03cb, 0x03cb,  // $5c0
            0x03cc, 0x03cd, 0x03ce, 0x03cf, 0x03d0, 0x03d1, 0x03d2, 0x03d3,  // $5c8
            0x03d4, 0x03d5, 0x03d5, 0x03d6, 0x03d7, 0x03d8, 0x03d9, 0x03da,  // $5d0
            0x03db, 0x03dc, 0x03dd, 0x03de, 0x03de, 0x03df, 0x03e0, 0x03e1,  // $5d8
            0x03e2, 0x03e3, 0x03e4, 0x03e5, 0x03e6, 0x03e7, 0x03e8, 0x03e9,  // $5e0
            0x03e9, 0x03ea, 0x03eb, 0x03ec, 0x03ed, 0x03ee, 0x03ef, 0x03f0,  // $5e8
            0x03f1, 0x03f2, 0x03f3, 0x03f4, 0x03f4, 0x03f5, 0x03f6, 0x03f7,  // $5f0
            0x03f8, 0x03f9, 0x03fa, 0x03fb, 0x03fc, 0x03fd, 0x03fe, 0x03ff   // $5f8
    };

    /**
     * Entry points
     */
    public void entryPoints(short adr) {
        switch (adr) {
            //	; $4000 Initialize
            case 0x4000:
                moon_init_all();
                break;

            //	; $4003 Execute 1frame(1/60)
            case 0x4003:
                moon_proc_tracks();
                break;

            //    ; $4006 All key-off
            case 0x4006:
                moon_seq_all_keyoff();
                break;

            //	; $4009 Set H.TIMI for timing
            case 0x4009:
                //    ret
                //    ret
                //    ret
                break;

            //    ; $400C Restore H.TIMI
            case 0x400c:
                //    ret
                //    ret
                //    ret
                break;

            //    ; $400F MOONDRIVER version number
            case 0x400f:
                //    dw  MOON_VERNUM
                break;
            //	; $4011 MOONDRIVER version string
            case 0x4011:
                //    dw  str_moondrv
                break;

            //	; $4013 LoadPCM
            case 0x4013:
                moon_load_pcm();
                break;
        }
    }

    //    org	$4020

    //str_moondrv:
    //private String  str_moondrv = "MOONDRIVER "
    private String version = "VER 160305";
    //+ "\0d\0a$";

    /** work for debug */
//#if MOON_HOOT
//        private short MDB_BASE=0x2F0;
//#else
    private final byte[] MDB_BASE = new byte[0x008];
//#endif

    /**
     * Initialises all driver things
     */
    private void moon_init_all() {
        work = new Work();
        work.ch = new Work.Ch[USE_CH];
        for (int i = 0; i < USE_CH; i++) work.ch[i] = new Ch();
        moon_init();
        moon_seq_init();
    }

    /**
     * initialize MoonSound
     */
    private void moon_init() {
        // CONNECTION SEL
        d = 0x4;
        e = 0;
        moon_fm2_out();

        // set 1 to NEW2, NEW
        d = 0x05;
        e = 0x03;
        moon_fm2_out();

        // RHYTHM
        d = (byte) 0xbd;
        e = 0;
        moon_fm1_out();

        // Set WaveTable header
        d = 0x02;
        e = 0x10;
        moon_wave_out();
    }

    //
    // Memory access routines
    //

    /**
     * set page3 to the bank of current channel
     * dest : AF
     */
    private void set_page3_ch() {

        a = work.ch[ix & 0xffff].bank;
        change_page3();
    }

    /**
     * changes page3
     * in   : A = page
     * dest : AF
     */
    private void change_page3() {
        //logger.log(Level.DEBUG, "ChangePage3: %d".formatted(a));
        a = (byte) ((a & 0xff) >>> 1); // srl
        a += 0x04; // The system uses 4pages for initial work area
        outport(RAM_PAGE3, a);
    }

    /**
     * get_table
     * in   : A = index, HL = address
     * out  : HL = (HL + (A * 2))
     * dest : AF,DE
     */
    private void get_table() {
        e = a;
        d = 0;

        get_table_hl_2de();
    }

    /**
     * get_hl_table
     * in   : HL = address
     * out  : HL = (HL + (cur_ch * 2))
     * dest : AF,DE
     */
    private void get_hl_table() {
        a = work.seq_cur_ch;

        e = a;
        d = 0x00;

        get_table_hl_2de();
    }

    private void get_table_hl_2de() {
        a = (byte) readMemory(hl).dat;
        hl++;
        hl = (short) ((readMemory(hl).dat & 0xff) * 0x100 + (a & 0xff));

        hl += e & 0xff;
        hl += e & 0xff;

        a = (byte) readMemory(hl).dat;
        hl++;
        hl = (short) ((readMemory(hl).dat & 0xff) * 0x100 + (a & 0xff));
    }

    /**
     * get_a_table
     * in   : HL = address
     * out  : A = (HL + (cur_ch * 2))
     * dest : HL,DE
     */
    private void get_a_table() {
        a = work.seq_cur_ch;
        e = a;
        d = 0x00;
        a = (byte) readMemory(hl).dat;
        hl++;
        hl = (short) ((readMemory(hl).dat & 0xff) * 0x100 + (a & 0xff));
        hl += e & 0xff;

        a = (byte) readMemory(hl).dat;
    }

    /**
     * moon_seq_init
     * initializes all channel's work
     * dest : ALL
     */
    private void moon_seq_init() {
        a = 0;
        work.seq_use_ch = a;
        work.seq_cur_ch = a;
        change_page3(); // Page to Top


        a = (byte) readMemory(S_DEVICE_FLAGS).dat;
        if (a == 0) a = 1; // OPL4 by default
        b = a;
        iy = 0; // fm_opbtbl;
        ix = 0; // seq_work;

        d = 0x00;
        e = 0x18; // 24channels; OPL4
        boolean cry = (b & 1) != 0;
        b = (byte) ((b & 0xff) >>> 1);
        b |= (byte) (cry ? 0x80 : 0);
        if (cry) {
            seq_init_chan();
        }

        d = 0x01;
        e = 0x12; // 18channels
        cry = (b & 1) != 0;
        b = (byte) ((b & 0xff) >>> 1);
        b |= (byte) (cry ? 0x80 : 0);
        if (cry) {
            seq_init_chan();
        }

        ix = 0; // seq_work;
    }

    /**
     * seq_init_chan
     * in   : D = device, E = channels
     * dest : AF,E
     */
    private void seq_init_chan() {
        if (d != 0) {
            work.seq_start_fm = work.seq_use_ch;
        }
        work.seq_use_ch += e;

//seq_init_chan_lp:
        do {
            work.ch[ix & 0xffff].cnt = 0;
            work.ch[ix & 0xffff].dsel = d;
            byte db = d;
            byte eb = e;
            work.ch[ix & 0xffff].venv = (byte) 0xff;
            work.ch[ix & 0xffff].penv = (byte) 0xff;
            work.ch[ix & 0xffff].nenv = (byte) 0xff;
            work.ch[ix & 0xffff].detune = (byte) 0xff;

            if (work.ch[ix & 0xffff].dsel != 0) {
//init_fmtone:
                work.ch[ix & 0xffff].tadr = 0; // fm_testtone;
                work.ch[ix & 0xffff].pan = 0x30;
                work.ch[ix & 0xffff].reverb = 0x02; // IDX_VOLOP
                work.ch[ix & 0xffff].vol = 0x3f;
                work.ch[ix & 0xffff].opsel = fm_opbtbl[iy & 0xffff];
                iy++;
            } else {
//init_op4tone:
                work.ch[ix & 0xffff].tadr = 0; // piano_tone;
                work.ch[ix & 0xffff].pan = 0x00;
            }

//init_tone_fin:

            hl = S_TRACK_TABLE;
            get_hl_table();
            work.ch[ix & 0xffff].addr = hl;

            hl = S_TRACK_BANK;
            get_a_table();
            work.ch[ix & 0xffff].bank = a;

            work.ch[ix & 0xffff].stBank = work.ch[ix & 0xffff].bank;
            work.ch[ix & 0xffff].stAddr = work.ch[ix & 0xffff].addr;
            work.ch[ix & 0xffff].endFlg = false;

            // next work
            ix++;

            d = db;
            e = eb;

            // next channel
            work.seq_cur_ch += 1;

            e--;
        } while ((e & 0xff) > 0);
    }

    /**
     * seq_init_fmbase
     * initializes all fmbase
     * dest : ALL
     */
    private void seq_init_fmbase() {
        ix = 0; // seq_work

        b = 18; // num of fmchan
        hl = 0; // fm_opbtbl;
//seq_init_fmbase_lp1:
        do {
            a = fm_opbtbl[hl & 0xffff];
            work.ch[ix & 0xffff].opsel = a;
            hl++;
            ix++;
            b--;
        } while ((b & 0xff) > 0);
    }

    /**
     * seq_all_keyoff
     * this makes all keys off
     */
    private void moon_seq_all_keyoff() {

        moon_seq_all_release_fm();

        ix = 0; // seq_work;
        work.seq_cur_ch = 0;

//seq_all_keyoff_lp:
        do {
            moon_key_off();

            //de = SEQ_WORKSIZE;
            ix++; // += de;
            work.seq_cur_ch++;

        } while (work.seq_cur_ch < work.seq_use_ch);
//seq_all_keyoff_end:
    }

    /**
     * set RR to all fm channnels.
     */
    private void moon_seq_all_release_fm() {
        //D = $80(reg adrs) E = (sl = $00, rr = $0f)
        d = (byte) 0x80;
        e = 0x0f;

        b = 18;

        hl = 0; // fm_opbtbl;

        // channel loop
//moon_set_rr_ch_lp:
        do {
            // read opsel tbl
            a = fm_opbtbl[hl & 0xffff];
            work.seq_opsel = a;
            hl++;

            c = 4;
//moon_set_rr_op_lp:
            do {
                // write fm op
                short de = (short) ((d & 0xff) * 0x100 + (e & 0xff));
                moon_write_fmop();
                d = (byte) ((de & 0xffff) >>> 8);
                e = (byte) (de & 0xff);

                // add opsel

                a = work.seq_opsel;
                a += 3;
                work.seq_opsel = a;

                //
                c--;
            } while ((c & 0xff) > 0);

            b--;
        } while ((b & 0xff) > 0);
    }

    /**
     * moon_proc_tracks
     * Process tracks in 1/60 interrupts
     */
    private void moon_proc_tracks() {
        do {
            if (work == null) {
                return;
            }

            // reset mapper
            a = 0;
            change_page3();

            ix = 0; // seq_work
            a = 0;

            work.seq_cur_ch = a;
//proc_tracks_lp:
            int endCnt = 0;
            int loop = Integer.MAX_VALUE;

            do {
                if (work.ch[ix & 0xffff].endFlg) {
                    endCnt++;
                    ix++;
                    work.seq_cur_ch++;
                    a = work.seq_cur_ch;
                    e = work.seq_use_ch;
                    continue;
                }

                proc_venv();
                proc_penv();
                proc_nenv();

                proc_venv_reg();
                proc_freq_reg();

                seq_track();

                if (ix < work.ch.length && !work.ch[ix & 0xffff].endFlg && work.ch[ix & 0xffff].addr != 0x0) {
                    loop = Math.min(work.ch[ix & 0xffff].loopCnt, loop);
                }

                //ld de, SEQ_WORKSIZE
                ix++; // add ix, de

                a = work.seq_use_ch;
                e = a;
                a = work.seq_cur_ch;
                a++;

                if (CP_CF(e)) {
                    work.seq_cur_ch = a;
                }
                //logger.log(Level.DEBUG, "a:%d".formatted(a));

            } while (CP_CF(e));

            if (endCnt == (ix & 0xffff)) {
                stopped = true;
            }

            vgmCurLoop = loop;

//proc_tracks_end:
            a = work.seq_jump_flag;

        } while (a != 0);
        //jr moon_proc_tracks
    }

    /**
     * seq_track
     * Count down and process in a channel
     */
    private void seq_track() {
        do {
            nextFlg = false;

            a = work.ch[ix & 0xffff].cnt;

            if (a != 0) {
                a--;
                work.ch[ix & 0xffff].cnt = a;
                return;
            }

//seq_cnt_zero:
            hl = work.ch[ix & 0xffff].addr;

//seq_track_lp:
            do {
                // Read command from memory
                set_page3_ch();
                a = (byte) readMemory(hl).dat;
                hl++;

                if (CP_CF((byte) 0xe0)) {
                    seq_repeat_or_note();
                    break;
                }

//seq_command:
                //bc = seq_track_lp;
                //push bc; <- return address
                //  push hl; <- Preserve HL as pointer
                //a += 0x20;
                //a <<= 1;
                //hl = a;
                //bc = seq_jmptable;
                //hl += (short)(((b & 0xff) << 8) + (c & 0xff));
                // Read address from table
                //a = (byte) readMemory(hl).dat;
                //hl++;
                //hl = (short) (readMemory(hl) * 0x100);
                //hl = (short) ((hl & 0xff00) + (a & 0xff));

                seq_jmptable[(a & 0xff) - 0xe0].run();
                if (nextFlg) break;
            } while (!work.ch[ix & 0xffff].endFlg);
        } while (nextFlg);
    }

    /**
     * seq_next
     * preserves address and do next
     * in   : HL
     * dest : AF
     */
    private void seq_next() {

        work.ch[ix & 0xffff].addr = hl;
        //seq_track();
        nextFlg = true;
    }

    private void seq_repeat_or_note() {
        if (CP_CF((byte) 0x90)) {
            seq_note();
            return;
        }
        if (CP_ZF((byte) 0xa1)) {
            seq_repeat_esc();
            return;
        }

        //
//seq_repeat_end:
        a = work.ch[ix & 0xffff].loop;
        if (CP_ZF((byte) 0x01)) {
            seq_skip_rep_jmp();
            return;
        }
        if (a == 0) {
            a = (byte) readMemory(hl).dat; // read repeat counter
        }

//seq_skip_set_repcnt_end:
        seq_rep_jmp();
    }

    /** */
    private void seq_repeat_esc() {
        a = work.ch[ix & 0xffff].loop;

        if (CP_ZF((byte) 0x01)) {
            seq_rep_jmp();
            return;
        }

        if (a == 0) {
            a = (byte) readMemory(hl).dat; // read repeat counter
        }

//seq_skip_set_repcnt_esc:
        seq_skip_rep_jmp();
    }

    private void seq_skip_rep_jmp() {
        hl++;
        a--;
        work.ch[ix & 0xffff].loop = a;
        hl++; // bank
        hl++; // addr l
        hl++; // addr h

        seq_next();
    }

    private void seq_rep_jmp() {
        hl++;
        a--;

        work.ch[ix & 0xffff].loop = a;

        //bc = seq_next;
        //push    bc
        //push    hl

        //hl = seq_bank; // go to address
        //jp(hl)
        seq_bank();
        seq_next();
    }

    /** */
    private void seq_note() {

        short hlb = hl;
        byte af = a;

        a = 0;
        change_page3();

        a = work.ch[ix & 0xffff].dsel;
        if (a == 0) {
            seq_note_opl4(hlb, af);
            return;
        }

//seq_note_fm:
        a = af;

        work.ch[ix & 0xffff].note = a;
        moon_set_fmnote();
        set_note_fin(hlb);
    }

    private void seq_note_opl4(short hlb, byte af) {
//seq_note_opl4:
        a = af;
        conv_data_to_midi();

        work.ch[ix & 0xffff].note = a;
        moon_set_midinote();
        set_note_fin(hlb);
    }

    private void set_note_fin(short hlb) {
//set_note_fin:
        set_page3_ch();
        moon_key_on();

        hl = hlb;

        // note length

        a = (byte) readMemory(hl).dat;

        work.ch[ix & 0xffff].cnt = a;
        hl++;
        seq_next();
    }

    private void read_cmd_length() {
        //pop af
        a = (byte) readMemory(hl).dat;
        work.ch[ix & 0xffff].cnt = a;
        hl++;
        seq_next();
    }


    private void start_venv() {
        a = work.ch[ix & 0xffff].venv;
        if (CP_ZF((byte) 0xff)) return;
        set_venv_head();
        proc_venv_start();
    }

    private void proc_venv() {
        a = work.ch[ix & 0xffff].venv;
        if (CP_ZF((byte) 0xff)) return;
        proc_venv_start();
    }

    private void proc_venv_start() {
        hl = work.ch[ix & 0xffff].venv_adr;
        a = (byte) (hl & 0xff);
        a |= (byte) ((hl & 0xff00) >>> 8);

        if (a == 0) return;

        read_effect_value();

        if (CP_ZF((byte) 0xff)) {
//proc_venv_end:
            set_venv_loop();
            return;
        }
        hl++;
        work.ch[ix & 0xffff].venv_adr = hl;
        work.ch[ix & 0xffff].vol = a;

    }

    private void proc_venv_reg() {
        a = work.ch[ix & 0xffff].venv;

        if (CP_ZF((byte) 0xff)) return;
        moon_set_vol_ch();
    }


    private void start_penv() {
        a = work.ch[ix & 0xffff].penv;
        if (CP_ZF((byte) 0xff)) return;
        set_penv_head();
        proc_penv_start();
    }

    private void proc_penv() {
        a = work.ch[ix & 0xffff].penv;
        if (CP_ZF((byte) 0xff)) return;
        proc_penv_start();
    }

    private void proc_penv_start() {

        hl = work.ch[ix & 0xffff].penv_adr;
        read_effect_value();

        if (CP_ZF((byte) 0xff)) {
//proc_penv_end:
            set_penv_loop();
            return;
        }

        hl++;
        work.ch[ix & 0xffff].penv_adr = hl;

        byte af = a; //  push    af
        a = work.ch[ix & 0xffff].dsel;
        if (a == 0) {
//proc_penv_opl4:
            a = af;

            hl = work.ch[ix & 0xffff].pitch;
            add_freq_offset();
            work.ch[ix & 0xffff].pitch = hl;

            moon_calc_opl4freq();
            return;
        }

//proc_penv_fm:
        a = af;

        hl = work.ch[ix & 0xffff].fnum;
        add_freq_offset();

        a = (byte) ((hl & 0xffff) >>> 8);
        if (!CP_CF((byte) 0x80)) {
//penv_fm_set_fnum:
            work.ch[ix & 0xffff].fnum = hl;
            moon_key_fmfreq();
            return;
        }

        d = 1;
        e = 0x5a; // de= 346;

        int ans = comp_hl_de();
        if (ans <= 0) {
//penv_fm_dec_oct:
            //	; hl < de
            do {
                work.ch[ix & 0xffff].oct--;
                hl += (short) (((d & 0xff) << 8) + (e & 0xff));

                ans = comp_hl_de();
            } while (ans < 0);

        } else {
            d = 2;
            e = (byte) 0xb5; // de=693

            ans = comp_hl_de();
            if (ans >= 0) {
//penv_fm_inc_oct:
                //            ; hl > de

                b = 1;
                c = 0x5a; // bc= 346;

//penv_fm_inc_oct_lp:
                do {
                    work.ch[ix & 0xffff].oct++;

                    a = 0;
                    //    sbc hl, bc
                    hl -= (short) (((b & 0xff) << 8) + (c & 0xff));
                    ans = comp_hl_de();
                } while (ans >= 0);
            }
        }

//penv_fm_set_fnum:
        work.ch[ix & 0xffff].fnum = hl;
        moon_key_fmfreq();
    }

    private int comp_hl_de() {
        return (hl & 0xffff) - (((d & 0xff) << 8) + (e & 0xff));
    }

    private void start_nenv() {
        a = work.ch[ix & 0xffff].nenv;
        if (CP_ZF((byte) 0xff)) return;
        set_nenv_head();
        proc_nenv_start();
    }

    private void proc_nenv() {
        a = work.ch[ix & 0xffff].nenv;

        if (CP_ZF((byte) 0xff)) return;
        proc_nenv_start();
    }

    private void proc_nenv_start() {
        hl = work.ch[ix & 0xffff].nenv_adr;
        read_effect_value();

        if (CP_ZF((byte) 0xff)) {
            set_nenv_loop();
            return;
        }

        hl++;
        work.ch[ix & 0xffff].nenv_adr = hl;

        byte af = a; //  push    af
        a = work.ch[ix & 0xffff].dsel;
        if (a != 0) {
            a = af;
            proc_nenv_fm();
        } else {
            a = af;
            proc_nenv_opl4();
        }
    }

    private void proc_nenv_opl4() {
        if ((a & 0x80) == 0) {

            a += work.ch[ix & 0xffff].note;
            work.ch[ix & 0xffff].note = a;

        } else {
//proc_nenv_nega_opl4:
            a &= 0x7f;
            e = a;
            a = work.ch[ix & 0xffff].note;
            a -= e;
            work.ch[ix & 0xffff].note = a;
        }

//proc_nenv_opl4_setnote
        moon_calc_midinote();
        moon_calc_opl4freq();
    }

    private void proc_nenv_fm() {

        b = 0x00;

        if ((a & 0x80) != 0) {
            proc_nenv_fm_nega();
            return;
        }
//proc_nenv_fm_lp1:
        while (!CP_CF((byte) 0xc)) {
            a -= 0xc;
            b++;
        }
//proc_nenv_fm_add:
        c = a; // C = (note % 12)
        a = work.ch[ix & 0xffff].note;
        a &= 0xf;
        a += c;

        if (!CP_CF((byte) 0xc)) {
            a += 0x4;
            a &= 0xf;
            b++;
        }
//skip_nenv_inc_oct:
        c = a; // C = (note & 0x0f)

        a = b; // B = oct

        a = (byte) (((a & 0xff) << 1) + ((a & 0xff) >>> 7)); // rlca
        a = (byte) (((a & 0xff) << 1) + ((a & 0xff) >>> 7));
        a = (byte) (((a & 0xff) << 1) + ((a & 0xff) >>> 7));
        a = (byte) (((a & 0xff) << 1) + ((a & 0xff) >>> 7));

        b = a;
        a = work.ch[ix & 0xffff].note;

        a &= (byte) 0xf0;
        a += b;
        a |= c;

        work.ch[ix & 0xffff].note = a;
        moon_set_fmnote();
        moon_key_fmfreq();
    }

    private void proc_nenv_fm_nega() {
        a &= 0x7f;
//proc_nenv_fm_nega_lp1
        do {
            if (CP_CF((byte) 0xc)) {
                break;
            }
            a -= 0xc;
            b++;
        } while (true);
//proc_nenv_fm_sub:
        c = a; // C = (note % 12)

        a = work.ch[ix & 0xffff].note;
        a &= 0xf;
        int ai = (a & 0xff) - (c & 0xff);
        a = (byte) ai;
        if (ai < 0) {
            a -= 0x04;
            a &= 0xf;
            b++;
        }
//skip_nenv_dec_oct:
        c = a; // C = (note & 0x0f)
        a = b; // B = oct

        a = (byte) (((a & 0xff) << 1) + ((a & 0xff) >>> 7)); // rlca
        a = (byte) (((a & 0xff) << 1) + ((a & 0xff) >>> 7));
        a = (byte) (((a & 0xff) << 1) + ((a & 0xff) >>> 7));
        a = (byte) (((a & 0xff) << 1) + ((a & 0xff) >>> 7));

        b = a;
        a = work.ch[ix & 0xffff].note;
        a &= (byte) 0xf0;
        a -= b;
        a |= c;

        work.ch[ix & 0xffff].note = a;
        moon_set_fmnote();
        moon_key_fmfreq();
    }

    /**
     * Set frequency to registers actually
     */
    private void proc_freq_reg() {
        a = work.ch[ix & 0xffff].penv;

        if (!CP_ZF((byte) 0xff)) {
            moon_set_freq_ch();
            return;
        }
        a = work.ch[ix & 0xffff].nenv;

        if (!CP_ZF((byte) 0xff)) {
            moon_set_freq_ch();
            return;
        }

//proc_freq_to_moon:
        //jp moon_set_freq_ch
    }

    private void seq_nop() {
        //nothing
    }

    private void seq_skip_1() {
        //pop hl
        hl++;
    }

    /** cmd $FF : loop point */
    private void seq_loop() {

        a = 0;
        change_page3();

        hl = S_LOOP_TABLE;
        get_hl_table();

        short hl1 = hl;

        hl = S_LOOP_BANK;
        get_a_table();

        work.ch[ix & 0xffff].bank = a;
        change_page3();

        hl = hl1;
    }

    /** cmd $FD : volume */
    private void seq_volume() {

        a = (byte) readMemory(hl).dat;
        work.ch[ix & 0xffff].venv = a;

        if ((0x80 & a) == 0) {
            seq_venv();
            return;
        }

        a &= 0x7f;
        work.ch[ix & 0xffff].vol = a;

        a = (byte) 0xff;
        work.ch[ix & 0xffff].venv = a; // venv = off

        moon_set_vol_ch();

        hl++;
    }

    private void seq_venv() {
        set_venv_head();
        hl++;
    }

    private void seq_rest() {
        moon_key_off();
        read_cmd_length();

        if (work.ch[ix & 0xffff].cnt == (byte) 255) {
            byte vee = (byte) readMemory(hl).dat;
            byte v00 = (byte) readMemory((short) ((hl + 1) & 0xffff)).dat;
            short adr = (short) (readMemory((short) ((hl + 2) & 0xffff)).dat + readMemory((short) ((hl + 3) & 0xffff)).dat * 0x100);
            if (vee == (byte) 0xee && v00 == 0x00 && ((hl - 2) & 0xffff) == (adr & 0xffff)) {
                work.ch[ix & 0xffff].endFlg = true;
            }
        }
    }

    private void seq_detune() {
        a = (byte) readMemory(hl).dat;

        work.ch[ix & 0xffff].detune = a;
        hl++;
    }

    private void seq_penv() {
        a = (byte) readMemory(hl).dat;

        hl++;
        work.ch[ix & 0xffff].penv = a;
        if (!CP_ZF((byte) 0xff)) {
            set_penv_head();
        }
    }

    private void seq_nenv() {
        a = (byte) readMemory(hl).dat;

        hl++;
        work.ch[ix & 0xffff].nenv = a;
        if (!CP_ZF((byte) 0xff)) {
            set_nenv_head();
        }
    }

    private void seq_data_write() {
        a = (byte) readMemory(hl).dat;
        d = a; // Address Low
        hl++;

        a = (byte) readMemory(hl).dat; // Address High
        if (a == 0) {
            write_data_cur_fm(); // (a >> 8) == 0
            return;
        }

        a--;
        if (a == 0) {
            write_data_fm1(); // (a >> 8) == 1
            return;
        }

        a--;
        if (a == 0) {
            write_data_fm2(); // (a >> 8) == 2
            return;
        }

        a--;
        if (a == 0) {
            write_data_wave(); // (a >> 8) == 3
            return;
        }

        hl++;
    }

    private void write_data_cur_fm() {
        hl++;

        a = (byte) readMemory(hl).dat;

        e = a; // Data
        hl++;

        a = work.seq_cur_ch;
        if (CP_CF((byte) 9))
            moon_fm1_out();
        else
            moon_fm2_out();
    }

    private void write_data_fm1() {
        hl++;

        a = (byte) readMemory(hl).dat;

        e = a; // Data
        hl++;
        moon_fm1_out();
    }

    private void write_data_fm2() {
        hl++;

        a = (byte) readMemory(hl).dat;

        e = a; // Data
        hl++;
        moon_fm2_out();
    }

    private void write_data_wave() {
        hl++;

        a = (byte) readMemory(hl).dat;

        e = a; // Data
        hl++;
        moon_wave_out();
    }

    private void seq_wait() {
        read_cmd_length();
    }

    private void seq_drum() {
        a = (byte) readMemory(hl).dat;

        a &= 0x1f;

        e = a;

        a = work.seq_reg_bd;

        a &= (byte) 0xe0;
        a |= e;

        work.seq_reg_bd = a;
        e = a;
        d = (byte) 0xbd;
        moon_fm1_out();
        hl++;
    }

    private void seq_drumbit() {
        // drums key-off

        a = (byte) readMemory(hl).dat;
        a &= 0x1f;
        a ^= (byte) 0xff;

        // e = mask bits of drums
        e = a;
        a = work.seq_reg_bd;
        a &= e;

        work.seq_reg_bd = a;
        e = a;
        d = (byte) 0xbd;
        moon_fm1_out();

        // set fnum
        short hlb = hl;
        a = (byte) readMemory(hl).dat;
        a &= 0x1f;

        c = 0;
        b = 5;

        a = (byte) (((a & 0xff) << 1) + ((a & 0x80) != 0 ? 1 : 0)); // rlca
        a = (byte) (((a & 0xff) << 1) + ((a & 0x80) != 0 ? 1 : 0));
        a = (byte) (((a & 0xff) << 1) + ((a & 0x80) != 0 ? 1 : 0));

        // A = drum bits, BC = count
//drumbit_fnum_lp:
        do {
            boolean cry = (a & 0x80) != 0;
            a = (byte) (((a & 0xff) << 1) + (cry ? 1 : 0)); // rlca
            if (cry) {
                byte af = a;
                byte bb = b;
                byte cb = c;
                drumbit_set_fnum(); // set Fnum for drums
                b = bb;
                c = cb;
                a = af;
            }
//drumbit_fnum_next:
            c++;
            b--;
        } while ((b & 0xff) > 0);

        hl = hlb;

        // skip if jump flag is true
        a = work.seq_jump_flag;
        if (a == 0) {
            // drums key-on
            a = (byte) readMemory(hl).dat;
            a &= 0x1f;
            e = a;
            a = work.seq_reg_bd;
            a &= (byte) 0xe0;
            a |= e;
            work.seq_reg_bd = a;
            e = a;
            d = (byte) 0xbd;
            moon_fm1_out();
        }

//drumbit_skip_keyon:
        // length check
        a = (byte) readMemory(hl).dat;
        a &= (byte) 0x80; // Lxxxxxxx L = the command has length
        if (a != 0) {
            // drumbit with length
//drumbit_with_length:
            hl++;
            read_cmd_length();
            return;
        }
        hl++;
    }

    /**
     * Set Fnum for drum
     * C = index
     * dest : almost all
     */
    private void drumbit_set_fnum() {
        // fnum
        hl = 0; // fm_drum_fnum
        b = 0;
        work.seq_tmp_fnum = fm_drum_fnum[c];

        // oct
//fm_drum_oct:
        work.seq_tmp_oct = fm_drum_oct[c];

        // ch
//fm_drum_fnum_map:
        work.seq_tmp_ch = fm_drum_fnum_map[c];

        // write FnumL
        moon_key_write_fmfreq_base();

        e = a;
        d = (byte) 0xb0;
        a = work.seq_tmp_ch;

        // write FnumH and BLK
        moon_write_fmreg_nch();
    }

    private void seq_drumnote() {
        // set fnum
        a = (byte) readMemory(hl).dat;
        a &= 0x1f;

        byte af = a;
        hl++;
        a = (byte) readMemory(hl).dat;
        work.seq_tmp_note = a;
        hl++;
        a = af;

        short hlb = hl;
        c = 0;
        b = 5;

        a = (byte) (((a & 0xff) << 1) + ((a & 0x80) != 0 ? 1 : 0)); //    rlca
        a = (byte) (((a & 0xff) << 1) + ((a & 0x80) != 0 ? 1 : 0)); //    rlca
        a = (byte) (((a & 0xff) << 1) + ((a & 0x80) != 0 ? 1 : 0)); //    rlca

        // A = drum bits, BC = count
//drumnote_lp:
        do {
            boolean cry = (a & 0x80) != 0;
            a = (byte) (((a & 0xff) << 1) + (cry ? 1 : 0)); //    rlca

            if (!cry) {
//drumnote_next:
                c++;
            } else {
                af = a;
                byte bb = b;
                byte cb = c;
                drumnote_fnum(); // set Fnum for drums
                b = bb;
                c = cb;
                a = af;
            }
            b--;
        } while ((b & 0xff) > 0);

        hl = hlb;
        hl++;
    }

    /**
     * C = index
     * dest: AF, BC, HL
     */
    private void drumnote_fnum() {
        byte bb = b;
        byte cb = c;
        a = work.seq_tmp_note;
        moon_calc_opl3note();
        b = bb;
        c = cb;

        // oct
        b = 0;

        hl = 0; // fm_drum_oct
        hl += c;
        a = work.seq_tmp_oct;
        fm_drum_oct[hl & 0xffff] = a;

        // fnum
        hl = 0; // fm_drum_fnum
        hl += c;
        fm_drum_fnum[hl & 0xffff] = work.seq_tmp_fnum;
    }

    private void seq_inst() {
        //pop hl
        a = (byte) readMemory(hl).dat;
        byte af = a;
        short hlb;
        a = 0;
        change_page3();

        // Device select
        a = work.ch[ix & 0xffff].dsel;
        if (a == 0) {
//seq_inst_opl4:
            a = af;
            hlb = hl;
            hl = S_INST_TABLE;
            get_table();
            work.ch[ix & 0xffff].tadr = hl;

//seq_inst_fin:
            set_page3_ch();
            hl = hlb;
            hl++;
            return;
        }

        // Load OPL3 instrument
        a = af;
        hlb = hl;
        hl = S_OPL3_TABLE;
        get_table();
        work.ch[ix & 0xffff].tadr = hl;
        // set tone to FM
        moon_set_fmtone();

//seq_inst_fin:
        set_page3_ch();
        hl = hlb;
        hl++;
    }

    private void seq_pan() {
        // Device select
        a = work.ch[ix & 0xffff].dsel;

        if (a == 0) {
//seq_pan_opl4:
            a = (byte) readMemory(hl).dat;
            work.ch[ix & 0xffff].pan = a;
        } else {
//seq_pan_fm:
            a = (byte) readMemory(hl).dat;
            a &= 0xf;
            a = (byte) (((a & 0xff) << 1) + (((a & 0x80) != 0) ? 1 : 0)); // rlca
            a = (byte) (((a & 0xff) << 1) + (((a & 0x80) != 0) ? 1 : 0)); // rlca
            a = (byte) (((a & 0xff) << 1) + (((a & 0x80) != 0) ? 1 : 0)); // rlca
            a = (byte) (((a & 0xff) << 1) + (((a & 0x80) != 0) ? 1 : 0)); // rlca

            work.ch[ix & 0xffff].pan = a; // PPPPxxxx
            moon_write_fmpan(); // Write PAN to FM
        }

//seq_pan_fin:
        hl++;
    }

    private void seq_lfosw() {
        a = (byte) readMemory(hl).dat;
        work.ch[ix & 0xffff].lfo = a;
        hl++;
    }

    private void seq_bank() {
        a = (byte) readMemory(hl).dat;
        hl++;

        byte af = a;

        a = (byte) readMemory(hl).dat;
        hl++;
        hl = (short) (readMemory(hl).dat * 0x100 + (a & 0xff));

        a = 0;
        change_page3();
        short ltbl = (short) (readMemory(S_LOOP_TABLE).dat + readMemory((short) ((S_LOOP_TABLE + 1) & 0xffff)).dat * 0x100 + ix * 2);
        ltbl = (short) (readMemory(ltbl).dat + readMemory((short) ((ltbl + 1) & 0xffff)).dat * 0x100);
        if (hl == ltbl) {
            work.ch[ix & 0xffff].loopCnt += (work.ch[ix & 0xffff].loopCnt == Integer.MAX_VALUE) ? 0 : 1;
        }

        a = af;

        work.ch[ix & 0xffff].bank = a;
        change_page3();
    }

    private void seq_damp() {
        // Device select
        a = work.ch[ix & 0xffff].dsel;
        if (a == 0) {
//seq_damp_opl4:
            a = (byte) readMemory(hl).dat;
            work.ch[ix & 0xffff].damp = a;
        } else {
            a = (byte) readMemory(hl).dat;
            a &= 0x3f;
            e = a;
            d = 0x4;

            moon_fm2_out();
        }
//seq_damp_fin:
        hl++;
    }

    private void seq_revbsw() {
        a = (byte) readMemory(hl).dat;
        work.ch[ix & 0xffff].reverb = a;
        hl++;
    }

    private void seq_slar() {
        work.ch[ix & 0xffff].efx1 |= 1;
    }

    private void seq_setop() {
        a = (byte) readMemory(hl).dat;
        work.ch[ix & 0xffff].opsel = a;
        hl++;
    }

    private void seq_ld2ops() {
        a = work.ch[ix & 0xffff].dsel;
        if (a == 0) {
            hl++;
            return;
        }

//seq_ld2ops_fm:
        a = (byte) readMemory(hl).dat;
        byte af = a;
        a = 0;
        change_page3();
        a = af;

        short hlb = hl;
        hl = S_OPL3_TABLE;

        get_table();

        work.ch[ix & 0xffff].tadr = hl;

        // Set 2OP tone to FM
        moon_set_fmtone2();
        set_page3_ch();

        hl = hlb;

        hl++;
    }

    private void seq_tvp() {
        a = (byte) readMemory(hl).dat;
        a &= 0x7;

        a = (byte) (((a & 0xff) >>> 1) + ((a & 1) != 0 ? 0x80 : 0)); // rrca
        a = (byte) (((a & 0xff) >>> 1) + ((a & 1) != 0 ? 0x80 : 0));
        a = (byte) (((a & 0xff) >>> 1) + ((a & 1) != 0 ? 0x80 : 0));
        e = a;
        a = work.seq_reg_bd;
        a &= 0x1f;
        a |= e;

        work.seq_reg_bd = a;
        e = a;
        d = (byte) 0xbd;
        moon_fm1_out();
        hl++;
    }

    private void seq_fbs() {
        a = (byte) readMemory(hl).dat;
        a &= 0x7;

        a = (byte) (((a & 0xff) << 1) + ((a & 0x80) != 0 ? 1 : 0)); // rlca
        a = (byte) (((a & 0xff) << 1) + ((a & 0x80) != 0 ? 1 : 0));

        e = a;
        a = work.ch[ix & 0xffff].synth;
        a &= (byte) 0xe3;
        a |= e;

        work.ch[ix & 0xffff].synth = a;
        hl++;
    }

    private void seq_jump() {
        a = (byte) readMemory(hl).dat;
        work.seq_jump_flag = a;
        hl++;
    }

    /*
     * pause_venv
     * dest : AF
     * pause_venv:
     */
    //xor a
    //
    //ld(ix + IDX_VENV_ADR), a
    //ld(ix + IDX_VENV_ADR+1), a
    //ret

    /**
     * read_effect_table
     * in   : A  = index
     *      : HL = table address
     *      : DE = pointer to value in work
     * out  : (ix + de) = (HL + 2A)
     * dest : AF
     */
    private void read_effect_table(/* ref */ short[] adr) {
        byte af = a;
        a = 0;
        change_page3();
        a = af;

        get_table();

        adr[0] = hl;

        set_page3_ch();
    }

    private void set_venv_loop() {
        short hlb = hl;

        hl = S_VENV_LOOP;

        //jr set_venv_hl
//set_venv_hl:
        //de = IDX_VENV_ADR;

        a = work.ch[ix & 0xffff].venv;
        a &= 0x7f;
        short[] tmp = {0};
        read_effect_table(tmp);
        work.ch[ix & 0xffff].venv_adr = tmp[0];

        hl = hlb;
    }

    private void set_venv_head() {
        short hlb = hl;

        hl = S_VENV_TABLE;

//set_venv_hl:
        //de = IDX_VENV_ADR;
        a = work.ch[ix & 0xffff].venv;
        a &= 0x7f;
        short[] tmp = {0};
        read_effect_table(tmp);
        work.ch[ix & 0xffff].venv_adr = tmp[0];

        hl = hlb;
    }

    /**
     * set_penv_loop
     * dest : AF, DE
     */
    private void set_penv_loop() {
        short hlb = hl;

        hl = S_PENV_LOOP;

        //jr set_penv_hl
//set_penv_hl:
        //de = IDX_PENV_ADR;

        a = work.ch[ix & 0xffff].penv;
        short[] tmp = {0};
        read_effect_table(tmp);
        work.ch[ix & 0xffff].penv_adr = tmp[0];

        hl = hlb;
    }

    private void set_penv_head() {
        short hlb = hl;

        hl = S_PENV_TABLE;

//set_penv_hl:
        //de = IDX_PENV_ADR;
        a = work.ch[ix & 0xffff].penv;
        short[] tmp = {0};
        read_effect_table(tmp);
        work.ch[ix & 0xffff].penv_adr = tmp[0];

        hl = hlb;
    }

    /**
     * set_nenv_loop
     * dest : AF, DE
     */
    private void set_nenv_loop() {
        //    push hl
        short hl1 = hl;
        hl = S_NENV_LOOP;
//set_nenv_hl:
        //de = IDX_NENV_ADR;
        a = work.ch[ix & 0xffff].nenv;
        short[] tmp = {0};
        read_effect_table(tmp);
        work.ch[ix & 0xffff].nenv_adr = tmp[0];
        hl = hl1;
    }

    /**
     * set_nenv_head
     * dest : AF, DE
     */
    private void set_nenv_head() {
        //    push hl
        short hl1 = hl;
        hl = S_NENV_TABLE;
//set_nenv_hl:
        //de = IDX_NENV_ADR;
        a = work.ch[ix & 0xffff].nenv;
        short[] tmp = {0};
        read_effect_table(tmp);
        work.ch[ix & 0xffff].nenv_adr = tmp[0];
        hl = hl1;
    }

    /**
     * read_effect_value
     * read_effect_value
     * in  : HL = address
     * out : A = data
     */
    private void read_effect_value() {

        a = 0;
        change_page3();

        a = (byte) readMemory(hl).dat;
        byte af = a;
        set_page3_ch();
        a = af;
    }

    /**
     * conv_data_to_midi
     * Converts data to midi note
     * in   : A = data($40 = o4c)
     * out  : A = midi note
     * dest : AF,DE
     */
    private void conv_data_to_midi() {
        d = 0x00;
        e = a;

        //a = (byte) (((a & 0xff) >> 1) + ((a & 1) != 0 ? 0x80 : 0));
        //a = (byte) (((a & 0xff) >> 1) + ((a & 1) != 0 ? 0x80 : 0));
        //a = (byte) (((a & 0xff) >> 1) + ((a & 1) != 0 ? 0x80 : 0));
        //a = (byte) (((a & 0xff) >> 1) + ((a & 1) != 0 ? 0x80 : 0));
        a= (byte) ((a & 0xff) >>> 4);
        a &= 0xf;
        d = a;
        if (a != 0) {
            a = 0;
            //conv_midi_lp:
            do {
                a += 0x0c;
                d--;
            } while (d != 0);
        }
        //skip_conv_midi_lp:
        d = a;
        a = e;
        a &= 0xf;
        a += d;
        a += 0xc;
    }

    /**
     * oct_div
     * octave divider
     * in   : H = pitch / 0x100
     * out  : L = octave
     * dest : AF
     */
    private void oct_div() {
        a = (byte) ((hl & 0xff00) >>> 8);
        hl &= (short) 0xff00;

//oct_div_lp:
        do {
            int ac = (a & 0xff) + 0xfa;
            a = (byte) ac;
            if (ac < 0x100) return;
            byte l = (byte) (hl & 0xff);
            l++;
            hl = (short) ((hl & 0xff00) + (l & 0xff));
        } while (true);
    }

    /**
     * make_fnum
     * Make F-number from pitch
     * F-num = pitch / $600
     * in   : HL = pitch
     * out  : HL = f-num
     * dest : AF, DE
     */
    private void make_fnum() {

        d = (byte) 0xfa;
        e = 0;
//make_fnum_lp:
        int hlc;
        do {
            hlc = (hl & 0xffff) + (((d & 0xff) << 8) + (e & 0xff));
            hl = (short) hlc;
        } while (hlc > 0xffff);

        d = 0x06;
        e = 0;
        hl += (short) (((d & 0xff) << 8) + (e & 0xff));
        short de = hl;
        hl = (short) (((d & 0xff) << 8) + (e & 0xff));
        d = (byte) ((de & 0xffff) >>> 8);
        e = (byte) (de & 0xff);

        hl = 0; //    ld hl, freq_table
        hl += (short) (((d & 0xff) << 8) + (e & 0xff));
        //hl += (short)(((d & 0xff) << 8) + (e & 0xff));
        hl = freq_table[hl & 0xffff]; //    ld a, (hl)
        //hl++;
        //    ld h, (hl)
        //    ld l, a
    }

    /**
     * moon_set_fmtone2
     * load and set 2 oprators from data in table
     * in   : work
     * dest : DE
     */
    private void moon_set_fmtone2() {
        //push af
        //push bc
        //push hl
        // repeat 2times
        moon_set_fmtone_start_lp((byte) 2);
    }

    /**
     * moon_set_fmtone
     * load and set 4 oprators from data in table
     * in   : work
     * dest : DE
     */
    private void moon_set_fmtone() {
        //push af
        //push bc
        //push hl
        // repeat 4 times
        moon_set_fmtone_start_lp((byte) 4);
    }

    private void moon_set_fmtone_start_lp(byte rt) {
        byte af = a;
        byte bb = b;
        byte cb = c;
        short hlb = hl;
        c = rt;

        hl = work.ch[ix & 0xffff].tadr;
        a = work.ch[ix & 0xffff].opsel;
        work.seq_opsel = a;

        // FBS store to IDX_SYNTH(OxxFFFSS)
        a = (byte) readMemory(hl).dat;
        a &= 0x0e;
        a = (byte) (((a & 0xff) << 1) + (((a & 0x80) != 0) ? 1 : 0)); //rlca
        e = a;
        a = (byte) readMemory(hl).dat;
        a &= 0x01;
        a |= e;
        e = a;
        hl++;

        a = work.ch[ix & 0xffff].synth;
        a &= (byte) 0xe2;
        a |= e;
        work.ch[ix & 0xffff].synth = a; // xxxFFFxS

        a = c;
        if (!CP_ZF((byte) 0x04)) {
            //fmtone_skip_set_fbs2:
            a = work.ch[ix & 0xffff].synth;
            a &= 0x7f;
            work.ch[ix & 0xffff].synth = a;
            hl++;
        } else {
            // FBS-2  Store SynthType for 4OP
            a = (byte) readMemory(hl).dat;
            a &= 0x01;
            a = (byte) (((a & 0xff) << 1) + (((a & 0x80) != 0) ? 1 : 0)); //rlca
            a |= (byte) 0x80; // 4OP flag
            e = a;
            a = work.ch[ix & 0xffff].synth;
            a &= 0x7d; // mask for 4OP and 2nd SynthType
            a |= e;

            work.ch[ix & 0xffff].synth = a; // OxxFFFSS
            hl++;
        }

//fmtone_set_tvp:
        // TYP in W / WX is removed
        hl++;
        moon_load_fmvol();

//moon_set_fmtone_lp1:
        do {
            moon_set_fmop();
            a = work.seq_opsel;
            a += 0x03;
            work.seq_opsel = a;
            c--;
        } while (c != 0);

        moon_write_fmpan();

        hl = hlb;
        b = bb;
        c = cb;
        a = af;
    }

    private void moon_load_fmvol() {
        short hlb = hl;
        byte bb = b;
        byte cb = c;
        short ixb = ix;

        hl++; // skip Reg.$20
        d = 0;
        e = 5;

//moon_load_fmvol_lp:
        int i = 0;
        do {
            a = (byte) readMemory(hl).dat;
            work.ch[ix & 0xffff].ol[i] = a; // .ar_d1r = a;
            i++; // ix++;
            hl += (short) (((d & 0xff) << 8) + (e & 0xff));
            c--;
        } while (c != 0);

        ix = ixb;
        b = bb;
        c = cb;
        hl = hlb;
    }

    private void moon_set_fmop() {
        a = (byte) readMemory(hl).dat;
        e = a;
        d = 0x20;
        moon_write_fmop();
        hl++;

        a = (byte) readMemory(hl).dat;
        e = a;
        d = 0x40;
        moon_write_fmop(); // OL
        hl++;

        a = (byte) readMemory(hl).dat;
        e = a;
        d = 0x60;
        moon_write_fmop();
        hl++;

        a = (byte) readMemory(hl).dat;
        e = a;
        d = (byte) 0x80;
        moon_write_fmop();
        hl++;

        a = (byte) readMemory(hl).dat;
        e = a;
        d = (byte) 0xe0;
        moon_write_fmop();
        hl++;
    }

    /**
     * moon_tonesel
     * Select tone number from table(OPL4 )
     * in   : work
     * dest : flags, HL
     */
    private void moon_tonesel() {
        hl = work.ch[ix & 0xffff].tadr;
        byte af;
//tonesel_lp01:
        do {
            af = a;
            short hlb = hl;

            a = (byte) readMemory(hl).dat;
            hl++;
            a |= (byte) readMemory(hl).dat;

            if (a == 0) {
                //tonesel_fin(); // ; if min == 0 && max == 0
                hl = hlb;
                a = af;
                return;
            }

            hl = hlb;
            a = af;

            if ((a & 0xff) - readMemory(hl).dat < 0) {
                //tonesel_skip01(); // if a < (hl)
                hl++;
                hl += 0x000a;
            } else {
                hl++;
                if ((a & 0xff) - readMemory(hl).dat <= 0) {
                    //tonesel_loadtone(); // if a < (hl)
                    break;
                } else {
//tonesel_skip02();
                    hl += 0x000a;
                }
            }
        } while (true);

//tonesel_loadtone:
        af = a;
        hl++;
        a = (byte) readMemory(hl).dat;
        work.ch[ix & 0xffff].tone = (short) (a & 0xff);
        hl++;
        a = (byte) readMemory(hl).dat;
        work.ch[ix].tone += (short) ((a & 0xff) << 8);
        hl++;

        a = (byte) readMemory(hl).dat;
        work.ch[ix & 0xffff].p_ofs = (short) (a & 0xff);
        hl++;
        a = (byte) readMemory(hl).dat;
        work.ch[ix & 0xffff].p_ofs += (short) ((a & 0xff) << 8);
        hl++;

        a = (byte) readMemory(hl).dat;
        work.ch[ix & 0xffff].lfo_vib = a;
        hl++;

        a = (byte) readMemory(hl).dat;
        work.ch[ix & 0xffff].ol[0] = a; // .ar_d1r = a;
        hl++;

        a = (byte) readMemory(hl).dat;
        work.ch[ix & 0xffff].ol[1] = a; // .dl_d2r = a;
        hl++;

        a = (byte) readMemory(hl).dat;
        work.ch[ix & 0xffff].ol[2] = a; // .rc_rr = a;
        hl++;

        a = (byte) readMemory(hl).dat;
        work.ch[ix & 0xffff].ol[3] = a; // .am = a;
        hl++;

        a = af;
    }

    /**
     * moon_calc_opl4freq
     * in   : HL = pitch
     * dest : AF,HL
     */
    private void moon_calc_opl4freq() {
        short hl1 = hl;

        oct_div();
        a = (byte) (hl & 0xff);
        a += (byte) 0xf8;
        work.ch[ix & 0xffff].oct = a;

        hl = hl1;

        make_fnum();

        work.ch[ix & 0xffff].fnum = hl;
    }

    /**
     * moon_calc_midinote
     * calclulates freq from note
     * in   : A = note( 04c = 0x3c)
     * out  : work
     * dest : AF,DE
     */
    private void moon_calc_midinote() {
        a += (byte) 0xc4; // a -= $3c
        hl &= (short) 0xff00;
        boolean cry = (a & 1) != 0;
        a = (byte) ((a & 0xff) >>> 1);
        hl = (short) ((hl & 0xff) + ((a & 0xff) << 8));
        byte l = (byte) (hl & 0xff);
        boolean cry2 = (l & 0x1) != 0;
        l = (byte) (((l & 0xff) >>> 1) + (cry ? 0x80 : 0));
        if (cry2) l |= (byte) 0x80;
        hl = (short) ((hl & 0xff00) + (l & 0xff));

        a &= 0x40;
        if (a != 0) {
            a = (byte) ((hl & 0xff00) >>> 8);
            a |= (byte) 0x80;
            hl = (short) ((hl & 0xff) + ((a & 0xff) << 8));
        }
        //skip_set_nega; if a >= $80 then it's negative
        d = 0x1e; // 7680
        e = 0;
        hl += (short) (((d & 0xff) << 8) + (e & 0xff));

        e = (byte) (work.ch[ix & 0xffff].p_ofs & 0xff);
        d = (byte) ((work.ch[ix & 0xffff].p_ofs & 0xffff) >>> 8);
        hl += (short) (((d & 0xff) << 8) + (e & 0xff));

        a = work.ch[ix & 0xffff].detune;
        add_freq_offset();

//skip_detune:
        a = (byte) ((hl & 0xff00) >>> 8);

        if (!CP_CF((byte) 0x60)) {
            hl = 0x5fff;
        }
//skip_set_pitch:

        work.ch[ix & 0xffff].pitch = hl;
    }

    /**
     * add_freq_offset
     * HL = HL + VALUE
     * in : A(detune data)
     * dest : DE
     */
    private void add_freq_offset() {
        if (CP_ZF((byte) 0xff)) return;

        if ((a & 0x80) != 0) {
//add_freq_nega:
            a &= 0x7f;
            d = 0;
            e = a;
            a = 0;
            int ac = (a & 0xff) - (e & 0xff);
            a = (byte) ac;
            if (ac < 0) {
                d--;
            }
        } else {
            e = a;
            d = 0;
        }

//add_freq_de:
        e = a;
        hl += (short) (((d & 0xff) << 8) + (e & 0xff));
        hl += (short) (((d & 0xff) << 8) + (e & 0xff));
    }

    /**
     * moon_set_midinote
     * in : A = note
     */
    private void moon_set_midinote() {
        if ((work.ch[ix & 0xffff].efx1 & 1) == 0) {
            moon_tonesel();
        }

        moon_calc_midinote();

        moon_calc_opl4freq();
    }

    /**
     * moon_set_fmnote
     * in   : A = note
     * dest : AF, HL, BC
     */
    private void moon_set_fmnote() {
        moon_calc_opl3note();

        // oct
        a = work.seq_tmp_oct;
        work.ch[ix & 0xffff].oct = a;

        // Fnum
        hl = work.seq_tmp_fnum;
        work.ch[ix & 0xffff].fnum = hl;
    }

    /**
     * moon_calc_opl3note
     * in : A = note , (ix + IDX_DETUNE)
     * out : (seq_tmp_fnum), (seq_tmp_oct)
     * dest AF, BC, HL
     */
    private void moon_calc_opl3note() {
        byte af = a; //	push af

        a &= 0xf;
        if (!CP_CF((byte) 0xc)) {
            a -= 0xc;
        }
//fm_load_fnumtbl:

        //   ld hl, fm_fnumtbl

        c = a;
        b = 0;
        hl = c;
        //hl += (short)((b << 8) + c);
        //hl += (short)((b << 8) + c);

        //    ld a, (hl)
        work.seq_tmp_fnum = fm_fnumtbl[hl & 0xffff];
        //hl++;
        //  ld  a, (hl)
        //  ld(seq_tmp_fnum + 1), a

        a = af;

        a = (byte) ((a & 0xff) >>> 4);
        //boolean cry = false;
        //boolean cryB;
        //cryB = (a & 1) != 0; a = (byte)((a >> 1) | (cry ? 0x80 : 0x00)); cry = cryB;
        //cryB = (a & 1) != 0; a = (byte)((a >> 1) | (cry ? 0x80 : 0x00)); cry = cryB;
        //cryB = (a & 1) != 0; a = (byte)((a >> 1) | (cry ? 0x80 : 0x00)); cry = cryB;
        //cryB = (a & 1) != 0; a = (byte)((a >> 1) | (cry ? 0x80 : 0x00)); cry = cryB;
        //a &= 0xf;
        work.seq_tmp_oct = a;

        // Add detune effect
        hl = work.seq_tmp_fnum;

        a = work.ch[ix & 0xffff].detune;
        add_freq_offset();

        work.seq_tmp_fnum = hl;
    }

    /**
     * moon_write_fmop
     * Write an OPL3 reg for op
     * (opsel)
     * in   : seq_opsel, D = addr, E = data
     * dest : AF, DE
     */
    private void moon_write_fmop() {

        a = work.seq_opsel;
        if ((a & 0xff) >= 0x12) {
            a -= 0x12;
        }

//skip_sub_a:
        short hl2 = 0; // fm_op2reg_tbl; HL = HL + A
        hl2 += a;
//add_hl_fin:

        a = fm_op2reg_tbl[hl2 & 0xffff];
        a += d;
        d = a;


        a = work.seq_opsel;
        if (CP_CF((byte) 0x12)) {
            moon_fm1_out();
        } else {
            moon_fm2_out();
        }
//moon_write_fmop_1:
    }

    /**
     * moon_write_fmreg
     * moon_write_fmreg_ch
     * Write to OPL3 register
     * in : D = addr, E = data
     * dest : AF, DE
     */
    private void moon_write_fmreg() {
        a = work.seq_cur_ch;
        moon_write_fmreg_nch();
    }

    private void moon_write_fmreg_nch() {
        //; A = ch
//moon_write_fmreg_nch:

        byte e1, d1;
        e1 = a;
        a = work.seq_start_fm;
        d1 = a;
        a = e1;
        a -= d1; // a = ch, d = start_fm

        //jr moon_write_fm_ch
        //; Write fm(A = ch)
//moon_write_fm_ch:

        // first or second FM register
        if (CP_CF((byte) 9)) {
            // first register
//moon_write_fm1:
            a += d;
            d = a;
            moon_fm1_out();
            return;
        }

        // second register
        a -= 9;
        a += d;
        d = a;
        moon_fm2_out();
    }

    private void moon_wait() {
        //in	a, (MOON_STAT)
        //and $01
        //jr nz, moon_wait
        //ret
    }

    private void moon_fm1_out() {
        ChipDatum cd = new ChipDatum(0 * 0x100 + 0, d & 0xff, e & 0xff);
        WriteOPL4Register.accept(cd);
    }

    private void moon_fm2_out() {
        ChipDatum cd = new ChipDatum(0 * 0x100 + 1, d & 0xff, e & 0xff);
        WriteOPL4Register.accept(cd);
    }

    private void moon_wave_out() {
        ChipDatum cd = new ChipDatum(0 * 0x100 + 2, d & 0xff, e & 0xff);
        WriteOPL4Register.accept(cd);
        backDat = e;
    }

    private byte backDat = 0;

    private byte moon_wave_in() {
        //call moon_wait
        //ld a, d
        //out	(MOON_WREG),a
        //call    moon_wait
        //in	a, (MOON_WDAT)
        //ret
        a = backDat;
        return a;
    }

    private void moon_add_reg_ch() {
        a = work.seq_cur_ch;
        a += d;
        d = a;
    }

    /**
     * set frequency on the channel
     * in   : work
     * dest : AF,DE
     */
    private void moon_set_freq_ch() {

        a = work.ch[ix & 0xffff].dsel;
        if (a != 0) return;

//set_freq_ch_opl4:
        //; ocatve and f - number(hi)
        a = (byte) ((work.ch[ix & 0xffff].fnum & 0xffff) >>> 8);

        a = (byte) (((a & 0xff) << 1) + ((a & 0xff) >>> 7)); // rlca
        a &= 0x0e;
        e = a;
        a = (byte) (work.ch[ix].fnum & 0xff);
        a = (byte) (((a & 0xff) << 1) + ((a & 0xff) >>> 7)); // rlca
        a &= 0x01;
        a |= e;
        e = a;

        a = work.ch[ix & 0xffff].oct;
        a &= 0xf;
        a = (byte) (((a & 0xff) << 1) + ((a & 0xff) >>> 7));
        a = (byte) (((a & 0xff) << 1) + ((a & 0xff) >>> 7));
        a = (byte) (((a & 0xff) << 1) + ((a & 0xff) >>> 7));
        a = (byte) (((a & 0xff) << 1) + ((a & 0xff) >>> 7));
        a |= e;
        e = a;

        a = work.ch[ix & 0xffff].reverb;
        if (a != 0) {
            e |= 0x8;
        }

//moon_set_freq_ch_skip_reverb:
        d = 0x38;
        moon_add_reg_ch();
        moon_wave_out();

        // f - number(lo)
        a = (byte) (work.ch[ix & 0xffff].tone >> 8);
        a &= 1;
        e = a;

        a = (byte) (work.ch[ix & 0xffff].fnum & 0xff);
        a = (byte) (((a & 0xff) << 1) + ((a & 0xff) >>> 7));
        a &= 0xfe;
        a |= e;
        e = a;
        d = 0x20;

        moon_add_reg_ch();
        moon_wave_out();
    }

    private void moon_set_vol_ch() {
        a = work.ch[ix & 0xffff].dsel;
        if (a != 0) {
            moon_set_fmvol_ch();
            return;
        }
        a = work.ch[ix & 0xffff].vol;
        a ^= 0x7f;
        a = (byte) (((a & 0xff) << 1) + ((a & 0xff) >>> 7));
        a |= 1;
        e = a;
        d = 0x50;
        moon_add_reg_ch();
        moon_wave_out();
    }

    private void moon_set_fmvol_ch() {
        short ixb = ix;
        short hlb = hl;
        byte bb = b;
        byte cb = c;

        a = work.ch[ix & 0xffff].opsel;
        work.seq_opsel = a;

        b = work.ch[ix & 0xffff].vol;
        hl = (short) ((hl & 0xff) + (work.ch[ix].reverb & 0xff) * 0x100); // .volop;

        c = 0x02;
        a = work.ch[ix & 0xffff].synth;
        a &= 0x80;
        if (a != 0) {
            c = 0x04;
        }

//moon_set_fmvol_lp:
        int i = 0;
        do {
            byte h = (byte) (hl >> 8);
            boolean cry = false, cryb;
            cryb = (h & 1) != 0;
            h = (byte) (((h & 0xff) >>> 1) + (cry ? 0x80 : 0x00));
            hl = (short) (((h & 0xff) << 8) + (hl & 0xff));
            cry = cryb;
            if (cry) {
                moon_calc_current_fmvol(i);
                e = a;
                d = 0x40;
                moon_write_fmop();
            }
//skip_set_fmvol:
            i++; // ix++;

            a = work.seq_opsel;
            a += 3;
            work.seq_opsel = a;

            c--;
        } while (c != 0);

        b = bb;
        c = cb;
        hl = hlb;
        ix = ixb;
    }

    private void moon_calc_current_fmvol(int i) {
        // A = (OL + (63 - VOL))

        a = b;
        a &= 0x3f;
        a ^= 0x3f;
        e = a;
        a = work.ch[ix & 0xffff].ol[i]; // .ar_d1r;
        a &= 0x3f;
        a += e;

        if (!CP_CF((byte) 0x40)) {
//set_fmvol_min:
            e = 0x3f;
        } else {
            e = a;
        }

//set_fmvol_ks:
        a = work.ch[ix & 0xffff].ol[i]; // .ar_d1r;
        a &= (byte) 0xc0;
        a |= e;
    }

    /**
     * set ADSR regs
     * in   : work
     * dest : AF,DE
     */
    private void moon_set_adsr() {
        e = work.ch[ix & 0xffff].lfo_vib;
        d = (byte) 0x80;
        moon_add_reg_ch();
        moon_wave_out();

        e = work.ch[ix & 0xffff].ol[0]; // .ar_d1r;
        d = (byte) 0x98;
        moon_add_reg_ch();
        moon_wave_out();

        e = work.ch[ix & 0xffff].ol[1]; // .dl_d2r;
        d = (byte) 0xB0;
        moon_add_reg_ch();
        moon_wave_out();

        e = work.ch[ix & 0xffff].ol[2]; // .rc_rr;
        d = (byte) 0xc8;
        moon_add_reg_ch();
        moon_wave_out();

        e = work.ch[ix & 0xffff].ol[3]; // .am;
        d = (byte) 0xe0;
        moon_add_reg_ch();
        moon_wave_out();
    }

    /**
     * moon_key_data
     * make data for key-on/off
     * in   : work
     * out  : E = data for key
     * dest : almost all
     */
    private void moon_key_data() {
        a = work.ch[ix & 0xffff].pan;
        a &= 0xf;

        //; ; or  $10; PCM - MIX
        e = a;
        a = work.ch[ix & 0xffff].lfo;

        if (a == 0) {
            e |= 0x20; // LFO deactivate
        }

//moon_key_data_lfo_on:
        a = work.ch[ix & 0xffff].damp;

        if (a != 0) {
            e |= 0x40; // Damp on
        }
//moon_key_data_damp_off:
    }

    /**
     * moon_key_off
     * this function does : key-off
     * in   : work
     * dest : almost all
     */
    private void moon_key_off() {

        // key off
        a = work.ch[ix & 0xffff].dsel;
        if (a != 0) {
//moon_key_fmoff:
            a = work.ch[ix & 0xffff].key;
            a &= 0x20;
            if (a == 0) return;
            a = work.ch[ix & 0xffff].key;
            a &= (byte) 0xdf;
            e = a;
            d = (byte) 0xb0;
            work.ch[ix & 0xffff].key = e;
            // skip if jump flag is true
            a = work.seq_jump_flag;
            if (a != 0) return;
            moon_write_fmreg(); // key - off
            return;
        }

//moon_key_opl4off:
        a = work.ch[ix & 0xffff].key;
        a &= (byte) 0x80;
        if (a == 0) return;
        a = work.ch[ix & 0xffff].key;
        a &= 0x7f;
        e = a;
        d = 0x68;
        work.ch[ix & 0xffff].key = e;
        moon_add_reg_ch();
        moon_wave_out(); // key - off
        pcmKeyon[ix & 0xffff] = -1;
    }

    /**
     * moon_write_fmpan
     * calc and write Reg $Cx
     * dest AF, DE
     */
    private void moon_write_fmpan() {
        a = work.ch[ix & 0xffff].synth;
        d = a;
        a &= 0x1c; // 000FFF00
        a = (byte) (((a & 0xff) >> 1) + ((a & 1) != 0 ? 0x80 : 0)); //r rca
        e = a;
        a = d;
        a &= 0x01; // SynthType
        a |= e;
        a |= work.ch[ix & 0xffff].pan;

        // E -> $C0
        e = a;
        d = (byte) 0xc0;
        moon_write_fmreg();

        // 4OP
        a = work.ch[ix & 0xffff].synth;
        d = a;
        a &= (byte) 0x80;

        // skip if not 4op
        if (a != 0) {
            a = d;
            a = (byte) (((a & 0xff) >> 1) + ((a & 1) != 0 ? 0x80 : 0)); // rrca
            a &= 0x01; // 2nd SynthType

            a |= work.ch[ix & 0xffff].pan;
            e = a;
            d = (byte) 0xc0;

            // E -> $C0 + 3 + ch
            a = work.seq_cur_ch;
            a += 0x03;
            moon_write_fmreg_nch();
            return;
        }

//moon_write_fmpan_4op_fin:
    }

    /**
     * moon_key_on
     * Set tone number, frequency and key-on
     * in   : work
     * dest : almost all
     */
    private void moon_key_on() {
        a = work.ch[ix & 0xffff].dsel;
        if (a == 0) {
            moon_key_opl4on();
            return;
        }

//moon_key_fmon:
        if ((work.ch[ix & 0xffff].efx1 & 1) == 0) {
            moon_key_off();
            start_venv();
            start_penv();
            start_nenv();
        }

//slar_fm_on:
        work.ch[ix & 0xffff].efx1 &= (byte) 0xfe;
        moon_key_write_fmfreq();
        a |= 0x20; //  key on

        e = a;
        d = (byte) 0xb0;
        work.ch[ix & 0xffff].key = e;

        // skip if jump flag is true
        a = work.seq_jump_flag;
        if (a != 0) return;

        moon_write_fmreg(); // key-on
    }

    private void moon_key_opl4on() {
        if ((work.ch[ix & 0xffff].efx1 & 1) != 0) {
//slar_opl4_on:
            work.ch[ix].efx1 &= 0xfe;
            pcmKeyon[ix & 0xffff] = work.ch[ix].note + 12 * 2;
            moon_set_freq_ch();
            return;
        }

        moon_key_off();
        start_venv();
        start_penv();
        start_nenv();

        // tone number(hi)
        a = (byte) ((work.ch[ix & 0xffff].tone & 0xffff) >> 8);
        a &= 0x1;
        e = a;
        d = 0x20;

        moon_add_reg_ch();
        moon_wave_out();

        // tone number(lo)
        e = (byte) (work.ch[ix & 0xffff].tone & 0xff);
        d = 0x08;

        moon_add_reg_ch();
        moon_wave_out();

//moon_wavechg_lp:
        do {
            a = inport(MOON_STAT);
            a &= 0x02;
        } while (a != 0);

        pcmKeyon[ix & 0xffff] = work.ch[ix & 0xffff].note + 12 * 2;
        moon_set_freq_ch();
        moon_set_vol_ch();
        moon_set_adsr();

//moon_opl4_set_keyreg:
        // OPL4 key-on
        // skip if jump flag is true
        a = work.seq_jump_flag;
        if (a != 0) return;

        moon_key_data();

        a = e;
        a |= (byte) 0x80; // key-on
        e = a;
        d = 0x68;
        work.ch[ix & 0xffff].key = e;

        moon_add_reg_ch();
        moon_wave_out(); // key-on
    }

    /**
     * moon_key_write_fmfreq
     * calculates and writes related FM frequency
     * out : A = Reg.$B0(FnumH + BLK )
     * dest : almost all
     */
    private void moon_key_write_fmfreq() {
        a = work.seq_cur_ch;

        work.seq_tmp_ch = a;

        a = work.ch[ix & 0xffff].oct;
        work.seq_tmp_oct = a;

        hl = work.ch[ix & 0xffff].fnum;
        work.seq_tmp_fnum = hl;

        moon_key_write_fmfreq_base();
    }

    private void moon_key_write_fmfreq_base() {
        a = (byte) work.seq_tmp_fnum;
        e = a;
        d = (byte) 0xa0;
        a = work.seq_tmp_ch;
        moon_write_fmreg_nch();

        a = (byte) ((work.seq_tmp_fnum & 0xffff) >>> 8);
        a &= 0x03;
        e = a;

        a = work.seq_tmp_oct;

        a = (byte) (((a & 0xff) << 1) + ((a & 0x80) != 0 ? 1 : 0));
        a = (byte) (((a & 0xff) << 1) + ((a & 0x80) != 0 ? 1 : 0));

        a &= 0x1c; // mask for Octave
        a |= e; // F-Number
    }

    /**
     * Calculates and writes related regs with keeping key.
     */
    private void moon_key_fmfreq() {
        moon_key_write_fmfreq();

        e = a;
        a = work.ch[ix & 0xffff].key;
        a &= 0x20;
        a |= e;

        work.ch[ix & 0xffff].key = a;
        e = a;
        d = (byte) 0xb0;

        // skip if jump flag is true

        a = work.seq_jump_flag;

        if (a != 0) return;

        moon_write_fmreg(); // key-on
    }

    //
    // load pcm
    //

    private static final short MDB_LDFLAG = 0; // MDB_BASE;
    private static final short MDB_ADRHI = 1; // MDB_BASE + 1;
    private static final short MDB_ADRMI = 2; // MDB_BASE + 2;
    private static final short MDB_ADRLO = 3; // MDB_BASE + 3;
    private static final short MDB_RESULT = 4; // MDB_BASE + 4;
    private static final short MDB_ROM = 5; // MDB_BASE + 5;

    /** Resets R/W address pointer */
    private void moon_reset_sram_adrs() {

        a = (byte) readMemory(MDR_DSTPCM).dat;

        MDB_BASE[MDB_ADRHI] = a;
        a = 0;
        MDB_BASE[MDB_ADRMI] = a;
        MDB_BASE[MDB_ADRLO] = a;
        moon_set_sram_adrs();
    }

    /** Increments address pointer. */
    private void moon_inc_sram_adrs() {
        a = MDB_BASE[MDB_ADRLO];
        a++;
        MDB_BASE[MDB_ADRLO] = a;
        if (a != 0) return;

        a = MDB_BASE[MDB_ADRMI];
        a++;
        MDB_BASE[MDB_ADRMI] = a;
        if (a != 0) return;

        a = MDB_BASE[MDB_ADRHI];
        a++;
        MDB_BASE[MDB_ADRHI] = a;
    }

    /** Sets SRAM address. */
    private void moon_set_sram_adrs() {
        a = MDB_BASE[MDB_ADRHI];
        e = a;
        d = 0x03;
        moon_wave_out();

        a = MDB_BASE[MDB_ADRMI];
        e = a;
        d = 0x04;
        moon_wave_out();

        // the last should be lowest to set chip's internal pointer.
        // (trigger to set)

        a = MDB_BASE[MDB_ADRLO];
        e = a;
        d = 0x05;
        moon_wave_out();
    }

    /** Checks ROM. */
    private void moon_check_rom() {
        a = 0;

        MDB_BASE[MDB_ADRHI] = a;
        MDB_BASE[MDB_ADRLO] = a;
        a = 0x12;
        MDB_BASE[MDB_ADRMI] = a;

        // A <- (001200h)
        moon_set_sram_adrs();

        b = 0x08;

        //String str_romchk = "Copyright";
        hl = 0;

        // check loop
//moon_check_rom_lp:

        // skip
//        do {
//            a = (byte) str_romchk[hl & 0xffff];
//            e = a;
//
//            // A <- (SRAM)
//            d = 0x06;
//            moon_wave_in();
//
//            MDB_BASE[MDB_ROM] = a;
//            if ((a & 0xff) - (e & 0xff) != 0) return;
//            hl++;
//            b--;
//        } while ((b & 0xff) > 0); // djnz moon_check_rom_lp
        a = 0;
    }

    /** Checks SRAM. */
    private boolean moon_check_sram() {
        // skip
        return false;
//        // $77-> ($200000)
//        moon_reset_sram_adrs();
//
//        d = 0x06;
//        e = 0x77;
//        moon_wave_out();
//
//        // ok if $77 < -($200000)
//        moon_set_sram_adrs();
//
//        d = 0x06;
//        moon_wave_in();
//
//        if (!CP_ZF(0x77)) return true;
//
//        // $88-> ($200000)
//        moon_set_sram_adrs();
//
//        d = 0x06;
//        e = 0x88;
//        moon_wave_out();
//
//        // ok if $88 < -($200000)
//        moon_set_sram_adrs();
//
//        d = 0x06;
//        moon_wave_in();
//
//        if (!CP_ZF(0x88)) return true;
//
//        // $99-> ($200001)
//        a = 0x01;
//
//        MDB_BASE[MDB_ADRLO] = a;
//        moon_set_sram_adrs();
//
//        d = 0x06;
//        e = 0x99;
//        moon_wave_out();
//
//        // ok if $99 < -($200001)
//        moon_set_sram_adrs();
//
//        d = 0x06;
//        moon_wave_in();
//
//        if (!CP_ZF(0x99)) return true;
//
//        // ok if $88 < -($200000)
//        a = 0;
//
//        MDB_BASE[MDB_ADRLO] = a;
//        moon_set_sram_adrs();
//
//        d = 0x06;
//        moon_wave_in();
//
//        if (!CP_ZF(0x88)) return true;
//        return false;
    }

    /** Load User PCM */
    private void moon_load_pcm() {
        a = 0;
        change_page3();

        // is PCM packed song file?
        a = (byte) readMemory(MDR_PACKED).dat;
        // output status for debug
        MDB_BASE[MDB_LDFLAG] = a;

        if (a == 0) return;

        // initialize to enable OPL4 function
        moon_init();

        // memory write mode
        d = 0x02;
        e = 0x11;

        moon_wave_out();

        // check ROM
        moon_check_rom();

        if (a == 0) {
            check_sram_start();
            return;
        }

        // failed to check ROM

        a = 0x03;

        MDB_BASE[MDB_LDFLAG] = a;
        // address reset

        moon_reset_sram_adrs();
    }

    /** Checks sram. */
    private void check_sram_start() {
        boolean flg = moon_check_sram();
        if (!flg) {
            sram_found();
            return;
        }

        // result
        MDB_BASE[MDB_RESULT] = a;

        // SRAM is not found
        a = 0x02;
        MDB_BASE[MDB_LDFLAG] = a;
    }

    private void sram_found() {
        byte moon_pcm_bank_count = 0;

        byte moon_pcm_bank = 0;

        byte moon_pcm_numbanks = 0;

        byte moon_pcm_lastsize = 0;

        // reset SRAM address
        moon_reset_sram_adrs();

        // PCM number of banks
        a = (byte) readMemory(MDR_PCMBANKS).dat;

        moon_pcm_numbanks = a;
        moon_pcm_bank_count = a;

        // size of lastbank
        a = (byte) readMemory(MDR_LASTS).dat;
        moon_pcm_lastsize = a;

        // size of start page
        a = (byte) readMemory(MDR_STPCM).dat;
        moon_pcm_bank = a;

        // start of source address
        hl = (short) 0x8000;

        // address = $A000 if (start_bank & 1) != 0
        a &= 1;
        if (a != 0) {
            // bank1 = $A000
            hl = (short) (0xa000 + (hl & 0xff));
        }

        // RAM to PCM
//pcm_copy_bank:
        do {
            // bank size = $2000
            b = 0x20;
            c = 0x00;

            // Change to user pcm bank
            a = moon_pcm_bank;
            byte a1 = a;
            change_page3();
            a = a1;

            a++;
            moon_pcm_bank = a;

            // reset address if HL >= $C000
            a = (byte) ((hl & 0xff00) >> 8);
            if (!CP_CF((byte) 0xc0)) {
                // reset source address
                hl = (short) 0x8000;
            }

            // is the bank last?
//pcm_chk_last:

            a = moon_pcm_bank_count;
            if (a == 0) {
                // use lastsize if the bank is last one.
                a = moon_pcm_lastsize;
                b = a;
            }

//pcm_copy_lp:
            do {
                MmlDatum md = readMemory(hl);
                if (md == null) a = 0;
                else a = (byte) md.dat;
                e = a;
                d = 0x06;

                // A -> (PCM SRAM)
                moon_wave_out();

                hl++;
                short bc = (short) (((b & 0xff) * 0x100 + (c & 0xff)) - 1);
                b = (byte) ((bc & 0xff00) >> 8);
                c = (byte) (bc & 0xff);

                // loop if BC > 0
                a = b;
                a |= c;
            } while (a != 0);

            // end if count is 0
            a = moon_pcm_bank_count;
            if (a == 0) {
                break;
                //    jr z, pcm_copy_end
            }

            a--;
            moon_pcm_bank_count = a;

        } while (true);

        // end of PCM copy
//pcm_copy_end:

        // normal mode

        d = 0x02;
        e = 0x10;
        moon_wave_out();
    }

    private byte inport(short adr) {
        return 0;
    }

    private void outport(byte adr, byte data) {
        if (adr == (byte) 0xfe) {
            seg0x8000 = data;
        }
    }

    private final MmlDatum[] mem = new MmlDatum[1024 * 64];
    private final MmlDatum[][] extMem = new MmlDatum[256][];
    private Byte seg0x0000 = null;
    private Byte seg0x4000 = null;
    private Byte seg0x8000 = null;
    private Byte seg0xc000 = null;
    private byte a = 0;
    private byte b = 0;
    private byte c = 0;
    private byte d = 0;
    private byte e = 0;
    private short hl = 0;
    private short ix = 0;
    private short iy = 0;

    private interface dlgSeqFunc extends Runnable {

    }

    private dlgSeqFunc[] seq_jmptable;

    private MmlDatum readMemory(short adr) {
        switch ((adr & 0xffff) >>> 14) {
            case 0: // 0x0000 - 0x3fff
            default:
                if (seg0x0000 == null)
                    return mem[adr & 0xffff];
                if (extMem[seg0x0000 & 0xff] == null)
                    extMem[seg0x0000 & 0xff] = new MmlDatum[1024 * 16];
                return extMem[seg0x0000 & 0xff][adr & 0x3fff];
            case 1: // 0x4000 - 0x7fff
                if (seg0x4000 == null)
                    return mem[adr & 0xffff];
                if (extMem[seg0x4000 & 0xff] == null)
                    extMem[seg0x4000 & 0xff] = new MmlDatum[1024 * 16];
                return extMem[seg0x4000 & 0xff][adr & 0x3fff];
            case 2: // 0x8000 - 0xbfff
                if (seg0x8000 == null || seg0x8000 == 0)
                    return mem[adr & 0xffff];
                if (extMem[seg0x8000 & 0xff] == null)
                    extMem[seg0x8000 & 0xff] = new MmlDatum[1024 * 16];
                return extMem[seg0x8000 & 0xff][adr & 0x3fff];
            case 3: // 0xc000 - 0xffff
                if (seg0xc000 == null)
                    return mem[adr & 0xffff];
                if (extMem[seg0xc000 & 0xff] == null)
                    extMem[seg0xc000 & 0xff] = new MmlDatum[1024 * 16];
                return extMem[seg0xc000 & 0xff][adr & 0x3fff];
        }
    }

    private void writeMemory(short adr, MmlDatum dat) {
        switch ((adr & 0xffff) >>> 14) {
            case 0: // 0x0000 - 0x3fff
            default:
                if (seg0x0000 == null || seg0x0000 == 0) {
                    mem[adr & 0xffff] = dat;
                } else {
                    if (extMem[seg0x0000 & 0xff] == null)
                        extMem[seg0x0000 & 0xff] = new MmlDatum[1024 * 16];
                    extMem[seg0x0000 & 0xff][adr & 0x3fff] = dat;
                }
                break;
            case 1: // 0x4000 - 0x7fff
                if (seg0x4000 == null || seg0x4000 == 0) {
                    mem[adr & 0xffff] = dat;
                } else {
                    if (extMem[seg0x4000 & 0xff] == null)
                        extMem[seg0x4000 & 0xff] = new MmlDatum[1024 * 16];
                    extMem[seg0x4000 & 0xff][adr & 0x3fff] = dat;
                }
                break;
            case 2: // 0x8000 - 0xbfff
                if (seg0x8000 == null || seg0x8000 == 0) {
                    mem[adr & 0xffff] = dat;
                } else {
                    if (extMem[seg0x8000 & 0xff] == null)
                        extMem[seg0x8000 & 0xff] = new MmlDatum[1024 * 16];
                    extMem[seg0x8000 & 0xff][adr & 0x3fff] = dat;
                }
                break;
            case 3: // 0xc000 - 0xffff
                if (seg0xc000 == null || seg0xc000 == 0) {
                    mem[adr & 0xffff] = dat;
                } else {
                    if (extMem[seg0xc000 & 0xff] == null)
                        extMem[seg0xc000 & 0xff] = new MmlDatum[1024 * 16];
                    extMem[seg0xc000 & 0xff][adr & 0x3fff] = dat;
                }
                break;
        }
    }

    private boolean CP_CF(byte n) {
        int ans = (a & 0xff) - (n & 0xff);
        if (ans < 0) return true;
        return false;
    }

    private boolean CP_ZF(byte n) {
        int ans = (a & 0xff) - (n & 0xff);
        if (ans == 0) return true;
        return false;
    }
}
