package com.ccode.mintest;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

public final class CAutoIndent implements TextWatcher {

    private static final String UNIT_TAB = "\t";
    private static final String UNIT_SPACE = "    ";

    private final EditText mEditor;
    private final CSyntaxHighlighter mHighlighter;
    private boolean mApplying;
    private boolean mNewlineInserted;
    private int mNewlinePos = -1;

    public CAutoIndent(EditText editor, CSyntaxHighlighter highlighter) {
        mEditor = editor;
        mHighlighter = highlighter;
        mEditor.addTextChangedListener(this);
    }

    public void detach() {
        mEditor.removeTextChangedListener(this);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        if (mApplying) return;
        mNewlineInserted = false;
        mNewlinePos = -1;
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (mApplying) return;
        mNewlineInserted = count == 1 && start < s.length()
                && s.charAt(start) == '\n';
        mNewlinePos = mNewlineInserted ? start : -1;
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (mApplying) return;
        if (!mNewlineInserted) return;
        mNewlineInserted = false;

        final int nl = mNewlinePos;
        if (nl < 0 || nl >= s.length()) return;

        final String indent = computeIndent(s, nl);
        if (indent.length() == 0) return;

        final int target = nl + 1 + indent.length();

        mApplying = true;
        try {
            mHighlighter.beginExternalInsert(s, nl + 1);
            s.insert(nl + 1, indent);
        } finally {
            mApplying = false;
            mHighlighter.endExternalInsert(s, indent.length(), 0);
        }

        mEditor.setSelection(target);
        mEditor.post(new Runnable() {
            @Override
            public void run() {
                if (target <= mEditor.length()) mEditor.setSelection(target);
            }
        });
    }

    private static String computeIndent(CharSequence text, int newlinePos) {
        int prevStart = 0;
        for (int i = newlinePos - 1; i >= 0; i--) {
            if (text.charAt(i) == '\n') {
                prevStart = i + 1;
                break;
            }
        }

        int p = prevStart;
        while (p < newlinePos && isIndentChar(text.charAt(p))) p++;
        String indent = text.subSequence(prevStart, p).toString();

        int end = newlinePos;
        while (end > prevStart && isTrailingBlank(text.charAt(end - 1))) end--;
        if (end > prevStart && text.charAt(end - 1) == '{') {
            indent += indent.indexOf('\t') >= 0 ? UNIT_TAB : UNIT_SPACE;
        }
        return indent;
    }

    private static boolean isIndentChar(char c) {
        return c == ' ' || c == '\t';
    }

    private static boolean isTrailingBlank(char c) {
        return c == ' ' || c == '\t' || c == '\r';
    }
}
