package moonDriver.player;

public class SChipType {

    public boolean getUseEmu() {
        return _UseEmu;
    }

    public void setUseEmu(boolean value) {
        _UseEmu = value;
    }

    public boolean isUseEmu2() {
        return _UseEmu2;
    }

    public void setUseEmu2(boolean _UseEmu2) {
        this._UseEmu2 = _UseEmu2;
    }

    public boolean isUseEmu3() {
        return _UseEmu3;
    }

    public void setUseEmu3(boolean _UseEmu3) {
        this._UseEmu3 = _UseEmu3;
    }

    public boolean isUseScci() {
        return _UseScci;
    }

    public void setUseScci(boolean _UseScci) {
        this._UseScci = _UseScci;
    }

    public String getInterfaceName() {
        return _InterfaceName;
    }

    public void setInterfaceName(String _InterfaceName) {
        this._InterfaceName = _InterfaceName;
    }

    public int getSoundLocation() {
        return _SoundLocation;
    }

    public void setSoundLocation(int _SoundLocation) {
        this._SoundLocation = _SoundLocation;
    }

    public int getBusID() {
        return _BusID;
    }

    public void setBusID(int _BusID) {
        this._BusID = _BusID;
    }

    public int getSoundChip() {
        return _SoundChip;
    }

    public void setSoundChip(int _SoundChip) {
        this._SoundChip = _SoundChip;
    }

    public String getChipName() {
        return _ChipName;
    }

    public void setChipName(String _ChipName) {
        this._ChipName = _ChipName;
    }

    public boolean isUseScci2() {
        return _UseScci2;
    }

    public void setUseScci2(boolean _UseScci2) {
        this._UseScci2 = _UseScci2;
    }

    public String getInterfaceName2A() {
        return _InterfaceName2A;
    }

    public void setInterfaceName2A(String _InterfaceName2A) {
        this._InterfaceName2A = _InterfaceName2A;
    }

    public int getSoundLocation2A() {
        return _SoundLocation2A;
    }

    public void setSoundLocation2A(int _SoundLocation2A) {
        this._SoundLocation2A = _SoundLocation2A;
    }

    public int getBusID2A() {
        return _BusID2A;
    }

    public void setBusID2A(int _BusID2A) {
        this._BusID2A = _BusID2A;
    }

    public int getSoundChip2A() {
        return _SoundChip2A;
    }

    public void setSoundChip2A(int _SoundChip2A) {
        this._SoundChip2A = _SoundChip2A;
    }

    public String getChipName2A() {
        return _ChipName2A;
    }

    public void setChipName2A(String _ChipName2A) {
        this._ChipName2A = _ChipName2A;
    }

    public String getInterfaceName2B() {
        return _InterfaceName2B;
    }

    public void setInterfaceName2B(String _InterfaceName2B) {
        this._InterfaceName2B = _InterfaceName2B;
    }

    public int getSoundLocation2B() {
        return _SoundLocation2B;
    }

    public void setSoundLocation2B(int _SoundLocation2B) {
        this._SoundLocation2B = _SoundLocation2B;
    }

    public int getBusID2B() {
        return _BusID2B;
    }

    public void setBusID2B(int _BusID2B) {
        this._BusID2B = _BusID2B;
    }

    public int getSoundChip2B() {
        return _SoundChip2B;
    }

    public void setSoundChip2B(int _SoundChip2B) {
        this._SoundChip2B = _SoundChip2B;
    }

    public String getChipName2B() {
        return _ChipName2B;
    }

    public void setChipName2B(String _ChipName2B) {
        this._ChipName2B = _ChipName2B;
    }

    public boolean isUseWait() {
        return _UseWait;
    }

    public void setUseWait(boolean _UseWait) {
        this._UseWait = _UseWait;
    }

    public boolean isUseWaitBoost() {
        return _UseWaitBoost;
    }

    public void setUseWaitBoost(boolean _UseWaitBoost) {
        this._UseWaitBoost = _UseWaitBoost;
    }

    public boolean isOnlyPCMEmulation() {
        return _OnlyPCMEmulation;
    }

    public void setOnlyPCMEmulation(boolean _OnlyPCMEmulation) {
        this._OnlyPCMEmulation = _OnlyPCMEmulation;
    }

    public int getLatencyForEmulation() {
        return _LatencyForEmulation;
    }

    public void setLatencyForEmulation(int _LatencyForEmulation) {
        this._LatencyForEmulation = _LatencyForEmulation;
    }

    public int getLatencyForScci() {
        return _LatencyForScci;
    }

    public void setLatencyForScci(int _LatencyForScci) {
        this._LatencyForScci = _LatencyForScci;
    }

    private boolean _UseEmu = true;

    private boolean _UseEmu2 = false;

    private boolean _UseEmu3 = false;

    private boolean _UseScci = false;

    private String _InterfaceName = "";

    private int _SoundLocation = -1;

    private int _BusID = -1;

    private int _SoundChip = -1;

    private String _ChipName = "";

    private boolean _UseScci2 = false;

    private String _InterfaceName2A = "";

    private int _SoundLocation2A = -1;

    private int _BusID2A = -1;

    private int _SoundChip2A = -1;

    private String _ChipName2A = "";

    private String _InterfaceName2B = "";

    private int _SoundLocation2B = -1;

    private int _BusID2B = -1;

    private int _SoundChip2B = -1;

    private String _ChipName2B = "";

    private boolean _UseWait = true;

    private boolean _UseWaitBoost = false;

    private boolean _OnlyPCMEmulation = false;

    private int _LatencyForEmulation = 0;

    private int _LatencyForScci = 0;

    public SChipType Copy() {
        SChipType ct = new SChipType();
        ct._UseEmu = this._UseEmu;
        ct._UseEmu2 = this._UseEmu2;
        ct._UseEmu3 = this._UseEmu3;
        ct._UseScci = this._UseScci;
        ct._SoundLocation = this._SoundLocation;

        ct._BusID = this._BusID;
        ct._InterfaceName = this._InterfaceName;
        ct._SoundChip = this._SoundChip;
        ct._ChipName = this._ChipName;
        ct._UseScci2 = this._UseScci2;
        ct._SoundLocation2A = this._SoundLocation2A;

        ct._InterfaceName2A = this._InterfaceName2A;
        ct._BusID2A = this._BusID2A;
        ct._SoundChip2A = this._SoundChip2A;
        ct._ChipName2A = this._ChipName2A;
        ct._SoundLocation2B = this._SoundLocation2B;

        ct._InterfaceName2B = this._InterfaceName2B;
        ct._BusID2B = this._BusID2B;
        ct._SoundChip2B = this._SoundChip2B;
        ct._ChipName2B = this._ChipName2B;

        ct._UseWait = this._UseWait;
        ct._UseWaitBoost = this._UseWaitBoost;
        ct._OnlyPCMEmulation = this._OnlyPCMEmulation;
        ct._LatencyForEmulation = this._LatencyForEmulation;
        ct._LatencyForScci = this._LatencyForScci;

        return ct;
    }
}
