package moonDriver.compiler;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import dotnet4j.util.compat.Tuple3;

import static java.lang.System.getLogger;
import static moonDriver.common.Common.charset;


public class Assemble {

    private static final Logger logger = getLogger(Assemble.class.getName());
    private List<MmlDatum2> asFp;
    private List<MmlDatum2> efFp;
    private List<MmlDatum2> ouFp;
    private List<MmlDatum2> inFp;

    private List<MmlDatum2> asm;
    private Map<String, List<MmlDatum2>> dicMacroBlock;
    private Map<String, MmlDatum2> dicDefine;
    private Map<String, List<Tuple3<Integer, Integer, Object>>> dicRefLabel = new HashMap<>();
    private Map<String, MmlDatum2> dicLabel;

    private List<List<MmlDatum2>> dest = new ArrayList<>();
    private int currentBank = 0;
    private int currentAddress = 0;
    private Stack<Boolean> assembleBlockStack = new Stack<>();
    private boolean assembleBlockLatest = false;

    public List<List<MmlDatum2>> build(Work wk, List<MmlDatum2> efFp, List<MmlDatum2> ouFp, List<MmlDatum2> inFp) {
        // I'll put it aside for now
        assembleBlockStack.push(false);
        UpdateAssembleBlockLatest();

        //
        GetAsmList();
        this.efFp = efFp;
        this.ouFp = ouFp;
        this.inFp = inFp;

        // Referencing includes and combining each list into one
        Step1_Append();
        // Collecting blocks of macros
        Step2_GetMacro();
        // Replace a block of macros
        Step3_ReplaceMacro();
        // Collect constants
        Step4_GetDefine();
        // Collect labels
        Step5_GetLabel();
        // Assemble
        assemble();
        // Label reference expansion
        SetLabel();

        return dest;
    }

    private void UpdateAssembleBlockLatest() {
        assembleBlockLatest = assembleBlockStack.contains(true);
    }

    private void GetAsmList() {
        String t;

        asFp = new ArrayList<>();

        t = ".include \"define.inc\"";
        asFp.add(new MmlDatum2(t, -4, t));

        t = "DATA_BANK equ 0";
        asFp.add(new MmlDatum2(t, -5, t));

        t = ".bank 0";
        asFp.add(new MmlDatum2(t, -4, ".bank", 0));
        t = ".org  $8000";
        asFp.add(new MmlDatum2(t, -4, t));
        t = ".code";
        asFp.add(new MmlDatum2(t, -4, t));

        asFp.add(new MmlDatum2("ds $80"
                , -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0
                , -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0
                , -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0
                , -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0
                , -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0
                , -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0
                , -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0
                , -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0
        ));

        t = ".org  $8000";
        asFp.add(new MmlDatum2(t, -4, t));

        t = "db \"MDRV\"";
        asFp.add(new MmlDatum2(t, -1, 'M', -1, 'D', -1, 'R', -1, 'V'));
        t = "dw $0004 ; version";
        asFp.add(new MmlDatum2(t, -1, 4, -1, 0));
        t = "db $00   ; num of used channels ( 0 = auto )";
        asFp.add(new MmlDatum2(t, -1, 0));

        t = "db SOUND_GENERATOR  ; device flags";
        asFp.add(new MmlDatum2(t, -6, "b:SOUND_GENERATOR"));

        t = "dw $0000 ; adr title String  ( terminated with zero )";
        asFp.add(new MmlDatum2(t, -1, 0, -1, 0));
        t = "dw $0000 ; adr artist string(terminated with zero)";
        asFp.add(new MmlDatum2(t, -1, 0, -1, 0));
        t = "dw $0000 ; adr comment string(terminated with zero)";
        asFp.add(new MmlDatum2(t, -1, 0, -1, 0));
        t = "db SOUND_USERPCM ; User PCM flag";
        asFp.add(new MmlDatum2(t, -6, "b:SOUND_USERPCM"));
        t = "db $00 ; reserved";
        asFp.add(new MmlDatum2(t, -1, 0));

        t = "dw sound_data_table; adr track table";
        asFp.add(new MmlDatum2(t, -3, "sound_data_table"));
        t = "dw sound_data_bank; adr track bank table";
        asFp.add(new MmlDatum2(t, -3, "sound_data_bank"));


        t = "dw loop_point_table; adr loop table";
        asFp.add(new MmlDatum2(t, -3, "loop_point_table"));
        t = "dw loop_point_bank; adr loop bank table";
        asFp.add(new MmlDatum2(t, -3, "loop_point_bank"));


        t = "dw softenve_table; adr venv table";
        asFp.add(new MmlDatum2(t, -3, "softenve_table"));
        t = "dw softenve_lp_table; adr venv lp table";
        asFp.add(new MmlDatum2(t, -3, "softenve_lp_table"));


        t = "dw pitchenve_table; adr penv table";
        asFp.add(new MmlDatum2(t, -3, "pitchenve_table"));
        t = "dw pitchenve_lp_table; adr penv lp table";
        asFp.add(new MmlDatum2(t, -3, "pitchenve_lp_table"));


        t = "dw arpeggio_table; adr nenv table";
        asFp.add(new MmlDatum2(t, -3, "arpeggio_table"));
        t = "dw arpeggio_lp_table; adr nenv lp table";
        asFp.add(new MmlDatum2(t, -3, "arpeggio_lp_table"));


        t = "dw $0000; adr lfo  table";
        asFp.add(new MmlDatum2(t, -1, 0, -1, 0));
        t = "dw ttbl_data_table; adr inst table";
        asFp.add(new MmlDatum2(t, -3, "ttbl_data_table"));

        t = "dw opl3tbl_data_table; adr opl3 table";
        asFp.add(new MmlDatum2(t, -3, "opl3tbl_data_table"));

        t = "pcm_flags:";
        asFp.add(new MmlDatum2(t, -2, t));
        t = "db    $00";
        asFp.add(new MmlDatum2(t, -1, 0));
        t = "db    $00";
        asFp.add(new MmlDatum2(t, -1, 0));

        t = ".if (SOUND_USERPCM = 1)";
        asFp.add(new MmlDatum2(t, -4, t));
        t = "dw userpcm_string";
        asFp.add(new MmlDatum2(t, -3, "userpcm_string"));
        t = ".else";
        asFp.add(new MmlDatum2(t, -4, t));
        t = "dw  $0000";
        asFp.add(new MmlDatum2(t, -1, 0, -1, 0));
        t = ".endif";
        asFp.add(new MmlDatum2(t, -4, t));


        t = "dw tag_string";
        asFp.add(new MmlDatum2(t, -3, "tag_string"));

        t = "db $00; start address of OPL4 SRAM(x * 0x10000)";
        asFp.add(new MmlDatum2(t, -1, 0));
        t = "db $00; start bank of PCM";
        asFp.add(new MmlDatum2(t, -1, 0));
        t = "db $00; size of PCM banks";
        asFp.add(new MmlDatum2(t, -1, 0));
        t = "db $00; size of last bank(x* 0x100)";
        asFp.add(new MmlDatum2(t, -1, 0));

        t = "db $00; large count of PCM banks";
        asFp.add(new MmlDatum2(t, -1, 0));

        t = ".org $8040";
        asFp.add(new MmlDatum2(t, -4, t));

        t = ".if (SOUND_USERPCM = 1)";
        asFp.add(new MmlDatum2(t, -4, t));
        t = "userpcm_string:";
        asFp.add(new MmlDatum2(t, -2, t));
        t = "PCMFILE";
        asFp.add(new MmlDatum2(t, -6, t));
        t = ".endif";
        asFp.add(new MmlDatum2(t, -4, t));

        t = ".org $8080";
        asFp.add(new MmlDatum2(t, -4, t));

        t = "tag_string:";
        asFp.add(new MmlDatum2(t, -2, t));
        t = "TITLE_TEXT ; Track name(en)";
        asFp.add(new MmlDatum2(t, -6, "TITLE_TEXT"));
        t = "db $00 ; Track name(jp)";
        asFp.add(new MmlDatum2(t, -1, 0));
        t = "MAKER_TEXT ; Game name(en)";
        asFp.add(new MmlDatum2(t, -6, "MAKER_TEXT"));
        t = "db $00 ; Game name(jp)";
        asFp.add(new MmlDatum2(t, -1, 0));
        t = "db $00 ; System name(en)";
        asFp.add(new MmlDatum2(t, -1, 0));
        t = "db $00 ; System name(jp)";
        asFp.add(new MmlDatum2(t, -1, 0));
        t = "COMPOSER_TEXT; Track author(en)";
        asFp.add(new MmlDatum2(t, -6, "COMPOSER_TEXT"));
        t = "db $00; Track author(jp)";
        asFp.add(new MmlDatum2(t, -1, 0));
        t = "db $00; Release date";
        asFp.add(new MmlDatum2(t, -1, 0));
        t = "db $00; Programmer";
        asFp.add(new MmlDatum2(t, -1, 0));
        t = "db $00; Notes";
        asFp.add(new MmlDatum2(t, -1, 0));

        t = ".include \"effect.h\"";
        asFp.add(new MmlDatum2(t, -4, t));
    }

    private void Step1_Append() {
        asm = new ArrayList<>();
        Step1_start(asFp);
    }

    private void Step1_start(List<MmlDatum2> crnt) {
        for (MmlDatum2 md : crnt) {
            if (md == null || md.args == null || md.args.size() < 2 || !(md.args.get(0) instanceof Integer) || (int) md.args.get(0) != -4) {
                asm.add(md);
                continue;
            }

            if (!(md.args.get(1) instanceof String)) {
                asm.add(md);
                continue;
            }

            String wd = ((String) md.args.get(1)).trim().toLowerCase();
            if (wd.indexOf(".include") != 0) {
                asm.add(md);
                continue;
            }

            wd = wd.substring(".include".length()).toLowerCase().trim();

            if (wd.equals("\"define.inc\"")) {
                Step1_start(inFp);
            } else if (wd.equals("\"effect.h\"")) {
                Step1_start(efFp);
            } else {
                Step1_start(ouFp);
            }
        }
    }

    private void Step2_GetMacro() {
        dicMacroBlock = new HashMap<>();

        for (int i = 0; i < asm.size(); i++) {
            MmlDatum2 md = asm.get(i);

            if (md == null || md.args == null || md.args.size() < 2 || !(md.args.get(0) instanceof Integer) || (int) md.args.get(0) != -4) {
                continue;
            }

            if (!(md.args.get(1) instanceof String)) {
                continue;
            }

            String wd = ((String) md.args.get(1)).trim().toLowerCase();
            if (!wd.contains(".macro")) {
                continue;
            }

            String macroLabel = wd.substring(0, wd.indexOf('.')).trim();
            asm.remove(i);
            List<MmlDatum2> mb = new ArrayList<>();

            while (i < asm.size()) {
                md = asm.get(i);
                if (md == null || md.args == null || md.args.size() < 2 || !(md.args.get(0) instanceof Integer) || (int) md.args.get(0) != -4) {
                    mb.add(md);
                    asm.remove(i);
                    continue;
                }

                if (!(md.args.get(1) instanceof String)) {
                    mb.add(md);
                    asm.remove(i);
                    continue;
                }

                wd = ((String) md.args.get(1)).trim().toLowerCase();
                if (!wd.contains(".endm")) {
                    mb.add(md);
                    asm.remove(i);
                    continue;
                }

                asm.remove(i);
                dicMacroBlock.put(macroLabel, mb);
                i--;
                break;
            }

        }
    }


    private void Step3_ReplaceMacro() {
        boolean f;

        do {
            f = false;
            for (int i = 0; i < asm.size(); i++) {
                MmlDatum2 md = asm.get(i);

                if (md == null || md.args == null || md.args.size() < 2 || !(md.args.get(0) instanceof Integer) || (int) md.args.get(0) != -6) {
                    continue;
                }

                if (!(md.args.get(1) instanceof String)) {
                    continue;
                }

                String wd = ((String) md.args.get(1)).trim().toLowerCase();
                for (String key : dicMacroBlock.keySet()) {
                    if (wd != key) continue;

                    f = true;
                    asm.remove(i);
                    for (MmlDatum2 val : dicMacroBlock.get(key)) {
                        asm.add(i++, val);
                    }
                }
            }
        } while (f);

    }

    private void Step4_GetDefine() {
        dicDefine = new HashMap<>();

        for (int i = 0; i < asm.size(); i++) {
            MmlDatum2 md = asm.get(i);

            if (md == null || md.args == null || md.args.size() < 2 || !(md.args.get(0) instanceof Integer) || (int) md.args.get(0) != -5) {
                continue;
            }

            if (!(md.args.get(1) instanceof String)) {
                continue;
            }

            String[] wd = ((String) md.args.get(1)).trim().toLowerCase().replace("\t", " ").split(" ");
            if (!wd[1].equals("equ")) {
                continue;
            }

            String defineLabel = wd[0];
            md = new MmlDatum2(wd[2], -5, "");
            dicDefine.put(defineLabel, md);

        }
    }

    private void Step5_GetLabel() {
        dicLabel = new HashMap<>();

        for (int i = 0; i < asm.size(); i++) {
            MmlDatum2 md = asm.get(i);

            if (md == null || md.args == null || md.args.size() < 2 || !(md.args.get(0) instanceof Integer) || (int) md.args.get(0) != -2) {
                continue;
            }

            if (!(md.args.get(1) instanceof String)) {
                continue;
            }

            md.dat = i; // Try adding the number of lines
            dicLabel.put((String) md.args.get(1), md);

        }
    }

    private void assemble() {
        for (int i = 0; i < asm.size(); i++) {

            if (asm == null || asm.get(i).args == null || asm.get(i).args.size() < 2) continue;
            List<Object> args = asm.get(i).args;

            String code = asm.get(i).code;
            while (code.indexOf("\n") == code.length() - 1) code = code.substring(0, code.length() - 1);
            while (code.indexOf("\n") == 0) code = code.substring(1);
            logger.log(Level.TRACE, code);

            int[] ptr = new int[1];
            while (ptr[0] < asm.get(i).args.size()) {
                if (!(args.get(ptr[0]) instanceof Integer)) {
                    ptr[0]++;
                    continue;
                }

                int tp = (int) args.get(ptr[0]);
                switch (tp) {
                    case -1: // db
                        asmDb(asm.get(i), /* ref */ ptr);
                        break;
                    case -2: // label
                        asmLabel(asm.get(i), /* ref */ ptr);
                        break;
                    case -3: // db /* ref */ label
                        asmDbRefLabel(asm.get(i), /* ref */ ptr);
                        break;
                    case -4: // macro
                        asmMacro(asm.get(i), /* ref */ ptr);
                        break;
                    case -5: // define
                        ptr[0] = asm.get(i).args.size();
                        break;
                    case -6: // db /* ref */ define
                        asmDbRefDefine(asm.get(i), /* ref */ ptr);
                        break;
                    default:
                        logger.log(Level.ERROR, String.format("Unknown type[%d] error. ", tp));
                        ptr[0]++;
                        break;
                }
            }
        }
    }

    private void asmDb(MmlDatum2 asm, /* ref */ int[] ptr) {
        if (assembleBlockLatest) {
            ptr[0] += 2;
            return;
        }

        List<Object> args = asm.args;
        if (args.get(ptr[0] + 1) instanceof Byte) {
            byte n = (byte) args.get(ptr[0] + 1);
            ptr[0] += 2;
            Poke(currentBank, currentAddress++, n, asm);
        } else if (args.get(ptr[0] + 1) instanceof Integer) {
            byte n = (byte) (int) args.get(ptr[0] + 1);
            ptr[0] += 2;
            Poke(currentBank, currentAddress++, n, asm); // Even if it is an int, it is treated as a byte.
        } else if (args.get(ptr[0] + 1) instanceof Character) {
            byte n = (byte) (char) args.get(ptr[0] + 1);
            ptr[0] += 2;
            Poke(currentBank, currentAddress++, n, asm);
        } else if (args.get(ptr[0] + 1) instanceof String) {
            // Composite type
            String sen = (String) args.get(ptr[0] + 1);
            List<Byte> wd = new ArrayList<>();
            for (int i = 0; i < sen.length(); i++) {
                if (sen.charAt(i) == ' ' || sen.charAt(i) == '\t') continue;
                if (sen.charAt(i) == ',') {
                    continue;
                }

                int j;
                String x = "";

                if (sen.charAt(i) == '"') {
                    x = "";
                    j = i + 1;
                    for (; j < sen.length(); j++) {
                        if (sen.charAt(j) == '"') break;
                        x += sen.charAt(j);
                    }
                    i = j;

                    byte[] ary = x.getBytes(charset);
                    for (byte b : ary) wd.add(b);
                    continue;
                }

                x = "";
                j = i;
                for (; j < sen.length(); j++) {
                    if (sen.charAt(i) == ' ' || sen.charAt(i) == '\t' || sen.charAt(i) == ',') break;
                    x += sen.charAt(j);
                }
                i = j;
                int n = GetInt(x);
                wd.add((byte) n);
            }

            ptr[0] += 2;
            for (byte b : wd) Poke(currentBank, currentAddress++, b, asm);

        } else {
            logger.log(Level.ERROR, "Db error.");
            ptr[0]++;
        }
    }

    private void asmLabel(MmlDatum2 asm, /* ref */ int[] ptr) {
        if (assembleBlockLatest) {
            ptr[0] += 2;
            return;
        }

        List<Object> args = asm.args;
        if (args.get(ptr[0] + 1) instanceof String) {
            String label = ((String) args.get(ptr[0] + 1)).toLowerCase();

            if (dicLabel.containsKey(label)) {
                MmlDatum2 md = dicLabel.get(label);
                md.args.add(currentBank);
                md.args.add(currentAddress);
                ptr[0] += 2;
            }

            ptr[0] += 2;
        } else {
            logger.log(Level.ERROR, "Db Label error.");
            ptr[0]++;
        }
    }

    private void asmDbRefLabel(MmlDatum2 asm, /* ref */ int[] ptr) {
        if (assembleBlockLatest) {
            ptr[0] += 2;
            return;
        }

        List<Object> args = asm.args;
        if (args.get(ptr[0] + 1) instanceof String) {
            String label = ((String) args.get(ptr[0] + 1)).toLowerCase();
            Object byteFlg = false;
            if (label.indexOf("b:") >= 0) {
                byteFlg = true;
                label = label.substring(2);
            }

            if (label.indexOf("bank(") >= 0) {
                byteFlg = -1;
                label = label.substring(label.indexOf("bank(") + 5, label.lastIndexOf(")") - label.indexOf("bank(") - 5);
            }

            if (!dicRefLabel.containsKey(label)) {
                dicRefLabel.put(label, new ArrayList<>()); // currentBank, currentAddress, byteFlg
            }

            dicRefLabel.get(label).add(new Tuple3<Integer, Integer, Object>(currentBank, currentAddress, byteFlg));
            Poke(currentBank, currentAddress++, (byte) 0, asm);
            if (byteFlg instanceof Boolean && !(boolean) byteFlg) Poke(currentBank, currentAddress++, (byte) 0, asm);

            ptr[0] += 2;
        } else {
            logger.log(Level.ERROR, "Db ref Label error.");
            ptr[0]++;
        }
    }

    private void asmDbRefDefine(MmlDatum2 asm, /* ref */ int[] ptr) {
        if (assembleBlockLatest) {
            ptr[0] += 2;
            return;
        }

        List<Object> args = asm.args;
        if (args.get(ptr[0] + 1) instanceof String) {
            String define = ((String) args.get(ptr[0] + 1)).toLowerCase();
            boolean byteFlg = false;
            if (define.indexOf("b:") >= 0) {
                byteFlg = true;
                define = define.substring(2);
            }
            int n = GetInt(dicDefine.get(define).code);

            ptr[0] += 2;
            Poke(currentBank, currentAddress++, (byte) n, asm);
            if (!byteFlg) Poke(currentBank, currentAddress++, (byte) (n >> 8), asm);
        } else {
            logger.log(Level.ERROR, "Db ref define error.");
            ptr[0]++;
        }
    }

    private void Poke(int bank, int adr, byte dat, MmlDatum2 src) {
        while (dest.size() < bank + 1) {
            dest.add(new ArrayList<>());
        }
        while (dest.get(bank).size() < adr + 1) {
            dest.get(bank).add(null);
        }

        dest.get(bank).set(adr, new MmlDatum2());
        dest.get(bank).get(adr).dat = dat;
        dest.get(bank).get(adr).args = null;
        dest.get(bank).get(adr).code = "";
        dest.get(bank).get(adr).linePos = src.linePos;
        dest.get(bank).get(adr).type = src.type;
        if (adr > 0x9b84) {
        }
        logger.log(Level.TRACE, String.format("%02x:%04d:%02x", bank, adr, dat));
    }

    private void asmMacro(MmlDatum2 asm,/* ref */ int[] ptr) {
        String tmp = ((String) asm.args.get(ptr[0] + 1)).replace("\t", " ");
        String[] macros = tmp.split(" ");
        int n;

        switch (macros[ptr[0]]) {
            case ".bank":
                if (assembleBlockLatest) break;
                n = AnaFormula(asm.args);
                currentBank = n;
                ptr[0] = asm.args.size();
                return;
            case ".org":
                if (assembleBlockLatest) break;
                n = GetInt(macros[ptr[0] + 1]);
                currentAddress = n;
                break;
            case ".code":
                // ignore
                break;
            case ".if":
                assembleBlockStack.push(!anaCondition(macros)); // This is a flag to determine whether to block, so the inverse of the result is set.
                UpdateAssembleBlockLatest();
                break;
            case ".else":
                boolean flg = assembleBlockStack.pop();
                assembleBlockStack.push(!flg);
                UpdateAssembleBlockLatest();
                break;
            case ".endif":
                assembleBlockStack.pop();
                UpdateAssembleBlockLatest();
                break;
            default:
                logger.log(Level.ERROR, "macro error.");
                ptr[0]++;
                return;
        }
        ptr[0] += 2;
    }

    private boolean anaCondition(String[] macros) {
        List<Integer> op = new ArrayList<>();
        int con = -1000;
        for (int i = 1; i < macros.length; i++) {
            // For now, ignore (
            if (macros[i].charAt(0) == '(')
                macros[i] = macros[i].substring(1);
            if (macros[i].charAt(macros[i].length() - 1) == ')')
                macros[i] = macros[i].substring(0, macros[i].length() - 1);

            // Is it a constant?
            if (dicDefine.containsKey(macros[i].toLowerCase())) {
                op.add(GetInt(dicDefine.get(macros[i].toLowerCase()).code));
            } else if (macros[i].equals("=")) {
                con = 0;
            } else if (macros[i].equals("<")) {
                con = -1;
            } else if (macros[i].equals(">")) {
                con = 1;
            } else if (macros[i].equals("<>")) {
                con = 2;
            } else {
                try {
                    op.add(GetInt(macros[i]));
                } catch (Exception e) {
                }
            }
        }

        if (op.size() != 1 && (op.size() != 2 || con == -1000)) {
            throw new IllegalArgumentException("anaCondition error");
        }

        if (op.size() == 1) {
            return op.get(0) != 0;
        }

        if (con == 0) {
            return op.get(0) == op.get(1);
        }
        if (con == -1) {
            return op.get(0) < op.get(1);
        }
        if (con == 1) {
            return op.get(0) > op.get(1);
        }
        if (con == 2) {
            return op.get(0) != op.get(1);
        }

        return false;
    }

    private int AnaFormula(List<Object> a) {
        int ans = 0;
        int ope = -1;
        int n = 0;
        boolean flg = false;
        for (int i = 2; i < a.size(); i++) {
            if (a.get(i) instanceof String) {
                String s = String.valueOf(a.add(i)).toLowerCase();

                // Is it a constant?
                if (dicDefine.containsKey(s)) {
                    n = GetInt(dicDefine.get(s).code);
                    flg = true;
                } else if (s.equals("+")) {
                    ope = 0;
                }
            } else if (a.get(i) instanceof Integer) {
                n = (int) a.get(i);
                flg = true;
            }

            if (!flg) continue;
            flg = false;
            if (ope == -1) ans = n;
            if (ope == 0) ans += n;
        }

        return ans;
    }

    private int GetInt(String v) {
        if (v == null || v.isEmpty()) {
            throw new IllegalArgumentException("integer parse error");
        }
        if (v.charAt(0) == '$') {
            // Hexadecimal
            return Integer.parseInt(v.substring(1), 16);
        } else {
            return Integer.parseInt(v);
        }
    }

    private void SetLabel() {
        for (String refkey : dicRefLabel.keySet()) {
            String key = refkey + ":";
            if (!dicLabel.containsKey(key)) continue;
            MmlDatum2 md = dicLabel.get(key);
            byte bank = (byte) (int) md.args.get(2);
            int adr = (int) md.args.get(3);

            List<Tuple3<Integer, Integer, Object>> trgs = dicRefLabel.get(refkey);
            for (Tuple3<Integer, Integer, Object> trg : trgs) {
                byte trgBank = (byte) (int) trg.getItem1();
                int trgAdr = trg.getItem2();
                Object trgByteFlg = trg.getItem3();

                MmlDatum2 m;
                if (trgByteFlg instanceof Boolean) {
                    m = new MmlDatum2();
                    m.dat = (byte) adr;
                    dest.get(trgBank).set(trgAdr, m);
                    if (!(boolean) trgByteFlg) {
                        m = new MmlDatum2();
                        m.dat = (byte) (adr >> 8);
                        dest.get(trgBank).set(trgAdr + 1, m);
                    }
                } else {
                    m = new MmlDatum2();
                    m.dat = bank;
                    dest.get(trgBank).set(trgAdr, m);
                }
            }
        }
    }
}
