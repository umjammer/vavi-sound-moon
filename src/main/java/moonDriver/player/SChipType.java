





package moonDriver.player;

    public class SChipType
    {
        private boolean _UseEmu = true;
        public boolean UseEmu
        {
            get
            {
                return _UseEmu;
            }

            set
            {
                _UseEmu = value;
            }
        }

        private boolean _UseEmu2 = false;
        public boolean UseEmu2
        {
            get
            {
                return _UseEmu2;
            }

            set
            {
                _UseEmu2 = value;
            }
        }

        private boolean _UseEmu3 = false;
        public boolean UseEmu3
        {
            get
            {
                return _UseEmu3;
            }

            set
            {
                _UseEmu3 = value;
            }
        }


        private boolean _UseScci = false;
        public boolean UseScci
        {
            get
            {
                return _UseScci;
            }

            set
            {
                _UseScci = value;
            }
        }

        private String  _InterfaceName = "";
        public String  InterfaceName
        {
            get
            {
                return _InterfaceName;
            }

            set
            {
                _InterfaceName = value;
            }
        }

        private int _SoundLocation = -1;
        public int SoundLocation
        {
            get
            {
                return _SoundLocation;
            }

            set
            {
                _SoundLocation = value;
            }
        }

        private int _BusID = -1;
        public int BusID
        {
            get
            {
                return _BusID;
            }

            set
            {
                _BusID = value;
            }
        }

        private int _SoundChip = -1;
        public int SoundChip
        {
            get
            {
                return _SoundChip;
            }

            set
            {
                _SoundChip = value;
            }
        }

        private String  _ChipName = "";
        public String  ChipName
        {
            get
            {
                return _ChipName;
            }

            set
            {
                _ChipName = value;
            }
        }


        private boolean _UseScci2 = false;
        public boolean UseScci2
        {
            get
            {
                return _UseScci2;
            }

            set
            {
                _UseScci2 = value;
            }
        }

        private String  _InterfaceName2A = "";
        public String  InterfaceName2A
        {
            get
            {
                return _InterfaceName2A;
            }

            set
            {
                _InterfaceName2A = value;
            }
        }

        private int _SoundLocation2A = -1;
        public int SoundLocation2A
        {
            get
            {
                return _SoundLocation2A;
            }

            set
            {
                _SoundLocation2A = value;
            }
        }

        private int _BusID2A = -1;
        public int BusID2A
        {
            get
            {
                return _BusID2A;
            }

            set
            {
                _BusID2A = value;
            }
        }

        private int _SoundChip2A = -1;
        public int SoundChip2A
        {
            get
            {
                return _SoundChip2A;
            }

            set
            {
                _SoundChip2A = value;
            }
        }

        private String  _ChipName2A = "";
        public String  ChipName2A
        {
            get
            {
                return _ChipName2A;
            }

            set
            {
                _ChipName2A = value;
            }
        }

        private String  _InterfaceName2B = "";
        public String  InterfaceName2B
        {
            get
            {
                return _InterfaceName2B;
            }

            set
            {
                _InterfaceName2B = value;
            }
        }

        private int _SoundLocation2B = -1;
        public int SoundLocation2B
        {
            get
            {
                return _SoundLocation2B;
            }

            set
            {
                _SoundLocation2B = value;
            }
        }

        private int _BusID2B = -1;
        public int BusID2B
        {
            get
            {
                return _BusID2B;
            }

            set
            {
                _BusID2B = value;
            }
        }

        private int _SoundChip2B = -1;
        public int SoundChip2B
        {
            get
            {
                return _SoundChip2B;
            }

            set
            {
                _SoundChip2B = value;
            }
        }

        private String  _ChipName2B = "";
        public String  ChipName2B
        {
            get
            {
                return _ChipName2B;
            }

            set
            {
                _ChipName2B = value;
            }
        }


        private boolean _UseWait = true;
        public boolean UseWait
        {
            get
            {
                return _UseWait;
            }

            set
            {
                _UseWait = value;
            }
        }

        private boolean _UseWaitBoost = false;
        public boolean UseWaitBoost
        {
            get
            {
                return _UseWaitBoost;
            }

            set
            {
                _UseWaitBoost = value;
            }
        }

        private boolean _OnlyPCMEmulation = false;
        public boolean OnlyPCMEmulation
        {
            get
            {
                return _OnlyPCMEmulation;
            }

            set
            {
                _OnlyPCMEmulation = value;
            }
        }

        private int _LatencyForEmulation = 0;
        public int LatencyForEmulation
        {
            get
            {
                return _LatencyForEmulation;
            }

            set
            {
                _LatencyForEmulation = value;
            }
        }

        private int _LatencyForScci = 0;
        public int LatencyForScci
        {
            get
            {
                return _LatencyForScci;
            }

            set
            {
                _LatencyForScci = value;
            }
        }


        public SChipType Copy()
        {
            SChipType ct = new SChipType();
            ct.UseEmu = this.UseEmu;
            ct.UseEmu2 = this.UseEmu2;
            ct.UseEmu3 = this.UseEmu3;
            ct.UseScci = this.UseScci;
            ct.SoundLocation = this.SoundLocation;

            ct.BusID = this.BusID;
            ct.InterfaceName = this.InterfaceName;
            ct.SoundChip = this.SoundChip;
            ct.ChipName = this.ChipName;
            ct.UseScci2 = this.UseScci2;
            ct.SoundLocation2A = this.SoundLocation2A;

            ct.InterfaceName2A = this.InterfaceName2A;
            ct.BusID2A = this.BusID2A;
            ct.SoundChip2A = this.SoundChip2A;
            ct.ChipName2A = this.ChipName2A;
            ct.SoundLocation2B = this.SoundLocation2B;

            ct.InterfaceName2B = this.InterfaceName2B;
            ct.BusID2B = this.BusID2B;
            ct.SoundChip2B = this.SoundChip2B;
            ct.ChipName2B = this.ChipName2B;

            ct.UseWait = this.UseWait;
            ct.UseWaitBoost = this.UseWaitBoost;
            ct.OnlyPCMEmulation = this.OnlyPCMEmulation;
            ct.LatencyForEmulation = this.LatencyForEmulation;
            ct.LatencyForScci = this.LatencyForScci;

            return ct;
        }
    }
}
