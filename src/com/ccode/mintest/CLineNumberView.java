package com.ccode.mintest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

import java.util.HashSet;
import java.util.Set;

public class CLineNumberView extends View {

    private EditText editor;
    private int scrollY;
    private final Set<Integer> breakpoints = new HashSet<Integer>();

    private static final float LINE_NUMBER_SCALE = 0.85f;

    private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint breakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public interface OnBreakpointToggleListener {
        void onBreakpointToggled(int line, boolean added);
    }

    private OnBreakpointToggleListener toggleListener;

    public CLineNumberView(Context context) {
        super(context);
        init();
    }

    public CLineNumberView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint.setColor(0xFF2D2D30);
        numberPaint.setColor(0xFF858585);
        numberPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 12f, getResources().getDisplayMetrics()));
        numberPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        breakPaint.setColor(0xFFE51400);
    }

    public void setEditor(EditText ed) {
        if (editor == ed) return;
        editor = ed;
        if (editor != null) {
            editor.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(Editable s) { invalidate(); }
            });
            editor.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                @Override
                public void onScrollChange(View v, int scrollX, int newScrollY,
                                           int oldScrollX, int oldScrollY) {
                    scrollY = newScrollY;
                    invalidate();
                }
            });
            updateNumberPaintSize(editor.getTextSize());
        }
        invalidate();
    }

    public void updateNumberPaintSize(float codeTextSizePx) {
        numberPaint.setTextSize(codeTextSizePx * LINE_NUMBER_SCALE);
        invalidate();
    }

    public Set<Integer> getBreakpoints() {
        return new HashSet<Integer>(breakpoints);
    }

    public void setBreakpoints(Set<Integer> lines) {
        breakpoints.clear();
        if (lines != null) {
            for (Integer l : lines) {
                if (l != null && l >= 1) breakpoints.add(l);
            }
        }
        invalidate();
    }

    public void setOnBreakpointToggleListener(OnBreakpointToggleListener l) {
        toggleListener = l;
    }

    public boolean toggleBreakpoint(int line) {
        boolean added;
        if (breakpoints.contains(line)) {
            breakpoints.remove(line);
            added = false;
        } else {
            breakpoints.add(line);
            added = true;
        }
        invalidate();
        if (toggleListener != null) toggleListener.onBreakpointToggled(line, added);
        return added;
    }

    public void setBreakpointSilently(int line, boolean add) {
        if (add) {
            breakpoints.add(line);
        } else {
            breakpoints.remove(line);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
        if (editor == null) return;
        android.text.Layout layout = editor.getLayout();
        if (layout == null) return;
        Editable text = editor.getText();
        if (text == null) return;

        int lineHeight = editor.getLineHeight();
        int total = text.length();
        int offset = 0;
        int logicalLine = 1;
        String full = text.toString();
        while (offset <= total) {
            int nl = full.indexOf('\n', offset);
            int lineEnd = (nl < 0) ? total : nl;
            if (lineEnd < offset) break;
            int layoutLine = layout.getLineForOffset(offset);
            int top = layout.getLineTop(layoutLine) + editor.getPaddingTop() - scrollY;
            int bottom = top + lineHeight;
            if (bottom >= 0 && top <= getHeight()) {
                Paint.FontMetrics fm = numberPaint.getFontMetrics();
                float baseline = top + (lineHeight - fm.ascent - fm.descent) / 2f;
                String num = String.valueOf(logicalLine);
                float x = getWidth() - numberPaint.measureText(num) - dp(8);
                canvas.drawText(num, x, baseline, numberPaint);
                if (breakpoints.contains(logicalLine)) {
                    float cy = top + lineHeight / 2f;
                    canvas.drawCircle(dp(10), cy, dp(4), breakPaint);
                }
            }
            if (nl < 0) break;
            offset = nl + 1;
            logicalLine++;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN && editor != null) {
            android.text.Layout layout = editor.getLayout();
            if (layout != null) {
                int layoutLine = layout.getLineForVertical(scrollY + (int) e.getY());
                int startOffset = layout.getLineStart(layoutLine);
                int logicalLine = logicalLineOfOffset(editor.getText() == null
                        ? "" : editor.getText().toString(), startOffset);
                toggleBreakpoint(logicalLine);
            }
            return true;
        }
        return super.onTouchEvent(e);
    }

    private static int logicalLineOfOffset(String text, int offset) {
        int line = 1;
        int n = Math.min(offset, text.length());
        for (int i = 0; i < n; i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
