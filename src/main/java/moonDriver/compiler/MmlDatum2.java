


package moonDriver.compiler;


import java.io.Serializable;
import java.util.List;

import dotnet4j.util.compat.StringUtilities;
import musicDriverInterface.LinePos;
import musicDriverInterface.MMLType;
import musicDriverInterface.MmlDatum;


public class MmlDatum2 extends MmlDatum implements Serializable {

    public String code;

    public MmlDatum2() {
        code = "";
    }

    public MmlDatum2(String code, int dat) {
        super(dat);
        this.code = code;
    }

    public MmlDatum2(String code, MMLType type, List<Object> args, LinePos linePos, int dat) {
        super(type, args, linePos, dat);
        this.code = code;
    }

    public MmlDatum2(String code, int dat, MMLType type, LinePos linePos, Object... args) {
        super(dat, type, linePos, args);
        this.code = code;
    }

    /**
     * @param code
     * @param args 0:none <br/>
     *             -1:data <br/>
     *             -2:label <br/>
     *             -3: [ref] label <br/>
     *             -4:macro <br/>
     *             -5:define <br/>
     *             -6:db [ref] define <br/>
     *             -7: [ref] macro <br/>
     */
    public MmlDatum2(String code, Object... args) {
        super(0xff, MMLType.Unknown, null, args);
        this.code = code;
    }

    @Override
    public String toString() {
        String c = StringUtilities.isNullOrEmpty(code) ? "" : code;
        String d = "";
        while (!c.isEmpty() && c.charAt(c.length() - 1) == '\n') {
            c = c.substring(0, c.length() - 1);
            d += "\n";
        }
        return "%d : %d%d".formatted(c, super.toString(), d);
    }

    public musicDriverInterface.MmlDatum ToMmlDatumn() {
        musicDriverInterface.MmlDatum md = new musicDriverInterface.MmlDatum();
        md.args = this.args;
        md.dat = this.dat;
        md.linePos = this.linePos;
        md.type = this.type;

        return md;
    }
}
