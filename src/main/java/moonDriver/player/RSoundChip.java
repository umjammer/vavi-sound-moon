package moonDriver.player;

public class RSoundChip {

    protected int SoundLocation;
    protected int BusID;
    protected int SoundChip;

    public int dClock = 3579545;

    public RSoundChip(int soundLocation, int busID, int soundChip) {
        SoundLocation = soundLocation;
        BusID = busID;
        SoundChip = soundChip;
    }

    public void init() {
        throw new UnsupportedOperationException();
    }

    public void setRegister(int adr, int dat) {
        throw new UnsupportedOperationException();
    }

    public int getRegister(int adr) {
        throw new UnsupportedOperationException();
    }

    public boolean isBufferEmpty() {
        throw new UnsupportedOperationException();
    }

    public int SetMasterClock(int mClock) {
        throw new UnsupportedOperationException();
    }

    public void setSSGVolume(byte vol) {
        throw new UnsupportedOperationException();
    }

    public static class RScciSoundChip extends RSoundChip {

//        public NScci.NScci scci = null;
//        private NSoundChip realChip = null;

        public RScciSoundChip(int soundLocation, int busID, int soundChip) {
            super(soundLocation, busID, soundChip);
        }

        @Override
        public void init() {
//            NSoundInterface nsif = Scci.NSoundInterfaceManager().getInterface(BusID);
//            NSoundChip nsc = nsif.getSoundChip(SoundChip);
//            realChip = nsc;
//            dClock = (int) nsc.getSoundChipClock();

            // If you want to send initialization commands for each chip type
//            switch (nsc.getSoundChipType()) {
//                case (int) EnmRealChipType.YM2608:
//                    //setRegister(0x2d, 00);
//                    //setRegister(0x29, 82);
//                    //setRegister(0x07, 38);
//                    break;
//            }
        }

        @Override
        public void setRegister(int adr, int dat) {
//            realChip.setRegister(adr, dat);
        }

        @Override
        public int getRegister(int adr) {
//            return realChip.getRegister(adr);
            return 0;
        }

        @Override
        public boolean isBufferEmpty() {
//            return realChip.isBufferEmpty();
            return false;
        }

        /**
         * Master Clock Settings
         *
         * @param mClock The value you want to set
         * @return The actual value set
         */
        @Override
        public int SetMasterClock(int mClock) {
            // SCCI cannot change the clock

//            return (int) realChip.getSoundChipClock();
            return 0;
        }

        @Override
        public void setSSGVolume(byte vol) {
            // SCCI cannot change SSG volume
        }
    }

    public static class RC86ctlSoundChip extends RSoundChip {

//        public Nc86ctl.Nc86ctl c86ctl = null;
//        public Nc86ctl.NIRealChip realChip = null;
//        public Nc86ctl.ChipType chiptype = ChipType.CHIP_UNKNOWN;

        public RC86ctlSoundChip(int soundLocation, int busID, int soundChip) {
            super(soundLocation, busID, soundChip);
        }

        @Override
        public void init() {
//            NIRealChip rc = c86ctl.getChipInterface(BusID);
//            rc.reset();
//            realChip = rc;
//            NIGimic2 gm = rc.QueryInterface();
//            dClock = gm.getPLLClock();
//            chiptype = gm.getModuleType();
//            if (chiptype == ChipType.CHIP_YM2608) {
//                //setRegister(0x2d, 00);
//                //setRegister(0x29, 82);
//                //setRegister(0x07, 38);
//            }
        }

        @Override
        public void setRegister(int adr, int dat) {
//            realChip/*.@out*/((short) adr, (byte) dat);
        }

        @Override
        public int getRegister(int adr) {
//            return realChip/*.@in*/((short) adr);
            return 0;
        }

        @Override
        public boolean isBufferEmpty() {
            return true;
        }

        /**
         * Master Clock Settings
         *
         * @param mClock The value you want to set
         * @return The actual value set
         */
        @Override
        public int SetMasterClock(int mClock) {
//            NIGimic2 gm = realChip.QueryInterface();
//            int nowClock = gm.getPLLClock();
//            if (nowClock != mClock) {
//                gm.setPLLClock(mClock);
//            }

//            return gm.getPLLClock();
            return 0;
        }

        @Override
        public void setSSGVolume(byte vol) {
//            NIGimic2 gm = realChip.QueryInterface();
//            gm.setSSGVolume(vol);
        }
    }

    enum EnmRealChipType {
        YM2608(1),
        YM2151(2),
        YM2610(3),
        YM2203(4),
        YM2612(5),
        SN76489(7),
        SPPCM(42),
        C140(43),
        SEGAPCM(44);
        final int v;

        EnmRealChipType(int v) {
            this.v = v;
        }
    }
}