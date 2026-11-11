# Capacidades reais das câmeras — Find X8 Ultra, Reno16 Pro e Reno14

Coleta em 09/11/2026 por `adb` sem fio e sem root, com `adb shell dumpsys media.camera` e `getprop`. Só entram aqui os metadados que o Android expõe a qualquer app (as `CameraCharacteristics` e os nomes de vendor tags publicados pelo HAL). Nenhum binário de firmware foi extraído ou descompilado.

Arquivos brutos derivados, na pasta [`dados_adb/`](dados_adb/):
- `resumo_caracteristicas_3_aparelhos.txt`: resumo por câmera, gerado por script a partir do dump.
- `vendor_tag_nomes_<modelo>.txt`: lista de **nomes** de vendor tags que o HAL publica em `com.oplus.vendor.tag.names`. Só os nomes, sem valores de calibração.

Os dumps completos têm EEPROM e calibração de cada módulo. Por isso não foram copiados para o projeto e ficaram só no scratchpad da sessão.

---

## 1. Aparelhos

| | Find X8 Ultra | Reno16 Pro | Reno14 |
|---|---|---|---|
| Modelo (adb) | PKJ110 (China, CN01) | CPH2863 (EX01) | CPH2737 (EX01) |
| SoC | Snapdragon 8 Elite (`SM8750`, plataforma `sun`) | Dimensity `MT6899` | Dimensity `MT6897` |
| Android / SDK | 17 / 37 (`PKJ110_17.0.0.104`) | 17 / 37 (`CPH2863_17.0.0.100`) | 16 / 36 (`CPH2737_16.0.10.600`) |
| HAL de câmera | Qualcomm CHI (`vendor_qti`), v1.2 | MediaTek (`internal`), v1.3 | MediaTek (`internal`), v1.2 |
| Câmeras no serviço | 7 (5 “normais”) | 5 | 5 |

## 2. IDs lógicos e físicos, focais e sensores

As focais equivalentes foram calculadas a partir de `lens.info.availableFocalLengths` e `sensor.info.physicalSize`, com fator de corte = 43,27 / diagonal. Os sensores identificados vêm de reviews públicos e estão citados abaixo; o Android não expõe o nome do sensor.

### Find X8 Ultra (PKJ110)
| ID | Tipo | Focal real → equiv. | f/ | Área ativa exposta | Sensor (fonte pública) |
|---|---|---|---|---|---|
| **0** | **Lógica** (`LOGICAL_MULTI_CAMERA`), físicas **3, 2, 4, 5**; zoom 0,6–20× | — | — | 4096×3072 | — |
| 2 | Principal | 8,67 mm → **~23 mm** | 1,8 | 4096×3072 (13,1×9,8 mm) | Sony LYT-900, 1" |
| 3 | Ultra-angular (só física, não pública via API1) | 2,30 mm → ~15 mm | 2,05 | 4096×3072 | Samsung JN5 |
| 4 | Tele 3× | 16,53 mm → ~70 mm | 2,1 | 4096×3072 | Sony LYT-700 |
| 5 | Periscópio 6× | 24,85 mm → ~131 mm | 3,1 | 4096×3072 | Sony LYT-600 |
| 1 | Frontal | 3,23 mm → ~21 mm | 2,4 | 3280×2464 | — |
| 6 | `SYSTEM_CAMERA` + `DEPTH_OUTPUT`, 1600×1200 | 1,93 mm | 2,4 | — | provável sensor auxiliar (o “True Chroma” é citado por reviews); **inacessível a apps de terceiros** |

Mapeamento da API1: 0→"0", 1→"1", 2→"2", 3→"4", 4→"5". A ultra-angular (3) só é alcançada como física da lógica 0.

### Reno16 Pro (CPH2863)
| ID | Tipo | Focal → equiv. | f/ | Sensor (fonte pública) |
|---|---|---|---|---|
| **0** | **Lógica**, físicas **2, 3, 4**; zoom 0,6–20× | — | — | — |
| 2 | Principal | 5,56 mm → ~23 mm | 1,8 | Samsung **HP5** 200 MP, 1/1,56" (saída binada 4×4 em 4096×3072; pixel binado de 2,016 µm segundo `com.oplus.custom.sensor.pixel.size`) |
| 3 | Ultra-angular | 2,31 mm → ~16 mm | 2,0 | provável GalaxyCore **GC50F6** (mesmo conjunto do Reno15 Pro) |
| 4 | Tele periscópio 3,5× | 12,19 mm → ~80 mm | 2,8 | Samsung **JN5** |
| 1 | Frontal (nível FULL) | 2,75 mm → ~18 mm | 2,0 | JN5 |

A ficha oficial do CPH2863 confirma 200 MP na principal e 50 MP na ultra-angular, na tele e na frontal. O HP5 oferece modos de 200 MP, 50 MP e 12,5 MP no sensor. No diagnóstico deste firmware, o modo padrão da Camera2 entregou até 4096 × 3072. A saída computacional de 6144 × 4608 representa uma grade 1,5× maior por lado, com ganho de detalhe condicionado aos deslocamentos e à nitidez dos quadros. [OPPO CPH2863](https://www.oppo.com/en/smartphones/series-reno/reno16-pro/specs/) · [Samsung ISOCELL HP5](https://semiconductor.samsung.com/image-sensor/mobile-image-sensor/isocell-hp5/) · [Android Camera2](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)

O TCS3720 mede RGB e luz no canal claro. O fabricante descreve uma calibração do conjunto sensor, vidro e tela para estimar iluminância e temperatura de cor. Na captura de 3,5× verificada, o Codex leu **5272 K** com uma amostra de 227 ms de idade. O OPLUS Fusion RGB também entregou canais brutos recentes, sem unidade de temperatura confirmada. A câmera registrou separadamente ganhos de balanço de branco R 1,789, G 1,000 e B 1,557. [ams OSRAM TCS3720](https://ams-osram.com/products/sensor-solutions/ambient-light-color-spectral-proximity-sensors/ams-tcs3720-alscolor-and-proximity-sensor) · [Ganhos de WB no Android](https://developer.android.com/reference/android/hardware/camera2/CaptureResult#COLOR_CORRECTION_GAINS)

O Codex registra a iluminância em lux quando o sensor padrão do Android entrega uma leitura recente. Para calibrar uma eventual correção de cor, medir uma carta neutra e de cores em cada lente sob luz única, repetir com luz mista e comparar o erro de cor e a estabilidade com o balanço automático da câmera. O sensor ambiente mede a luz que chega ao telefone, e a carta mede a luz refletida no enquadramento.

### Reno14 (CPH2737)
| ID | Tipo | Focal → equiv. | f/ | Observação |
|---|---|---|---|---|
| **0** | **Lógica**, físicas **2, 3, 4**; zoom 0,6–20× | — | — | — |
| 2 | Principal | 4,84 mm → ~26 mm | 1,8 | 50 MP, 6,55×4,92 mm (~1/1,95") |
| 3 | Ultra-angular 8 MP (FULL) | 1,60 mm → ~15 mm | 2,2 | 3264×2448 |
| 4 | Tele 3,5× (FULL) | 12,19 mm → ~80 mm | 2,8 | provável JN5 (mesma óptica e sensor físico do Reno16 Pro) |
| 1 | Frontal (FULL) | 3,20 mm → ~21 mm | 2,0 | 50 MP |

**OIS:** todas as câmeras informam `availableOpticalStabilization = 0`. Isso não quer dizer que falte OIS no hardware (os reviews confirmam OIS na principal e nos teles). Quer dizer só que o app **não controla** a estabilização pela API padrão, porque ela fica sempre a cargo do HAL.

## 3. Nível de hardware e capacidades

| | Find X8 Ultra | Reno16 Pro | Reno14 |
|---|---|---|---|
| Nível | LEVEL_3 em todas | LEVEL_3; frontal FULL | LEVEL_3 em 0 e 2; FULL em 1, 3 e 4 |
| RAW (DNG) | sim, em todas | sim, em todas | sim, em todas |
| MANUAL_SENSOR / POST_PROCESSING | sim | sim | sim |
| BURST_CAPTURE | sim | sim | sim |
| YUV_REPROCESSING | sim | sim, exceto a frontal | sim em 0 e 2 |
| PRIVATE_REPROCESSING | sim | **não** | sim em 0, 2 e 4 |
| 10 bits (DYNAMIC_RANGE_TEN_BIT) | sim (HLG10, HDR10, HDR10+, Dolby Vision 8b) | sim (HLG10) | sim (perfis 2/4/8/64) |
| COLOR_SPACE_PROFILES | sim | sim | sim |
| **ULTRA_HIGH_RESOLUTION_SENSOR** | **não** | **não** | **não** |
| **REMOSAIC_REPROCESSING** | **não** | **não** | **não** |

## 4. Tamanhos de saída (padrão Android)

| | Find X8 Ultra | Reno16 Pro | Reno14 |
|---|---|---|---|
| JPEG máx. | 4096×3072 (12,6 MP); frontal 3280×2464 | 4096×3072 em todas | 4096×3072; UW 3264×2448 |
| YUV_420_888 máx. | 4096×3072 | 4096×3072 | 4096×3072 |
| RAW_SENSOR | 4096×3072, em todas | 4096×3072 (+2048×1536 na principal e na UW) | 4096×3072 (+4096×2304 e 4096×1864 na principal) |
| RAW10 / RAW_PRIVATE | sim | não | não |
| HEIC | não anunciado no mapa padrão (o vendor tag `HEICEnabled=1` existe) | sim, 4096×3072 | sim, 4096×3072 |
| JPEG_R (Ultra HDR) | sim, 4096×3072 | sim, 4096×3072 | sim, 4096×3072 |
| Tamanhos JPEG distintos | 14–37 | 43–51 | 40–44 |

### Mapa de resolução máxima (MAXIMUM_RESOLUTION)
**Nenhum dos três aparelhos expõe** `scaler.availableStreamConfigurationsMaximumResolution` nem `sensor.info.pixelArraySizeMaximumResolution`. Para um app de terceiros, o teto real pela API padrão é **12,6 MP (4096×3072)** em todas as câmeras, mesmo com sensores de 50 MP e 200 MP.

A resolução cheia existe, mas só aparece em **vendor tags**:
- Find X8 Ultra: `com.oplus.custom.raw.size` / `com.oplus.custom.support.raw.size` listam 8192×6144, e o `SensorModeTable` da Qualcomm tem um modo de 8192×6144. Há ainda `com.oplus.QCFARemosaicType = 1` e `com.oplus.sensor.qcfafactor = 2` (Quad Bayer, fator 2).
- Reno16 Pro: `com.oplus.custom.sensor.info.pixel.array.size = 8192×6144` e `com.mediatek.control.capture.remosaicenable = 1`.
- Reno14: o mesmo `pixel.array.size = 8192×6144`, mais `com.oplus.upscale.input.size 4096×3072 → output.size 8192×6144`. Isso indica que o “50 MP” da câmera nativa pode ser **upscale** em parte dos modos.

**Implicação:** o modo “Máx. resolução” do app precisa tratar 12,6 MP como teto verificado. Os vendor tags de remosaic não fazem parte da API pública e não há garantia de que o HAL aceite esses pedidos de apps de terceiros. Só vale tentar como experimento isolado, com fallback para o tamanho padrão.

O JN5 suporta zoom de 2× com remosaic em hardware. Se a cadeia do fabricante expuser esse modo na tele de 3,5×, ele corresponde a aproximadamente 7×. Os recortes de 8× e 10× correspondem a cerca de 2,29× e 2,86× sobre a mesma tele. O Reno examinado deixou a capacidade pública `REMOSAIC_REPROCESSING` indisponível. No Codex, a medição das capturas registra a câmera efetiva e a grade da fusão, e o tratamento de sombras segue o mesmo critério nos dois recortes. [Samsung ISOCELL JN5](https://semiconductor.samsung.com/image-sensor/mobile-image-sensor/isocell-jn5/) · [Remosaic pela Camera2](https://developer.android.com/reference/android/hardware/camera2/CameraMetadata#REQUEST_AVAILABLE_CAPABILITIES_REMOSAIC_REPROCESSING)

## 5. ISO, exposição e compensação

| Câmera | ISO (`sensitivityRange`) | ISO analógico máx. | Exposição | Passo de EV |
|---|---|---|---|---|
| X8U principal (2) | 100–6400 | 6400 | 60 µs – **30 s** | ±3 EV em 1/330 (−990…990) |
| X8U UW (3) | 100–16000 | 16000 | 41 µs – 30 s | idem |
| X8U tele 3× / 6× | 100–6400 | 6400 | 78 / 51 µs – 30 s | idem |
| X8U frontal | 100–3200 | 3200 | 43 µs – 17 s | idem |
| R16P principal (2) | 100–25600 | 25600 | 100 µs – **32 s** | ±3 EV em 1/6 (−18…18) |
| R16P lógica (0) | 100–19200 | 19200 | 100 µs – 32 s | idem |
| R16P UW / tele | 100–19200 | 3200 / 12800 | 100 µs – 32 s | idem |
| R14 principal (2) | 100–6400 | 6400 | 100 µs – 32 s | ±3 EV em 1/6 |
| R14 lógica (0) | 100–19200 | 6400 | 100 µs – 32 s | idem |
| R14 UW | 100–15500 | **1550** | 100 µs – 32 s | idem |

Quando o ISO máximo passa do analógico (por exemplo, a UW do Reno14 com 1550 analógico e 15500 total), tudo acima desse ponto é **ganho digital**. Nesses casos é melhor aumentar a exposição ou o número de quadros do que subir o ISO.

Ranges “estendidos” em vendor tags: `com.oplus.pro.extension.iso.range` é 50–12800 no X8U e 50–3200 no R16P.

## 6. Redução de ruído, bordas e tons

| | Find X8 Ultra | Reno16 Pro | Reno14 |
|---|---|---|---|
| NR | OFF, FAST, HIGH_QUALITY, **MINIMAL**, ZSL, em todas | idem; frontal sem ZSL | idem; frontal e UW sem ZSL |
| EDGE | OFF, FAST, HIGH_QUALITY, ZSL | idem | idem; frontal e UW sem ZSL |
| Tonemap | CONTRAST_CURVE, FAST, HQ; **512 pontos** | 101 pontos | 101 pontos |
| Hot pixel / shading | OFF, FAST, HQ | idem | idem |
| Nitidez do vendor | `org.codeaurora.qcamera3.sharpness.range` 0–6; `saturation.range` 0–10 | `com.mediatek.ispfeature.controlEdgeLevel`, `controlContrastLevel`, `controlSaturationLevel`, `controlDisableTone` (só nomes, sem faixa publicada) | `controlEdgeLevel` e `com.oplus.controlEdgeLevel` |
| NR temporal / multiquadro do vendor | `MFNRType = 1`, `isQLLSupported = 1`, `temporal_denoise.*`, `SWMFandAIDenoiser.*`, `mfnrconfigs`, `mfsrconfigs` | `mfnrfeature.availablemfbmodes` 0/1/2/255, `nrfeature.available3dnrmodes` 0/1, `videoAinrAvailableModes` | idem ao R16P |
| HDR do vendor | `supportedHDRmodes`, `HDRDCGModes = 2563`, `isAutoHDRSupported = 1`, `stats_control.hdr_EV_*` e `night_EV_*` | `availableHdrModesPhoto` 0/3/1, vHDR/cHDR/stagger | idem |
| LTM (contraste local do ISP) | `ltmDynamicContrast.*` −100…100 (contraste, realce de sombras e contenção de altas luzes) | — | — |

Para a fusão própria, o mais útil aqui é **`NOISE_REDUCTION_MODE_MINIMAL`** junto com **`EDGE_MODE_OFF`** nos quadros do burst. Em todos os aparelhos, a curva de tom `CONTRAST_CURVE` permite pedir ao ISP uma saída mais “plana”.

## 7. Extensões do sistema (Noite / HDR)

| | Biblioteca `androidx.camera.extensions.impl` declarada |
|---|---|
| Find X8 Ultra | `/odm/framework/androidx.camera.extensions.impl.fake.jar` e, em outro XML, `/system/system_ext/framework/androidx.camera.extensions.impl.jar` |
| Reno16 Pro | `/vendor/framework/androidx.camera.extensions.impl.dummy.jar` |
| Reno14 | `/vendor/framework/androidx.camera.extensions.impl.dummy.jar` (`ro.camerax.extensions.enabled = true`) |

Os nomes **fake** e **dummy** indicam implementações vazias. A OPPO não entrega o Noite e o HDR dela por extensões a terceiros: esses modos ficam no app `com.oplus.camera`, pela SDK privada `com.oplus.camera.unit.sdk`. Por isso, **`CameraExtensionCharacteristics.getSupportedExtensions()` deve voltar vazio**, ou com extensões sem tamanhos de saída. O `CameraCaps.java` já trata esse caso com `hasExtensionOutput`.

*Ainda falta confirmar:* não deu para chamar a API pelo adb. É preciso registrar o retorno real num log do app em cada aparelho. Se vier vazio, o modo Noite do The Natural Cam deve usar só a fusão própria (`BurstFusion`).

## 8. Outros dados úteis

- `request.partialResultCount`: 2 no X8U e 10 nos Reno. `sync.maxLatency`: PER_FRAME_CONTROL em todos, ou seja, exposição e ISO valem quadro a quadro, o que serve para bracketing.
- `pipelineMaxDepth`: 8 em todos. Recomenda-se um ImageReader de YUV com no máximo cerca de 8 a 12 buffers.
- CFA: varia por câmera (RGGB, BGGR, GBRG, GRBG). O código de RAW precisa ler `colorFilterArrangement` e não pode presumir um padrão.
- Nível de branco 1023 e preto 64: RAW de 10 bits efetivos em todas as câmeras.
- `jpeg.availableThumbnailSizes`, faces e cenas: há modos de cena padrão; `extendedSceneMode` (bokeh) não é anunciado.
- Vendor tags publicados: X8U, 1299 nomes (Qualcomm `org.quic`, `org.codeaurora` e `com.qti`, mais `com.oplus`); R16P, 792; R14, 839 (`com.mediatek` e `com.oplus`).

## 9. Conclusões para o The Natural Cam

1. **O teto padrão é 12,6 MP**: não há mapa de resolução máxima e o 50/200 MP fica fechado em vendor tags.
2. **Há RAW_SENSOR 4096×3072 em todas as câmeras dos 3 aparelhos.** Isso abre caminho para uma **fusão em RAW** (Bayer de 10 bits) no lugar de YUV de 8 bits processado pelo ISP, que é o passo mais forte para ganhar textura e baixar o ruído (veja `pipeline_computacional.md`).
3. **NR MINIMAL e EDGE OFF estão disponíveis** nas câmeras traseiras. Hoje o burst pede `EDGE_MODE_FAST` por quadro e o `BurstFusion` aplica nitidez de novo, de modo que **a imagem é afiada duas vezes**.
4. **Não há extensões de Noite e HDR de verdade.** A fusão do app é o único caminho de várias exposições.
5. O X8U aceita exposições de 30 s e o R16P/R14 de 32 s. Na noite em tripé, dá para usar quadros longos com ISO baixo.
6. Na UW do Reno14, o ISO acima de 1550 é digital: o certo é limitar o ISO e compensar com mais quadros.

## Fontes

- Android, `CameraCharacteristics`: https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics
- Android, extensões de câmera: https://developer.android.com/media/camera/camera-extensions
- Sensores do Find X8 Ultra: GSMArena review https://m.gsmarena.com/oppo_find_x8_ultra-review-2822.php ; DeviceSpecifications https://www.devicespecifications.com/en/news/644b1766 ; T3 sobre o True Chroma https://www.t3.com/tech/phones/i-tested-the-future-of-phone-cameras-but-its-not-from-samsung-or-apple
- Reno16 Pro (HP5 200 MP): GSMArena https://www.gsmarena.com/oppo_reno16_pro_and_reno16_go_official_with_200mp_rear_camera-news-72985.php ; review https://www.gsmarena.com/oppo_reno16_pro-review-2973p5.php ; FoneArena https://www.fonearena.com/blog/483600/oppo-reno-16-price-specifications-reno-16-pro.html
- Reno15 Pro (HP5 + JN5 + GC50F6, conjunto que o Reno16 Pro herda): Gizmochina https://www.gizmochina.com/2025/08/02/oppo-reno-15-pro-camera-specs-design-leak/ ; GSMArena https://fo.gsmarena.com/oppo_reno15_pro_mini-review-2920p5.php
- Reno14: OPPO https://www.oppo.com/en/smartphones/series-reno/reno14/specs/ ; GSMArena https://m.gsmarena.com/oppo_reno14-ampp-13866.php
