package com.custom.os;

import android.app.Activity;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.admin.DevicePolicyManager;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

/** COs 1.0 - launcher neon : intro, menu Cocoon/COs, accueil avec apps, reglages */
public class MainActivity extends Activity {
    static final int PICK = 1, HOME_REQ = 2, FILE_REQ = 3, MUSIC_REQ = 4, WALL_REQ = 5, ADMIN_REQ = 6;
    static final int DARK = 0xFF05030F;
    static final int[][] THEMES = {
        {0xFF00E5FF, 0xFFD500F9}, {0xFF39FF14, 0xFF00E5FF}, {0xFFFF9100, 0xFFFF1744},
        {0xFF7C4DFF, 0xFF18FFFF}, {0xFFFF2D95, 0xFF00E5FF}, {0xFFFFEA00, 0xFF76FF03},
        {0xFF2979FF, 0xFFE0E0FF}, {0xFFFF4081, 0xFF7C4DFF}, {0xFF1DE9B6, 0xFF00B0FF},
        {0xFFFF1744, 0xFFD500F9}};
    static boolean introDone = false;

    SharedPreferences sp;
    int C1, C2;
    FrameLayout stage, content;
    NeonBg bg;
    Bitmap logo;
    boolean inIntro = false, askPending = false;
    ValueCallback<Uri[]> fileCb;
    int wall = 0, fillA = 31, bgOp = 100;
    static class Pos { float fx, fy; boolean auto = true; String key; }
    interface IntCb { void on(int v); }
    MediaPlayer mp;
    boolean mpReady = false, userPaused = false, autoPaused = false, focusPaused = false;
    int cur = 0;
    String curPath = "";
    List<File> tracks = new ArrayList<>();
    final Map<String, String[]> meta = new HashMap<>();
    TextView npTv, ppBtn;
    final AudioManager.OnAudioFocusChangeListener focusL = ch -> {
        try {
            if (mp == null) return;
            if (ch == AudioManager.AUDIOFOCUS_LOSS || ch == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                if (mp.isPlaying()) { mp.pause(); focusPaused = true; }
            } else if (ch == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                mp.setVolume(vf() * 0.25f, vf() * 0.25f);
            } else if (ch == AudioManager.AUDIOFOCUS_GAIN) {
                mp.setVolume(vf(), vf());
                if (focusPaused && !userPaused && !autoPaused && mpReady) mp.start();
                focusPaused = false;
            }
        } catch (Exception ignored) {}
    };
    static final String ACT_INSTALL = "com.custom.os.INSTALL_RESULT";
    static class PackItem {
        String file, path, pkg, label, url;
        Drawable icon;
        boolean online;
        long dlId = -1;
        int pct = 0;
        TextView stateTv;
    }
    final Map<Long, PackItem> dlWait = new HashMap<>();
    final BroadcastReceiver dlRcv = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent i) { onDownloadComplete(i); }
    };
    final Runnable pollTick = new Runnable() {
        public void run() {
            if (dlWait.isEmpty()) return;
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            for (Map.Entry<Long, PackItem> e : new ArrayList<>(dlWait.entrySet())) {
                PackItem it = e.getValue();
                try (Cursor c = dm.query(new DownloadManager.Query().setFilterById(e.getKey()))) {
                    if (c != null && c.moveToFirst()) {
                        long got = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        long tot = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        it.pct = tot > 0 ? (int) (got * 100 / tot) : 0;
                        if (it.stateTv != null && screen.equals("pack")) it.stateTv.setText(stateText(it, false));
                    }
                } catch (Exception ignored) {}
            }
            ui.postDelayed(this, 1000);
        }
    };
    List<PackItem> packItems = new ArrayList<>();
    List<PackItem> queue = new ArrayList<>();
    int qi = 0;
    boolean queueOn = false, resumed = false, pendingNext = false;
    Intent pendingConfirm = null;
    final BroadcastReceiver installRcv = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent i) { onInstallResult(i); }
    };
    static final String[] WALLS = {"Grille", "Pluie", "Aurore", "Particules", "Warp", "Sobre", "Image"};
    static final String[] ACTS = {"Rien", "Tiroir d'apps", "Recherche", "Réglages", "Mon OS", "Musique", "Verrouiller l'écran"};
    final TextView[] wv = new TextView[3];
    final NeonBar[] wb = new NeonBar[3];
    final Runnable widgetTick = new Runnable() {
        public void run() {
            if (!screen.equals("home") || wv[0] == null) return;
            updateWidgets();
            ui.postDelayed(this, 4000);
        }
    };
    String screen = "";
    final Handler ui = new Handler(Looper.getMainLooper());

    static class AppItem { String label, pkg, cls, folderId; ResolveInfo ri; Drawable icon; }
    static class Folder { String id, name; List<String> keys = new ArrayList<>(); }
    interface StrCb { void on(String s); }
    public static class AdminRcv extends android.app.admin.DeviceAdminReceiver {}
    GestureDetector homeGd;
    Dialog folderDlg;
    EditText searchEt;
    int wveil = 25;

    // ---------------------------------------------------------------- cycle de vie
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("cos", MODE_PRIVATE);
        loadTheme();
        wall = sp.getInt("wall", 0);
        fillA = sp.getInt("fillA", 31);
        bgOp = sp.getInt("bgOp", 100);
        wveil = sp.getInt("wveil", 25);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(DARK);
        logo = loadLogo();
        stage = new FrameLayout(this);
        bg = new NeonBg(this);
        stage.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        content = new FrameLayout(this);
        stage.addView(content, new FrameLayout.LayoutParams(-1, -1));
        setContentView(stage);
        android.content.IntentFilter inf = new android.content.IntentFilter(ACT_INSTALL);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(installRcv, inf, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(installRcv, inf);
        android.content.IntentFilter dlf = new android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(dlRcv, dlf, Context.RECEIVER_EXPORTED);
        else registerReceiver(dlRcv, dlf);
        askPending = (b == null && !isHome());
        if (b == null && !introDone && sp.getBoolean("intro", true)) intro();
        else enter();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (pendingConfirm != null) {
            Intent cf = pendingConfirm; pendingConfirm = null;
            try { startActivity(cf); } catch (Exception e) { toast("Confirmation impossible"); }
        }
        if (pendingNext) { pendingNext = false; ui.postDelayed(this::installNext, 300); }
    }

    @Override protected void onPause() { super.onPause(); resumed = false; }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(installRcv); } catch (Exception ignored) {}
        try { unregisterReceiver(dlRcv); } catch (Exception ignored) {}
        releasePlayer();
    }

    @Override protected void onNewIntent(Intent i) { // appui sur Accueil
        super.onNewIntent(i);
        if (!inIntro) enter();
    }

    @Override public void onBackPressed() {
        if (!(screen.equals("home") || screen.equals("menu") || screen.equals("intro"))) home();
    }

    void enter() {
        inIntro = false;
        if (sp.getBoolean("menu", true)) menu(); else home();
        musicStart();
        if (askPending) { askPending = false; ui.postDelayed(this::askHome, 400); }
        if (hasPack() && !sp.getBoolean("packAsked", false)) {
            sp.edit().putBoolean("packAsked", true).apply();
            ui.postDelayed(this::pack, 1500);
        }
    }

    // ---------------------------------------------------------------- outils UI
    int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    static int withAlpha(int c, int a) { return (Math.max(0, Math.min(255, a)) << 24) | (c & 0x00FFFFFF); }
    static float cl(float x) { return x < 0 ? 0 : (x > 1 ? 1 : x); }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    void loadTheme() {
        int i = sp.getInt("theme", 0);
        if (i == -1) {
            C1 = sp.getInt("c1", THEMES[0][0]);
            C2 = sp.getInt("c2", THEMES[0][1]);
            return;
        }
        if (i < 0 || i >= THEMES.length) i = 0;
        C1 = THEMES[i][0]; C2 = THEMES[i][1];
    }

    Bitmap loadLogo() {
        try {
            int id = getResources().getIdentifier("cos_logo", "drawable", getPackageName());
            if (id == 0) return null;
            return BitmapFactory.decodeResource(getResources(), id);
        } catch (Throwable e) { return null; }
    }

    void show(View v, String name) {
        screen = name;
        if (name.equals("os")) {
            try { if (mp != null && mpReady && mp.isPlaying()) { mp.pause(); autoPaused = true; } } catch (Exception ignored) {}
        } else if (autoPaused) {
            autoPaused = false;
            try { if (!userPaused && mp != null && mpReady) mp.start(); } catch (Exception ignored) {}
        }
        content.removeAllViews();
        content.addView(v, new FrameLayout.LayoutParams(-1, -1));
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(240).start();
    }

    LinearLayout col(int gravity) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(gravity);
        c.setPadding(dp(24), dp(20), dp(24), dp(20));
        return c;
    }

    ScrollView scroll(View c) {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.setVerticalScrollBarEnabled(false);
        s.addView(c, new ViewGroup.LayoutParams(-1, -2));
        return s;
    }

    LinearLayout.LayoutParams lp(int w, int h, int mb) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.bottomMargin = mb;
        p.gravity = Gravity.CENTER_HORIZONTAL;
        return p;
    }

    TextView txt(String s, float size, int color, boolean glow) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (glow) {
            t.setShadowLayer(dp(14), 0, 0, C1);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setLetterSpacing(0.08f);
        }
        return t;
    }

    Drawable neonBox(float radiusDp, boolean strong) {
        GradientDrawable glow = new GradientDrawable();
        glow.setCornerRadius(dp(radiusDp + 3));
        glow.setStroke(dp(5), withAlpha(C1, strong ? 0x45 : 0x25));
        GradientDrawable box = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{withAlpha(C1, fillA * 153 / 100), withAlpha(C2, fillA * 153 / 100)});
        box.setCornerRadius(dp(radiusDp));
        box.setStroke(dp(2), C1);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{glow, box});
        ld.setLayerInset(1, dp(3), dp(3), dp(3), dp(3));
        return ld;
    }

    void pressFx(View t) {
        t.setOnTouchListener((v, e) -> {
            int a = e.getAction();
            if (a == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start();
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL)
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            return false;
        });
    }

    TextView nbtn(String s, final View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(19);
        t.setGravity(Gravity.CENTER);
        t.setShadowLayer(dp(8), 0, 0, C1);
        t.setLetterSpacing(0.04f);
        t.setPadding(dp(18), dp(12), dp(18), dp(12));
        t.setBackground(neonBox(18, true));
        t.setClickable(true);
        t.setOnClickListener(v -> { tick(); l.onClick(v); });
        pressFx(t);
        return t;
    }

    // ---------------------------------------------------------------- ecrans
    void menu() {
        LinearLayout c = col(Gravity.CENTER);
        if (logo != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(logo);
            c.addView(iv, lp(dp(210), dp(210), 0));
            final ObjectAnimator fl = ObjectAnimator.ofFloat(iv, "translationY", -dp(7), dp(7));
            fl.setDuration(2400);
            fl.setRepeatCount(ValueAnimator.INFINITE);
            fl.setRepeatMode(ValueAnimator.REVERSE);
            fl.start();
            iv.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                public void onViewAttachedToWindow(View v) {}
                public void onViewDetachedFromWindow(View v) { fl.cancel(); }
            });
        } else {
            c.addView(txt("COs", 54, Color.WHITE, true));
        }
        TextView sub = txt("Choisis un système", 16, 0xCCFFFFFF, false);
        sub.setPadding(0, dp(8), 0, dp(18));
        c.addView(sub);
        c.addView(nbtn("Cocoon", v -> launch("cocoon", "Cocoon")), lp(dp(300), dp(58), dp(14)));
        c.addView(nbtn("COs", v -> home()), lp(dp(300), dp(58), dp(14)));
        c.addView(nbtn("Mon OS", v -> os()), lp(dp(300), dp(58), dp(14)));
        if (hasPack()) c.addView(nbtn("Pack d'apps", v -> pack()), lp(dp(300), dp(58), dp(14)));
        c.addView(nbtn("Réglages", v -> settings()), lp(dp(300), dp(50), dp(14)));
        if (!isHome()) c.addView(nbtn("Définir COs comme launcher", v -> askHome()), lp(dp(300), dp(50), dp(14)));
        show(scroll(c), "menu");
    }

    // ---------------------------------------------------------------- favoris (apps de l'accueil)
    List<String> favs() {
        String s = sp.getString("favs", "");
        List<String> l = new ArrayList<>();
        if (!s.isEmpty()) l.addAll(Arrays.asList(s.split("\n")));
        return l;
    }

    void saveFavs(List<String> l) { sp.edit().putString("favs", android.text.TextUtils.join("\n", l)).apply(); }

    static String key(AppItem a) { return a.pkg + "/" + a.cls; }

    // ---------------------------------------------------------------- accueil
    void home() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(20), dp(8), dp(20), dp(10));
        homeGd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onDoubleTap(MotionEvent e) { doAction(sp.getInt("gDouble", 0)); return true; }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dy = e2.getY() - e1.getY(), dx = e2.getX() - e1.getX();
                if (Math.abs(dy) > dp(60) && Math.abs(dy) > Math.abs(dx) * 1.3f) {
                    doAction(dy < 0 ? sp.getInt("gUp", 1) : sp.getInt("gDown", 2));
                    return true;
                }
                return false;
            }
        });
        final View.OnTouchListener gl = (gv, ge) -> { homeGd.onTouchEvent(ge); return true; };

        TextClock clk = new TextClock(this);
        clk.setFormat24Hour("HH:mm"); clk.setFormat12Hour("HH:mm");
        clk.setTextSize(52); clk.setTextColor(Color.WHITE);
        clk.setShadowLayer(dp(16), 0, 0, C1);
        clk.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clk.setGravity(Gravity.CENTER_HORIZONTAL);
        clk.setOnTouchListener(gl);
        c.addView(clk, new LinearLayout.LayoutParams(-1, -2));

        TextClock dt = new TextClock(this);
        dt.setFormat24Hour("EEEE d MMMM"); dt.setFormat12Hour("EEEE d MMMM");
        dt.setTextSize(15); dt.setTextColor(0xCCFFFFFF);
        dt.setShadowLayer(dp(6), 0, 0, C2);
        dt.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.bottomMargin = dp(10);
        dt.setOnTouchListener(gl);
        c.addView(dt, dl);
        if (sp.getBoolean("widgets", true)) {
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
            wl.bottomMargin = dp(10);
            View wr = widgetRow();
            wr.setOnTouchListener(gl);
            c.addView(wr, wl);
        }

        final List<AppItem> all = loadApps();
        final List<AppItem> shown = new ArrayList<>();
        final List<Folder> fls = loadFolders();
        for (String k : favs()) {
            boolean inFolder = false;
            for (Folder fo : fls) if (fo.keys.contains(k)) inFolder = true;
            if (inFolder) continue;
            for (AppItem a : all) if (key(a).equals(k)) { shown.add(a); break; }
        }
        for (Folder fo : fls) {
            AppItem fi = new AppItem();
            fi.label = fo.name; fi.pkg = "folder"; fi.cls = fo.id; fi.folderId = fo.id;
            fi.icon = folderIcon(fo, all);
            shown.add(fi);
        }

        if (shown.isEmpty()) {
            LinearLayout empty = col(Gravity.CENTER);
            empty.addView(txt("Ton accueil est vide", 20, Color.WHITE, true), lp(-2, -2, dp(8)));
            empty.addView(txt("Choisis les apps à afficher ici.\nToutes tes apps sont derrière le bouton à 4 points.", 14, 0xCCFFFFFF, false), lp(-2, -2, dp(16)));
            empty.addView(nbtn("Choisir mes apps", v -> appsScreen("pick")), lp(dp(260), dp(54), 0));
            c.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1f));
        } else {
            c.addView(makeDesk(shown), new LinearLayout.LayoutParams(-1, 0, 1f));
        }

        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        List<View> dv = dockViews(all);
        int half = (dv.size() + 1) / 2;
        for (int di = 0; di < dv.size(); di++) {
            if (di == half) addDots(dock);
            dock.addView(dv.get(di));
        }
        if (half >= dv.size()) addDots(dock);
        c.addView(dock, new LinearLayout.LayoutParams(-1, -2));
        show(c, "home");
    }

    void addDots(LinearLayout dock) {
        DotsBtn db = new DotsBtn(this);
        db.setOnClickListener(v -> { tick(); appsScreen("drawer"); });
        LinearLayout.LayoutParams dbp = new LinearLayout.LayoutParams(dp(64), dp(64));
        dbp.setMargins(dp(8), dp(8), dp(8), 0);
        dock.addView(db, dbp);
    }

    void dockBtn(LinearLayout dock, String s, View.OnClickListener l) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(50), 1f);
        p.setMargins(dp(4), dp(8), dp(4), 0);
        dock.addView(nbtn(s, l), p);
    }

    class DotsBtn extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        DotsBtn(Context c) {
            super(c);
            setBackground(neonBox(32, true));
            setClickable(true);
            pressFx(this);
        }
        @Override protected void onDraw(Canvas cv) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, gap = dp(7), r = dp(4.5f);
            for (int i = 0; i < 4; i++) {
                float x = cx + ((i % 2 == 0) ? -gap : gap), y = cy + ((i / 2 == 0) ? -gap : gap);
                p.setColor(withAlpha(C1, 110)); cv.drawCircle(x, y, r * 1.9f, p);
                p.setColor(Color.WHITE); cv.drawCircle(x, y, r, p);
            }
        }
    }

    // ---------------------------------------------------------------- liste d'apps (tiroir / choix de l'accueil)
    GridView makeGrid(final List<AppItem> shown, final String mode, final Set<String> sel) {
        final PackageManager pm = getPackageManager();
        GridView g = new GridView(this);
        g.setNumColumns(GridView.AUTO_FIT);
        g.setColumnWidth(dp(iconDp() + 40));
        g.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        g.setVerticalSpacing(dp(8));
        g.setSelector(new ColorDrawable(0));
        g.setAdapter(new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int i) { return shown.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup parent) {
                AppItem a = shown.get(i);
                if (v == null) {
                    LinearLayout cell = new LinearLayout(MainActivity.this);
                    cell.setOrientation(LinearLayout.VERTICAL);
                    cell.setGravity(Gravity.CENTER_HORIZONTAL);
                    cell.setPadding(dp(4), dp(6), dp(4), dp(6));
                    ImageView iv0 = new ImageView(MainActivity.this);
                    iv0.setId(1001);
                    int pd0 = dp(iconDp() * 0.15f);
                    iv0.setPadding(pd0, pd0, pd0, pd0);
                    Drawable bg0 = iconBg(iconDp());
                    if (bg0 != null) iv0.setBackground(bg0);
                    cell.addView(iv0, new LinearLayout.LayoutParams(dp(iconDp()), dp(iconDp())));
                    TextView tv0 = new TextView(MainActivity.this);
                    tv0.setId(1002);
                    tv0.setTextSize(12); tv0.setTextColor(Color.WHITE);
                    tv0.setGravity(Gravity.CENTER);
                    tv0.setMaxLines(1);
                    tv0.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    tv0.setShadowLayer(dp(4), 0, 0, C1);
                    tv0.setPadding(0, dp(4), 0, 0);
                    tv0.setVisibility(sp.getBoolean("ilabels", true) ? View.VISIBLE : View.GONE);
                    cell.addView(tv0, new LinearLayout.LayoutParams(-1, -2));
                    v = cell;
                }
                ImageView iv = (ImageView) v.findViewById(1001);
                TextView tv = (TextView) v.findViewById(1002);
                if (a.icon == null) a.icon = a.ri.loadIcon(pm);
                boolean on = sel != null && sel.contains(key(a));
                iv.setImageDrawable(a.icon);
                iv.setAlpha(sel == null || on ? 1f : 0.3f);
                tv.setText((on ? "✓ " : "") + a.label);
                return v;
            }
        });
        g.setOnItemClickListener((p, v, i, id) -> {
            AppItem a = shown.get(i);
            tick();
            if (mode.equals("pick") || mode.equals("dock")) {
                String k = key(a);
                if (!sel.remove(k)) sel.add(k);
                ((BaseAdapter) p.getAdapter()).notifyDataSetChanged();
            } else {
                openApp(a);
                if (mode.startsWith("folder:") && folderDlg != null) folderDlg.dismiss();
            }
        });
        g.setOnItemLongClickListener((p, v, i, id) -> {
            if (mode.startsWith("folder:")) folderItemMenu(shown.get(i), mode.substring(7));
            else if (!mode.equals("pick") && !mode.equals("dock")) appMenu(shown.get(i), mode);
            return true;
        });
        return g;
    }

    EditText searchBox(LinearLayout c) {
        EditText q = new EditText(this);
        q.setHint("Rechercher une app…");
        q.setHintTextColor(0x88FFFFFF); q.setTextColor(Color.WHITE);
        q.setSingleLine(true);
        q.setBackground(neonBox(16, false));
        q.setPadding(dp(16), dp(8), dp(16), dp(8));
        LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(-1, dp(50));
        ql.bottomMargin = dp(10);
        c.addView(q, ql);
        return q;
    }

    void appsScreen(final String mode) {
        final boolean pick = mode.equals("pick") || mode.equals("dock");
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(20), dp(12), dp(20), dp(10));
        c.addView(txt(mode.equals("dock") ? "Apps du dock (5 max)" : (pick ? "Apps de l'accueil" : "Toutes les apps"), 26, Color.WHITE, true), lp(-2, -2, dp(6)));
        if (pick) c.addView(txt(mode.equals("dock") ? "Touche les apps à mettre dans le dock" : "Touche les apps à afficher sur l'accueil", 13, 0xCCFFFFFF, false), lp(-2, -2, dp(10)));
        EditText q = searchBox(c);
        searchEt = q;

        final List<AppItem> all = loadApps();
        final List<AppItem> shown = new ArrayList<>(all);
        final Set<String> sel = pick ? new LinkedHashSet<String>(mode.equals("dock") ? cut(sp.getString("dock", ""), '\n') : favs()) : null;
        final GridView g = makeGrid(shown, mode, sel);
        c.addView(g, new LinearLayout.LayoutParams(-1, 0, 1f));
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            public void onTextChanged(CharSequence s, int x, int y, int z) {}
            public void afterTextChanged(Editable e) {
                String k = e.toString().trim().toLowerCase();
                shown.clear();
                for (AppItem a : all) if (k.isEmpty() || a.label.toLowerCase().contains(k)) shown.add(a);
                ((BaseAdapter) g.getAdapter()).notifyDataSetChanged();
            }
        });

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        if (pick) {
            dockBtn(bar, "Annuler", v -> { if (mode.equals("dock")) custom(); else home(); });
            dockBtn(bar, "Valider", v -> {
                if (mode.equals("dock")) {
                    List<String> od = cut(sp.getString("dock", ""), '\n'), nd = new ArrayList<>();
                    for (String k : od) if (sel.contains(k)) nd.add(k);
                    for (AppItem a : all) if (sel.contains(key(a)) && !nd.contains(key(a))) nd.add(key(a));
                    if (nd.size() > 5) { nd = new ArrayList<>(nd.subList(0, 5)); toast("5 apps maximum : les premières sont gardées"); }
                    sp.edit().putString("dock", android.text.TextUtils.join("\n", nd)).apply();
                    custom();
                    return;
                }
                List<String> old = favs(), out = new ArrayList<>();
                for (String k : old) if (sel.contains(k)) out.add(k);
                for (AppItem a : all) if (sel.contains(key(a)) && !out.contains(key(a))) out.add(key(a));
                saveFavs(out);
                home();
            });
        } else {
            dockBtn(bar, "Retour à l'accueil", v -> home());
        }
        c.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        show(c, mode);
    }

    // ---------------------------------------------------------------- Mon OS (bureau a fenetres dans une WebView)
    void os() {
        WebView w = new WebView(this);
        w.setBackgroundColor(DARK);
        WebSettings st = w.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setMediaPlaybackRequiresUserGesture(false);
        w.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void exit() { runOnUiThread(() -> home()); }
        }, "Android");
        w.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("*/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(Intent.createChooser(i, "Choisir un fichier"), FILE_REQ);
                } catch (Exception e) {
                    fileCb = null;
                    cb.onReceiveValue(null);
                    return false;
                }
                return true;
            }
        });
        String q = "?c1=" + String.format("%06x", C1 & 0xFFFFFF) + "&c2=" + String.format("%06x", C2 & 0xFFFFFF);
        w.loadUrl("file:///android_asset/cos_os.html" + q);
        w.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) {}
            public void onViewDetachedFromWindow(View v) { try { ((WebView) v).destroy(); } catch (Exception ignored) {} }
        });
        show(w, "os");
    }

    // ---------------------------------------------------------------- musique (playlist)
    File musicDir() {
        File d = new File(getFilesDir(), "music");
        d.mkdirs();
        return d;
    }

    void ensureDefault() {
        if (sp.getBoolean("defRemoved", false)) return;
        File f = new File(musicDir(), "like_forever.mp3");
        if (f.exists()) return;
        try { copyAsset("music/like_forever.mp3", f); } catch (Exception e) { f.delete(); }
    }

    List<File> listTracks() {
        List<File> out = new ArrayList<>();
        File[] fs = musicDir().listFiles();
        if (fs != null) for (File f : fs) if (f.isFile() && f.getName().toLowerCase().endsWith(".mp3")) out.add(f);
        Collections.sort(out, (a, b) -> {
            boolean da = a.getName().equals("like_forever.mp3"), db = b.getName().equals("like_forever.mp3");
            if (da != db) return da ? -1 : 1;
            return Long.compare(a.lastModified(), b.lastModified());
        });
        return out;
    }

    String[] trackMeta(File f) {
        String[] m = meta.get(f.getAbsolutePath());
        if (m != null) return m;
        String title = null, artist = null;
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(f.getAbsolutePath());
            title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        } catch (Exception ignored) {}
        try { r.release(); } catch (Exception ignored) {}
        if (title == null || title.trim().isEmpty()) {
            String n = f.getName();
            int k = n.lastIndexOf('.');
            if (k > 0) n = n.substring(0, k);
            title = n.replace('_', ' ');
        }
        m = new String[]{title.trim(), artist == null ? "" : artist.trim()};
        meta.put(f.getAbsolutePath(), m);
        return m;
    }

    void musicStart() {
        if (!sp.getBoolean("music", false) || mp != null) return;
        ensureDefault();
        tracks = listTracks();
        if (tracks.isEmpty()) return;
        playTrack(Math.min(cur, tracks.size() - 1));
    }

    void releasePlayer() {
        if (mp != null) {
            try { mp.release(); } catch (Exception ignored) {}
            mp = null;
        }
        mpReady = false;
        try { ((AudioManager) getSystemService(Context.AUDIO_SERVICE)).abandonAudioFocus(focusL); } catch (Exception ignored) {}
    }

    void playTrack(int i) {
        if (tracks.isEmpty()) return;
        cur = ((i % tracks.size()) + tracks.size()) % tracks.size();
        curPath = tracks.get(cur).getAbsolutePath();
        if (mp != null) { try { mp.release(); } catch (Exception ignored) {} mp = null; }
        mpReady = false;
        try {
            mp = new MediaPlayer();
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setDataSource(curPath);
            mp.setOnPreparedListener(m -> {
                mpReady = true;
                if (!userPaused && !autoPaused) {
                    try { ((AudioManager) getSystemService(Context.AUDIO_SERVICE)).requestAudioFocus(focusL, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN); } catch (Exception ignored) {}
                    m.setVolume(vf(), vf());
                    m.start();
                }
                refreshPlayerUi();
            });
            mp.setOnCompletionListener(m -> playTrack(cur + 1));
            mp.setOnErrorListener((m, w, x) -> { releasePlayer(); refreshPlayerUi(); return true; });
            mp.prepareAsync();
        } catch (Exception e) {
            releasePlayer();
            toast("Lecture impossible");
        }
        refreshPlayerUi();
    }

    float vf() { return sp.getInt("vol", 100) / 100f; }

    boolean isPlayingNow() {
        try { return mp != null && mpReady && mp.isPlaying(); } catch (Exception e) { return false; }
    }

    void togglePlay() {
        if (!sp.getBoolean("music", false)) sp.edit().putBoolean("music", true).apply();
        if (mp == null) { userPaused = false; musicStart(); }
        else if (isPlayingNow()) { try { mp.pause(); } catch (Exception ignored) {} userPaused = true; }
        else { userPaused = false; try { if (mpReady) mp.start(); } catch (Exception ignored) {} }
        refreshPlayerUi();
    }

    void setMusic(boolean on) {
        sp.edit().putBoolean("music", on).apply();
        if (on) { userPaused = false; musicStart(); }
        else { releasePlayer(); }
    }

    void refreshPlayerUi() {
        if (!screen.equals("music") || npTv == null) return;
        String t = "—";
        if (mp != null && !tracks.isEmpty() && cur < tracks.size()) {
            String[] m = trackMeta(tracks.get(cur));
            t = m[0] + (m[1].isEmpty() ? "" : "  ·  " + m[1]);
        }
        npTv.setText(t);
        ppBtn.setText(isPlayingNow() ? "⏸" : "▶");
    }

    void musicScreen() {
        ensureDefault();
        tracks = listTracks();
        if (mp != null) {
            int k = -1;
            for (int j = 0; j < tracks.size(); j++) if (tracks.get(j).getAbsolutePath().equals(curPath)) k = j;
            if (k >= 0) cur = k;
        }
        boolean on = sp.getBoolean("music", false);
        LinearLayout c = col(Gravity.CENTER_HORIZONTAL);
        c.addView(txt("Musique ♪", 30, Color.WHITE, true), lp(-2, -2, dp(12)));
        c.addView(nbtn("Musique : " + (on ? "ON" : "OFF"), v -> { setMusic(!sp.getBoolean("music", false)); musicScreen(); }), lp(dp(460), dp(54), dp(12)));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(neonBox(16, true));
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        npTv = new TextView(this);
        npTv.setTextColor(Color.WHITE); npTv.setTextSize(16); npTv.setGravity(Gravity.CENTER);
        npTv.setShadowLayer(dp(8), 0, 0, C1);
        card.addView(npTv, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout ctl = new LinearLayout(this);
        ctl.setOrientation(LinearLayout.HORIZONTAL);
        ctl.setGravity(Gravity.CENTER);
        TextView prev = nbtn("⏮", v -> { userPaused = false; if (tracks.isEmpty()) return; if (mp == null) { sp.edit().putBoolean("music", true).apply(); } playTrack(cur - 1); });
        ppBtn = nbtn("▶", v -> togglePlay());
        TextView next = nbtn("⏭", v -> { userPaused = false; if (tracks.isEmpty()) return; if (mp == null) { sp.edit().putBoolean("music", true).apply(); } playTrack(cur + 1); });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(76), dp(52));
        bp.setMargins(dp(6), dp(10), dp(6), 0);
        ctl.addView(prev, bp); ctl.addView(ppBtn, bp); ctl.addView(next, bp);
        card.addView(ctl);
        c.addView(card, lp(dp(460), -2, dp(14)));
        c.addView(sliderRow("Volume", 0, 100, sp.getInt("vol", 100), v -> {
            sp.edit().putInt("vol", v).apply();
            try { if (mp != null) mp.setVolume(v / 100f, v / 100f); } catch (Exception ignored) {}
        }), lp(dp(340), -2, dp(10)));

        c.addView(txt("Playlist (" + tracks.size() + ")", 14, 0xCCFFFFFF, false), lp(-2, -2, dp(8)));
        if (tracks.isEmpty()) c.addView(txt("Aucun morceau. Importe des MP3 ci-dessous.", 14, 0xCCFFFFFF, false), lp(-2, -2, dp(12)));
        for (int j = 0; j < tracks.size(); j++) {
            final int idx = j;
            final File tf = tracks.get(j);
            String[] m = trackMeta(tf);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(neonBox(14, false));
            row.setPadding(dp(14), dp(8), dp(6), dp(8));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            TextView t1 = new TextView(this);
            t1.setText((tf.getAbsolutePath().equals(curPath) && mp != null ? "♪ " : "") + m[0]);
            t1.setTextColor(Color.WHITE); t1.setTextSize(15); t1.setSingleLine(true);
            t1.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(t1);
            if (!m[1].isEmpty()) {
                TextView t2 = new TextView(this);
                t2.setText(m[1]); t2.setTextColor(0x99FFFFFF); t2.setTextSize(11);
                info.addView(t2);
            }
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView del = new TextView(this);
            del.setText("✕"); del.setTextColor(Color.WHITE); del.setTextSize(18);
            del.setGravity(Gravity.CENTER);
            del.setOnClickListener(v -> confirmRemove(tf));
            row.addView(del, new LinearLayout.LayoutParams(dp(48), dp(44)));
            row.setOnClickListener(v -> {
                tick();
                sp.edit().putBoolean("music", true).apply();
                userPaused = false;
                playTrack(idx);
            });
            c.addView(row, lp(dp(460), -2, dp(8)));
        }
        c.addView(nbtn("Importer des MP3", v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("audio/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            try { startActivityForResult(i, MUSIC_REQ); } catch (Exception e) { toast("Sélecteur indisponible"); }
        }), lp(dp(460), dp(54), dp(10)));
        if (sp.getBoolean("defRemoved", false))
            c.addView(nbtn("Remettre « Like Forever »", v -> {
                sp.edit().putBoolean("defRemoved", false).apply();
                ensureDefault();
                musicScreen();
            }), lp(dp(460), dp(54), dp(10)));
        c.addView(nbtn("Retour", v -> settings()), lp(dp(460), dp(54), dp(12)));
        show(scroll(c), "music");
        refreshPlayerUi();
    }

    void confirmRemove(final File f) {
        String[] m = trackMeta(f);
        new AlertDialog.Builder(this).setTitle("Retirer ce morceau ?").setMessage(m[0])
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Retirer", (d, w) -> removeTrack(f)).show();
    }

    void removeTrack(File f) {
        boolean wasCur = f.getAbsolutePath().equals(curPath);
        if (f.getName().equals("like_forever.mp3")) sp.edit().putBoolean("defRemoved", true).apply();
        f.delete();
        meta.remove(f.getAbsolutePath());
        tracks = listTracks();
        if (mp != null) {
            if (tracks.isEmpty()) { releasePlayer(); curPath = ""; }
            else if (wasCur) playTrack(Math.min(cur, tracks.size() - 1));
            else for (int j = 0; j < tracks.size(); j++) if (tracks.get(j).getAbsolutePath().equals(curPath)) cur = j;
        }
        musicScreen();
    }

    String displayName(Uri u) {
        try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
        } catch (Exception ignored) {}
        return null;
    }

    void importMusic(final List<Uri> uris) {
        toast("Import en cours…");
        new Thread(() -> {
            int ok = 0;
            for (Uri u : uris) {
                try {
                    String n = displayName(u);
                    if (n == null || n.trim().isEmpty()) n = "morceau.mp3";
                    n = n.replaceAll("[^A-Za-z0-9._ -]", "_");
                    if (!n.toLowerCase().endsWith(".mp3")) n = n + ".mp3";
                    File dst = new File(musicDir(), n);
                    int k = 1;
                    while (dst.exists()) { dst = new File(musicDir(), (k++) + "_" + n); }
                    try (InputStream in = getContentResolver().openInputStream(u); OutputStream out = new FileOutputStream(dst)) {
                        byte[] b = new byte[65536];
                        int r;
                        while ((r = in.read(b)) > 0) out.write(b, 0, r);
                    }
                    ok++;
                } catch (Exception ignored) {}
            }
            final int done = ok;
            runOnUiThread(() -> {
                toast(done + " morceau(x) importé(s)");
                if (screen.equals("music")) musicScreen();
            });
        }).start();
    }

    // ---------------------------------------------------------------- pack d'apps (APK inclus dans l'application)
    String[] packFiles() {
        try {
            String[] l = getAssets().list("pack");
            List<String> o = new ArrayList<>();
            if (l != null) for (String n : l) if (n.toLowerCase().endsWith(".apk")) o.add(n);
            Collections.sort(o);
            return o.toArray(new String[0]);
        } catch (IOException e) { return new String[0]; }
    }

    void copyAsset(String name, File dst) throws IOException {
        try (InputStream in = getAssets().open(name); OutputStream out = new FileOutputStream(dst)) {
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
        }
    }

    boolean isInstalled(String pkg) {
        try { getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception e) { return false; }
    }

    // liste "Nom|lien" fournie par l'outil (apps trop grosses pour etre incluses)
    List<String[]> onlineList() {
        List<String[]> out = new ArrayList<>();
        try (InputStream in = getAssets().open("pack/online.txt")) {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                int k = line.indexOf('|');
                if (k <= 0) continue;
                String url = line.substring(k + 1).trim();
                if (url.startsWith("http")) out.add(new String[]{line.substring(0, k).trim(), url});
            }
        } catch (Exception ignored) {}
        return out;
    }

    boolean hasPack() { return packFiles().length > 0 || !onlineList().isEmpty(); }

    boolean installedItem(PackItem it) {
        if (it.online) return find(it.label.toLowerCase()) != null;
        return it.pkg != null && isInstalled(it.pkg);
    }

    String stateText(PackItem it, boolean inst) {
        if (inst) return "✓ Installée";
        if (it.online) {
            if (it.dlId >= 0) return "Téléchargement " + it.pct + " %";
            if (new File(it.path).exists()) return "Téléchargée";
            return "À télécharger";
        }
        return "À installer";
    }

    void downloadThenInstall(PackItem it) {
        try {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            File part = new File(it.path + ".part");
            part.delete();
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(it.url));
            r.setTitle(it.label);
            r.setDestinationUri(Uri.fromFile(part));
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            it.dlId = dm.enqueue(r);
            it.pct = 0;
            dlWait.put(it.dlId, it);
            toast("Téléchargement de " + it.label + "…");
            ui.removeCallbacks(pollTick);
            ui.postDelayed(pollTick, 1000);
            if (it.stateTv != null) it.stateTv.setText(stateText(it, false));
        } catch (Exception e) {
            toast("Téléchargement impossible : " + it.label);
            it.dlId = -1;
            if (queueOn) { qi++; installNext(); }
        }
    }

    void onDownloadComplete(Intent i) {
        long id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        PackItem it = dlWait.remove(id);
        if (it == null) return;
        boolean ok = false;
        try {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            try (Cursor c = dm.query(new DownloadManager.Query().setFilterById(id))) {
                if (c != null && c.moveToFirst())
                    ok = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL;
            }
        } catch (Exception ignored) {}
        it.dlId = -1;
        boolean current = queueOn && qi < queue.size() && queue.get(qi) == it;
        if (ok) {
            new File(it.path + ".part").renameTo(new File(it.path));
            if (current) installOne(it);
            else if (screen.equals("pack")) packScreen();
        } else {
            new File(it.path + ".part").delete();
            toast("Téléchargement échoué : " + it.label);
            if (current) { qi++; installNext(); }
            else if (screen.equals("pack")) packScreen();
        }
    }

    void pack() {
        LinearLayout c0 = col(Gravity.CENTER);
        c0.addView(txt("Chargement du pack…", 18, Color.WHITE, true));
        show(c0, "pack");
        final List<PackItem> old = new ArrayList<>(packItems);
        new Thread(() -> {
            final List<PackItem> items = new ArrayList<>();
            PackageManager pm = getPackageManager();
            File dir = new File(getCacheDir(), "pack");
            dir.mkdirs();
            for (String n : packFiles()) {
                PackItem it = new PackItem();
                it.file = n; it.label = n;
                File f = new File(dir, n);
                try {
                    if (!f.exists()) copyAsset("pack/" + n, f);
                    it.path = f.getAbsolutePath();
                    android.content.pm.PackageInfo pi = pm.getPackageArchiveInfo(it.path, 0);
                    if (pi != null && pi.applicationInfo != null) {
                        pi.applicationInfo.sourceDir = it.path;
                        pi.applicationInfo.publicSourceDir = it.path;
                        it.pkg = pi.packageName;
                        it.label = pi.applicationInfo.loadLabel(pm).toString();
                        it.icon = pi.applicationInfo.loadIcon(pm);
                    }
                } catch (Exception ignored) {}
                items.add(it);
            }
            for (String[] o : onlineList()) {
                PackItem it = null;
                for (PackItem p : old) if (p.online && p.label.equals(o[0])) it = p;
                if (it == null) {
                    it = new PackItem();
                    it.online = true; it.label = o[0]; it.url = o[1];
                    it.file = o[0].replaceAll("[^A-Za-z0-9._-]", "_") + ".apk";
                    File ext = getExternalFilesDir(null);
                    File d = new File(ext != null ? ext : getCacheDir(), "pack");
                    d.mkdirs();
                    it.path = new File(d, it.file).getAbsolutePath();
                }
                items.add(it);
            }
            runOnUiThread(() -> { packItems = items; packScreen(); });
        }).start();
    }

    void packScreen() {
        if (!screen.equals("pack")) return;
        LinearLayout c = col(Gravity.CENTER_HORIZONTAL);
        c.addView(txt("Apps du pack", 30, Color.WHITE, true), lp(-2, -2, dp(6)));
        c.addView(txt("Android demande une confirmation pour chaque app.", 13, 0xCCFFFFFF, false), lp(-2, -2, dp(16)));
        int todo = 0;
        if (packItems.isEmpty()) c.addView(txt("Aucune app dans le pack.", 16, Color.WHITE, false), lp(-2, -2, dp(16)));
        for (PackItem it : packItems) {
            final PackItem fit = it;
            boolean inst = installedItem(it);
            if (!inst && (it.pkg != null || it.online)) todo++;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(neonBox(14, false));
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            ImageView iv = new ImageView(this);
            if (it.icon != null) iv.setImageDrawable(it.icon);
            else if (it.online) iv.setImageResource(android.R.drawable.sym_def_app_icon);
            row.addView(iv, new LinearLayout.LayoutParams(dp(46), dp(46)));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(12), 0, dp(8), 0);
            TextView t1 = new TextView(this);
            t1.setText(it.label); t1.setTextColor(Color.WHITE); t1.setTextSize(16);
            t1.setTypeface(Typeface.DEFAULT_BOLD);
            TextView t2 = new TextView(this);
            t2.setText(it.online ? "Téléchargé depuis internet" : (it.pkg == null ? "APK illisible" : it.pkg)); t2.setTextColor(0x99FFFFFF); t2.setTextSize(11);
            info.addView(t1); info.addView(t2);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView state = new TextView(this);
            it.stateTv = state;
            state.setText(stateText(it, inst));
            state.setTextColor(inst ? 0xFF69F0AE : Color.WHITE);
            state.setTextSize(13);
            row.addView(state);
            row.setOnClickListener(v -> {
                if (fit.pkg == null && !fit.online) return;
                tick();
                List<PackItem> one = new ArrayList<>();
                one.add(fit);
                startQueue(one);
            });
            c.addView(row, lp(dp(460), -2, dp(10)));
        }
        if (todo > 0) c.addView(nbtn("Installer tout (" + todo + ")", v -> {
            List<PackItem> l = new ArrayList<>();
            for (PackItem p : packItems) if ((p.pkg != null || p.online) && !installedItem(p)) l.add(p);
            startQueue(l);
        }), lp(dp(460), dp(56), dp(12)));
        c.addView(nbtn("Retour", v -> home()), lp(dp(460), dp(54), dp(12)));
        show(scroll(c), "pack");
    }

    void startQueue(List<PackItem> l) {
        if (l.isEmpty()) { toast("Tout est déjà installé."); return; }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            toast("Autorise COs à installer des apps, puis réessaie.");
            try { startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) {}
            return;
        }
        queue = new ArrayList<>(l);
        qi = 0;
        queueOn = true;
        installNext();
    }

    void installNext() {
        if (!queueOn) return;
        if (qi >= queue.size()) {
            queueOn = false;
            toast("Installation terminée");
            if (screen.equals("pack")) packScreen();
            return;
        }
        installOne(queue.get(qi));
    }

    void installOne(PackItem it) {
        if (it.online) {
            if (!new File(it.path).exists()) { downloadThenInstall(it); return; }
            if (it.pkg == null) {
                try {
                    android.content.pm.PackageInfo inf = getPackageManager().getPackageArchiveInfo(it.path, 0);
                    if (inf != null) it.pkg = inf.packageName;
                } catch (Exception ignored) {}
                if (it.pkg == null) { toast("APK illisible : " + it.label); qi++; installNext(); return; }
            }
        }
        try {
            PackageInstaller pi = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams prm = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            prm.setAppPackageName(it.pkg);
            int id = pi.createSession(prm);
            PackageInstaller.Session ss = pi.openSession(id);
            try (InputStream in = new FileInputStream(it.path); OutputStream out = ss.openWrite("app.apk", 0, -1)) {
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
                ss.fsync(out);
            }
            Intent ri = new Intent(ACT_INSTALL).setPackage(getPackageName());
            int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent pend = PendingIntent.getBroadcast(this, id, ri, fl);
            ss.commit(pend.getIntentSender());
            ss.close();
        } catch (Exception e) {
            toast("Échec : " + it.label);
            qi++;
            installNext();
        }
    }

    void onInstallResult(Intent i) {
        int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = (Intent) i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm == null) return;
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (resumed) {
                try { startActivity(confirm); } catch (Exception e) { toast("Confirmation impossible"); }
            } else pendingConfirm = confirm;
            return;
        }
        if (status != PackageInstaller.STATUS_SUCCESS) {
            String m = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            toast("Installation non terminée" + (m != null ? " : " + m : ""));
        }
        if (status == PackageInstaller.STATUS_SUCCESS && queueOn && qi < queue.size() && queue.get(qi).online)
            new File(queue.get(qi).path).delete();
        if (queueOn) {
            qi++;
            if (resumed) ui.postDelayed(this::installNext, 300);
            else pendingNext = true;
        }
    }

    // ---------------------------------------------------------------- widgets de l'accueil
    class NeonBar extends View {
        float frac = 0f;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        NeonBar(Context c) { super(c); }
        void set(float f) { frac = Math.max(0f, Math.min(1f, f)); invalidate(); }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), r = h / 2f;
            p.setShader(null);
            p.setColor(0x33FFFFFF);
            cv.drawRoundRect(0, 0, w, h, r, r, p);
            if (frac > 0) {
                p.setShader(new LinearGradient(0, 0, w, 0, C1, C2, Shader.TileMode.CLAMP));
                cv.drawRoundRect(0, 0, Math.max(h, w * frac), h, r, r, p);
                p.setShader(null);
            }
        }
    }

    View widgetRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] titles = {"Batterie", "Stockage", "Mémoire"};
        for (int i = 0; i < 3; i++) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(neonBox(14, false));
            card.setPadding(dp(14), dp(10), dp(14), dp(12));
            TextView t = new TextView(this);
            t.setText(titles[i]); t.setTextSize(12); t.setTextColor(0xB3FFFFFF);
            wv[i] = new TextView(this);
            wv[i].setTextSize(17); wv[i].setTextColor(Color.WHITE);
            wv[i].setShadowLayer(dp(8), 0, 0, C1);
            wv[i].setTypeface(Typeface.DEFAULT_BOLD);
            wv[i].setSingleLine(true);
            wb[i] = new NeonBar(this);
            card.addView(t);
            card.addView(wv[i]);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(6));
            bp.topMargin = dp(6);
            card.addView(wb[i], bp);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
            cp.setMargins(dp(4), 0, dp(4), 0);
            row.addView(card, cp);
        }
        updateWidgets();
        ui.removeCallbacks(widgetTick);
        ui.postDelayed(widgetTick, 4000);
        return row;
    }

    String fmtGo(long b) { return String.format(Locale.FRANCE, "%.1f Go", b / 1073741824.0); }

    void updateWidgets() {
        try {
            Intent b = registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b != null) {
                int lv = b.getIntExtra("level", 0), sc = Math.max(1, b.getIntExtra("scale", 100)), stt = b.getIntExtra("status", 0);
                boolean ch = (stt == 2 || stt == 5);
                wv[0].setText((ch ? "⚡ " : "") + (lv * 100 / sc) + " %");
                wb[0].set(lv / (float) sc);
            }
            android.os.StatFs s = new android.os.StatFs(android.os.Environment.getDataDirectory().getPath());
            long tot = s.getTotalBytes(), used = tot - s.getAvailableBytes();
            wv[1].setText(fmtGo(used) + " / " + fmtGo(tot));
            wb[1].set(used / (float) tot);
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long mu = mi.totalMem - mi.availMem;
            wv[2].setText(fmtGo(mu) + " / " + fmtGo(mi.totalMem));
            wb[2].set(mu / (float) mi.totalMem);
        } catch (Exception ignored) {}
    }

    // ---------------------------------------------------------------- dossiers d'apps
    static List<String> cut(String s, char c) {
        List<String> o = new ArrayList<>();
        int a = 0;
        for (int i = 0; i <= s.length(); i++) {
            if (i == s.length() || s.charAt(i) == c) { o.add(s.substring(a, i)); a = i + 1; }
        }
        return o;
    }

    List<Folder> loadFolders() {
        List<Folder> out = new ArrayList<>();
        for (String ln : cut(sp.getString("folders", ""), '\n')) {
            List<String> p = cut(ln, '|');
            if (p.size() < 2 || p.get(0).isEmpty()) continue;
            Folder f = new Folder();
            f.id = p.get(0);
            f.name = p.get(1);
            for (int i = 2; i < p.size(); i++) if (!p.get(i).isEmpty()) f.keys.add(p.get(i));
            out.add(f);
        }
        return out;
    }

    void saveFolders(List<Folder> l) {
        StringBuilder sb = new StringBuilder();
        for (Folder f : l) {
            sb.append(f.id).append('|').append(f.name.replace('|', '/').replace('\n', ' '));
            for (String k : f.keys) sb.append('|').append(k);
            sb.append('\n');
        }
        sp.edit().putString("folders", sb.toString()).apply();
    }

    String folderOf(String appKey) {
        for (Folder f : loadFolders()) if (f.keys.contains(appKey)) return f.id;
        return null;
    }

    void putInFolder(List<Folder> fl, Folder target, String k) {
        for (Folder f : fl) f.keys.remove(k);
        if (!target.keys.contains(k)) target.keys.add(k);
        saveFolders(fl);
    }

    void removeFromFolder(String fid, String k) {
        List<Folder> fl = loadFolders();
        for (Folder f : new ArrayList<>(fl)) {
            if (f.id.equals(fid)) { f.keys.remove(k); if (f.keys.isEmpty()) fl.remove(f); }
        }
        saveFolders(fl);
    }

    void askText(String title, String init, final StrCb cb) {
        final EditText e = new EditText(this);
        e.setText(init);
        e.setSingleLine(true);
        e.setSelection(e.getText().length());
        new AlertDialog.Builder(this).setTitle(title).setView(e)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("OK", (d, w) -> {
                String t = e.getText().toString().trim();
                if (!t.isEmpty()) cb.on(t);
            }).show();
    }

    void chooseFolder(final AppItem a, final String mode) {
        final List<Folder> fl = loadFolders();
        String[] names = new String[fl.size() + 1];
        for (int i = 0; i < fl.size(); i++) names[i] = fl.get(i).name;
        names[fl.size()] = "＋ Nouveau dossier…";
        new AlertDialog.Builder(this).setTitle("Mettre dans…").setItems(names, (dlg, w) -> {
            if (w < fl.size()) {
                putInFolder(fl, fl.get(w), key(a));
                toast("Ajouté au dossier");
                if (mode.equals("home")) home();
            } else {
                askText("Nom du dossier", "Dossier", t -> {
                    Folder f = new Folder();
                    f.id = Long.toString(System.currentTimeMillis(), 36);
                    f.name = t.replace('|', '/');
                    fl.add(f);
                    putInFolder(fl, f, key(a));
                    toast("Dossier créé");
                    if (mode.equals("home")) home();
                });
            }
        }).show();
    }

    Drawable folderIcon(Folder f, List<AppItem> all) {
        int S = 192;
        Bitmap b = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        pt.setStyle(Paint.Style.FILL);
        pt.setColor(withAlpha(C1, 60));
        cv.drawRoundRect(8, 8, S - 8, S - 8, 36, 36, pt);
        pt.setStyle(Paint.Style.STROKE);
        pt.setStrokeWidth(6);
        pt.setColor(C1);
        cv.drawRoundRect(8, 8, S - 8, S - 8, 36, 36, pt);
        int n = 0;
        PackageManager pm = getPackageManager();
        for (String k : f.keys) {
            if (n >= 4) break;
            for (AppItem a : all) {
                if (!key(a).equals(k)) continue;
                if (a.icon == null) a.icon = a.ri.loadIcon(pm);
                Drawable.ConstantState cs = a.icon.getConstantState();
                Drawable d = cs != null ? cs.newDrawable().mutate() : a.icon;
                int cx = 28 + (n % 2) * 70, cy = 28 + (n / 2) * 70;
                d.setBounds(cx, cy, cx + 60, cy + 60);
                d.draw(cv);
                n++;
                break;
            }
        }
        return new BitmapDrawable(getResources(), b);
    }

    void openFolder(final String id) {
        Folder found = null;
        for (Folder x : loadFolders()) if (x.id.equals(id)) found = x;
        if (found == null) return;
        final Folder fo = found;
        List<AppItem> all = loadApps();
        final List<AppItem> items = new ArrayList<>();
        for (String k : fo.keys) for (AppItem a : all) if (key(a).equals(k)) { items.add(a); break; }
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(neonBox(20, true));
        panel.setPadding(dp(16), dp(14), dp(16), dp(14));
        panel.addView(txt(fo.name, 22, Color.WHITE, true), lp(-2, -2, dp(10)));
        if (items.isEmpty()) {
            panel.addView(txt("Dossier vide.", 13, 0xCCFFFFFF, false), lp(-2, -2, dp(10)));
        } else {
            int rows = Math.max(1, Math.min(3, (items.size() + 3) / 4));
            panel.addView(makeGrid(items, "folder:" + id, null), new LinearLayout.LayoutParams(-1, dp(rows * (iconDp() + 46))));
        }
        panel.addView(nbtn("Fermer", v -> { if (folderDlg != null) folderDlg.dismiss(); }), lp(dp(200), dp(46), 0));
        folderDlg = new Dialog(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        folderDlg.setContentView(panel);
        if (folderDlg.getWindow() != null) {
            folderDlg.getWindow().setBackgroundDrawable(new ColorDrawable(0));
            folderDlg.getWindow().setLayout(dp(520), ViewGroup.LayoutParams.WRAP_CONTENT);
            folderDlg.getWindow().setDimAmount(0.6f);
        }
        folderDlg.show();
    }

    void folderItemMenu(final AppItem a, final String fid) {
        new AlertDialog.Builder(this).setTitle(a.label)
            .setItems(new String[]{"Retirer du dossier", "Infos de l'app", "Désinstaller"}, (dlg, w) -> {
                if (w == 0) {
                    removeFromFolder(fid, key(a));
                    if (folderDlg != null) folderDlg.dismiss();
                    home();
                    return;
                }
                Uri u = Uri.parse("package:" + a.pkg);
                Intent i = (w == 1) ? new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, u) : new Intent(Intent.ACTION_DELETE, u);
                try { startActivity(i); } catch (Exception e) { toast("Action impossible"); }
            }).show();
    }

    void folderMenu(final String fid) {
        new AlertDialog.Builder(this).setTitle("Dossier")
            .setItems(new String[]{"Renommer", "Supprimer le dossier"}, (dlg, w) -> {
                List<Folder> fl = loadFolders();
                Folder t = null;
                for (Folder f : fl) if (f.id.equals(fid)) t = f;
                if (t == null) return;
                if (w == 0) {
                    final Folder tf = t;
                    final List<Folder> ff = fl;
                    askText("Renommer", t.name, nm -> { tf.name = nm.replace('|', '/'); saveFolders(ff); home(); });
                } else {
                    fl.remove(t);
                    saveFolders(fl);
                    toast("Dossier supprimé (les apps restent installées)");
                    home();
                }
            }).show();
    }

    // ---------------------------------------------------------------- image de fond
    File wallFile() { return new File(getFilesDir(), "wall.jpg"); }

    void pickWall() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(i, WALL_REQ); } catch (Exception e) { toast("Sélecteur indisponible"); }
    }

    void importWall(final Uri u) {
        toast("Import de l'image…");
        new Thread(() -> {
            boolean ok = false;
            try {
                File tmp = new File(getCacheDir(), "wall_src");
                try (InputStream in = getContentResolver().openInputStream(u); OutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(tmp.getAbsolutePath(), o);
                int ss = 1;
                while (Math.max(o.outWidth, o.outHeight) / ss > 2400) ss *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = ss;
                Bitmap bm = BitmapFactory.decodeFile(tmp.getAbsolutePath(), o2);
                if (bm != null) {
                    int rot = 0;
                    try {
                        int ori = new android.media.ExifInterface(tmp.getAbsolutePath()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
                        if (ori == 6) rot = 90; else if (ori == 3) rot = 180; else if (ori == 8) rot = 270;
                    } catch (Exception ignored) {}
                    if (rot != 0) {
                        Matrix m = new Matrix();
                        m.postRotate(rot);
                        bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
                    }
                    try (OutputStream out = new FileOutputStream(wallFile())) { ok = bm.compress(Bitmap.CompressFormat.JPEG, 90, out); }
                }
                tmp.delete();
            } catch (Throwable e) { ok = false; }
            final boolean fin = ok;
            runOnUiThread(() -> {
                if (fin) {
                    wall = 6;
                    sp.edit().putInt("wall", 6).apply();
                    bg.rebuildWall();
                    bg.invalidate();
                    toast("Image appliquée");
                } else toast("Image illisible");
                if (screen.equals("custom")) custom();
            });
        }).start();
    }

    // ---------------------------------------------------------------- gestes
    void doAction(int a) {
        switch (a) {
            case 1: appsScreen("drawer"); break;
            case 2: appsScreen("drawer"); ui.postDelayed(this::focusSearch, 350); break;
            case 3: settings(); break;
            case 4: os(); break;
            case 5: musicScreen(); break;
            case 6: lockScreen(); break;
            default: break;
        }
    }

    void focusSearch() {
        if (searchEt == null || !screen.equals("drawer")) return;
        searchEt.requestFocus();
        try { ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(searchEt, InputMethodManager.SHOW_IMPLICIT); } catch (Exception ignored) {}
    }

    void lockScreen() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName cn = new ComponentName(this, AdminRcv.class);
            if (dpm.isAdminActive(cn)) { dpm.lockNow(); return; }
            Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn);
            i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Nécessaire pour verrouiller l'écran avec un geste.");
            startActivityForResult(i, ADMIN_REQ);
        } catch (Exception e) { toast("Verrouillage indisponible"); }
    }

    // ---------------------------------------------------------------- dock
    View dockBtnView(String label, View.OnClickListener l) {
        TextView t = nbtn(label, l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(50), 1f);
        p.setMargins(dp(4), dp(8), dp(4), 0);
        t.setLayoutParams(p);
        return t;
    }

    View dockAppView(final AppItem a) {
        ImageView iv = new ImageView(this);
        int pd = dp(8);
        iv.setPadding(pd, pd, pd, pd);
        Drawable bgd = iconBg(50);
        if (bgd != null) iv.setBackground(bgd);
        if (a.icon == null) a.icon = a.ri.loadIcon(getPackageManager());
        iv.setImageDrawable(a.icon);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(54), dp(54));
        p.setMargins(dp(4), dp(8), dp(4), 0);
        iv.setLayoutParams(p);
        iv.setOnClickListener(v -> { tick(); openApp(a); });
        return iv;
    }

    List<View> dockViews(List<AppItem> all) {
        List<View> out = new ArrayList<>();
        if (sp.getBoolean("dFiles", true)) out.add(dockBtnView("Fichiers", v -> pickFile()));
        if (sp.getBoolean("dSettings", true)) out.add(dockBtnView("Réglages", v -> settings()));
        if (sp.getBoolean("dMenu", false)) out.add(dockBtnView("Menu", v -> menu()));
        for (String k : cut(sp.getString("dock", ""), '\n')) {
            if (k.isEmpty()) continue;
            for (AppItem a : all) if (key(a).equals(k)) { out.add(dockAppView(a)); break; }
        }
        return out;
    }

    // ---------------------------------------------------------------- icones : taille, forme, placement libre
    int iconDp() { return sp.getInt("isize", 62); }

    Drawable iconBg(int sz) {
        switch (sp.getInt("ishape", 0)) {
            case 1: return neonBox(sz / 2f - 3, false);
            case 2: return neonBox(4, false);
            case 3: return null;
            default: return neonBox(16, false);
        }
    }

    class Desk extends FrameLayout {
        final int cw, ch;
        Desk(Context c, int cw, int ch) { super(c); this.cw = cw; this.ch = ch; }

        @Override protected void onMeasure(int wm, int hm) {
            super.onMeasure(wm, hm);
            int a = MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), b = MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY);
            for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(a, b);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int w = r - l, h = b - t, gap = dp(10), cols = Math.max(1, w / (cw + gap)), n = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                Pos p = (Pos) c.getTag();
                int x, y;
                if (p.auto) { x = (n % cols) * (cw + gap); y = (n / cols) * (ch + gap); n++; }
                else { x = (int) (p.fx * Math.max(0, w - cw)); y = (int) (p.fy * Math.max(0, h - ch)); }
                c.layout(x, y, x + cw, y + ch);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (homeGd != null) homeGd.onTouchEvent(e);
            return true;
        }

        void freeze() { // fixe les icones encore en placement automatique a leur place actuelle
            float ax = Math.max(1, getWidth() - cw), ay = Math.max(1, getHeight() - ch);
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                Pos p = (Pos) c.getTag();
                if (p.auto) {
                    p.fx = Math.max(0f, Math.min(1f, c.getLeft() / ax));
                    p.fy = Math.max(0f, Math.min(1f, c.getTop() / ay));
                    p.auto = false;
                }
            }
        }

        void moveTo(View v, float nx, float ny) {
            freeze();
            Pos p = (Pos) v.getTag();
            float ax = Math.max(1, getWidth() - cw), ay = Math.max(1, getHeight() - ch);
            p.fx = Math.max(0f, Math.min(1f, nx / ax));
            p.fy = Math.max(0f, Math.min(1f, ny / ay));
            requestLayout();
        }

        void drop(View v) {
            if (sp.getBoolean("snap", false)) {
                Pos p = (Pos) v.getTag();
                float ax = Math.max(1, getWidth() - cw), ay = Math.max(1, getHeight() - ch);
                float sx = cw + dp(10), sy = ch + dp(10);
                p.fx = Math.max(0f, Math.min(1f, Math.round(p.fx * ax / sx) * sx / ax));
                p.fy = Math.max(0f, Math.min(1f, Math.round(p.fy * ay / sy) * sy / ay));
                requestLayout();
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < getChildCount(); i++) {
                Pos p = (Pos) getChildAt(i).getTag();
                if (!p.auto) sb.append(p.key).append('=').append(p.fx).append(',').append(p.fy).append('\n');
            }
            sp.edit().putString("pos", sb.toString()).apply();
        }
    }

    View makeDesk(List<AppItem> shown) {
        int sz = iconDp();
        boolean lab = sp.getBoolean("ilabels", true);
        int cw = dp(sz + 26), ch = dp(sz + (lab ? 26 : 8));
        Desk desk = new Desk(this, cw, ch);
        Map<String, float[]> pm = new HashMap<>();
        for (String ln : sp.getString("pos", "").split("\n")) {
            int k = ln.indexOf('='), m = ln.indexOf(',');
            if (k > 0 && m > k) {
                try { pm.put(ln.substring(0, k), new float[]{Float.parseFloat(ln.substring(k + 1, m)), Float.parseFloat(ln.substring(m + 1))}); }
                catch (Exception ignored) {}
            }
        }
        PackageManager pk = getPackageManager();
        for (AppItem a : shown) {
            Pos p = new Pos();
            p.key = key(a);
            float[] f = pm.get(p.key);
            if (f != null) { p.fx = f[0]; p.fy = f[1]; p.auto = false; }
            desk.addView(makeCell(a, p, desk, sz, lab, pk), new FrameLayout.LayoutParams(cw, ch));
        }
        return desk;
    }

    View makeCell(final AppItem a, Pos p, final Desk desk, int sz, boolean lab, PackageManager pm) {
        final LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(2), dp(4), dp(2), dp(2));
        ImageView iv = new ImageView(this);
        int pd = dp(sz * 0.15f);
        iv.setPadding(pd, pd, pd, pd);
        Drawable bgd = iconBg(sz);
        if (bgd != null) iv.setBackground(bgd);
        if (a.icon == null) a.icon = a.ri.loadIcon(pm);
        iv.setImageDrawable(a.icon);
        cell.addView(iv, new LinearLayout.LayoutParams(dp(sz), dp(sz)));
        if (lab) {
            TextView tv = new TextView(this);
            tv.setText(a.label); tv.setTextSize(12); tv.setTextColor(Color.WHITE);
            tv.setGravity(Gravity.CENTER); tv.setMaxLines(1);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setShadowLayer(dp(4), 0, 0, C1);
            tv.setPadding(0, dp(4), 0, 0);
            cell.addView(tv, new LinearLayout.LayoutParams(-1, -2));
        }
        cell.setTag(p);
        final float[] st = new float[4];
        final boolean[] md = new boolean[2]; // [0] deplacement actif, [1] a bouge
        final Runnable lpRun = () -> {
            md[0] = true;
            cell.bringToFront();
            cell.setScaleX(1.12f); cell.setScaleY(1.12f);
            cell.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        };
        cell.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    md[0] = false; md[1] = false;
                    st[0] = e.getRawX(); st[1] = e.getRawY(); st[2] = v.getLeft(); st[3] = v.getTop();
                    ui.postDelayed(lpRun, 380);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!md[0]) {
                        if (Math.hypot(e.getRawX() - st[0], e.getRawY() - st[1]) > dp(10)) ui.removeCallbacks(lpRun);
                        return true;
                    }
                    md[1] = true;
                    desk.moveTo(v, st[2] + e.getRawX() - st[0], st[3] + e.getRawY() - st[1]);
                    return true;
                case MotionEvent.ACTION_UP:
                    ui.removeCallbacks(lpRun);
                    v.setScaleX(1f); v.setScaleY(1f);
                    if (md[0]) {
                        if (md[1]) desk.drop(v); else if (a.folderId != null) folderMenu(a.folderId); else appMenu(a, "home");
                    } else { tick(); if (a.folderId != null) openFolder(a.folderId); else openApp(a); }
                    md[0] = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    ui.removeCallbacks(lpRun);
                    v.setScaleX(1f); v.setScaleY(1f);
                    md[0] = false;
                    return true;
                default:
                    return false;
            }
        });
        return cell;
    }

    View sliderRow(final String label, final int min, int max, int val, final IntCb cb) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        final TextView t = txt(label + " : " + val, 14, 0xCCFFFFFF, false);
        r.addView(t, lp(-2, -2, dp(2)));
        final SeekBar[] sb = new SeekBar[1];
        sb[0] = seek(max - min, val - min, () -> {
            int v = sb[0].getProgress() + min;
            t.setText(label + " : " + v);
            cb.on(v);
        });
        GradientDrawable tr = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{withAlpha(C1, 0x66), withAlpha(C2, 0x66)});
        tr.setCornerRadius(dp(18));
        sb[0].setBackground(tr);
        r.addView(sb[0], lp(dp(320), dp(34), dp(8)));
        return r;
    }

    static final String[] SHAPES = {"Carré arrondi", "Rond", "Carré", "Sans fond"};

    void custom() {
        LinearLayout c = col(Gravity.CENTER_HORIZONTAL);
        c.addView(txt("Personnalisation", 30, Color.WHITE, true), lp(-2, -2, dp(14)));
        c.addView(sliderRow("Taille des icônes", 30, 110, iconDp(), v -> sp.edit().putInt("isize", v).apply()), lp(dp(340), -2, dp(10)));
        c.addView(nbtn("Forme des icônes : " + SHAPES[sp.getInt("ishape", 0)], v -> {
            sp.edit().putInt("ishape", (sp.getInt("ishape", 0) + 1) % SHAPES.length).apply();
            custom();
        }), lp(dp(340), dp(54), dp(12)));
        c.addView(toggle("Noms des apps", "ilabels"), lp(dp(340), dp(54), dp(12)));
        c.addView(toggle("Placement aimanté", "snap", false), lp(dp(340), dp(54), dp(12)));
        c.addView(nbtn("Réinitialiser les positions", v -> {
            sp.edit().remove("pos").apply();
            toast("Positions réinitialisées");
        }), lp(dp(340), dp(54), dp(16)));
        c.addView(txt("Fond d'écran", 14, 0xCCFFFFFF, false), lp(-2, -2, dp(8)));
        c.addView(nbtn("Choisir une image de fond", v -> pickWall()), lp(dp(340), dp(54), dp(12)));
        if (wallFile().exists()) c.addView(nbtn("Retirer l'image", v -> {
            wallFile().delete();
            if (wall == 6) { wall = 0; sp.edit().putInt("wall", 0).apply(); }
            bg.rebuildWall();
            bg.invalidate();
            custom();
        }), lp(dp(340), dp(54), dp(12)));
        c.addView(sliderRow("Flou de l'image", 0, 25, sp.getInt("wblur", 6), v -> { sp.edit().putInt("wblur", v).apply(); bg.rebuildWall(); bg.invalidate(); }), lp(dp(340), -2, dp(10)));
        c.addView(sliderRow("Voile néon (%)", 0, 100, wveil, v -> { wveil = v; sp.edit().putInt("wveil", v).apply(); bg.invalidate(); }), lp(dp(340), -2, dp(16)));
        c.addView(txt("Dock", 14, 0xCCFFFFFF, false), lp(-2, -2, dp(8)));
        c.addView(nbtn("Apps du dock", v -> appsScreen("dock")), lp(dp(340), dp(54), dp(12)));
        c.addView(toggle("Bouton Fichiers", "dFiles", true), lp(dp(340), dp(54), dp(12)));
        c.addView(toggle("Bouton Réglages", "dSettings", true), lp(dp(340), dp(54), dp(12)));
        c.addView(toggle("Bouton Menu de démarrage", "dMenu", false), lp(dp(340), dp(54), dp(16)));
        c.addView(txt("Gestes (sur l'accueil)", 14, 0xCCFFFFFF, false), lp(-2, -2, dp(8)));
        c.addView(nbtn("Balayer vers le haut : " + ACTS[sp.getInt("gUp", 1)], v -> {
            sp.edit().putInt("gUp", (sp.getInt("gUp", 1) + 1) % ACTS.length).apply();
            custom();
        }), lp(dp(340), dp(54), dp(12)));
        c.addView(nbtn("Balayer vers le bas : " + ACTS[sp.getInt("gDown", 2)], v -> {
            sp.edit().putInt("gDown", (sp.getInt("gDown", 2) + 1) % ACTS.length).apply();
            custom();
        }), lp(dp(340), dp(54), dp(12)));
        c.addView(nbtn("Double appui : " + ACTS[sp.getInt("gDouble", 0)], v -> {
            sp.edit().putInt("gDouble", (sp.getInt("gDouble", 0) + 1) % ACTS.length).apply();
            custom();
        }), lp(dp(340), dp(54), dp(16)));
        c.addView(sliderRow("Remplissage des boutons (%)", 0, 100, fillA, v -> { fillA = v; sp.edit().putInt("fillA", v).apply(); }), lp(dp(340), -2, dp(10)));
        c.addView(sliderRow("Opacité du fond (%)", 0, 100, bgOp, v -> { bgOp = v; sp.edit().putInt("bgOp", v).apply(); bg.invalidate(); }), lp(dp(340), -2, dp(10)));
        c.addView(txt("Sur l'accueil : appui long sur une icône puis glisse pour la placer où tu veux. Appui long sans bouger : menu de l'app (épingler, dossier, infos). Les gestes marchent sur l'espace libre, l'horloge et les widgets.", 12, 0x99FFFFFF, false), lp(dp(340), -2, dp(14)));
        c.addView(nbtn("Retour", v -> settings()), lp(dp(340), dp(54), dp(12)));
        show(scroll(c), "custom");
    }

    // ---------------------------------------------------------------- reglages
    void settings() {
        LinearLayout c = col(Gravity.CENTER_HORIZONTAL);
        c.addView(txt("Réglages", 34, Color.WHITE, true), lp(-2, -2, dp(18)));
        c.addView(txt("Thème néon", 15, 0xCCFFFFFF, false), lp(-2, -2, dp(10)));
        LinearLayout[] rows = {new LinearLayout(this), new LinearLayout(this)};
        for (int i = 0; i < THEMES.length; i++) {
            final int k = i;
            View sw = new View(this);
            GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, THEMES[i]);
            gd.setShape(GradientDrawable.OVAL);
            gd.setStroke(dp(3), sp.getInt("theme", 0) == i ? Color.WHITE : 0x55FFFFFF);
            sw.setBackground(gd);
            sw.setOnClickListener(v -> {
                tick();
                sp.edit().putInt("theme", k).apply();
                loadTheme(); bg.invalidate(); settings();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(46), dp(46));
            p.setMargins(dp(5), 0, dp(5), 0);
            rows[i / 5].addView(sw, p);
        }
        c.addView(rows[0], lp(-2, -2, dp(10)));
        c.addView(rows[1], lp(-2, -2, dp(16)));
        c.addView(nbtn("Palette personnalisée", v -> palette()), lp(dp(320), dp(54), dp(18)));
        c.addView(toggle("Sons", "sfx"), lp(dp(320), dp(54), dp(12)));
        c.addView(toggle("Intro au démarrage", "intro"), lp(dp(320), dp(54), dp(12)));
        c.addView(toggle("Menu de choix au démarrage", "menu"), lp(dp(320), dp(54), dp(12)));
        c.addView(toggle("Widgets de l'accueil", "widgets"), lp(dp(320), dp(54), dp(12)));
        c.addView(nbtn("Personnalisation", v -> custom()), lp(dp(320), dp(54), dp(12)));
        c.addView(nbtn("Fond d'écran : " + WALLS[wall], v -> {
            do { wall = (wall + 1) % WALLS.length; } while (wall == 6 && !wallFile().exists());
            bg.rebuildWall();
            sp.edit().putInt("wall", wall).apply();
            bg.invalidate();
            settings();
        }), lp(dp(320), dp(54), dp(12)));
        c.addView(nbtn("Musique ♪ : " + (sp.getBoolean("music", false) ? "ON" : "OFF"), v -> musicScreen()), lp(dp(320), dp(54), dp(12)));
        c.addView(nbtn("Apps de l'accueil", v -> appsScreen("pick")), lp(dp(320), dp(54), dp(12)));
        c.addView(nbtn("Mon OS", v -> os()), lp(dp(320), dp(54), dp(12)));
        if (hasPack()) c.addView(nbtn("Pack d'apps", v -> pack()), lp(dp(320), dp(54), dp(12)));
        c.addView(nbtn("Rejouer l'intro", v -> intro()), lp(dp(320), dp(54), dp(12)));
        c.addView(nbtn("Menu de démarrage", v -> menu()), lp(dp(320), dp(54), dp(12)));
        c.addView(nbtn("Réglages Android", v -> {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception e) { toast("Impossible d'ouvrir les réglages"); }
        }), lp(dp(320), dp(54), dp(12)));
        if (!isHome()) c.addView(nbtn("Définir COs comme launcher", v -> askHome()), lp(dp(320), dp(54), dp(12)));
        c.addView(nbtn("Retour", v -> home()), lp(dp(320), dp(54), dp(12)));
        show(scroll(c), "settings");
    }

    TextView toggle(String label, final String key) { return toggle(label, key, true); }

    TextView toggle(String label, final String key, boolean def) {
        final boolean on = sp.getBoolean(key, def);
        return nbtn(label + " : " + (on ? "ON" : "OFF"), v -> {
            sp.edit().putBoolean(key, !on).apply();
            if (screen.equals("custom")) custom(); else settings();
        });
    }

    // ---------------------------------------------------------------- palette personnalisee
    View palPreview;

    void palette() {
        LinearLayout c = col(Gravity.CENTER_HORIZONTAL);
        c.addView(txt("Palette personnalisée", 28, Color.WHITE, true), lp(-2, -2, dp(18)));
        palPreview = new View(this);
        c.addView(palPreview, lp(dp(320), dp(70), dp(16)));
        updatePreview();
        c.addView(txt("Couleur principale (contours, lueur)", 14, 0xCCFFFFFF, false), lp(-2, -2, dp(6)));
        colorPicker(c, true);
        c.addView(txt("Couleur secondaire (dégradés, fond)", 14, 0xCCFFFFFF, false), lp(-2, -2, dp(6)));
        colorPicker(c, false);
        c.addView(nbtn("Retour", v -> settings()), lp(dp(320), dp(54), dp(12)));
        show(scroll(c), "palette");
    }

    void updatePreview() {
        if (palPreview == null) return;
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{C1, C2});
        g.setCornerRadius(dp(16));
        g.setStroke(dp(2), Color.WHITE);
        palPreview.setBackground(g);
    }

    SeekBar seek(int max, int val, final Runnable onChange) {
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        s.setProgress(val);
        s.setProgressDrawable(new ColorDrawable(0));
        s.setPadding(dp(14), 0, dp(14), 0);
        s.getThumb().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int pr, boolean user) { onChange.run(); }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        return s;
    }

    void styleSat(SeekBar s, float hue) {
        GradientDrawable sg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{Color.WHITE, Color.HSVToColor(new float[]{hue, 1f, 1f})});
        sg.setCornerRadius(dp(18));
        s.setBackground(sg);
    }

    void colorPicker(LinearLayout parent, final boolean first) {
        final float[] hsv = new float[3];
        Color.colorToHSV(first ? C1 : C2, hsv);
        final SeekBar[] sb = new SeekBar[2];
        Runnable change = () -> {
            hsv[0] = sb[0].getProgress();
            hsv[1] = sb[1].getProgress() / 100f;
            hsv[2] = 1f;
            int col = Color.HSVToColor(hsv);
            if (first) C1 = col; else C2 = col;
            sp.edit().putInt("theme", -1).putInt("c1", C1).putInt("c2", C2).apply();
            styleSat(sb[1], hsv[0]);
            updatePreview();
            bg.invalidate();
        };
        sb[0] = seek(360, (int) hsv[0], change);
        sb[1] = seek(100, (int) (hsv[1] * 100), change);
        GradientDrawable rb = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000});
        rb.setCornerRadius(dp(18));
        sb[0].setBackground(rb);
        styleSat(sb[1], hsv[0]);
        parent.addView(txt("Teinte", 12, 0x99FFFFFF, false), lp(-2, -2, dp(2)));
        parent.addView(sb[0], lp(dp(320), dp(34), dp(8)));
        parent.addView(txt("Intensité", 12, 0x99FFFFFF, false), lp(-2, -2, dp(2)));
        parent.addView(sb[1], lp(dp(320), dp(34), dp(18)));
    }

    // ---------------------------------------------------------------- apps
    List<AppItem> loadApps() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> l = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        List<AppItem> out = new ArrayList<>();
        for (ResolveInfo r : l) {
            if (r.activityInfo.packageName.equals(getPackageName())) continue;
            AppItem a = new AppItem();
            a.ri = r; a.label = r.loadLabel(pm).toString();
            a.pkg = r.activityInfo.packageName; a.cls = r.activityInfo.name;
            out.add(a);
        }
        Collections.sort(out, (x, y) -> x.label.compareToIgnoreCase(y.label));
        return out;
    }

    void openApp(AppItem a) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setClassName(a.pkg, a.cls);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception e) { toast("Impossible d'ouvrir " + a.label); }
    }

    void appMenu(final AppItem a, final String mode) {
        final List<String> fl = favs();
        final boolean pinned = fl.contains(key(a));
        final String inF = folderOf(key(a));
        String[] items = {pinned ? "Retirer de l'accueil" : "Épingler à l'accueil",
            inF != null ? "Retirer du dossier" : "Mettre dans un dossier…", "Infos de l'app", "Désinstaller"};
        new AlertDialog.Builder(this).setTitle(a.label).setItems(items, (dlg, w) -> {
            if (w == 0) {
                if (pinned) fl.remove(key(a)); else fl.add(key(a));
                saveFavs(fl);
                toast(pinned ? "Retiré de l'accueil" : "Ajouté à l'accueil");
                if (mode.equals("home")) home();
                return;
            }
            if (w == 1) {
                if (inF != null) { removeFromFolder(inF, key(a)); toast("Retiré du dossier"); if (mode.equals("home")) home(); }
                else chooseFolder(a, mode);
                return;
            }
            Uri u = Uri.parse("package:" + a.pkg);
            Intent i = (w == 2) ? new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, u) : new Intent(Intent.ACTION_DELETE, u);
            try { startActivity(i); } catch (Exception e) { toast("Action impossible"); }
        }).show();
    }


    // Retrouve une app par nom (ou paquet), parmi les apps normales et les launchers
    ResolveInfo find(String key) {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> all = new ArrayList<>();
        all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0));
        for (ResolveInfo r : all) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            String label = r.loadLabel(pm).toString().toLowerCase();
            if (label.contains(key) || pkg.toLowerCase().contains(key)) return r;
        }
        return null;
    }

    boolean launch(String key, String nom) {
        ResolveInfo r = find(key);
        if (r == null) { toast(nom + " n'est pas installé."); return false; }
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setClassName(r.activityInfo.packageName, r.activityInfo.name);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); return true; }
        catch (Exception e) { toast("Impossible d'ouvrir " + nom); return false; }
    }

    // ---------------------------------------------------------------- launcher par defaut
    boolean isHome() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.app.role.RoleManager rm = (android.app.role.RoleManager) getSystemService(Context.ROLE_SERVICE);
                return rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME);
            }
            ResolveInfo r = getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
            return r != null && getPackageName().equals(r.activityInfo.packageName);
        } catch (Exception e) { return false; }
    }

    void askHome() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.app.role.RoleManager rm = (android.app.role.RoleManager) getSystemService(Context.ROLE_SERVICE);
                if (rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                    startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), HOME_REQ);
                    return;
                }
            }
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Exception e) {
            toast("Paramètres > Apps > Apps par défaut > Application d'accueil");
        }
    }

    // ---------------------------------------------------------------- fichiers .apk / .exe
    void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == WALL_REQ) {
            if (res == RESULT_OK && data != null && data.getData() != null) importWall(data.getData());
            return;
        }
        if (req == MUSIC_REQ) {
            if (res != RESULT_OK || data == null) return;
            List<Uri> us = new ArrayList<>();
            android.content.ClipData cd = data.getClipData();
            if (cd != null) { for (int k = 0; k < cd.getItemCount(); k++) us.add(cd.getItemAt(k).getUri()); }
            else if (data.getData() != null) us.add(data.getData());
            importMusic(us);
            return;
        }
        if (req == FILE_REQ) {
            Uri[] out = (res == RESULT_OK && data != null && data.getData() != null) ? new Uri[]{data.getData()} : null;
            if (fileCb != null) { fileCb.onReceiveValue(out); fileCb = null; }
            return;
        }
        if (req != PICK || res != RESULT_OK || data == null) return;
        Uri u = data.getData();
        String name = "";
        try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
        } catch (Exception ignored) {}
        String n = name.toLowerCase();
        if (n.endsWith(".apk")) {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(u, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try { startActivity(i); } catch (Exception e) { toast("Autorise COs à installer des apps (Paramètres)."); }
        } else if (n.endsWith(".exe")) {
            toast("Les .exe passent par Winlator : ouvre-le puis choisis ce fichier.");
            launch("winlator", "Winlator");
        } else {
            toast("Format non pris en charge : " + name);
        }
    }

    // ---------------------------------------------------------------- sons (synthetises, aucun fichier)
    short[] introPcm() {
        int rate = 22050, n = rate * 4;
        short[] d = new short[n];
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) rate, s = 0;
            if (t > 0.3 && t < 1.1) { // montee d'energie
                double u = (t - 0.3) / 0.8;
                ph += 2 * Math.PI * (180 + 900 * u * u) / rate;
                s += 0.30 * Math.sin(ph) * Math.min(1, u * 3) * (1 - 0.3 * u);
            }
            if (t >= 1.1) { // impact grave
                double u = t - 1.1;
                s += 0.75 * Math.sin(2 * Math.PI * (60 - 20 * Math.min(u, 1)) * u) * Math.exp(-4 * u);
            }
            double[][] chimes = {{1.12, 1320}, {1.30, 1760}, {1.50, 2640}};
            for (double[] ch : chimes) {
                double u = t - ch[0];
                if (u > 0) s += 0.16 * Math.sin(2 * Math.PI * ch[1] * u) * Math.exp(-3.2 * u);
            }
            if (t > 3.5) s *= Math.max(0, (4.0 - t) / 0.5);
            s = Math.max(-1, Math.min(1, s));
            d[i] = (short) (s * 30000);
        }
        return d;
    }

    short[] blip(double f, int ms) {
        int rate = 22050, n = rate * ms / 1000;
        short[] d = new short[n];
        for (int i = 0; i < n; i++) {
            double u = i / (double) n;
            d[i] = (short) (Math.sin(2 * Math.PI * f * i / rate) * Math.exp(-5 * u) * 7000);
        }
        return d;
    }

    void tick() { playPcm(blip(1500, 45), 22050); }

    void playPcm(short[] pcm, int rate) {
        if (!sp.getBoolean("sfx", true)) return;
        try {
            final AudioTrack at = new AudioTrack(AudioManager.STREAM_MUSIC, rate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, pcm.length * 2, AudioTrack.MODE_STATIC);
            at.write(pcm, 0, pcm.length);
            at.play();
            ui.postDelayed(() -> { try { at.release(); } catch (Exception ignored) {} }, pcm.length * 1000L / rate + 500);
        } catch (Exception ignored) {}
    }

    // ---------------------------------------------------------------- intro
    void intro() {
        inIntro = true;
        IntroView iv = new IntroView(this, logo);
        screen = "intro";
        content.removeAllViews();
        content.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        iv.onDone = () -> { introDone = true; enter(); };
        playPcm(introPcm(), 22050);
        iv.start();
    }

    class IntroView extends View {
        final Bitmap lg;
        long t0 = 0;
        boolean done = false;
        Runnable onDone;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint fp = new Paint();
        final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint tg = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF dst = new RectF();
        final Rect src = new Rect();

        IntroView(Context c, Bitmap b) {
            super(c);
            lg = b;
            fp.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            tp.setColor(Color.WHITE); tp.setTextAlign(Paint.Align.CENTER);
            tp.setTypeface(Typeface.DEFAULT_BOLD);
            tg.setColor(Color.WHITE); tg.setTextAlign(Paint.Align.CENTER);
            tg.setLetterSpacing(0.5f);
        }

        void start() { t0 = System.nanoTime(); invalidate(); }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) finish();
            return true;
        }

        void finish() {
            if (done) return;
            done = true;
            if (onDone != null) onDone.run();
        }

        @Override protected void onDraw(Canvas cv) {
            if (done) return;
            float t = (System.nanoTime() - t0) / 1e9f;
            float w = getWidth(), h = getHeight();
            cv.drawColor(0xFF000000);

            float a = cl((t - 0.3f) / 0.8f);
            float k = cl((t - 0.3f) / 1.0f);
            float ob = (float) (1 + 2.70158 * Math.pow(k - 1, 3) + 1.70158 * Math.pow(k - 1, 2));
            float size = Math.min(w, h) * 0.40f * (0.55f + 0.45f * ob);
            float cx = w / 2, cy = h * 0.40f;
            float pulse = 0.8f + 0.2f * (float) Math.sin(t * 5);

            gp.setShader(new RadialGradient(cx, cy, size * 1.25f,
                new int[]{withAlpha(C1, (int) (110 * a * pulse)), withAlpha(C2, (int) (45 * a)), 0},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, size * 1.25f, gp);

            float half = size / 2;
            if (lg != null) {
                // reflet "eau" : tranches decalees, estompees vers le bas
                float top = cy + half + dp(6);
                int slices = 40;
                float sh = size / slices;
                int layer = cv.saveLayer(0, top, w, h, null);
                p.setAlpha((int) (150 * a));
                for (int i = 0; i < slices; i++) {
                    float f = i / (float) slices;
                    float off = (float) Math.sin(f * 16 + t * 5) * dp(4) * (0.2f + f);
                    src.set(0, (int) (lg.getHeight() * (1 - (i + 1) / (float) slices)),
                        lg.getWidth(), (int) (lg.getHeight() * (1 - i / (float) slices)));
                    dst.set(cx - half + off, top + i * sh, cx + half + off, top + (i + 1) * sh + 1);
                    cv.drawBitmap(lg, src, dst, p);
                }
                fp.setShader(new LinearGradient(0, top, 0, top + size * 0.8f, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
                cv.drawRect(0, top, w, h, fp);
                cv.restoreToCount(layer);

                p.setAlpha((int) (255 * a));
                dst.set(cx - half, cy - half, cx + half, cy + half);
                cv.drawBitmap(lg, null, dst, p);
            } else {
                tp.setTextSize(size * 0.5f);
                tp.setShadowLayer(dp(24), 0, 0, C1);
                tp.setAlpha((int) (255 * a));
                cv.drawText("COs", cx, cy + size * 0.18f, tp);
            }

            float ta = cl((t - 1.6f) / 0.6f);
            tg.setTextSize(dp(15));
            tg.setShadowLayer(dp(10), 0, 0, C2);
            tg.setAlpha((int) (220 * ta));
            cv.drawText("CUSTOM OS", cx, h * 0.90f, tg);

            if (t > 1.1f) { // flash sur l'impact
                float fl = cl(1 - (t - 1.1f) / 0.25f) * 0.28f;
                if (fl > 0) cv.drawColor(withAlpha(0xFFFFFF, (int) (255 * fl)));
            }
            float fo = cl((t - 3.7f) / 0.5f);
            if (fo > 0) cv.drawColor(withAlpha(0x000000, (int) (255 * fo)));
            if (t >= 4.2f) { finish(); return; }
            postInvalidateOnAnimation();
        }
    }

    // ---------------------------------------------------------------- fond neon anime
    class NeonBg extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final long t0 = System.nanoTime();
        final float[] sx = new float[40], sy = new float[40], ss = new float[40];
        final float[] ra = new float[120], rb2 = new float[120], rc = new float[120];

        NeonBg(Context c) {
            super(c);
            Random r = new Random(7);
            for (int i = 0; i < 40; i++) { sx[i] = r.nextFloat(); sy[i] = r.nextFloat() * 0.55f; ss[i] = 0.5f + r.nextFloat(); }
            for (int i = 0; i < 120; i++) { ra[i] = r.nextFloat(); rb2[i] = r.nextFloat(); rc[i] = 0.4f + r.nextFloat(); }
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), t = (System.nanoTime() - t0) / 1e9f;
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, 0, 0, h, new int[]{DARK, 0xFF120A30, 0xFF2A0F4A},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, p);
            p.setShader(null);
            switch (wall) {
                case 1: rain(cv, w, h, t); break;
                case 2: aurora(cv, w, h, t); break;
                case 3: particles(cv, w, h, t); break;
                case 4: warp(cv, w, h, t); break;
                case 5: break;
                case 6: image(cv, w, h); break;
                default: grid(cv, w, h, t);
            }
            if (bgOp < 100) {
                p.setStyle(Paint.Style.FILL);
                p.setShader(null);
                p.setColor(withAlpha(DARK, (100 - bgOp) * 255 / 100));
                cv.drawRect(0, 0, w, h, p);
            }
            if (wall != 5 && wall != 6) postInvalidateOnAnimation();
        }

        Bitmap wallBmp;

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            rebuildWall();
        }

        void rebuildWall() {
            wallBmp = null;
            int w = getWidth(), h = getHeight();
            if (wall != 6 || w <= 0 || h <= 0) return;
            File f = wallFile();
            if (!f.exists()) return;
            try {
                Bitmap src = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (src == null) return;
                float sc = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
                int cw = Math.min(src.getWidth(), Math.round(w / sc)), chh = Math.min(src.getHeight(), Math.round(h / sc));
                Bitmap crop = Bitmap.createBitmap(src, (src.getWidth() - cw) / 2, (src.getHeight() - chh) / 2, cw, chh);
                int blur = sp.getInt("wblur", 6);
                if (blur <= 0) {
                    wallBmp = Bitmap.createScaledBitmap(crop, w, h, true);
                } else {
                    float div = 1f + blur / 2.5f;
                    Bitmap small = Bitmap.createScaledBitmap(crop, Math.max(8, Math.round(w / div)), Math.max(8, Math.round(h / div)), true);
                    wallBmp = Bitmap.createScaledBitmap(small, w, h, true);
                }
            } catch (Throwable t) { wallBmp = null; }
        }

        void image(Canvas cv, float w, float h) {
            if (wallBmp != null) cv.drawBitmap(wallBmp, 0, 0, null);
            if (wveil > 0) {
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0, 0, w, h, withAlpha(C1, wveil * 150 / 100), withAlpha(C2, wveil * 150 / 100), Shader.TileMode.CLAMP));
                cv.drawRect(0, 0, w, h, p);
                p.setShader(null);
            }
        }

        void grid(Canvas cv, float w, float h, float t) {
            for (int i = 0; i < 40; i++) {
                float tw = 0.5f + 0.5f * (float) Math.sin(t * ss[i] * 2 + i);
                p.setColor(withAlpha(0xFFFFFF, (int) (40 + 120 * tw)));
                cv.drawCircle(sx[i] * w, sy[i] * h, dp(1.2f) * ss[i], p);
            }
            float cyc = t % 7f;
            if (cyc < 0.9f) {
                float u = cyc / 0.9f;
                float x0 = w * 0.78f - u * w * 0.35f, y0 = h * 0.08f + u * h * 0.22f;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(2));
                p.setColor(withAlpha(0xFFFFFF, (int) (220 * (1 - u))));
                cv.drawLine(x0, y0, x0 + dp(64), y0 - dp(26), p);
                p.setStyle(Paint.Style.FILL);
            }
            float hy = h * 0.68f;
            p.setShader(new RadialGradient(w / 2, hy, w * 0.45f, new int[]{withAlpha(C2, 90), 0}, null, Shader.TileMode.CLAMP));
            cv.drawCircle(w / 2, hy, w * 0.45f, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.2f));
            float phase = (t * 0.35f) % 1f;
            int n = 14;
            for (int i = 0; i < n; i++) {
                float f = (i + phase) / n;
                float y = hy + (h - hy) * f * f;
                p.setColor(withAlpha(C1, (int) (8 + 175 * f)));
                cv.drawLine(0, y, w, y, p);
            }
            p.setColor(withAlpha(C1, 110));
            for (int k = -12; k <= 12; k++) cv.drawLine(w / 2 + k * w * 0.02f, hy, w / 2 + k * w * 0.11f, h, p);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, 0, w, 0, new int[]{0, withAlpha(C2, 170), 0}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            cv.drawRect(0, hy - dp(1), w, hy + dp(1), p);
            p.setShader(null);
        }

        void rain(Canvas cv, float w, float h, float t) {
            int cols = 26;
            float cw = w / cols, len = dp(14);
            for (int i = 0; i < cols; i++) {
                float spd = 0.12f + 0.25f * ra[i];
                float head = ((t * spd + rb2[i]) % 1.25f - 0.1f) * h;
                float x = i * cw + cw / 2;
                for (int j = 0; j < 14; j++) {
                    float y = head - j * len;
                    if (y < -len || y > h) continue;
                    int al = (int) (200 * (1f - j / 14f) * (0.35f + 0.65f * rc[i] / 1.4f));
                    p.setColor(withAlpha(j == 0 ? 0xFFFFFF : (((i & 1) == 0) ? C1 : C2), al));
                    cv.drawRect(x - dp(1.5f), y, x + dp(1.5f), y + len * 0.8f, p);
                }
            }
        }

        void aurora(Canvas cv, float w, float h, float t) {
            for (int i = 0; i < 3; i++) {
                float cx = w * (0.5f + 0.35f * (float) Math.sin(t * 0.30f + i * 2.1f));
                float cy = h * (0.40f + 0.22f * (float) Math.cos(t * 0.23f + i * 1.7f));
                float r = Math.max(w, h) * (0.45f + 0.08f * i);
                p.setShader(new RadialGradient(cx, cy, r, new int[]{withAlpha(i == 1 ? C2 : C1, 120), 0}, null, Shader.TileMode.CLAMP));
                cv.drawCircle(cx, cy, r, p);
            }
            p.setShader(null);
        }

        void particles(Canvas cv, float w, float h, float t) {
            for (int i = 0; i < 70; i++) {
                float x = ((ra[i] + t * 0.01f * (rc[i] - 0.7f)) % 1f + 1f) % 1f;
                float y = ((rb2[i] - t * 0.02f * rc[i]) % 1f + 1f) % 1f;
                float r = dp(1.5f + 3.5f * rc[i] / 1.4f);
                int col = ((i & 1) == 0) ? C1 : C2;
                float tw = 0.6f + 0.4f * (float) Math.sin(t * rc[i] * 2 + i);
                p.setColor(withAlpha(col, (int) (50 * tw)));
                cv.drawCircle(x * w, y * h, r * 2.6f, p);
                p.setColor(withAlpha(col, (int) (200 * tw)));
                cv.drawCircle(x * w, y * h, r, p);
            }
        }

        void warp(Canvas cv, float w, float h, float t) {
            float cx = w / 2, cy = h / 2, maxR = (float) Math.hypot(cx, cy);
            p.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 120; i++) {
                float ang = ra[i] * 6.2832f;
                float f = (t * (0.10f + 0.12f * rc[i]) + rb2[i]) % 1f;
                float f0 = Math.max(0f, f - 0.05f);
                float r1 = f * f * maxR, r0 = f0 * f0 * maxR;
                p.setStrokeWidth(dp(0.6f + 2f * f));
                p.setColor(withAlpha((i % 3 == 0) ? C1 : 0xFFFFFF, (int) (230 * f)));
                float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
                cv.drawLine(cx + ca * r0, cy + sa * r0, cx + ca * r1, cy + sa * r1, p);
            }
            p.setStyle(Paint.Style.FILL);
        }
    }
}
