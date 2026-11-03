package com.ccode.mintest;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import java.util.HashMap;
import java.util.Map;

public class CBracketAutoComplete implements TextWatcher {

    private static final Map<Character, Character> PAIRS = new HashMap<Character, Character>();

    static {
        PAIRS.put('(', ')');
        PAIRS.put('[', ']');
        PAIRS.put('{', '}');
        PAIRS.put('"', '"');
        PAIRS.put('\'', '\'');
        PAIRS.put('`', '`');
    }

    private final EditText mEditor;
    private boolean mApplying = false;
    private int mPendingStart = -1;
    private char mPendingOpen = 0;

    public CBracketAutoComplete(EditText editor) {
        mEditor = editor;
        mEditor.addTextChangedListener(this);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (mApplying) return;
        if (before != 0 || count != 1) return;
        char c = s.charAt(start);
        if (!PAIRS.containsKey(c)) return;
        mPendingStart = start;
        mPendingOpen = c;
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (mApplying) return;
        if (mPendingStart < 0) return;
        char open = mPendingOpen;
        int pos = mPendingStart;
        mPendingStart = -1;
        mPendingOpen = 0;
        if (pos >= s.length()) return;
        if (s.charAt(pos) != open) return;
        if (mEditor.getSelectionStart() != pos + 1) return;
        char close = PAIRS.get(open);
        mApplying = true;
        s.insert(pos + 1, String.valueOf(close));
        mEditor.setSelection(pos + 1);
        mApplying = false;
    }
}
