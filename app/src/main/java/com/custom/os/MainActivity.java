package com.custom.os;

import android.app.Activity;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.AudioFormat;
import android.media.AudioManager;
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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.*;
import java.util.*;

/** COs 1.0 - launcher neon : intro, menu Cocoon/COs, accueil avec apps, reglages */
public class MainActivity extends Activity {
    static final int PICK = 1, HOME_REQ = 2;
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
    String screen = "";
    final Handler ui = new Handler(Looper.getMainLooper());

    static class AppItem { String label, pkg, cls; ResolveInfo ri; Drawable icon; }

    // ---------------------------------------------------------------- cycle de vie
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("cos", MODE_PRIVATE);
        loadTheme();
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(DARK);
        logo = loadLogo();
        stage = new FrameLayout(this);
        bg = new NeonBg(this);
        stage.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        content = new FrameLayout(this);
        stage.addView(content, new FrameLayout.LayoutParams(-1, -1));
        setContentView(stage);
        askPending = (b == null && !isHome());
        if (b == null && !introDone && sp.getBoolean("intro", true)) intro();
        else enter();
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
        if (askPending) { askPending = false; ui.postDelayed(this::askHome, 400); }
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
            new int[]{withAlpha(C1, 0x30), withAlpha(C2, 0x30)});
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

        TextClock clk = new TextClock(this);
        clk.setFormat24Hour("HH:mm"); clk.setFormat12Hour("HH:mm");
        clk.setTextSize(52); clk.setTextColor(Color.WHITE);
        clk.setShadowLayer(dp(16), 0, 0, C1);
        clk.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clk.setGravity(Gravity.CENTER_HORIZONTAL);
        c.addView(clk, new LinearLayout.LayoutParams(-1, -2));

        TextClock dt = new TextClock(this);
        dt.setFormat24Hour("EEEE d MMMM"); dt.setFormat12Hour("EEEE d MMMM");
        dt.setTextSize(15); dt.setTextColor(0xCCFFFFFF);
        dt.setShadowLayer(dp(6), 0, 0, C2);
        dt.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.bottomMargin = dp(10);
        c.addView(dt, dl);

        final List<AppItem> all = loadApps();
        final List<AppItem> shown = new ArrayList<>();
        for (String k : favs())
            for (AppItem a : all) if (key(a).equals(k)) { shown.add(a); break; }

        if (shown.isEmpty()) {
            LinearLayout empty = col(Gravity.CENTER);
            empty.addView(txt("Ton accueil est vide", 20, Color.WHITE, true), lp(-2, -2, dp(8)));
            empty.addView(txt("Choisis les apps à afficher ici.\nToutes tes apps sont derrière le bouton à 4 points.", 14, 0xCCFFFFFF, false), lp(-2, -2, dp(16)));
            empty.addView(nbtn("Choisir mes apps", v -> appsScreen("pick")), lp(dp(260), dp(54), 0));
            c.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1f));
        } else {
            c.addView(makeGrid(shown, "home", null), new LinearLayout.LayoutParams(-1, 0, 1f));
        }

        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dockBtn(dock, "Fichiers", v -> pickFile());
        DotsBtn db = new DotsBtn(this);
        db.setOnClickListener(v -> { tick(); appsScreen("drawer"); });
        LinearLayout.LayoutParams dbp = new LinearLayout.LayoutParams(dp(64), dp(64));
        dbp.setMargins(dp(8), dp(8), dp(8), 0);
        dock.addView(db, dbp);
        dockBtn(dock, "Réglages", v -> settings());
        c.addView(dock, new LinearLayout.LayoutParams(-1, -2));
        show(c, "home");
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
        g.setColumnWidth(dp(100));
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
                    iv0.setPadding(dp(9), dp(9), dp(9), dp(9));
                    iv0.setBackground(neonBox(16, false));
                    cell.addView(iv0, new LinearLayout.LayoutParams(dp(62), dp(62)));
                    TextView tv0 = new TextView(MainActivity.this);
                    tv0.setId(1002);
                    tv0.setTextSize(12); tv0.setTextColor(Color.WHITE);
                    tv0.setGravity(Gravity.CENTER);
                    tv0.setMaxLines(1);
                    tv0.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    tv0.setShadowLayer(dp(4), 0, 0, C1);
                    tv0.setPadding(0, dp(4), 0, 0);
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
            if (mode.equals("pick")) {
                String k = key(a);
                if (!sel.remove(k)) sel.add(k);
                ((BaseAdapter) p.getAdapter()).notifyDataSetChanged();
            } else openApp(a);
        });
        g.setOnItemLongClickListener((p, v, i, id) -> {
            if (!mode.equals("pick")) appMenu(shown.get(i), mode);
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
        final boolean pick = mode.equals("pick");
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(20), dp(12), dp(20), dp(10));
        c.addView(txt(pick ? "Apps de l'accueil" : "Toutes les apps", 26, Color.WHITE, true), lp(-2, -2, dp(6)));
        if (pick) c.addView(txt("Touche les apps à afficher sur l'accueil", 13, 0xCCFFFFFF, false), lp(-2, -2, dp(10)));
        EditText q = searchBox(c);

        final List<AppItem> all = loadApps();
        final List<AppItem> shown = new ArrayList<>(all);
        final Set<String> sel = pick ? new LinkedHashSet<String>(favs()) : null;
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
            dockBtn(bar, "Annuler", v -> home());
            dockBtn(bar, "Valider", v -> {
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
        w.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void exit() { runOnUiThread(() -> home()); }
        }, "Android");
        String q = "?c1=" + String.format("%06x", C1 & 0xFFFFFF) + "&c2=" + String.format("%06x", C2 & 0xFFFFFF);
        w.loadUrl("file:///android_asset/cos_os.html" + q);
        w.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) {}
            public void onViewDetachedFromWindow(View v) { try { ((WebView) v).destroy(); } catch (Exception ignored) {} }
        });
        show(w, "os");
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
        c.addView(nbtn("Apps de l'accueil", v -> appsScreen("pick")), lp(dp(320), dp(54), dp(12)));
        c.addView(nbtn("Mon OS", v -> os()), lp(dp(320), dp(54), dp(12)));
        c.addView(nbtn("Rejouer l'intro", v -> intro()), lp(dp(320), dp(54), dp(12)));
        c.addView(nbtn("Menu de démarrage", v -> menu()), lp(dp(320), dp(54), dp(12)));
        c.addView(nbtn("Réglages Android", v -> {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception e) { toast("Impossible d'ouvrir les réglages"); }
        }), lp(dp(320), dp(54), dp(12)));
        if (!isHome()) c.addView(nbtn("Définir COs comme launcher", v -> askHome()), lp(dp(320), dp(54), dp(12)));
        c.addView(nbtn("Retour", v -> home()), lp(dp(320), dp(54), dp(12)));
        show(scroll(c), "settings");
    }

    TextView toggle(String label, final String key) {
        final boolean on = sp.getBoolean(key, true);
        return nbtn(label + " : " + (on ? "ON" : "OFF"), v -> {
            sp.edit().putBoolean(key, !on).apply();
            settings();
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
        String[] items = {pinned ? "Retirer de l'accueil" : "Épingler à l'accueil", "Infos de l'app", "Désinstaller"};
        new AlertDialog.Builder(this).setTitle(a.label).setItems(items, (dlg, w) -> {
            if (w == 0) {
                if (pinned) fl.remove(key(a)); else fl.add(key(a));
                saveFavs(fl);
                toast(pinned ? "Retiré de l'accueil" : "Ajouté à l'accueil");
                if (mode.equals("home")) home();
                return;
            }
            Uri u = Uri.parse("package:" + a.pkg);
            Intent i = (w == 1) ? new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, u) : new Intent(Intent.ACTION_DELETE, u);
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

        NeonBg(Context c) {
            super(c);
            Random r = new Random(7);
            for (int i = 0; i < 40; i++) { sx[i] = r.nextFloat(); sy[i] = r.nextFloat() * 0.55f; ss[i] = 0.5f + r.nextFloat(); }
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), t = (System.nanoTime() - t0) / 1e9f;
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, 0, 0, h, new int[]{DARK, 0xFF120A30, 0xFF2A0F4A},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, p);
            p.setShader(null);
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
            postInvalidateOnAnimation();
        }
    }
}
