package com.ccode.mintest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.LocalServerSocket;
import android.net.LocalSocketAddress;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int REQ_OPEN = 1001;
    private static final int REQ_SAVE = 1002;
    private static final int REQ_BG = 1003;
    private static final int MAX_FILE_BYTES = 5 * 1024 * 1024;

    private static final String KEY_TOOLBAR_COLLAPSED = "toolbar_collapsed";
    private static final String KEY_CONSOLE_COLLAPSED = "console_collapsed";
    private static final String KEY_FILE_NAME = "file_name";
    private static final String KEY_CONSOLE_TEXT = "console_text";
    private static final String KEY_FILE_URI = "file_uri";
    private static final String KEY_BG_LAYER_TEST = "bg_layer_test";
    private static final float CODE_TOP_ELEVATION = 50f;
    private static final String PREF_FONT_SP = "font_sp";
    private static final String PREF_BREAKPOINTS = "breakpoints";

    private static final String BG_FILE_NAME = "bg.jpg";
    private static final int MAX_BG_BYTES = 5 * 1024 * 1024;
    private static final String BG_VIDEO_FILE_NAME = "bg.mp4";
    private static final int MAX_BG_VIDEO_BYTES = 50 * 1024 * 1024;
    private static final String PREF_BG_OPACITY = "bg_opac";
    private static final int DEFAULT_BG_OPACITY = 50;

    private static final String C_TEMPLATE =
            "#include <stdio.h>\n"
                    + "int main(){\n"
                    + "return 0;\n"
                    + "}\n";

    private static final String[] C_SYMBOLS = {
            "{", "}", "(", ")", "[", "]", ";",
            "\"", "'", "#", "<", ">", "=",
            "+", "-", "*", "/", "%", "!",
            "&", "|", "?", ":", ","
    };

    private EditText etCode;
    private EditText etInput;
    private TextView tvFile;
    private TextView tvConsole;
    private ScrollView scConsole;
    private LinearLayout toolbar;
    private LinearLayout toolbarCollapsed;
    private LinearLayout console;
    private LinearLayout consoleCollapsedView;
    private TextView btnToggleConsole;
    private Button btnRun;
    private LinearLayout inputBar;
    private CLineNumberView lineNumbers;

    private boolean consoleCollapsed;

    private String currentFileName = "untitled.c";
    private Uri currentUri;
    private float startSp;

    private final RunEngine engine = new RunEngine();
    private boolean running;
    private boolean assetsReady;

    private CSyntaxHighlighter highlighter;
    private CAutoIndent indenter;
    private Button btnStepToolbar;
    private CBracketAutoComplete bracketCompleter;

    private FrameLayout editorContainer;
    private ImageView ivBg;
    private TextureView vvBg;
    private TextureView vvBgFull;
    private MediaPlayer bgPlayer;
    private boolean bgVideoStarted;
    private boolean bgLayerTestOn;
    private FrameLayout rootFrame;
    private ViewGroup codeParent;
    private int codeIndex = -1;
    private ViewGroup.LayoutParams codeLp;
    private int bgOpacity = DEFAULT_BG_OPACITY;

    private static final String VIDEO_MODE_FULL = "full";
    private static final String VIDEO_MODE_INLINE = "inline";
    private String bgVideoMode = VIDEO_MODE_FULL;
    private static final String PREF_VIDEO_MODE = "bg_video_mode";

    private boolean bgOpacityDialogPending;
    private int bgVideoWidth, bgVideoHeight;

    private LinearLayout debugBar;
    private Button btnStep;
    private Button btnContinue;
    private Button btnBreak;
    private Button btnStopDebug;
    private TextView tvDebugStatus;
    private boolean debugging = false;
    private LocalServerSocket dbgServer;
    private BackgroundColorSpan dbgLineSpan;

    @SuppressWarnings("deprecation")
    private Uri restoreFileUri(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= 33) {
            return savedInstanceState.getParcelable(KEY_FILE_URI, Uri.class);
        }
        return savedInstanceState.getParcelable(KEY_FILE_URI);
    }

    private void setupEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);
            if (Build.VERSION.SDK_INT >= 28) {
                android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.layoutInDisplayCutoutMode =
                        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                getWindow().setAttributes(lp);
            }
        }
        getWindow().setStatusBarColor(0x00000000);
        getWindow().setNavigationBarColor(0x00000000);
    }

    private void applySystemBarInsets() {
        applyInsetPadding(findViewById(R.id.toolbar), true);
        applyInsetPadding(findViewById(R.id.toolbarCollapsed), true);
        applyInsetPadding(findViewById(R.id.symbolRow), false);
        if (getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            applyInsetPadding(findViewById(R.id.consoleTitleBar), true);
        }
    }

    private void applyInsetPadding(final View v, final boolean topSide) {
        if (v == null) return;
        final int baseLeft = v.getPaddingLeft();
        final int baseTop = v.getPaddingTop();
        final int baseRight = v.getPaddingRight();
        final int baseBottom = v.getPaddingBottom();
        v.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public android.view.WindowInsets onApplyWindowInsets(View view, android.view.WindowInsets insets) {
                int left, top, right, bottom;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets ime = insets.getInsets(
                            android.view.WindowInsets.Type.ime());
                    android.graphics.Insets sys = insets.getInsets(
                            android.view.WindowInsets.Type.systemBars());
                    left = sys.left;
                    top = sys.top;
                    right = sys.right;
                    if (insets.isVisible(android.view.WindowInsets.Type.ime())) {
                        View root = view.getRootView();
                        int screenH = view.getResources().getDisplayMetrics().heightPixels;
                        int rootH = root != null ? root.getHeight() : 0;
                        boolean windowResized = rootH > 0 && (screenH - rootH) >= ime.bottom / 2;
                        bottom = windowResized ? sys.bottom : ime.bottom;
                    } else {
                        bottom = sys.bottom;
                    }
                } else {
                    left = insets.getSystemWindowInsetLeft();
                    top = insets.getSystemWindowInsetTop();
                    right = insets.getSystemWindowInsetRight();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                if (topSide) {
                    view.setPadding(baseLeft + left, baseTop + top, baseRight + right, baseBottom);
                } else {
                    view.setPadding(baseLeft + left, baseTop, baseRight + right, baseBottom + bottom);
                }
                return insets;
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupEdgeToEdge();
        setContentView(R.layout.activity_main);

        etCode = findViewById(R.id.etCode);
        tvFile = findViewById(R.id.tvFile);
        tvFile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showRenameDialog(); }
        });
        tvConsole = findViewById(R.id.tvConsole);
        scConsole = findViewById(R.id.scConsole);
        toolbar = findViewById(R.id.toolbar);
        toolbarCollapsed = findViewById(R.id.toolbarCollapsed);
        console = findViewById(R.id.console);
        consoleCollapsedView = findViewById(R.id.consoleCollapsed);
        btnToggleConsole = findViewById(R.id.btnToggleConsole);
        final Button btnFile = findViewById(R.id.btnFile);
        btnRun = findViewById(R.id.btnRun);
        btnStepToolbar = findViewById(R.id.btnStepToolbar);
        inputBar = findViewById(R.id.inputBar);
        etInput = findViewById(R.id.etInput);
        final Button btnSend = findViewById(R.id.btnSend);
        final TextView btnToggleToolbar = findViewById(R.id.btnToggleToolbar);
        final TextView btnShowToolbar = findViewById(R.id.btnShowToolbar);
        final TextView btnShowConsole = findViewById(R.id.btnShowConsole);
        final TextView btnMore = findViewById(R.id.btnMore);

        debugBar = findViewById(R.id.debugBar);
        btnStep = findViewById(R.id.btnStep);
        btnContinue = findViewById(R.id.btnContinue);
        btnBreak = findViewById(R.id.btnBreak);
        btnStopDebug = findViewById(R.id.btnStopDebug);
        tvDebugStatus = findViewById(R.id.tvDebugStatus);

        applySystemBarInsets();

        btnToggleToolbar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setToolbarCollapsed(true);
            }
        });
        btnShowToolbar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setToolbarCollapsed(false);
            }
        });

        btnToggleConsole.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setConsoleCollapsed(!consoleCollapsed);
            }
        });
        btnShowConsole.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setConsoleCollapsed(false);
            }
        });

        btnFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFileMenu(v);
            }
        });

        btnMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMoreMenu(v);
            }
        });

        btnRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!assetsReady) {
                    appendLog("正在准备编译环境（首次启动需解压 TCC，约几秒）…");
                    return;
                }
                if (running) {
                    engine.stop();
                    appendLog("■ 已停止运行");
                    return;
                }
                String code = etCode.getText().toString();
                if (code.trim().isEmpty()) {
                    Toast.makeText(MainActivity.this,
                            "请先输入代码", Toast.LENGTH_SHORT).show();
                    return;
                }
                File src = new File(getFilesDir(), "run_main.c");
                try {
                    FileOutputStream fos = new FileOutputStream(src);
                    fos.write(code.getBytes(StandardCharsets.UTF_8));
                    fos.close();
                } catch (IOException e) {
                    appendLog("写入临时文件失败：" + e.getMessage());
                    return;
                }

                setConsoleCollapsed(false);
                appendLog("▶ 开始编译运行…");
                running = true;
                btnRun.setText("停止");
                inputBar.setVisibility(View.VISIBLE);
                etInput.requestFocus();

                engine.run(EnsureAssets.resolveTccBin(MainActivity.this),
                        EnsureAssets.resolveSysroot(MainActivity.this),
                        EnsureAssets.resolveRuntimeDir(MainActivity.this),
                        src, new RunEngine.Callback() {
                            @Override
                            public void onOutput(final String text, final boolean isError) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        appendColored(text, isError ? 0xFFF48771 : 0xFFD4D4D4);
                                    }
                                });
                            }

                            @Override
                            public void onExit(final int code) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        running = false;
                                        btnRun.setText("运行");
                                        inputBar.setVisibility(View.GONE);
                                        appendColored("[进程结束，退出码 " + code + "]\n",
                                                0xFF6A9955);
                                    }
                                });
                            }

                            @Override
                            public void onError(final String message) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        running = false;
                                        btnRun.setText("运行");
                                        inputBar.setVisibility(View.GONE);
                                        appendColored("运行出错：" + message + "\n", 0xFFF48771);
                                        appendGuideIfExecBlocked(message);
                                    }
                                });
                            }
                        });
            }
        });

        btnRun.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (debugging) {
                    stopDebug();
                } else if (running) {
                    engine.stop();
                    appendLog("■ 已停止运行");
                } else {
                    startDebug();
                }
                return true;
            }
        });

        btnStepToolbar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (debugging) {
                    setConsoleCollapsed(false);
                    engine.sendDebugCommand("STEP\n");
                } else if (running) {
                    engine.stop();
                    appendLog("■ 已停止运行");
                } else {
                    startDebug();
                }
            }
        });

        btnStep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setConsoleCollapsed(false);
                engine.sendDebugCommand("STEP\n");
            }
        });
        btnContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                engine.sendDebugCommand("CONT\n");
                tvDebugStatus.setText("▶ 继续运行中（遇断点或结束才再暂停）");
            }
        });
        btnBreak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setBreakpointAtCursor();
            }
        });
        btnStopDebug.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopDebug();
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendProgramInput(etInput);
            }
        });
        etInput.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(android.widget.TextView v, int actionId,
                                          android.view.KeyEvent event) {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    sendProgramInput(etInput);
                    return true;
                }
                return false;
            }
        });

        EnsureAssets.ensure(MainActivity.this, new EnsureAssets.Done() {
            @Override
            public void run() {
                assetsReady = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        appendLog("编译环境就绪，点击「运行」即可执行代码。");
                    }
                });
            }
        });

        if (savedInstanceState != null) {
            setToolbarCollapsed(savedInstanceState.getBoolean(KEY_TOOLBAR_COLLAPSED, false));
            setConsoleCollapsed(savedInstanceState.getBoolean(KEY_CONSOLE_COLLAPSED, false));
            currentFileName = savedInstanceState.getString(KEY_FILE_NAME, "untitled.c");
            tvFile.setText(currentFileName);
            currentUri = restoreFileUri(savedInstanceState);
            String ct = savedInstanceState.getString(KEY_CONSOLE_TEXT);
            if (ct != null) tvConsole.setText(ct);
        }

        setupSymbolBar();
        setupLineNumbers();
        setupPinchZoom();
        setupBackground();

        if (getBgVideoFile().exists()) {
            editorContainer.post(new Runnable() {
                @Override
                public void run() {
                    TextureView active = activeVideoView();
                    if (active == null) return;
                    if (editorContainer.getWidth() <= 0 || editorContainer.getHeight() <= 0) {
                        editorContainer.post(this);
                        return;
                    }
                    applyVideoTopLayer();
                }
            });
        }

        highlighter = new CSyntaxHighlighter(etCode);
        highlighter.rehighlightAll();
        indenter = new CAutoIndent(etCode, highlighter);
        bracketCompleter = new CBracketAutoComplete(etCode);

        if (savedInstanceState == null) {
            fillCTemplate();
        }
    }

    private void setupLineNumbers() {
        lineNumbers = findViewById(R.id.lineNumbers);
        if (lineNumbers == null) return;
        lineNumbers.setEditor(etCode);

        SharedPreferences prefs = getSharedPreferences("ccode", MODE_PRIVATE);
        Set<String> saved = prefs.getStringSet(PREF_BREAKPOINTS, null);
        if (saved != null) {
            Set<Integer> bps = new HashSet<Integer>();
            for (String s : saved) {
                try {
                    bps.add(Integer.parseInt(s));
                } catch (NumberFormatException ignore) {
                }
            }
            lineNumbers.setBreakpoints(bps);
        }

        lineNumbers.setOnBreakpointToggleListener(
                new CLineNumberView.OnBreakpointToggleListener() {
                    @Override
                    public void onBreakpointToggled(int line, boolean added) {
                        persistBreakpoints();
                        if (added && debugging) {
                            engine.sendDebugCommand("BREAK " + line + "\n");
                        }
                        tvDebugStatus.setText((added ? "🔴 已设断点：第 " : "已移除断点：第 ")
                                + line + " 行");
                        appendLog((added ? "已设断点：第 " : "已移除断点：第 ")
                                + line + " 行");
                    }
                });
    }

    private void persistBreakpoints() {
        Set<String> s = new HashSet<String>();
        if (lineNumbers != null) {
            for (Integer i : lineNumbers.getBreakpoints()) {
                s.add(String.valueOf(i));
            }
        }
        getSharedPreferences("ccode", MODE_PRIVATE)
                .edit().putStringSet(PREF_BREAKPOINTS, s).apply();
    }

    private void clearBreakpoints() {
        if (lineNumbers != null) {
            lineNumbers.setBreakpoints(new HashSet<Integer>());
        }
        persistBreakpoints();
    }

    private void setupSymbolBar() {
        LinearLayout row = findViewById(R.id.symbolRow);
        for (final String sym : C_SYMBOLS) {
            TextView t = new TextView(this);
            t.setText(sym);
            t.setTextSize(16);
            t.setTextColor(0xFF9CDCFE);
            t.setPadding(dp(11), dp(6), dp(11), dp(6));
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    insertAtCursor(sym);
                }
            });
            row.addView(t);
        }
    }

    private void insertAtCursor(String text) {
        int pos = etCode.getSelectionStart();
        if (pos < 0) pos = etCode.length();
        etCode.getText().insert(pos, text);
        etCode.setSelection(pos + text.length());
    }

    private void setupPinchZoom() {
        final SharedPreferences prefs = getSharedPreferences("ccode", MODE_PRIVATE);
        final float saved = prefs.getFloat(PREF_FONT_SP, 14f);
        etCode.setTextSize(TypedValue.COMPLEX_UNIT_SP, saved);

        final ScaleGestureDetector detector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector d) {
                        startSp = etCode.getTextSize()
                                / TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f,
                                        getResources().getDisplayMetrics());
                        return true;
                    }

                    @Override
                    public boolean onScale(ScaleGestureDetector d) {
                        float ns = startSp * d.getScaleFactor();
                        ns = Math.max(10f, Math.min(30f, ns));
                        etCode.setTextSize(TypedValue.COMPLEX_UNIT_SP, ns);
                        if (lineNumbers != null) lineNumbers.updateNumberPaintSize(etCode.getTextSize());
                        startSp = ns;
                        prefs.edit().putFloat(PREF_FONT_SP, ns).apply();
                        return true;
                    }
                });

        etCode.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent e) {
                detector.onTouchEvent(e);
                return detector.isInProgress();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_TOOLBAR_COLLAPSED,
                toolbar.getVisibility() == View.GONE);
        outState.putBoolean(KEY_CONSOLE_COLLAPSED, consoleCollapsed);
        outState.putString(KEY_FILE_NAME, currentFileName);
        outState.putString(KEY_CONSOLE_TEXT, tvConsole.getText().toString());
        if (currentUri != null) outState.putParcelable(KEY_FILE_URI, currentUri);
        outState.putBoolean(KEY_BG_LAYER_TEST, bgLayerTestOn);
    }

    private void setToolbarCollapsed(boolean collapsed) {
        toolbar.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        toolbarCollapsed.setVisibility(collapsed ? View.VISIBLE : View.GONE);
    }

    private void setConsoleCollapsed(boolean collapsed) {
        consoleCollapsed = collapsed;
        if (collapsed) {
            console.setVisibility(View.GONE);
            consoleCollapsedView.setVisibility(View.VISIBLE);
            btnToggleConsole.setText("展开");
            hideKeyboard();
        } else {
            console.setVisibility(View.VISIBLE);
            consoleCollapsedView.setVisibility(View.GONE);
            btnToggleConsole.setText("收起");
            scConsole.post(new Runnable() {
                @Override
                public void run() {
                    scConsole.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager)
                        getSystemService(INPUT_METHOD_SERVICE);
        if (imm == null) return;
        View cur = getCurrentFocus();
        if (cur != null) {
            imm.hideSoftInputFromWindow(cur.getWindowToken(), 0);
        }
    }

    @Override
    protected void onDestroy() {
        if (running) engine.stop();
        stopBgVideo();
        if (highlighter != null) highlighter.detach();
        if (indenter != null) indenter.detach();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        if (requestCode == REQ_OPEN) {
            byte[] bytes = readAll(uri);
            if (bytes == null) {
                appendLog("打开失败：无法读取文件（可能超过 5MB 或已损坏）");
                Toast.makeText(this, "打开失败", Toast.LENGTH_SHORT).show();
                return;
            }
            String text = decode(bytes);
            etCode.setText(text);
            currentFileName = guessName(uri);
            currentUri = uri;
            takePersist(uri);
            tvFile.setText(currentFileName);
            appendLog("打开文件：" + currentFileName + "（" + (bytes.length / 1024) + " KB）");
            Toast.makeText(this, "已打开 " + currentFileName, Toast.LENGTH_SHORT).show();
            clearBreakpoints();
            if (isBlankText(text) && isCFileName(currentFileName)) {
                fillCTemplate();
                appendLog("检测到空白 .c 文件，已填充代码模板");
            }
        } else if (requestCode == REQ_SAVE) {
            currentUri = uri;
            takePersist(uri);
            currentFileName = guessName(uri);
            tvFile.setText(currentFileName);
            writeCurrentTo(uri);
        } else if (requestCode == REQ_BG) {
            handleBgPick(uri);
        }
    }

    private void showFileMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.inflate(R.menu.menu_file);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.action_new) {
                    newFile();
                    return true;
                }
                if (id == R.id.action_open) {
                    openFile();
                    return true;
                }
                if (id == R.id.action_save) {
                    saveCurrentFile();
                    return true;
                }
                return false;
            }
        });
        menu.show();
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.inflate(R.menu.menu_more);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.action_bg) {
                    openBgPicker();
                    return true;
                }
                if (id == R.id.action_bg_opacity) {
                    if (getBgFile().exists() || getBgVideoFile().exists()) {
                        showOpacityDialog();
                    } else {
                        Toast.makeText(MainActivity.this,
                                "尚未设置背景，请先选择「设置背景」",
                                Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                if (id == R.id.action_bg_video_mode) {
                    showVideoModeDialog();
                    return true;
                }
                if (id == R.id.action_bg_layer_test) {
                    TextureView active = activeVideoView();
                    if (active == null || active.getVisibility() != View.VISIBLE) {
                        Toast.makeText(MainActivity.this,
                                "当前没有可见的视频背景，请先设置视频",
                                Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    if (!bgLayerTestOn) {
                        applyVideoTopLayer();
                        appendLog("视频背景图层设置：视频已置顶，代码文字保持在视频之上；再点一次还原。");
                    } else {
                        restoreVideoLayer();
                        bgLayerTestOn = false;
                        appendLog("视频背景图层设置：已还原图层顺序");
                    }
                    return true;
                }
                if (id == R.id.action_about) {
                    startActivity(new Intent(MainActivity.this, AboutActivity.class));
                    return true;
                }
                return false;
            }
        });
        menu.show();
    }

    private void fillCTemplate() {
        etCode.setText(C_TEMPLATE);
        etCode.setSelection(templateCaretOffset());
    }

    private static int templateCaretOffset() {
        int brace = C_TEMPLATE.indexOf('{');
        return brace < 0 ? C_TEMPLATE.length() : brace + 1;
    }

    private static boolean isBlankText(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean isCFileName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".c");
    }

    private void showRenameDialog() {
        final EditText input = new EditText(this);
        input.setText(currentFileName);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("重命名代码文件");
        b.setView(input);
        b.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) {
                    Toast.makeText(MainActivity.this, "名称不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                currentFileName = name;
                tvFile.setText(name);
                appendLog("已重命名为 " + name);
                Toast.makeText(MainActivity.this, "已重命名为 " + name, Toast.LENGTH_SHORT).show();
            }
        });
        b.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { dialog.dismiss(); }
        });
        b.show();
    }

    private void newFile() {
        fillCTemplate();
        currentFileName = "untitled.c";
        currentUri = null;
        tvFile.setText(currentFileName);
        clearBreakpoints();
        appendLog("新建：已填充代码模板（untitled.c）");
    }

    private void openFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_OPEN);
    }

    private void saveCurrentFile() {
        if (currentUri != null) {
            writeCurrentTo(currentUri);
            return;
        }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TITLE, currentFileName);
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_SAVE);
    }

    private void writeCurrentTo(Uri uri) {
        try {
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os == null) {
                appendLog("保存失败：无法写入目标位置");
                return;
            }
            os.write(etCode.getText().toString().getBytes(StandardCharsets.UTF_8));
            os.close();
            appendLog("已保存：" + currentFileName);
            Toast.makeText(this, "已保存 " + currentFileName, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            appendLog("保存失败：" + (e.getMessage() == null ? "IO 错误" : e.getMessage()));
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void takePersist(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {
        }
    }

    private byte[] readAll(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > MAX_FILE_BYTES) {
                    in.close();
                    return null;
                }
            }
            in.close();
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private String decode(byte[] data) {
        CharsetDecoder d = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return d.decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            try {
                return new String(data, "GB18030");
            } catch (Exception e2) {
                return new String(data, StandardCharsets.UTF_8);
            }
        }
    }

    private String guessName(Uri uri) {
        String name = null;
        try {
            android.database.Cursor c = getContentResolver().query(uri,
                    new String[]{"_display_name"}, null, null, null);
            if (c != null) {
                if (c.moveToFirst()) name = c.getString(0);
                c.close();
            }
        } catch (Exception ignored) {
        }
        if (name == null) {
            String p = uri.getLastPathSegment();
            if (p != null && p.contains("/")) name = p.substring(p.lastIndexOf('/') + 1);
            else name = p;
        }
        if (name == null || name.isEmpty()) name = "打开的文件";
        return name;
    }

    private void appendGuideIfExecBlocked(String message) {
        String m = message == null ? "" : message;
        boolean blocked = m.contains("Permission denied")
                || m.contains("error=13")
                || m.contains("EACCES");
        if (!blocked) return;
        appendColored("检测到执行权限受限，可换用成熟编译器：\n", 0xFFCE9178);
        appendColored("① Termux（免费开源）：安装后依次执行\n"
                + "   pkg install clang tcc\n"
                + "   然后编译你的代码：clang 文件名.c -o 程序 && ./程序\n", 0xFF9CDCFE);
        appendColored("② C4droid（应用商店搜索）：自带编译器，无需折腾权限。\n", 0xFF9CDCFE);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void setupBackground() {
        editorContainer = findViewById(R.id.editorContainer);
        ivBg = findViewById(R.id.ivBg);
        vvBg = findViewById(R.id.vvBg);
        vvBgFull = findViewById(R.id.vvBgFull);

        ViewGroup content = (ViewGroup) findViewById(android.R.id.content);
        rootFrame = (content != null && content.getChildCount() > 0)
                ? (FrameLayout) content.getChildAt(0) : null;
        codeParent = (ViewGroup) etCode.getParent();
        codeIndex = codeParent != null ? codeParent.indexOfChild(etCode) : -1;
        codeLp = etCode.getLayoutParams();

        bgOpacity = getSharedPreferences("ccode", MODE_PRIVATE)
                .getInt(PREF_BG_OPACITY, DEFAULT_BG_OPACITY);
        if (bgOpacity < 0) bgOpacity = 0;
        if (bgOpacity > 100) bgOpacity = 100;

        bgVideoMode = getSharedPreferences("ccode", MODE_PRIVATE)
                .getString(PREF_VIDEO_MODE, VIDEO_MODE_FULL);
        if (bgVideoMode == null
                || (!bgVideoMode.equals(VIDEO_MODE_FULL)
                && !bgVideoMode.equals(VIDEO_MODE_INLINE))) {
            bgVideoMode = VIDEO_MODE_FULL;
        }

        vvBg.setSurfaceTextureListener(makeInlineListener());
        vvBgFull.setSurfaceTextureListener(makeFullListener());

        if (getBgVideoFile().exists()) {
            editorContainer.post(new Runnable() {
                @Override
                public void run() {
                    applyBgVideo();
                }
            });
        } else if (getBgFile().exists()) {
            editorContainer.post(new Runnable() {
                @Override
                public void run() {
                    applyBgBitmap();
                }
            });
        }
    }

    private File getBgFile() {
        return new File(getFilesDir(), BG_FILE_NAME);
    }

    private File getBgVideoFile() {
        return new File(getFilesDir(), BG_VIDEO_FILE_NAME);
    }

    private void openBgPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"image/*", "video/*"});
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_BG);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "未找到可用的文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleBgPick(Uri uri) {
        String mime = null;
        try {
            mime = getContentResolver().getType(uri);
        } catch (Exception ignored) {
        }
        if (mime != null && mime.startsWith("video/")) {
            handleVideoPick(uri);
            return;
        }
        if (mime != null && mime.startsWith("image/")) {
            handleImagePick(uri);
            return;
        }
        String name = guessName(uri).toLowerCase(Locale.ROOT);
        if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm")
                || name.endsWith(".3gp") || name.endsWith(".mov") || name.endsWith(".avi")) {
            handleVideoPick(uri);
        } else {
            handleImagePick(uri);
        }
    }

    private void handleImagePick(Uri uri) {
        File tmp = new File(getFilesDir(), BG_FILE_NAME + ".tmp");
        InputStream in = null;
        OutputStream out = null;
        boolean ok = false;
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) {
                Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show();
                return;
            }
            out = new FileOutputStream(tmp);
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_BG_BYTES) {
                    Toast.makeText(this, "图片过大（上限 5MB），请选择更小的图片",
                            Toast.LENGTH_SHORT).show();
                    break;
                }
                out.write(buf, 0, n);
            }
            out.flush();
            ok = total > 0 && total <= MAX_BG_BYTES;
        } catch (IOException e) {
            Toast.makeText(this, "导入背景图失败：" + (e.getMessage() == null
                    ? "IO 错误" : e.getMessage()), Toast.LENGTH_SHORT).show();
        } finally {
            closeQuietly(out);
            closeQuietly(in);
        }
        if (!ok) {
            tmp.delete();
            return;
        }
        File dst = getBgFile();
        if (!replaceBgFile(tmp, dst)) {
            appendLog("背景图保存失败，请重试");
            return;
        }
        File oldVideo = getBgVideoFile();
        if (oldVideo.exists()) oldVideo.delete();
        stopBgVideo();
        applyBgBitmap();
        if (ivBg.getVisibility() == View.VISIBLE) {
            appendLog("已设置代码区背景图（" + (dst.length() / 1024) + " KB）");
            showOpacityDialog();
        } else {
            appendLog("背景图解码失败，请换一张图片试试");
            dst.delete();
        }
    }

    private void handleVideoPick(Uri uri) {
        File tmp = new File(getFilesDir(), BG_VIDEO_FILE_NAME + ".tmp");
        InputStream in = null;
        OutputStream out = null;
        boolean ok = false;
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) {
                Toast.makeText(this, "读取视频失败", Toast.LENGTH_SHORT).show();
                return;
            }
            out = new FileOutputStream(tmp);
            byte[] buf = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_BG_VIDEO_BYTES) {
                    Toast.makeText(this, "视频过大（上限 50MB），请选择更小的视频",
                            Toast.LENGTH_SHORT).show();
                    break;
                }
                out.write(buf, 0, n);
            }
            out.flush();
            ok = total > 0 && total <= MAX_BG_VIDEO_BYTES;
        } catch (IOException e) {
            Toast.makeText(this, "导入背景视频失败：" + (e.getMessage() == null
                    ? "IO 错误" : e.getMessage()), Toast.LENGTH_SHORT).show();
        } finally {
            closeQuietly(out);
            closeQuietly(in);
        }
        if (!ok) {
            tmp.delete();
            return;
        }
        File dst = getBgVideoFile();
        if (!replaceBgFile(tmp, dst)) {
            appendLog("背景视频保存失败，请重试");
            return;
        }
        File oldImg = getBgFile();
        if (oldImg.exists()) oldImg.delete();
        ivBg.setImageDrawable(null);
        ivBg.setVisibility(View.GONE);
        probeVideo(dst);
        bgOpacityDialogPending = true;
        releaseBgPlayer();
        applyBgVideo();
        if (getBgVideoFile().exists()) {
            editorContainer.post(new Runnable() {
                @Override
                public void run() {
                    TextureView active = activeVideoView();
                    if (active == null) return;
                    if (editorContainer.getWidth() <= 0 || editorContainer.getHeight() <= 0) {
                        editorContainer.post(this);
                        return;
                    }
                    applyVideoTopLayer();
                }
            });
        }
    }

    private void applyBgVideo() {
        File f = getBgVideoFile();
        if (!f.exists()) return;
        bgVideoStarted = false;
        bgLayerTestOn = false;
        if (editorContainer != null) restoreCodeFromRoot();
        if (bgVideoMode.equals(VIDEO_MODE_FULL)) {
            vvBg.setVisibility(View.GONE);
            vvBgFull.setVisibility(View.VISIBLE);
            if (vvBgFull.isAvailable()) {
                if (bgPlayer == null) startBgPlayer(vvBgFull.getSurfaceTexture());
            }
        } else {
            vvBgFull.setVisibility(View.GONE);
            vvBg.setVisibility(View.VISIBLE);
            if (vvBg.isAvailable()) {
                if (bgPlayer == null) startBgPlayer(vvBg.getSurfaceTexture());
            }
        }
    }

    private TextureView activeVideoView() {
        if (vvBgFull == null) return null;
        return bgVideoMode.equals(VIDEO_MODE_FULL) ? vvBgFull : vvBg;
    }

    private void startBgPlayer(SurfaceTexture st) {
        File f = getBgVideoFile();
        if (!f.exists()) return;
        releaseBgPlayer();
        final TextureView active = activeVideoView();
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(f.getAbsolutePath());
            mp.setSurface(new Surface(st));
            mp.setLooping(true);
            mp.setVolume(0f, 0f);
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer p) {
                    if (active != null) {
                        active.setVisibility(View.VISIBLE);
                        active.setAlpha(alphaOf(bgOpacity) / 255f);
                    }
                    p.start();
                    bgVideoStarted = true;
                    appendLog("已设置视频动态背景（" + (f.length() / 1024) + " KB，"
                            + (bgVideoMode.equals(VIDEO_MODE_FULL)
                            ? "全屏模式" : "编辑区模式") + "，循环播放）");
                    if (bgVideoMode.equals(VIDEO_MODE_FULL)) applyVideoTransform();
                    if (bgOpacityDialogPending) {
                        bgOpacityDialogPending = false;
                        showOpacityDialog();
                    }
                }
            });
            mp.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                @Override
                public void onVideoSizeChanged(MediaPlayer p, int width, int height) {
                    bgVideoWidth = width;
                    bgVideoHeight = height;
                    if (bgVideoMode.equals(VIDEO_MODE_FULL)) applyVideoTransform();
                }
            });
            mp.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer p, int what, int extra) {
                    if (what == 3) {
                        bgVideoStarted = true;
                    }
                    return false;
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer p, int what, int extra) {
                    bgVideoStarted = false;
                    appendLog("背景视频解码失败 → " + describeMediaError(what, extra));
                    appendLog("建议用 H.264+AAC 编码、1080p 以下的 mp4 再导入。");
                    releaseBgPlayer();
                    if (active != null) active.setVisibility(View.GONE);
                    File bad = getBgVideoFile();
                    if (bad.exists()) bad.delete();
                    return true;
                }
            });
            bgPlayer = mp;
            mp.prepareAsync();
        } catch (Exception e) {
            bgVideoStarted = false;
            appendLog("背景视频启动异常：" + (e.getMessage() == null
                    ? e.getClass().getSimpleName() : e.getMessage()));
            releaseBgPlayer();
            if (active != null) active.setVisibility(View.GONE);
        }
    }

    private TextureView.SurfaceTextureListener makeFullListener() {
        return new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (bgVideoMode.equals(VIDEO_MODE_FULL) && getBgVideoFile().exists()) {
                    startBgPlayer(surface);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                if (bgVideoMode.equals(VIDEO_MODE_FULL)) applyVideoTransform();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (bgVideoMode.equals(VIDEO_MODE_FULL)) releaseBgPlayer();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        };
    }

    private TextureView.SurfaceTextureListener makeInlineListener() {
        return new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (bgVideoMode.equals(VIDEO_MODE_INLINE) && getBgVideoFile().exists()) {
                    startBgPlayer(surface);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (bgVideoMode.equals(VIDEO_MODE_INLINE)) releaseBgPlayer();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        };
    }

    private void applyVideoTransform() {
        if (vvBgFull == null || bgVideoWidth <= 0 || bgVideoHeight <= 0) return;
        int viewW = vvBgFull.getWidth();
        int viewH = vvBgFull.getHeight();
        if (viewW <= 0 || viewH <= 0) return;
        float videoAspect = (float) bgVideoWidth / (float) bgVideoHeight;
        float viewAspect = (float) viewW / (float) viewH;
        float sx = 1f;
        float sy = 1f;
        if (videoAspect > viewAspect) {
            sx = videoAspect / viewAspect;
        } else {
            sy = viewAspect / videoAspect;
        }
        Matrix m = new Matrix();
        m.setScale(sx, sy, viewW / 2f, viewH / 2f);
        vvBgFull.setTransform(m);
    }

    private void restoreVideoLayer() {
        if (bgVideoMode.equals(VIDEO_MODE_FULL)) {
            ViewGroup root = (ViewGroup) vvBgFull.getParent();
            if (root != null) {
                root.removeView(vvBgFull);
                root.addView(vvBgFull, 0);
            }
        } else {
            ViewGroup ec = (ViewGroup) vvBg.getParent();
            if (ec != null) {
                ec.removeView(vvBg);
                ec.addView(vvBg, 1);
            }
        }
        restoreCodeFromRoot();
    }

    private void applyVideoTopLayer() {
        TextureView active = activeVideoView();
        if (active == null) return;
        active.bringToFront();
        if (bgVideoMode.equals(VIDEO_MODE_FULL)) {
            liftCodeToRoot();
            if (etCode != null) etCode.setElevation(CODE_TOP_ELEVATION);
            keepLineNumbersInUiLayer();
        } else {
            if (etCode != null) etCode.bringToFront();
            if (editorContainer != null) editorContainer.setElevation(CODE_TOP_ELEVATION);
        }
        bgLayerTestOn = true;
    }

    private void keepLineNumbersInUiLayer() {
        if (lineNumbers == null) return;
        if (editorContainer != null && lineNumbers.getParent() != editorContainer) {
            ViewGroup p = (ViewGroup) lineNumbers.getParent();
            if (p != null) p.removeView(lineNumbers);
            editorContainer.addView(lineNumbers);
        }
        lineNumbers.setElevation(0f);
    }

    private void liftCodeToRoot() {
        if (rootFrame == null || etCode == null) return;
        ViewGroup parent = (ViewGroup) etCode.getParent();
        if (parent == rootFrame) {
            etCode.bringToFront();
            return;
        }
        int w = etCode.getWidth();
        int h = etCode.getHeight();
        if (w <= 0 || h <= 0) return;
        codeParent = parent;
        codeIndex = parent != null ? parent.indexOfChild(etCode) : -1;
        codeLp = etCode.getLayoutParams();
        int[] loc = new int[2];
        int[] rootLoc = new int[2];
        etCode.getLocationInWindow(loc);
        rootFrame.getLocationInWindow(rootLoc);
        if (parent != null) parent.removeView(etCode);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(w, h);
        flp.leftMargin = loc[0] - rootLoc[0];
        flp.topMargin = loc[1] - rootLoc[1];
        rootFrame.addView(etCode, flp);
        etCode.bringToFront();
    }

    private void restoreCodeFromRoot() {
        if (etCode == null) return;
        if (rootFrame != null && etCode.getParent() == rootFrame) {
            rootFrame.removeView(etCode);
            if (codeParent != null) {
                int idx = Math.min(codeIndex, codeParent.getChildCount());
                if (codeLp != null) {
                    codeParent.addView(etCode, idx, codeLp);
                } else {
                    codeParent.addView(etCode, idx);
                }
            }
        }
        etCode.setElevation(0f);
        if (editorContainer != null) editorContainer.setElevation(0f);
    }

    private void showVideoModeDialog() {
        final String[] labels = {
                "全屏模式（铺满全屏、不压缩，推荐）",
                "编辑区模式（旧版方式，会随编辑区/旋转改变比例）"};
        int checked = bgVideoMode.equals(VIDEO_MODE_FULL) ? 0 : 1;
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("视频背景模式");
        b.setSingleChoiceItems(labels, checked,
                new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String newMode = which == 0 ? VIDEO_MODE_FULL : VIDEO_MODE_INLINE;
                        if (!newMode.equals(bgVideoMode)) {
                            bgVideoMode = newMode;
                            getSharedPreferences("ccode", MODE_PRIVATE).edit()
                                    .putString(PREF_VIDEO_MODE, newMode).apply();
                            bgLayerTestOn = false;
                            if (getBgVideoFile().exists()) {
                                releaseBgPlayer();
                                applyBgVideo();
                            }
                            appendLog("视频背景模式已切换为："
                                    + (which == 0 ? "全屏模式" : "编辑区模式"));
                        }
                        dialog.dismiss();
                    }
                });
        b.setNegativeButton("取消", null);
        b.show();
    }

    private String describeMediaError(int what, int extra) {
        String whatDesc;
        switch (what) {
            case 1:
                whatDesc = "未知错误(1)";
                break;
            case 2:
                whatDesc = "多媒体框架/服务器错误(2)";
                break;
            default:
                whatDesc = what + "(未定义)";
        }
        String extraDesc;
        switch (extra) {
            case -1004:
                extraDesc = "IO 错误(-1004)";
                break;
            case -1007:
                extraDesc = "文件损坏/格式异常(-1007)";
                break;
            case -1010:
                extraDesc = "编码或格式不受设备支持(-1010)";
                break;
            case -110:
                extraDesc = "解码超时(-110)";
                break;
            default:
                extraDesc = extra + "(未定义)";
        }
        return "what=" + whatDesc + "，extra=" + extraDesc;
    }

    private void probeVideo(File f) {
        if (f == null || !f.exists()) {
            appendLog("视频预检测：文件不存在");
            return;
        }
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(f.getAbsolutePath());
            String mime = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            String dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String hasV = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
            String hasA = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO);
            String w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            StringBuilder sb = new StringBuilder("视频预检测：");
            sb.append("mimetype=").append(mime == null ? "未知" : mime);
            if (dur != null) {
                try {
                    sb.append("，时长=").append(Math.round(Long.parseLong(dur) / 1000.0)).append("s");
                } catch (NumberFormatException ignored) {
                }
            }
            if (w != null && h != null) {
                sb.append("，分辨率=").append(w).append("x").append(h);
            }
            sb.append("，视频轨=").append(isYes(hasV) ? "有" : "无");
            sb.append("，音频轨=").append(isYes(hasA) ? "有" : "无");
            appendLog(sb.toString());
            if (mime == null || !mime.startsWith("video/")) {
                appendLog("预检测警告：不是可识别的视频容器");
            }
        } catch (Exception e) {
            appendLog("视频预检测失败：" + (e.getMessage() == null
                    ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
            }
        }
    }

    private boolean isYes(String v) {
        return v != null && ("yes".equalsIgnoreCase(v) || "1".equals(v));
    }

    private void stopBgVideo() {
        releaseBgPlayer();
        if (vvBg != null) vvBg.setVisibility(View.GONE);
        if (vvBgFull != null) vvBgFull.setVisibility(View.GONE);
    }

    private void releaseBgPlayer() {
        bgVideoStarted = false;
        if (bgPlayer != null) {
            try {
                bgPlayer.release();
            } catch (Exception ignored) {
            }
            bgPlayer = null;
        }
    }

    private boolean replaceBgFile(File tmp, File dst) {
        if (dst.exists()) dst.delete();
        if (tmp.renameTo(dst)) return true;
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new java.io.FileInputStream(tmp);
            out = new FileOutputStream(dst);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            closeQuietly(out);
            closeQuietly(in);
            tmp.delete();
        }
    }

    private static void closeQuietly(Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException ignored) {
        }
    }

    private void applyBgBitmap() {
        File f = getBgFile();
        if (!f.exists() || ivBg == null) return;

        int reqW = editorContainer.getWidth();
        int reqH = editorContainer.getHeight();
        if (reqW <= 0 || reqH <= 0) {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            reqW = dm.widthPixels;
            reqH = dm.heightPixels;
        }

        Bitmap bmp = decodeSampled(f, reqW, reqH);
        if (bmp == null) {
            ivBg.setVisibility(View.GONE);
            return;
        }
        ivBg.setImageBitmap(bmp);
        ivBg.setImageAlpha(alphaOf(bgOpacity));
        ivBg.setVisibility(View.VISIBLE);
    }

    private static Bitmap decodeSampled(File f, int reqW, int reqH) {
        if (reqW <= 0) reqW = 1;
        if (reqH <= 0) reqH = 1;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null;
        opts.inSampleSize = calcInSampleSize(opts.outWidth, opts.outHeight, reqW, reqH);
        opts.inJustDecodeBounds = false;
        try {
            return BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static int calcInSampleSize(int w, int h, int reqW, int reqH) {
        int sample = 1;
        if (h > reqH || w > reqW) {
            int halfW = w / 2;
            int halfH = h / 2;
            while ((halfW / sample) >= reqW && (halfH / sample) >= reqH) {
                sample *= 2;
            }
        }
        return sample < 1 ? 1 : sample;
    }

    private static int alphaOf(int percent) {
        int p = percent < 0 ? 0 : (percent > 100 ? 100 : percent);
        return (int) (p * 255 / 100f);
    }

    private void showOpacityDialog() {
        final int oldOpacity = bgOpacity;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(6));

        final TextView tvVal = new TextView(this);
        tvVal.setText("透明度 " + bgOpacity + "%");
        tvVal.setTextSize(14);
        tvVal.setTextColor(0xFFD4D4D4);
        tvVal.setPadding(dp(2), dp(2), dp(2), dp(8));
        box.addView(tvVal);

        final SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(bgOpacity);
        box.addView(sb);

        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvVal.setText("透明度 " + progress + "%");
                ivBg.setImageAlpha(alphaOf(progress));
                if (vvBg != null) vvBg.setAlpha(alphaOf(progress) / 255f);
                if (vvBgFull != null) vvBgFull.setAlpha(alphaOf(progress) / 255f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("背景透明度")
                .setView(box)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        bgOpacity = sb.getProgress();
                        getSharedPreferences("ccode", MODE_PRIVATE)
                                .edit().putInt(PREF_BG_OPACITY, bgOpacity).apply();
                        ivBg.setImageAlpha(alphaOf(bgOpacity));
                        if (vvBg != null) vvBg.setAlpha(alphaOf(bgOpacity) / 255f);
                        if (vvBgFull != null) vvBgFull.setAlpha(alphaOf(bgOpacity) / 255f);
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        ivBg.setImageAlpha(alphaOf(oldOpacity));
                        if (vvBg != null) vvBg.setAlpha(alphaOf(oldOpacity) / 255f);
                        if (vvBgFull != null) vvBgFull.setAlpha(alphaOf(oldOpacity) / 255f);
                    }
                })
                .setNeutralButton("清除背景", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        clearBackground();
                    }
                })
                .create();
        dialog.show();
    }

    private void clearBackground() {
        File f = getBgFile();
        if (f.exists()) f.delete();
        if (ivBg != null) {
            ivBg.setImageDrawable(null);
            ivBg.setVisibility(View.GONE);
        }
        File vf = getBgVideoFile();
        if (vf.exists()) vf.delete();
        stopBgVideo();
        appendLog("已清除代码区背景（图片/视频）");
        Toast.makeText(this, "已清除背景", Toast.LENGTH_SHORT).show();
    }

    private void sendProgramInput(EditText input) {
        String s = input.getText().toString();
        if (s.isEmpty()) return;
        engine.sendInput(s);
        appendColored("（输入）" + s + "\n", 0xFF9CDCFE);
        input.setText("");
    }

    private void appendColored(String text, int color) {
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        sb.setSpan(new ForegroundColorSpan(color), 0, sb.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvConsole.append(sb);
        scConsole.post(new Runnable() {
            @Override
            public void run() {
                scConsole.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void appendLog(String line) {
        Editable e = tvConsole.getEditableText();
        if (e == null) {
            tvConsole.append(line + "\n");
        } else {
            e.append(line + "\n");
        }
        scConsole.post(new Runnable() {
            @Override
            public void run() {
                scConsole.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void startDebug() {
        if (!assetsReady) {
            appendLog("正在准备编译环境（首次启动需解压 TCC，约几秒）…");
            return;
        }
        String code = etCode.getText().toString();
        if (code.trim().isEmpty()) {
            Toast.makeText(this, "请先输入代码", Toast.LENGTH_SHORT).show();
            return;
        }
        File src = new File(getFilesDir(), "run_debug.c");
        try {
            String combined = CDebugInstrumenter.instrument(code);
            FileOutputStream fos = new FileOutputStream(src);
            fos.write(combined.getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (IOException e) {
            appendLog("写入调试文件失败：" + e.getMessage());
            return;
        }
        try {
            dbgServer = new LocalServerSocket("ccode_dbg");
        } catch (IOException e) {
            appendLog("调试通道创建失败：" + e.getMessage());
            return;
        }
        setConsoleCollapsed(false);
        debugging = true;
        running = true;
        btnRun.setText("停止");
        clearDebugLine();
        showDebugBar(true);
        inputBar.setVisibility(View.VISIBLE);
        etInput.requestFocus();
        appendLog("▶ 开始调试（已逐行插桩）。操作：");
        appendLog("  · 点「步过」逐行执行；点「继续」一直跑到断点或结束；");
        appendLog("  · 把光标放到某行，点「断点」设断点，再点「继续」即在该行暂停。");
        appendLog("  · 每行暂停时会自动显示该行可见变量的值。");
        engine.runDebug(EnsureAssets.resolveTccBin(this),
                EnsureAssets.resolveSysroot(this),
                EnsureAssets.resolveRuntimeDir(this), src, "ccode_dbg", dbgServer,
                new RunEngine.Callback() {
                    @Override
                    public void onOutput(final String text, final boolean isError) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                appendColored(text, isError ? 0xFFF48771 : 0xFFD4D4D4);
                            }
                        });
                    }

                    @Override
                    public void onExit(final int code) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                finishDebug();
                                appendColored("[调试结束，退出码 " + code + "]\n", 0xFF6A9955);
                            }
                        });
                    }

                    @Override
                    public void onError(final String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                finishDebug();
                                appendColored("调试出错：" + message + "\n", 0xFFF48771);
                                appendGuideIfExecBlocked(message);
                            }
                        });
                    }
                },
                new RunEngine.DebugCallback() {
                    @Override
                    public void onDebugLine(final int line) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                highlightDebugLine(line);
                                tvDebugStatus.setText("⏸ 暂停于第 " + line + " 行");
                            }
                        });
                    }

                    @Override
                    public void onDebugVars(final String vars) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (vars != null && !vars.isEmpty()) {
                                    appendColored("  ◈ 变量： " + vars + "\n", 0xFFCE9178);
                                }
                            }
                        });
                    }
                });
    }

    private void finishDebug() {
        debugging = false;
        running = false;
        clearDebugLine();
        showDebugBar(false);
        inputBar.setVisibility(View.GONE);
        if (dbgServer != null) {
            try {
                dbgServer.close();
            } catch (Exception ignore) {
            }
            dbgServer = null;
        }
        btnRun.setText("运行");
    }

    private void stopDebug() {
        engine.stop();
        finishDebug();
        appendLog("■ 调试已停止");
    }

    private void setBreakpointAtCursor() {
        int sel = etCode.getSelectionStart();
        if (sel < 0) sel = 0;
        int line = lineOfOffsetInText(etCode.getText().toString(), sel);
        engine.sendDebugCommand("BREAK " + line + "\n");
        if (lineNumbers != null) {
            lineNumbers.setBreakpointSilently(line, true);
            persistBreakpoints();
        }
        tvDebugStatus.setText("🔴 已设断点：第 " + line + " 行（点「继续」后将在该处暂停）");
        appendLog("已设断点：第 " + line + " 行");
    }

    private void highlightDebugLine(int line) {
        Editable text = etCode.getText();
        if (text == null) return;
        clearDebugLine();
        int total = text.length();
        int start = 0;
        for (int l = 1; l < line && start < total; l++) {
            int idx = text.toString().indexOf('\n', start);
            if (idx < 0) {
                start = total;
                break;
            }
            start = idx + 1;
        }
        int end = text.toString().indexOf('\n', start);
        if (end < 0) end = total;
        if (start > end) start = end;
        dbgLineSpan = new BackgroundColorSpan(0x554EC9B0);
        text.setSpan(dbgLineSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        try {
            etCode.setSelection(start);
        } catch (Exception ignore) {
        }
    }

    private void clearDebugLine() {
        Editable text = etCode.getText();
        if (text != null && dbgLineSpan != null) {
            text.removeSpan(dbgLineSpan);
        }
        dbgLineSpan = null;
    }

    private void showDebugBar(boolean show) {
        if (debugBar != null) {
            debugBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private static int lineOfOffsetInText(String text, int offset) {
        int line = 1;
        int n = Math.min(offset, text.length());
        for (int i = 0; i < n; i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }
}
