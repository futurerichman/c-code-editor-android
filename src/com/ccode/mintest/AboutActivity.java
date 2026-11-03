package com.ccode.mintest;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

public class AboutActivity extends Activity {

    private static final String FALLBACK_VERSION = "v1.14-UI";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        TextView tvVersion = findViewById(R.id.tvAboutVersion);
        tvVersion.setText(readVersionName());
    }

    private String readVersionName() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (pi.versionName != null && pi.versionName.length() > 0) {
                return "v" + pi.versionName;
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return FALLBACK_VERSION;
    }
}
