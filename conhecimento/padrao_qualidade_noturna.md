# Câmera X · padrão de qualidade noturna

## Referências e escopo

O **OPPO Find X8 Ultra** é a referência visual para textura, nitidez e ruído nas sombras. Na foto do pé, avalie pelos, vincos, contorno das unhas e ruído no fundo liso. O **Câmera X aprovado por Duvier** é a referência para cor real, tom de pele e aparência da luz ambiente. No rótulo, preserve letras pequenas, linhas do código de barras e a trama do tecido atrás. Use também as referências de jardim em 7× registradas no [AGENTS.md](../AGENTS.md) para conferir textura diurna.

Este protocolo vale para **Foto, Foto com Máxima e Noite**, em todas as lentes que o Câmera X expõe. Teste cada lente traseira do Reno16 Pro no ponto nativo e os recortes usuais de 5×, 7× e 10×. Registre 6×, 8× e 9× ao avaliar a transição do zoom. Para outras versões de aparelho, mapeie as câmeras físicas disponíveis e os pontos nativos separadamente. Confirme a lente no resultado da captura, especialmente quando foco próximo ou luz insuficiente acionarem outra lente. Use também a câmera original do mesmo aparelho para distinguir uma diferença de processamento de uma diferença de hardware. [OPPO Reno16 Pro](https://www.oppo.com/en/smartphones/series-reno/reno16-pro/specs/) · [OPPO Find X8 Ultra](https://www.oppo.com/en/newsroom/stories/oppo-find-x8-ultra-rewriting-photography-rules/)

## Régua de pontuação v1

Os pesos, metas e limites abaixo são **valores internos iniciais**, escolhidos para o Câmera X. A calibração exige fotos pareadas no aparelho. As fontes orientam o método de medição e a escolha dos atributos.

| Ordem | Critério | Peso | Meta | Limite | Unidade | Fonte ou referência |
| --- | --- | ---: | ---: | ---: | --- | --- |
| 1 | Textura de baixo contraste | 22 | 1,00 | 0,90 | razão | Acutância de textura do candidato ÷ X8 Ultra. Use alvo Dead Leaves Cross com o mesmo método de correlação nos dois. |
| 2 | Ruído de cor nas sombras | 18 | 0,95 | 1,10 | razão | σC do candidato ÷ X8 Ultra. σC é a média quadrática do desvio residual de Cb e Cr nos alvos uniformes. |
| 3 | Grão de luminância nas sombras | 12 | 1,00 | 1,10 | razão | Desvio residual de Y′ do candidato ÷ X8 Ultra nos mesmos alvos. |
| 4 | Fidelidade de cor | 12 | 2,0 | 3,0 | ΔE00 | Mediana dos 18 campos cromáticos contra as cores de referência do ColorChecker sob o iluminante medido. |
| 5 | Tom de pele | 10 | 1,5 | 2,5 | ΔE00 | Mediana nos recortes de pele pareados em relação à imagem aprovada do Câmera X. Use pele clara, média e escura em áreas de brilho semelhante e fora de realces especulares. |
| 6 | Nitidez medida | 10 | 1,00 | 0,90 | razão | MTF50P do candidato ÷ X8 Ultra, em LW/PH no quadro de escala comum. Avalie bordas de contraste moderado no centro e na periferia. |
| 7 | Legibilidade do rótulo | 8 | 0 | 0,05 | fração | CER do OCR, calculada por (substituições + inserções + exclusões) ÷ caracteres da transcrição. Os campos críticos devem ter correspondência exata. |
| 8 | Velocidade de entrega | 8 | 1,00 | 1,10 | razão | Maior valor entre P50 novo ÷ P50 aprovado e P95 novo ÷ P95 aprovado. Use a mesma lente, modo, cena e condição térmica. |

**Cálculo.** A nota por critério vale 100 na meta e 70 no limite, por interpolação linear. Use a expressão `max(0, min(100, 70 + 30 × (medição − limite) ÷ (meta − limite)))`. A nota final é a média ponderada pelos pesos. **A** vale de 95 a 100, **B** de 85 até abaixo de 95, e **C** de 70 até abaixo de 85. Qualquer limite individual estourado ou reprovação visual resulta em **Reprovado**, independentemente da média. Medida ausente, amostra sem par ou referência sem calibração fica **Pendente**. O ranking de versões usa a menor nota aprovada entre os cenários obrigatórios. Registre também a mediana para ver tendência.

**Condições eliminatórias.** Foto aprovada tem foco no assunto e lente efetiva documentada. Deve manter a resolução prometida, a leitura exata dos campos críticos e pele com textura. Reprove fantasmas, halos visíveis, pele encerada, textura inventada e manchas cromáticas. Compare os campos cinza escuros ao Câmera X aprovado sob a mesma luz, com diferença mediana de luminância `|ΔL*| ≤ 3`. Nos campos claros, pixels recortados podem aumentar no máximo **1 ponto percentual** em relação à base. A nitidez alta só conta quando o alvo de textura e a verificação visual também passam.

Para desenvolvimento, adote inicialmente estes objetivos absolutos de **P50/P95 do disparo ao JPEG disponível** em cenas na mão de 3 a 10 lux. Foto **2,5/4,0 s**, Máxima **4,0/6,0 s**, Noite **5,0/8,0 s**. Compare com as medições do aparelho para calibrar esses objetivos. O limite relativo de **+10%** do ranking vale separadamente para P50 e P95 em relação à versão aprovada na mesma condição.

## Como medir

1. Use **100 lux** para regressão em luz comum e **10, 3 e 1 lux** para pouca luz. Meça no plano do assunto. Comece com LED quente estável (~2.850–3.000 K), depois repita 10 lux sob LED neutro (~4.000 K). Registre lux, CCT, versão do sistema, lente, zoom, modo, distância, posição de foco, EV, flash e movimento. Faça uma série apoiada e outra na mão. Alterne apps e versões para minimizar mudança de luz e temperatura.
2. Monte uma cena com escala cinza, ColorChecker, borda inclinada, Dead Leaves Cross e um rótulo fixo. Fotografe também pele, tecido e cena com movimento leve. Faça **5 disparos** por célula para qualidade, use a mediana das medições e verifique os cinco JPEGs para falhas. Faça **20 disparos ou mais** por célula de latência e anote P50 e P95 por `PERCENTILE.INC`. Nas cenas controladas, use desfoque desligado.
3. Guarde JPEG, EXIF e ficha originais. Confira 100% nativo. Para métricas comparativas, alinhe a mesma área de cena e use escala comum de até **3.000 px** no lado maior, com a mesma reamostragem e sem ampliar uma fonte menor. Converta a mesma representação SDR pelo perfil ICC para o teste de cor. Um screenshot HDR ou um recorte exportado serve de referência visual.
4. Meça ruído no interior dos campos cinza escuros com **5%, 10% e 18% de refletância**, sem bordas. Na medição interna, use Y′CbCr BT.601 em 8 bits e tire um plano de brilho de cada canal. Calcule o desvio padrão residual em códigos de 8 bits por canal e campo. Combine Cb/Cr por `sqrt((σCb² + σCr²)/2)` e use a mediana das razões por campo, com a versão da fórmula registrada. Valores de referência iguais a zero ficam sem razão válida. As medições internas em códigos de 8 bits são distintas do ruído visual ISO em JND.
5. Use a mesma versão do programa de MTF50P, análise de textura e OCR para todas as imagens. Na cor, use CIEDE2000 com iluminante/valores do alvo e cor gerenciada. Na legibilidade, fixe transcrição, normalização do espaço e campos críticos. No rótulo de referência, inclua nome, concentração, volume, lote e validade.
6. Compare o **quadro de referência**, a **fusão antes do acabamento** e o **JPEG final**, com a mesma escala. Registre `quadros_pedidos`, `quadros_recebidos`, `quadros_usados`, rota de captura, zoom real, ISO, exposição, modo pixel, OIS, decisão da super-resolução, valores de detalhe/ruído/fusão e `tempo_ate_jpeg_disponivel_ms`. O tempo principal começa no toque do obturador e termina quando o arquivo foi publicado. Meça com diagnóstico de cópias desligado.

## Leitura inicial dos anexos

Os JPEGs 68315, 68317 e 68313 têm **1.153 × 1.536 px** e vieram sem modelo, lente, ISO ou exposição úteis no EXIF. Nos dois primeiros, o grão também aparece no fundo e sobre a pele. No terceiro, pelos e linhas da pele aparecem mais separados do grão. Os dois prints usados na comparação do Find X8 Ultra vieram em Display P3 com mapa de ganho HDR. As imagens do rótulo dão a âncora de legibilidade em dois enquadramentos.

Como verificação exploratória das três cópias de 1.153 × 1.536, use a faixa vertical de fundo em `x=472…517, y=84…201`. Em Y′ de 8 bits, o indicador de variação de alta frequência `1,4826 × MAD(TL − TR − BL + BR) / 2` com vizinhos de um pixel deu **9,64**, **9,64** e **2,22**, respectivamente. São valores descritivos das cópias anexadas, com pequenos deslocamentos de enquadramento e possível compressão. As notas do ranking aguardam originais pareados e medição controlada.

## Versões, parâmetros e reversão

| Versão | Commit local | Uso na comparação |
| --- | --- | --- |
| 0.1.2-teste.1 | `cb0ae0a` | Base histórica aprovada para 1×, 3× e 3,5×. Qualidade noturna por lente ainda sem nota documentada. |
| 0.1.11-teste.1 | `42ca1be` | Referência aprovada de contraste e luminância em 7×. |
| 0.1.15-teste.1 | `650301f` | Base de código para repetir a regressão. Padrões de captura em **Detalhe 55, Redução de ruído 60, Fusão 70**. Tele longa em **Realces 55, Definição 65**. |

Para cada modo e lente, arquive o **APK assinado**, commit, SHA-256 do APK, modelo/firmware, configuração usada, JPEGs de referência e resultados por cenário. Identifique sempre a versão aprovada para aquele atributo. Os controles de Ajustes persistem, então fotografe com os valores conferidos e compare a ficha. Alto nível de ruído no JPEG direto deve ser diagnosticado pela rota e pelo modo do sensor antes de mexer no acabamento da fusão.

Quando houver regressão, reproduza com a configuração aprovada. Volte primeiro aos níveis registrados para aquele perfil na tela Ajustes. Se a regressão for do código, reverta o commit da mudança em uma branch de teste e repita as cenas. Para reinstalação por cima no Android, gere nova versão de teste assinada pela mesma chave, com o mesmo pacote e `versionCode` superior. A regressão só está corrigida quando a nova medição recupera os limites no aparelho. Guarde fotos pessoais, fichas e chaves fora do Git público.

### Ensaio de redução de ruído v1

Em **Ajustes → Detalhe e ruído**, o **Teste de ruído em baixa luz** começa desligado. Ao ligar, o perfil de ensaio atua no YUV escolhido para o JPEG em Foto, Máxima e Noite, incluindo a saída de um único quadro quando a fusão é rejeitada. Também vale para Máxima no Retrato. Use as mesmas regulagens para os dois disparos. Desligue para restaurar o tratamento da 0.1.15. O JPEG direto, RAW e Rua seguem as rotas próprias.

Ativação inicial com redução de ruído acima de zero. Use ISO equivalente (ISO × ganho pós-RAW ÷ 100) de pelo menos **1.600**, ou equivalente de pelo menos **800** com exposição de **25 ms** ou mais. O perfil usa o ruído de brilho por faixa da imagem, protege estruturas com apoio nos vizinhos e reduz croma nas sombras com uma janela contígua de 5 × 5 na grade de cor. O teste compara o acabamento com a mesma captura e mantém a política de exposição e número de quadros da base. A ficha registra `perfil_ruido`, ISO, ganho e tempos.

Em uma cena sintética replicável em PC com sombra neutra, pele e traços finos, o perfil reduziu o indicador local de grão de Y′ em **18%** e de Cb em **45%** na comparação com o acabamento anterior. O traço reteve **98%** do contraste medido. Esses resultados unitários aguardam validação de textura, cor e tempo no Reno16 Pro.

Para localizar a etapa da perda, use **Salvar etapas da fusão** no mesmo painel. Esse diagnóstico é opcional e guarda duas cópias auxiliares junto à foto final. Faça as séries de tempo com o diagnóstico desligado.

## Fontes técnicas

- [DXOMARK · protocolo de câmeras de smartphone](https://corp.dxomark.com/smartphone-camera/) e [teste Find X8 Ultra](https://www.dxomark.com/oppo-find-x8-ultra-camera-test/). Atributos separados e comparação de fotos em condições de luz e movimento.
- [VCX Forum · PhoneCam Test](https://vcx-forum.org/standard/phonecam-test) e [Image Engineering · medição de perda de textura](https://www.image-engineering.de/en/texture-loss). Sequência de baixa luz e aferição de textura distinta de definição de contorno.
- [Imatest · nitidez](https://docs.imatest.com/25.1/sharpness), [alvos de textura](https://www.imatest.com/test-targets/texture/) e [Colorcheck](https://docs.imatest.com/docs/colorcheck). MTF50P reduz o incentivo ao excesso de nitidez. Alvos de baixo contraste e ΔE00 verificam textura e cor.
- [DPReview · Studio Test Scene](https://www.dpreview.com/articles/2601653565/studio-test-scene/). Cena repetível, detalhe fino, texturas suaves e comparação em boa e baixa luz.
- [ISO 12233:2024](https://www.iso.org/standard/88626.html), [ISO 15739:2023](https://www.iso.org/standard/82233.html) e [ISO 19093:2018](https://www.iso.org/standard/63942.html). Referências para resolução, ruído e desempenho em baixa iluminação, respectivamente.
- [Google Research · Burst photography for HDR and low light](https://research.google/pubs/burst-photography-for-high-dynamic-range-and-low-light-imaging-on-mobile-cameras/) e [Handheld Multi-Frame Super-Resolution](https://research.google/pubs/handheld-multi-frame-super-resolution/). Alinhamento e fusão reduzem ruído temporal. Super-resolução depende de amostras úteis e tratamento de movimento.
