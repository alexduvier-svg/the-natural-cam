# The Natural Cam — o que falta (atualizado 2026-11-14 ~17h30)

Projeto: `ClaraCam/` (pacote br.com.duvier.thenaturalcam), versão atual **1.47.1** (versionCode 133, compilada e 95 testes no PC ok, NÃO TESTADA no aparelho; X Cam 1.40.0 testada pelo usuário: "está funcionando"),
APK: `entrega/APK_1.39.1.apk` (testada: 1.38.4) (instalado no Reno 16 Pro). Partida congelada: `entrega/APK_PARTIDA.apk` (1.30.3).









## 1.46.0 (14/11 21h) — correções do teste no aparelho (entrega em até 2 h)
Teste do usuário com a 1.45 (20h42–20h49), 6 problemas:
1/4. Cor inventada: fronha azul-escura (a 1.43 das 20h05 tinha a cor real) → tom noturno DESLIGADO (`NIGHT_TONE=false`).
2. Rótulo amarelo ilegível na tele à noite (contorno duplo) → quadros extras da 1.45 revertidos (Noite 12 na faixa
   "pouca", Máxima 6, Noite com zoom como antes).
3. Foco preso no rótulo ao apontar para o pé → do Câmera X 0.1.15: giro > 7° com cadeado aberto solta o foco do
   toque e volta ao contínuo (`SceneRotation` + `GyroMeter.reframedSince`); a marca de foco some da tela.
5. Processamento lento → menos quadros (item 2), tom noturno fora (~1 s), limpeza de grão em 1 etapa acima de 16 MP.
6. Voltando da galeria, a tela da foto antiga continuava por cima → a tela da foto fecha ao abrir a galeria; imagem
   congelada de troca de lente é limpa ao voltar ao app.
Mantido: limpeza de grão (NightDenoise), clarear interior escuro (IndoorExposure), nitidez mais forte só em imagem
ampliada (textura). NÃO TESTADO no aparelho.

## Tabela de qualidade — formato pedido pelo usuário (14/11 20h21)
Ao fim de cada trabalho de qualidade: tabela com OPPO × X Cam antes × X Cam novo e, depois dela, a explicação de cada
item e as ferramentas usadas. Itens: brilho (mediana), preto (p5), realce (p99), grão (σ do resíduo diagonal 2×2,
30º percentil, meios-tons), textura (|alta frequência σ 1,5 px| numa região de pele/tecido), nitidez (p95 do
laplaciano), contraste local (|Y − desfoque σ 20 px|), cor (R/G e B/G numa região), croma médio, MUSIQ (0–100, maior
melhor) e NIQE (menor melhor). Ferramentas: Python (NumPy, SciPy, Pillow), pyiqa (MUSIQ, NIQE), piq (BRISQUE),
código do app compilado (cadeia real de acabamento aplicada nos prints). Script: lab2/Chain.java + medidor em Python.
Na tela do celular a 1ª coluna de tabelas largas some: rótulos curtos, no máx. 4 colunas.

## 1.45.0 (14/11 20h) — textura da OPPO, cor do X Cam
- Usuário (20h16–20h17): "tom de pele e cor estão mais reais no X Cam do que na OPPO"; "o X8 tem mais definição,
  textura, mas o excesso de contraste deixa a pele avermelhada e a fronha azul escura". Régua: textura/definição da
  OPPO, cor e tom de pele do X Cam.
- Par 7× Máxima X Cam (NATURAL_20261114_201330_957) × OPPO 6× (IMG20261114201226): sem grão nos dois; X Cam detalhe
  11 × 28; pele R/G 1,15 (X Cam) × 1,19 (OPPO, avermelhada); fronha B/G 1,15 × 1,23 (OPPO azul escuro).
- NightTone suavizado: saturação 1,2 → 1,0 (croma só acompanha o brilho), claridade 0,35 → 0,15, pé 1,35 → 1,15,
  escurecer no máx. −0,5 EV. PhotonLook em imagem ampliada (zoom/Máxima): captura 0,4 → 0,8, detalhe ×0,4 → ×0,8.
- Na foto do 7×: textura 11 → 18, pele R/G 1,15 → 1,14 (cor mantida). Frasco: definição 22 → 30, fronha B/G mantida.
  NÃO TESTADO no aparelho.
- Usuário 20h18: o padrão vale para todas as lentes à noite; Máxima e Noite devem tirar mais informação do merge.
  CaptureDirector em pouca luz (ISO > 400): Noite com fusão incremental 15 quadros (era 12 na faixa "pouca");
  Noite com zoom por super-resolução 12 quadros; Máxima 1,5× 10 quadros (de dia continua 6). Limpeza, tom noturno
  e nitidez já valem para qualquer lente em pouca luz.

## 1.44.0 (14/11 20h) — tom noturno pela régua (mesma cena OPPO × X Cam)
- Par do usuário: frasco às 20h06 no Find X8 Ultra e às 20h05 no X Cam 1.43 (Reno16 Pro, NATURAL_20261114_200523_800).
  Medido (prints): grão ≈ 0 nos dois (limpeza OK); X Cam claro e lavado — mediana 103 × 85, preto p5 22 × 10,
  fronha azul brilho 104 × 58 e saturação 81 × 106, detalhe 22 × 42.
- Novo `NightTone` (só em pouca luz, depois da limpeza de grão): escurece só acima da mediana 86 (até ~80, máx.
  −0,8 EV, luz linear); claridade 0,35 em σ ≈ 1,5 % do lado menor; pé abaixo de 60 (expoente 1,35); croma
  acompanha o brilho ×1,2. Na foto do X Cam: mediana 103 → 79, p5 22 → 10, fronha 104 → 79 de brilho, detalhe 22 → 24.
- Ainda abaixo da OPPO: detalhe (24 × 42) e saturação da fronha (92 × 106). NÃO TESTADO no aparelho.

## 1.43.0 (14/11) — ajuste guiado por medidores de qualidade (pedido: "corrigir a partir das ferramentas")
- Ferramentas instaladas na nuvem: pyiqa (NIQE, MUSIQ; pesos baixados do GitHub, o huggingface é bloqueado) e
  piq (BRISQUE). Régua: prints da OPPO X8 Ultra ISO 1250 → MUSIQ 59–60, NIQE 3,5–3,8. Foto escura do X Cam → MUSIQ 25–26.
- Achados: (1) clarear a foto escura até a mediana da OPPO subiu o MUSIQ de 26 → ~32 (maior ganho medido);
  (2) a limpeza forte da 1.42 (2,5/0,08) BAIXOU as notas e deixou a pele cerosa → limpeza intermediária 1,8/0,12;
  (3) com a cadeia completa da 1.43 (clarear +1,2 EV, limpeza, nitidez) a foto fica mais limpa e na mediana 37,
  mas as notas não sobem (MUSIQ 25,1 → 23,2): os medidores premiam o grão como textura numa imagem que já perdeu
  detalhe real. Limite: os medidores rodaram em PRINTS reduzidos e recomprimidos — calibrar com JPEG original + ficha.
- Novo `IndoorExposure`: em interior escuro (ISO ≥ 800 ou Noite, sem clarear já planejado) clareia em luz linear até
  a mediana ~42, no máximo +1,2 EV, antes da limpeza (que mede o grão já ampliado). NÃO TESTADO no aparelho.

## RÉGUA NOTURNA (definida pelo usuário 14/11 18h41) — padrão MÍNIMO para fotos noturnas e de interior
- Referências: OPPO Find X8 Ultra, tele 70 mm f/2,1, ISO 1250, 1/60 s (frasco Naridrin sobre almofada azul e pelúcia,
  ev −1,3) e 1/40 s (pés na sala, ev −1,0), JPEG 12 MP. Prints recebidos em 1659×2212.
- 3ª referência (20h06, mesma cena do frasco, mais clara): mediana 86, p5 11, p95 146, grão ≈ 0 em todas as faixas,
  detalhe maior (pelúcia e trama nítidas). Faixa aceitável de brilho em interior: mediana ~42–86 conforme a cena.
- 4ª referência (20h12, OPPO 6×, pé de perto, interior): ver medidas na seção da versão que usar este par.
- Medido nos prints: grão de brilho praticamente zero em todas as faixas (resíduo diagonal ≈ 0 da sombra ao meio-tom);
  brilho mediano 42–64 (Y 0–255), sombras profundas mantidas (p5 ≈ 6); rótulo inteiro legível (letras de ~1 mm),
  pelos, rugas da pele, fibras da pelúcia e trama do tecido nítidos; cor natural, sem dominante.
- X Cam (foto escura do pé, 18h15, print): grão 14,3 na pele (faixa 40–80) e 7,8 nos meios-tons; mediana 24 (mais
  escura que a OPPO). Depois da limpeza 1.42.0: 2,6 na pele, sem plástico — mas o detalhe que já chegou afogado no
  ruído não volta: para a régua falta também CAPTURA (conferir na ficha: quadros usados, ISO, tempo, clarear).

## 1.42.0 (14/11) — limpeza calibrada na régua noturna
- NightDenoise: força 2,5 e ganho mínimo 0,08 (antes 1,3 / 0,18). Testado 1,3/2,5/4/6: 4 e 6 deixam a pele de plástico.
  Cena sintética σ 8: sombra lisa erro 8,0 → 1,1; tecido 8,0 → 2,5 com listras preservadas. NÃO TESTADO no aparelho.

## 1.41.0 (14/11) — limpeza profissional do grão em pouca luz + ideias do Câmera X
- Pedido do usuário: X Cam 1.40.0 funciona; em fotos escuras falta correção profissional de ruído. Pesquisa (GitHub,
  Reddit, artigos): o método de referência é o do HDR+ do Google (Hasinoff 2016: filtro de Wiener por frequência
  em blocos, calibrado pelo modelo de ruído), também usado no hdr-plus/Burst Photo; MotionCam e darktable separam
  granulado grosso e fino.
- Nova etapa `NightDenoise` (código próprio) no acabamento, depois da curva de tons e do clarear e antes da nitidez:
  ruído medido NA FOTO por faixa de brilho (resíduo diagonal 2×2 do Câmera X, 30º percentil para não confundir
  folhagem com ruído) → escala reduzida à metade (granulado grosso, só a correção volta ampliada) → escala cheia
  com Wiener 8×8 passo 4 em duas etapas (como o BM3D), média do bloco intacta, ganho mínimo 0,18 (grão fino natural,
  sem plástico). Só roda com σ ≥ 1,6; em boa luz a foto não muda.
- A nitidez (PhotonLook) agora usa o grão que SOBROU na foto final como portão; antes usava o ruído da fusão,
  medido antes do clarear, e realçava o grão nas fotos escuras.
- Do Câmera X 0.1.15: super-resolução pulada quando os quadros não têm deslocamento de subpixel (celular firme) —
  mais rápido (1.41.1: só no zoom com recorte; a Máxima mantém 28 MP).
- Do Câmera X, NÃO trazido ainda: pesos de movimento contra fantasmas, cor preservada na fusão, ajustes por lente
  (5×–10×) — dependem das aprovações do Câmera X e mexem na fusão aprovada.
- 1.41.1 (revisão independente): (1) GRAVE corrigido — de dia folhagem/grama fina disparava a limpeza; agora só roda
  em pouca luz (ISO ≥ 800, clarear ou Noite); (2) σ do medidor sem o viés de arredondamento (2/3/6 → 2,0/3,0/5,9);
  (3) piso do σ = ruído medido entre quadros na fusão (o resíduo 2×2 vê pouco ruído correlacionado; suave: −30 %);
  (4) nitidez volta ao ruído da fusão quando a limpeza não roda (de dia igual à 1.40); (5) super-resolução pulada SÓ
  no zoom com recorte — a Máxima continua com super-resolução e 28 MP (resolução aprovada); ficha só diz "pulada" no zoom.
- Prova: nível 1 (84 testes; cena sintética σ 8: sombra lisa erro 8,0 → 3,0, tecido 8,0 → 3,5 com listras
  preservadas, borda e letras firmes, foto limpa intacta; 12 MP de ruído puro ~1,8 s no PC). Na foto do pé enviada
  pelo usuário (JPEG reduzido): σ 6,7 → 3,8, pele sem plástico. NÃO TESTADO no aparelho.

## 1.40.0 (14/11) — padrão de cor e nitidez do PhotonCamera em todas as lentes
- Pedido do usuário: trazer a qualidade de cor e nitidez do PhotonCamera e aplicar em todas as lentes, principalmente
  3,5×–10×, substituindo o processamento anterior (escolha dele: "substituir tudo"; técnica reescrita com código
  próprio, sem copiar o código GPL do Photon).
- Nova etapa única `PhotonLook` no acabamento (CameraController), no lugar das 4 passadas de nitidez por lente
  (zoom 0,35 · ampliação 0,3 · recorte 0,6 · processador/tele 0,35):
  cor só no croma (vermelho 15 % mais contido, altas luzes rolam para o branco a partir de 92 %, saturação neutra 1,0)
  + nitidez de captura σ 1,05 × escala, força 0,4 + detalhe 5×5 força 0,20 limitado pelo contraste local
  (×0,4 em imagem ampliada) + portão de ruído 2,5σ + trava anti-halo na faixa 3×3 com folga de 2 níveis.
- A nitidez interna da fusão (0,30, 3×3) continua, porque o verificador de qualidade compara a fusão com ela; em pixel
  nativo ela conta como 0,3 da força de captura (PhotonLook completa só 0,1). Revisão 14/11: sem isso o grão do 1×
  subia +33 %; agora +20 % numa cena sintética (1.39: +12 %) — conferir o 1× em foto real.
- Vermelho contido só desce o vermelho acima da média (céu/ciano intactos); gaussiano de verdade na captura
  (antes a Máxima 1,5× caía no mesmo raio do 1×); Máxima 1,5× conta como ampliada.
- Fora do padrão por enquanto: RAW+, longa exposição e modos do sistema (têm acabamento próprio).
- Prova: só nível 1 (10 testes no PC em `PhotonLookTest`: cinza neutro intacto, vermelho contido sem mudar matiz,
  grão no nível da passada antiga, borda sem halo, textura mais definida, 28 MP cabe em 512 MB, 12 MP ~1,2 s no PC).
  Compilada na nuvem (assembleDebug + testDebugUnitTest: 71 testes, 0 falhas). NÃO TESTADO no aparelho.
- "X Cam" (pedido do usuário): tipo de compilação `xcam` = mesma câmera com identificador
  `br.com.duvier.thenaturalcam.xcam`, instala AO LADO do The Natural Cam (assinatura de debug da nuvem, diferente
  da do PC). Entregue como `XCam_1.40.0.apk`. Compilar: `./gradlew assembleXcam`.
- Atenção: os 3,5×, 6×, 7× e 8× aprovados como "perfeitos" em 13/11 mudaram — conferir em foto real contra a 1.39.1.

## Teste com o dedo 13/11 → 1.33.x
- Aprovado pelo usuário como perfeito: 3,5×, 6×, 7×, 8× (régua de qualidade para todas as lentes).
- 1×/2×: principal sem nitidez do ISP por quadro (letras falsas); 1× com passada leve 0,3.
- Máxima: não desliga ao voltar ao app; 1× e 2× com Máxima saem 6144×4608 (28 MP).
- EXIF completo em toda lente (AndroidX ExifInterface): LensMake/LensModel (ultra 16 mm, principal 23 mm, tele 80 mm
  + recorte), focal real e equivalente final, FNumber, zoom digital, ISO/tempo, EV do usuário.
- Troca de lente: (1.34.0) a imagem atual fica parada e nítida até a lente nova chegar JÁ FOCADA (começa na distância
  medida pela anterior + varredura ativa); alinha a paralaxe e esmaece com o zoom. Sem desfoque, preto ou quadro transparente.
  A OPPO é instantânea porque deixa as lentes ligadas; igualar exige abrir a tele pela câmera lógica (para depois).
- 1.34.6: corte seco (sem fantasma) só quando a câmera troca de lente de verdade; espera a tela se ajustar à lente nova;
  troca ~0,7 s (0,55 s é abrir a câmera). Testado: câmera LÓGICA sempre (1.35.0) dá troca instantânea, mas a ~40 cm o
  sistema alterna tele↔principal no meio do foco e o 3,5× fica desfocado — chave LOGICA_SEMPRE=false.
- Regra: tocar de novo num objeto já focado mantém o foco (raio 20 % da tela).
- 1.35.x (teste 13/11 tarde): o Reno NÃO libera o sensor de 50 MP a apps (sem ULTRA_HIGH_RESOLUTION; a chave MediaTek
  remosaicenable é ignorada) — o 2× da OPPO lê o centro do sensor e o nosso não consegue (foto OPPO_2x em entrega/teste_duvier2).
  2× agora pelo zoom do processador (mais natural, sem letras falsas da super-res); Máxima com 8 quadros e nitidez na
  escala da super-res (estava mole); sem limpeza de cor em 28 MP; "Quadro k/n" no Foto (não "Noite").
  Máxima ainda ~20 s (super-resolução de 28 MP ~14 s).
- 1.36.x (A/B com a câmera da OPPO na mesma posição, entrega/teste_duvier3/OPPO_ab_*): principal (0,6×, 1×, 2×) com
  nitidez de ALTA QUALIDADE do processador; 2× + passada honesta 0,35 na escala do zoom (rótulo 50 → 167, perto da
  OPPO, sem super-res); Máxima no 2× = só mais quadros, 12 MP, 5,6 s (era 18–27 s com aspecto de HDR).
  1× igual à OPPO; 3,5× igual ao das 8h40 (aprovado). Regra: acima da OPPO SEM a IA dela (nada inventado).
- 1.37.x: botões 1×, 3,5×, 7×, 10× (pedido do usuário); decisão de lente estável na faixa da tele (sem vai-e-vem;
  volta da galeria não reabre a tele que falhou); "não focado" da tele só vale com a principal medindo < 42 cm; botão
  livre assim que a rajada é somada e durante as cópias de óptica. LIMITE: frasco a 38–40 cm está abaixo do foco
  mínimo da tele (40 cm) — a 50 cm+ o 3,5×–10× fica sempre na tele.
- 1.37.3: 1× sem foco (foco ia ao fundo, 140 cm) — a principal abria na distância NÃO calibrada da tele e um toque
  podia ser ignorado por uma busca que nunca terminava; 3,5× com passada honesta 0,35. Teste do usuário 14h: todas as
  fotos 3,5×–10× pela tele (lente estável); 10× ruim = fora de foco a 42 cm (limite da tele), boas a 44–49 cm.
- 1.37.4–1.37.7: 7×/10× "1ª boa, depois ruins" tinha 2 causas: (a) a lente trocava para a principal pelo estado do AF
  da tele depois da rajada — removido, a lente é decidida só pela distância da principal ao escolher o zoom; (b) o toque
  na tele fazia varredura AUTO que travava a lente errada perto do limite — agora o toque na tele só indica a região ao
  foco contínuo (acerta). Segundo toque refoca se o último foco bem-sucedido tem mais de 5 s. Teste 15h: 7× duas fotos
  seguidas pela tele, detalhe 0,93 e 0,94; foco mantido depois da foto.
- 1.38.x: toque que falha 3× volta ao foco contínuo na região (1× parava em 19 cm); cópias de desfoque num grupo de
  2 núcleos, começam só após 8 s sem fotografar e são descartadas se o usuário fotografar (a 2ª foto seguida não saía);
  Máxima só começa sem cópia rodando (OutOfMemory 15h14/15h20); Máxima 0,6×/1× com 6 quadros (~18 s); fotos sequenciais
  (liberar a próxima antes de gravar deixava a câmera imprevisível — desligado).
- Avaliação do usuário 15h35: Máxima 1× ainda sem foco; 3,5×, 7× e 10× precisam de mais nitidez nas letras.
- 1.39.1 (NÃO TESTADA): "não foca no toque" no 1× — o rastreador que travava a lente no "pico de nitidez" medido na
  prévia errava (frasco a 40 cm travado em 19 cm / 140 cm); desligado, o foco do toque é o da câmera (AF AUTO confirmado).
- 1.39.0 (NÃO TESTADA no aparelho): lente da tele decidida só pela distância ao escolher o zoom (removidos o verificador
  que mandava 7×/10× para a principal depois de uma foto ruim e a decisão "perto" que durava 2 min); Máxima na principal
  = mais quadros em 12 MP (28 MP ampliados pareciam sem foco); Máxima de 28 MP só na tele.
- Falta: 7×/10× a 40–45 cm (limite da tele: forçar busca de foco antes da rajada ou usar a principal); 0,6×–2× abaixo
  da OPPO por falta de acesso ao sensor de 50 MP; Máxima 0,6×/1× ~20 s; app congelado pela OPPO no fundo.
- Tempo: super-res do zoom 4,5–8 s continua (é o que dá a qualidade aprovada); cópias de óptica em prioridade baixa.

## Entrega 13/11 — feito na 1.31.0–1.32.5
- Item 9: estado limpo a cada volta ao app (trava, lente por falha, medida velha; após 1 min fora: zoom 1×, EV 0, Foto, sem Máxima).
  3 fotos 3,5× com o app ido e voltado: mesma lente, 1/120 s, foco 45–46 cm (nível 2, toques por adb).
- Ficha/EXIF gravavam "1.24.1" fixo; agora a versão vem do build.gradle. Arquivos _DBG_ só com files/laboratorio.
- Tele a partir de ~42 cm (margem 15 % → 5 %).
- Recorte do zoom: uma só passada de nitidez (sem efeito aquarela); super-res no zoom até ISO 3200; 6× pela principal
  saía 682×512 — agora sempre 4096×3072.
- Sombras: limpeza só de cor (ShadowChroma): ruído de cor 0,73 → 0,43 no aparelho, brilho e textura iguais, 1,6 s.
- Visor mostra "zoom digital" acima de 3,5×.
- Achado: a OPPO congela o app ~10 s depois de ir para o fundo; a cópia _OPTICA_TELE (~45 s) só termina ao voltar ao app.
- 1.32.5 (revisão): lente certa ao voltar ao app, Máxima do Pro desliga, limpeza de cor só ISO ≥ 400 e até luma 100,
  uma passada de nitidez também acima de 9×, sem travamento por rosto na borda (MeteringRectangle negativo).
- LIMITE: com o objeto a ~40 cm a leitura da principal varia 34–47 cm e o 3,5× oscila entre tele (nítida) e recorte.
- Falta (depois da entrega): render em serviço de primeiro plano (OPPO congela no fundo); item 5 (troca de lente)
  e item 7/8 (prévia do desfoque) sem teste; foco à distância (ramo codex) não portado.

Build: `cd ClaraCam && ./gradlew --no-daemon assembleDebug testDebugUnitTest` (Git Bash).
Instalar via Wi-Fi: adb em `C:/Users/alexd/AppData/Local/Android/Sdk/platform-tools/adb.exe`,
aparelho `adb-3B664V0165A00000-6UyeKU._adb-tls-connect._tcp`, `export MSYS_NO_PATHCONV=1`;
`adb push apk /data/local/tmp/tnc.apk` e `adb shell pm install -r /data/local/tmp/tnc.apk`.
ATENÇÃO: se o usuário estiver usando o celular, NÃO dirigir a tela por adb.

REGRA: a captura nunca pode ter nitidez excessiva (olho humano); nitidez extra só no editor.

## OBJETIVO DO USUÁRIO (premissa)
O bokeh deve recortar o sujeito PERFEITAMENTE em qualquer distância focal (0,6×…7×), sem halo nem
bordas/partes finas borradas. Hoje o recorte vem só da profundidade (Depth Anything, baixa resolução)
e falha em objetos finos (tampa do Naridrin em 1×). Plano: máscara do sujeito por toque (segmentação
interativa) + refino pela cor em alta resolução (filtro guiado), testada em 1×, 3,5× e 7×.

### Recorte feito (2026-11-10)
- SubjectMask.java: MagicTouch (MediaPipe, Apache-2.0, assets/magic_touch.tflite 6 MB). Entrada
  [1,512,512,4] = RGB em [0,1] + ponto tocado (pixel único = 1); saída [1,512,512,1] JÁ é
  probabilidade (não aplicar sigmoide). Refinado por filtro guiado em 2 escalas na guia.
- PortraitEngine.bokeh(dp, focus, ap, alpha): coc × (1 − alpha) — máscara garante contorno fino,
  profundidade cobre o corpo do objeto e gradua o fundo; fundo não puxa amostras do sujeito (sem
  halo); desfoque em luz linear com peso para altas luzes (discos de bokeh nas luzes pontuais).
- Testado no Reno (frasco, toque no gargalo): 1× e 3,5× com contorno limpo, sem halo, tampa nítida.
- Regras do usuário aplicadas: sem contorno (limiar da máscara 0,32–0,82 joga o pixel misturado da
  borda para o fundo) e realces contidos (ombro suave acima de 60 % em luz linear no fundo desfocado,
  peso das luzes fortes até 4×). Testes: perfil da borda monotônico; luz pontual vira disco, máx 170.
- 7× NÃO validado: na luz de interior o celular usa a câmera principal com recorte 7× e o foco caiu no
  fundo (foto base já desfocada). Refazer com mais luz e objeto a > 60 cm (tele). Discos de bokeh em
  luzes pontuais ainda não vistos numa cena real (noite com luzes).
- Tela de teste do recorte (debug): files/recorte/*.jpg +
  `am start -n br.com.duvier.thenaturalcam/.TesteRecorteActivity --ef fx 0.54 --ef fy 0.43`.

## Novo layout do editor (2026-11-10)
- Abas Estilos · Ferramentas; grade por seção ("Ajustar e Corrigir", "Foco e Seleção") com ponto
  laranja nas ferramentas em uso; cada ferramenta abre só seus controles (cabeçalho com nome
  centralizado, "‹ Ferramentas" e "Zerar" com respiro da borda).
- Controles em pílula (PillSlider): arraste horizontal relativo e gradual, toque não pula, vertical
  rola, toque duplo zera. Curva de pontos estilo Lightroom (CurveView) com canais e curvas prontas.
- Resposta imediata: prévia leve mostrada a cada resultado (antes era descartada) — 27 atualizações
  em 1,5 s de arraste (~50 ms cada). Toque na foto recolhe/mostra o painel (fora de Desfoque/Máscaras);
  pinça recolhe e o zoom é mantido quando a área muda.

## Color grading e HSL no editor (2026-11-10)
- Recipe: gradeHue/Sat/Lum[4] (sombras, meios, realces, global), gradeBlend, gradeBalance;
  hslHue/Sat/Lum[8]. Salvos nas predefinições e na ficha. ColorWheelView: só arrasta se o dedo
  começa no ponto (rolar por cima da roda não tinge mais — bug visto no teste); toque duplo zera.
- Testes: pesos HSL somam 1 (sem cortes entre cores), cinza intacto, branco não estoura.
  No Reno: roda arrastada para o laranja aquece de leve (R +2, B −2 a ~30 % de saturação).

## Ruído IA calibrado (2026-11-10, teste com 4 rostos no Reno, debug TesteRuidoActivity)
- Modo "preservar textura": cor sai inteira; brilho sai 90% (LUMA_KEEP) e na pele 85% disso.
  A 85% de força: poros, sardas, cílios e vincos do lábio intactos, sem pele de plástico; pele negra
  melhor que o modo antigo 100%. Força recomendada ~85%.
- Tela de teste só no debug: adb push fotos em Android/data/<pkg>/files/teste e
  `adb shell am start -n br.com.duvier.thenaturalcam/.TesteRuidoActivity`.

## Feito na 1.14.0 (2026-11-10)
- Ruído IA no editor: NAFNet-SIDD-width32 (MIT, assets/nafnet_sidd_width32_fp16.tflite, 62 MB) em
  blocos 256 px na GPU (litert-gpu). Prévia 1200x1600 = 10,6 s; salvar 12 MP = ~50 s. Só regressão
  (tira ruído, não inventa detalhe). A 100% alisa um pouco o papel; ~60% é o ponto natural.
- Bokeh refeito (PortraitEngine.bokeh/apply): raio contínuo, fundo suavizado (sem degraus entre
  planos), primeiro plano desfocado se espalha sobre o fundo, BOKEH_SCALE 26 (f/5,6 ≈ 1,3–1,6 % do
  lado). focusAt usa o plano sob o dedo + percentil 75 (objetos finos). Testado no Reno: frasco nítido.
- Foco: tocar de novo no mesmo ponto já focado mantém o foco (log "foco mantido").
- APK agora tem ~169 MB por causa do modelo.

## Feito na 1.13.0 (2026-11-10, lista de 18 itens do usuário)
- Salvar edição de 28 MP: corrigido (etapas de vizinhança em faixas; teste prova 28 MP em 512 MB;
  no aparelho salvou em 7 s, 4608x6144). EXIF: abertura, focal, 35 mm, WB, EV, flash, ISO; edições e
  _RETRATO copiam o EXIF do original (inclui GPS se houver).
- Editor: segurar a foto = antes/depois; botão ⤢ esconde os controles; "Fotos" abre a galeria
  (e Editar sem foto abre o seletor); toque na foto = ponto de foco com marcador (liga o Desfoque);
  Máscaras Sujeito/Fundo (profundidade), Radial e Linear com luz/cor/matiz próprios;
  Curva de tons (5 pontos) e Matiz; família "Minhas" = predefinições salvas pelo usuário.
- Retrato: aberturas f/5,6–f/16 na câmera, padrão f/8, bokeh mais suave (escala 60 → 34).
- Noite: sempre a fusão própria (a extensão OPPO ignorava o zoom).
- RAW fora da lista de modos; 3× fora dos atalhos de zoom (era recorte da principal; 3,5× é a tele).
- Ajuste IA: servidor em ia-ponte/ pronto, falta o usuário publicar (wrangler) e configurar no app.
- Falta: fotos do Naridrin (original + "Aprimorar nitidez" da OPPO) para comparar o redutor de ruído;
  não estão mais no celular. WB 3,5× vs 1× (lentes diferentes) não foi tratado.

## Feito na 1.12.0 (sessão automática)
- Retrato: desfoque por camadas sem cos/sin/sqrt por amostra (deslocamentos pré-calculados,
  ~200 milhões de operações a menos; resultado idêntico, coberto por PortraitEngineTest).
- Retrato: medição por etapa gravada na ficha JSON (`retrato_tempo_decodificar_ms`,
  `_profundidade_ms`, `_inferencia_ms`, `_camadas_ms`, `_aplicar_ms`, `retrato_resolucao`) e no
  logcat (tag `TNC`, inclui o tempo de salvar). Ficha antiga (1.4.1) mostrava: fusão 4,2 s +
  profundidade 5,6 s, e ~20 s sem medição entre a foto e o retrato.
- Instalada no aparelho, mas NÃO testada na tela (estava acesa/em uso).

## Feito na 1.11.0
- Editor: Desfoque com modos Bokeh / Movimento / Zoom (MotionEffect.java) + controle contínuo.
- Granulado com tipos de filme (T-Max 100, Portra 400, Tri-X 400, HP5 400, Delta 3200).
- Filtros: Kodak Ektar 100, Hora dourada, Pele profunda, Moda rua, Tri-X 400; família Silver (estilo Silver Efex,
  com viragens selênio/sépia/platina); Herzog, Salgado, Hasselblad X2D 100 etc. (1.10.0).
- App lembra a última foto ao reabrir (Editar não diz mais "Fotografe primeiro").
- Longa: sem falta de memória, "Luz 0/−1/−2", sem pontos de ruído. Astro, Rua, Máx, peaking testados.

## Falta
1. Testar no aparelho (não verificado): Movimento/Zoom, Granulado, família Silver e Ektar no editor;
   ver se o resultado é natural e se o Movimento não recorta o cabelo.
2. Retrato lento (39 s): tirar 1 retrato na 1.12.0 e ler a ficha `_RETRATO_ficha.json` (ou
   `adb logcat -s TNC`) para ver qual etapa domina. Próximas ideias conforme o resultado:
   manter o Interpreter TFLite em cache entre fotos (liberar em onTrimMemory/onStop); evitar
   redecodificar o JPEG cheio (usar o bitmap da fusão); salvar o retrato em resolução menor se o
   salvar for o gargalo. Notar também: onSaved() libera o obturador enquanto o retrato ainda é
   processado e troca o texto "Criando retrato…" por "Salva" (decidir se é desejado).
3. Fusão RAW (próximo passo grande).
4. "Ajuste IA" com Claude: aguardando decisão do usuário (precisa de servidor intermediário com a chave).

## Feito na 1.15.0 (2026-11-10)
- Editor: voltar por gesto recua um nível (painel escondido → ferramenta → grade → Estilos → sai).
- Grade sem quadrinhos vazios: novas ferramentas Brancos e Pretos, Vibração (em Cor, protege a pele),
  Nitidez (fina, só brilho, com limiar e limite anti-halo), Névoa (+ tira véu / − névoa leitosa),
  Difusão (tipo Pro-Mist, ombro nas luzes), Miniatura (tilt-shift com o desfoque de lente do Retrato
  numa profundidade sintética; toque posiciona a faixa). Vinheta foi para "Foco e Seleção".
- Títulos principais em laranja e centralizados (editor, Ui.sheet). Ajustes da câmera em seções:
  Captura · Visor · Sobre o Aparelho.
- Instalada e aberta no Reno. NÃO testado na tela: voltar por gesto, novas ferramentas, Ajustes.

## Feito na 1.16.0 (2026-11-10) — qualidade nas lentes
- Causa do 3× destruído: a tele (lente 4) só foca a partir de 40 cm; a 38 cm o sistema trocou em
  silêncio para recorte 3,5× ampliado da principal (lente 2). Ficha: lente_fisica_ativa=2, zoom 3,5.
- Aviso no visor (laranja, no topo): "Perto demais para a tele… afaste-se" quando o zoom é da tele e
  a lente ativa não é a tele; "Não focou. Afaste-se: esta lente foca a partir de X cm" após 3
  tentativas de foco. Ficha ganha aviso_lente e aviso_foco.
- Recorte ampliado: nitidez do ISP desligada (EDGE_MODE OFF) para não "aquarelar" letras.
- Verificador de qualidade (QualityCheck): compara a fusão com o quadro mais nítido nas bordas
  (detalhe ≥ 0,7 e forma ≥ 0,9); se reprovar, salva o quadro mais nítido limpo. Ficha:
  verificador_qualidade. Testes: fiel passa, borrão e fantasma reprovam.
- Fluidez medida no visor parado: 0,9 % de quadros lentos, 99 % abaixo de 13 ms.
- Falta testar na mão: aviso da tele perto/longe, foco em cada lente, troca de lentes e edição.
- Próximo grande: ISP próprio a partir do RAW (fusão RAW, revelação tipo Hasselblad).

## Feito na 1.17.0 (2026-11-10) — tele de verdade e ISP RAW próprio
- Vídeo do usuário: na mesma distância a câmera nativa usa a tele e o nosso app ficava no recorte
  da principal (a câmera lógica não troca para a tele no app). Agora a partir de 3,45× o app abre
  a tele DIRETO (câmera pública "4"); abaixo disso volta à lógica. Razão calculada pela focal
  equivalente (80 ÷ 23 mm), não pela focal real (dava 2,2×). Troca ≈ fecha/abre a câmera.
- Avisos de distância removidos do visor a pedido (continuam na ficha: aviso_lente, aviso_foco).
- RAW+ (modo de volta na lista): rajada RAW 3–8 quadros alinhada e somada no domínio Bayer com
  recusa de movimento pelo modelo de ruído do sensor; preto, sombreamento (só se o sensor não
  aplica), WB e matriz do próprio resultado, demosaico Malvar–He–Cutler, curva tipo filme com
  ombro longo (branco da cena = 243, não estoura), acabamento leve. RawIspTest: 5 testes.
- NÃO testado no aparelho: troca para a tele em 3,5× (log confirma "câmera 4 a partir de
  3,45×"), RAW+ (cor, exposição, sombreamento nas bordas, tempo) — comparar com Foto.

## Feito na 1.17.2 (2026-11-10) — tele perto demais
- Vídeo: a 3,5× com a tele a ~40 cm (limite dela) o foco não firmava. Agora, na tele, se o foco
  ficar sem firmar com a lente no limite de perto por 0,7 s, o app troca sozinho para a principal
  (3,5×); volta para a tele quando a principal foca além de ~55 cm por 1,5 s.
- Na principal recortada, a foto usa super-resolução de vários quadros (detalhe real).
- A nativa usa recorte do sensor de 200 MP, que não é acessível a apps; nosso recorte parte do
  modo 12 MP. NÃO testado ainda na mão: a troca automática e a qualidade de perto.

## Feito na 1.18.0 (2026-11-10)
- Fichas das fotos do usuário (1.17.2, 3,5×, 38 cm): a tele ficou aberta com AF "focado" no limite
  (2,6 dpt, mínimo 2,5) — por isso a troca não acontecia; agora troca também quando a lente encosta
  no limite de perto (≥ 96 % do foco mínimo). EV estava em −5 (−0,8 EV): foto escura.
- Fusão descarta quadros com < 70 % da nitidez (resolução cheia) do melhor quadro.
- Foto de perto pela principal gera também _OPTICA_TELE (fundo levemente desfocado, f/8 simulado).

## Feito na 1.19.0 (2026-11-10) — zooms 3,5× a 8× e diretriz de captura
- Usuário usa mais 3,5×, 6×, 7×, 8×. 6–8× = recorte da tele; o ISP ampliava e amolecia.
- ZOOM POR SUPER-RESOLUÇÃO: quadros pedidos com zoom 1,0 (grade nativa da lente), o app recorta o
  centro (YuvFrame.cropCenter) e a fusão amplia até 2,5× com amostras reais de 8+ quadros.
- CaptureDirector: diretriz por eixos (luz × tempo × zoom × lente × distância × rosto × modo ×
  política × manual) decide quadros, tempo máximo por quadro (regra 1/focal folgada; rosto ≤ 1/60;
  rua ≤ 1/250), super-resolução e acabamento. CaptureDirectorTest verifica 1.075.200 cenários.
- Corrigidos: falha "Index 0" com super-resolução; barra de EV mudando sem querer.
- NÃO testado na mão: 3,5/6/7/8× com o zoom por super-resolução (tempo e nitidez de rótulo).

## Feito na 1.19.1–1.19.3 (2026-11-10)
- Verificador compara super-resolução na MESMA escala (reduz por área) e desconta o ruído dos dois
  lados (antes reprovava fusões boas a 2,5× e salvava quadro único ruidoso/pequeno).
- Reserva do zoom: quadro mais nítido ampliado (bicúbico) + nitidez na escala, no tamanho da fusão.
- Diretriz aplica a limpeza de ruído por luz (fundo sem grão) e nitidez na escala do zoom super-res.
- Aviso "os quadros variaram" saiu da tela (fica na ficha).
- Sensor de cor TCS3720 (AmbientColor): luz_ambiente_cct_k e wb_ganhos_camera na ficha (passo 1;
  passo 2 = corrigir o WB com ele após calibrar).
- Troca tele/principal só depois de 1,5 s aberta e com o AF assentado (foco "agarrava" ao abrir).
- Integrados os arquivos do Codex (PhotoKnowledge/PhotoScenarioSpace): keySet() → keys() (Android).
- Usuário: 3,5× com boa nitidez; ruído vinha do quadro único (fusão reprovada) — corrigido.

## Feito na 1.19.4–1.19.7 (2026-11-10) — testes feitos pelo app no celular (frasco)
- Descoberto com imagens de depuração (_DBG_FUSAO/_DBG_REF, só na versão de teste): a tele diz
  "foco travado" e distâncias falsas (chegou a 25 m) com o objeto abaixo de 40 cm — tudo desfocado.
- Decisão de lente agora pela distância da PRINCIPAL (coerente: 24–38 cm medidos): ao escolher zoom
  de tele, se a principal mediu < ~46 cm, fica na principal + zoom por super-resolução; volta para a
  tele quando a principal mede > ~55 cm. Reserva: tele sem foco por 2,5 s → principal.
- Disparo espera a varredura do foco tocado (até 2 s); verificador reduz as imagens em pouca luz
  (ruído alto) antes de comparar; nitidez só acima de 2,5σ do ruído (diretriz do Codex).
- Resultado 3,5× a 27 cm: fusão aprovada (0,85), 2924×2194, rótulo inteiro legível.
- Codex: diretriz de 50 milhões (assets/knowledge, PhotoKnowledge, PhotoScenarioSpace) — referência,
  não usada na captura; regras úteis aproveitadas (limiar de nitidez, estados de foco).

## Feito na 1.19.8–1.20.0 (2026-11-10) — testes no boné/cadeira a ~2 m (tele)
- 3,5×/6×/7×/8× pela tele aprovadas. Reserva da super-resolução: refaz a fusão simples dos mesmos
  quadros (antes caía num quadro único ISO 6000 cheio de pontos coloridos); quadros só são
  liberados em saídas > 20 MP. Regra de tempo por quadro 6/focal (8× = 1/31 s, ISO ~1/3 menor).
- Memória das sombras (BurstFusion.shadowDenoise): limpeza forte em áreas escuras e lisas, leve no
  resto, textura quase intacta (teste: sombra −72% de ruído, rótulo mantém 91% do detalhe).
- Resolução: a tele só lista 4096×3072 em getHighResolutionOutputSizes — agora incluído (antes
  3264×2448). Saída mínima 4096×3072 (ampliação final bicúbica + nitidez na escala). JPEG 97.
- Resultado: 3,5× e 8× em 4096×3072, ~2,5 MB em interior escuro (ISO 5000–7000).

## Feito na 1.20.1 (2026-11-10)
- Pele protegida na memória das sombras (BurstFusion.skinCbCr, todos os tons): rosto em meia-luz
  recebe só a limpeza leve geral — teste: pele mantém 90% da textura, sombra cinza −67% de ruído.
- Rosto hoje: detector da câmera guia foco/exposição sem toque; diretriz limita 1/60 s; fusão
  recusa movimento. Falta: detecção de olho; testar com rosto real.

## Feito na 1.20.2 (2026-11-10)
- Usuário comparou 7× e 8× com a câmera da OPPO: o nosso app saiu melhor (boné natural, "como
  câmera, sem traços de IA"). A limpeza das sombras foi aprovada ("ótimo").
- Limpeza das sombras agora também no Rua e no RAW+ (já valia em Foto/Retrato/Noite em todas as
  lentes, 0,6× a 8×).
- Verificação da proteção de pele com 3 retratos reais (DemoPeleTest + testes_comparacao/): sem e
  com proteção davam quase o mesmo — quem limpa a pele é a limpeza leve geral (cor 5,0→1,2; brilho
  9→5–6). Pele agora: ruído de cor sai inteiro, brilho pela metade (SKIN_LUMA_CLEAN 0,5).

## Feito na 1.20.3 (2026-11-10)
- "Falha ao combinar quadros": no escuro (ISO 9000) a tele não firmava foco → ia para a principal →
  a principal media longe → voltava para a tele → pingue-pongue a cada 2–3 s; a câmera ficava
  fechada nas trocas. Agora a volta automática só ocorre se foi a principal que mediu perto, e a
  troca por falha da tele só uma vez por escolha de zoom. Se o acabamento falhar (memória), salva
  a fusão já aprovada. Teste: 8× no escuro salvou normalmente.

## Feito na 1.21.0 (2026-11-10)
- Ajustes em cards no estilo do editor (Captura · Visor · Sobre o Aparelho), card "Luz ambiente" com
  a temperatura de cor ao vivo (sensor TCS3720).
- Laboratório de tempo: ficha ganha tempo_etapa_* (verificador, limpeza leve, sombras, nitidez,
  ampliação). Medido: 1× ~10 s (fusão 4,7), 8× ~12–16 s (super-res 5,3–5,7), Máxima ~25 s (SR 12,7).
- Super-resolução pula amostras com d² > 1,2 px² (peso < 0,75 %): teste no PC −33 % de tempo com
  a mesma imagem (dif. máx 1 nível); no celular uma foto 8× não mostrou ganho claro (5,7 s) — medir mais.
- Codex (worktree próprio, pacote separado): diretriz de 50 milhões só de referência; vale trazer o
  CameraMotionMeter (giroscópio) para o tempo por quadro.
- Pendente: teste de cor do piso de taco com o sensor espectral vs câmera OPPO; referência X8 Ultra
  8× (desfoque natural sob a aba, trama do tecido nítida) — comparar com o nosso 8× no mesmo enquadramento.

## Laboratório boné 8× vs Find X8 Ultra 8× (2026-11-10)
- Enquadramento 3,5×/8× confere com o visor (a foto escura anterior era cena sem luz).
- Logo, pontos do bordado e costuras da aba: bons, sem aspecto digital. Trama do tecido da copa: a
  X8 Ultra mostra, nós não. Medido (LabTexturaTest sobre _DBG_PRE): a trama já não existe antes do
  acabamento (textura 0,56) e nem na 3,5× nativa — limite óptico: a X8 Ultra usa periscópica 6× de
  50 MP; nossa 8× é recorte 2,3× da tele de 80 mm. Limpeza das sombras não é a causa (variantes iguais).
- Desfoque sob a aba na X8U é óptico (focal ~131 mm). Opção: simular a profundidade de campo da focal
  equivalente com o motor de Retrato (gradual, sem halo). Não inventar textura (regra: sem IA generativa).
- Ferramentas: _DBG_PRE (imagem antes do acabamento, só na versão de teste), LabTexturaTest.

## Feito na 1.22.0 (2026-11-10) — óptica de lente longa
- Foto em 6×–8× (modo Foto) gera também _OPTICA_LONGA: desfoque gradual da profundidade de campo de
  uma lente longa (motor do Retrato: profundidade + recorte do sujeito tocado), 8× → f/5,6, 6–7× →
  f/8. Original intacta (arquivo separado); processamento normal não muda.
- Teste inválido: o celular foi movido (estava no WhatsApp) e o toque de foco caiu na parede, então a
  versão longa desfocou o boné. Refazer com o celular parado e o boné tocado. Cuidado: não dirigir o
  celular sem checar se o usuário está usando (aconteceu aqui).

## Feito na 1.22.1–1.23.1 (2026-11-10)
- Regra: desfoque sempre acima de f/5,6 (Retrato da câmera f/8–f/16; lente longa pelo dial).
- Dial de desfoque no visor (PillSlider, como o EV): Retrato e Foto em 6×+; f/16 → f/8, à esquerda
  "desligado" na lente longa (sem cópia). Aviso da Máxima removido.
- Desfoque inteligente nas cópias de óptica (tele de perto e lente longa): frente do plano de foco com
  só 15% do desfoque (PortraitEngine.bokeh frontScale) — como a X8 Ultra: atrás suave, frente nítida.
- Pedido: tratamento para pouca luz de fim de tarde (talvez no Noite) — próximo passo.
- 1.23.2: faixa nítida atrás do sujeito nas cópias de óptica (sharpBand 0,05 da profundidade relativa):
  o desfoque só começa alguns centímetros atrás; objetos vazados tratados pixel a pixel. Teste
  PortraitEngineTest.faixaNitidaAtrasEFrenteSuave (logo atrás 0, frente 15 % da lente real).

## Feito na 1.24.0 (2026-11-10) — pouca luz
- GyroMeter (giroscópio, ideia do Codex): tremor real entra na diretriz. 8×: sem medida 1/31 s,
  apoiado 1/15 s (Foto até 1/15, Noite até 1/8 quando firme), tremendo 1/53, rosto 1/60.
  Ficha: tremor_rad_s. Objetivo: menos ISO em fim de tarde/sem luz artificial.
- Usuário: textura do boné boa; em pouca luz o ponto é o ruído. Testar 8× apoiado vs na mão.
- 1.24.1: dial de desfoque recolhido num ícone pequeno à direita do quadro (◎ + abertura); toque
  abre/fecha. Aparece no Retrato e em 6×+. Não conferido na tela: a bateria do celular acabou (1 %).
- Pouca luz medida: quarto quase escuro, AE da câmera já em 1/11 s e ISO 14.664 → a regra do
  giroscópio não alonga mais (ISO máx 19.200). Fusão tira ~70 % do ruído (20 → 6). Próximas opções:
  Noite apoiado (até 1/8 s) e Ruído IA (NAFNet) automático no Noite acima de ISO ~8000 (~50 s).

## Feito na 1.25.0 (2026-11-10) — Noite na mão (inspirado no Night Sight / Huawei)
- CaptureDirector.nightCap: o tremor do giroscópio ANTES do disparo decide o tempo de cada quadro
  no Noite: apoiado 1/3 s · mão muito firme 1/4 s · mão comum 1/8 s · inquieta 1/15 s · tremendo
  1/30 s · sem giroscópio 1/10 s. Rosto continua ≤ 1/60 s. Regra 1/focal continua valendo no zoom.
- CameraController: Noite alonga até 8× a medição (Foto 4×), nunca abaixo do ISO mínimo (luzes não
  estouram); prazo da rajada = 6 s + quadros × tempo.
- Teste noiteNaMaoComoNightSight (CaptureDirectorTest, 4/4 ok).
- Lab no aparelho (mesma cena do frasco, apoiado): antes 12 × 1/8 s ISO 1416 → agora 12 × 1/4 s
  ISO 456; ruído da fusão 2,74 → 2,41; mesa (ruído/brilho) −11%; 12/12 quadros usados (sem
  tremido); rótulo com a mesma nitidez. Ganho maior esperado em cenas mais escuras.
- Falta: fusão incremental (memória constante) para passar de 12 quadros (Night Sight usa até 15);
  testar na mão de verdade em cena bem escura; NAFNet (só regressivo) para ISO > 8000 no Noite.
- Pergunta em aberto: 6×+ com objeto < 40 cm (recorte da principal) — indicador / 3,5× / deixar.

## Feito na 1.26.0 (2026-11-10) — fusão incremental, luz do toque, 6× perto
- BurstFusion.Stream: fusão incremental (cada quadro alinhado e somado ao chegar, depois descartado;
  ~110 MB de acumuladores em vez de 18 MB por quadro). Referência = mais nítido dos 4 primeiros.
  Mesma matemática da fuse(). Noite sem super-resolução usa 15 quadros (CaptureDirector.stream).
  PC (StreamFusionTest, cena com textura aleatória, σ=9): 1 quadro 9,03 · completa 12 2,65 ·
  incremental 12 2,64 · incremental 15 2,37; quadro tremido fica fora.
- globalShift paralelo por deslocamento candidato (mesmo resultado; 17→5 ms e 14→3 ms no PC).
- Aparelho, Noite apoiado: 14/15 quadros de 1/4 s somados (1 pulado por atraso), ruído 11,5 → 3,1.
- Toque: FOCO no ponto (7%), LUZ medida no objeto + primeiro plano (±30% largura, até 40% abaixo):
  frasco branco não escurece mais a cena (ISO 3700 → 3268 ao tocar, ~0,2 EV).
- Bug 6× perto: a medida de distância da principal "vencia" (10 s) durante a fusão longa do 3,5× e
  o 6× abria a tele desfocada (ISO 15602, fusão reprovada). Agora vale enquanto a principal segue
  aberta. Teste 3,5×/6×/7×/8× no frasco: todos na principal, fusão aprovada, ruído 1,4–1,56.

## Feito na 1.27.0 (2026-11-10) — Primeiro Plano no editor e claridade da tela na foto
- Editor › Foco e Seleção › "Primeiro Plano" (ForegroundLight): clareia só o que está perto pela
  profundidade (Depth Anything), transição suave, curva x(1+g)/(1+g·x) (branco não estoura, cor
  preservada). Sliders Clarear e Alcance. Teste PC: fundo 40→40, mesa +0,98 EV (Clarear 0,6),
  frasco 245→247, degrau no ganho ≤ 1. Falta validar no aparelho numa foto Noite.
- Usuário: "na tela está claro, mas ao processar não vai". Medido: foto final 0,5–1 EV mais escura
  que a prévia (mesa 127→82, piso 107→76); o acabamento não escurece (43,3→42,9 no DBG_PRE vs final).
  Hipótese: ganho pós-RAW (CONTROL_POST_RAW_SENSITIVITY_BOOST) da prévia volta a 1× com ISO/tempo
  fixos. Correção: rajada repete o ganho da prévia; ficha grava "ganho_pos_raw_previa".
  NÃO VERIFICADO no aparelho: se o ganho for 100, a causa é outra (curva de tom da captura do ISP).
- Referência do usuário: app nativo do Reno ilumina e foca, mas "inventa detalhe e parece pintura"
  — queremos a claridade e o foco SEM isso.
- (1.27.0, continuação) Hipótese do ganho pós-RAW DESCARTADA no aparelho (ganho = 100). Causa real:
  com ISO/tempo fixos o sistema desliga o realce de sombras da prévia. Correção ToneMatch: 1 quadro
  com exposição automática do sistema antes da rajada (régua) + curva de tons por percentis
  (±1 EV, monotônica, só no Y). Aparelho: tela×foto 0,34 EV → 0,02 EV (mesa 0,40→0,09).
- Primeiro Plano refeito em EXPOSIÇÃO DE LUZ LINEAR (nível Lightroom, sem acinzentar): pé (preto
  fica preto), ombro que preserva o branco exato; Clarear 1 = +2,5 EV; Alcance até 92 % da cena;
  Fundo −1..1 (−4 EV … +1,5 EV); atalhos Rebatedor e Modo Palco. Aparelho: mesa +0,54 EV com 0,5
  (versão antiga), fundo inalterado.
- Editor: aba Estilos em grade igual às Ferramentas (seção por família, títulos laranja); voltar
  "‹" quando o nome da ferramenta é longo; pílulas Estilos/Ferramentas com respiro da borda.
- PRÓXIMO (pedido do usuário): níveis de iluminação estilo "Iluminação de Retrato" do iPhone
  (Natural, Estúdio, Contorno, Palco, Palco Mono, Clave Alta Mono) para pessoas e objetos, nos
  modos Noite, Foto e Retrato (na câmera, como cópia separada) e no editor.

## Feito na 1.28.0 (2026-11-10) — Iluminação de retrato no editor
- PortraitLight: 6 níveis (Natural, Estúdio, Contorno, Palco, Palco Mono, Clave Alta Mono) em
  exposição de luz linear, pessoas e objetos; sujeito = toque (MagicTouch + profundidade).
  Palco/Clave Alta isolam o sujeito (recorte × faixa de profundidade ±0,10) — a mesa da frente não
  acende; a luz é expandida ~0,6 % antes da transição (a borda do vidro não escurece/deforma).
  Teste PC: Estúdio +0,77 EV, Palco fundo 80→2, Clave Alta fundo →247, cabelo preto continua preto.
- Fica SÓ no editor (Ferramentas › Primeiro Plano), pedido do usuário; a câmera não gera cópia.
- Editor: setinha "‹" fixa em todas as abas (Estilos ↔ Ferramentas pela aba anterior; ferramenta →
  grade). Aparelho: Palco no frasco = fundo preto, frasco inteiro; base do frasco um pouco escura.
- Ideia do usuário em aberto: "1000+ paletas de cor" — ver resposta (calibração com cartela).

## Feito na 1.29.0 (2026-11-10) — foco perto, baixa luz em Foto/Máxima, Noite mais claro
- FOCO: no Foto 3,5× com o frasco a ~35 cm o app abria a TELE (mín. 40 cm) — foco no infinito
  (44 m) e fusão reprovada (detalhe 0,01–0,59). Causa: a medida de perto da principal "vencia" ao
  trocar de modo. Agora vale enquanto o celular não for reenquadrado (GyroMeter.stillSince, até 5 min).
  2ª proteção: foto na tele reprovada com detalhe < 0,6 → a próxima sai da principal (sem aviso).
- RUÍDO 3,5× Noite: a super-resolução no escuro espalhava as amostras (grão). Noite escuro com
  recorte: fusão incremental de 15 quadros em pixel cheio + recorte ampliado depois.
- BAIXA LUZ em Foto e Máxima (iso > 1600): mesmo tratamento do Noite (nightLike: 15 quadros,
  tempo pelo giroscópio, sem super-res, limpeza/nitidez/cor).
- Noite mais claro: +0,5 EV no escuro (+0,25 EV com pouca luz) em luz linear, pretos firmes,
  branco intacto, cor acompanha (ToneMatch.applyExposure).
- ColorCalib (ΔE 2000 + matriz 3×3) pronto para quando chegar a ColorChecker Passport Photo 2.
- NÃO VERIFICADO no aparelho ainda: 1.29.0 instalada, usuário testando.

## Feito na 1.29.1 (2026-11-10) — prévia perto na principal (foco "terrível")
- Na câmera lógica, pedir 3,5× fazia o SISTEMA trocar sozinho para a tele (lente 4) mesmo com o
  objeto perto: prévia borrada, distância falsa (456 cm) lida como da principal → voltava à tele;
  Noite 3,5× saiu da tele desfocada e ainda recortada 3,5× (zoom dobrado).
- Correções: (1) no modo perto a prévia pede teleRatio×0,9 (fica na principal, nítida);
  (2) a distância só vale quando a lente ativa é a principal (LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID).
- Aparelho (frasco ~36 cm, escuro): Foto 1× tratamento de Noite (15 quadros, ISO 637, +0,5 EV);
  Foto 3,5× principal, detalhe 0,98, ruído 1,27 (antes 1,72); Noite 3,5× principal, foco 36 cm,
  ruído 1,59, prévia nítida.
- FALTA: no modo perto a prévia mostra ~10 % a mais de cena que a foto (zoom 3,15 em vez de 3,5) —
  escalar a TextureView pela razão zoom/deviceZoom para igualar o enquadramento.
- 1.29.2: prévia de perto ampliada na tela (TextureView × zoom/deviceZoom ≈ 1,11) — o TAMANHO do
  enquadramento agora bate com a foto; o toque de foco é convertido para o miolo. FALTA: posição
  ~5 % deslocada (na foto o objeto fica mais à direita/abaixo que na prévia) — provável recorte do
  ISP descentralizado na câmera lógica a 3,15×; medir o SCALER_CROP_REGION da prévia vs o recorte
  central do app e alinhar (bateria 8 % no teste).
- 1.29.3: "o que você vê é o que sai" — a régua de claridade é capturada no MESMO zoom da prévia e
  FrameLocate acha onde ela está no quadro completo (correlação de brilho normalizado, pirâmide ÷8;
  PC: erro 0,3 %, 0,19 s); o recorte (stream e super-res) é feito nesse ponto (YuvFrame.cropAt).
  Aparelho: Foto 3,5× perto, centro achado (0,528; 0,490) erro 0,05 — prévia e foto com o frasco na
  mesma posição e tamanho. Nitidez do recorte ampliado 0,9 → 1,15 (pedido do usuário; anti-halo
  mantido). JPEG 97 também no caminho Bitmap (a fusão já salvava a 97); 12 MP, ~2,2 MB.

## Feito na 1.30.0 (2026-11-10) — todos os ajustes com efeito real (nível Lightroom)
- EditorStrengthTest: cada controle no máximo medido contra alvos de Lightroom ±100. ANTES: 14 de
  26 fora (Exposição ±1 EV e estourava; Sombras +1 levava o preto 5→64 = acinzentava; Realces −1
  levava o branco 252→192; Contraste cortava pontas 15→0/240→255; Brancos/Pretos/Temperatura/
  Vibração/Claridade/Curva fracos; Vinheta com sinal invertido). DEPOIS: 26/26 OK.
- Luz do editor refeita em EXPOSIÇÃO LINEAR com pesos por faixa (StylePipeline.Prepared.apply):
  Exposição ±2,5 EV com ombro; Sombras +1 = +1,3 EV com preto 5→5; Realces −1 = −1 EV com branco
  252→246; Brancos +1 200→235; Pretos −1 40→20; Contraste em curva S (×1,59, pontas 15→4, 240→251);
  Temperatura b* +26; Vibração ×1,35; Claridade ×1,64; Curva ±30 níveis; Vinheta − escurece (−2,15 EV).
- ForegroundLight.lift: ombro novo (joelho em 50 %, inclinação contínua, branco → 1 exato; o
  antigo passava de 1 com ganho grande). Primeiro Plano/Iluminação seguem passando.
- Aparelho: Sombras +0,85 no editor — sombra da mesa 41,8→61,7, preto 3,4→3,4.
- FALTA medir: HSL, Color Grading, Difusão, Granulado, Ruído, Máscaras.
- 1.30.1: EditorStrength2Test — HSL (Sat ×1,71/×0,01, Lum Azul −17/+22 L*, Matiz +39°), Difusão
  (+24 níveis ao lado da luz), Granulado (7,9 níveis), Ruído (−85 %, borda intacta), Máscara radial
  (+2,4 EV no centro, canto igual) já OK; Color Grading reforçado .18→.23 (Δab 10,9/11,4 → 13,2/14,7).
  Editor: 37/37 controles medidos no padrão. NÃO INSTALADO: celular com tela bloqueada na hora.

## Feito na 1.30.2 (2026-11-10) — DIRETRIZ: o desfoque não come bordas
- Defeito visto pelo usuário na cópia de óptica do frasco: a borda de trás da mesa e o fio ficaram
  borrados como o fundo distante (profundidade de baixa resolução mistura borda perto com piso longe).
- PortraitEngine.bokeh (Retrato, óptica 3,5×–8×, Desfoque do editor):
  1. "o perto vence" — profundidade do mais perto da vizinhança (máx. local ~0,5 %) com rampa suave;
  2. coisas finas pela FOTO — perto de saltos de profundidade (faixa ~1,5 %), contorno forte na
     imagem (imageEdges) fica nítido;
  3. incerteza → mais nítido ou desfoque suave, nunca corte duro;
  4. não vale para o que está claramente à frente do foco (primeiro plano desfocado de propósito).
  Óptica: faixa nítida atrás do sujeito 5 % → 8 %.
- BokehEdgeTest (profundidade borrada como a da rede): borda da mesa raio 0,02, fio 0,41, fundo
  junto da borda 2,19, fundo longe 2,35 (antes: borda e fio recebiam o raio do fundo).
- Memória: desfoque-nao-come-bordas. NÃO TESTADO no aparelho (tela bloqueada).
- 1.30.3: no ESCURO (ISO ≥ 6400, luz de fundo apagada) a principal não mediu a distância e o 3,5×
  abriu a tele com o frasco a ~35 cm (ISO 18000, foco 25 m, detalhe 0,1; a proteção "próxima pela
  principal" funcionou). Regra nova: escuro sem medida de LONGE → 3,5× fica na principal
  (nearReason 1: volta à tele sozinha se a principal medir > ~55 cm). mainFarNow().
  NÃO testado no aparelho (usuário usando o app); APK na pasta do PC.

## Feito na 1.47.1 (X Cam, 14/11 22h)
- Usuário: na 1.47.0 a qualidade caiu e o rótulo ganhou "letras inventadas". Causa provável: a nitidez do PhotonLook
  realçava o grão que a limpeza nova deixa. Em pouca luz agora vale o par inteiro do Câmera X 0.1.15: limpeza
  que preserva traço + nitidez que só age em borda/traço acima de ~2,2σ (detalhe 55, halo ≤ 4,4 níveis), com o
  mesmo medidor de ruído (`BurstFusion.noiseModel`). A cor do PhotonLook continua. Boa luz: sem mudança.
- Teste no PC: a nitidez nova deixa o grão do fundo igual (σ 4,00 → 4,00) e não enfraquece as letras.
- NÃO TESTADO no aparelho.

## Feito na 1.47.0 (X Cam, 14/11 noite)
- Texto miúdo do rótulo à noite: a limpeza Wiener 8×8 (NightDenoise) foi trocada pela limpeza do Câmera X 0.1.15
  (`StrokeSafeDenoise`, cópia do caminho de limpeza do `CaptureTuning`): só alisa áreas lisas, não toca traços,
  pele nem tons acima de 140. Teste no PC (letras de 1–2 px, contraste 15, ruído σ 4): preserva 93 % do contraste
  (Wiener antigo: 87 %). O fundo fica com um pouco mais de grão que na 1.46 (σ 3,5 contra 0,9): é o preço do texto.
- NÃO TESTADO no aparelho: falta a foto do frasco à noite comparada com o Câmera X.

