# Pipeline computacional: HDR+, Night Sight e projetos abertos aplicados ao `BurstFusion`

## 1. Estado atual do `BurstFusion.java`

O código foi lido sem alteração em 09/11/2026.

| Etapa | Hoje |
|---|---|
| Entrada | Quadros **YUV_420_888 de 8 bits** já processados pelo ISP. Por quadro: NR `MINIMAL` (ou FAST) e **`EDGE_MODE_FAST`** (`CameraController.java` ~l.1028–1036). |
| Referência | O quadro mais nítido do burst inteiro (gradiente em 1/4). |
| Alinhamento | Deslocamento global em pirâmide (1/16 → 1/4) e refinamento por **blocos de 128 px**, com busca **inteira** de ±2 px em resolução cheia e mediana 3×3 dos vetores. Sem subpixel e sem sobreposição entre blocos. |
| Modelo de ruído | σ por faixa de brilho (16 faixas), medido entre a referência e quadros alinhados pela mediana de \|ref − alt\|. |
| Mistura | Por pixel: peso cheio até 2σd, zero a partir de 4,5σd (curva k²), em Y e UV (UV na média 2×2). |
| Acabamento | NR de luma 5×5 com limiar de 2,5σ (força ≤ 0,6), filtro de croma guiado, **nitidez 3×3** com *coring* acima de 3σ. |

Pontos fracos que as referências resolvem:
1. **Nitidez dupla:** o ISP já afia cada quadro (EDGE FAST) e o app afia de novo, o que gera halos e realça o ruído residual.
2. **8 bits pós-ISP:** a NR e o tone mapping do ISP já destruíram parte da textura, e o ruído passa a ser espacialmente correlacionado (o próprio código comenta isso). O HDR+ funde em **RAW**.
3. **Alinhamento inteiro com blocos duros:** dá meio pixel de erro (detalhe fino vira média borrada) e pode deixar costuras entre blocos.
4. **Mistura no domínio espacial pixel a pixel:** é robusta, mas desperdiça redução de ruído nas baixas frequências, onde dá para misturar mais sem risco.

## 2. O que dizem as referências

### HDR+ (Hasinoff et al., SIGGRAPH Asia 2016)
- Captura **quadros com a mesma exposição, subexpostos** de propósito para proteger as altas luzes. Isso torna o alinhamento robusto: todos os quadros são parecidos.
- Trabalha em **RAW Bayer**, não na saída do ISP.
- Referência: o mais nítido entre os **primeiros 3** quadros, para limitar a latência.
- Alinhamento hierárquico por blocos (pirâmide de 4 níveis sobre o Bayer reduzido a cinza 2×2), com blocos de 16 px e busca L2 nos níveis grossos e L1 no fino.
- Mistura por bloco no **domínio da frequência**: um filtro de Wiener par a par (referência × alternativo) com **modelo de ruído do sensor** (variância = a·sinal + b). Em cada frequência, a diferença é atenuada conforme o ruído esperado. Os blocos se **sobrepõem pela metade**, com janela cosseno, e não deixam costura.
- Depois vêm denoise espacial, demosaico, cor e **tone mapping local** (fusão de exposições sintéticas), nitidez e denoise de croma.
- Cerca de 4 s para 12 MP no celular de 2016, em Halide.
- A análise detalhada de Monod, Delon e Veit (IPOL 2021) mostra o efeito de cada parâmetro: tamanho de bloco, fator de robustez do Wiener (`c`) e número de quadros.

### Night Sight (Levoy e Pritch, blog de 2018; Liba et al., SIGGRAPH Asia 2019)
- **Motion metering:** fluxo óptico e giroscópio medem o movimento antes do clique e definem o tempo por quadro, de 15 × 1/15 s até 6 × 1 s, com até 333 ms na mão se a cena estiver parada.
- **Positive shutter lag:** na noite, só começa a capturar depois do clique.
- A mistura padrão do Night Sight é a **super-resolução multiquadro** (Wronski et al., 2019), com balanço de branco aprendido e tone mapping que mantém “cara de noite” (sombras escuras, sem virar dia).

### Super-resolução multiquadro na mão (Wronski et al., SIGGRAPH 2019)
- **Substitui o demosaico.** O tremor da mão desloca cada quadro em subpixel, e cada amostra Bayer é “espalhada” na grade final com um **núcleo anisotrópico** orientado pelo tensor de estrutura local: estreito ao longo da borda e largo no sentido dela.
- **Mapa de robustez:** compara a cor local com a variação esperada pelo ruído; onde não bate (movimento, oclusão), o quadro perde peso.
- Cerca de 100 ms por quadro de 12 MP no celular. É a base do Super-Res Zoom.

### HDR+ com bracketing (Ernst e Wronski, 2021)
- Junta 5 exposições curtas antes do clique e **1 longa depois**, e a longa melhora sombras, textura e cor. No Night Sight, são 12 curtas + 3 longas.

## 3. Projetos abertos e licenças (verificadas em 09/11/2026)

| Projeto | Licença | O que tem | Pode usar no The Natural Cam? |
|---|---|---|---|
| **timothybrooks/hdr-plus** (Halide/C++; hoje mantido por Titaniumtown) | **MIT** (GitHub API) | Implementação do HDR+: alinhamento hierárquico, mistura no domínio da frequência, acabamento | **Sim**, inclusive trechos de código, **mantendo o aviso de copyright e a licença MIT** num arquivo `THIRD_PARTY_NOTICES`. É preciso portar de Halide para Java (ou para GPU via OpenGL ES/Vulkan). |
| **amonod/hdrplus-python** (IPOL 2021) | **AGPL-3.0** | Python fiel ao artigo, com parâmetros documentados | **Só como referência de leitura.** Copiar código obriga o app a ser AGPL. O próprio repositório alerta para possível ligação com a **patente US9077913B2**. |
| **eszdman/PhotonCamera** | **GPL-3.0** | App Android Camera2 com fusão RAW em GPU (OpenGL ES), alinhamento, mistura e pipeline de cor | **Só como referência de ideias.** Copiar código exige que o app inteiro seja GPL-3. Está ativo (push em out/2026). |
| **Open Camera** (Mark Harman) | **GPL-3.0-or-later** | Modo “Redução de ruído” (alinha e funde burst em YUV, desde a 1.44), HDR por bracketing, DRO | Só como referência. É útil para ver **como lidar com quirks de aparelhos** no Camera2. |
| **MotionCam** (app de Mirsad Makalic) | **Proprietário** (o app atual é fechado) | Pipeline RAW completo | Não. |
| f0enix/motioncam (versão antiga) | GPL-3.0 | Snapshot de quando era aberto | Só como referência. |
| **mirsadm/motioncam-decoder** | **Apache-2.0** | Decodificador do formato MCRAW | Sim, com aviso. É pouco relevante para a fusão. |
| kunzmi/ImageStackAlignator | GPL-3.0 | Implementação (C#/CUDA) da super-resolução de Wronski | Só como referência. |
| HDR+ Burst Dataset (Google) | Dados para pesquisa (ver termos no site) | Bursts RAW de Nexus e Pixel com resultado de referência | Para **testes no PC** (`BurstFusionTest`), conforme os termos do dataset. |

**Regra prática (diretriz do projeto):** ideias e algoritmos descritos em artigos podem ser reimplementados do zero a partir do **artigo**, sem olhar o código GPL/AGPL na hora de escrever. Código MIT ou Apache pode ser aproveitado com atribuição. **Patentes** (Google tem patentes sobre partes do HDR+) **não** são resolvidas pela licença de código: antes de publicar comercialmente, a mistura no domínio da frequência no estilo HDR+ precisa de consulta jurídica. A mistura robusta no domínio espacial que o app já usa é técnica clássica e de baixo risco.

## 4. O que aproveitar no `BurstFusion` (em ordem de ganho por esforço)

### A. Corrigir a nitidez dupla (esforço baixo, ganho alto)
- Quadros do burst com **`EDGE_MODE_OFF`** e `NOISE_REDUCTION_MODE_MINIMAL`. Os três aparelhos suportam esses modos nas câmeras traseiras (`capacidades_aparelhos.md` §6).
- Uma **única** nitidez no fim, em luma, no estilo Adobe/HDR+: *unsharp mask* com raio ~0,8–1,0 px, **máscara de borda** (gradiente acima de k·σ residual) e limiar = 2–3 σ residual. Isso evita halos e não realça o ruído.

### B. Modelo de ruído do sensor como prior (esforço baixo)
- `CaptureResult.SENSOR_NOISE_PROFILE` dá (S, O) por canal CFA, com variância = S·x + O em RAW. Em YUV, serve como prior; em RAW, é o modelo exato.
- Combinar com o σ medido entre quadros (que já existe) pela mediana dos dois. Isso dá estabilidade em cenas com muito movimento, onde a medição sozinha superestima o ruído.

### C. Alinhamento subpixel e blocos sobrepostos (esforço médio, ganho alto em textura)
- Blocos de **32–64 px com 50% de sobreposição** e janela cosseno elevada na hora de misturar. Acaba com as costuras e segue a paralaxe local melhor que blocos de 128 px.
- **Subpixel:** ajustar uma parábola nos 3×3 valores de SAD ao redor do mínimo e amostrar o quadro alternativo com interpolação bilinear (ou bicúbica em luma).
- Tremor da mão em subpixel, não compensado, é exatamente o que hoje “borra” o detalhe fino.

### D. Mistura em duas escalas (esforço médio)
- Separar cada quadro alinhado em **baixa e alta frequência**: pirâmide laplaciana de 2–3 níveis ou um passa-baixa 5×5.
- Baixa frequência: limiares de robustez **mais largos** (o ruído ali é baixo e o ganho de SNR é grande). Alta frequência: limiar atual (2σ → 4,5σ).
- É o efeito do Wiener do HDR+ (atenuar a diferença por frequência) sem FFT e sem tocar na abordagem patenteada.

### E. Fusão em RAW (esforço alto, maior ganho)
- Os 3 aparelhos têm **RAW_SENSOR 4096×3072, 10 bits**, em todas as câmeras (o app já cria `rawReader`).
- Pipeline proposto: burst RAW com exposição constante, **−0,7 a −1 EV** para proteger as altas luzes, seguindo o HDR+. Depois: alinhamento no Bayer reduzido a cinza 2×2, mistura robusta por canal CFA com `SENSOR_NOISE_PROFILE`, subtração de preto, `LENS_SHADING_MAP`, ganhos de WB (`COLOR_CORRECTION_GAINS`), demosaico (começar por um RCD/AHD simples) e matriz `SENSOR_COLOR_TRANSFORM`/`FORWARD_MATRIX`. Por fim, tone mapping local, que é onde os estilos entram.
- **Atalho que mantém o ISP:** os três aparelhos anunciam **YUV_REPROCESSING** na traseira. Dá para fundir em YUV e **devolver o quadro fundido ao ISP** por uma requisição de reprocessamento, com `REPROCESS_EFFECTIVE_EXPOSURE_FACTOR = N_efetivo`. O ISP então aplica a NR e a nitidez “sabendo” que o ruído caiu √N, um caminho que o Android previu justamente para fusão multiquadro feita pelo app. Custa pouco e aproveita a cor e o JPEG do fabricante.

### F. Noite: motion metering simples (esforço médio)
- Antes do clique, medir o deslocamento global entre os últimos quadros da prévia, que já é calculado no alinhamento, e o giroscópio. Com isso, escolher entre “**muitos quadros curtos**” (movimento) e “**poucos longos**” (parado ou tripé). Limite de 333 ms na mão; no tripé, até os 30–32 s que o sensor aceita.
- Limitar o ISO ao **analógico máximo** (`SENSOR_MAX_ANALOG_SENSITIVITY`; por exemplo, 1550 na UW do Reno14) e compensar com quadros.

### G. Referência entre os primeiros quadros (esforço baixo)
- Escolher o mais nítido entre os 3 primeiros, como no HDR+. O momento capturado fica mais próximo do clique e o custo do cálculo cai.

### H. Super-resolução (futuro)
- Para zoom digital de 1,5–2× na principal, a mistura com núcleo anisotrópico (Wronski) aproveita o tremor da mão. Só vale **depois** do RAW (item E).

## 5. Métricas de teste sugeridas (`BurstFusionTest` no PC)

- **Ruído:** σ em zonas planas antes e depois (já existe em `Result.noiseBefore/After`).
- **Textura:** energia de alta frequência em zonas texturizadas, por exemplo a variância do laplaciano em blocos com gradiente acima de 3σ. A textura deve se manter ou subir enquanto o σ das zonas planas cai.
- **Halos:** máximo de *overshoot* nas bordas fortes.
- **Fantasmas:** porcentagem de pixels com peso < 0,2 em blocos com movimento simulado.
- **Cena-padrão:** a do copo de vidro em luz interna (referência do usuário) e bursts do HDR+ Dataset.

## Fontes

- Hasinoff et al., “Burst photography for high dynamic range and low-light imaging on mobile cameras” (2016): https://research.google/pubs/burst-photography-for-high-dynamic-range-and-low-light-imaging-on-mobile-cameras/ ; dataset: https://www.hdrplusdata.org/dataset.html
- Levoy, “HDR+: Low Light and High Dynamic Range photography” (2014): https://research.google/blog/hdr-low-light-and-high-dynamic-range-photography-in-the-google-camera-app/
- Levoy e Pritch, “Night Sight: Seeing in the Dark on Pixel Phones” (2018): https://research.google/blog/night-sight-seeing-in-the-dark-on-pixel-phones/
- Liba et al., “Handheld Mobile Photography in Very Low Light” (2019): https://arxiv.org/abs/1910.11336
- Wronski et al., “Handheld Multi-Frame Super-Resolution” (2019): https://arxiv.org/abs/1905.03277 ; https://sites.google.com/view/handheld-super-res
- Ernst e Wronski, “HDR+ with Bracketing on Pixel Phones” (2021): https://research.google/blog/hdr-with-bracketing-on-pixel-phones/
- Monod, Delon e Veit, “An Analysis and Implementation of the HDR+ Burst Denoising Method”, IPOL 2021: https://www.ipol.im/pub/art/2021/336/ ; https://arxiv.org/abs/2110.09354
- timothybrooks/hdr-plus (MIT): https://github.com/timothybrooks/hdr-plus ; texto original: https://timothybrooks.com/tech/hdr-plus
- amonod/hdrplus-python (AGPL-3.0): https://github.com/amonod/hdrplus-python
- eszdman/PhotonCamera (GPL-3.0): https://github.com/eszdman/PhotonCamera
- Open Camera (GPL-3.0+): https://opencamera.sourceforge.io/ ; ajuda do modo NR: https://opencamera.sourceforge.io/help.html ; F-Droid: https://apt.izzysoft.de/fdroid/index/apk/net.sourceforge.opencamera
- MotionCam (proprietário): https://alternativeto.net/software/motioncam/about/ ; f0enix/motioncam (GPL-3.0): https://github.com/f0enix/motioncam ; mirsadm/motioncam-decoder (Apache-2.0): https://github.com/mirsadm/motioncam-decoder
- kunzmi/ImageStackAlignator (GPL-3.0): https://github.com/kunzmi/ImageStackAlignator
- Android, `CaptureResult.SENSOR_NOISE_PROFILE` e reprocessamento: https://developer.android.com/reference/android/hardware/camera2/CaptureResult#SENSOR_NOISE_PROFILE ; https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#REPROCESS_EFFECTIVE_EXPOSURE_FACTOR

As licenças foram conferidas pela API do GitHub (`license.spdx_id`) em 09/11/2026, exceto Open Camera (site oficial e F-Droid) e MotionCam (AlternativeTo).
