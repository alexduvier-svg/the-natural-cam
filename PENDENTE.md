# The Natural Cam — o que falta (atualizado 2026-11-13 ~09h35, entrega das 12h)

Projeto: `ClaraCam/` (pacote br.com.duvier.thenaturalcam), versão atual **1.33.4** (versionCode 87),
APK: `entrega/APK_1.33.4.apk` (instalado no Reno 16 Pro). Partida congelada: `entrega/APK_PARTIDA.apk` (1.30.3).

## Teste com o dedo 13/11 → 1.33.x
- Aprovado pelo usuário como perfeito: 3,5×, 6×, 7×, 8× (régua de qualidade para todas as lentes).
- 1×/2×: principal sem nitidez do ISP por quadro (letras falsas); 1× com passada leve 0,3.
- Máxima: não desliga ao voltar ao app; 1× e 2× com Máxima saem 6144×4608 (28 MP).
- EXIF completo em toda lente (AndroidX ExifInterface): LensMake/LensModel (ultra 16 mm, principal 23 mm, tele 80 mm
  + recorte), focal real e equivalente final, FNumber, zoom digital, ISO/tempo, EV do usuário.
- Troca de lente: imagem congelada desfocada que se amplia, troca rápida para a lente nova (sem preto/imagem dupla).
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
