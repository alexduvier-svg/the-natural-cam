# Câmera X — prioridades de desenvolvimento

Estas diretrizes valem para a branch `camera-x` e têm prioridade sobre as notas históricas do The Natural Cam. O app Android fica em `ClaraCam/`, com identificador `br.com.duvier.camerax`. Preserve o identificador, a assinatura e a possibilidade de instalar novas versões por cima.

## Referência fotográfica aprovada

A versão **0.1.2-teste.1** é a referência aprovada em fotos do OPPO Reno16 Pro em 14/11/2026. O usuário aprovou o **1× inclusive ampliado**, o **3× com a legibilidade melhorada** e a qualidade do **3,5×**. Preserve o APK de referência. Use os JPEGs originais e suas fichas quando estiverem disponíveis. Mantenha fotos pessoais, fichas de captura e credenciais fora do Git. A troca de cena com foco preso foi reportada nessa mesma rodada e deve ser testada em versões seguintes.

A versão **0.1.5-teste.1** é a referência aprovada para a identificação do assunto e o contorno das folhas no desfoque das fotos de jardim em **3,5×, 5× e 7×**. A aprovação cobre o recorte e a transição do desfoque. A cor e a tonalidade dessas fotos continuam em avaliação.

A versão **0.1.11-teste.1** é a referência aprovada para contraste e luminância em **7×** nos modos Rua, Foto e Foto com Máxima. Nitidez, textura e controle de ruído nessa versão foram reprovados. Confira esses atributos separadamente do acabamento tonal.

Use o [padrão de qualidade noturna](conhecimento/padrao_qualidade_noturna.md) em mudanças de Foto, Máxima e Noite: mantenha as referências do Find X8 Ultra para textura e ruído e do Câmera X para cor e pele. Registre os valores por versão e aplique os limites de aprovação antes de promover mudanças de captura ou acabamento.

## Meta visual da tele de 5× a 10×

As referências do jardim **67950.jpg** e **67951.jpg**, enviadas em 14/11/2026, são prints ampliados de fotos feitas em **7×**, conforme informado por Duvier. São a régua visual para toda a faixa de **5× a 10×**, inclusive os pontos intermediários, em Foto, Máxima, Rua e Retrato. Os anexos recebidos têm 698 × 1536 px e não registram lente ou exposição no EXIF. Use os JPEGs originais para medir resolução, ruído e qualidade óptica.

| Região de referência | Resultado exigido |
| --- | --- |
| Folha rosa e verde em 67950 | Nervuras principais e secundárias legíveis, centro rosa e borda verde distintos, brilho úmido localizado e borda sem halo. |
| Tronco, pedra e folhas pequenas em 67950 | Manchas e relevo da casca e da pedra visíveis, talos finos separados do fundo, flores vermelhas com gradação. |
| Vaso, musgo, pedra escura e folhas em 67951 | Sombras profundas com textura onde há sinal útil, pouco ruído cromático e contraste local que distingue verde, pedra e fundo. |

- Compare OPPO nativo, fusão do Câmera X e JPEG direto da tele em **5×, 6×, 7×, 8×, 9× e 10×**, com a mesma posição, luz e ponto de foco. Faça três disparos por configuração para observar consistência. Inclua jardim parado, folhagem em movimento e retrato. Use desfoque desligado na comparação de textura e recortes à mesma escala.
- Confirme lente física, recorte, resolução de entrada, quadros pedidos, recebidos e usados, decisão da super-resolução e tempo até o JPEG. Teste alta resolução se a tele a expuser. Uma rota só vira padrão após demonstrar mais detalhe real ou menos ruído com enquadramento equivalente no Reno16 Pro.
- Compare quadro de referência, imagem antes do acabamento e JPEG final para localizar perdas. Preserve o quadro mais nítido nas regiões móveis. Reconstrua detalhes das regiões estáveis quando a rajada fornecer amostras úteis. Mantenha sombras limpas sem alisar nervuras, casca, musgo ou pele. Reprove halos, textura inventada, cor lavada, fantasmas e queda de legibilidade.

## Critérios de aceitação de cada atualização

- **Qualidade da imagem.** Preserve cores reais, balanço de branco natural, contraste e realces, nitidez e legibilidade de letras pequenas. Preserve fibras, cabelo e pele. Reprove halos, textura inventada, aparência plástica, granulação realçada, fantasmas e redução silenciosa de resolução.
- **Sombras.** Controle o ruído cromático e o grão nas áreas lisas. Preserve a textura nas zonas escuras e os detalhes em pele, cabelo, tecido e rótulos. Um resultado mais liso só conta como melhora se conservar esses detalhes.
- **5× e 7×.** Compare os mesmos traços do assunto em recortes de escala igual. Busque legibilidade, microcontraste e cor consistentes entre os dois zooms. Teste o balanço de branco em todas as lentes com alvo cinza sob a mesma luz e permita que acompanhe mudanças reais de iluminação. Use a temperatura ambiente, quando disponível e plausível, como referência auxiliar à leitura da câmera. Contenha apenas as altas luzes e conserve as sombras. A transição de desfoque deve ser gradual, com o plano em foco e os contornos finos preservados.
- **Desfoque e recorte.** O botão Desfoque em Foto e Retrato controla a intensidade antes do disparo e começa desligado. Grave uma única foto por disparo, com o efeito escolhido já aplicado. Preserve o plano em foco e o recorte aprovado em 0.1.5. Folhas finas, pontas, talos e os vãos entre eles devem conservar seus contornos. O fundo deve desfocar de forma gradual. Reprove halos, contorno duplo e desfoque que invade o assunto. Faça disparos separados com desfoque ligado e desligado, no mesmo ponto de foco, em 3,5×, 5× e 7×.
- **Cor na fusão.** Use o quadro de referência para conferir matiz, saturação e luminosidade do assunto. A fusão deve reduzir ruído e preservar textura, a cor sob a luz real e a diferença entre sol e sombra. Na cena de jardim, compare o verde e o centro rosa da folha frontal, o tronco e os reflexos ao sol. Contenha altas luzes localmente e preserve o nível das sombras. Compare os mesmos trechos com enquadramento, exposição e luz equivalentes.
- **Tele em pouca luz.** Use a edição de referência de 5× para avaliar a direção do acabamento também em 3,5× e 7×. Dose o contraste nos tons médios, contenha os realces e modere as cores intensas. Preserve o matiz, o balanço de branco e os detalhes reais nas sombras. Avalie as nervuras e a casca do tronco à mesma escala. Priorize a aparência da cena ao vivo.
- **Rua e Máxima em 7×.** Use as cores do modo Rua na cena de chuva como referência. Preserve a rapidez do disparo e o aspecto das nervuras, gotas, talos e contornos. Dê prioridade ao quadro de referência nas regiões móveis. Use as demais amostras para reduzir ruído e ganhar resolução nas regiões alinhadas. Compare folhagem parada e em movimento, no mesmo enquadramento.
- **Qualidade em 7×.** Aplique os mesmos critérios em Rua, Máxima e Retrato. Use a câmera da OPPO como referência no Reno16 Pro, com a mesma luz, plano de foco e enquadramento. Busque superioridade em textura real, naturalidade de cor, ruído e transição dos realces. Compare os JPEGs originais à mesma escala e verifique reflexos em folhas molhadas, nervuras, casca, pele e contorno do retrato.
- **Textura e contraste em 7×.** No Rua, use a rajada existente para reconstruir detalhe quando o alinhamento permitir. Confira nervuras finas e gotas à mesma escala. Em Foto e Máxima, preserve a diferença entre luz e sombra e o volume do assunto. Verifique textura de pele e ausência de halos sempre que alterar a definição.
- **Fusão e desempenho.** Use a política de quadros da 0.1.2 como referência, incluindo Máxima e Noite. Confirme quadros pedidos, recebidos e usados por lente e modo. Mudanças de quantidade, exposição, alinhamento ou limpeza precisam demonstrar ganho sem aumentar o tempo de captura ou pós-processamento. Compare a mediana e as capturas mais lentas em condições equivalentes. Meça do disparo ao último quadro e até o JPEG disponível, usando a ficha e os registros da câmera ou instrumentação no OPPO.
- **Foco.** O toque se aplica ao assunto escolhido. Com o cadeado aberto, o autofoco volta a acompanhar a cena após reenquadramento. A trava explícita e o foco manual continuam disponíveis. Ao trocar do rótulo para os pés e voltar, confira o foco no visor e no JPEG, com e sem Máxima.
- **Teste de regressão no aparelho.** Repetir a mesma cena e enquadramento em 1×, 3× e 3,5× e conferir também 5×–10× quando a área afetar o zoom. Incluir um rótulo, tecido/pele e sombras em pouca luz. Leia o rótulo em três fotos consecutivas, compare recortes de igual escala, lente física e modo efetivos e registre os tempos. Preserve o layout aprovado.
- **Entrega.** Testes automatizados e compilação autorizam identificar um APK como versão de teste. A aprovação fotográfica e de tempo depende das fotos e medições reais. Se faltar acesso ao aparelho, indique o que ainda precisa ser conferido. Se houver regressão, ajuste ou reverta a mudança responsável antes de promover a versão.

## Verificação local

Em `ClaraCam/`, rode `./gradlew --no-daemon assembleRelease testDebugUnitTest`. Verifique a assinatura do APK, o pacote e o código da versão. Use a branch do Câmera X para suas alterações e mantenha a base versionada acessível para comparações.
