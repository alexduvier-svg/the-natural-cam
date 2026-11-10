package br.com.duvier.thenaturalcam;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Size;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tela da câmera no layout do Natural Cam: barra superior (EV, trava, HDR, ajustes), visor central,
 * zoom em pílulas, linha Máxima · obturador laranja · Edição e modos embaixo.
 */
public final class MainActivity extends Activity implements CameraController.Listener {

    private static final int REQ_CAMERA = 7;
    private static final Locale PT = new Locale("pt", "BR");

    private CameraController camera;
    private CameraCaps caps;

    private FrameLayout root, stage;
    private TextureView texture;
    private Overlay overlay;
    private TextView evLabel, hdr, exposureLine, details, status;
    private Ui.Icon lock, info, quality, edit;
    private LinearLayout zooms, modes;
    private Shutter shutter;

    private CameraController.Mode mode = CameraController.Mode.FOTO;
    private int resIndex = 0;
    private int flash = CameraController.FLASH_OFF;
    private float zoom = 1f;
    private int ev = 0;
    private int manualIso = 0;
    private long manualExposure = 0;
    private float focusDistance = -1f;
    private boolean awbLocked;
    private boolean locked;
    private boolean grid;
    private boolean liveInfo = true;
    private boolean lowLight;
    private int stackPolicy = CameraController.STACK_AUTO;
    private int nrPolicy = CameraController.NR_LIGHT;
    private boolean busy;
    private boolean resumed;
    private Uri lastUri;
    private android.graphics.Bitmap lastPreview;
    private ImageView thumb;
    private android.widget.ProgressBar thumbBusy;
    private static final int REQ_EDIT = 8;
    private int lastOrientation = -1;
    private OrientationEventListener orientationListener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<TextView> modeButtons = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        camera = new CameraController(this, this);
        buildUi();
        restoreLastPhoto();
        orientationListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int o) {
                if (o == ORIENTATION_UNKNOWN) return;
                int deg = ((o + 45) / 90 * 90) % 360;
                if (deg != lastOrientation) {
                    lastOrientation = deg;
                    camera.setOrientation(deg);
                }
            }
        };
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (orientationListener.canDetectOrientation()) orientationListener.enable();
        camera.ambient.start(); // sensor de cor da luz ambiente (registro na ficha)
        if (peaking) startPeaking();
        maybeStart();
    }

    @Override
    protected void onPause() {
        resumed = false;
        orientationListener.disable();
        camera.stop();
        camera.ambient.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        camera.release();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode != REQ_CAMERA) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) maybeStart();
        else status.setText("Toque aqui para permitir a câmera.");
    }

    private void maybeStart() {
        if (!resumed || texture == null || !texture.isAvailable()) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        lastOrientation = -1;
        status.setText("");
        camera.start(texture.getSurfaceTexture());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_CAMERA) {
            if (event.getRepeatCount() == 0) shoot();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    // =====================================================================================
    // Montagem da tela
    // =====================================================================================

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets a = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            root.setPadding(a.left, a.top, a.right, a.bottom);
            root.post(this::layoutStage);
            return insets;
        });
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> layoutStage());

        stage = new FrameLayout(this);
        texture = new TextureView(this);
        overlay = new Overlay(this);
        stage.addView(texture, new FrameLayout.LayoutParams(-1, -1));
        stage.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        // aviso no visor: distância de foco, tele trocada por recorte (some sozinho)
        hintBanner = Ui.text(this, "", 13, Ui.TEXT);
        hintBanner.setGravity(Gravity.CENTER);
        hintBanner.setPadding(dp(14), dp(8), dp(14), dp(8));
        hintBanner.setBackground(Ui.rounded(this, 0xCC1A1206, dp(14)));
        hintBanner.setVisibility(View.GONE);
        FrameLayout.LayoutParams hb = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hb.setMargins(dp(16), dp(16), dp(16), 0);
        stage.addView(hintBanner, hb);
        // seletor de abertura simulada (só no modo Retrato), na base do visor
        apertureRow = Ui.row(this);
        apertureRow.setGravity(Gravity.CENTER);
        HorizontalScrollView apScroll = Ui.horizontal(this, apertureRow);
        apScroll.setBackgroundColor(0x6608090B);
        apScroll.setVisibility(View.GONE);
        apertureScroll = apScroll;
        stage.addView(apScroll, new FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM));
        buildApertureRow();
        longRow = Ui.row(this);
        longRow.setGravity(Gravity.CENTER);
        HorizontalScrollView lgScroll = Ui.horizontal(this, longRow);
        lgScroll.setBackgroundColor(0x6608090B);
        lgScroll.setVisibility(View.GONE);
        longScroll = lgScroll;
        stage.addView(lgScroll, new FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM));
        buildLongRow();
        astroRow = Ui.row(this);
        astroRow.setGravity(Gravity.CENTER);
        HorizontalScrollView asScroll = Ui.horizontal(this, astroRow);
        asScroll.setBackgroundColor(0x6608090B);
        asScroll.setVisibility(View.GONE);
        astroScroll = asScroll;
        stage.addView(asScroll, new FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM));
        buildAstroRow();
        root.addView(stage, new FrameLayout.LayoutParams(-1, -1));
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) {
                maybeStart();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture s) {
                camera.stop();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture s) {
            }
        });

        // ---- barra superior: EV · trava · … · HDR · ajustes
        LinearLayout top = Ui.row(this);
        top.setPadding(dp(12), 0, dp(8), 0);
        top.setBackgroundColor(0x8808090B);
        evLabel = Ui.action(this, "EV 0,0");
        evLabel.setTextColor(Ui.ACCENT);
        evLabel.setSingleLine(true);
        evLabel.setTextSize(12);
        evLabel.setPadding(dp(6), 0, dp(6), 0);
        evLabel.setContentDescription("Compensação de exposição");
        evLabel.setOnClickListener(v -> showExposure());
        evLabel.setOnLongClickListener(v -> {
            setEv(0);
            Toast.makeText(this, "Exposição de volta a 0.", Toast.LENGTH_SHORT).show();
            return true;
        });
        top.addView(evLabel, new LinearLayout.LayoutParams(dp(84), dp(48)));
        lock = new Ui.Icon(this, "lock", "Travar ou liberar exposição e foco");
        lock.setOnClickListener(v -> setLocked(!locked));
        top.addView(lock, new LinearLayout.LayoutParams(dp(48), dp(48)));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        hdr = Ui.action(this, "HDR");
        hdr.setVisibility(View.GONE);
        hdr.setOnClickListener(v -> selectMode(mode == CameraController.Mode.HDR ? CameraController.Mode.FOTO : CameraController.Mode.HDR));
        top.addView(hdr, new LinearLayout.LayoutParams(dp(52), dp(48)));
        edit = new Ui.Icon(this, "edit", "Editar a última foto (filtros Leica, Fuji, Kodak, Hasselblad)");
        edit.setOnClickListener(v -> editLast());
        top.addView(edit, new LinearLayout.LayoutParams(dp(48), dp(48)));
        info = new Ui.Icon(this, "info", "Informações e ajustes");
        info.setOnClickListener(v -> showSettings());
        top.addView(info, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(top, new FrameLayout.LayoutParams(-1, dp(52), Gravity.TOP));

        // ISO e velocidade: branco, com fundo escuro, logo abaixo do cadeado/EV (legível sobre qualquer cena)
        exposureLine = Ui.text(this, "", 13, Ui.TEXT);
        exposureLine.setGravity(Gravity.CENTER_VERTICAL);
        exposureLine.setTypeface(null, android.graphics.Typeface.BOLD);
        exposureLine.setShadowLayer(dp(2), 0, dp(1), 0xFF000000);
        exposureLine.setBackground(Ui.rounded(this, 0xB308090B, 12));
        exposureLine.setPadding(dp(10), dp(3), dp(10), dp(3));
        exposureLine.setOnClickListener(v -> {
            if (lowLight) selectMode(CameraController.Mode.NOITE);
        });
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        ep.topMargin = dp(54);
        ep.leftMargin = dp(12);
        root.addView(exposureLine, ep);

        // ---- zoom em pílulas, na faixa preta acima do painel
        zooms = Ui.row(this);
        zooms.setGravity(Gravity.CENTER);
        HorizontalScrollView zoomScroll = Ui.horizontal(this, zooms);
        zoomScroll.setBackgroundColor(Ui.BG);
        zoomScroll.setFillViewport(true);
        FrameLayout.LayoutParams zp = new FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM);
        zp.bottomMargin = dp(184);
        root.addView(zoomScroll, zp);

        // ---- painel inferior
        LinearLayout bottom = Ui.column(this);
        bottom.setBackgroundColor(Ui.BG);
        bottom.setPadding(dp(14), 0, dp(14), dp(4));

        LinearLayout shoot = Ui.row(this);
        shoot.setGravity(Gravity.CENTER);
        quality = new Ui.Icon(this, "quality", "Qualidade máxima disponível");
        quality.label = "Máxima";
        quality.setOnClickListener(v -> toggleMaximum());
        shoot.addView(quality, new LinearLayout.LayoutParams(0, dp(72), 1));
        shutter = new Shutter(this);
        shutter.setContentDescription("Fotografar");
        shutter.setOnClickListener(v -> shoot());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(82), dp(82));
        sp.setMargins(dp(25), dp(4), dp(25), dp(4));
        shoot.addView(shutter, sp);
        // miniatura da última foto: aparece no disparo, antes de terminar o processamento
        FrameLayout thumbCell = new FrameLayout(this);
        FrameLayout thumbBox = new FrameLayout(this);
        thumbBox.setBackground(Ui.rounded(this, Ui.SURFACE, 12));
        thumbBox.setClipToOutline(true);
        thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbBox.addView(thumb, new FrameLayout.LayoutParams(-1, -1));
        thumbBusy = new android.widget.ProgressBar(this);
        thumbBusy.setIndeterminate(true);
        thumbBusy.setVisibility(View.GONE);
        thumbBox.addView(thumbBusy, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        thumbBox.setContentDescription("Ver a última foto");
        thumbBox.setOnClickListener(v -> showViewer());
        thumbCell.addView(thumbBox, new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER));
        shoot.addView(thumbCell, new LinearLayout.LayoutParams(0, dp(72), 1));
        bottom.addView(shoot);

        modes = Ui.row(this);
        modes.setGravity(Gravity.CENTER);
        HorizontalScrollView modeScroll = Ui.horizontal(this, modes);
        modeScroll.setFillViewport(true);
        bottom.addView(modeScroll, new LinearLayout.LayoutParams(-1, dp(44)));

        details = Ui.text(this, "JPEG · Natural", 12, Ui.TEXT);
        details.setGravity(Gravity.CENTER);
        details.setVisibility(View.GONE); // rodapé limpo: sem "Foto · 13 MP · empilhamento…"
        bottom.addView(details, new LinearLayout.LayoutParams(-1, dp(18)));
        status = Ui.text(this, "Permita o acesso à câmera.", 11, Ui.MUTED);
        status.setGravity(Gravity.CENTER);
        status.setSingleLine(true);
        status.setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            }
        });
        bottom.addView(status, new LinearLayout.LayoutParams(-1, dp(32)));
        root.addView(bottom, new FrameLayout.LayoutParams(-1, dp(184), Gravity.BOTTOM));

        buildModes(false, false);
        setContentView(root);
    }

    /** Visor 3:4 centralizado no espaço entre a barra superior e o painel (52 + 48 + 184 dp). */
    private void layoutStage() {
        if (stage == null || root.getWidth() == 0) return;
        int w = root.getWidth() - root.getPaddingLeft() - root.getPaddingRight();
        int available = root.getHeight() - root.getPaddingTop() - root.getPaddingBottom() - dp(284);
        int h = Math.max(dp(180), Math.min(available, Math.round(w * 4f / 3f)));
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) stage.getLayoutParams();
        int topMargin = dp(52) + Math.max(0, (available - h) / 2);
        if (p.height != h || p.topMargin != topMargin) {
            p.width = -1;
            p.height = h;
            p.topMargin = topMargin;
            p.gravity = Gravity.TOP;
            stage.setLayoutParams(p);
        }
    }

    private void buildModes(boolean raw, boolean hdrAvailable) {
        modes.removeAllViews();
        modeButtons.clear();
        addMode("Foto", CameraController.Mode.FOTO);
        addMode("Retrato", CameraController.Mode.RETRATO);
        addMode("Rua", CameraController.Mode.RUA);
        addMode("Noite", CameraController.Mode.NOITE);
        addMode("Longa", CameraController.Mode.LONGA);
        addMode("Astro", CameraController.Mode.ASTRO);
        // RAW+: rajada RAW revelada pelo ISP próprio (RawIsp) — o DNG de quadro único da OPPO saía ruim.
        if (raw) addMode("RAW+", CameraController.Mode.RAW);
        addMode("Pro", CameraController.Mode.PRO);
        hdr.setVisibility(hdrAvailable ? View.VISIBLE : View.GONE);
        refreshModes();
    }

    private void addMode(String name, CameraController.Mode m) {
        TextView t = Ui.action(this, name);
        t.setPadding(dp(14), 0, dp(14), 0);
        t.setMinWidth(0);
        t.setSingleLine(true);
        t.setMaxLines(1);
        t.setTag(m);
        t.setOnClickListener(v -> {
            if (m == CameraController.Mode.PRO && mode == CameraController.Mode.PRO) {
                showPro();
                return;
            }
            selectMode(m);
            if (m == CameraController.Mode.PRO) showPro();
        });
        modes.addView(t, new LinearLayout.LayoutParams(-2, dp(44)));
        modeButtons.add(t);
    }

    private void refreshModes() {
        for (TextView t : modeButtons) {
            boolean on = t.getTag() == mode;
            t.setTextColor(on ? Ui.ACCENT : Ui.TEXT);
            t.setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        hdr.setTextColor(mode == CameraController.Mode.HDR ? Ui.ACCENT : Ui.TEXT);
        apertureScroll.setVisibility(blurDialVisible() ? View.VISIBLE : View.GONE);
        longScroll.setVisibility(mode == CameraController.Mode.LONGA ? View.VISIBLE : View.GONE);
        astroScroll.setVisibility(mode == CameraController.Mode.ASTRO ? View.VISIBLE : View.GONE);
        boolean resAllowed = mode == CameraController.Mode.FOTO || mode == CameraController.Mode.PRO;
        boolean stackMode = mode == CameraController.Mode.FOTO || mode == CameraController.Mode.RETRATO
                || mode == CameraController.Mode.NOITE;
        boolean proRes = mode == CameraController.Mode.PRO && caps != null && caps.resolutions.size() > 1;
        quality.setEnabled(caps != null && (stackMode || proRes));
        boolean maxOn = stackMode ? stackPolicy == CameraController.STACK_MAX : (resAllowed && resIndex > 0);
        quality.selected(maxOn);
        quality.label = !maxOn ? "Máxima"
                : (mode == CameraController.Mode.FOTO || mode == CameraController.Mode.RETRATO) ? "Máx 28 MP"
                : mode == CameraController.Mode.NOITE ? "Máx 12q" : "Máxima";
        quality.invalidate();
    }

    private void selectMode(CameraController.Mode m) {
        if (busy || m == mode) return;
        mode = m;
        if (m != CameraController.Mode.PRO) {
            manualIso = 0;
            manualExposure = 0;
            camera.setManual(0, 0);
        }
        setLocked(false);
        camera.setMode(m);
        refreshModes();
        status.setText("");
    }

    // =====================================================================================
    // Ações
    // =====================================================================================

    private void shoot() {
        if (busy) return;
        overlay.flash();
        camera.capture();
    }

    /**
     * Máxima = qualidade máxima. Nos OPPO a API só entrega 12,6 MP a outros apps, então o ganho
     * real vem de juntar mais quadros (10–12): captura mais lenta, menos ruído, mais textura.
     * Se o aparelho oferecer uma resolução maior de verdade, ela também é usada no Pro.
     */
    private void toggleMaximum() {
        if (caps == null || busy) return;
        if (mode == CameraController.Mode.PRO && caps.resolutions.size() > 1) {
            setResolution(resIndex > 0 ? 0 : 1);
            return;
        }
        if (mode != CameraController.Mode.FOTO && mode != CameraController.Mode.RETRATO && mode != CameraController.Mode.NOITE) return;
        stackPolicy = stackPolicy == CameraController.STACK_MAX ? CameraController.STACK_AUTO : CameraController.STACK_MAX;
        camera.setStackPolicy(stackPolicy);
        refreshModes();
    }

    private void setResolution(int index) {
        resIndex = index;
        camera.setResolution(index);
        refreshModes();
        if (index > 0) {
            Toast.makeText(this, caps.resolutions.get(index).label
                    + ": mais detalhe com luz forte, porém mais ruído em interiores e à noite.", Toast.LENGTH_LONG).show();
        }
    }

    private void setLocked(boolean on) {
        locked = on;
        lock.selected(on);
        overlay.invalidate();
        camera.lock(on);
    }

    private void editLast() {
        if (lastUri == null) {
            if (busy) {
                status.setText("Aguarde a foto terminar de gravar para editar.");
                return;
            }
            // sem foto do app: escolhe qualquer foto da galeria para editar
            startActivityForResult(new Intent(this, EditorActivity.class).putExtra(EditorActivity.EXTRA_PICK, true), REQ_EDIT);
            return;
        }
        Intent i = new Intent(this, EditorActivity.class);
        i.putExtra(EditorActivity.EXTRA_URI, lastUri);
        startActivityForResult(i, REQ_EDIT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EDIT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            onSaved(data.getData(), "edição salva");
        }
    }

    /** Visualizador em tela cheia: pinça para ampliar, arrastar para mover, toque duplo alterna 1×/2,5×. */
    private void showViewer() {
        if (lastUri == null && lastPreview == null) return;
        Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout box = new FrameLayout(this);
        box.setBackgroundColor(Color.BLACK);
        ZoomImageView img = new ZoomImageView(this);
        box.addView(img, new FrameLayout.LayoutParams(-1, -1));
        TextView note = Ui.text(this, "", 12, Ui.TEXT);
        note.setBackground(Ui.rounded(this, 0x99000000, 12));
        note.setPadding(dp(12), dp(6), dp(12), dp(6));
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        np.topMargin = dp(40);
        box.addView(note, np);
        LinearLayout bar = Ui.row(this);
        bar.setGravity(Gravity.CENTER);
        bar.setBackgroundColor(0xAA08090B);
        TextView close = Ui.action(this, "Fechar");
        close.setOnClickListener(v -> d.dismiss());
        TextView ed = Ui.action(this, "Editar");
        ed.setTextColor(Ui.ACCENT);
        ed.setOnClickListener(v -> {
            d.dismiss();
            editLast();
        });
        TextView gal = Ui.action(this, "Galeria");
        gal.setOnClickListener(v -> viewLast());
        bar.addView(close, new LinearLayout.LayoutParams(0, dp(56), 1));
        bar.addView(ed, new LinearLayout.LayoutParams(0, dp(56), 1));
        bar.addView(gal, new LinearLayout.LayoutParams(0, dp(56), 1));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        bp.bottomMargin = dp(24);
        box.addView(bar, bp);
        d.setContentView(box);
        if (busy || lastUri == null) {
            img.setImageBitmap(lastPreview);
            note.setText("Prévia · processando a foto final…");
        } else {
            note.setText("Carregando…");
            final Uri uri = lastUri;
            new Thread(() -> {
                try {
                    android.graphics.Bitmap b = android.graphics.ImageDecoder.decodeBitmap(
                            android.graphics.ImageDecoder.createSource(getContentResolver(), uri), (dec, info, s) -> {
                                Size sz = info.getSize();
                                float k = Math.min(1f, 3000f / Math.max(sz.getWidth(), sz.getHeight()));
                                dec.setTargetSize(Math.round(sz.getWidth() * k), Math.round(sz.getHeight() * k));
                            });
                    main.post(() -> {
                        img.setImageBitmap(b);
                        note.setVisibility(View.GONE);
                    });
                } catch (Exception e) {
                    main.post(() -> note.setText("Não foi possível abrir: " + e.getMessage()));
                }
            }).start();
        }
        d.show();
    }


    private void viewLast() {
        if (lastUri == null) return;
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(lastUri, "image/jpeg");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Nenhum app de galeria encontrado.", Toast.LENGTH_SHORT).show();
        }
    }

    private void setEv(int value) {
        if (caps == null) return;
        ev = Math.max(caps.evRange.getLower(), Math.min(caps.evRange.getUpper(), value));
        double stops = ev * caps.evStep.doubleValue();
        evLabel.setText(String.format(PT, "EV %s%.1f", stops > 0.04 ? "+" : "", stops));
        camera.setEv(ev);
        if (overlay != null) overlay.invalidate();
    }

    private void zoomTo(float z) {
        if (caps == null) return;
        zoom = Math.max(caps.zoomRange.getLower(), Math.min(caps.zoomRange.getUpper(), z));
        camera.setZoom(zoom);
        if (apertureScroll != null) {
            apertureScroll.setVisibility(blurDialVisible() ? View.VISIBLE : View.GONE);
            buildApertureRow(); // texto "desligado" só faz sentido fora do Retrato
        }
        updateZoomSelection();
    }

    private void buildZooms() {
        zooms.removeAllViews();
        float lo = caps.zoomRange.getLower(), hi = caps.zoomRange.getUpper();
        // Sem 3×: era recorte da câmera principal (menos detalhe e outra cor que o 3,5× da
        // teleobjetiva). 3,5× é a lente tele nativa; a pinça ainda chega a qualquer zoom.
        float[] choices = {0.6f, 1, 2, 3.5f, 6, 7, 8, 10};
        for (float c : choices) {
            float z = c == 0.6f && lo < 1f ? lo : c;
            if (z < lo - 0.04f || z > hi + 0.01f) continue;
            TextView t = Ui.pill(this, zoomName(z), false);
            t.setTag(z);
            t.setOnClickListener(v -> zoomTo((Float) v.getTag()));
            zooms.addView(t, Ui.pillParams(this));
        }
        updateZoomSelection();
    }

    private void updateZoomSelection() {
        TextView nearest = null;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < zooms.getChildCount(); i++) {
            TextView t = (TextView) zooms.getChildAt(i);
            double d = Math.abs(Math.log(zoom / (Float) t.getTag()));
            if (d < best) {
                best = d;
                nearest = t;
            }
        }
        for (int i = 0; i < zooms.getChildCount(); i++) {
            TextView t = (TextView) zooms.getChildAt(i);
            boolean on = t == nearest;
            Ui.select(this, t, on);
            t.setText(on && best > 0.03 ? zoomName(zoom) : zoomName((Float) t.getTag()));
        }
    }

    private static String zoomName(float z) {
        if (Math.abs(z - Math.round(z)) < 0.05f) return Math.round(z) + "×";
        return String.format(PT, "%.1f×", z);
    }

    // =====================================================================================
    // Painéis
    // =====================================================================================

    private void showExposure() {
        if (caps == null) return;
        int lo = caps.evRange.getLower(), hi = caps.evRange.getUpper();
        if (hi <= lo) return;
        LinearLayout panel = Ui.column(this);
        panel.addView(Ui.text(this, "Compensação de exposição", 14, Ui.TEXT));
        SeekBar s = new SeekBar(this);
        s.setMax(hi - lo);
        s.setProgress(ev - lo);
        s.setOnSeekBarChangeListener(onChange(() -> setEv(s.getProgress() + lo)));
        panel.addView(s, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView reset = Ui.action(this, "Voltar para 0");
        reset.setTextColor(Ui.ACCENT);
        reset.setOnClickListener(v -> {
            s.setProgress(-lo);
            setEv(0);
        });
        panel.addView(reset);
        Ui.sheet(this, "Exposição", panel).show();
    }

    private void showPro() {
        if (caps == null) return;
        LinearLayout content = Ui.column(this);
        if (caps.manualSensor && caps.isoRange != null && caps.exposureRange != null) {
            content.addView(Ui.text(this, "ISO", 13, Ui.MUTED));
            LinearLayout isos = Ui.row(this);
            int[] values = {0, 50, 100, 200, 400, 800, 1600, 3200, 6400};
            List<TextView> isoPills = new ArrayList<>();
            for (int value : values) {
                if (value != 0 && (value < caps.isoRange.getLower() || value > caps.isoRange.getUpper())) continue;
                TextView t = Ui.pill(this, value == 0 ? "Auto" : Integer.toString(value), value == manualIso);
                t.setTag(value);
                t.setOnClickListener(v -> {
                    manualIso = value;
                    if (value > 0 && manualExposure == 0) manualExposure = 16_666_667L;
                    for (TextView p : isoPills) Ui.select(this, p, (Integer) p.getTag() == manualIso);
                    applyManual();
                });
                isoPills.add(t);
                isos.addView(t, Ui.pillParams(this));
            }
            content.addView(Ui.horizontal(this, isos), new LinearLayout.LayoutParams(-1, dp(48)));

            content.addView(Ui.text(this, "Velocidade", 13, Ui.MUTED));
            LinearLayout speeds = Ui.row(this);
            int[] den = {4000, 2000, 1000, 500, 250, 125, 60, 30, 15, 8, 4, 2, 1};
            List<TextView> speedPills = new ArrayList<>();
            for (int d : den) {
                long ns = 1_000_000_000L / d;
                if (ns < caps.exposureRange.getLower() || ns > caps.exposureRange.getUpper()) continue;
                TextView t = Ui.pill(this, d == 1 ? "1 s" : "1/" + d, manualIso > 0 && Math.abs(ns - manualExposure) < ns / 10);
                t.setTag(ns);
                t.setOnClickListener(v -> {
                    manualExposure = ns;
                    if (manualIso == 0) {
                        manualIso = 400;
                        for (TextView p : isoPills) Ui.select(this, p, (Integer) p.getTag() == manualIso);
                    }
                    for (TextView p : speedPills) Ui.select(this, p, (Long) p.getTag() == manualExposure);
                    applyManual();
                });
                speedPills.add(t);
                speeds.addView(t, Ui.pillParams(this));
            }
            content.addView(Ui.horizontal(this, speeds), new LinearLayout.LayoutParams(-1, dp(48)));
            content.addView(note("ISO acima de 1600 aumenta muito o ruído. No escuro, prefira o modo Noite."));
        }

        if (caps.minFocus > 0) {
            content.addView(Ui.text(this, "Foco manual · perto → infinito", 13, Ui.MUTED));
            SeekBar focus = new SeekBar(this);
            focus.setMax(1000);
            focus.setProgress(focusDistance < 0 ? 1000 : Math.round(1000 * (1 - focusDistance / caps.minFocus)));
            TextView auto = Ui.pill(this, "Foco automático", focusDistance < 0);
            focus.setOnSeekBarChangeListener(onChange(() -> {
                focusDistance = caps.minFocus * (1 - focus.getProgress() / 1000f);
                Ui.select(this, auto, false);
                camera.setFocusDistance(focusDistance);
            }));
            auto.setOnClickListener(v -> {
                focusDistance = -1f;
                Ui.select(this, auto, true);
                camera.setFocusDistance(-1f);
            });
            content.addView(focus, new LinearLayout.LayoutParams(-1, dp(48)));
            LinearLayout r = Ui.row(this);
            r.addView(auto, Ui.pillParams(this));
            content.addView(r);
        }

        content.addView(Ui.text(this, "Redução de ruído da câmera (quadro único: Pro, RAW, 50/200 MP)", 13, Ui.MUTED));
        LinearLayout nrRow = Ui.row(this);
        String[] nrNames = {"Desligada · textura", "Leve", "Alta · suave"};
        List<TextView> nrPills = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final int p = i;
            TextView t = Ui.pill(this, nrNames[i], nrPolicy == i);
            t.setOnClickListener(v -> {
                nrPolicy = p;
                camera.setNrPolicy(p);
                for (int k = 0; k < nrPills.size(); k++) Ui.select(this, nrPills.get(k), k == nrPolicy);
            });
            nrPills.add(t);
            nrRow.addView(t, Ui.pillParams(this));
        }
        content.addView(Ui.horizontal(this, nrRow), new LinearLayout.LayoutParams(-1, dp(48)));

        if (caps.awbLock) {
            TextView wb = Ui.action(this, awbLocked ? "Liberar balanço de branco" : "Travar balanço de branco");
            wb.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            wb.setTextColor(awbLocked ? Ui.ACCENT : Ui.TEXT);
            wb.setOnClickListener(v -> {
                awbLocked = !awbLocked;
                camera.setAwbLock(awbLocked);
                wb.setText(awbLocked ? "Liberar balanço de branco" : "Travar balanço de branco");
                wb.setTextColor(awbLocked ? Ui.ACCENT : Ui.TEXT);
            });
            content.addView(wb);
        }

        TextView reset = Ui.action(this, "Redefinir controles");
        reset.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        reset.setOnClickListener(v -> {
            manualIso = 0;
            manualExposure = 0;
            focusDistance = -1f;
            awbLocked = false;
            camera.setManual(0, 0);
            camera.setFocusDistance(-1f);
            camera.setAwbLock(false);
            setEv(0);
            Toast.makeText(this, "Controles automáticos.", Toast.LENGTH_SHORT).show();
        });
        content.addView(reset);
        Dialog d = Ui.sheet(this, "Pro", content);
        d.show();
    }

    private void applyManual() {
        camera.setManual(manualIso, manualIso > 0 ? manualExposure : 0);
    }

    /** Seção do painel de Ajustes: título centralizado em laranja e um cartão arredondado. */
    private LinearLayout settingsSection(LinearLayout root, String title) {
        TextView h = Ui.text(this, title, 16, Ui.ACCENT);
        h.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        h.setGravity(Gravity.CENTER);
        h.setPadding(0, dp(root.getChildCount() == 0 ? 2 : 14), 0, dp(8));
        root.addView(h, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.rounded(this, 0xFF1C1D22, dp(14)));
        card.setPadding(dp(14), dp(8), dp(14), dp(8));
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private TextView settingsLabel(String s) {
        TextView t = Ui.text(this, s, 13, Ui.MUTED);
        t.setPadding(0, dp(6), 0, 0);
        return t;
    }

    /** Card de ajuste (estilo da grade do editor): ícone, nome, valor atual em laranja; toque alterna. */
    private static final class SettingCard {
        final String icon, name;
        final java.util.function.Supplier<String> value;
        final Runnable tap;

        SettingCard(String icon, String name, java.util.function.Supplier<String> value, Runnable tap) {
            this.icon = icon;
            this.name = name;
            this.value = value;
            this.tap = tap;
        }
    }

    private static final int SET_TILE_BG = 0xFF0B0C0E, SET_TILE_LINE = 0xFF26272B;

    /** Seção da tela de Ajustes: título laranja centralizado + grade de cards (2 por linha, sem vazios). */
    private void settingsGrid(LinearLayout root, String title, List<SettingCard> cards) {
        TextView h = Ui.text(this, title, 16, Ui.ACCENT);
        h.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        h.setGravity(Gravity.CENTER);
        h.setPadding(0, dp(root.getChildCount() == 0 ? 2 : 16), 0, dp(10));
        root.addView(h, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout box = Ui.column(this);
        box.setBackground(Ui.rounded(this, SET_TILE_LINE, dp(14)));
        box.setClipToOutline(true);
        int cols = 2;
        for (int i = 0; i < cards.size(); i += cols) {
            LinearLayout row = Ui.row(this);
            int inRow = Math.min(cols, cards.size() - i); // última linha incompleta divide a largura
            for (int c = 0; c < inRow; c++) {
                SettingCard sc = cards.get(i + c);
                LinearLayout tile = Ui.column(this);
                tile.setGravity(Gravity.CENTER);
                tile.setBackgroundColor(SET_TILE_BG);
                tile.setPadding(dp(8), dp(10), dp(8), dp(10));
                TextView icon = Ui.text(this, sc.icon, 22, Ui.TEXT);
                icon.setGravity(Gravity.CENTER);
                tile.addView(icon);
                TextView name = Ui.text(this, sc.name, 12, Ui.TEXT);
                name.setGravity(Gravity.CENTER);
                name.setPadding(0, dp(6), 0, dp(2));
                tile.addView(name);
                TextView val = Ui.text(this, sc.value.get(), 12, Ui.ACCENT);
                val.setGravity(Gravity.CENTER);
                tile.addView(val);
                if (sc.tap != null) {
                    tile.setOnClickListener(v -> {
                        sc.tap.run();
                        val.setText(sc.value.get());
                    });
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(104), 1);
                lp.setMargins(c == 0 ? 0 : dp(1), i == 0 ? 0 : dp(1), 0, 0);
                row.addView(tile, lp);
            }
            box.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        root.addView(box, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showSettings() {
        LinearLayout root = Ui.column(this);
        String[] flashNames = {"Desligado", "Automático", "Ligado"};
        String[] stackNames = {"Automático", "Desligado", "Máximo"};
        List<SettingCard> captura = new ArrayList<>();
        if (caps != null && caps.flash) {
            captura.add(new SettingCard("ϟ", "Flash", () -> flashNames[flash], () -> {
                flash = (flash + 1) % 3;
                camera.setFlash(flash);
            }));
        }
        if (caps != null && !caps.resolutions.isEmpty()) {
            captura.add(new SettingCard("▦", "Resolução",
                    () -> caps.resolutions.get(Math.min(resIndex, caps.resolutions.size() - 1)).label + (resIndex == 0 ? " · menos ruído" : ""),
                    () -> setResolution((resIndex + 1) % caps.resolutions.size())));
        }
        captura.add(new SettingCard("❏", "Empilhamento", () -> stackNames[stackPolicy], () -> {
            stackPolicy = (stackPolicy + 1) % 3;
            camera.setStackPolicy(stackPolicy);
            refreshModes();
        }));
        captura.add(new SettingCard("◐", "Sombras", () -> "limpeza automática", null));
        settingsGrid(root, "Captura", captura);
        root.addView(note("Empilhamento automático junta 3 a 10 quadros conforme a luz; Máximo usa 10–12 e super-resolução. "
                + "A limpeza de ruído é forte só nas sombras e áreas lisas, leve no resto e respeita a pele."));
        List<SettingCard> visor = new ArrayList<>();
        visor.add(new SettingCard("◎", "Focus peaking", () -> peaking ? "Ligado" : "Desligado", () -> {
            peaking = !peaking;
            if (peaking) startPeaking();
            else overlay.peakPts = null;
            overlay.invalidate();
        }));
        visor.add(new SettingCard("▤", "Grade", () -> grid ? "Ligada" : "Desligada", () -> {
            grid = !grid;
            overlay.invalidate();
        }));
        visor.add(new SettingCard("ⓘ", "ISO e velocidade", () -> liveInfo ? "No visor" : "Ocultos", () -> {
            liveInfo = !liveInfo;
            exposureLine.setVisibility(liveInfo ? View.VISIBLE : View.INVISIBLE);
        }));
        visor.add(new SettingCard("◑", "Luz ambiente", () -> {
            float k = camera.ambient.cct();
            return k > 0 ? Math.round(k) + " K" : (camera.ambient.available() ? "medindo…" : "sem sensor");
        }, null));
        settingsGrid(root, "Visor", visor);
        List<SettingCard> sobre = new ArrayList<>();
        sobre.add(new SettingCard("✦", "Versão", () -> BuildConfigInfo.VERSION, null));
        sobre.add(new SettingCard("▣", "Câmera", () -> caps == null ? "—" : caps.id + (caps.logicalMultiCamera ? " (lógica)" : ""), null));
        sobre.add(new SettingCard("◫", "RAW", () -> caps != null && caps.raw ? "Disponível (RAW+)" : "Não", null));
        sobre.add(new SettingCard("☾", "Noite", () -> caps != null && caps.extNight ? "Sistema + própria" : "Fusão própria", null));
        settingsGrid(root, "Sobre o Aparelho", sobre);
        root.addView(note("Fotos em " + MediaSaver.PHOTO_DIR + " · ficha técnica de cada foto em " + MediaSaver.SHEET_DIR + "."));
        Ui.sheet(this, "Ajustes", root).show();
    }

    private interface OnToggle {
        void set(boolean on);
    }

    private TextView toggle(String label, boolean initial, OnToggle cb) {
        TextView t = Ui.action(this, "");
        t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        final boolean[] state = {initial};
        Runnable paint = () -> {
            t.setText((state[0] ? "●  " : "○  ") + label);
            t.setTextColor(state[0] ? Ui.ACCENT : Ui.TEXT);
        };
        paint.run();
        t.setOnClickListener(v -> {
            state[0] = !state[0];
            paint.run();
            cb.set(state[0]);
        });
        return t;
    }

    private TextView note(String s) {
        TextView t = Ui.text(this, s, 12, Ui.MUTED);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    private static SeekBar.OnSeekBarChangeListener onChange(Runnable r) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) r.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
    }

    // =====================================================================================
    // Eventos da câmera
    // =====================================================================================

    @Override
    public void onOpened(CameraCaps c) {
        caps = c;
        buildModes(c.raw, c.extHdr);
        buildZooms();
        resIndex = Math.min(resIndex, Math.max(0, c.resolutions.size() - 1));
        setEv(ev);
        refreshModes();
    }

    @Override
    public void onSessionReady(CameraController.Mode m, Size previewSize, String description) {
        if (m != mode) {
            mode = m;
            refreshModes();
        }
        details.setText(description);
        if (!busy) status.setText(statusHint());
    }

    /** Rodapé limpo: sem dicas de uso (só andamento da captura, "Salva" e erros aparecem ali). */
    private String statusHint() {
        if (true) return "";
        switch (mode) {
            case NOITE: return "Noite: apoie o celular e segure firme até terminar.";
            case RAW: return "RAW+: revelação própria do sensor (teste) — compare com Foto.";
            case RETRATO: return "Retrato: toque no rosto para focar · " + PortraitEngine.label(aperture) + " (simulação).";
            case RUA: return "Rua: disparo imediato, obturador rápido para congelar o movimento.";
            case LONGA: return (longTrails ? "Luzes: faróis e luzes viram rastros" : "Água: efeito sedoso, como filtro ND") + " · " + longSeconds + " s · apoie o celular.";
            case PRO: return "Pro: toque em Pro de novo para os controles.";
            default: return "Toque para focar · segure para travar · deslize para os lados = exposição.";
        }
    }

    @Override
    public void onLive(int iso, long exposureNs, String nr) {
        if (!liveInfo) return;
        // só ISO e velocidade (sem dica de "pouca luz")
        lowLight = false;
        String tip = "";
        exposureLine.setText("ISO " + iso + "  ·  " + CameraController.shutterLabel(exposureNs) + tip);
        exposureLine.setTextColor(Ui.TEXT);
    }

    @Override
    public void onBusy(String text, float p) {
        busy = true;
        shutter.invalidate();
        status.setText(p >= 0 ? String.format(PT, "%s  %d%%", text, Math.round(p * 100)) : text);
    }

    @Override
    public void onSaved(Uri uri, String summary) {
        if (mode == CameraController.Mode.RETRATO && !summary.startsWith("retrato")) {
            makePortrait(uri, aperture, "_RETRATO", "retrato");
        } else if (mode == CameraController.Mode.FOTO && !summary.startsWith("óptica") && camera != null && camera.lastShotWasCrop()) {
            // de perto no zoom da tele a foto vem da principal (profundidade de campo de grande-
            // angular, fundo nítido demais): gera também a versão com o fundo levemente desfocado
            // de uma tele de verdade — gradual, sem contorno
            makePortrait(uri, 8f, "_OPTICA_TELE", "óptica de tele", 0.15f);
        } else if (mode == CameraController.Mode.FOTO && !summary.startsWith("óptica") && camera != null && camera.lastShotZoom() >= 5.5f) {
            // 6×–8× são recortes da tele de 80 mm: a profundidade de campo é a de 80 mm, maior que a de
            // uma lente longa de verdade (ex.: periscópica ~131–184 mm). Gera também a versão com o
            // desfoque gradual que essa focal daria (fundo atrás do sujeito tocado), sem halo; a
            // original fica. Regra do usuário: bokeh sem exagero, sempre ACIMA de f/5,6 —
            // a abertura vem do dial de desfoque no visor (f/16…f/8).
            float ap = longLensAperture(); // dial de desfoque (f/16…f/8; desligado = sem cópia)
            if (ap > 0) makePortrait(uri, ap, "_OPTICA_LONGA", "óptica de lente longa", 0.15f);
        }
        lastUri = uri;
        busy = false;
        shutter.invalidate();
        thumbBusy.setVisibility(View.GONE);
        status.setText("Salva · " + summary);
        main.postDelayed(() -> {
            if (!busy) status.setText(statusHint());
        }, 3500);
        new Thread(() -> {
            try {
                android.graphics.Bitmap b = getContentResolver().loadThumbnail(uri, new Size(dp(116), dp(116)), null);
                main.post(() -> {
                    lastPreview = b;
                    thumb.setImageBitmap(b);
                });
            } catch (Exception ignored) {
            }
        }).start();
    }

    /**
     * Ao abrir o app, recupera a foto mais recente do próprio app (DCIM/TheNaturalCam): sem isso,
     * depois de reiniciar, Editar e a miniatura diziam "Fotografe primeiro".
     */
    private void restoreLastPhoto() {
        new Thread(() -> {
            try (android.database.Cursor c = getContentResolver().query(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{android.provider.MediaStore.Images.Media._ID},
                    android.provider.MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?",
                    new String[]{MediaSaver.PHOTO_DIR + "%"},
                    android.provider.MediaStore.Images.Media.DATE_ADDED + " DESC")) {
                if (c == null || !c.moveToFirst()) return;
                Uri uri = android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0));
                android.graphics.Bitmap b = null;
                try {
                    b = getContentResolver().loadThumbnail(uri, new Size(dp(116), dp(116)), null);
                } catch (Exception ignored) {
                }
                final android.graphics.Bitmap thumbB = b;
                main.post(() -> {
                    if (lastUri != null) return;
                    lastUri = uri;
                    if (thumbB != null) {
                        lastPreview = thumbB;
                        thumb.setImageBitmap(thumbB);
                    }
                });
            } catch (Exception ignored) {
            }
        }).start();
    }

    // ---- Foco que "agarra" o pico de nitidez + focus peaking ------------------------------------

    private Runnable focusTracker;
    private android.graphics.Bitmap probe;
    private boolean peaking;
    private Runnable peakingTask;

    /** Lê a prévia em baixa resolução (sem custo de câmera: vem da própria TextureView). */
    private android.graphics.Bitmap previewProbe(int w, int h) {
        if (texture == null || !texture.isAvailable()) return null;
        if (probe == null || probe.getWidth() != w || probe.getHeight() != h) {
            probe = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        }
        return texture.getBitmap(probe);
    }

    /**
     * Nitidez da área tocada: energia de gradiente do brilho, normalizada pelo brilho médio
     * (para não confundir mudança de exposição com foco).
     */
    private float regionSharpness(float nx, float ny) {
        android.graphics.Bitmap b = previewProbe(240, 320);
        if (b == null) return -1f;
        int w = b.getWidth(), h = b.getHeight();
        int half = Math.round(w * 0.11f);
        int cx = Math.round(nx * w), cy = Math.round(ny * h);
        int x0 = Math.max(1, cx - half), x1 = Math.min(w - 2, cx + half);
        int y0 = Math.max(1, cy - half), y1 = Math.min(h - 2, cy + half);
        if (x1 <= x0 || y1 <= y0) return -1f;
        int rw = x1 - x0 + 2;
        int[] px = new int[rw * (y1 - y0 + 2)];
        b.getPixels(px, 0, rw, x0, y0, rw, y1 - y0 + 2);
        double g = 0, sum = 0;
        int n = 0;
        for (int y = 0; y < y1 - y0 + 1; y++) {
            for (int x = 0; x < rw - 1; x++) {
                int c = px[y * rw + x], r = px[y * rw + x + 1], d = px[(y + 1) * rw + x];
                int l = luma(c), lr = luma(r), ld = luma(d);
                g += (lr - l) * (lr - l) + (ld - l) * (ld - l);
                sum += l;
                n++;
            }
        }
        double mean = sum / Math.max(1, n);
        return (float) (g / Math.max(1, n) / (mean * mean + 64));
    }

    private static int luma(int c) {
        return (77 * ((c >> 16) & 255) + 150 * ((c >> 8) & 255) + 29 * (c & 255)) >> 8;
    }

    /**
     * Enquanto a câmera varre o foco depois do toque, mede a nitidez da área a cada quadro e
     * anota a distância da lente. No fim, trava a lente na distância de MAIOR nitidez — o foco
     * para onde ficou nítido, em vez de passar por ali e voltar a embaçar.
     */
    private void startFocusTracker(float nx, float ny) {
        if (focusTracker != null) main.removeCallbacks(focusTracker);
        final long t0 = SystemClock.uptimeMillis();
        final float[] lag = {-1f, -1f, -1f}; // distâncias recentes: a prévia exibida atrasa ~2 quadros
        final java.util.ArrayList<float[]> trace = new java.util.ArrayList<>(); // {nitidez, distância}
        focusTracker = new Runnable() {
            @Override
            public void run() {
                long el = SystemClock.uptimeMillis() - t0;
                float s = regionSharpness(nx, ny);
                float dNow = camera.focusDistanceNow();
                float dShown = lag[0] >= 0 ? lag[0] : dNow;
                lag[0] = lag[1];
                lag[1] = lag[2];
                lag[2] = dNow;
                if (s >= 0 && dShown >= 0 && el > 100) trace.add(new float[]{s, dShown});
                int st = camera.afStateNow();
                boolean scanEnded = st == android.hardware.camera2.CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED
                        || st == android.hardware.camera2.CameraMetadata.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED;
                if (el > 2600 || (el > 500 && scanEnded)) {
                    // Memória do foco: sempre trava a lente ao fim da busca (fica até novo toque,
                    // zoom ou modo) — 1º pico claro de nitidez; 2º onde a câmera confirmou o foco;
                    // 3º a distância mais nítida medida.
                    float d = clearPeak(trace);
                    if (d < 0 && st == android.hardware.camera2.CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED) d = dNow;
                    if (d < 0) d = sharpestSmoothed(trace);
                    if (d >= 0) camera.holdFocus(d);
                    focusTracker = null;
                    return;
                }
                main.postDelayed(this, 33);
            }
        };
        main.postDelayed(focusTracker, 50);
    }

    /**
     * Distância do pico de nitidez, ou -1 se não houver pico claro. A curva é suavizada (média de 3)
     * para o ruído de ISO alto não criar picos falsos, e o pico precisa superar a mediana em 35%
     * e a lente precisa ter percorrido distâncias diferentes durante a medição.
     */
    private static float clearPeak(java.util.List<float[]> t) {
        int n = t.size();
        if (n < 6) return -1f;
        float[] sm = new float[n];
        for (int i = 0; i < n; i++) {
            float a = t.get(Math.max(0, i - 1))[0], b = t.get(i)[0], c = t.get(Math.min(n - 1, i + 1))[0];
            sm[i] = (a + b + c) / 3f;
        }
        float[] sorted = sm.clone();
        java.util.Arrays.sort(sorted);
        float median = sorted[n / 2];
        int bi = 0;
        float dMin = Float.MAX_VALUE, dMax = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (sm[i] > sm[bi]) bi = i;
            dMin = Math.min(dMin, t.get(i)[1]);
            dMax = Math.max(dMax, t.get(i)[1]);
        }
        boolean lensMoved = dMax - dMin > 0.5f;
        if (!lensMoved || median <= 0 || sm[bi] < median * 1.35f) return -1f;
        return t.get(bi)[1];
    }

    /** Distância de maior nitidez na curva suavizada (sem exigir pico claro); -1 se vazia. */
    private static float sharpestSmoothed(java.util.List<float[]> t) {
        int n = t.size();
        if (n == 0) return -1f;
        int bi = 0;
        float bs = -1f;
        for (int i = 0; i < n; i++) {
            float s = (t.get(Math.max(0, i - 1))[0] + t.get(i)[0] + t.get(Math.min(n - 1, i + 1))[0]) / 3f;
            if (s > bs) {
                bs = s;
                bi = i;
            }
        }
        return t.get(bi)[1];
    }

    /** Focus peaking: marca as bordas mais nítidas da prévia (só na tela, nunca na foto). */
    private void startPeaking() {
        if (peakingTask != null) main.removeCallbacks(peakingTask);
        peakingTask = new Runnable() {
            @Override
            public void run() {
                if (!peaking) {
                    peakingTask = null;
                    return;
                }
                android.graphics.Bitmap b = resumed ? previewProbe(270, 360) : null;
                if (b != null) {
                    int w = b.getWidth(), h = b.getHeight();
                    int[] px = new int[w * h];
                    b.getPixels(px, 0, w, 0, 0, w, h);
                    int[] l = new int[w * h];
                    for (int i = 0; i < px.length; i++) l[i] = luma(px[i]);
                    int[] gm = new int[w * h];
                    int[] hist = new int[512];
                    for (int y = 1; y < h - 1; y++) {
                        for (int x = 1; x < w - 1; x++) {
                            int i = y * w + x;
                            int gx = l[i + 1] - l[i - 1], gy = l[i + w] - l[i - w];
                            int v = Math.min(511, Math.abs(gx) + Math.abs(gy));
                            gm[i] = v;
                            hist[v]++;
                        }
                    }
                    // limiar adaptativo: só os ~4% de bordas mais fortes, e nunca bordas fracas
                    int target = (int) (w * h * 0.04), acc = 0, thr = 511;
                    for (int v = 511; v >= 0; v--) {
                        acc += hist[v];
                        if (acc >= target) {
                            thr = v;
                            break;
                        }
                    }
                    thr = Math.max(thr, 28);
                    float sx = overlay.getWidth() / (float) w, sy = overlay.getHeight() / (float) h;
                    float[] pts = new float[w * h / 8];
                    int k = 0;
                    for (int i = 0; i < gm.length && k < pts.length - 1; i++) {
                        if (gm[i] >= thr) {
                            pts[k++] = (i % w + 0.5f) * sx;
                            pts[k++] = (i / w + 0.5f) * sy;
                        }
                    }
                    overlay.peakPts = java.util.Arrays.copyOf(pts, k);
                    overlay.peakSize = Math.max(sx, sy) * 1.1f;
                    overlay.invalidate();
                }
                main.postDelayed(this, 120);
            }
        };
        main.post(peakingTask);
    }

    // ---- Astro ---------------------------------------------------------------------------------

    private boolean astroTrails;
    private int astroMinutes = 3;
    private LinearLayout astroRow;
    private View astroScroll;

    private void buildAstroRow() {
        astroRow.removeAllViews();
        for (int k = 0; k < 2; k++) {
            final boolean t = k == 1;
            TextView p = Ui.pill(this, t ? "Rastro" : "Estrelas", t == astroTrails);
            p.setOnClickListener(v -> {
                astroTrails = t;
                camera.setAstro(astroTrails, astroMinutes);
                buildAstroRow();
            });
            astroRow.addView(p, Ui.pillParams(this));
        }
        if (!astroTrails) return;
        for (int m : new int[]{1, 3, 5, 10}) {
            TextView t = Ui.pill(this, m + " min", m == astroMinutes);
            t.setOnClickListener(v -> {
                astroMinutes = m;
                camera.setAstro(astroTrails, astroMinutes);
                buildAstroRow();
            });
            astroRow.addView(t, Ui.pillParams(this));
        }
    }

    // ---- Longa exposição ----------------------------------------------------------------------

    private int longSeconds = 2;
    private LinearLayout longRow;
    private View longScroll;

    private boolean longTrails = true;
    private int longProtect = 1;

    private void buildLongRow() {
        longRow.removeAllViews();
        for (int k = 0; k < 2; k++) {
            final boolean t = k == 0;
            TextView p = Ui.pill(this, t ? "Luzes" : "Água", t == longTrails);
            p.setOnClickListener(v -> {
                longTrails = t;
                camera.setLongTrails(t);
                buildLongRow();
                if (!busy) status.setText(statusHint());
            });
            longRow.addView(p, Ui.pillParams(this));
        }
        for (int s : new int[]{1, 2, 4}) {
            TextView t = Ui.pill(this, s + " s", s == longSeconds);
            t.setOnClickListener(v -> {
                longSeconds = s;
                camera.setLongSeconds(s);
                buildLongRow();
                if (!busy) status.setText(statusHint());
            });
            longRow.addView(t, Ui.pillParams(this));
        }
        // Proteção de luzes: captura mais escura e clareia a soma com ombro (luzes não estouram)
        for (int p : new int[]{0, 1, 2}) {
            TextView t = Ui.pill(this, p == 0 ? "Luz 0" : "Luz −" + p, p == longProtect);
            t.setOnClickListener(v -> {
                longProtect = p;
                camera.setLongProtect(p);
                buildLongRow();
            });
            longRow.addView(t, Ui.pillParams(this));
        }
    }

    // ---- Retrato --------------------------------------------------------------------------

    private float aperture = apertureOf(0.5f); // ≈ f/11, mesmo ponto inicial do dial
    private LinearLayout apertureRow;
    private View apertureScroll;
    private final java.util.concurrent.ExecutorService portraitWorker = java.util.concurrent.Executors.newSingleThreadExecutor();

    /**
     * Dial de desfoque (como o de EV): 0 = desligado (lente longa) / f/16 (Retrato), 1 = f/8.
     * Regra do usuário: nunca mais aberto que f/8 — sem bokeh exagerado (sempre acima de f/5,6).
     */
    private float blurLevel = 0.5f;

    /** Abertura simulada do nível (f/16 → f/8, contínuo). */
    private static float apertureOf(float level) {
        return (float) (16.0 * Math.pow(8.0 / 16.0, Math.max(0, Math.min(1, level))));
    }

    /** Abertura da versão de lente longa (6×–8×), ou 0 se o dial estiver desligado. */
    private float longLensAperture() {
        return blurLevel < 0.02f ? 0f : apertureOf(blurLevel);
    }

    private boolean blurDialVisible() {
        return mode == CameraController.Mode.RETRATO || (mode == CameraController.Mode.FOTO && zoom >= 5.5f);
    }

    private void buildApertureRow() {
        apertureRow.removeAllViews();
        PillSlider s = new PillSlider(this, "Desfoque", blurLevel, 0f, 1f);
        s.setFormat(v -> {
            if (mode != CameraController.Mode.RETRATO && v < 0.02f) return "desligado";
            return PortraitEngine.label(Math.round(apertureOf(v) * 10) / 10f);
        });
        s.setListener((v, drag) -> {
            blurLevel = v;
            aperture = apertureOf(v);
            if (!busy && !drag) status.setText(statusHint());
        });
        int width = getResources().getDisplayMetrics().widthPixels - dp(32);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, dp(40));
        lp.setMargins(dp(16), dp(4), dp(16), dp(4));
        apertureRow.addView(s, lp);
    }

    /**
     * Cria a versão com desfoque a partir da foto recém-gravada (que fica preservada).
     * Plano em foco = último toque no visor (ou o centro, um pouco acima, onde costuma estar o rosto).
     */
    private void makePortrait(Uri original, float apIn, String suffix, String kind) {
        makePortrait(original, apIn, suffix, kind, 1f);
    }

    /** frontScale abaixo de 1: desfoque inteligente — atrás do sujeito suave, a frente quase intacta. */
    private void makePortrait(Uri original, float apIn, String suffix, String kind, float frontScale) {
        final float fx = overlay.x >= 0 ? overlay.x / Math.max(1, overlay.getWidth()) : 0.5f;
        final float fy = overlay.y >= 0 ? overlay.y / Math.max(1, overlay.getHeight()) : 0.42f;
        final float ap = apIn;
        busy = true;
        shutter.invalidate();
        thumbBusy.setVisibility(View.VISIBLE);
        status.setText("Criando " + kind + " " + PortraitEngine.label(ap) + "…");
        portraitWorker.execute(() -> {
            try {
                long t0 = System.currentTimeMillis();
                android.graphics.Bitmap full = android.graphics.ImageDecoder.decodeBitmap(
                        android.graphics.ImageDecoder.createSource(getContentResolver(), original), (dec, info, s) -> {
                            dec.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
                            dec.setMutableRequired(true);
                        });
                long tDecode = System.currentTimeMillis();
                android.graphics.Bitmap guide = PortraitEngine.guideOf(full);
                PortraitEngine.Depth depth = PortraitEngine.estimate(this, guide);
                guide.recycle();
                float focus = depth.focusAt(fx, fy);
                long tDepth = System.currentTimeMillis();
                // recorte do sujeito tocado + profundidade (objetivo: recorte perfeito em qualquer zoom)
                float[] alpha = PortraitEngine.subjectAlpha(this, depth, fx, fy);
                PortraitEngine.Bokeh layers = PortraitEngine.bokeh(depth, focus, ap, alpha, frontScale);
                long tLayers = System.currentTimeMillis();
                int w = full.getWidth(), h = full.getHeight();
                int[] px = new int[w * h];
                full.getPixels(px, 0, w, 0, 0, w, h);
                PortraitEngine.apply(px, w, h, layers);
                full.setPixels(px, 0, w, 0, 0, w, h);
                px = null;
                layers = null;
                long tApply = System.currentTimeMillis();
                org.json.JSONObject sheet = new org.json.JSONObject();
                sheet.put("app", "The Natural Cam " + BuildConfigInfo.VERSION);
                sheet.put("origem", original.toString());
                sheet.put("retrato_abertura_simulada", PortraitEngine.label(ap));
                sheet.put("retrato_foco", fx + "," + fy);
                sheet.put("retrato_modelo", "Depth Anything V2 Small (Apache-2.0)");
                sheet.put("retrato_recorte", alpha != null ? "MagicTouch (Apache-2.0) + profundidade" : "só profundidade");
                sheet.put("retrato_tempo_profundidade_ms", depth.millis);
                sheet.put("retrato_tempo_inferencia_ms", depth.inferMs);
                sheet.put("retrato_tempo_decodificar_ms", tDecode - t0);
                sheet.put("retrato_tempo_camadas_ms", tLayers - tDepth);
                sheet.put("retrato_tempo_aplicar_ms", tApply - tLayers);
                sheet.put("retrato_resolucao", w + "x" + h);
                sheet.put("tipo", kind);
                Uri out = new MediaSaver(this).saveBitmap(MediaSaver.newName() + suffix, full, 0, 0, 0, sheet, original);
                android.util.Log.i("TNC", "retrato: decodificar " + (tDecode - t0) + " ms, profundidade " + depth.millis
                        + " ms (rede " + depth.inferMs + "), camadas " + (tLayers - tDepth) + " ms, aplicar " + (tApply - tLayers)
                        + " ms, salvar " + (System.currentTimeMillis() - tApply) + " ms, " + w + "x" + h);
                full.recycle();
                main.post(() -> onSaved(out, kind + " " + PortraitEngine.label(ap) + " · original mantido"));
            } catch (Throwable e) {
                main.post(() -> {
                    busy = false;
                    shutter.invalidate();
                    thumbBusy.setVisibility(View.GONE);
                    status.setText("Retrato indisponível: " + e.getMessage() + " (foto original salva)");
                });
            }
        });
    }

    @Override
    public void onQuickPreview(android.graphics.Bitmap preview) {
        lastPreview = preview;
        lastUri = null;
        thumb.setImageBitmap(preview);
        thumbBusy.setVisibility(View.VISIBLE);
    }

    private TextView hintBanner;

    @Override
    public void onHint(String text) {
        if (hintBanner == null) return;
        if (text == null) {
            hintBanner.setVisibility(View.GONE);
        } else {
            hintBanner.setText("⚠  " + text);
            hintBanner.setTextColor(Ui.ACCENT);
            hintBanner.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onNotice(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onError(String text) {
        busy = false;
        thumbBusy.setVisibility(View.GONE);
        shutter.invalidate();
        status.setText(text);
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    // =====================================================================================
    // Visor: foco, régua de EV ao lado do ponto, trava, pinça, grade
    // =====================================================================================

    private final class Overlay extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float x = -1, y = -1;
        long flashAt;
        final GestureDetector gestures;
        final ScaleGestureDetector pinch;

        // deslize horizontal = exposição (em qualquer ponto do visor)
        private static final int IDLE = 0, UNDECIDED = 1, EV_DRAG = 2;
        private int drag = IDLE;
        private float downX, downY, startFraction, dragFraction = 0.5f;
        private long evShownUntil;
        private long lastBarTap;

        Overlay(Activity a) {
            super(a);
            setClickable(true);
            setContentDescription("Visor. Toque para foco, segure para travar; deslize para os lados para ajustar a exposição.");
            gestures = new GestureDetector(a, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    x = e.getX();
                    y = e.getY();
                    locked = false;
                    lock.selected(false);
                    camera.focusAt(x / getWidth(), y / getHeight());
                    startFocusTracker(x / getWidth(), y / getHeight());
                    invalidate();
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    if (drag == EV_DRAG) return;
                    if (locked) {
                        setLocked(false);
                        return;
                    }
                    x = e.getX();
                    y = e.getY();
                    camera.focusAt(x / getWidth(), y / getHeight());
                    setLocked(true);
                    invalidate();
                }
            });
            pinch = new ScaleGestureDetector(a, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector d) {
                    zoomTo(zoom * d.getScaleFactor());
                    return true;
                }
            });
        }

        void flash() {
            flashAt = SystemClock.uptimeMillis();
            invalidate();
        }

        int evLow() {
            return caps == null ? 0 : caps.evRange.getLower();
        }

        int evHigh() {
            return caps == null ? 0 : caps.evRange.getUpper();
        }

        float fraction(int value) {
            int lo = evLow(), hi = evHigh();
            return hi <= lo ? 0.5f : (float) (value - lo) / (hi - lo);
        }

        // ---- barra-pílula horizontal na base do visor ---------------------------------
        float barWidth() {
            return Math.min(getWidth() * 0.72f, dp(340));
        }

        float barLeft() {
            return (getWidth() - barWidth()) / 2f;
        }

        float barCenterY() {
            boolean apertureVisible = (apertureScroll != null && apertureScroll.getVisibility() == View.VISIBLE)
                    || (longScroll != null && longScroll.getVisibility() == View.VISIBLE)
                    || (astroScroll != null && astroScroll.getVisibility() == View.VISIBLE);
            return getHeight() - dp(30) - (apertureVisible ? dp(48) : 0);
        }

        /** O cursor (raio 14 dp) anda só entre as bordas internas: nunca ultrapassa a pílula. */
        float xAt(float f) {
            float r = dp(14);
            return barLeft() + r + Math.max(0, Math.min(1, f)) * (barWidth() - 2 * r);
        }

        float fracAtX(float x) {
            float r = dp(14);
            return (x - barLeft() - r) / Math.max(1, barWidth() - 2 * r);
        }

        boolean onBar(float tx, float ty) {
            return Math.abs(ty - barCenterY()) < dp(26) && tx > barLeft() - dp(16) && tx < barLeft() + barWidth() + dp(16);
        }

        private void applyFraction(float f) {
            dragFraction = Math.max(0, Math.min(1, f));
            int lo = evLow(), hi = evHigh();
            int v = Math.round(lo + dragFraction * (hi - lo));
            if (v != ev) setEv(v); // a câmera recebe o novo valor durante o gesto (prévia ao vivo)
            evShownUntil = SystemClock.uptimeMillis() + 1500;
            invalidate();
        }

        private void cancelGestures(MotionEvent e) {
            MotionEvent cancel = MotionEvent.obtain(e);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            gestures.onTouchEvent(cancel);
            cancel.recycle();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            pinch.onTouchEvent(e);
            if (e.getPointerCount() > 1) {
                if (drag != IDLE) cancelGestures(e);
                drag = IDLE;
                return true;
            }
            boolean evAvailable = evHigh() > evLow();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    startFraction = fraction(ev);
                    // a barra só responde ao toque se já estiver ativa (aberta por um arraste lateral):
                    // antes, encostar nela sem querer (perto dos botões de zoom) mudava o EV
                    if (evAvailable && onBar(downX, downY) && SystemClock.uptimeMillis() < evShownUntil) {
                        long now = SystemClock.uptimeMillis();
                        if (now - lastBarTap < 350) {
                            // toque duplo na barra: exposição volta a 0
                            lastBarTap = 0;
                            drag = IDLE;
                            setEv(0);
                            evShownUntil = now + 1200;
                            invalidate();
                            return true;
                        }
                        lastBarTap = now;
                        // tocar direto na barra: posiciona o valor ali e segue o dedo
                        drag = EV_DRAG;
                        applyFraction(fracAtX(downX));
                        return true;
                    }
                    drag = UNDECIDED;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (drag == UNDECIDED && evAvailable) {
                        float dx = e.getX() - downX, dy = e.getY() - downY;
                        if (Math.abs(dx) > dp(32) && Math.abs(dx) > Math.abs(dy) * 2f) {
                            drag = EV_DRAG;
                            downX = e.getX();
                            cancelGestures(e);
                        }
                    }
                    if (drag == EV_DRAG) {
                        // atravessar ~75% da largura do visor percorre toda a faixa de exposição
                        float dx = e.getX() - downX;
                        applyFraction(startFraction + dx / (getWidth() * 0.75f));
                        if (onBar(downX, downY)) applyFraction(fracAtX(e.getX()));
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (drag == EV_DRAG) {
                        drag = IDLE;
                        evShownUntil = SystemClock.uptimeMillis() + 1500;
                        postInvalidateDelayed(1600);
                        performClick();
                        return true;
                    }
                    drag = IDLE;
                    break;
                default:
                    break;
            }
            gestures.onTouchEvent(e);
            return true;
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(0.6f));
            p.setColor(0x55FFFFFF);
            if (grid) {
                for (int i = 1; i < 3; i++) {
                    c.drawLine(getWidth() * i / 3f, 0, getWidth() * i / 3f, getHeight(), p);
                    c.drawLine(0, getHeight() * i / 3f, getWidth(), getHeight() * i / 3f, p);
                }
            }
            if (x >= 0) {
                p.setStyle(Paint.Style.STROKE);
                p.setColor(Ui.ACCENT);
                p.setStrokeWidth(dp(1.3f));
                float a = dp(28);
                c.drawRoundRect(x - a, y - a, x + a, y + a, dp(3), dp(3), p);
                if (locked) {
                    p.setTextSize(dp(11));
                    p.setStyle(Paint.Style.FILL);
                    c.drawText("AE/AF LOCK", Math.max(dp(8), x - dp(35)), y - dp(40), p);
                }
            }
            drawPeaking(c);
            drawEvBar(c);
            long fl = SystemClock.uptimeMillis() - flashAt;
            if (fl < 160) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb((int) (200 * (1 - fl / 160f)), 0, 0, 0));
                c.drawRect(0, 0, getWidth(), getHeight(), p);
                postInvalidateOnAnimation();
            }
        }

        float[] peakPts;
        float peakSize = 3f;

        private void drawPeaking(Canvas c) {
            float[] pts = peakPts;
            if (pts == null || pts.length == 0) return;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.ACCENT);
            p.setAlpha(230);
            p.setStrokeWidth(peakSize);
            p.setStrokeCap(Paint.Cap.SQUARE);
            c.drawPoints(pts, p);
            p.setAlpha(255);
        }

        /** Barra-pílula de exposição: forte durante o gesto, discreta quando EV ≠ 0, oculta em 0. */
        private void drawEvBar(Canvas c) {
            if (evHigh() <= evLow()) return;
            boolean active = drag == EV_DRAG || SystemClock.uptimeMillis() < evShownUntil;
            if (!active && ev == 0) return;
            int alpha = active ? 255 : 120;
            float left = barLeft(), w = barWidth(), cy = barCenterY();
            float th = dp(12);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x9908090B);
            p.setAlpha(Math.round(0x99 * alpha / 255f));
            c.drawRoundRect(left - dp(8), cy - dp(22), left + w + dp(8), cy + dp(14), dp(18), dp(18), p);
            p.setColor(0x55F5F5F5);
            p.setAlpha(Math.round(0x55 * alpha / 255f));
            c.drawRoundRect(left, cy - th / 2, left + w, cy + th / 2, th / 2, th / 2, p);
            float f = drag == EV_DRAG ? dragFraction : fraction(ev);
            float zero = xAt(fraction(0));
            float pos = xAt(f);
            // trecho entre 0 e o valor atual em laranja
            p.setColor(Ui.ACCENT);
            p.setAlpha(Math.round(200 * alpha / 255f));
            c.drawRoundRect(Math.min(zero, pos), cy - th / 2, Math.max(zero, pos), cy + th / 2, th / 2, th / 2, p);
            // marca do zero
            p.setColor(0xFFF5F5F5);
            p.setAlpha(alpha);
            c.drawRect(zero - dp(1), cy - th / 2 - dp(3), zero + dp(1), cy + th / 2 + dp(3), p);
            // cursor em forma de pílula
            p.setColor(Ui.ACCENT);
            p.setAlpha(alpha);
            c.drawRoundRect(pos - dp(14), cy - dp(10), pos + dp(14), cy + dp(10), dp(10), dp(10), p);
            // valor
            double stops = ev * caps.evStep.doubleValue();
            String label = String.format(PT, "EV %s%.1f", stops > 0.04 ? "+" : "", stops);
            p.setTextSize(dp(12));
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(0xFFF5F5F5);
            p.setAlpha(alpha);
            c.drawText(label, getWidth() / 2f, cy - dp(9), p);
            p.setTextAlign(Paint.Align.LEFT);
            if (active) postInvalidateDelayed(200);
        }
    }


    /** Obturador laranja; dispara no toque inicial (como no Natural Cam). */
    private final class Shutter extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Shutter(Activity a) {
            super(a);
            setClickable(true);
            setFocusable(true);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    setPressed(true);
                    invalidate();
                    performClick();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    setPressed(false);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    return true;
                default:
                    return super.onTouchEvent(event);
            }
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        @Override
        protected void onDraw(Canvas c) {
            float x = getWidth() / 2f, y = getHeight() / 2f, r = Math.min(x, y) - dp(3);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(busy ? 0xFF777777 : Ui.TEXT);
            c.drawCircle(x, y, r, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(busy ? 0xFF444444 : Ui.ACCENT);
            c.drawCircle(x, y, (r - dp(6)) * (isPressed() ? 0.92f : 1f), p);
        }
    }

    @SuppressWarnings("unused")
    private static ViewGroup.LayoutParams wrap() {
        return new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
