package com.ccode.mintest;

import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.widget.EditText;

import java.util.HashSet;
import java.util.Set;

public final class CSyntaxHighlighter implements TextWatcher {

    public static final int COLOR_PLAIN = 0xFFD4D4D4;
    public static final int COLOR_KEYWORD = 0xFF569CD6;
    public static final int COLOR_CONTROL = 0xFFC586C0;
    public static final int COLOR_TYPE = 0xFF4EC9B0;
    public static final int COLOR_STRING = 0xFFCE9178;
    public static final int COLOR_NUMBER = 0xFFB5CEA8;
    public static final int COLOR_COMMENT = 0xFF6A9955;
    public static final int COLOR_PREPROC = 0xFFC586C0;

    private static final Set<String> CONTROL_KEYWORDS = buildSet(new String[]{
            "if", "else", "for", "while", "do", "switch", "case", "default",
            "break", "continue", "return", "goto"
    });

    private static final Set<String> KEYWORDS = buildSet(new String[]{
            "auto", "sizeof", "static", "extern", "const", "volatile", "register",
            "inline", "typedef", "struct", "union", "enum", "restrict", "asm",
            "_Alignas", "_Alignof", "_Atomic", "_Generic", "_Noreturn",
            "_Static_assert", "_Thread_local", "_Complex", "_Imaginary",
            "__attribute__", "__inline__", "__asm__"
    });

    private static final Set<String> TYPES = buildSet(new String[]{
            "int", "char", "float", "double", "long", "short", "signed", "unsigned",
            "void", "_Bool", "bool", "size_t", "ssize_t", "ptrdiff_t", "wchar_t",
            "int8_t", "int16_t", "int32_t", "int64_t",
            "uint8_t", "uint16_t", "uint32_t", "uint64_t",
            "intptr_t", "uintptr_t", "intmax_t", "uintmax_t",
            "FILE", "time_t", "clock_t", "div_t", "ldiv_t", "va_list", "jmp_buf",
            "fpos_t", "sig_atomic_t"
    });

    private static final int MAX_HIGHLIGHT_CHARS = 2 * 1024 * 1024;

    private int[] mLineStarts = new int[256];
    private boolean[] mBlockStates = new boolean[256];
    private int mLineCount = 1;

    private final EditText mEditor;
    private boolean mHighlighting;
    private boolean mDegraded;
    private boolean mExternal;
    private int mExtLine = 0;
    private int mChangeLine = 0;
    private int mRemovedNewlines = 0;
    private int mAddedNewlines = 0;
    private int mCharDelta = 0;

    public CSyntaxHighlighter(EditText editor) {
        mEditor = editor;
        mEditor.addTextChangedListener(this);
        rebuildLines(mEditor.getText());
    }

    public void detach() {
        mEditor.removeTextChangedListener(this);
    }

    public void rehighlightAll() {
        Editable e = mEditor.getText();
        mDegraded = e.length() > MAX_HIGHLIGHT_CHARS;
        rebuildLines(e);
        if (mDegraded) {
            removeHighlights(e, 0, e.length());
            return;
        }
        highlight(e, 0, Math.max(0, mLineCount - 1));
    }

    public void beginExternalInsert(Editable e, int start) {
        if (mLineCount < 1 || mLineStarts[mLineCount] != e.length()) {
            rebuildLines(e);
        }
        if (start < 0) start = 0;
        if (start > e.length()) start = e.length();
        mExternal = true;
        mExtLine = lineOfOffset(start);
    }

    public void endExternalInsert(Editable e, int insertedChars, int insertedNewlines) {
        mExternal = false;
        if (insertedChars <= 0 || e.length() > MAX_HIGHLIGHT_CHARS) return;
        spliceLines(e, mExtLine, 0, Math.max(0, insertedNewlines), insertedChars);
        int startLine = Math.max(0, Math.min(mExtLine, mLineCount - 1));
        int endLine = Math.min(mLineCount - 1,
                startLine + Math.max(0, insertedNewlines));
        highlight(e, startLine, endLine);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        if (mHighlighting || mExternal) return;
        mChangeLine = lineOfOffset(start);
        mRemovedNewlines = countNewlines(s, start, start + count);
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (mHighlighting || mExternal) return;
        mAddedNewlines = countNewlines(s, start, start + count);
        mCharDelta = count - before;
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (mHighlighting || mExternal) return;
        if (s.length() > MAX_HIGHLIGHT_CHARS) {
            if (!mDegraded) {
                mDegraded = true;
                removeHighlights(s, 0, s.length());
            }
            return;
        }
        mDegraded = false;
        spliceLines(s, mChangeLine, mRemovedNewlines, mAddedNewlines, mCharDelta);
        int startLine = mChangeLine;
        int endLine = mChangeLine + mAddedNewlines;
        if (endLine >= mLineCount) endLine = mLineCount - 1;
        highlight(s, startLine, endLine);
    }

    private void highlight(Editable e, int startLine, int endLine) {
        final int textLen = e.length();
        final int lineCount = mLineCount;
        if (startLine < 0) startLine = 0;
        if (startLine >= lineCount) startLine = lineCount - 1;
        if (endLine < startLine) endLine = startLine;
        if (endLine >= lineCount) endLine = lineCount - 1;

        mHighlighting = true;
        try {
            int clearFrom = mLineStarts[startLine];
            int clearTo = (endLine + 1 <= lineCount) ? mLineStarts[endLine + 1] : textLen;
            removeHighlights(e, clearFrom, clearTo);

            boolean inBlock = startLine > 0 && mBlockStates[startLine - 1];
            int line = startLine;
            while (line < lineCount) {
                int ls = mLineStarts[line];
                int le = (line + 1 < lineCount) ? mLineStarts[line + 1] - 1 : textLen;
                if (le < ls) le = ls;

                if (line > endLine) {
                    removeHighlights(e, ls, le);
                }
                boolean newState = colorizeLine(e, ls, le, inBlock);
                boolean oldState = mBlockStates[line];
                mBlockStates[line] = newState;
                if (line > endLine && newState == oldState) break;
                inBlock = newState;
                line++;
            }
        } finally {
            mHighlighting = false;
        }
    }

    private boolean colorizeLine(Editable e, int start, int end, boolean inBlock) {
        int i = start;
        boolean block = inBlock;
        while (i < end) {
            char c = e.charAt(i);

            if (block) {
                int close = indexOf(e, "*/", i, end);
                if (close < 0) {
                    setColor(e, i, end, COLOR_COMMENT);
                    return true;
                }
                setColor(e, i, close + 2, COLOR_COMMENT);
                i = close + 2;
                block = false;
                continue;
            }

            if (c == '/' && i + 1 < end) {
                char n = e.charAt(i + 1);
                if (n == '/') {
                    setColor(e, i, end, COLOR_COMMENT);
                    return false;
                }
                if (n == '*') {
                    int close = indexOf(e, "*/", i + 2, end);
                    if (close < 0) {
                        setColor(e, i, end, COLOR_COMMENT);
                        return true;
                    }
                    setColor(e, i, close + 2, COLOR_COMMENT);
                    i = close + 2;
                    continue;
                }
            }

            if (c == '#' && isBlankBefore(e, start, i)) {
                setColor(e, i, end, COLOR_PREPROC);
                return false;
            }

            if (c == '"' || c == '\'') {
                i = colorizeQuoted(e, i, end, c);
                continue;
            }

            if (isDigit(c) || (c == '.' && i + 1 < end && isDigit(e.charAt(i + 1)))) {
                i = colorizeNumber(e, i, end);
                continue;
            }

            if (isIdStart(c)) {
                int j = i + 1;
                while (j < end && isIdPart(e.charAt(j))) j++;
                int color = colorOfWord(e.subSequence(i, j).toString());
                if (color != 0) setColor(e, i, j, color);
                i = j;
                continue;
            }

            i++;
        }
        return block;
    }

    private static int colorizeQuoted(Editable e, int start, int end, char quote) {
        int i = start + 1;
        while (i < end) {
            char c = e.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) {
                i++;
                break;
            }
            i++;
        }
        if (i > end) i = end;
        setColor(e, start, i, COLOR_STRING);
        return i;
    }

    private static int colorizeNumber(Editable e, int start, int end) {
        int i = start;
        boolean hex = false;

        if (e.charAt(i) == '0' && i + 1 < end) {
            char n = e.charAt(i + 1);
            if (n == 'x' || n == 'X') {
                hex = true;
                i += 2;
                while (i < end && isHexDigit(e.charAt(i))) i++;
            } else if (n == '.') {
                i++;
                while (i < end && isDigit(e.charAt(i))) i++;
                i = scanExponent(e, i, end);
            } else if (n >= '0' && n <= '7') {
                i++;
                while (i < end && e.charAt(i) >= '0' && e.charAt(i) <= '7') i++;
            } else {
                i++;
            }
        } else {
            while (i < end && isDigit(e.charAt(i))) i++;
            if (i < end && e.charAt(i) == '.') {
                i++;
                while (i < end && isDigit(e.charAt(i))) i++;
            }
            i = scanExponent(e, i, end);
        }

        if (!hex) {
            while (i < end) {
                char c = e.charAt(i);
                if (c == 'u' || c == 'U' || c == 'l' || c == 'L'
                        || c == 'f' || c == 'F') {
                    i++;
                } else {
                    break;
                }
            }
        }

        if (i <= start) i = start + 1;
        setColor(e, start, i, COLOR_NUMBER);
        return i;
    }

    private static int scanExponent(CharSequence s, int i, int end) {
        if (i >= end) return i;
        char c = s.charAt(i);
        if (c != 'e' && c != 'E') return i;
        int j = i + 1;
        if (j < end && (s.charAt(j) == '+' || s.charAt(j) == '-')) j++;
        if (j < end && isDigit(s.charAt(j))) {
            j++;
            while (j < end && isDigit(s.charAt(j))) j++;
            return j;
        }
        return i;
    }

    private static void setColor(Editable e, int start, int end, int color) {
        if (end <= start) return;
        e.setSpan(new ForegroundColorSpan(color), start, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void removeHighlights(Editable e, int start, int end) {
        if (end <= start) return;
        ForegroundColorSpan[] spans = e.getSpans(start, end, ForegroundColorSpan.class);
        if (spans == null) return;
        for (int i = 0; i < spans.length; i++) {
            e.removeSpan(spans[i]);
        }
    }

    private void rebuildLines(CharSequence text) {
        int len = text.length();
        int needed = len + 2;
        if (mLineStarts.length < needed) mLineStarts = new int[needed];
        if (mBlockStates.length < needed) mBlockStates = new boolean[needed];

        int count = 1;
        mLineStarts[0] = 0;
        for (int i = 0; i < len; i++) {
            if (text.charAt(i) == '\n') mLineStarts[count++] = i + 1;
        }
        mLineStarts[count] = len;
        mLineCount = count;
    }

    private int lineOfOffset(int offset) {
        if (mLineCount <= 1) return 0;
        int lo = 0;
        int hi = mLineCount - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >> 1;
            if (mLineStarts[mid] <= offset) lo = mid;
            else hi = mid - 1;
        }
        if (mLineStarts[lo] > offset && lo > 0) lo--;
        return lo;
    }

    private void spliceLines(CharSequence text, int firstLine, int removedNewlines,
                             int addedNewlines, int charDelta) {
        if (firstLine < 0) firstLine = 0;
        if (firstLine >= mLineCount) firstLine = mLineCount - 1;

        int newLineCount = mLineCount - removedNewlines + addedNewlines;
        int keepFrom = firstLine + removedNewlines + 1;
        int newKeepFrom = firstLine + addedNewlines + 1;
        int tailLen = mLineCount + 1 - keepFrom;

        if (newLineCount < 1 || tailLen < 0 || newKeepFrom + tailLen > newLineCount + 1) {
            rebuildLines(text);
            return;
        }

        int need = newLineCount + 1;
        if (mLineStarts.length < need) {
            int[] ns = new int[Math.max(need, mLineStarts.length * 2)];
            System.arraycopy(mLineStarts, 0, ns, 0, mLineCount + 1);
            mLineStarts = ns;
        }
        if (mBlockStates.length < need) {
            boolean[] nb = new boolean[Math.max(need, mBlockStates.length * 2)];
            System.arraycopy(mBlockStates, 0, nb, 0, mLineCount);
            mBlockStates = nb;
        }

        if (tailLen > 0) {
            System.arraycopy(mLineStarts, keepFrom, mLineStarts, newKeepFrom, tailLen);
            System.arraycopy(mBlockStates, keepFrom, mBlockStates, newKeepFrom,
                    Math.max(0, mLineCount - keepFrom));
        }

        for (int i = newKeepFrom; i <= newLineCount; i++) {
            mLineStarts[i] += charDelta;
        }

        int idx = firstLine + 1;
        int scan = (firstLine <= mLineCount) ? mLineStarts[firstLine] : 0;
        for (int k = 0; k < addedNewlines; k++) {
            int nl = indexOfNewline(text, scan);
            if (nl < 0) break;
            scan = nl + 1;
            if (idx <= newLineCount) mLineStarts[idx++] = scan;
        }

        mLineStarts[newLineCount] = text.length();
        mLineCount = newLineCount;
    }

    private static int countNewlines(CharSequence s, int from, int to) {
        int n = 0;
        if (to > s.length()) to = s.length();
        for (int i = from; i < to; i++) {
            if (s.charAt(i) == '\n') n++;
        }
        return n;
    }

    private static int indexOfNewline(CharSequence s, int from) {
        for (int i = from; i < s.length(); i++) {
            if (s.charAt(i) == '\n') return i;
        }
        return -1;
    }

    private static int indexOf(CharSequence s, String needle, int from, int to) {
        char first = needle.charAt(0);
        int last = to - needle.length();
        for (int i = from; i <= last; i++) {
            if (s.charAt(i) != first) continue;
            boolean ok = true;
            for (int k = 1; k < needle.length(); k++) {
                if (s.charAt(i + k) != needle.charAt(k)) {
                    ok = false;
                    break;
                }
            }
            if (ok) return i;
        }
        return -1;
    }

    private static boolean isBlankBefore(CharSequence s, int start, int index) {
        for (int i = start; i < index; i++) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t' && c != '\r') return false;
        }
        return true;
    }

    private static boolean isIdStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdPart(char c) {
        return isIdStart(c) || isDigit(c);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isHexDigit(char c) {
        return isDigit(c)
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
    }

    private static int colorOfWord(String word) {
        if (CONTROL_KEYWORDS.contains(word)) return COLOR_CONTROL;
        if (KEYWORDS.contains(word)) return COLOR_KEYWORD;
        if (TYPES.contains(word)) return COLOR_TYPE;
        return 0;
    }

    private static Set<String> buildSet(String[] words) {
        Set<String> set = new HashSet<String>(words.length * 2);
        for (int i = 0; i < words.length; i++) {
            set.add(words[i]);
        }
        return set;
    }
}
