# The Natural Cam — o que falta (atualizado 2026-11-10 ~08h30)

Projeto: `ClaraCam/` (pacote br.com.duvier.thenaturalcam), versão atual **1.15.0** (versionCode 28),
APK: `The_Natural_Cam_1.15.0_teste.apk` (já instalado no Reno 16 Pro via adb Wi-Fi).
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
