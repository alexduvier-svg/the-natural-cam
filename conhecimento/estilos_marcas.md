# Como as marcas tratam nitidez, textura, micro-contraste, curva e ruído

O que segue resume fontes públicas: sites oficiais, manuais e artigos. Nada foi copiado. **Não são receitas oficiais** e nenhuma LUT, perfil ou arquivo proprietário foi usado (diretriz do projeto). Os nomes das marcas aparecem só como referência de inspiração, como o `StyleCatalog.java` já declara.

---

## 1. O que cada referência ensina

### Hasselblad: HNCS (Hasselblad Natural Colour Solution)
- A Hasselblad descreve a HNCS como um **sistema único de cor**: em vez de um perfil por tipo de cena, um só caminho de cor deve servir pele, paisagem e produto do disparo à edição. No celular, foi adaptada pela OPPO com a Hasselblad para lembrar o caráter da X2D.
- No Find X8 Ultra, a cor se apoia num **sensor cromático dedicado** (“True Chroma”, 2 MP), que mede a luz da cena para o balanço de branco. No aparelho, essa câmera aparece como `SYSTEM_CAMERA` (ID 6), inacessível a terceiros; veja `capacidades_aparelhos.md`.
- O que os reviews e o próprio discurso da marca mostram: tons de pele estáveis entre as lentes, **altas luzes que descem suaves** (sem recorte duro), saturação contida e nitidez moderada. O acabamento é de “arquivo bruto bem revelado”, não de imagem “crocante”.
- **Para o app:** um caminho de cor neutro e igual para todas as lentes, ombro longo nas altas luzes, pouca clareza, nitidez fina baixa a moderada e ruído residual tolerado em vez de pele “plástica”.

### Leica: Leica Looks / perfis de cor
- A Leica publica “Looks” como perfis de cor: Standard, Vivid, Natural, Monochrome, Monochrome High Contrast. O **Classic** é descrito como contraste alto, **saturação suave** e cores quentes um pouco lavadas, evocando cinema.
- A comunidade (L-Camera-Forum, Red Dot Forum) separa **micro-contraste** de nitidez medida e lembra que sensores Leica não têm filtro anti-aliasing. O resultado é detalhe fino “de origem” e pouca necessidade de nitidez adicional. A própria comunidade trata o “Leica look” como algo subjetivo.
- **Para o app:** micro-contraste na banda de **textura** (frequência média), não nitidez de borda forte. Pretos densos, saturação mais baixa que o contraste sugere e P&B com contraste alto e grão fino.

### Fujifilm: simulações de filme e ajustes de JPEG
- A Fujifilm documenta os ajustes de JPEG que acompanham cada simulação:
  - **Tone Curve:** dureza separada de altas luzes e sombras.
  - **Sharpness:** mais definição, mexendo o mínimo nos tons.
  - **High ISO NR:** quanto mais alto, menos ruído e mais contornos suavizados.
  - **Clarity:** definição de detalhe, mexendo pouco nas altas luzes e nas sombras.
  - **Grain Effect:** intensidade e tamanho do grão.
  - **Color Chrome Effect (e FX Blue):** mais gradação em cores muito saturadas.
- Descrições oficiais: o *Classic Chrome* tem cor suave e **contraste de sombra reforçado**, o *Classic Neg* tem cor realçada e tonalidade dura, e o *Velvia* tem cor vibrante para paisagem.
- Numa entrevista resumida pelo Fuji Rumors, engenheiros dizem que cada simulação ajusta o balanço de cada cor (não dá para fazer Velvia só subindo a saturação do Provia) e que **mais nitidez também realça o ruído**. Por isso nitidez e NR precisam ser ajustadas juntas.
- **Para o app:** cada estilo precisa de um par **nitidez × NR** coerente, curva separada para sombras e altas luzes, grão opcional em dois tamanhos e um “cromo de cor” que reduza a luminância só em cores muito saturadas, para recuperar textura em vermelhos e verdes.

### Kodak: filmes (Portra, Ektar, Tri-X)
- As fichas técnicas da Kodak (por exemplo, a E-4050 do Portra 400) dão o grão em **Print Grain Index**: cerca de 25 em ampliação 4×6, que é o limiar visual de granulação. Descrevem ainda grão muito fino, **pele natural** e latitude ampla.
- **Para o app:** retratos “K” com nitidez baixa, **textura protegida na pele** (sem suavização forte) e grão finíssimo, perto do limiar visual. Ombro longo, para que a superexposição não queime. O Tri-X (“Prata 400”) leva grão médio visível e contraste alto.

### Nikon: Picture Control (modelo de três bandas)
- A Nikon divide a nitidez em três controles:
  - **Sharpening:** detalhe fino e contornos.
  - **Mid-range sharpening:** padrões e linhas entre a nitidez fina e a clareza.
  - **Clarity:** áreas grandes de contraste, sem mexer no brilho geral.
  - **Quick Sharp:** move os três juntos.
- Guia prático (Universidade de Sussex): clareza e nitidez média *negativas* deixam a pele lisa, e a nitidez fina positiva mantém os olhos nítidos.
- **Para o app:** é o modelo mais direto para o `StylePipeline`. São três parâmetros por estilo (`sharpen`, `texture`, `clarity`) e um atalho de intensidade que move os três.

### Adobe: Camera Raw / Lightroom (modelo do afiador)
- A Adobe descreve a nitidez como uma variação de *unsharp mask* com quatro controles:
  - **Amount:** força.
  - **Radius:** tamanho do detalhe.
  - **Detail:** quanto da alta frequência entra (valores baixos tratam mais as bordas e evitam halo).
  - **Masking:** uma máscara de bordas; em 100, só as bordas fortes recebem nitidez.
- O limiar é calibrado automaticamente por câmera e ISO. Na comunidade, *Texture* é descrita como a banda entre Sharpen e Clarity, e *Clarity* puxa contraste de meios-tons.
- **Para o app:** um afiador com `amount`, `radius` e `masking` (máscara de borda), cujo **limiar segue o ruído medido** pela fusão. O `BurstFusion.sharpenLuma` já usa um limiar baseado no ruído (*coring*); falta a máscara de borda e o raio variável.

---

## 2. Parâmetros novos propostos para `StyleCatalog.Style`

Hoje cada estilo tem `contrast, saturation, warmth, lift, shoulder, mono, mix`. A proposta é acrescentar estes campos, com o neutro em 0:

| Campo | Faixa | O que faz | Base |
|---|---|---|---|
| `sharpen` | −0,5…+0,5 | nitidez fina (raio ~0,7 px), somada à do pipeline | Nikon Sharpening / Adobe Amount |
| `radius` | 0,6…1,4 px | tamanho do detalhe afiado | Adobe Radius |
| `masking` | 0…1 | quanto a nitidez se restringe às bordas fortes (pele e céu ficam de fora) | Adobe Masking |
| `texture` | −0,5…+0,5 | contraste local de média frequência (σ ≈ 2–4 px) | Nikon Mid-range / Adobe Texture / micro-contraste Leica |
| `clarity` | −0,5…+0,5 | contraste local de baixa frequência (σ ≈ 15–30 px), só nos meios-tons | Nikon/Fuji Clarity |
| `toe` | −0,1…+0,1 | dureza das sombras, separada do contraste geral | Fuji Tone Curve (Shadow) |
| `nrBias` | −0,5…+0,5 | desloca a força do acabamento de NR da fusão (negativo preserva mais grão e textura) | Fuji High ISO NR |
| `grain` | 0…0,3 | grão aplicado **depois** da NR, modulado pela luminância (mais nos meios-tons) | Fuji Grain Effect / Kodak PGI |
| `grainSize` | 1…2 | 1 = fino (Portra/Acros), 2 = médio (Tri-X) | Fuji Grain Size |
| `chrome` | 0…0,3 | reduz a luminância de cores muito saturadas, para devolver gradação | Fuji Color Chrome Effect |
| `skin` | 0…1 | protege a faixa de matiz da pele contra `texture` e `clarity` positivas | prática Nikon/Kodak |

Regras de segurança:
1. `sharpen` e `texture` são multiplicados por `(1 − ruído_residual_normalizado)`. Em ISO alto, o estilo não pode “inventar” nitidez sobre ruído (lição da Fuji).
2. O grão sempre entra **depois** da NR e da nitidez, para não ser afiado.
3. `clarity` usa um filtro com preservação de bordas (guiado ou bilateral em escala reduzida), para não criar halos.

---

## 3. Valores sugeridos para os 40 estilos

Valores iniciais, para calibrar com imagens de referência do app (veja `agentes_e_banco.md`). Colunas: **sh** = sharpen, **ms** = masking, **tx** = texture, **cl** = clarity, **toe**, **nr** = nrBias, **gr** = grain (tamanho), **chr** = chrome, **sk** = skin.

| # | Estilo | sh | ms | tx | cl | toe | nr | gr | chr | sk | Racional |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 0 | Natural (Original) | 0 | .5 | 0 | 0 | 0 | 0 | 0 | 0 | .5 | referência: só a fusão |
| 1 | L · Natural | 0 | .5 | +.10 | 0 | 0 | −.1 | 0 | 0 | .5 | micro-contraste discreto |
| 2 | L · Clássico | 0 | .6 | +.15 | +.05 | +.04 | −.1 | .04 (1) | .10 | .6 | contraste alto, saturação suave |
| 3 | L · Cor intensa | +.05 | .5 | +.15 | +.10 | +.03 | 0 | 0 | .20 | .6 | Vivid, com gradação nas cores fortes |
| 4 | L · Retrato | −.10 | .8 | −.05 | −.10 | 0 | 0 | 0 | .05 | 1 | pele protegida, olhos pela máscara |
| 5 | L · Rua cromática | +.10 | .4 | +.20 | +.10 | +.05 | −.15 | .05 (1) | .15 | .5 | rua com textura |
| 6 | L · Monocromático | +.05 | .5 | +.20 | +.05 | +.04 | −.2 | .06 (1) | — | .3 | P&B: o ruído pode virar grão |
| 7 | L · Prata suave | −.05 | .6 | +.05 | −.05 | −.02 | −.1 | .05 (1) | — | .5 | tons longos |
| 8 | L · Documental denso | +.10 | .4 | +.25 | +.15 | +.08 | −.25 | .10 (2) | — | .2 | Monochrome High Contrast |
| 9 | L · P&B vermelho | +.05 | .5 | +.20 | +.10 | +.05 | −.2 | .06 (1) | — | .3 | filtro vermelho, céu escuro |
| 10 | L · Noite P&B | 0 | .6 | +.10 | +.05 | +.03 | −.3 | .12 (2) | — | .3 | ruído noturno assumido como grão |
| 11 | F · Equilíbrio | 0 | .5 | +.05 | 0 | 0 | 0 | 0 | .05 | .5 | Provia/Standard |
| 12 | F · Natureza viva | +.05 | .5 | +.10 | +.10 | +.02 | 0 | 0 | .25 | .3 | Velvia: chrome alto contra verdes “chapados” |
| 13 | F · Retrato suave | −.10 | .8 | −.10 | −.10 | −.02 | +.1 | 0 | .05 | 1 | Astia/Pro Neg |
| 14 | F · Cromo clássico | 0 | .6 | +.10 | +.05 | +.08 | −.1 | .04 (1) | .15 | .6 | Classic Chrome: **sombra dura** |
| 15 | F · Negativo clássico | 0 | .6 | +.10 | +.05 | +.07 | −.1 | .06 (1) | .15 | .6 | Classic Neg: tonalidade dura |
| 16 | F · Cinema suave | −.05 | .7 | 0 | −.10 | −.03 | 0 | .03 (1) | .10 | .7 | Eterna: plano |
| 17 | F · Cor real | 0 | .5 | +.05 | 0 | 0 | 0 | 0 | .10 | .6 | Nostalgic/Reala: neutro |
| 18 | F · Prata | 0 | .5 | +.15 | +.05 | +.03 | −.2 | .06 (1) | — | .3 | Acros: grão fino característico |
| 19 | F · Prata amarelo | 0 | .5 | +.15 | +.05 | +.04 | −.2 | .06 (1) | — | .3 | Acros + Ye |
| 20 | F · Prata vermelho | +.05 | .5 | +.20 | +.10 | +.06 | −.2 | .07 (1) | — | .3 | Acros + R |
| 21 | K · Retrato 160 | −.10 | .8 | −.05 | −.10 | −.02 | 0 | .03 (1) | 0 | 1 | Portra 160: grão no limiar visual |
| 22 | K · Retrato 400 | −.05 | .8 | 0 | −.05 | 0 | −.05 | .04 (1) | 0 | 1 | Portra 400 |
| 23 | K · Retrato 800 | −.05 | .7 | 0 | −.05 | +.02 | −.1 | .07 (1) | 0 | 1 | Portra 800: grão um pouco maior |
| 24 | K · Dourado | 0 | .6 | +.05 | +.05 | +.02 | −.05 | .06 (1) | .10 | .7 | Gold: quente, granulação leve |
| 25 | K · Paisagem 100 | +.10 | .5 | +.15 | +.10 | +.03 | 0 | .02 (1) | .20 | .4 | Ektar: grão finíssimo, nitidez alta |
| 26 | K · Cromo 100 | +.05 | .5 | +.10 | +.05 | +.04 | 0 | .02 (1) | .15 | .5 | E100: limpo |
| 27 | K · Cromo documental | +.05 | .5 | +.15 | +.10 | +.06 | −.1 | .04 (1) | .15 | .5 | Kodachrome-like |
| 28 | K · Cinema diurno | −.05 | .7 | 0 | −.05 | −.02 | 0 | .04 (1) | .05 | .7 | Vision3 50D: plano e limpo |
| 29 | K · Prata 400 | +.05 | .4 | +.20 | +.10 | +.08 | −.3 | .14 (2) | — | .2 | Tri-X: grão médio visível |
| 30 | K · Prata fina | 0 | .5 | +.10 | +.05 | +.03 | −.15 | .05 (1) | — | .4 | T-Max: fino |
| 31 | H · Natural | −.05 | .6 | +.05 | 0 | 0 | 0 | 0 | .05 | .7 | HNCS: neutro e suave |
| 32 | H · Pele neutra | −.10 | .8 | −.05 | −.10 | −.02 | +.05 | 0 | 0 | 1 | pele da X2D |
| 33 | H · Paisagem | 0 | .5 | +.10 | +.05 | +.02 | 0 | 0 | .15 | .5 | detalhe sem crocância |
| 34 | H · Documental | 0 | .6 | +.10 | +.05 | +.03 | −.1 | .03 (1) | .10 | .6 | |
| 35 | H · Luz suave | −.10 | .7 | −.05 | −.15 | −.03 | +.05 | 0 | .05 | .8 | |
| 36 | H · Sombras profundas | 0 | .6 | +.10 | +.05 | +.08 | −.05 | 0 | .10 | .6 | sombra dura, altas luzes suaves |
| 37 | H · Pastel | −.10 | .7 | −.10 | −.15 | −.04 | +.1 | 0 | 0 | .8 | |
| 38 | H · Estúdio | 0 | .6 | +.05 | 0 | +.01 | 0 | 0 | .05 | .8 | produto e retrato |
| 39 | H · Prata natural | −.05 | .6 | +.10 | 0 | +.02 | −.15 | .04 (1) | — | .5 | |
| 40 | H · P&B clássico | 0 | .5 | +.15 | +.05 | +.05 | −.15 | .05 (1) | — | .4 | |

Observações:
- O **Natural** e a família **H** são os que mais se aproximam do objetivo do projeto (textura real com pouco ruído) e da referência de qualidade, a câmera nativa do Find X8 Ultra. Devem ser calibrados primeiro.
- Nos estilos P&B, `nrBias` negativo faz sentido: ruído de luminância fino, sem cor, lê como grão e preserva textura (Fuji e Kodak).
- `chrome` atua só onde a saturação HSV passa de cerca de 0,6. Assim não mexe na pele.

## 4. Ordem recomendada no `StylePipeline`

1. Fusão e acabamento de NR do `BurstFusion`, com `nrBias` do estilo.
2. Matriz de cor (`mix`), saturação e calor (como hoje).
3. **Clarity** (baixa frequência, em luminância e meios-tons, com proteção de pele).
4. Curva (`contrast`, `toe`, `lift`, `shoulder`).
5. **Texture** (média frequência) e **sharpen** com `radius`/`masking`, limitados pelo ruído residual.
6. `chrome`.
7. **Grão** (por último).

## Fontes

- Hasselblad, HNCS: https://www.hasselblad.com/learn/hasselblad-natural-colour-solution/
- OPPO, HNCS e cor (marketing do Find X9 Ultra; usado só como contexto): https://www.oppo.com/uk/articles/find-x9-ultra/phones-hasselblad-natural-colour-solution/
- Amateur Photographer, parceria OPPO × Hasselblad: https://amateurphotographer.com/latest/photo-news/true-colours-oppo-and-hasselblad-team-up
- T3, review e sensor True Chroma: https://www.t3.com/tech/phones/i-tested-the-future-of-phone-cameras-but-its-not-from-samsung-or-apple
- Leica Looks (oficial): https://leica-camera.com/en-AU/photography/leica-looks
- Manual Leica X1, contraste, nitidez e saturação: https://www.manualslib.com/manual/290851/Leica-X1.html?page=32
- Macfilos, Leica Looks × simulações Fuji: https://www.macfilos.com/2025/09/10/leica-looks-vs-fuji-film-simulations/
- L-Camera-Forum, “Leica look” (opinião): https://www.l-camera-forum.com/topic/293758-the-leica-look-is-real/?page=14
- Red Dot Forum, nitidez em arquivos Leica: https://www.reddotforum.com/content/2020/04/lightroom-presets-for-leica-cameras/
- Fujifilm, Film Simulation e ajustes: https://www.fujifilm-x.com/en-us/products/film-simulation/
- Fujifilm, receitas (Clarity, Grain, Color Chrome): https://shopusa.fujifilm-x.com/discover/how-to-make-a-film-simulation-recipe/
- Fuji Rumors, ciência de cor (entrevista resumida): https://www.fujirumors.com/fujifilm-colors-science-gfx-vs-x-series-from-superia-to-classic-negative-more-film-simulations-part-2/
- Kodak Portra 400, ficha E-4050: https://www.freestylephoto.com/static/pdf/product_pdf/kodak/e4050_Kodak_Portra_400.pdf
- Nikon NX Studio, Picture Controls: https://www.nikonimglib.com/nxstdo/onlinehelp/en/the_picture_controls_tool_22.html
- Nikon Z9, Picture Controls: https://onlinemanual.nikonimglib.com/z9/en/05_shooting_settings_08.html
- Universidade de Sussex, ajustes da Z6: https://www.sussex.ac.uk/schools/media-arts-humanities/internal/documents/z6-images.pdf
- Adobe, nitidez e redução de ruído no Camera Raw: https://helpx.adobe.com/camera-raw/using/sharpening-noise-reduction-camera-raw.html
- Adobe Community, Sharpen × Texture × Clarity: https://community.adobe.com/t5/camera-raw-discussions/does-anyone-know-the-difference-between-sharpness-and-sharpening/td-p/14327291
- PhotoshopCAFE, Texture/Clarity/Dehaze: https://photoshopcafe.com/texture-clarity-and-dehaze-in-lightroom-acr-explained-the-ultimate-comparision/

**Licenças:** todas as fontes são texto público consultado para resumo. Nenhuma LUT, perfil `.dcp`/`.xmp`, receita oficial ou código foi incorporado. Os valores da tabela são originais do projeto.
