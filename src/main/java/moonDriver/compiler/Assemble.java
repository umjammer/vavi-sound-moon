package moonDriver.compiler;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.Stack;

import dotnet4j.util.compat.Tuple;


public class Assemble
    {

        private List<MmlDatum2> asFp;
        private List<MmlDatum2> efFp;
        private List<MmlDatum2> ouFp;
        private List<MmlDatum2> inFp;

        private List<MmlDatum2> asm;
        private Dictionary<String, List<MmlDatum2>> dicMacroBlock;
        private Dictionary<String, MmlDatum2> dicDefine;
        private Dictionary<String, List<Tuple<int, int, object>>> dicRefLabel=new Dictionary<String, List<Tuple<int, int, object>>>();
        private Dictionary<String, MmlDatum2> dicLabel;

        private List<List<MmlDatum2>> dest = new List<List<MmlDatum2>>();
        private int currentBank = 0;
        private int currentAddress = 0;
        private Stack<bool> assembleBlockStack = new Stack<bool>();
        private boolean assembleBlockLatest = false;

        public List<List<MmlDatum2>> build(moonDriver.Compiler.work wk, List<MmlDatum2> efFp, List<MmlDatum2> ouFp, List<MmlDatum2> inFp)
        {
            //とりあえず積んでおく
            assembleBlockStack.Push(false);
            UpdateAssembleBlockLatest();

            //
            GetAsmList();
            this.efFp = efFp;
            this.ouFp = ouFp;
            this.inFp = inFp;

            //インクルードを参照し、各リストを一つにまとめる
            Step1_Append();
            //マクロのブロックを収集する
            Step2_GetMacro();
            //マクロのブロックを置換する
            Step3_ReplaceMacro();
            //定数を収集する
            Step4_GetDefine();
            //ラベルを収集する
            Step5_GetLabel();
            //アセンブル
            Assemblling();
            //ラベル参照展開
            SetLabel();

            return dest;
        }

        private void UpdateAssembleBlockLatest()
        {
            assembleBlockLatest = assembleBlockStack.Contains(true);
        }

        private void GetAsmList()
        {
            String  t;

            asFp = new ArrayList<>();

            t = ".include \"define.inc\""; asFp.add(new MmlDatum2(t, -4, t));

            t = "DATA_BANK equ 0";        asFp.add(new MmlDatum2(t, -5, t));

            t = ".bank 0";                 asFp.add(new MmlDatum2(t, -4, ".bank", 0));
            t = ".org  $8000";             asFp.add(new MmlDatum2(t, -4, t));
            t = ".code";                   asFp.add(new MmlDatum2(t, -4, t));

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

            t = ".org  $8000"; asFp.add(new MmlDatum2(t, -4, t));

            t = "db \"MDRV\""; asFp.add(new MmlDatum2(t, -1, 'M', -1, 'D', -1, 'R', -1, 'V'));
            t = "dw $0004 ; version"; asFp.add(new MmlDatum2(t, -1, 4, -1, 0));
            t = "db $00   ; num of used channels ( 0 = auto )"; asFp.add(new MmlDatum2(t, -1, 0));

            t = "db SOUND_GENERATOR  ; device flags"; asFp.add(new MmlDatum2(t, -6, "b:SOUND_GENERATOR"));

            t = "dw $0000 ; adr title String  ( terminated with zero )"; asFp.add(new MmlDatum2(t, -1, 0, -1, 0));
            t = "dw $0000 ; adr artist string(terminated with zero)"; asFp.add(new MmlDatum2(t, -1, 0, -1, 0));
            t = "dw $0000 ; adr comment string(terminated with zero)"; asFp.add(new MmlDatum2(t, -1, 0, -1, 0));
            t = "db SOUND_USERPCM ; User PCM flag"; asFp.add(new MmlDatum2(t, -6, "b:SOUND_USERPCM"));
            t = "db $00 ; reserved"; asFp.add(new MmlDatum2(t, -1, 0));

            t = "dw sound_data_table; adr track table"; asFp.add(new MmlDatum2(t, -3, "sound_data_table"));
            t = "dw sound_data_bank; adr track bank table"; asFp.add(new MmlDatum2(t, -3, "sound_data_bank"));


            t = "dw loop_point_table; adr loop table"; asFp.add(new MmlDatum2(t, -3, "loop_point_table"));
            t = "dw loop_point_bank; adr loop bank table"; asFp.add(new MmlDatum2(t, -3, "loop_point_bank"));


            t = "dw softenve_table; adr venv table"; asFp.add(new MmlDatum2(t, -3, "softenve_table"));
            t = "dw softenve_lp_table; adr venv lp table"; asFp.add(new MmlDatum2(t, -3, "softenve_lp_table"));


            t = "dw pitchenve_table; adr penv table"; asFp.add(new MmlDatum2(t, -3, "pitchenve_table"));
            t = "dw pitchenve_lp_table; adr penv lp table"; asFp.add(new MmlDatum2(t, -3, "pitchenve_lp_table"));


            t = "dw arpeggio_table; adr nenv table"; asFp.add(new MmlDatum2(t, -3, "arpeggio_table"));
            t = "dw arpeggio_lp_table; adr nenv lp table"; asFp.add(new MmlDatum2(t, -3, "arpeggio_lp_table"));


            t = "dw $0000; adr lfo  table"; asFp.add(new MmlDatum2(t, -1, 0, -1, 0));
            t = "dw ttbl_data_table; adr inst table"; asFp.add(new MmlDatum2(t, -3, "ttbl_data_table"));

            t = "dw opl3tbl_data_table; adr opl3 table"; asFp.add(new MmlDatum2(t, -3, "opl3tbl_data_table"));

            t = "pcm_flags:"; asFp.add(new MmlDatum2(t,-2,t));
            t = "db    $00"; asFp.add(new MmlDatum2(t,-1,0));
            t = "db    $00"; asFp.add(new MmlDatum2(t,-1,0));

            t = ".if (SOUND_USERPCM = 1)"; asFp.add(new MmlDatum2(t, -4, t));
            t = "dw userpcm_string"; asFp.add(new MmlDatum2(t,-3, "userpcm_string"));
            t = ".else"; asFp.add(new MmlDatum2(t, -4, t));
            t = "dw  $0000"; asFp.add(new MmlDatum2(t, -1, 0, -1, 0));
            t = ".endif"; asFp.add(new MmlDatum2(t, -4, t));


            t = "dw tag_string"; asFp.add(new MmlDatum2(t, -3, "tag_string"));

            t = "db $00; start address of OPL4 SRAM(x * 0x10000)"; asFp.add(new MmlDatum2(t,-1,0));
            t = "db $00; start bank of PCM"; asFp.add(new MmlDatum2(t, -1, 0));
            t = "db $00; size of PCM banks"; asFp.add(new MmlDatum2(t, -1, 0));
            t = "db $00; size of last bank(x* 0x100)"; asFp.add(new MmlDatum2(t, -1, 0));

            t = "db $00; large count of PCM banks"; asFp.add(new MmlDatum2(t, -1, 0));

            t = ".org $8040"; asFp.add(new MmlDatum2(t, -4, t));

            t = ".if (SOUND_USERPCM = 1)"; asFp.add(new MmlDatum2(t, -4, t));
            t = "userpcm_string:"; asFp.add(new MmlDatum2(t, -2, t));
            t = "PCMFILE"; asFp.add(new MmlDatum2(t, -6, t));
            t = ".endif"; asFp.add(new MmlDatum2(t, -4, t));

            t = ".org $8080"; asFp.add(new MmlDatum2(t, -4, t));

            t = "tag_string:"; asFp.add(new MmlDatum2(t, -2, t));
            t = "TITLE_TEXT ; Track name(en)"; asFp.add(new MmlDatum2(t, -6, "TITLE_TEXT"));
            t = "db $00 ; Track name(jp)"; asFp.add(new MmlDatum2(t, -1, 0));
            t = "MAKER_TEXT ; Game name(en)"; asFp.add(new MmlDatum2(t,-6, "MAKER_TEXT"));
            t = "db $00 ; Game name(jp)"; asFp.add(new MmlDatum2(t,-1,0));
            t = "db $00 ; System name(en)"; asFp.add(new MmlDatum2(t, -1, 0));
            t = "db $00 ; System name(jp)"; asFp.add(new MmlDatum2(t, -1, 0));
            t = "COMPOSER_TEXT; Track author(en)"; asFp.add(new MmlDatum2(t, -6, "COMPOSER_TEXT"));
            t = "db $00; Track author(jp)"; asFp.add(new MmlDatum2(t, -1, 0));
            t = "db $00; Release date"; asFp.add(new MmlDatum2(t, -1, 0));
            t = "db $00; Programmer"; asFp.add(new MmlDatum2(t, -1, 0));
            t = "db $00; Notes"; asFp.add(new MmlDatum2(t, -1, 0));

            t = ".include \"effect.h\""; asFp.add(new MmlDatum2(t, -4, t));
        }

        private void Step1_Append()
        {
            asm = new ArrayList<>();
            Step1_start(asFp);
        }

        private void Step1_start(List<MmlDatum2> crnt)
        {
            foreach(MmlDatum2 md in crnt)
            {
                if (md == null || md.args == null || md.args.Count < 2 || !(md.args[0] is int) || (int)md.args[0] != -4)
                {
                    asm.add(md);
                    continue;
                }

                if(!(md.args[1] is string))
                {
                    asm.add(md);
                    continue;
                }

                String  wd = ((String)md.args[1]).Trim().toLowerCase();
                if (wd.indexOf(".include") != 0)
                {
                    asm.add(md);
                    continue;
                }

                wd = wd.Substring(".include".length).toLowerCase().Trim();

                if (wd == "\"define.inc\"")
                {
                    Step1_start(inFp);
                }
                else if (wd == "\"effect.h\"")
                {
                    Step1_start(efFp);
                }
                else
                {
                    Step1_start(ouFp);
                }
            }

        }


        private void Step2_GetMacro()
        {
            dicMacroBlock = new Dictionary<String, List<MmlDatum2>>();

            for(int i = 0; i < asm.Count; i++)
            {
                MmlDatum2 md = asm[i];

                if (md == null || md.args == null || md.args.Count < 2 || !(md.args[0] is int) || (int)md.args[0] != -4)
                {
                    continue;
                }

                if (!(md.args[1] is string))
                {
                    continue;
                }

                String  wd = ((String)md.args[1]).Trim().toLowerCase();
                if (wd.indexOf(".macro") < 0)
                {
                    continue;
                }

                String  macroLabel = wd.Substring(0, wd.indexOf('.')).Trim();
                asm.RemoveAt(i);
                List<MmlDatum2> mb = new ArrayList<>();

                while (i<asm.Count)
                {
                    md = asm[i];
                    if (md == null || md.args == null || md.args.Count < 2 || !(md.args[0] is int) || (int)md.args[0] != -4)
                    {
                        mb.add(md);
                        asm.RemoveAt(i);
                        continue;
                    }

                    if (!(md.args[1] is string))
                    {
                        mb.add(md);
                        asm.RemoveAt(i);
                        continue;
                    }
                    
                    wd = ((String)md.args[1]).Trim().toLowerCase();
                    if (wd.indexOf(".endm") < 0)
                    {
                        mb.add(md);
                        asm.RemoveAt(i);
                        continue;
                    }

                    asm.RemoveAt(i);
                    dicMacroBlock.add(macroLabel, mb);
                    i--;
                    break;
                }

            }
        }


        private void Step3_ReplaceMacro()
        {
            boolean f;

            do
            {
                f = false;
                for (int i = 0; i < asm.Count; i++)
                {
                    MmlDatum2 md = asm[i];

                    if (md == null || md.args == null || md.args.Count < 2 || !(md.args[0] is int) || (int)md.args[0] != -6)
                    {
                        continue;
                    }

                    if (!(md.args[1] is string))
                    {
                        continue;
                    }

                    String  wd = ((String)md.args[1]).Trim().toLowerCase();
                    for (String key : dicMacroBlock.Keys)
                    {
                        if (wd != key) continue;

                        f = true;
                        asm.RemoveAt(i);
                        for (MmlDatum2 val : dicMacroBlock[key])
                        {
                            asm.Insert(i++, val);
                        }
                    }
                }
            } while (f);

        }

        private void Step4_GetDefine()
        {
            dicDefine = new Dictionary<String, MmlDatum2>();

            for (int i = 0; i < asm.Count; i++)
            {
                MmlDatum2 md = asm[i];

                if (md == null || md.args == null || md.args.Count < 2 || !(md.args[0] is int) || (int)md.args[0] != -5)
                {
                    continue;
                }

                if (!(md.args[1] is string))
                {
                    continue;
                }

                String[] wd = ((String)md.args[1]).Trim().toLowerCase().Replace("\t", " ").split(new String[] { " " }, StringSplitOptions.RemoveEmptyEntries);
                if (wd[1]!="equ")
                {
                    continue;
                }

                String  defineLabel = wd[0];
                md = new MmlDatum2(wd[2], -5, "");
                dicDefine.add(defineLabel, md);

            }
        }

        private void Step5_GetLabel()
        {
            dicLabel = new Dictionary<String, MmlDatum2>();

            for (int i = 0; i < asm.Count; i++)
            {
                MmlDatum2 md = asm[i];

                if (md == null || md.args == null || md.args.Count < 2 || !(md.args[0] is int) || (int)md.args[0] != -2)
                {
                    continue;
                }

                if (!(md.args[1] is string))
                {
                    continue;
                }

                md.dat = i;//行数を入れてみる
                dicLabel.add((String)md.args[1], md);

            }
        }

        private void Assemblling()
        {
            for(int i = 0; i < asm.Count; i++)
            {
                
                if (asm==null || asm[i].args == null || asm[i].args.Count < 2) continue;
                List<Object> args = asm[i].args;

                String  code = asm[i].code;
                while (code.indexOf("\n") == code.length - 1) code = code.Substring(0, code.length - 1);
                while (code.indexOf("\n") == 0) code = code.Substring(1);
                Log.WriteLine(LogLevel.TRACE, code);

                int ptr = 0;
                while (ptr < asm[i].args.Count)
                {
                    if(!(args[ptr ] is int))
                    {
                        ptr++;
                        continue;
                    }

                    int tp = (int)args.charAt(ptr);
                    switch (tp)
                    {
                        case -1://db
                            asmDb(asm[i], /* ref */ ptr);
                            break;
                        case -2://label
                            asmLabel(asm[i], /* ref */ ptr);
                            break;
                        case -3://db /* ref */ label
                            asmDbRefLabel(asm[i], /* ref */ ptr);
                            break;
                        case -4://macro
                            asmMacro(asm[i], /* ref */ ptr);
                            break;
                        case -5://define
                            ptr = asm[i].args.Count;
                            break;
                        case -6://db /* ref */ define
                            asmDbRefDefine(asm[i], /* ref */ ptr);
                            break;
                        default:
                            logger.log(Level.ERROR, String.format("Unknown type[%d] error. ", tp));
                            ptr++;
                            break;
                    }
                }
            }
        }

        private void asmDb(MmlDatum2 asm, /* ref */ int ptr)
        {
            if (assembleBlockLatest) { ptr += 2; return; }

            List<Object> args = asm.args;
            if (args[ptr + 1] is byte)
            {
                byte n = (byte)args[ptr + 1];
                ptr += 2;
                Poke(currentBank, currentAddress++, n, asm);
            }
            else if (args[ptr + 1] is int)
            {
                byte n = (byte)(int)args[ptr + 1];
                ptr += 2;
                Poke(currentBank, currentAddress++, n, asm);//int であってもbyte扱いです
            }
            else if (args[ptr + 1] is char)
            {
                byte n = (byte)(char)args[ptr + 1];
                ptr += 2;
                Poke(currentBank, currentAddress++, n, asm);
            }
            else if (args[ptr + 1] is string)
            {
                //複合型
                String  sen = (String)args[ptr + 1];
                List<Byte> wd = new ArrayList<>();
                for(int i = 0; i < sen.length; i++)
                {
                    if (sen[i] == ' ' || sen[i] == '\t') continue;
                    if (sen[i] == ',')
                    {
                        continue;
                    }

                    int j;
                    String  x = "";

                    if (sen[i] == '"')
                    {
                        x = "";
                        j = i + 1;
                        for (; j < sen.length; j++)
                        {
                            if (sen[j] == '"') break;
                            x += sen[j];
                        }
                        i = j;

                        Common.myEncoding enc = new Common.myEncoding();
                        byte[] ary = enc.GetSjisArrayFromString(x);
                        for (byte b : ary) wd.add(b);
                        continue;
                    }

                    x = "";
                    j = i;
                    for (; j < sen.length; j++)
                    {
                        if (sen[i] == ' ' || sen[i] == '\t' || sen[i] == ',') break;
                        x += sen[j];
                    }
                    i = j;
                    int n = GetInt(x);
                    wd.add((byte)n);
                }

                ptr += 2;
                for (byte b : wd) Poke(currentBank, currentAddress++, b, asm);

            }
            else
            {
                logger.log(Level.ERROR, "Db error.");
                ptr++;
            }
        }

        private void asmLabel(MmlDatum2 asm, /* ref */ int ptr)
        {
            if (assembleBlockLatest) { ptr += 2; return; }

            List<Object> args = asm.args;
            if (args[ptr + 1] is string)
            {
                String  label = ((String)args[ptr + 1]).toLowerCase();

                if (dicLabel.ContainsKey(label))
                {
                    MmlDatum2 md = dicLabel[label];
                    ;
                    md.args.add(currentBank);
                    md.args.add(currentAddress);
                    ptr += 2;
                }

                ptr += 2;
            }
            else
            {
                logger.log(Level.ERROR, "Db Label error.");
                ptr++;
            }
        }

        private void asmDbRefLabel(MmlDatum2 asm, /* ref */ int ptr)
        {
            if (assembleBlockLatest) { ptr += 2; return; }

            List<Object> args = asm.args;
            if (args[ptr + 1] is string)
            {
                String  label = ((String)args[ptr + 1]).toLowerCase();
                Object byteFlg = false;
                if (label.indexOf("b:") >= 0)
                {
                    byteFlg = true;
                    label = label.Substring(2);
                }

                if (label.indexOf("bank(") >= 0)
                {
                    byteFlg = -1;
                    label = label.Substring(label.indexOf("bank(") + 5, label.LastIndexOf(")") - label.indexOf("bank(") - 5);
                }

                if (!dicRefLabel.ContainsKey(label))
                {
                    dicRefLabel.add(label, new List<Tuple<int, int, object>>());// currentBank, currentAddress, byteFlg
                }

                dicRefLabel[label].add(new Tuple<int, int, object>(currentBank, currentAddress, byteFlg));
                Poke(currentBank, currentAddress++, 0, asm);
                if (byteFlg is boolean && !(bool)byteFlg) Poke(currentBank, currentAddress++, 0, asm);

                ptr += 2;
            }
            else
            {
                logger.log(Level.ERROR, "Db /* ref */ Label error.");
                ptr++;
            }
        }

        private void asmDbRefDefine(MmlDatum2 asm, /* ref */ int ptr)
        {
            if (assembleBlockLatest) { ptr += 2; return; }

            List<Object> args = asm.args;
            if (args[ptr + 1] is string)
            {
                String  define = ((String)args[ptr + 1]).toLowerCase();
                boolean byteFlg = false;
                if (define.indexOf("b:") >= 0)
                {
                    byteFlg = true;
                    define = define.Substring(2);
                }
                int n = GetInt(dicDefine[define].code);

                ptr += 2;
                Poke(currentBank, currentAddress++, (byte)n, asm);
                if (!byteFlg) Poke(currentBank, currentAddress++, (byte)(n >> 8), asm);
            }
            else
            {
                logger.log(Level.ERROR, "Db /* ref */ degine error.");
                ptr++;
            }
        }

        private void Poke(int bank, int adr, byte dat, MmlDatum2 src)
        {
            while (dest.Count < bank + 1)
            {
                dest.add(new ArrayList<>());
            }
            while (dest[bank].Count < adr + 1)
            {
                dest[bank].add(null);
            }

            dest[bank][adr] = new MmlDatum2();
            dest[bank][adr].dat = dat;
            dest[bank][adr].args = null;
            dest[bank][adr].code = "";
            dest[bank][adr].linePos = src.linePos;
            dest[bank][adr].type = src.type;
            if (adr > 0x9b84)
            {
                ;
            }
            Log.WriteLine(LogLevel.TRACE, String.format("{0:X02}:{1:X04}:{2:X02}",bank,adr,dat));
        }

        private void asmMacro(MmlDatum2 asm,/* ref */ int ptr)
        {
            String  tmp = ((String)asm.args[ptr + 1]).Replace("\t", " ");
            String[] macros = tmp.split(new String[] { " " }, StringSplitOptions.RemoveEmptyEntries);
            int n;

            switch (macros.charAt(ptr))
            {
                case ".bank":
                    if (assembleBlockLatest) break;
                    n = AnaFormula(asm.args);
                    currentBank = n;
                    ptr = asm.args.Count;
                    return;
                case ".org":
                    if (assembleBlockLatest) break;
                    n = GetInt(macros[ptr+1]);
                    currentAddress = n;
                    break;
                case ".code":
                    //無視
                    break;
                case ".if":
                    assembleBlockStack.Push(!anaCondition(macros));//ブロックするかどうかのフラグなので判定結果を反転させたものがセットされる
                    UpdateAssembleBlockLatest();
                    break;
                case ".else":
                    boolean flg = assembleBlockStack.Pop();
                    assembleBlockStack.Push(!flg);
                    UpdateAssembleBlockLatest();
                    break;
                case ".endif":
                    assembleBlockStack.Pop();
                    UpdateAssembleBlockLatest();
                    break;
                default:
                    logger.log(Level.ERROR, "macro error.");
                    ptr++;
                    return;
            }
            ptr += 2;
        }

        private boolean anaCondition(String[] macros)
        {
            List<int> op = new ArrayList<>();
            int con = -1000;
            for (int i = 1; i < macros.length; i++)
            {
                //とりあえず(は無視する
                if (macros[i][0] == '(')
                    macros[i] = macros[i].Substring(1);
                if (macros[i][macros[i].length - 1] == ')')
                    macros[i] = macros[i].Substring(0, macros[i].length - 1);

                //定数かな
                if (dicDefine.ContainsKey(macros[i].toLowerCase()))
                {
                    op.add(GetInt(dicDefine[macros[i].toLowerCase()].code));
                }
                else if (macros[i] == "=")
                {
                    con = 0;
                }
                else if (macros[i] == "<")
                {
                    con = -1;
                }
                else if (macros[i] == ">")
                {
                    con = 1;
                }
                else if (macros[i] == "<>")
                {
                    con = 2;
                }
                else
                {
                    try
                    {
                        op.add(GetInt(macros[i]));
                    }
                    catch { }
                }
            }

            if(op.Count!=1 && (op.Count!=2 || con == -1000))
            {
                throw new Exception("anaCondition error");
            }

            if (op.Count == 1)
            {
                return op[0] != 0;
            }

            if (con == 0)
            {
                return op[0] == op[1];
            }
            if (con == -1)
            {
                return op[0] < op[1];
            }
            if (con == 1)
            {
                return op[0] > op[1];
            }
            if (con == 2)
            {
                return op[0] != op[1];
            }

            return false;
        }

        private int AnaFormula(List<Object> a)
        {
            int ans = 0;
            int ope = -1;
            int n = 0;
            boolean flg = false;
            for (int i = 2; i < a.Count; i++)
            {
                if (a[i] is string)
                {
                    String  s = ((String)a[i]).toLowerCase();

                    //定数かな
                    if (dicDefine.ContainsKey(s))
                    {
                        n = GetInt(dicDefine[s].code);
                        flg = true;
                    }
                    else if (s == "+")
                    {
                        ope = 0;
                    }
                }
                else if (a[i] is int)
                {
                    n = (int)a[i];
                    flg = true;
                }

                if (!flg) continue;
                flg = false;
                if (ope == -1) ans = n;
                if (ope == 0) ans += n;
            }

            return ans;
        }

        private int GetInt(String  v)
        {
            if (v == null || v.length < 1)
            {
                throw new Exception("integer parse error");
            }
            if (v[0] == '$')
            {
                //16進数
                return Convert.ToInt32(v.Substring(1), 16);
            }
            else
            {
                return int.Parse(v);
            }
        }

        private void SetLabel()
        {
            for (String refkey : dicRefLabel.Keys)
            {
                String  key = refkey + ":";
                if (!dicLabel.ContainsKey(key)) continue;
                MmlDatum2 md = dicLabel[key];
                byte bank = (byte)(int)md.args[2];
                int adr = (int)md.args[3];

                List<Tuple<int, int, object>> trgs = dicRefLabel[refkey];
                for (Tuple<int, int, object> trg : trgs)
                {
                    byte trgBank = (byte)trg.getItem1();
                    int trgAdr = trg.getItem2();
                    Object trgByteFlg = trg.Item3;

                    MmlDatum2 m;
                    if (trgByteFlg is bool)
                    {
                        m = new MmlDatum2();
                        m.dat = (byte)adr;
                        dest[trgBank][trgAdr] = m;
                        if (!(bool)trgByteFlg)
                        {
                            m = new MmlDatum2();
                            m.dat = (byte)(adr >> 8);
                            dest[trgBank][trgAdr + 1] = m;
                        }
                    }
                    else
                    {
                        m = new MmlDatum2();
                        m.dat = bank;
                        dest[trgBank][trgAdr] = m;
                    }
                }
            }
        }

    }
}
