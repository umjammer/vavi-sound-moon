package moonDriver.compiler;

/**
 *
 */
public class Strings {

    /**
     * Skip spaces/tabs
     *
     * @param buf Data Storage Pointer
     * @return Pointer after skip
     */
    public int skipSpaceOld(String buf, int ptr) {
        while (ptr < buf.length() && buf.charAt(ptr) != '\0') {
            if (buf.charAt(ptr) != ' ' && buf.charAt(ptr) != '\t') {
                break;
            }
            ptr++;
        }
        return ptr;
    }

    /**
     * Skip a string
     */
    public int skipQuote(String buf, int ptr) {
        if (buf.charAt(ptr) != 0 &&
                buf.charAt(ptr) == '\"') {
            ptr++; // skip start charactor
            while (ptr < buf.length() && buf.charAt(ptr) != 0) {
                if (buf.charAt(ptr) == '\"') { // end of the quote
                    ptr++;
                    break;
                }

                if (buf.charAt(ptr) == '\\' && buf.charAt(ptr + 1) != 0) // skip Escape
                    ptr++;
                ptr++;
            }
        }
        return ptr;
    }

    /**
     * Check for comment characters
     */
    public boolean isComment(String buf, int ptr) {
        if (buf.charAt(ptr) != 0 &&
                (buf.charAt(ptr) == ';' ||
                        //		 (*ptr == '/' && *(ptr+1) == '/')
                        buf.charAt(ptr) == '/'))
            return true;

        return false;
    }

    /**
     * Skip comments
     */
    public int skipComment(String buf, int ptr) {
        if (isComment(buf, ptr)) {
            while (true) {
                // '\0' = EOL or EOF , '\n' = EOL
                if (ptr == buf.length() || buf.charAt(ptr) == '\0' || buf.charAt(ptr) == '\n')
                    break;
                ptr++;
            }
        }
        return ptr;
    }

    /**
     * Skip spaces/tabs (also skip line comments)
     */
    public int skipSpace(String buf, int ptr) {
        while (true) {
            if (ptr == buf.length()) break; //EOL or EOF
            if (buf.charAt(ptr) == ' ' || buf.charAt(ptr) == '\t') {
                //Skip Space
                ptr++;
                continue;
            } else if (isComment(buf, ptr)) {
                //Skip Comment(return EOL)
                ptr = skipComment(buf, ptr);
            } else {
                //Normal Chars
                break;
            }
        }
        return ptr;
    }

//    /**
//     * Check if the character is a Kanji
//     * Input:
//     * char	c	: character
//     * Return:
//     * 0: Non-Kanji 1: Kanji code
//     */
//    int checkKanji(unsigned char c) {
//        if (0x81 <= c && c <= 0x9f) return 1;
//        if (0xe0 <= c && c <= 0xef) return 1;
//        return 0;
//    }

//    /**
//     * Make a string uppercase (Kanji compatible version)
//     * Input:
//     * char *ptr	: A pointer to a string
//     * Output:
//     * none
//     */
//    void strupper(String[] ptr) {
//        while (ptr != '\0') {
//            if (checkKanji((unsigned char) *ptr) ==0 ){
//    					*ptr = toupper(( int)*ptr);
//            ptr++;
//        } else {
//                // Processing when using Kanji
//                ptr += 2;
//            }
//        }
//    }

    /**
     * Convert string to number
     */
    public int asc2Int(String buf, int ptr, /* ref */ int[] cnt) {
        int num;
        char c;
        int minus_flag = 0;

        num = 0;
        cnt[1] = 0;

        if (buf.charAt(ptr) == '-') {
            minus_flag = 1;
            ptr++;
            cnt[0]++;
        }
        switch (buf.charAt(ptr)) {
            // Hexadecimal
            case 'x':
            case '$':
                ptr++;
                cnt[0]++;
                while (true) {
                    c = String.valueOf(Character.toUpperCase(buf.charAt(ptr))).charAt(0);
                    if ('0' <= c && c <= '9') {
                        num = num * 16 + (c - '0');
                    } else if ('A' <= c && c <= 'F') {
                        num = num * 16 + (c - 'A' + 10);
                    } else {
                        break;
                    }
                    cnt[0]++;
                    ptr++;
                }
                break;
            // Binary numbers
            case '%':
                ptr++;
                cnt[0]++;
                while (true) {
                    if ('0' <= buf.charAt(ptr) && buf.charAt(ptr) <= '1') {
                        num = num * 2 + (buf.charAt(ptr) - '0');
                    } else {
                        break;
                    }
                    cnt[0]++;
                    ptr++;
                }
                break;
            // Decimal
            default:
                while (true) {
                    if ('0' <= buf.charAt(ptr) && buf.charAt(ptr) <= '9') {
                        num = num * 10 + (buf.charAt(ptr) - '0');
                    } else {
                        break;
                    }
                    cnt[0]++;
                    ptr++;
                }
                break;
        }
        if (minus_flag != 0) {
            num = -num;
        }
        return num;
    }
}
