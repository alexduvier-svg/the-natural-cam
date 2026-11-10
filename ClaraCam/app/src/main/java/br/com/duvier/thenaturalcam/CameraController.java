package br.com.duvier.thenaturalcam;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraExtensionCharacteristics;
import android.hardware.camera2.CameraExtensionSession;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.ExtensionSessionConfiguration;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Motor de câmera Camera2.
 *
 * Princípios (as causas de ruído encontradas no Natural Cam 0.2.1):
 *  - resolução padrão com pixels agrupados (~12 MP); 50/200 MP só se o usuário escolher;
 *  - redução de ruído e nitidez da câmera sempre em ALTA QUALIDADE na foto;
 *  - câmera lógica (o zoom troca de lente por dentro, mantendo o processamento do fabricante);
 *  - modo Noite: extensão Noite do sistema quando existe; senão, fusão própria de vários quadros.
 */
final class CameraController {

    private static final String TAG = "TheNaturalCam";

    enum Mode { FOTO, RETRATO, RUA, NOITE, LONGA, ASTRO, RAW, HDR, PRO }

    interface Listener {
        void onOpened(CameraCaps caps);

        void onSessionReady(Mode mode, Size previewSize, String description);

        void onLive(int iso, long exposureNs, String nr);

        void onBusy(String text, float progress);

        void onSaved(Uri uri, String summary);

        /** Prévia reduzida da foto recém-capturada, antes de terminar o processamento. */
        void onQuickPreview(Bitmap preview);

        void onError(String text);

        /** Aviso persistente no visor (null = some): distância de foco, lente trocada etc. */
        void onHint(String text);

        /** Aviso rápido que não interrompe o processamento. */
        void onNotice(String text);
    }

    static final int FLASH_OFF = 0, FLASH_AUTO = 1, FLASH_ON = 2;

    private final Context context;
    private final CameraManager manager;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread thread;
    private final Handler handler;
    private final Executor handlerExecutor;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final MediaSaver saver;

    private CameraCaps caps;
    private CameraDevice device;
    private CameraCaptureSession session;
    private CameraExtensionSession ext;
    private int extType = -1;
    private int extFormat = ImageFormat.JPEG;
    private final boolean[] extFailed = new boolean[8];
    private boolean opening;
    // A tela quer a câmera aberta (entre start e stop). Evita aparelho "órfão" quando o stop
    // chega antes do onOpened, e permite reabrir se outro processo tomou a câmera (tela preta).
    private boolean wanted;
    private int reopenTries;
    private int generation;

    private SurfaceTexture texture;
    private Surface previewSurface;
    private ImageReader jpegReader;
    private ImageReader yuvReader;
    private ImageReader rawReader;
    private Size activePreview;

    // ajustes (lidos/escritos só na thread da câmera)
    private Mode mode = Mode.FOTO;
    private int resIndex = 0;
    private float zoom = 1f;
    /** Câmera lógica (interface) e tele física aberta direto; onTele = a tele está aberta. */
    private CameraCaps logicalCaps, teleCaps;
    private boolean onTele;

    /** Zoom pedido ao dispositivo aberto: na tele direta, relativo à própria tele (3,5× → 1×). */
    private float deviceZoom() {
        if (!onTele || teleCaps == null || logicalCaps == null) return zoom;
        return clamp(zoom / logicalCaps.teleRatio, teleCaps.zoomRange.getLower(), teleCaps.zoomRange.getUpper());
    }
    private int ev = 0;
    private int manualIso = 0;
    private long manualExposureNs = 0;
    private int flash = FLASH_OFF;
    static final int STACK_AUTO = 0, STACK_OFF = 1, STACK_MAX = 2;
    static final int NR_OFF = 0, NR_LIGHT = 1, NR_HIGH = 2;
    private int stackPolicy = STACK_AUTO;
    private int nrPolicy = NR_LIGHT;
    private int jpegOrientation = 90;
    private MeteringRectangle[] regions;
    private long regionsUntil;
    private float regionsZoom = 1f;
    private volatile Rect lastCrop;
    private MeteringRectangle[] faceRegions;
    private long faceSeenAt;
    private volatile int lastAfState = -1;
    /** Distância (dioptrias) em que a lente está, informada pela câmera a cada quadro. */
    private volatile float lastFocusDistance = -1f;
    /** Foco do toque travado nesta distância (dioptrias); negativo = sem trava. */
    private volatile float tapHold = -1f;
    private boolean afTriggered;
    private boolean aeLocked;
    private boolean userLock;
    private boolean awbLocked;
    private float focusDistance = -1f;

    // estado ao vivo
    private int lastIso;
    private long lastExposure;
    private long lastLivePost;
    private boolean capturing;

    // pré-captura (medição antes da foto)
    private static final int PRE_IDLE = 0, PRE_WAIT_START = 1, PRE_WAIT_DONE = 2, PRE_WAIT_LOCK = 3;
    private int preState = PRE_IDLE;
    private long preStartedAt;
    private Runnable preAction;

    // trabalho de captura em andamento
    private Job job;
    private final List<YuvFrame> burstFrames = new ArrayList<>();
    private final Map<Long, TotalCaptureResult> burstResults = new HashMap<>();
    private int burstTarget;
    private int longSeconds = 2;
    private boolean longTrails = true;
    /** Longa: quantos EV abaixo cada quadro é capturado para não estourar as luzes (0–2). */
    private int longProtect = 1;

    void setLongProtect(int stops) {
        handler.post(() -> longProtect = Math.max(0, Math.min(2, stops)));
    }

    private static final class Job {
        String name;
        int gen;
        int orientation;
        boolean needsResult = true;
        String pipeline;
        byte[] jpeg;
        boolean needsRaw;
        ByteBuffer raw;
        Size rawSize;
        boolean rawDone;
        /** RAW+: rajada de RAW para o ISP próprio (null = não é RAW+). */
        List<RawIsp.Frame> rawFrames;
        int rawTarget;
        boolean superRes;
        LongExposure longExp;
        int longCount;
        /** Quadros entregues pela câmera (somados ou pulados) e somas ainda na fila. */
        int longSeen;
        final java.util.concurrent.atomic.AtomicInteger longPending = new java.util.concurrent.atomic.AtomicInteger();
        TotalCaptureResult result;
        boolean done;
        JSONObject sheet = new JSONObject();
    }

    CameraController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        this.listener = listener;
        this.saver = new MediaSaver(context);
        thread = new HandlerThread("TheNaturalCam-camera");
        thread.start();
        handler = new Handler(thread.getLooper());
        handlerExecutor = handler::post;
    }

    // =====================================================================================
    // API pública (chamada pela tela)
    // =====================================================================================

    void start(SurfaceTexture tex) {
        handler.post(() -> {
            texture = tex;
            wanted = true;
            reopenTries = 0;
            if (previewSurface != null) previewSurface.release();
            previewSurface = new Surface(texture);
            openCamera();
        });
    }

    void stop() {
        handler.post(() -> { wanted = false; closeAll(); });
    }

    void release() {
        handler.post(() -> {
            wanted = false;
            closeAll();
            if (previewSurface != null) previewSurface.release();
            previewSurface = null;
            thread.quitSafely();
        });
        worker.shutdown();
    }

    /** Distância atual da lente (dioptrias), estado do AF e se há um toque em andamento. */
    float focusDistanceNow() {
        return lastFocusDistance;
    }

    int afStateNow() {
        return lastAfState;
    }

    /**
     * Trava a lente na distância indicada (dioptrias) — usado para "agarrar" o ponto de maior
     * nitidez medido durante a varredura do toque. Vale até novo toque, troca de zoom ou modo.
     */
    void holdFocus(float diopters) {
        handler.post(() -> {
            if (session == null || caps == null || capturing || regions == null) return;
            // Foco sem calibração (Reno16): pedir uma distância não devolve a lente ao mesmo
            // ponto (medido: travar no valor informado borrou a imagem). Aí quem segura é o AF AUTO.
            if (!caps.focusCalibrated) return;
            tapHold = Math.max(0f, diopters);
            tapScanning = false;
            Log.w(TAG, "foco travado no pico de nitidez: " + tapHold + " dpt (" + (tapHold > 0 ? Math.round(100 / tapHold) + " cm" : "infinito") + ")");
            startRepeating();
        });
    }

    void setMode(Mode m) {
        handler.post(() -> {
            if (mode == m || capturing) return;
            tapHold = -1f;
            mode = m;
            regions = null;
            if (m != Mode.PRO) manualIso = 0;
            configure();
        });
    }

    void setResolution(int index) {
        handler.post(() -> {
            if (resIndex == index || capturing) return;
            resIndex = index;
            if (session != null && mode != Mode.NOITE) configure();
        });
    }

    void setZoom(float z) {
        handler.post(() -> {
            if (caps == null || logicalCaps == null) return;
            zoom = clamp(z, logicalCaps.zoomRange.getLower(), logicalCaps.zoomRange.getUpper());
            boolean wantTele = teleCaps != null && zoom >= logicalCaps.teleRatio * 0.97f;
            if (wantTele != onTele && !capturing) {
                // troca de lente física: fecha e abre a outra (≈ meio segundo), sem travar a tela
                onTele = wantTele;
                caps = wantTele ? teleCaps : logicalCaps;
                regions = null;
                tapHold = -1f;
                closeAll();
                if (wanted) openCamera();
                return;
            }
            // enquadramento mudou: o ponto tocado antes não corresponde mais ao mesmo objeto
            if (regions != null && !userLock && Math.abs(Math.log(zoom / regionsZoom)) > 0.05) {
                regions = null;
                tapHold = -1f;
            }
            if (session != null && !capturing) startRepeating();
        });
    }

    void setEv(int value) {
        handler.post(() -> {
            ev = value;
            if (session != null && !capturing) startRepeating();
        });
    }

    /** iso = 0 volta ao automático. */
    void setManual(int iso, long exposureNs) {
        handler.post(() -> {
            manualIso = iso;
            manualExposureNs = exposureNs;
            if (session != null && !capturing) startRepeating();
        });
    }

    /** Trava (true) ou libera exposição e foco. Ao travar, foca e mede no ponto atual. */
    void lock(boolean on) {
        handler.post(() -> {
            if (session == null || capturing) return;
            userLock = on;
            if (on) {
                regionsUntil = Long.MAX_VALUE;
                try {
                    if (CameraCaps.contains(caps.afModes, CameraMetadata.CONTROL_AF_MODE_AUTO) && focusDistance < 0) {
                        CaptureRequest.Builder b = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        b.addTarget(previewSurface);
                        applyCommon(b, false, false);
                        b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
                        session.capture(b.build(), liveCallback, handler);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "trava", e);
                }
            } else {
                regions = null;
                tapHold = -1f;
            }
            startRepeating();
        });
    }

    void setAwbLock(boolean on) {
        handler.post(() -> {
            awbLocked = on;
            if (session != null && !capturing) startRepeating();
        });
    }

    /** Distância em dioptrias (0 = infinito); negativo volta ao foco automático. */
    void setFocusDistance(float d) {
        handler.post(() -> {
            focusDistance = d;
            if (session != null && !capturing) startRepeating();
        });
    }

    void setFlash(int f) {
        handler.post(() -> {
            boolean before = useFusion();
            flash = f;
            if (session == null || capturing) return;
            if (before != useFusion()) configure();
            else startRepeating();
        });
    }

    void setStackPolicy(int p) {
        handler.post(() -> {
            if (stackPolicy == p) return;
            stackPolicy = p;
            if (session != null && !capturing && (mode == Mode.FOTO || mode == Mode.NOITE || mode == Mode.RETRATO)) configure();
        });
    }

    void setNrPolicy(int p) {
        handler.post(() -> nrPolicy = p);
    }

    void setOrientation(int degrees) {
        handler.post(() -> {
            if (caps == null) return;
            jpegOrientation = (caps.sensorOrientation + degrees + 360) % 360;
        });
    }

    /**
     * Retângulo (em coordenadas do sensor) que corresponde ao que a prévia mostra.
     * Pela especificação, com CONTROL_ZOOM_RATIO as regiões usam o campo de visão já com zoom
     * (= matriz ativa inteira). Alguns HALs (vistos em OPPO/MediaTek) ainda respondem no sistema
     * antigo, de recorte: isso aparece no SCALER_CROP_REGION do resultado, menor que a matriz.
     * Nesse caso a região precisa ser montada dentro desse recorte, senão o toque cai em outro
     * ponto da cena (geralmente o fundo).
     */
    private Rect visibleSensorRect() {
        Rect active = caps.ch.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (active == null) return null;
        Rect full = new Rect(0, 0, active.width(), active.height());
        Rect crop = lastCrop;
        if (zoom > 1.05f && crop != null && crop.width() < full.width() * 0.9f && crop.width() > 16) return crop;
        return full;
    }

    private static float[] toSensor(int sensorOrientation, float nx, float ny) {
        switch (sensorOrientation) {
            case 90: return new float[]{ny, 1f - nx};
            case 270: return new float[]{1f - ny, nx};
            case 180: return new float[]{1f - nx, 1f - ny};
            default: return new float[]{nx, ny};
        }
    }

    private MeteringRectangle[] regionAt(float nx, float ny, float size, int weight) {
        Rect v = visibleSensorRect();
        if (v == null) return null;
        float[] s = toSensor(caps.sensorOrientation, nx, ny);
        int half = Math.max(8, Math.round(Math.min(v.width(), v.height()) * size));
        int cx = v.left + Math.round(s[0] * v.width()), cy = v.top + Math.round(s[1] * v.height());
        Rect r = new Rect(clampI(cx - half, v.left, v.right - 2), clampI(cy - half, v.top, v.bottom - 2),
                clampI(cx + half, v.left + 2, v.right), clampI(cy + half, v.top + 2, v.bottom));
        return new MeteringRectangle[]{new MeteringRectangle(r, weight)};
    }

    /** Toque para foco/medição. nx, ny em 0..1 relativos à prévia em pé (retrato). */
    void focusAt(float nx, float ny) {
        handler.post(() -> {
            if (session == null || caps == null || capturing) return;
            // Toque de novo no mesmo objeto (perto do toque anterior, mesmo zoom): se o foco já
            // está travado nele, ou a busca ainda está em andamento, MANTÉM — não refaz a busca
            // (antes o foco "respirava" a cada toque). Outro ponto da cena busca normalmente.
            float dx = nx - tapX, dy = ny - tapY;
            boolean samePlace = regions != null && Math.abs(zoom - regionsZoom) < 0.01f && dx * dx + dy * dy < 0.08f * 0.08f;
            if (samePlace && (tapScanning || lastAfState == CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED)) {
                Log.w(TAG, "foco mantido: toque no mesmo ponto já focado (" + nx + "," + ny + ")");
                return;
            }
            userLock = false;
            tapX = nx;
            tapY = ny;
            tapRetries = 0;
            tapCaf = false;
            tapHold = -1f;
            regions = regionAt(nx, ny, 0.07f, MeteringRectangle.METERING_WEIGHT_MAX - 1);
            if (regions == null) return;
            regionsZoom = zoom;
            scanTap();
            Rect rr = regions[0].getRect();
            Rect crop = lastCrop;
            Log.w(TAG, "foco toque n=(" + nx + "," + ny + ") zoom=" + zoom + " regiao=" + rr.toShortString()
                    + " recorte=" + (crop == null ? "?" : crop.toShortString()));
            // o ponto tocado vale até novo toque, troca de zoom ou de modo (não expira sozinho)
        });
    }

    private float tapX, tapY;
    private int tapRetries;
    private boolean tapScanning;
    private boolean tapCaf;

    /**
     * Varredura ativa no ponto tocado (modo AUTO): o foco contínuo deste aparelho ignora troca de
     * região (medido no Reno16), então é preciso CANCELAR e, no quadro seguinte, INICIAR uma
     * varredura. Se ela falhar (pouca luz/pouco contraste), tenta de novo com área maior.
     */
    private void scanTap() {
        if (session == null || device == null) return;
        try {
            startRepeating(); // a prévia passa a pedir AF AUTO com a nova região
            CaptureRequest.Builder c = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            c.addTarget(previewSurface);
            applyCommon(c, false, false);
            c.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            session.capture(c.build(), liveCallback, handler);
            final int gen = generation;
            handler.postDelayed(() -> {
                if (gen != generation || session == null || capturing) return;
                try {
                    CaptureRequest.Builder b = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    b.addTarget(previewSurface);
                    applyCommon(b, false, false);
                    b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
                    tapScanning = true;
                    session.capture(b.build(), liveCallback, handler);
                } catch (Exception e) {
                    Log.w(TAG, "foco", e);
                }
            }, 70);
        } catch (Exception e) {
            Log.w(TAG, "foco", e);
        }
    }

    /** Chamado a cada mudança de estado do AF durante a varredura do toque. */
    private void onTapAf(int st) {
        if (!tapScanning) return;
        if (st == CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED) {
            tapScanning = false;
        } else if (st == CameraMetadata.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
            tapScanning = false;
            if (tapRetries < 2 && regions != null) {
                // Área sem textura em pouca luz: amplia a área (pega bordas próximas do ponto)
                // e varre de novo — 14% e depois 26% do quadro em volta do toque.
                tapRetries++;
                regions = regionAt(tapX, tapY, tapRetries == 1 ? 0.14f : 0.26f, MeteringRectangle.METERING_WEIGHT_MAX - 1);
                Log.w(TAG, "foco falhou; tentativa " + (tapRetries + 1) + " com área maior");
                scanTap();
            } else {
                // Não volta ao foco contínuo (é ele que fica "indo e voltando"): o AF AUTO
                // mantém a lente onde a última varredura parou até um novo toque.
                Log.w(TAG, "foco não confirmado após 3 tentativas; mantendo a lente parada");
            }
        }
    }

    void capture() {
        handler.post(() -> {
            if (capturing || device == null || (session == null && ext == null)) return;
            capturing = true;
            busy("Capturando…", -1);
            try {
                if (ext != null) {
                    captureExtension();
                } else if (yuvReader != null && mode == Mode.ASTRO) {
                    astroBurst(); // tripé: exposição manual, sem medir
                } else if (yuvReader != null && mode == Mode.RUA) {
                    burst(); // Rua: dispara na hora, sem esperar foco/medição (instante decisivo)
                } else if (mode == Mode.RAW && rawReader != null) {
                    precaptureThen(() -> {
                        if (isManual()) {
                            rawBurst();
                            return;
                        }
                        aeLocked = true;
                        startRepeating();
                        preAction = this::rawBurst;
                        preState = PRE_WAIT_LOCK;
                        preStartedAt = SystemClock.elapsedRealtime();
                    });
                } else if (yuvReader != null && (mode == Mode.NOITE || mode == Mode.FOTO || mode == Mode.RETRATO || mode == Mode.LONGA)) {
                    precaptureThen(this::lockAeThenBurst);
                } else if (isManual()) {
                    stillCapture();
                } else {
                    precaptureThen(this::stillCapture);
                }
            } catch (Exception e) {
                fail("Falha ao capturar: " + e.getMessage());
            }
        });
    }

    // =====================================================================================
    // Abrir / configurar
    // =====================================================================================

    @SuppressLint("MissingPermission")
    private void openCamera() {
        if (device != null) {
            configure();
            return;
        }
        if (opening) return;
        try {
            if (caps == null) {
                caps = logicalCaps = CameraCaps.pick(manager);
                teleCaps = CameraCaps.teleOf(manager, logicalCaps);
                Log.w(TAG, "tele direta: " + (teleCaps != null ? "câmera " + teleCaps.id + " a partir de "
                        + logicalCaps.teleRatio + "×" : "indisponível"));
            }
            opening = true;
            manager.openCamera(caps.id, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice cameraDevice) {
                    opening = false;
                    if (!wanted) { cameraDevice.close(); return; }
                    if (device != null && device != cameraDevice) device.close();
                    reopenTries = 0;
                    device = cameraDevice;
                    CameraCaps c = caps;
                    // a interface (zooms, modos) segue a câmera lógica; a tele direta é interna
                    if (c == logicalCaps) main.post(() -> listener.onOpened(c));
                    configure();
                }

                @Override
                public void onDisconnected(CameraDevice cameraDevice) {
                    opening = false;
                    cameraDevice.close();
                    if (device == cameraDevice) { closeAll(); }
                    Log.w(TAG, "câmera desconectada (wanted=" + wanted + ")");
                    retryOpen();
                }

                @Override
                public void onError(CameraDevice cameraDevice, int error) {
                    opening = false;
                    cameraDevice.close();
                    if (device == cameraDevice) { closeAll(); }
                    Log.w(TAG, "câmera erro " + error);
                    if (retryOpen()) return;
                    error("A câmera está ocupada ou falhou (código " + error + "). Feche outros apps de câmera.");
                }
            }, handler);
        } catch (Exception e) {
            opening = false;
            error("Não foi possível abrir a câmera: " + e.getMessage());
        }
    }

    /** Reabre depois de uma desconexão/erro, se a tela ainda quer a câmera (até 4 vezes). */
    private boolean retryOpen() {
        if (!wanted || reopenTries >= 4) return false;
        reopenTries++;
        handler.postDelayed(() -> { if (wanted && device == null) openCamera(); }, 400L * reopenTries);
        return true;
    }

    private void closeAll() {
        generation++;
        closeSession();
        if (device != null) {
            device.close();
            device = null;
        }
        capturing = false;
        preState = PRE_IDLE;
        burstFrames.clear();
        burstResults.clear();
    }

    private void closeSession() {
        if (session != null) {
            try { session.close(); } catch (Exception ignored) { }
            session = null;
        }
        if (ext != null) {
            try { ext.close(); } catch (Exception ignored) { }
            ext = null;
        }
        extType = -1;
        if (jpegReader != null) {
            jpegReader.close();
            jpegReader = null;
        }
        if (yuvReader != null) {
            yuvReader.close();
            yuvReader = null;
        }
        if (rawReader != null) {
            rawReader.close();
            rawReader = null;
        }
    }

    private void configure() {
        if (device == null || texture == null) return;
        generation++;
        closeSession();
        aeLocked = false;
        preState = PRE_IDLE;
        try {
            // Noite: sempre a fusão própria. A extensão "Noite do sistema" da OPPO ignora o zoom
            // (o modo Noite ficava preso em 1×) e, medido antes, não processa nada a mais.
            if (false && mode == Mode.NOITE && caps.extNight && !extFailed[CameraExtensionCharacteristics.EXTENSION_NIGHT]) {
                configureExtension(CameraExtensionCharacteristics.EXTENSION_NIGHT);
            } else if (mode == Mode.HDR && caps.extHdr && !extFailed[CameraExtensionCharacteristics.EXTENSION_HDR]) {
                configureExtension(CameraExtensionCharacteristics.EXTENSION_HDR);
            } else {
                if (mode == Mode.HDR) mode = Mode.FOTO;
                configureStandard();
            }
        } catch (Exception e) {
            error("Configuração da câmera falhou: " + e.getMessage());
        }
    }

    private CameraCaps.ResOption currentRes() {
        if (caps.resolutions.isEmpty()) return null;
        return caps.resolutions.get(Math.min(resIndex, caps.resolutions.size() - 1));
    }

    private void configureStandard() throws CameraAccessException {
        final int gen = generation;
        Size pv = caps.previewSize;
        texture.setDefaultBufferSize(pv.getWidth(), pv.getHeight());
        activePreview = pv;
        List<OutputConfiguration> outs = new ArrayList<>();
        OutputConfiguration pc = new OutputConfiguration(previewSurface);
        outs.add(pc);
        final String description;
        final CameraCaps.ResOption res = currentRes();
        if (mode == Mode.NOITE || useFusion()) {
            if (caps.nightYuvSize == null) {
                mode = Mode.FOTO;
                error("Esta câmera não entrega quadros YUV; modo Noite indisponível.");
                configureStandard();
                return;
            }
            Size ys = caps.nightYuvSize;
            yuvReader = ImageReader.newInstance(ys.getWidth(), ys.getHeight(), ImageFormat.YUV_420_888, 14);
            yuvReader.setOnImageAvailableListener(this::onYuvImage, handler);
            outs.add(new OutputConfiguration(yuvReader.getSurface()));
            description = (mode == Mode.NOITE ? "Noite" : mode == Mode.RETRATO ? "Retrato" : mode == Mode.RUA ? "Rua · rápido"
                    : mode == Mode.LONGA ? "Longa exposição" : mode == Mode.ASTRO ? "Astro · tripé" : "Foto") + " · " + CameraCaps.mp(ys) + " · empilhamento "
                    + (stackPolicy == STACK_MAX ? "máximo" : "automático");
        } else if (mode == Mode.RAW) {
            if (!caps.raw || caps.rawSize == null || caps.resolutions.isEmpty()) {
                mode = Mode.FOTO;
                error("Esta câmera não oferece RAW para apps.");
                configureStandard();
                return;
            }
            // RAW+: só RAW do sensor; a revelação é toda nossa (RawIsp)
            rawReader = ImageReader.newInstance(caps.rawSize.getWidth(), caps.rawSize.getHeight(), ImageFormat.RAW_SENSOR, 8);
            rawReader.setOnImageAvailableListener(this::onRawImage, handler);
            outs.add(new OutputConfiguration(rawReader.getSurface()));
            description = "RAW+ · revelação própria do sensor · " + CameraCaps.mp(caps.rawSize);
        } else {
            if (res == null) throw new CameraAccessException(CameraAccessException.CAMERA_ERROR, "sem tamanhos JPEG");
            jpegReader = ImageReader.newInstance(res.size.getWidth(), res.size.getHeight(), ImageFormat.JPEG, 2);
            jpegReader.setOnImageAvailableListener(this::onJpegImage, handler);
            OutputConfiguration jc = new OutputConfiguration(jpegReader.getSurface());
            if (res.maximumResolution) {
                jc.addSensorPixelModeUsed(CameraMetadata.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION);
                pc.addSensorPixelModeUsed(CameraMetadata.SENSOR_PIXEL_MODE_DEFAULT);
            }
            outs.add(jc);
            description = (mode == Mode.PRO ? "Pro · " : "Foto · ") + res.label
                    + (res.maximumResolution ? " · sensor cheio (mais ruído)" : "");
        }
        SessionConfiguration sc = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outs, handlerExecutor,
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(CameraCaptureSession s) {
                        if (gen != generation) {
                            s.close();
                            return;
                        }
                        session = s;
                        startRepeating();
                        Size p = activePreview;
                        Mode m = mode;
                        main.post(() -> listener.onSessionReady(m, p, description));
                    }

                    @Override
                    public void onConfigureFailed(CameraCaptureSession s) {
                        if (gen != generation) return;
                        if (res != null && resIndex > 0) {
                            resIndex = 0;
                            error("A câmera recusou " + res.label + "; voltando para " + caps.resolutions.get(0).label + ".");
                            configure();
                        } else {
                            error("A câmera recusou a configuração.");
                        }
                    }
                });
        device.createCaptureSession(sc);
    }

    private void configureExtension(int type) throws CameraAccessException {
        final int gen = generation;
        List<Size> pvSizes = caps.extensionPreviewSizes(manager, type);
        Size pv = CameraCaps.largest43(pvSizes, 1920L * 1440L);
        if (pv == null && !pvSizes.isEmpty()) {
            pv = pvSizes.get(pvSizes.size() - 1);
            for (Size s : pvSizes) {
                if (CameraCaps.area(s) <= 1920L * 1440L) {
                    pv = s;
                    break;
                }
            }
        }
        if (pv == null) pv = caps.previewSize;
        texture.setDefaultBufferSize(pv.getWidth(), pv.getHeight());
        activePreview = pv;

        List<Size> jpeg = caps.extensionSizes(manager, type, ImageFormat.JPEG);
        Surface still;
        Size stillSize;
        if (!jpeg.isEmpty()) {
            stillSize = pickStill(jpeg);
            extFormat = ImageFormat.JPEG;
            jpegReader = ImageReader.newInstance(stillSize.getWidth(), stillSize.getHeight(), ImageFormat.JPEG, 2);
            jpegReader.setOnImageAvailableListener(this::onJpegImage, handler);
            still = jpegReader.getSurface();
        } else {
            List<Size> yuv = caps.extensionSizes(manager, type, ImageFormat.YUV_420_888);
            stillSize = pickStill(yuv);
            extFormat = ImageFormat.YUV_420_888;
            yuvReader = ImageReader.newInstance(stillSize.getWidth(), stillSize.getHeight(), ImageFormat.YUV_420_888, 2);
            yuvReader.setOnImageAvailableListener(this::onYuvImage, handler);
            still = yuvReader.getSurface();
        }
        List<OutputConfiguration> outs = new ArrayList<>();
        outs.add(new OutputConfiguration(previewSurface));
        outs.add(new OutputConfiguration(still));
        final String description = (type == CameraExtensionCharacteristics.EXTENSION_NIGHT ? "Noite do sistema · " : "HDR do sistema · ")
                + CameraCaps.mp(stillSize);
        final Size previewFinal = pv;
        ExtensionSessionConfiguration cfg = new ExtensionSessionConfiguration(type, outs, handlerExecutor,
                new CameraExtensionSession.StateCallback() {
                    @Override
                    public void onConfigured(CameraExtensionSession s) {
                        if (gen != generation) {
                            try { s.close(); } catch (Exception ignored) { }
                            return;
                        }
                        ext = s;
                        extType = type;
                        try {
                            CaptureRequest.Builder b = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            b.addTarget(previewSurface);
                            s.setRepeatingRequest(b.build(), handlerExecutor, new CameraExtensionSession.ExtensionCaptureCallback() { });
                        } catch (Exception e) {
                            Log.w(TAG, "prévia extensão", e);
                        }
                        Mode m = mode;
                        main.post(() -> listener.onSessionReady(m, previewFinal, description));
                    }

                    @Override
                    public void onConfigureFailed(CameraExtensionSession s) {
                        if (gen != generation) return;
                        extFailed[type] = true;
                        error("O modo do sistema falhou; usando o modo próprio do app.");
                        configure();
                    }
                });
        device.createExtensionSession(cfg);
    }

    private static Size pickStill(List<Size> sizes) {
        Size s = CameraCaps.largest43(sizes, 52_000_000L);
        if (s == null && !sizes.isEmpty()) s = sizes.get(0);
        return s;
    }

    // =====================================================================================
    // Pedidos de captura
    // =====================================================================================

    private boolean isManual() {
        return mode == Mode.PRO && manualIso > 0 && manualExposureNs > 0 && caps.manualSensor;
    }

    private int aeMode() {
        CameraCaps.ResOption res = currentRes();
        boolean flashAllowed = caps.flash && (mode == Mode.FOTO || mode == Mode.PRO)
                && (res == null || !res.maximumResolution);
        if (flashAllowed && flash == FLASH_ON) return CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH;
        if (flashAllowed && flash == FLASH_AUTO) return CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH;
        return CameraMetadata.CONTROL_AE_MODE_ON;
    }

    private void applyCommon(CaptureRequest.Builder b, boolean still, boolean maxRes) {
        b.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        boolean useRegions = regions != null && !maxRes;
        boolean manualFocus = focusDistance >= 0f && caps.minFocus > 0f
                && CameraCaps.contains(caps.afModes, CameraMetadata.CONTROL_AF_MODE_OFF);
        boolean holdFocus = tapHold >= 0f && caps.minFocus > 0f
                && CameraCaps.contains(caps.afModes, CameraMetadata.CONTROL_AF_MODE_OFF);
        if (manualFocus) {
            b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
            b.set(CaptureRequest.LENS_FOCUS_DISTANCE, Math.min(focusDistance, caps.minFocus));
        } else if (holdFocus) {
            // foco do toque "agarrado" no ponto de maior nitidez medido: lente parada ali
            b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
            b.set(CaptureRequest.LENS_FOCUS_DISTANCE, Math.min(tapHold, caps.minFocus));
        } else if (userLock && CameraCaps.contains(caps.afModes, CameraMetadata.CONTROL_AF_MODE_AUTO)) {
            // foco travado: modo AUTO sem novo gatilho mantém a lente onde focou
            b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
            if (useRegions && caps.maxAfRegions > 0) b.set(CaptureRequest.CONTROL_AF_REGIONS, regions);
        } else if (useRegions && caps.maxAfRegions > 0
                && (tapCaf || !CameraCaps.contains(caps.afModes, CameraMetadata.CONTROL_AF_MODE_AUTO))
                && CameraCaps.contains(caps.afModes, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
            // toque: foco contínuo restrito à região — insiste até achar (pouca luz, pouco contraste)
            b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            b.set(CaptureRequest.CONTROL_AF_REGIONS, regions);
        } else if (useRegions && CameraCaps.contains(caps.afModes, CameraMetadata.CONTROL_AF_MODE_AUTO) && caps.maxAfRegions > 0) {
            b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
            b.set(CaptureRequest.CONTROL_AF_REGIONS, regions);
        } else if (CameraCaps.contains(caps.afModes, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
            b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // Sem toque: prioriza rosto detectado; senão o centro do quadro — evita que o foco
            // contínuo "escolha" o fundo de maior contraste (cadeira atrás do copo).
            if (caps.maxAfRegions > 0 && !maxRes) {
                MeteringRectangle[] auto = currentFaces();
                if (auto == null) auto = regionAt(0.5f, 0.5f, 0.16f, MeteringRectangle.METERING_WEIGHT_MAX - 1);
                if (auto != null) b.set(CaptureRequest.CONTROL_AF_REGIONS, auto);
            }
        }
        if (useRegions && caps.maxAeRegions > 0) b.set(CaptureRequest.CONTROL_AE_REGIONS, regions);
        else if (!maxRes && caps.maxAeRegions > 0 && currentFaces() != null) b.set(CaptureRequest.CONTROL_AE_REGIONS, currentFaces());
        if (caps.faceDetectMode != CameraMetadata.STATISTICS_FACE_DETECT_MODE_OFF) {
            b.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, caps.faceDetectMode);
        }

        if (isManual()) {
            b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
            b.set(CaptureRequest.SENSOR_SENSITIVITY, manualIso);
            b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, manualExposureNs);
            b.set(CaptureRequest.SENSOR_FRAME_DURATION, Math.max(manualExposureNs, 33_333_333L));
        } else {
            b.set(CaptureRequest.CONTROL_AE_MODE, aeMode());
            b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                    clampI(ev, caps.evRange.getLower(), caps.evRange.getUpper()));
            b.set(CaptureRequest.CONTROL_AE_LOCK, (aeLocked || userLock) && caps.aeLock);
        }
        b.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO);
        if (caps.awbLock) b.set(CaptureRequest.CONTROL_AWB_LOCK, awbLocked);
        b.set(CaptureRequest.CONTROL_ZOOM_RATIO, maxRes ? 1f : deviceZoom());

        if (still) {
            // Quadro único: "leve" por padrão. A redução HIGH_QUALITY da OPPO apaga textura em ISO alto
            // (aspecto de pintura); OFF deixa todo o ruído. OFF/alta ficam como escolha no Pro.
            int nr = nrPolicy == NR_OFF ? CameraMetadata.NOISE_REDUCTION_MODE_OFF
                    : nrPolicy == NR_HIGH ? CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
                    : CameraMetadata.NOISE_REDUCTION_MODE_FAST;
            if (caps.supportsNr(nr)) b.set(CaptureRequest.NOISE_REDUCTION_MODE, nr);
            int edge = nrPolicy == NR_OFF ? CameraMetadata.EDGE_MODE_OFF : CameraMetadata.EDGE_MODE_HIGH_QUALITY;
            if (caps.supportsEdge(edge)) b.set(CaptureRequest.EDGE_MODE, edge);
            if (CameraCaps.contains(caps.aberrationModes, CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY)) {
                b.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY);
            }
            if (CameraCaps.contains(caps.hotPixelModes, CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY)) {
                b.set(CaptureRequest.HOT_PIXEL_MODE, CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY);
            }
            b.set(CaptureRequest.JPEG_QUALITY, (byte) 96);
            b.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation);
            b.set(CaptureRequest.CONTROL_ENABLE_ZSL, false);
        }
    }

    private void startRepeating() {
        if (session == null || device == null) return;
        try {
            CaptureRequest.Builder b = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            b.addTarget(previewSurface);
            applyCommon(b, false, false);
            session.setRepeatingRequest(b.build(), liveCallback, handler);
        } catch (Exception e) {
            Log.w(TAG, "prévia", e);
        }
    }

    private final CameraCaptureSession.CaptureCallback liveCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest request, TotalCaptureResult result) {
            Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
            Long exp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            if (iso != null) lastIso = iso;
            if (exp != null) lastExposure = exp;
            Rect crop = result.get(CaptureResult.SCALER_CROP_REGION);
            if (crop != null) lastCrop = crop;
            Float lfd = result.get(CaptureResult.LENS_FOCUS_DISTANCE);
            if (lfd != null) lastFocusDistance = lfd;
            Integer af = result.get(CaptureResult.CONTROL_AF_STATE);
            if (af != null && af != lastAfState) {
                Float fd = result.get(CaptureResult.LENS_FOCUS_DISTANCE);
                String phys = result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
                Log.w(TAG, "AF estado " + lastAfState + "->" + af + " foco(dpt)=" + fd + " lente=" + phys + " iso=" + lastIso);
            }
            String physNow = result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
            if (physNow != null) lastPhys = physNow;
            if (af != null && af != lastAfState) onTapAf(af);
            if (af != null) lastAfState = af;
            // avisos de distância removidos a pedido do usuário (não ajudavam); ficam só na ficha
            android.hardware.camera2.params.Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
            if (faces != null && faces.length > 0) {
                android.hardware.camera2.params.Face best = null;
                for (android.hardware.camera2.params.Face f : faces) {
                    if (f.getScore() < 30) continue;
                    if (best == null || area(f.getBounds()) > area(best.getBounds())) best = f;
                }
                if (best != null) {
                    faceRegions = new MeteringRectangle[]{new MeteringRectangle(best.getBounds(), MeteringRectangle.METERING_WEIGHT_MAX - 1)};
                    faceSeenAt = SystemClock.elapsedRealtime();
                }
            }
            long now = SystemClock.elapsedRealtime();
            if (now - lastLivePost > 300) {
                lastLivePost = now;
                int i = lastIso;
                long e = lastExposure;
                String nr = nrName(result.get(CaptureResult.NOISE_REDUCTION_MODE));
                main.post(() -> listener.onLive(i, e, nr));
            }
            stepPrecapture(result.get(CaptureResult.CONTROL_AE_STATE));
        }
    };

    /** Lente física em uso agora (a câmera lógica troca sozinha conforme zoom, luz e distância). */
    private volatile String lastPhys;
    private String lastHint;
    private long focusWarnUntil;

    /**
     * No zoom da tele, se o sistema estiver usando OUTRA lente, a foto seria um recorte ampliado
     * (degradado) da principal — quase sempre porque o objeto está mais perto que o foco mínimo
     * da tele. Avisa na hora, antes de fotografar.
     */
    private boolean teleFallback() {
        return caps != null && caps.teleId != null && caps.teleRatio > 1.5f && lastPhys != null
                && zoom >= caps.teleRatio * 0.97f && !lastPhys.equals(caps.teleId);
    }

    private void updateLensHint() {
        if (SystemClock.elapsedRealtime() < focusWarnUntil) return; // aviso de foco ainda na tela
        String h = null;
        if (teleFallback()) {
            h = "Perto demais para a tele (foca a partir de " + Math.round(100f / Math.max(.1f, caps.teleMinFocus))
                    + " cm). Afaste-se — agora seria recorte ampliado da principal.";
        }
        postHint(h);
    }

    private void postHint(String h) {
        if (h == null ? lastHint == null : h.equals(lastHint)) return;
        lastHint = h;
        main.post(() -> listener.onHint(h));
    }

    /** Rosto visto há menos de 1 s (evita piscar quando a detecção falha um quadro). */
    private MeteringRectangle[] currentFaces() {
        return faceRegions != null && SystemClock.elapsedRealtime() - faceSeenAt < 1000 ? faceRegions : null;
    }

    private static long area(Rect r) {
        return (long) r.width() * r.height();
    }

    /** Foco automático ativo (não manual, não travado, não já fixado por toque). */
    private boolean afNeedsTrigger() {
        boolean manualFocus = focusDistance >= 0f && caps.minFocus > 0f;
        return !manualFocus && !userLock && regions == null
                && CameraCaps.contains(caps.afModes, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
    }

    private static boolean afDone(int st) {
        return st == CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED
                || st == CameraMetadata.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED;
    }

    /** Depois da foto, libera o foco contínuo que foi travado para a captura. */
    private void afRelease() {
        if (!afTriggered || session == null || device == null) return;
        afTriggered = false;
        try {
            CaptureRequest.Builder b = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            b.addTarget(previewSurface);
            applyCommon(b, false, false);
            b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            session.capture(b.build(), liveCallback, handler);
        } catch (Exception ignored) {
        }
    }

    private void precaptureThen(Runnable action) throws CameraAccessException {
        if (isManual()) {
            action.run();
            return;
        }
        CaptureRequest.Builder b = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        b.addTarget(previewSurface);
        applyCommon(b, false, false);
        b.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
        // foco contínuo: dispara uma varredura final e só fotografa quando confirmar (até ~1,2 s)
        afTriggered = afNeedsTrigger();
        if (afTriggered) b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
        lastAfState = -1;
        preAction = action;
        preState = PRE_WAIT_START;
        preStartedAt = SystemClock.elapsedRealtime();
        session.capture(b.build(), liveCallback, handler);
        final int gen = generation;
        handler.postDelayed(() -> {
            if (gen == generation && (preState == PRE_WAIT_START || preState == PRE_WAIT_DONE)) runPreAction();
        }, 1800);
    }

    private void stepPrecapture(Integer ae) {
        if (preState == PRE_IDLE) return;
        long elapsed = SystemClock.elapsedRealtime() - preStartedAt;
        int st = ae != null ? ae : -1;
        // espera o foco confirmar quando foi disparado (limite 1,2 s para não travar o disparo)
        boolean focusReady = !afTriggered || afDone(lastAfState) || elapsed > 1200;
        switch (preState) {
            case PRE_WAIT_START:
                if (st == CameraMetadata.CONTROL_AE_STATE_PRECAPTURE) {
                    preState = PRE_WAIT_DONE;
                } else if (focusReady && (ae == null || (elapsed > 500 && (st == CameraMetadata.CONTROL_AE_STATE_CONVERGED
                        || st == CameraMetadata.CONTROL_AE_STATE_FLASH_REQUIRED || st == CameraMetadata.CONTROL_AE_STATE_LOCKED)))) {
                    runPreAction();
                }
                break;
            case PRE_WAIT_DONE:
                if (focusReady && st != CameraMetadata.CONTROL_AE_STATE_PRECAPTURE) runPreAction();
                break;
            case PRE_WAIT_LOCK:
                if (ae == null || st == CameraMetadata.CONTROL_AE_STATE_LOCKED || elapsed > 600) runPreAction();
                break;
            default:
                break;
        }
    }

    private void runPreAction() {
        preState = PRE_IDLE;
        Runnable r = preAction;
        preAction = null;
        if (r == null) return;
        try {
            r.run();
        } catch (Exception e) {
            fail("Falha ao capturar: " + e.getMessage());
        }
    }

    // ---- Foto comum (JPEG da câmera) ----------------------------------------------------

    private void stillCapture() {
        try {
            boolean withRaw = mode == Mode.RAW && rawReader != null;
            CameraCaps.ResOption res = withRaw ? caps.resolutions.get(0) : currentRes();
            boolean maxRes = res != null && res.maximumResolution;
            CaptureRequest.Builder b = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            b.addTarget(jpegReader.getSurface());
            if (withRaw) b.addTarget(rawReader.getSurface());
            applyCommon(b, true, maxRes);
            if (withRaw && CameraCaps.contains(caps.ch.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES),
                    CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON)) {
                b.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON);
            }
            if (maxRes) b.set(CaptureRequest.SENSOR_PIXEL_MODE, CameraMetadata.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION);
            Job j = newJob(withRaw ? "camera_jpeg_mais_dng" : maxRes ? "camera_jpeg_sensor_cheio" : "camera_jpeg");
            j.needsRaw = withRaw;
            j.sheet.put("resolucao", res != null ? res.label : "?");
            j.sheet.put("tamanho", res != null ? res.size.toString() : "?");
            if (withRaw) j.sheet.put("dng_tamanho", caps.rawSize.toString());
            job = j;
            session.capture(b.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest r, TotalCaptureResult result) {
                    j.result = result;
                    tryFinishJpeg(j);
                }

                @Override
                public void onCaptureFailed(CameraCaptureSession s, CaptureRequest r, CaptureFailure failure) {
                    if (job == j) fail("A câmera recusou a captura (motivo " + failure.getReason() + ").");
                }
            }, handler);
            handler.postDelayed(() -> {
                if (job == j && !j.done) fail("A câmera não entregou a foto a tempo.");
            }, 8000);
        } catch (Exception e) {
            fail("Falha ao capturar: " + e.getMessage());
        }
    }

    private Job newJob(String pipeline) {
        Job j = new Job();
        j.name = MediaSaver.newName();
        j.gen = generation;
        j.orientation = jpegOrientation;
        j.pipeline = pipeline;
        try {
            j.sheet.put("app", "The Natural Cam " + BuildConfigInfo.VERSION);
            j.sheet.put("aparelho", Build.MANUFACTURER + " " + Build.MODEL);
            j.sheet.put("android", Build.VERSION.RELEASE);
            j.sheet.put("camera_id", caps.id);
            j.sheet.put("camera_logica", caps.logicalMultiCamera);
            j.sheet.put("modo", mode.name());
            j.sheet.put("processo", pipeline);
            j.sheet.put("zoom", zoom);
            j.sheet.put("ev", ev);
            j.sheet.put("iso_manual", isManual() ? manualIso : 0);
            j.sheet.put("exposicao_manual_ns", isManual() ? manualExposureNs : 0);
            j.sheet.put("flash", flash);
            j.sheet.put("orientacao_jpeg", jpegOrientation);
            j.sheet.put("reducao_ruido_pedida", nrPolicy == NR_OFF ? "OFF" : nrPolicy == NR_HIGH ? "HIGH_QUALITY" : "FAST");
            j.sheet.put("empilhamento", stackPolicy == STACK_OFF ? "desligado" : stackPolicy == STACK_MAX ? "maximo" : "automatico");
            j.sheet.put("iso_previa", lastIso);
            j.sheet.put("exposicao_previa_ns", lastExposure);
        } catch (Exception ignored) {
        }
        return j;
    }

    private void onJpegImage(ImageReader reader) {
        Image img = reader.acquireNextImage();
        if (img == null) return;
        try {
            Job j = job;
            if (j == null || j.done) return;
            if (img.getFormat() != ImageFormat.JPEG) return;
            ByteBuffer buf = img.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            j.jpeg = bytes;
            tryFinishJpeg(j);
        } finally {
            img.close();
        }
    }

    /** Copia o RAW_SENSOR para um buffer denso (o DngCreator exige linhas sem preenchimento). */
    private void onRawImage(ImageReader reader) {
        Image img = reader.acquireNextImage();
        if (img == null) return;
        try {
            Job j = job;
            if (j != null && !j.done && j.rawFrames != null) {
                onRawPlus(j, img);
                return;
            }
            if (j == null || j.done || !j.needsRaw) return;
            int w = img.getWidth(), h = img.getHeight();
            Image.Plane p = img.getPlanes()[0];
            ByteBuffer src = p.getBuffer();
            int row = p.getRowStride();
            ByteBuffer dst = ByteBuffer.allocateDirect(w * h * 2);
            byte[] line = new byte[w * 2];
            for (int y = 0; y < h; y++) {
                src.position(y * row);
                src.get(line, 0, w * 2);
                dst.put(line);
            }
            dst.rewind();
            j.raw = dst;
            j.rawSize = new Size(w, h);
            tryFinishJpeg(j);
        } catch (OutOfMemoryError oom) {
            Job j = job;
            if (j != null) {
                j.needsRaw = false;
                j.sheet.remove("dng_tamanho");
                tryFinishJpeg(j);
            }
            error("Memória insuficiente para o DNG; JPEG mantido.");
        } finally {
            img.close();
        }
    }

    private void tryFinishJpeg(Job j) {
        if (j.done || j.jpeg == null || (j.needsResult && j.result == null) || (j.needsRaw && j.raw == null)) return;
        j.done = true;
        job = null;
        capturing = false;
        aeLocked = false;
        if (session != null) startRepeating();
        afRelease();
        fillResult(j.sheet, j.result);
        busy("Salvando…", -1);
        final byte[] data = j.jpeg;
        j.jpeg = null;
        final CameraCharacteristics chars = caps.ch;
        worker.execute(() -> {
            try {
                quickPreviewJpeg(data);
                if (j.needsRaw && j.raw != null) {
                    try {
                        saver.saveDng(j.name, chars, j.result, j.rawSize, j.raw, j.orientation);
                        j.sheet.put("dng", "salvo");
                    } catch (Exception e) {
                        j.sheet.put("dng", "falhou: " + e.getMessage());
                        error("DNG não salvo: " + e.getMessage());
                    }
                    j.raw = null;
                }
                Uri uri = saver.saveJpeg(j.name, data, j.sheet);
                String summary = summary(j.sheet);
                main.post(() -> listener.onSaved(uri, summary));
            } catch (Exception e) {
                error("Não foi possível salvar: " + e.getMessage());
            }
        });
    }

    // ---- RAW+: ISP próprio a partir do RAW do sensor ----------------------------------

    private void rawBurst() {
        try {
            Job j = newJob("raw_isp");
            j.rawFrames = new ArrayList<>();
            // 25 MB por quadro (12 MP × 16 bits): de 3 (luz boa) a 8 (pouca luz)
            j.rawTarget = lastIso >= 1600 ? 8 : lastIso >= 800 ? 6 : lastIso >= 200 ? 4 : 3;
            j.sheet.put("quadros_pedidos", j.rawTarget);
            j.sheet.put("tamanho_raw", caps.rawSize.toString());
            job = j;
            long exp = 0;
            int iso = 0;
            if (!isManual() && caps.manualSensor && lastIso >= 400 && lastExposure > 0 && caps.isoRange != null && caps.exposureRange != null) {
                // a fusão RAW alinha o tremor: cada quadro expõe mais (até 1/30 s) com ISO menor
                long target = Math.min(Math.min(lastExposure * 4, 33_333_333L), caps.exposureRange.getUpper());
                if (target > lastExposure * 1.2) {
                    iso = Math.max(caps.isoRange.getLower(), (int) Math.round((double) lastIso * lastExposure / target));
                    exp = target;
                    j.sheet.put("raw_exposicao_ns", exp);
                    j.sheet.put("raw_iso", iso);
                }
            }
            List<CaptureRequest> list = new ArrayList<>();
            for (int i = 0; i < j.rawTarget; i++) {
                CaptureRequest.Builder b = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                b.addTarget(rawReader.getSurface());
                applyCommon(b, true, false);
                b.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON);
                if (exp > 0) {
                    b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                    b.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
                    b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exp);
                    b.set(CaptureRequest.SENSOR_FRAME_DURATION, exp);
                }
                list.add(b.build());
            }
            busy("RAW+: segure firme…", 0f);
            session.captureBurst(list, new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest r, TotalCaptureResult result) {
                    if (j.result == null) j.result = result;
                }
            }, handler);
            final int gen = generation;
            handler.postDelayed(() -> {
                if (gen != generation || job != j || j.done) return;
                if (j.rawFrames.size() >= 1 && j.result != null) finishRawPlus(j);
                else fail("A câmera não entregou os quadros RAW.");
            }, 10000);
        } catch (Exception e) {
            fail("Falha no RAW+: " + e.getMessage());
        }
    }

    private void onRawPlus(Job j, Image img) {
        if (j.rawFrames.size() >= j.rawTarget) return;
        try {
            int w = img.getWidth(), h = img.getHeight();
            Image.Plane p = img.getPlanes()[0];
            java.nio.ShortBuffer src = p.getBuffer().order(java.nio.ByteOrder.nativeOrder()).asShortBuffer();
            int rowShorts = p.getRowStride() / 2;
            short[] d = new short[w * h];
            for (int y = 0; y < h; y++) {
                src.position(y * rowShorts);
                src.get(d, y * w, w);
            }
            RawIsp.Frame f = new RawIsp.Frame(d, w, h);
            f.timestampNs = img.getTimestamp();
            j.rawFrames.add(f);
        } catch (OutOfMemoryError oom) {
            j.rawTarget = j.rawFrames.size();
        }
        int k = j.rawFrames.size();
        busy("RAW+: quadro " + k + "/" + j.rawTarget + " — segure firme", 0.3f * k / j.rawTarget);
        if (k >= j.rawTarget && j.result != null) finishRawPlus(j);
        else if (k >= j.rawTarget) handler.postDelayed(() -> { if (!j.done && j.result != null) finishRawPlus(j); }, 300);
    }

    /** Metadados da revelação a partir do resultado da captura e das características do sensor. */
    private RawIsp.Meta rawMeta(TotalCaptureResult r) throws Exception {
        RawIsp.Meta m = new RawIsp.Meta();
        CameraCharacteristics ch = caps.ch;
        Integer cfa = ch.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        m.cfa = cfa != null && cfa <= 3 ? cfa : 0;
        float[] dyn = r.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL);
        if (dyn != null && dyn.length == 4) {
            m.black = dyn.clone();
        } else {
            android.hardware.camera2.params.BlackLevelPattern bp = ch.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
            if (bp != null) m.black = new float[]{bp.getOffsetForIndex(0, 0), bp.getOffsetForIndex(1, 0), bp.getOffsetForIndex(0, 1), bp.getOffsetForIndex(1, 1)};
        }
        Integer dw = r.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL);
        Integer sw = ch.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
        m.white = dw != null && dw > 0 ? dw : sw != null ? sw : 1023;
        android.hardware.camera2.params.RggbChannelVector g = r.get(CaptureResult.COLOR_CORRECTION_GAINS);
        if (g != null) {
            float gg = (g.getGreenEven() + g.getGreenOdd()) / 2f;
            m.wb = new float[]{g.getRed() / gg, 1f, g.getBlue() / gg};
        }
        android.hardware.camera2.params.ColorSpaceTransform t = r.get(CaptureResult.COLOR_CORRECTION_TRANSFORM);
        if (t != null) {
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) m.ccm[row * 3 + col] = t.getElement(col, row).floatValue();
        }
        Boolean shadingApplied = ch.get(CameraCharacteristics.SENSOR_INFO_LENS_SHADING_APPLIED);
        android.hardware.camera2.params.LensShadingMap sm = r.get(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP);
        if (sm != null && (shadingApplied == null || !shadingApplied)) {
            int cw = sm.getColumnCount(), rh = sm.getRowCount();
            m.shadingW = cw;
            m.shadingH = rh;
            m.shading = new float[3][cw * rh];
            for (int y = 0; y < rh; y++) {
                for (int x = 0; x < cw; x++) {
                    m.shading[0][y * cw + x] = sm.getGainFactor(android.hardware.camera2.params.RggbChannelVector.RED, x, y);
                    m.shading[1][y * cw + x] = (sm.getGainFactor(android.hardware.camera2.params.RggbChannelVector.GREEN_EVEN, x, y)
                            + sm.getGainFactor(android.hardware.camera2.params.RggbChannelVector.GREEN_ODD, x, y)) / 2f;
                    m.shading[2][y * cw + x] = sm.getGainFactor(android.hardware.camera2.params.RggbChannelVector.BLUE, x, y);
                }
            }
        }
        android.util.Pair<Double, Double>[] np = r.get(CaptureResult.SENSOR_NOISE_PROFILE);
        if (np != null && np.length > 0) {
            double s = 0, o = 0;
            for (android.util.Pair<Double, Double> pr : np) {
                s += pr.first;
                o += pr.second;
            }
            m.noiseS = s / np.length;
            m.noiseO = o / np.length;
        }
        // zoom: o RAW é o sensor inteiro da lente física em uso; recorta o que falta (sem ampliar)
        String phys = r.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
        float lensRatio = 1f;
        if (phys != null && caps.mainId != null && caps.physLens.containsKey(phys) && caps.physLens.containsKey(caps.mainId)) {
            lensRatio = caps.physLens.get(phys)[0] / caps.physLens.get(caps.mainId)[0];
        }
        Float zr = r.get(CaptureResult.CONTROL_ZOOM_RATIO);
        m.crop = Math.max(1f, (zr != null ? zr : zoom) / lensRatio);
        return m;
    }

    private void finishRawPlus(Job j) {
        j.done = true;
        job = null;
        aeLocked = false;
        startRepeating();
        afRelease();
        final List<RawIsp.Frame> frames = new ArrayList<>(j.rawFrames);
        j.rawFrames.clear();
        fillResult(j.sheet, j.result);
        worker.execute(() -> {
            try {
                RawIsp.Meta m = rawMeta(j.result);
                j.sheet.put("raw_cfa", m.cfa);
                j.sheet.put("raw_preto", java.util.Arrays.toString(m.black));
                j.sheet.put("raw_branco", m.white);
                j.sheet.put("raw_balanco_branco", java.util.Arrays.toString(m.wb));
                j.sheet.put("raw_matriz_cor", java.util.Arrays.toString(m.ccm));
                j.sheet.put("raw_sombreamento", m.shading != null ? m.shadingW + "×" + m.shadingH : "não aplicado (sensor já corrige ou sem mapa)");
                j.sheet.put("raw_ruido_s_o", m.noiseS + " / " + m.noiseO);
                j.sheet.put("raw_recorte_zoom", round2(m.crop));
                RawIsp.Result res = RawIsp.process(frames, m, (label, p) -> busy("RAW+: " + label, 0.35f + 0.6f * p));
                frames.clear();
                j.sheet.put("quadros_recebidos", res.framesIn);
                j.sheet.put("quadro_referencia", res.reference);
                j.sheet.put("aproveitamento_medio", round2(res.meanWeight));
                j.sheet.put("deslocamento_max_px", res.maxShift);
                j.sheet.put("raw_ganho_exposicao", round2(res.exposureGain));
                j.sheet.put("tempo_fusao_raw_ms", res.mergeMs);
                j.sheet.put("tempo_revelacao_ms", res.developMs);
                j.sheet.put("tempo_acabamento_ms", res.finishMs);
                j.sheet.put("tempo_total_ms", res.millis);
                j.sheet.put("tamanho", res.frame.width + "x" + res.frame.height);
                quickPreview(res.frame, j.orientation);
                busy("Gravando…", 0.97f);
                saveYuv(j, res.frame);
            } catch (Throwable t) {
                frames.clear();
                Log.w(TAG, "RAW+", t);
                error("RAW+ falhou: " + t.getMessage());
                idle();
            }
        });
    }

    // ---- Noite própria (fusão) -------------------------------------------------------

    /** Quantidade adaptativa de quadros conforme a luz (ISO medido) e a política de empilhamento. */
    private int burstCount() {
        int n;
        if (mode == Mode.RUA) return 3;
        if (mode == Mode.LONGA) {
            double fps = lastExposure > 0 ? Math.min(30.0, 1e9 / lastExposure) : 15.0;
            return Math.max(6, Math.min(60, (int) Math.round(longSeconds * fps * 0.6)));
        }
        if (mode == Mode.NOITE) {
            n = lastIso >= 1600 ? 10 : lastIso >= 400 ? 8 : 6;
        } else {
            n = lastIso >= 1600 ? 8 : lastIso >= 800 ? 6 : lastIso >= 200 ? 4 : 3;
        }
        if (stackPolicy == STACK_MAX) n = Math.max(10, Math.min(12, n + 4));
        return n;
    }

    /** Foto 12 MP e Noite usam fusão de quadros; 50/200 MP, Pro e RAW usam um quadro da câmera. */
    private boolean useFusion() {
        if (caps == null || caps.nightYuvSize == null) return false;
        if (mode == Mode.NOITE) return true;
        if (mode == Mode.RETRATO || mode == Mode.RUA || mode == Mode.LONGA || mode == Mode.ASTRO) return true;
        return mode == Mode.FOTO && resIndex == 0 && stackPolicy != STACK_OFF && flash == FLASH_OFF;
    }

    private void lockAeThenBurst() {
        if (isManual()) {
            burst();
            return;
        }
        aeLocked = true;
        startRepeating();
        preAction = this::burst;
        preState = PRE_WAIT_LOCK;
        preStartedAt = SystemClock.elapsedRealtime();
    }

    private void burst() {
        try {
            burstFrames.clear();
            burstResults.clear();
            burstTarget = burstCount();
            Job j = newJob(mode == Mode.NOITE ? "fusao_noite" : mode == Mode.RETRATO ? "fusao_retrato"
                    : mode == Mode.RUA ? "rua_rapida" : mode == Mode.LONGA ? "longa_exposicao" : "fusao_foto");
            j.sheet.put("quadros_pedidos", burstTarget);
            j.superRes = (mode == Mode.FOTO || mode == Mode.RETRATO) && stackPolicy == STACK_MAX;
            if (mode == Mode.LONGA) {
                j.longExp = new LongExposure(longTrails);
                j.longExp.setProtectStops(longProtect);
                j.sheet.put("longa_protecao_luzes_ev", -longProtect);
                j.sheet.put("longa_estilo", longTrails ? "rastros de luz (média + máximo)" : "água sedosa (média, efeito filtro ND)");
                j.sheet.put("longa_duracao_s", longSeconds);
            }
            j.sheet.put("tamanho", caps.nightYuvSize.toString());
            j.sheet.put("reducao_ruido_por_quadro", caps.supportsNr(CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL) ? "MINIMAL" : "FAST");
            job = j;
            // Fusão: o alinhamento compensa o tremor entre quadros, então cada quadro pode expor mais
            // (Foto até 1/30 s, Noite até 1/15 s) com ISO proporcionalmente menor = menos ruído por quadro.
            long nightExp = 0;
            int nightIso = 0;
            if (mode == Mode.RUA && !isManual() && caps.manualSensor && lastIso > 0 && lastExposure > 0
                    && caps.isoRange != null && caps.exposureRange != null) {
                // Rua: congela movimento — obturador até 1/250 s, compensado com ISO (máx. 6400)
                long target = Math.max(caps.exposureRange.getLower(), 4_000_000L);
                if (lastExposure > target) {
                    int isoCap = Math.min(caps.isoRange.getUpper(), 6400);
                    double light = (double) lastIso * lastExposure;
                    int iso = (int) Math.round(light / target);
                    if (iso > isoCap) {
                        iso = isoCap;
                        target = Math.min(lastExposure, Math.round(light / isoCap));
                    }
                    nightIso = Math.max(caps.isoRange.getLower(), iso);
                    nightExp = target;
                    j.sheet.put("rua_exposicao_ns", nightExp);
                    j.sheet.put("rua_iso", nightIso);
                }
            } else if (mode == Mode.LONGA && longProtect > 0 && !isManual() && caps.manualSensor && lastIso > 0
                    && lastExposure > 0 && caps.exposureRange != null) {
                // Longa: cada quadro N EV mais escuro (luzes não estouram); a soma clareia de volta
                nightExp = Math.max(caps.exposureRange.getLower(), lastExposure >> longProtect);
                nightIso = lastIso;
                j.sheet.put("longa_exposicao_quadro_ns", nightExp);
            } else if (mode != Mode.LONGA && !isManual() && caps.manualSensor && lastIso >= 400 && lastExposure > 0
                    && caps.isoRange != null && caps.exposureRange != null) {
                long cap = mode == Mode.NOITE ? 66_666_666L : 33_333_333L;
                long target = Math.min(lastExposure * 4, cap);
                target = Math.min(target, caps.exposureRange.getUpper());
                if (target > lastExposure * 1.2) {
                    int iso = (int) Math.round((double) lastIso * lastExposure / target);
                    nightIso = Math.max(caps.isoRange.getLower(), iso);
                    nightExp = target;
                    j.sheet.put("fusao_exposicao_ns", nightExp);
                    j.sheet.put("fusao_iso", nightIso);
                }
            }
            List<CaptureRequest> list = new ArrayList<>();
            for (int i = 0; i < burstTarget; i++) {
                CaptureRequest.Builder b = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                b.addTarget(yuvReader.getSurface());
                applyCommon(b, true, false);
                if (nightExp > 0) {
                    b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                    b.set(CaptureRequest.SENSOR_SENSITIVITY, nightIso);
                    b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, nightExp);
                    b.set(CaptureRequest.SENSOR_FRAME_DURATION, mode == Mode.LONGA ? Math.max(nightExp, Math.min(lastExposure, 33_333_333L)) : nightExp);
                }
                // A fusão limpa o ruído; pedir pouca redução por quadro preserva o detalhe fino.
                if (caps.supportsNr(CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL)) {
                    b.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL);
                } else if (caps.supportsNr(CameraMetadata.NOISE_REDUCTION_MODE_FAST)) {
                    b.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_FAST);
                }
                if (teleFallback() && caps.supportsEdge(CameraMetadata.EDGE_MODE_OFF)) {
                    // recorte ampliado da principal: a nitidez do ISP sobre a ampliação é o que
                    // "aquarela" letras e bordas — desliga e deixa só o acabamento suave da fusão
                    b.set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_OFF);
                } else if (caps.supportsEdge(CameraMetadata.EDGE_MODE_FAST)) {
                    b.set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_FAST);
                }
                list.add(b.build());
            }
            busy(mode == Mode.NOITE ? "Noite: segure firme…" : mode == Mode.LONGA ? "Longa exposição: " + longSeconds + " s — apoie o celular…"
                    : mode == Mode.RUA ? "Rua…" : "Segure firme…", 0f);
            session.captureBurst(list, new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest r, TotalCaptureResult result) {
                    Long ts = result.get(CaptureResult.SENSOR_TIMESTAMP);
                    if (ts != null) burstResults.put(ts, result);
                    if (j.result == null) j.result = result;
                }
            }, handler);
            final int gen = generation;
            handler.postDelayed(() -> {
                if (gen != generation || job != j || j.done) return;
                if (j.longExp != null && j.longCount >= 2) finishLong(j);
                else if (burstFrames.size() >= 2) finishBurst(j);
                else fail("A câmera não entregou os quadros.");
            }, mode == Mode.LONGA ? longSeconds * 1000L + 6000 : 6000);
        } catch (Exception e) {
            fail("Falha no modo Noite: " + e.getMessage());
        }
    }

    private void onYuvImage(ImageReader reader) {
        Image img = reader.acquireNextImage();
        if (img == null) return;
        Job j = job;
        try {
            if (j == null || j.done) return;
            if (ext != null) {
                // extensão entregando YUV: converte e salva
                YuvFrame f = copy(img);
                finishExtensionYuv(j, f);
                return;
            }
            if (j.longExp != null) {
                // longa exposição: cada quadro vai direto para a soma (memória constante)
                if (j.longSeen >= burstTarget) return;
                j.longSeen++;
                final int k = j.longSeen;
                // A soma de um quadro 12 MP leva mais que o intervalo entre quadros (30 fps): sem
                // limite, as cópias de 18 MB se acumulavam na fila até faltar memória. Com mais de
                // 2 somas pendentes, o quadro é pulado (a média continua correta, só usa menos quadros).
                if (j.longPending.get() < 3 || k == burstTarget) {
                    final YuvFrame f = copy(img);
                    j.longCount++;
                    j.longPending.incrementAndGet();
                    final LongExposure acc = j.longExp;
                    final Job jj = j;
                    worker.execute(() -> {
                        try { acc.add(f); } finally { jj.longPending.decrementAndGet(); }
                    });
                    if (j.longCount == 1) quickPreview(f, j.orientation);
                }
                busy((mode == Mode.ASTRO ? "Astro" : "Longa exposição") + ": quadro " + k + "/" + burstTarget, 0.8f * k / burstTarget);
                if (k >= burstTarget) finishLong(j);
                return;
            }
            if (burstFrames.size() >= burstTarget) return;
            burstFrames.add(copy(img));
            int k = burstFrames.size();
            busy("Noite: quadro " + k + "/" + burstTarget + " — segure firme", 0.3f * k / burstTarget);
            if (k >= burstTarget) finishBurst(j);
        } catch (OutOfMemoryError oom) {
            burstFrames.clear();
            fail("Memória insuficiente para o modo Noite.");
        } finally {
            img.close();
        }
    }

    private void finishLong(Job j) {
        j.done = true;
        job = null;
        aeLocked = false;
        startRepeating();
        afRelease();
        fillResult(j.sheet, j.result);
        final LongExposure acc = j.longExp;
        // a fila do worker é sequencial: este passo roda depois de todas as somas
        worker.execute(() -> {
            try {
                busy("Montando a longa exposição…", 0.9f);
                YuvFrame f = acc.result();
                if (f == null) throw new IllegalStateException("nenhum quadro somado");
                j.sheet.put("quadros_somados", acc.count());
                j.sheet.put("quadros_recebidos_longa", j.longSeen);
                saveYuv(j, f);
            } catch (Throwable t) {
                error("Falha na longa exposição: " + t.getMessage());
                idle();
            }
        });
    }

    // ---- Astro (tripé) ------------------------------------------------------------------

    private boolean astroTrails;
    private int astroMinutes = 3;

    void setAstro(boolean trails, int minutes) {
        handler.post(() -> {
            astroTrails = trails;
            astroMinutes = minutes;
        });
    }

    /**
     * Astrofotografia com tripé: foco no infinito, exposição manual de até 4 s por quadro.
     *  - Estrelas: média de 12 quadros (~50 s) → céu limpo, estrelas pontuais, pouco ruído;
     *  - Rastro: máximo de quadros contínuos por 1–10 min → cada estrela vira um arco.
     * Sem alinhamento: com tripé o chão fica parado e o céu gira (é o efeito desejado no rastro).
     */
    private void astroBurst() {
        try {
            long exp = 4_000_000_000L;
            int iso = astroTrails ? 800 : 1600;
            if (caps.exposureRange != null) exp = Math.min(exp, caps.exposureRange.getUpper());
            if (caps.isoRange != null) iso = Math.max(caps.isoRange.getLower(), Math.min(caps.isoRange.getUpper(), iso));
            int frames = astroTrails ? (int) Math.max(4, Math.min(600, astroMinutes * 60_000_000_000L / exp)) : 12;
            burstTarget = frames;
            Job j = newJob(astroTrails ? "astro_rastro_estrelas" : "astro_estrelas");
            j.longExp = new LongExposure(astroTrails, false, astroTrails ? 40 : 255, astroTrails ? 10 : 255);
            j.sheet.put("astro_quadros", frames);
            j.sheet.put("astro_exposicao_por_quadro_s", exp / 1e9);
            j.sheet.put("astro_iso", iso);
            j.sheet.put("astro_duracao_total_s", Math.round(frames * exp / 1e9));
            j.sheet.put("tamanho", caps.nightYuvSize.toString());
            job = j;
            boolean manualFocus = caps.minFocus > 0 && CameraCaps.contains(caps.afModes, CameraMetadata.CONTROL_AF_MODE_OFF);
            List<CaptureRequest> list = new ArrayList<>();
            for (int i = 0; i < frames; i++) {
                CaptureRequest.Builder b = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                b.addTarget(yuvReader.getSurface());
                applyCommon(b, true, false);
                b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                b.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
                b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exp);
                b.set(CaptureRequest.SENSOR_FRAME_DURATION, exp);
                if (manualFocus) {
                    b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
                    b.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0f); // infinito
                }
                if (caps.supportsNr(CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL)) {
                    // a redução forte do ISP apaga estrelas fracas; a soma dos quadros limpa o ruído
                    b.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL);
                }
                list.add(b.build());
            }
            busy(astroTrails ? "Rastro de estrelas: " + astroMinutes + " min — não mexa no tripé" : "Estrelas: ~" + Math.round(frames * exp / 1e9) + " s — não mexa no tripé", 0f);
            session.captureBurst(list, new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest r, TotalCaptureResult result) {
                    if (j.result == null) j.result = result;
                }
            }, handler);
            final int gen = generation;
            handler.postDelayed(() -> {
                if (gen != generation || job != j || j.done) return;
                if (j.longCount >= 2) finishLong(j);
                else fail("A câmera não entregou os quadros do modo Astro.");
            }, frames * exp / 1_000_000L + 15_000L);
        } catch (Exception e) {
            fail("Falha no modo Astro: " + e.getMessage());
        }
    }

    void setLongTrails(boolean t) {
        handler.post(() -> longTrails = t);
    }

    void setLongSeconds(int s) {
        handler.post(() -> longSeconds = s);
    }

    private void finishBurst(Job j) {
        j.done = true;
        job = null;
        aeLocked = false;
        startRepeating();
        afRelease();
        final List<YuvFrame> frames = new ArrayList<>(burstFrames);
        burstFrames.clear();
        for (YuvFrame f : frames) {
            TotalCaptureResult r = burstResults.get(f.timestampNs);
            if (r != null) {
                Integer iso = r.get(CaptureResult.SENSOR_SENSITIVITY);
                Long exp = r.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                if (iso != null) f.iso = iso;
                if (exp != null) f.exposureNs = exp;
            }
        }
        burstResults.clear();
        fillResult(j.sheet, j.result);
        if (!frames.isEmpty()) quickPreview(frames.get(0), j.orientation);
        worker.execute(() -> {
            try {
                boolean superRes = j.superRes;
                BurstFusion.Result fr = new BurstFusion(0.30f, true, superRes ? 1.5f : 1f).fuse(frames, (label, p) -> busy(label, 0.3f + 0.6f * p));
                if (fr.scale > 1f) {
                    j.sheet.put("super_resolucao", "1,5× · " + fr.frame.width + "×" + fr.frame.height + " (resolução computacional, não leitura nativa do sensor)");
                    j.sheet.put("tempo_super_resolucao_ms", fr.superResMs);
                }
                // Verificador de qualidade: a fusão só vira a foto final se mantiver o detalhe
                // e a forma das bordas do quadro mais nítido; senão salva esse quadro limpo.
                busy("Verificando qualidade…", 0.92f);
                YuvFrame refFrame = frames.get(Math.max(0, Math.min(frames.size() - 1, fr.reference)));
                QualityCheck.Report q = QualityCheck.compare(refFrame, fr.frame, fr.noiseBefore);
                org.json.JSONObject qj = new org.json.JSONObject();
                qj.put("detalhe_vs_referencia", round2(q.detailRatio));
                qj.put("forma_das_bordas", round2(q.edgeCorrelation));
                qj.put("pixels_de_borda", q.edgePixels);
                qj.put("aprovada", q.passed);
                qj.put("motivo", q.reason);
                YuvFrame finalFrame = fr.frame;
                if (!q.passed && fr.framesIn > 1) {
                    finalFrame = QualityCheck.copy(refFrame);
                    float[] model = BurstFusion.noiseModel(finalFrame);
                    BurstFusion.lumaDenoise(finalFrame, model, 0.3f);
                    BurstFusion.chromaFilter(finalFrame, model[8]);
                    qj.put("decisao", "quadro_unico_limpo (fusão reprovada)");
                    final String why = q.reason;
                    main.post(() -> listener.onNotice("Qualidade: " + why + " — salvo o quadro mais nítido."));
                } else {
                    qj.put("decisao", "fusao");
                }
                j.sheet.put("verificador_qualidade", qj);
                frames.clear();
                j.sheet.put("quadros_recebidos", fr.framesIn);
                j.sheet.put("quadro_referencia", fr.reference);
                j.sheet.put("ruido_estimado_antes", round2(fr.noiseBefore));
                j.sheet.put("ruido_estimado_depois", round2(fr.noiseAfter));
                j.sheet.put("aproveitamento_medio", round2(fr.meanWeight));
                j.sheet.put("deslocamento_max_px", fr.maxShiftPx);
                j.sheet.put("limpeza_brilho", round2(fr.lumaDenoise));
                j.sheet.put("tempo_alinhamento_ms", fr.alignMs);
                j.sheet.put("tempo_mistura_ms", fr.mergeMs);
                j.sheet.put("tempo_acabamento_ms", fr.finishMs);
                j.sheet.put("tempo_fusao_ms", fr.millis);
                j.sheet.put("nucleos", Runtime.getRuntime().availableProcessors());
                busy("Gravando…", 0.95f);
                saveYuv(j, finalFrame);
            } catch (Throwable t) {
                // Diretriz: se a fusão falhar, conservar uma captura original utilizável.
                YuvFrame keep = frames.isEmpty() ? null : frames.get(0);
                frames.clear();
                if (keep == null) {
                    error("Falha ao combinar quadros: " + t.getMessage());
                    idle();
                    return;
                }
                try {
                    j.sheet.put("fusao", "falhou: " + t.getClass().getSimpleName() + " — salvo quadro original");
                    error("Fusão falhou; salvando o quadro original.");
                    saveYuv(j, keep);
                } catch (Throwable t2) {
                    error("Não foi possível salvar: " + t2.getMessage());
                    idle();
                }
            }
        });
    }

    /** Executa na thread de trabalho. */
    /**
     * EXIF a partir dos metadados reais da captura: abertura, focal, equivalente 35 mm (com o
     * zoom aplicado), balanço de branco, compensação de EV, flash e lente. Antes as fotos do app
     * só tinham fabricante, modelo, ISO e tempo.
     */
    private java.util.Map<String, String> exifTags(TotalCaptureResult r) {
        java.util.Map<String, String> t = new java.util.HashMap<>();
        t.put("LensMake", android.os.Build.MANUFACTURER);
        t.put(android.media.ExifInterface.TAG_EXPOSURE_PROGRAM, isManual() ? "1" : "2");
        t.put(android.media.ExifInterface.TAG_METERING_MODE, "5");
        if (r == null) return t;
        Float ap = r.get(CaptureResult.LENS_APERTURE);
        if (ap != null && ap > 0) t.put(android.media.ExifInterface.TAG_F_NUMBER, String.valueOf(ap));
        Float fl = r.get(CaptureResult.LENS_FOCAL_LENGTH);
        if (fl != null && fl > 0) {
            t.put(android.media.ExifInterface.TAG_FOCAL_LENGTH, Math.round(fl * 1000) + "/1000");
            Float zr = r.get(CaptureResult.CONTROL_ZOOM_RATIO);
            float eq = equivalent35(zr == null ? 1f : zr);
            if (eq > 0) t.put(android.media.ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, String.valueOf(Math.round(eq)));
            String lens = r.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
            t.put("LensModel", String.format(java.util.Locale.ROOT,
                    "%s %.2fmm f/%.1f%s", android.os.Build.MODEL, fl, ap == null ? 0f : ap, lens != null ? " (lente " + lens + ")" : ""));
            if (zr != null) t.put(android.media.ExifInterface.TAG_DIGITAL_ZOOM_RATIO, Math.round(zr * 100) + "/100");
        }
        Integer awb = r.get(CaptureResult.CONTROL_AWB_MODE);
        t.put(android.media.ExifInterface.TAG_WHITE_BALANCE,
                awb != null && awb == CameraMetadata.CONTROL_AWB_MODE_AUTO ? "0" : "1");
        Integer evc = r.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION);
        if (evc != null && caps.evStep != null) {
            double stops = evc * caps.evStep.doubleValue();
            t.put(android.media.ExifInterface.TAG_EXPOSURE_BIAS_VALUE, Math.round(stops * 100) + "/100");
        }
        Integer fs = r.get(CaptureResult.FLASH_STATE);
        t.put(android.media.ExifInterface.TAG_FLASH, fs != null && fs == CameraMetadata.FLASH_STATE_FIRED ? "1" : "0");
        return t;
    }

    /** Focal equivalente 35 mm: lente principal da câmera lógica × zoom pedido. */
    private float equivalent35(float zoomRatio) {
        try {
            float[] fls = caps.ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            android.util.SizeF ps = caps.ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            if (fls == null || fls.length == 0 || ps == null) return 0;
            double diag = Math.hypot(ps.getWidth(), ps.getHeight());
            return (float) (fls[0] * 43.27 / diag * zoomRatio);
        } catch (Exception e) {
            return 0;
        }
    }

    private void saveYuv(Job j, YuvFrame f) throws Exception {
        // JPEG direto do YUV (NV21): sem bitmap ARGB gigante — cabe até a saída de 28 MP
        Uri uri = saver.saveYuv(j.name, f, j.orientation, j.sheet, exifTags(j.result));
        String summary = summary(j.sheet);
        main.post(() -> listener.onSaved(uri, summary));
        idle();
    }

    /** Miniatura imediata a partir do primeiro quadro (antes da fusão terminar). */
    private void quickPreview(YuvFrame f, int rotation) {
        try {
            int step = Math.max(1, f.width / 640);
            YuvFrame s = new YuvFrame(f.width / step, f.height / step);
            for (int y = 0; y < s.height; y++) {
                for (int x = 0; x < s.width; x++) s.y[y * s.width + x] = f.y[(y * step) * f.width + x * step];
            }
            for (int y = 0; y < s.chromaHeight; y++) {
                int sy = Math.min(f.chromaHeight - 1, y * step);
                for (int x = 0; x < s.chromaWidth; x++) {
                    int sx = Math.min(f.chromaWidth - 1, x * step);
                    s.u[y * s.chromaWidth + x] = f.u[sy * f.chromaWidth + sx];
                    s.v[y * s.chromaWidth + x] = f.v[sy * f.chromaWidth + sx];
                }
            }
            Bitmap b = Bitmap.createBitmap(BurstFusion.toArgb(s), s.width, s.height, Bitmap.Config.ARGB_8888);
            postPreview(rotate(b, rotation));
        } catch (Throwable ignored) {
        }
    }

    private void quickPreviewJpeg(byte[] jpeg) {
        try {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inSampleSize = 8;
            Bitmap b = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, o);
            if (b == null) return;
            int deg = 0;
            android.media.ExifInterface exif = new android.media.ExifInterface(new java.io.ByteArrayInputStream(jpeg));
            switch (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                case android.media.ExifInterface.ORIENTATION_ROTATE_90: deg = 90; break;
                case android.media.ExifInterface.ORIENTATION_ROTATE_180: deg = 180; break;
                case android.media.ExifInterface.ORIENTATION_ROTATE_270: deg = 270; break;
                default: break;
            }
            postPreview(rotate(b, deg));
        } catch (Throwable ignored) {
        }
    }

    private static Bitmap rotate(Bitmap b, int deg) {
        if (deg % 360 == 0) return b;
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.postRotate(deg);
        return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
    }

    private void postPreview(Bitmap b) {
        main.post(() -> listener.onQuickPreview(b));
    }

    private void idle() {
        handler.post(() -> capturing = false);
    }

    // ---- Extensões do sistema (Noite / HDR do fabricante) ----------------------------

    private void captureExtension() throws CameraAccessException {
        Job j = newJob(extType == CameraExtensionCharacteristics.EXTENSION_NIGHT ? "extensao_noite_sistema" : "extensao_hdr_sistema");
        j.needsResult = false;
        job = j;
        CaptureRequest.Builder b = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        b.addTarget(extFormat == ImageFormat.JPEG ? jpegReader.getSurface() : yuvReader.getSurface());
        b.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation);
        b.set(CaptureRequest.JPEG_QUALITY, (byte) 96);
        busy(extType == CameraExtensionCharacteristics.EXTENSION_NIGHT ? "Noite do sistema: segure firme…" : "HDR: segure firme…", -1);
        ext.capture(b.build(), handlerExecutor, new CameraExtensionSession.ExtensionCaptureCallback() {
            @Override
            public void onCaptureProcessStarted(CameraExtensionSession s, CaptureRequest r) {
                busy("Processando no sistema…", -1);
            }

            @Override
            public void onCaptureFailed(CameraExtensionSession s, CaptureRequest r) {
                if (job == j) fail("O modo do sistema falhou nesta foto.");
            }
        });
        handler.postDelayed(() -> {
            if (job == j && !j.done) fail("O modo do sistema não entregou a foto a tempo.");
        }, 15000);
    }

    private void finishExtensionYuv(Job j, YuvFrame f) {
        j.done = true;
        job = null;
        worker.execute(() -> {
            try {
                busy("Gravando…", -1);
                saveYuv(j, f);
            } catch (Exception e) {
                error("Não foi possível salvar: " + e.getMessage());
                idle();
            }
        });
    }

    // =====================================================================================
    // Utilidades
    // =====================================================================================

    /** Copia um Image YUV_420_888 (qualquer stride) para YuvFrame planar. */
    static YuvFrame copy(Image img) {
        int w = img.getWidth(), h = img.getHeight();
        YuvFrame f = new YuvFrame(w, h);
        f.timestampNs = img.getTimestamp();
        Image.Plane[] p = img.getPlanes();
        ByteBuffer yb = p[0].getBuffer();
        int yRow = p[0].getRowStride(), yPix = p[0].getPixelStride();
        if (yPix == 1) {
            for (int y = 0; y < h; y++) {
                yb.position(y * yRow);
                yb.get(f.y, y * w, w);
            }
        } else {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) f.y[y * w + x] = yb.get(y * yRow + x * yPix);
            }
        }
        copyChroma(p[1], f.u, f.chromaWidth, f.chromaHeight);
        copyChroma(p[2], f.v, f.chromaWidth, f.chromaHeight);
        return f;
    }

    private static void copyChroma(Image.Plane plane, byte[] out, int cw, int ch) {
        ByteBuffer b = plane.getBuffer();
        int row = plane.getRowStride(), pix = plane.getPixelStride();
        int limit = b.limit();
        byte[] line = new byte[row];
        for (int y = 0; y < ch; y++) {
            int start = y * row;
            int len = Math.min(row, limit - start);
            if (len <= 0) break;
            b.position(start);
            b.get(line, 0, len);
            int o = y * cw;
            for (int x = 0; x < cw; x++) {
                int idx = x * pix;
                out[o + x] = idx < len ? line[idx] : out[Math.max(0, o + x - 1)];
            }
        }
    }

    private void fillResult(JSONObject sheet, TotalCaptureResult r) {
        if (r == null) return;
        try {
            Integer iso = r.get(CaptureResult.SENSOR_SENSITIVITY);
            Long exp = r.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            if (iso != null) sheet.put("iso", iso);
            if (exp != null) sheet.put("exposicao_ns", exp);
            if (exp != null) sheet.put("exposicao", shutterLabel(exp));
            sheet.put("reducao_ruido_observada", nrName(r.get(CaptureResult.NOISE_REDUCTION_MODE)));
            Integer edge = r.get(CaptureResult.EDGE_MODE);
            sheet.put("nitidez_observada", edge == null ? "?" : (edge == 2 ? "HIGH_QUALITY" : edge == 1 ? "FAST" : edge == 0 ? "OFF" : String.valueOf(edge)));
            Integer pm = r.get(CaptureResult.SENSOR_PIXEL_MODE);
            sheet.put("modo_pixel", pm == null ? "?" : (pm == 1 ? "MAXIMUM_RESOLUTION" : "DEFAULT"));
            Float zr = r.get(CaptureResult.CONTROL_ZOOM_RATIO);
            if (zr != null) sheet.put("zoom_observado", zr);
            String phys = r.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
            if (phys != null) sheet.put("lente_fisica_ativa", phys);
            if (phys != null && caps.teleId != null && caps.teleRatio > 1.5f && zoom >= caps.teleRatio * 0.97f && !phys.equals(caps.teleId)) {
                sheet.put("aviso_lente", "zoom da tele feito como recorte ampliado da lente " + phys
                        + " (tele foca a partir de " + Math.round(100f / Math.max(.1f, caps.teleMinFocus)) + " cm)");
            }
            Integer afCap = r.get(CaptureResult.CONTROL_AF_STATE);
            if (afCap != null && afCap == CameraMetadata.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                sheet.put("aviso_foco", "foco não confirmado na captura");
            }
            Float fdist = r.get(CaptureResult.LENS_FOCUS_DISTANCE);
            if (fdist != null) sheet.put("foco_dioptrias", fdist);
            if (fdist != null && fdist > 0) sheet.put("foco_distancia_cm", Math.round(100f / fdist));
            Integer afs = r.get(CaptureResult.CONTROL_AF_STATE);
            if (afs != null) sheet.put("estado_af", afs);
            Integer ae = r.get(CaptureResult.CONTROL_AE_STATE);
            if (ae != null) sheet.put("estado_ae", ae);
        } catch (Exception ignored) {
        }
    }

    private static String summary(JSONObject s) {
        StringBuilder b = new StringBuilder();
        int iso = s.optInt("iso", 0);
        long exp = s.optLong("exposicao_ns", 0);
        if (iso > 0) b.append("ISO ").append(iso);
        if (exp > 0) b.append(b.length() > 0 ? " · " : "").append(shutterLabel(exp));
        if (s.has("quadros_recebidos")) b.append(" · ").append(s.optInt("quadros_recebidos")).append(" quadros");
        if (s.has("reducao_ruido_observada")) b.append(" · RR ").append(s.optString("reducao_ruido_observada"));
        return b.toString();
    }

    static String shutterLabel(long ns) {
        if (ns <= 0) return "?";
        double s = ns / 1e9;
        if (s >= 0.5) return String.format(java.util.Locale.ROOT, "%.1f s", s);
        return "1/" + Math.round(1.0 / s) + " s";
    }

    static String nrName(Integer m) {
        if (m == null) return "?";
        switch (m) {
            case 0: return "OFF";
            case 1: return "FAST";
            case 2: return "HIGH_QUALITY";
            case 3: return "MINIMAL";
            case 4: return "ZSL";
            default: return String.valueOf(m);
        }
    }

    private void fail(String text) {
        job = null;
        capturing = false;
        aeLocked = false;
        preState = PRE_IDLE;
        burstFrames.clear();
        startRepeating();
        afRelease();
        error(text);
    }

    private void busy(String text, float progress) {
        main.post(() -> listener.onBusy(text, progress));
    }

    private void error(String text) {
        Log.w(TAG, text);
        main.post(() -> listener.onError(text));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static int clampI(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    @SuppressWarnings("unused")
    private static <T> List<T> none() {
        return Collections.emptyList();
    }
}
