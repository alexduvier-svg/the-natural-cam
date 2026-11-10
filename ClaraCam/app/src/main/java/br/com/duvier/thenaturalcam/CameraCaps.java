package br.com.duvier.thenaturalcam;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraExtensionCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Range;
import android.util.Rational;
import android.util.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** O que a câmera traseira escolhida realmente oferece. */
final class CameraCaps {

    static final class ResOption {
        final String label;
        final Size size;
        final boolean maximumResolution;

        ResOption(String label, Size size, boolean maximumResolution) {
            this.label = label;
            this.size = size;
            this.maximumResolution = maximumResolution;
        }
    }

    final String id;
    final CameraCharacteristics ch;
    final int sensorOrientation;
    final boolean logicalMultiCamera;
    final List<ResOption> resolutions = new ArrayList<>();
    final Size previewSize;
    final Size nightYuvSize;
    final Range<Float> zoomRange;
    final Range<Integer> isoRange;
    final Range<Long> exposureRange;
    final Range<Integer> evRange;
    final Rational evStep;
    final boolean manualSensor;
    final boolean flash;
    final int[] nrModes;
    final int[] edgeModes;
    final int[] afModes;
    final int[] aberrationModes;
    final int[] hotPixelModes;
    final int maxAfRegions;
    final int maxAeRegions;
    final boolean extNight;
    final boolean extHdr;
    final boolean raw;
    final Size rawSize;
    final float minFocus;
    /** Lentes físicas: id → {focal mm, foco mínimo em dioptrias}. */
    final java.util.Map<String, float[]> physLens = new java.util.HashMap<>();
    /** Tele (maior focal) e principal; null se a câmera não for multi-lente. */
    final String teleId, mainId;
    /** Foco mínimo da tele em dioptrias (2,5 = 40 cm) e zoom em que ela assume (focal tele/principal). */
    final float teleMinFocus, teleRatio;

    /** Distância mínima de foco (cm) da lente física, ou 0 se desconhecida. */
    int minFocusCm(String physId) {
        float[] v = physId != null ? physLens.get(physId) : null;
        float d = v != null ? v[1] : minFocus;
        return d > 0 ? Math.round(100f / d) : 0;
    }

    final boolean awbLock;
    final boolean aeLock;
    final int faceDetectMode;
    /** Distância de foco calibrada (APPROXIMATE/CALIBRATED); o Reno16 declara UNCALIBRATED. */
    final boolean focusCalibrated;

    private CameraCaps(CameraManager manager, String id, CameraCharacteristics ch) {
        this.id = id;
        this.ch = ch;
        Integer so = ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
        sensorOrientation = so != null ? so : 90;
        int[] caps = orEmpty(ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES));
        logicalMultiCamera = contains(caps, CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA);
        manualSensor = contains(caps, CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR);
        boolean ultraHigh = contains(caps, CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR);

        StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        List<Size> jpeg = sorted(map != null ? map.getOutputSizes(ImageFormat.JPEG) : null);
        List<Size> yuv = sorted(map != null ? map.getOutputSizes(ImageFormat.YUV_420_888) : null);
        List<Size> tex = sorted(map != null ? map.getOutputSizes(SurfaceTexture.class) : null);

        // Resoluções: padrão com pixels agrupados (~12 MP) primeiro — é a que tem menos ruído.
        Size binned = largest43(jpeg, 14_000_000L);
        if (binned == null && !jpeg.isEmpty()) binned = jpeg.get(0);
        if (binned != null) resolutions.add(new ResOption(mp(binned), binned, false));
        Size full = largest43(jpeg, Long.MAX_VALUE);
        if (full != null && binned != null && area(full) > area(binned) * 1.5) {
            resolutions.add(new ResOption(mp(full), full, false));
        }
        if (ultraHigh) {
            StreamConfigurationMap maxMap = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION);
            List<Size> maxJpeg = sorted(maxMap != null ? maxMap.getOutputSizes(ImageFormat.JPEG) : null);
            Size max = largest43(maxJpeg, Long.MAX_VALUE);
            if (max == null && !maxJpeg.isEmpty()) max = maxJpeg.get(0);
            long biggest = 0;
            for (ResOption r : resolutions) biggest = Math.max(biggest, area(r.size));
            if (max != null && area(max) > biggest * 1.5) {
                resolutions.add(new ResOption(mp(max), max, true));
            }
        }

        Size pv = largest43(tex, 1920L * 1440L);
        previewSize = pv != null ? pv : (tex.isEmpty() ? new Size(1440, 1080) : tex.get(tex.size() - 1));
        List<Size> rawSizes = sorted(map != null ? map.getOutputSizes(ImageFormat.RAW_SENSOR) : null);
        raw = contains(caps, CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW) && !rawSizes.isEmpty();
        Size rs = null;
        if (raw) {
            long limit = binned != null ? Math.round(area(binned) * 1.3) : Long.MAX_VALUE;
            for (Size s : rawSizes) {
                if (area(s) <= limit) { rs = s; break; }
            }
            if (rs == null) rs = rawSizes.get(rawSizes.size() - 1);
        }
        rawSize = rs;
        Float mf = ch.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        minFocus = mf != null ? mf : 0f;
        // Lentes físicas da câmera lógica: focal e distância mínima de foco de cada uma. A tele
        // (maior focal) do Reno16 Pro só foca a partir de ~40 cm; mais perto que isso o sistema
        // troca em silêncio para um RECORTE ampliado da principal — a foto se degrada.
        String tId = null, mId = null;
        float tFocal = 0, tMin = 0;
        if (logicalMultiCamera) {
            for (String pid : ch.getPhysicalCameraIds()) {
                try {
                    CameraCharacteristics pc = manager.getCameraCharacteristics(pid);
                    float[] fl = pc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    Float pm = pc.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
                    float focal = fl != null && fl.length > 0 ? fl[0] : 0;
                    float minD = pm != null ? pm : 0;
                    physLens.put(pid, new float[]{focal, minD});
                    if (focal > tFocal) {
                        tFocal = focal;
                        tId = pid;
                        tMin = minD;
                    }
                } catch (Exception ignored) {
                }
            }
            // principal = a de focal intermediária (nem a tele, nem a ultra-angular)
            float best = Float.MAX_VALUE;
            float[] sorted = new float[physLens.size()];
            int k = 0;
            for (float[] v : physLens.values()) sorted[k++] = v[0];
            java.util.Arrays.sort(sorted);
            float median = sorted.length == 0 ? 0 : sorted[sorted.length / 2];
            for (java.util.Map.Entry<String, float[]> e : physLens.entrySet()) {
                float d = Math.abs(e.getValue()[0] - median);
                if (d < best) {
                    best = d;
                    mId = e.getKey();
                }
            }
        }
        teleId = physLens.size() >= 2 ? tId : null;
        mainId = mId;
        teleMinFocus = tMin;
        float mainFocal = mId != null ? physLens.get(mId)[0] : 0;
        // zoom em que a tele assume (3,5× no Reno16 Pro)
        teleRatio = teleId != null && mainFocal > 0 ? tFocal / mainFocal : 0;
        Boolean awl = ch.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE);
        awbLock = awl != null && awl;
        Boolean ael = ch.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE);
        aeLock = ael == null || ael;
        int[] fm = orEmpty(ch.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES));
        Integer fcal = ch.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION);
        focusCalibrated = fcal != null && fcal != CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED;
        faceDetectMode = contains(fm, CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE) ? CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE
                : contains(fm, CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL) ? CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL
                : CameraMetadata.STATISTICS_FACE_DETECT_MODE_OFF;
        Size ny = largest43(yuv, 14_000_000L);
        nightYuvSize = ny != null ? ny : (yuv.isEmpty() ? null : yuv.get(0));

        Range<Float> zr = ch.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
        Float maxDigital = ch.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        zoomRange = zr != null ? zr : new Range<>(1f, maxDigital != null ? maxDigital : 1f);
        isoRange = ch.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        exposureRange = ch.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        Range<Integer> ev = ch.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        evRange = ev != null ? ev : new Range<>(0, 0);
        Rational step = ch.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
        evStep = step != null ? step : new Rational(1, 3);
        Boolean fl = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        flash = fl != null && fl;
        nrModes = orEmpty(ch.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES));
        edgeModes = orEmpty(ch.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES));
        afModes = orEmpty(ch.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES));
        aberrationModes = orEmpty(ch.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES));
        hotPixelModes = orEmpty(ch.get(CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES));
        Integer af = ch.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
        Integer ae = ch.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
        maxAfRegions = af != null ? af : 0;
        maxAeRegions = ae != null ? ae : 0;

        boolean night = false, hdr = false;
        try {
            CameraExtensionCharacteristics ext = manager.getCameraExtensionCharacteristics(id);
            List<Integer> list = ext.getSupportedExtensions();
            night = list.contains(CameraExtensionCharacteristics.EXTENSION_NIGHT)
                    && hasExtensionOutput(ext, CameraExtensionCharacteristics.EXTENSION_NIGHT);
            hdr = list.contains(CameraExtensionCharacteristics.EXTENSION_HDR)
                    && hasExtensionOutput(ext, CameraExtensionCharacteristics.EXTENSION_HDR);
        } catch (Exception ignored) {
            // aparelho sem suporte a extensões
        }
        extNight = night;
        extHdr = hdr;
    }

    private static boolean hasExtensionOutput(CameraExtensionCharacteristics ext, int extension) {
        return !ext.getExtensionSupportedSizes(extension, ImageFormat.JPEG).isEmpty()
                || !ext.getExtensionSupportedSizes(extension, ImageFormat.YUV_420_888).isEmpty();
    }

    /** Escolhe a câmera traseira principal, preferindo a câmera "lógica" (que recebe o melhor processamento). */
    static CameraCaps pick(CameraManager manager) throws CameraAccessException {
        CameraCaps best = null;
        long bestScore = Long.MIN_VALUE;
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics c = manager.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (facing == null || facing != CameraMetadata.LENS_FACING_BACK) continue;
            CameraCaps caps = new CameraCaps(manager, id, c);
            long score = 0;
            if (caps.logicalMultiCamera) score += 1_000;
            if (caps.zoomRange.getLower() < 0.99f) score += 500;
            if ("0".equals(id)) score += 100;
            if (!caps.resolutions.isEmpty()) score += area(caps.resolutions.get(0).size) / 1_000_000L;
            if (score > bestScore) {
                bestScore = score;
                best = caps;
            }
        }
        if (best == null) throw new CameraAccessException(CameraAccessException.CAMERA_ERROR, "Nenhuma câmera traseira encontrada.");
        return best;
    }

    List<Size> extensionSizes(CameraManager manager, int extension, int format) {
        try {
            return sorted(manager.getCameraExtensionCharacteristics(id)
                    .getExtensionSupportedSizes(extension, format).toArray(new Size[0]));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    List<Size> extensionPreviewSizes(CameraManager manager, int extension) {
        try {
            return sorted(manager.getCameraExtensionCharacteristics(id)
                    .getExtensionSupportedSizes(extension, SurfaceTexture.class).toArray(new Size[0]));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    boolean supportsNr(int mode) {
        return contains(nrModes, mode);
    }

    boolean supportsEdge(int mode) {
        return contains(edgeModes, mode);
    }

    // ---------------------------------------------------------------------------------

    static Size largest43(List<Size> sizes, long maxArea) {
        for (Size s : sizes) {
            if (area(s) <= maxArea && is43(s)) return s;
        }
        return null;
    }

    static boolean is43(Size s) {
        double r = (double) Math.max(s.getWidth(), s.getHeight()) / Math.min(s.getWidth(), s.getHeight());
        return Math.abs(r - 4.0 / 3.0) < 0.02;
    }

    static long area(Size s) {
        return (long) s.getWidth() * s.getHeight();
    }

    static String mp(Size s) {
        double m = area(s) / 1_000_000.0;
        return m < 9.5 ? String.format(Locale.ROOT, "%.1f MP", m) : String.format(Locale.ROOT, "%d MP", Math.round(m));
    }

    static List<Size> sorted(Size[] sizes) {
        List<Size> list = sizes == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(sizes));
        list.sort((a, b) -> Long.compare(area(b), area(a)));
        return list;
    }

    static boolean contains(int[] arr, int v) {
        if (arr == null) return false;
        for (int a : arr) if (a == v) return true;
        return false;
    }

    private static int[] orEmpty(int[] a) {
        return a != null ? a : new int[0];
    }
}
