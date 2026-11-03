package com.ccode.mintest;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.EditText;

public class CCodeEditText extends EditText {

    private static final float LINE_NUMBER_WIDTH_DP = 44f;

    public CCodeEditText(Context context) {
        super(context);
    }

    public CCodeEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CCodeEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                && event.getX() <= dp(LINE_NUMBER_WIDTH_DP)) {
            return false;
        }
        return super.onTouchEvent(event);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
