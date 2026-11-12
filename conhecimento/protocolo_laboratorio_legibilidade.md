# Luz localizada e legibilidade no Reno 16 Pro

## Referência em 3,5×

Nos JPEGs fornecidos pelo usuário, os dois apps registraram a lente principal de 5,56 mm e f/1,8. O app OPPO usou cerca de 1/120 s a ISO 1000 com −1,16 EV. O Codex 1.27.2 usou cerca de 1/30 s a ISO 155 com −1,67 EV, embora a prévia já estivesse em 1/120 s a ISO 619. O tempo maior aumenta a oportunidade de borrar os detalhes dentro de cada quadro. O enquadramento e a compensação de exposição diferiram entre as fotos.

| Região comparável | OPPO, luminância JPEG | Codex 1.27.2, luminância JPEG |
|---|---:|---:|
| Fundo branco do rótulo | 113 | 74 |
| Corpo liso do frasco | 131 | 88 |
| Tampa | 142 | 94 |
| Mesa | 161 | 116 |

As medições são medianas aproximadas na escala de 0 a 255. A leitura da tipografia pequena e dos frisos da tampa deve ser verificada no JPEG a 100%.

## Regras de captura e acabamento

- Em Foto e Retrato, manter uma exposição já curta na prévia. Limitar tempos mais longos conforme a política de captura. Em Noite, usar a estimativa de movimento para distribuir o tempo entre quadros. [Google Night Sight](https://arxiv.org/html/1910.11336)
- Após a fusão, medir se as bordas foram preservadas também em pequenas regiões. Reduzir ruído com mais intensidade em superfícies lisas e limitar o realce de nitidez pelo ruído residual. Inspecionar a textura e os halos a 100%. [Adobe Camera Raw](https://helpx.adobe.com/in/camera-raw/desktop/using/sharpening-noise-reduction-camera-raw.html) · [Capture One](https://support.captureone.com/hc/en-us/articles/360002606917-Adjusting-sharpening-in-the-Sharpening-tool)
- Na exposição automática neutra, estimar uma máscara do sujeito a partir do ponto de foco, do rosto detectado ou do centro em pouca luz. Medir os tons dentro da máscara. Aplicar ganho de até 0,65 EV na foto e até 0,5 EV com rosto, com o mesmo multiplicador em RGB linear, limitado pelas altas luzes e pelo nível de ruído nos pretos. A luminosidade do segundo plano fora da máscara fica como na entrada dessa etapa.
- Conferir contraste e forma das bordas após o ganho. Tentar metade do ganho se as métricas caírem além dos limites e preservar a entrada se a segunda tentativa falhar. A foto segue sem correção de luz automática quando a seleção do sujeito é incerta. O controle manual Primeiro Plano do editor permanece disponível.

No ensaio de software sobre o JPEG Codex de referência, a máscara foi desenhada no PC para isolar a curva de luz. O ganho calculado foi de 0,47 EV. Na área lisa do frasco, a luminância média subiu de 88 a 100. O contraste das bordas do texto fino aumentou cerca de 14% e os frisos retiveram cerca de 96% do contraste medido. A região amostrada no fundo conservou a mesma luminância e o mesmo contraste. Essas medições antecedem o ensaio da máscara automática no Reno.

## Reserva de fusão por focal

Uma captura anterior da tele física em 3,5× levou 19,7 s na etapa de fusão. O verificador registrou 0,66 de detalhe relativo ao quadro de referência e reprovou a saída de 6144 × 4608. A lista de quadros já tinha sido liberada e o app entregou um quadro único com limpeza leve. A partir da 1.27.4, o verificador pode selecionar a fusão na grade de captura feita pelo mesmo alinhamento. O JPEG preserva a resolução dessa fusão ou o redimensionamento de entrega já previsto para os recortes, identificado na ficha. A ficha diferencia a super-resolução tentada da efetivamente usada. O quadro único fica como reserva se as duas fusões perderem detalhe. [Google Research sobre super-resolução com quadros deslocados](https://research.google/pubs/handheld-multi-frame-super-resolution/)

## Tempo entre disparo e JPEG

Uma captura de 3,5× da versão 1.27.3 na tele física usou 12 quadros a 1/60 s. A super-resolução tomou 13,1 s, a limpeza residual 8,0 s e a etapa final de nitidez excedeu a memória disponível. A 1.27.4 reaproveita as posições de amostragem e os pesos em grades de proporção regular, calcula as distâncias entre regiões com somas corridas e aplica o mesmo filtro de nitidez em faixas com margens sobrepostas.

Com a grade nativa e uma rajada sem deslocamentos fracionários úteis nas bordas, o modo máximo entrega a fusão na resolução de captura. A saída de 28 MP é avaliada quando aparecem amostras distintas. A ficha registra o tempo de foco por toque, o primeiro resultado de prévia no zoom solicitado, o tempo até a fusão, o acabamento regional e a publicação do JPEG. A meta de cerca de 10 s é medida da ação no obturador até a publicação, incluindo a aquisição. Em modos de exposição longa, registrar o tempo de exposição selecionado separadamente.

Em um ensaio no PC com oito quadros derivados do mesmo JPEG de 3,5×, a super-resolução passou de 1,62 s a 1,14 s, a limpeza residual de 1,15 s a 0,82 s e a nitidez de 0,68 s a 0,28 s. As saídas de limpeza e nitidez coincidiram byte a byte nas etapas comparadas. A maior diferença de luminância na super-resolução foi de um nível em 255, em cerca de 0,05% dos pixels, com crominância idêntica. Os tempos de PC cobrem as etapas isoladas.

No Reno, a 1.27.4 levou 21,0 s para publicar a foto de 28 MP em 3,5×. A etapa de super-resolução ficou em 3,3 s e a nitidez final terminou em 0,88 s. O resultado de prévia em 3,5× chegou em 188 ms. A captura de 8× registrou 521 ms de foco ao toque e 446 ms até o resultado no novo zoom.

## Teste do boné em zoom longo

| Zoom | JPEG publicado na 1.27.4 | Decisão da fusão | Observação visual |
|---|---:|---|---|
| 6× | 16,6 s | Quadro único limpo | Grão visível no painel sombreado sob a mesa |
| 7× | 12,5 s | Fusão na grade de captura | Sombra mais uniforme e bordado visível |
| 8× | 13,7 s | Quadro único limpo | Grão de luminância e cor na sombra e no tecido |

Na 1.27.5, a reserva reaproveita a fusão nas sombras lisas quando o tom coincide com o quadro de referência. A limpeza mais forte tem uma máscara de sombra com transição gradual e exclui a área de rosto detectada, mesmo na parte escura da pele. Em saídas ampliadas com pouco ruído fino, a correção residual é calculada na grade de captura e transferida com proteção de bordas. Para cenas em que a medição já convergiu na prévia, o app pode reaproveitá-la e ainda esperar a confirmação de foco quando necessária.

Na mesma versão, a foto de referência do tecido em 6× levou 8,9 s. A tele falhou em confirmar o foco e a captura usou a principal com recorte de 682 × 512 antes da reconstrução. A foto mais aberta, pela tele direta, levou 18,8 s, dos quais 6,5 s foram gastos numa tentativa de super-resolução que o verificador rejeitou. A trama diagonal, os fios do bordado e os pelos visíveis na foto próxima são as referências para a inspeção de textura.

O controle de desfoque em zoom longo começa em f/11 simulado quando ligado. Usa o ponto de foco registrado no disparo, mantém o assunto e a frente em foco e grava uma cópia com desfoque atrás do plano escolhido. A cópia de 6× suavizou a caixa ao fundo e preservou o bordado. A profundidade, as camadas e a aplicação do desfoque somaram 11,6 s adicionais nessa captura.

Na 1.27.6, a fusão usa a grade disponível quando o ISP já entregou o recorte do zoom. O acabamento por região atua antes da ampliação de entrega. Se a limpeza das sombras afetar o detalhe de luminância, o verificador pode manter a limpeza de cor com a luminância original. O toque passa a pedir apenas a região de AF. A exposição segue a medição automática da cena ou a trava escolhida, com ajuste pelo controle EV. A ficha registra as regiões de AF e AE separadamente. [Android Camera2](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_AE_REGIONS)

No teste de 6× da 1.27.6, o toque conservou 1/60 s, ISO 7664 e EV 0 na prévia. A luminância média do trecho amostrado do tecido variou menos de um nível em 255. A ficha confirmou o foco em 491 ms e mostrou uma região de AF separada da medição AE. A foto na tele direta teve 7.139.364 bytes em 4096 × 3074, com 0,48 s até o primeiro quadro, 1,32 s até o último e 17,05 s até o JPEG. A super-resolução levou 6,26 s e foi descartada por duas regiões reprovadas. A versão publicada preservou o bordado, com granulação ainda visível no tecido e na sombra.

O JPEG anterior em 6× com menos grão usou a fusão na grade de captura e teve 4,32 MB. A captura mais granulada precisou usar um quadro de referência apesar de receber 12. A 1.27.7 tenta manter a fusão quando a falha está localizada. Repõe as regiões reprovadas do quadro de referência com transição gradual e mede novamente o resultado inteiro. Se a perda for disseminada, conserva o quadro de referência e pode aplicar uma correção cromática leve também nos meios-tons, protegendo o rosto detectado. A limpeza forte de luminância continua restrita às sombras.

Na 1.27.7, a tele direta aprovou a fusão em 6× e 8×, com JPEGs de 3,37 MB em 18,72 s e 3,31 MB em 13,20 s. O disparo de 10× mais instável teve deslocamento estimado de 150 pixels, voltou ao quadro único e manteve grão visível. A repetição teve deslocamento de 10 pixels e recuperou uma região a partir da referência, em 8,46 s e 3,64 MB. O bordado e a aba ganharam definição, com uma diferença visível de grão perto da caixa no fundo.

Na 1.27.8, a recuperação do quadro de referência usa um mapa de estrutura para atuar perto das bordas reprovadas. As superfícies lisas da mesma região aproveitam a fusão quando o verificador confirma a imagem composta. Em Foto com Máx detalhe, pouca luz e a tele direta estável em 10×, a rajada pode passar de 12 para 16 quadros. O app copia da entrada YUV só o recorte necessário à captura, com as mesmas coordenadas e alinhamento dos canais do recorte anterior. O plano de 8× mantém os 12 quadros nas condições do ensaio.

De 3,5× a 7×, a otimização do recorte e das coordenadas de super-resolução mantém o plano de quadros e o método de reconstrução. Em três execuções no PC com 12 quadros derivados do JPEG de 6× e saída 4096 × 3074, a etapa de super-resolução passou de 0,92–1,26 s para 0,45–0,85 s. Os três planos YUV coincidiram byte a byte. Esses tempos medem a etapa isolada no PC.

### JPEGs conferidos no Reno com a 1.27.8

| Focal | Quadros recebidos | Disparo até JPEG | Arquivo | Resultado da fusão |
|---|---:|---:|---:|---|
| 3,5× | 12 | 16,08 s | 6,15 MB | Fusão com recuperação de bordas em três regiões. Bordado legível, com manchas localizadas de grão nas sombras. |
| 6× | 12 | 16,65 s | 3,33 MB | Super-resolução aprovada. Trama, costura e bordado visíveis. |
| 7× | 12 | 8,17 s | 3,74 MB | Fusão na grade de captura aprovada. |
| 10× | 16 | 7,13 s | 3,42 MB | Fusão dos 16 quadros na grade de captura aprovada. Costura definida e sem a área retangular de grão do teste anterior. |

São capturas individuais em Máx detalhe com a tele direta. No disparo de 6×, a etapa de super-resolução levou 3,80 s, ante 6,23 s na foto da mesma cena da 1.27.7, com pequenas diferenças de luz e posição. A amostra nova confirmou foco ao toque em 467 ms, com AF e AE registrados em regiões separadas. Em 10×, o primeiro quadro chegou em 0,46 s e o último em 1,57 s. O arquivo de 4096 × 3070 saiu a partir da fusão na grade do recorte e da ampliação de entrega. Permanece grão fino na sombra. A meta de aproximadamente 10 s segue pendente em 3,5× e 6× em Máx detalhe. Retomar pelo grão localizado após recuperação de bordas em 3,5× e pela medição repetida de acabamento por região. Retratos humanos e o tempo completo dos demais modos requerem validação física.

## Recuperação de detalhe em sombras na 1.27.9-codex

A retomada partiu do commit `b968fe6` com o pacote Codex 1.27.8-codex, versionCode 74. O JPEG anterior do boné em 3,5× apresentou manchas de grão em áreas escuras depois da recuperação local de três regiões. A ficha registrou ISO 7118 e rejeição da limpeza posterior de luminância. Os quadros intermediários dessa captura não estavam disponíveis.

O teste de regressão acrescenta uma mancha de 4 × 4 pixels à sombra na mesma célula que perde contraste na fusão. A 1.27.8 repôs a mancha (Y 76), e a 1.27.9 manteve a sombra fundida (Y 42), restaurou os traços e passou no verificador. Um segundo teste desloca um contorno de cor e verifica a correção no canal que divergiu, incluindo o interior da faixa deslocada. Cada canal usa sua própria máscara. A referência continua disponível se a recomposição não passar.

No PC, o filtro de sombras foi medido em sete repetições após quatro aquecimentos, em YUV 4096 × 3072 derivado do JPEG anterior. Os planos Y, U e V produziram o mesmo SHA-256 nas duas versões. A mediana do filtro caiu de 387 para 362 ms. A verificação isolada caiu de 89 para 62 ms, com métricas idênticas. Esses tempos abrangem duas etapas isoladas em bancada.

### Ensaio físico no Reno16 Pro em 12/11/2026

A 1.27.9-codex, versionCode 75, foi instalada sobre a 1.27.8-codex e conferida pelo SHA-256 do APK instalado. As capturas foram feitas em Foto, Máx detalhe, EV 0, com o frasco e o fundo escuro.

| Focal | Caminho de captura | Disparo até JPEG | Tamanho | Observação visual |
|---|---|---:|---:|---|
| 3,5× | Tele direta e fusão na grade de captura | 17,89 s | 4,29 MB | Frasco inteiro, com perda de legibilidade nas letras menores. |
| 3,5× | Tele direta e foco contínuo, 12 quadros recebidos | 14,92 s | 8,17 MB | Frisos e letras legíveis. Sombra com grão visível. O verificador recorreu à referência nas bordas e registrou peso médio 0,02 de fusão nas sombras. |
| 6× | Tele direta, 12 quadros recebidos | 12,88 s | 2,47 MB | O AF confirmou em 565 ms. O rótulo saiu desfocado. |
| 6× | Câmera lógica em reserva, 12 quadros recebidos | 4,13 s | 3,85 MB | O rótulo também ficou desfocado. A grade de referência foi 682 × 512 antes da ampliação de entrega. |

Na captura física de referência da 1.27.8 em 3,5×, o rótulo saiu parcialmente fora do quadro e desfocado. A prévia posterior mostrou a mesma composição. As posições variaram entre capturas. O ensaio permite documentar foco, enquadramento e latência individuais, com comparação controlada de ruído e foco por repetir. O caso de manchas reintroduzidas na recuperação local foi reproduzido e corrigido em bancada. As capturas físicas acima seguiram outros caminhos de reserva ou fusão. O foco ao toque, a limpeza das sombras quando prevalece a referência e a meta de aproximadamente 10 s continuam pendentes.

## Reserva de sombras e foco ao toque na 1.27.10-codex

A retomada partiu do commit `1e2afb9`, versão 1.27.9-codex, código 75, com hash do APK local confirmado no Reno. No frasco em 3,5×, a captura nítida anterior usou pouco da fusão nas sombras. O verificador recusou duas limpezas posteriores de luminância. No código, um extremo entre nove blocos vizinhos podia vetar todo o trecho, inclusive numa sombra plana.

A reserva agora compara níveis médios e contraste direcional em regiões alinhadas. A crominância tem sua própria verificação de compatibilidade e a validação final continua protegendo bordas e rosto. O teste sintético de 4096 × 3072, após quatro aquecimentos e sete medições por versão, reduziu o erro RMS de luminância na parede de 4,42 para 0,38 níveis Y e recuperou os oito níveis de contraste dos frisos escuros. A etapa isolada passou de 51 para 101 ms no PC. A regressão unitária também protege a posição de bordas cromáticas isoluminantes e reproduziu a falha na base.

Na tele direta, a primeira falha explícita permite tentar uma região maior. Se a tentativa não convergir ou a tele exceder o tempo da busca, o app pode usar a câmera lógica e refazer o foco no ponto tocado. Um disparo que já aguardava o foco continua após a mudança. No firmware Reno observado, uma queda forte de nitidez num alvo estável pode rejeitar a confirmação do AF travado. Essa comparação exige uma região inicialmente texturizada e dados válidos de movimento da prévia. A exposição mantém o valor pedido pelo usuário em EV, com a quantização da lente em uso. O sensor físico escolhido pela câmera lógica deve ser conferido na ficha.

## Aplicação da pesquisa de 11/11/2026

- **Latência por etapa.** As fichas registram firmware, tempo até a chegada do primeiro e último quadros e tempo até a publicação do JPEG. O timestamp do sensor fica em campo separado. Essa leitura permite distinguir espera na câmera, processamento e gravação em cada modo. Os percentis exigem séries repetidas com lente, modo e luz identificados.
- **Ruído e cor por lente.** O RAW+ já lê níveis de preto e branco, ganhos, matriz, mapa de sombreamento e perfil de ruído quando entregues. O próximo ensaio compara patches lisos em vários ISOs por lente física, com a exposição fixa para cada par. No JPEG/YUV, medir o resíduo após o ISP, pois a curva RAW descreve outro domínio. Usar cartão cinza e carta de cores antes de aplicar correções por iluminante. [Perfil de ruído da Camera2](https://developer.android.com/reference/android/hardware/camera2/CaptureResult#SENSOR_NOISE_PROFILE) · [Mosleh et al., CVPR 2026](https://arxiv.org/abs/2603.12493)
- **Zoom e foco.** Manter a validação da fusão local em 6×, 7×, 8× e 10×. Comparar o quadro de referência e a fusão com o mesmo enquadramento, verificando letras, trama, pelos e sombras. O remosaico depende de acesso comprovado pelo firmware. O exemplo público CameraUnit lista suporte até a geração Reno6 na linha Reno. [Google Research](https://research.google/pubs/handheld-multi-frame-super-resolution/) · [OPPO CameraUnit](https://github.com/oppo/CameraUnit#readme)
- **Exportação.** A saída preserva as dimensões e os metadados do caminho de captura. O app solicita qualidade 100 no JPEG direto e usa a mesma qualidade nas cópias do editor. Em YUV, quando a qualidade 97 produz até 2.000.000 bytes, o app tenta qualidade 100 a partir do YUV. A ficha registra a qualidade do codificador YUV e o tamanho do JPEG final. Uma imagem com pouca variação pode ficar abaixo de 2 MB mesmo na qualidade máxima. [Qualidade JPEG na Camera2](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#JPEG_QUALITY)
- **Aceleração experimental.** Medir GPU/NPU e modos ZSL em ensaios próprios. O estudo Mobile AI 2026 mede Full HD no Snapdragon 8 Elite Gen 5 e no Dimensity 9500. A confirmação do desempenho em fotos de 12–28 MP requer medição no Reno. [Mobile AI 2026](https://arxiv.org/abs/2611.00974)

## Ensaio comparável por focal

1. Capturar um alvo com texto fino, bordas inclinadas, superfícies foscas, texturas e carta de cor em 0,6×, 1×, 2×, 3,5×, 6×, 7×, 8× e 10×. Repetir três vezes sob luz de dia, luz interna e baixa luz, com tripé e à mão. Anotar a iluminação, a distância, a compensação e o sensor físico efetivamente usado, inclusive quando o zoom é recorte da principal.
2. Comparar os JPEGs gravados pelo Codex, pela versão anterior e pelo app OPPO com o mesmo tamanho do assunto no quadro e a mesma orientação. Medir SFR e MTF50P nas bordas inclinadas, incluindo a altura dos halos, e conferir separadamente a leitura da menor linha de texto e a separação dos frisos. [ISO 12233](https://www.iso.org/cms/live/live/en/sites/isoorg/contents/data/standard/08/86/88626.html?browse=tc) · [Imatest](https://www.imatest.com/imaging/sharpness/) · [FADGI](https://www.digitizationguidelines.gov/guidelines/digitize-technical.html)
3. Medir ruído nas áreas lisas do assunto e do fundo, luminância de meios-tons e altas luzes e erro de cor ΔE00 na carta. Conferir tom de pele, cores e textura no mesmo monitor e registrar o tempo até o JPEG ficar disponível. Usar a reprodução tonal e a consistência de cor como critérios de referência. [ISO 15739](https://www.iso.org/obp/ui?_escaped_fragment_=iso%3Astd%3Aiso%3A15739%3Aed-4%3Av1%3Aen) · [Hasselblad Natural Colour Solution](https://www.hasselblad.com/learn/hasselblad-natural-colour-solution/) · [Phase One XF IQ4](https://www.phaseone.com/applications/bespoke-photography/studio-photography-xf-camera-system/features-and-specifications/)

O verificador interno mede a preservação de bordas em relação à entrada de cada etapa. A resolução óptica, o ruído, a cor e a legibilidade do aparelho são medidos no ensaio físico.
