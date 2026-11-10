# The Natural Cam — Diretriz fotográfica de 50 milhões de combinações

Versão 1.0.0 · 10/11/2026 · Identificador `tnc.photo-memory.v1`

## 1. Missão e alcance

Produzir fotografias naturais, com foco no sujeito escolhido, detalhe real, luz coerente, cor fiel e contraste legível. Preservar poros, cabelos, tecidos, letras, reflexos e transições suaves. A captura deve parecer uma boa fotografia, sem contornos artificiais nem aparência de pintura. Nitidez criativa adicional pertence ao editor.

Esta diretriz é uma memória local de referência, versionada, para orientar o app. Não é um modelo treinado, nem uma coleção de 50 milhões de fotografias ou receitas calibradas. Os 50 milhões representam o produto cartesiano de dez eixos. Algumas combinações são raras ou inviáveis e devem ser recusadas pelo controlador de capacidades, sem inventar resultados.

O catálogo JSON e este manual ficam em `app/src/main/assets/knowledge/` na compilação. `PhotoKnowledge` os lê sob demanda; `PhotoScenarioSpace` indexa as combinações. Na versão 1.20.0-codex, `CameraController` carrega esta base e registra as regras aplicáveis aos eixos conhecidos; `CaptureDirector` implementa limites de exposição por focal, nitidez moderada e quantidade de quadros. A consulta textual continua sendo referência, não código executável. Direção de luz, contraste e movimento não medidos ficam desconhecidos; não é atribuído um ID completo fictício. As demais regras só podem ser consideradas ativas quando implementadas e verificadas.

## 2. Hierarquia de decisão

Resolver conflitos nesta ordem:

1. Intenção explícita: sujeito tocado, foco manual, travas, movimento criativo e estilo escolhido.
2. Capacidades e limites reais da câmera ativa, firmware, memória e temperatura.
3. Foco útil no sujeito e tempo compatível com seu movimento.
4. Exposição útil do sujeito, proteção de altas luzes importantes e cor coerente.
5. Preservação de textura e redução de ruído sem apagar detalhe.
6. Contraste, acabamento e resolução de saída.

Uma instrução manual pode produzir uma fotografia tecnicamente imperfeita e deve ser respeitada. Informar a limitação sem alterar silenciosamente a intenção. Nenhum processamento recupera informação que não foi capturada. Nunca reconstruir rostos, letras, logotipos ou materiais e apresentar o detalhe inventado como original.

## 3. Espaço de 50.000.000 de combinações

| Ordem | Eixo | Estados | Uso |
|---|---|---:|---|
| 0 | Sujeito | 20 | Prioridade de detalhe e exposição |
| 1 | Iluminação disponível | 10 | Planejamento de exposição e ruído |
| 2 | Direção da luz principal | 10 | Orientação de posicionamento |
| 3 | Contraste da cena | 10 | Distribuição tonal e HDR |
| 4 | Distância ao sujeito | 5 | Foco, perspectiva e viabilidade óptica |
| 5 | Movimento do sujeito | 5 | Tempo e tratamento temporal |
| 6 | Óptica efetivamente usada | 5 | Tremor, recorte e resolução real |
| 7 | Estabilidade da câmera | 2 | Na mão ou apoio confirmado |
| 8 | Intenção temporal | 2 | Congelar ou mostrar movimento |
| 9 | Destino | 5 | Acabamento e exportação |

`20 × 10 × 10 × 10 × 5 × 5 × 5 × 2 × 2 × 5 = 50.000.000`.

Cada vetor completo tem ID único de 0 a 49.999.999. Codificação em base mista: começar em 0 e, para cada eixo na ordem acima, calcular `id = id × quantidade_de_estados + índice`. Decodificação: restos e divisões na ordem inversa. IDs só são comparáveis dentro da mesma versão do catálogo. Nunca reordenar estados em v1; uma alteração de significado exige nova versão.

Valores desconhecidos ficam fora da classificação: armazenar o eixo ausente e usar `scenario_id = null`. Não atribuir iluminação, distância, identidade do sujeito ou direção de luz com confiança falsa. A classificação de uma cena não demonstra qualidade da foto nem validação física da combinação.

### Interpretação dos eixos

- **Iluminação:** dez faixas de EV100 estimado, de menos de −2 a pelo menos 14 EV. Se abertura N, exposição t em segundos e sensibilidade S forem conhecidas: `EV100 = log2(N²/t) − log2(S/100)`. Isso é uma estimativa derivada da medição da câmera, afetada por compensação, reflexividade, filtros e processamento; não é lux medido. ISO sozinho não identifica luz disponível.
- **Direção:** luz frontal, oblíqua esquerda/direita, lateral esquerda/direita, contraluz, superior, inferior ou difusa. São categorias dominantes; fontes secundárias, dureza e altura ficam nos modificadores. Não inferir direção física com certeza a partir de uma única imagem ambígua.
- **Contraste:** medir `C = log2((Y95 + ε)/(Y5 + ε))` em luminância linear normalizada, `ε = 0,0001`; usar dez faixas de 1 EV, com a última ≥9 EV. Preferir RAW linear com preto removido. Em JPEG/YUV com curva do ISP, registrar `contrast_proxy`, nunca chamar a estimativa de alcance dinâmico do sensor. Separar estatísticas do sujeito e do quadro inteiro.
- **Distância:** muito perto <0,2 m; perto 0,2–<0,6 m; intermediário 0,6–<2 m; distante 2–<10 m; muito distante ≥10 m ou infinito. São faixas de contexto, não promessas de foco. Dioptrias só viram metros quando a calibração da lente permite; uma lente em infinito não prova a distância de um sujeito desfocado.
- **Movimento:** parado, lento, moderado, rápido ou irregular. Combinar fluxo do sujeito e giroscópio; estabilizar a estimativa da câmera antes de atribuir todo fluxo ao sujeito. Conservar velocidade, confiança e oclusão como medidas contínuas.
- **Óptica:** ultra-angular, principal, tele curta, tele longa ou recorte digital relevante. Registrar sempre ID físico ativo, focal e fator de recorte. Na presença de recorte relevante, usar a categoria de recorte e preservar a identidade óptica nos modificadores. Zoom pedido não prova troca de lente.
- **Estabilidade:** apoio confirmado exige observação temporal; um instante sem tremor não comprova tripé.
- **Intenção:** mostrar movimento só é selecionado explicitamente pelo usuário ou por um modo criativo escolhido por ele.

### Modificadores fora da contagem

Manter, sem inflar o número: cor e dureza da luz; cintilação; WB; pele; distância mínima da lente; foco/AE/AWB e suas travas; oclusão; confiança do rastreador; histograma por canal; ruído residual; ISO; exposição; resolução; orientação; flash; bateria; temperatura; RAM; firmware; capacidade RAW; latência; desejo de silhueta; várias pessoas em planos distintos. A ausência de um modificador reduz a confiança e pode impedir uma ação.

## 4. Posicionamento e qualidade da luz

O app pode sugerir mover fotógrafo, sujeito, fonte ou rebatedor. Uma curva digital altera aparência tonal; não move uma fonte real nem revela textura sem sinal. Sugestões respeitam o enquadramento e ficam discretas, com no máximo três avisos úteis.

| Situação | Orientação inicial | Preservação exigida |
|---|---|---|
| Retrato com janela | Colocar rosto cerca de 30–45° em relação à janela, com fonte um pouco acima dos olhos; adaptar à pose | Olhos legíveis, volume suave, pele sem clareamento seletivo |
| Luz frontal | Usar para reprodução e informação; sugerir leve lateralidade se o usuário pedir volume | Não fabricar relevo com clareza excessiva |
| Luz lateral dura | Oferecer difusão ou rebatedor do lado oposto; variar distância para equilibrar | Sombras coerentes, textura sem contraste agressivo |
| Sol alto | Sugerir sombra aberta ou mudança de orientação para retratos | Evitar olhos soterrados e testa recortada; respeitar a luz da cena |
| Contraluz | Escolher prioridade: rosto, equilíbrio ou silhueta explícita; sugerir preenchimento quando disponível | Não clarear sujeito até parecer recortado e colado |
| Luz inferior | Informar efeito dramático; sugerir fonte acima da linha dos olhos para retrato convencional | Preservar expressão e anatomia |
| Produto opaco | Fonte ampla lateral/oblíqua; fundo separado pela iluminação real | Textura, cor e rótulo íntegros |
| Vidro e metal | Mover fonte ou câmera para controlar reflexos; usar fontes amplas e cartões quando disponíveis | Reflexos definem forma; não apagar todos nem inventar transparência |
| Comida | Luz lateral ou contraluz suave; controlar brilho gorduroso | Cor plausível e textura sem aspecto plástico |
| Documento/obra | Câmera paralela; luz distribuída; mudar ângulo da fonte para evitar reflexo | Geometria e cor verificáveis; sem branco estourado sobre a escrita |
| Ambiente com fontes mistas | Priorizar a fonte sobre o sujeito; sugerir desligar uma fonte se viável | Não neutralizar todas as áreas com um WB global impossível |
| Noite com letreiros | Expor para preservar texto/cor importantes e o sujeito; aceitar pontos especulares saturados | Não comprimir tudo em cinza nem tornar a noite dia |

Os ângulos são pontos de partida de composição, não requisitos universais. Não exigir equipamentos que o usuário não possui. Não ativar flash automaticamente para resolver qualquer contraluz; reflexos e intenção podem torná-lo inadequado.

## 5. Exposição, movimento e estabilidade

Medir sujeito e altas luzes relevantes separadamente. Valorizar uma pessoa corretamente registrada mesmo quando um reflexo minúsculo satura. Não proteger cada lâmpada às custas de um rosto inutilizável. Não aplicar subexposição fixa a todas as cenas.

Para congelar, estimar deslocamento durante a exposição na escala de saída: `borrão_px ≈ velocidade_px_por_segundo × tempo_s`. Reduzir o tempo quando ultrapassar o orçamento de borrão do sujeito. Inicializar com 0,5–1 pixel de saída em detalhe crítico e calibrar por câmera e destino. Alinhamento corrige deslocamentos entre quadros; não remove o borrão dentro de cada quadro. Tripé e OIS não congelam pessoas.

| Sujeito | Ponto de partida para congelar, com luz suficiente |
|---|---|
| Objeto parado na mão | Tempo determinado por focal, tremor medido e estabilização comprovada |
| Adulto quase imóvel | Aproximadamente 1/125 s; alongar apenas se medição e intenção permitirem |
| Criança ou animal ativo | Aproximadamente 1/250–1/500 s |
| Caminhada | Aproximadamente 1/250 s |
| Esporte ou movimento rápido | Aproximadamente 1/500–1/1000 s ou mais curto |
| Água, rastros, panning | Duração criativa definida pelo usuário e avaliada na prévia |

Esses valores não são garantias nem limites fixos do hardware. Movimento transversal, aproximação, distância e enquadramento mudam a exigência. Se faltar luz, aceitar mais ruído ou sugerir luz/apoio. Não trocar movimento borrado por ISO baixo. Respeitar ISO e tempo manuais; relatar saturação, tremor provável e limites reais.

Avaliar cintilação de LEDs/rede quando detectável; compatibilizar tempo e antibanding suportado sem sacrificar silenciosamente o objetivo temporal. Não prometer eliminar bandas em todas as fontes. Alterações de exposição devem ser graduais na prévia, com histerese, salvo grande mudança de cena.

## 6. Foco persistente: estados e comportamento

Persistência significa conservar a intenção de foco no sujeito escolhido. Um objeto parado pode usar trava; um sujeito que se aproxima requer refoco. Definir explicitamente três modos: automático, acompanhar sujeito e travar distância. Foco e exposição têm travas independentes, mesmo quando a interface oferece comando conjunto.

| Estado | Entrada/transição | Comportamento |
|---|---|---|
| AUTO | Abertura da sessão ou liberação da seleção | Usar AF contínuo de foto se suportado; não prometer detecção de olhos sem detector |
| ADQUIRINDO | Toque em novo sujeito ou recuperação autorizada | Mapear toque ao sensor com rotação, espelho, zoom e crop; disparar busca uma vez |
| MANTENDO | Foco confirmado em sujeito parado selecionado | Conservar região e solução; tocar novamente ali não reinicia busca sem motivo |
| ACOMPANHANDO | Sujeito selecionado em movimento, rastreamento confiável | Atualizar região e distância por evidência; impedir salto para o fundo |
| OCLUSÃO | Alvo temporariamente encoberto | Conservar identidade/última região por prazo limitado; não declarar foco confirmado |
| PERDIDO | Prazo excedido, rastreamento fraco ou alvo saiu | Indicar perda; pedir novo toque ou retomar AUTO conforme preferência explícita |
| TRAVA_MANUAL | Usuário trava distância ou escolhe dioptrias | Manter posição mesmo com mudança do sujeito; liberar só por ação explícita ou invalidação física |
| INVIÁVEL | Dentro da distância mínima ou AF sem sucesso | Avisar para afastar/iluminar; oferecer lente alternativa com informação de recorte |

Implementação Camera2: consultar modos/regiões disponíveis, enviar gatilhos AF como eventos e observar os resultados; estado passivo focado e foco travado não são equivalentes. A confirmação deve corresponder à requisição e lente atuais. `INACTIVE` não equivale automaticamente a falha, especialmente em foco manual ou lente fixa. [Referências de requisição](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_AF_TRIGGER) e [resultado AF](https://developer.android.com/reference/android/hardware/camera2/CaptureResult#CONTROL_AF_STATE).

Regras adicionais:

- A evidência de foco usa a região do sujeito, não o laplaciano global: o fundo pode ser mais contrastado.
- Mudança de lente, reabertura da sessão, grande mudança de zoom ou orientação invalida coordenadas antigas; preservar a intenção e readquirir quando necessário.
- Não transferir dioptrias cegamente entre lentes. Distância calculada sem calibração é aproximada.
- Mais contraste por sharpen não comprova melhor foco. Nitidez de ruído tampouco comprova detalhe.
- Detecção de rosto genérica não prova foco no olho; priorizar olho próximo apenas com localização confiável.
- Evitar caçar foco durante burst de sujeito parado. Para aproximação rápida, selecionar estratégia compatível com acompanhamento e validar cada quadro; não fundir planos de foco incompatíveis.
- Falha/timeout não bloqueia o obturador indefinidamente. Permitir captura com aviso e guardar o estado real. Nunca marcar verde apenas porque o timeout terminou.
- Persistência entre reaberturas guarda preferência de modo, não uma promessa de manter o mesmo objeto do mundo.

Parâmetros iniciais, pendentes de calibração: confirmação estável por cerca de 150 ms; oclusão tolerada até 600 ms; aquisição automática com aviso após 1.500 ms; até duas novas tentativas após a primeira busca; histerese espacial de aproximadamente 4% do lado menor da prévia para reconhecer repetição de toque. Rastreamento, distância e mudança de lente prevalecem sobre proximidade do toque. Esses valores são referências e não alteram os limites do código existente nesta entrega.

## 7. Lentes, zoom e distância mínima

Consultar capacidades da sessão e conferir a lente física nos resultados quando o HAL a informar. Registrar separadamente lente pedida e usada. Se a informação não existir, usar `desconhecida`.

Priorizar a lente que consegue focar, com sinal e resolução suficientes. A tele pode perder para a principal em curta distância ou pouca luz. Se o sistema usar principal recortada, informar isso e reduzir a expectativa de detalhe. Preservar enquadramento quando possível; mudança de perspectiva ou campo de visão requer comunicação.

Não tratar 0,6×, 3,5×, 6× ou 7× como identidades ópticas universais. Os dados locais por aparelho servem de referência; as características atuais prevalecem. A distância mínima é específica da lente e pode variar com o modo. Não tentar corrigir uma foto originalmente fora de foco usando sharpening ou super-resolução.

## 8. Nitidez natural, ruído e fusão

Ordem recomendada: validar quadros e metadados → escolher referência nítida próxima ao instante do clique → alinhamento → rejeição de movimento/paralaxe → fusão → redução de ruído residual → cor/tom → redimensionamento → acabamento final adequado à saída.

O caminho nativo e o caminho próprio precisam declarar quem aplica cada etapa. Evitar afiar no ISP e repetir uma nitidez forte no app. Se não for possível controlar o ISP, reduzir a intervenção adicional com base na imagem observada. Desligar etapas só quando suportado e quando o pipeline próprio cobre sua função.

- Reduzir ruído de cor sem espalhar cor através de letras, fios e contornos. Redução de luminância deve proteger microtextura.
- Estimar ruído em regiões adequadas; não classificar toda textura como ruído nem todo ruído como detalhe.
- Em RAW linear normalizado, o perfil de ruído Camera2 fornece um modelo por canal. Não transportá-lo como medida exata para JPEG/YUV já processado. [Modelo oficial](https://developer.android.com/reference/android/hardware/camera2/CaptureResult#SENSOR_NOISE_PROFILE).
- Como início de calibração, aplicar detalhe apenas acima de 2–3 desvios-padrão residuais na mesma escala/domínio da medição. Em dúvida, aplicar menos acabamento.
- Limitar overshoot e undershoot nas bordas; avaliar em 100% e no tamanho de uso. Ganho de nitidez depende de algoritmo e resolução; não há um slider universal válido para todas as etapas.
- Não usar blur para esconder falha de alinhamento. Movimento local exige rejeição ou quadro único naquela região.
- Se a fusão piorar forma ou detalhe, manter o melhor quadro disponível e registrar fallback. Não apagar a foto por reprovação de qualidade.
- Super-resolução exige amostras reais e ganho medido. Dimensão maior por interpolação não equivale a detalhe novo.

Captura multiquadro pode melhorar ruído e faixa tonal quando alinhamento e movimento são tratados; o ganho depende da cena. A referência HDR+ descreve alinhamento e fusão de bursts e não constitui validação deste app. [Pesquisa de referência](https://research.google/pubs/burst-photography-for-high-dynamic-range-and-low-light-imaging-on-mobile-cameras/).

## 9. Contraste, cor e pele

Separar exposição, contraste global e microcontraste. Ajustar primeiro exposição/WB; depois comprimir realces com transição suave e sustentar sombras sem amplificar ruído. Preservar pretos com informação quando ela existe. Não exigir histograma cheio nem eliminar sombras intencionais.

Contra luz dura, preservar volume com curva suave; em neblina ou luz difusa, evitar produzir preto agressivo em toda região. Contraste local deve respeitar bordas para não criar halo em montanhas, cabelo e arquitetura. HDR é recurso para uma necessidade tonal medida, sujeito a movimento e latência, não um efeito sempre ativo.

Manter luminância e crominância da pele coerentes com a iluminação e a pessoa. Não clarear pele escura para caber em um alvo de brilho único. Proteger olhos, cílios, sardas e poros; não mudar anatomia. Letreiros coloridos e fontes artísticas não devem ser neutralizados automaticamente. Conferir recorte de canal RGB, não só luminância.

Estabilizar WB dentro de um burst compatível e evitar saltos na prévia. Não exigir WB idêntico para cenas realmente iluminadas de forma diferente. Mudança de lente pode exigir calibração de cor específica, não um filtro único para todos os aparelhos.

## 10. Retrato e profundidade

Foco óptico vem antes do desfoque computacional. Profundidade/segmentação não substitui foco. Preservar cabelo, óculos, orelhas, dedos, bordas de objetos, vazados, fios e rótulos. Tratar regiões transparentes/refletivas como incertas quando necessário.

Se a máscara não for confiável, reduzir ou desligar bokeh naquela captura e informar; não recortar agressivamente para aparentar certeza. Separar sujeito/fundo com transição plausível, sem halo claro/escuro e sem contaminar o fundo com amostras do sujeito. O desfoque deve variar com profundidade e manter realces plausíveis. Não prometer recorte perfeito em toda distância e focal.

## 11. Regras por família de sujeito

| Família | Prioridade específica |
|---|---|
| Retrato individual / grupo | Olhos e expressão; em grupo, avaliar planos de foco sem inventar profundidade de campo |
| Criança / animal | Instante e movimento; pelo e pele com textura |
| Produto / vidro-metal / joia | Cor, material, geometria e inscrições; reflexos úteis |
| Comida | Luz com volume e cor plausível, sem saturação indiscriminada |
| Documento / obra de arte | Leitura, paralelismo, fidelidade; correção geométrica como edição rastreável |
| Paisagem / arquitetura | Textura distante, horizonte, linhas e céu sem halos |
| Rua / esporte / palco | Instante, alvo e tempo; luz cênica mantida |
| Macro / vegetação | Plano de foco estreito, tremor e vento; não confundir detalhe com ruído |
| Água / veículos | Escolha explícita entre congelamento e movimento |
| Céu noturno | Apoio, foco confirmado e tratamento de estrelas; rejeitar rastros quando não desejados |

## 12. Memória, telemetria e evolução

Armazenar a base em assets e carregar uma cópia pequena sob demanda. Não materializar 50 milhões de objetos/linhas. Guardar somente observações e combinações efetivamente usadas, com versão e origem. O leitor desta entrega consulta regras de referência; executar ajustes exige integração explícita e validação pelo controlador.

Ficha proposta para integração futura:

```json
{
  "knowledge_version": "1.0.0",
  "scenario_id": null,
  "axes": {},
  "missing_axes": ["light_direction"],
  "confidence": {},
  "device": {"model": "observado", "build": "observado", "camera_id": "observado"},
  "focus": {"intent": "follow_subject", "state": "occluded", "confirmed": false},
  "requested": {},
  "observed": {},
  "rules_consulted": ["FOCUS-01"],
  "fallbacks": [],
  "metrics": {},
  "validation": "not_physically_validated"
}
```

Guardar solicitado e observado separadamente; null significa desconhecido. Não fabricar valores para preencher ficha. O exemplo não é telemetria já ligada ao app.

Aprendizado local pode ajustar preferências de estilo e modelos calibrados por aparelho/lente/firmware; feedback subjetivo isolado não muda limites de qualidade. Invalidar calibrações incompatíveis após firmware. O catálogo funciona offline e não envia imagens. A função opcional de IA já existente no projeto tem fluxo próprio; esta diretriz não altera sua configuração nem suas permissões.

## 13. Critérios de aceitação e validação

Validação lógica e validação fotográfica são evidências diferentes. O teste de todos os IDs comprova indexação reversível do espaço, não a qualidade de 50 milhões de fotografias.

| Verificação | Evidência necessária |
|---|---|
| Contagem e endereçamento | Produto igual a 50.000.000; todos os IDs codificam/decodificam; rejeitar entradas fora da faixa |
| Base empacotada | JSON e Markdown presentes no APK, mesma versão e conteúdo conferido |
| Foco persistente | Toque repetido, aproximação/afastamento, oclusão, mudança de lente, pouca luz e falha AF no aparelho |
| Movimento | Pessoas andando, criança/animal e alvo controlado; inspecionar o sujeito, não apenas fundo |
| Nitidez e ruído | Comparar original, referência e saída com mesmo enquadramento/exposição; bordas, letras, poros e áreas planas |
| Contraste e cor | Contraluz, fonte colorida, luz mista, várias tonalidades de pele; histogramas por canal |
| Lentes | Perto/longe em cada lente real, incluindo 0,6×, 1×, tele e recortes usados pelo aparelho |
| Retrato | Cabelo, óculos, transparência e objetos finos em planos distintos; admitir falhas |
| Exportação | Arquivo efetivamente salvo: dimensão, orientação, perfil, EXIF aplicável, integridade e cópia do original |
| Recursos | Latência, RAM e aquecimento em sequências; fallback legível sem perda silenciosa |

Calibrar limiares com conjunto versionado de cenas reais, referências e avaliação visual. Uma métrica global de alta frequência pode crescer com ruído e não deve aprovar sozinha. Métricas iniciais do projeto, como detalhe ≥0,7 e forma ≥0,9, pertencem ao seu comparador específico; não são padrões universais.

## 14. Limites e integração

O número de combinações é exato para o esquema v1. A interpretação física é limitada pela qualidade de observação e pelas capacidades do aparelho. Não afirmar treinamento, domínio de toda cena, foco infalível, recorte perfeito ou melhoria comprovada sem ensaio.

Integração futura: coletor de contexto → classificação com confiança → consulta à memória → controlador de captura com limites do aparelho → processamento → verificador de qualidade → ficha real. Ativar por etapa, preservando os ajustes aprovados e oferecendo fallback. `PhotoKnowledge.advice(id)` retorna referências pertinentes; não modifica foco, exposição, nitidez ou editor.

Referências locais complementares: `capacidades_aparelhos.md`, `pipeline_computacional.md`, `agentes_e_banco.md` e o histórico em `PENDENTE.md`. Dados coletados anteriormente são evidência histórica, não garantia de capacidades atuais.

## 15. Implementação Codex de 10/11/2026

Versão do app: 1.20.0-codex. Pacote próprio br.com.duvier.thenaturalcam.codex; arquivos em DCIM/TheNaturalCamCodex e Documents/TheNaturalCamCodex.

- Base consultada localmente com classificação parcial; regras registradas na ficha e ID de cenário null quando faltarem eixos.
- Qualidade Máxima por padrão. Tempo de exposição nas teles considera borrão dentro do quadro; referência inicial conservadora de 1/(1,5 × focal equivalente), sujeita a calibração real. Para retrato, teto inicial de 1/125 s; não substitui controle de movimento medido.
- Nitidez única no app quando EDGE_MODE_OFF é suportado. Teto de redução de luminância e força de nitidez obedecem ao plano; acabamento menos intenso em retratos.
- Foco por toque com rejeição de estados antigos, timeout sem falso sucesso e opção de AF contínuo na área tocada. Não é rastreamento semântico de uma pessoa através do quadro.
- Bokeh: padrão f/8, opções f/5,6 a f/16; versões antigas com valores menores não produzem blur maior que f/5,6. A abertura é simulada, não uma alteração do diafragma físico.
- RAW+: JPEG revelado pelo ISP Bayer já existente e DNG original de um quadro, com metadados de timestamp correspondente quando entregues. O DNG não é o RAW fundido.
- TCS3720 CCT e OPLUS Fusion RGB: leitura local e registro dos canais, timestamp e validade. Não se converte um valor Kelvin diretamente em ganhos RGB sem calibração por lente.
- JPEG solicitado em qualidade 100; tamanho em bytes e dimensões reais registrados. Arquivo maior não comprova detalhe adicional.
- Sete exemplos originais do usuário em biblioteca local, acessível nos Ajustes. São referências visuais e não treinamento ou calibração fotométrica.
- Sem aumento automático para 28 MP na lente nativa. Em recortes, a saída da fusão é identificada como computacional. Resoluções altas reais são procuradas também na lista high-resolution da Camera2.

No checkpoint inicial, os 48 testes automatizados aprovados cobriram regressões e cenários sintéticos. Não comprovam qualidade de nível Sony, Hasselblad, Leica, Nikon ou Phase One, nem substituem fotografias comparativas no Reno 16 Pro. A validação atual fica no relatório Codex.

## 16. Integração Codex 1.27.1 — captura de referência e acabamento por área

A estratégia padrão de Foto/Retrato foi restaurada do snapshot publicado `cee696e` (Claude 1.24.1), após comparação física mostrar regressões nas tentativas de trocar o sensor e de encurtar a exposição por movimento estimado. O pacote, arquivos e desenvolvimento do Claude continuam separados.

- Foco, medição, seleção de lente e plano de Foto usam a estratégia publicada que produziu a referência aprovada. A medição de movimento da prévia não encurta automaticamente a exposição de Foto. A transição suave de zoom aprovada pelo usuário foi mantida.
- O fluxo pode usar recorte na grade nativa, super-resolução computacional e complementação de tamanho, conforme a base publicada. Isso não constitui leitura de 50/200 MP. Dimensões, lente real e decisões devem ser conferidas na ficha.
- Após a limpeza da base publicada, o acabamento adicional trata ruído residual por semelhança de pequenas regiões, com mais atuação em áreas lisas e sombras e proteção para letras, bordas e possível pele. Há uma escala maior só para manchas de cor. A correção de luminância volta à grade inteira, sem reduzir o tamanho da fotografia ou recriar detalhe.
- Os realces por escala agora têm uma zona sem realce abaixo do ruído e limite de 6 níveis de luminância. O acabamento final não acrescenta outra afiação. As ranhuras da tampa e as letras do frasco em 3,5× são referências de inspeção a 100%; não alisar os frisos nem desenhar sulcos inexistentes. Não equivale a uma máscara semântica de primeiro/segundo plano.
- O mapeamento tonal local limitado da 1.27.0 foi mantido para conservar a claridade. Não acrescentar ganho de exposição para mascarar ruído. O processamento regional completo continua no JPEG do RAW+, preservando o DNG original.
- O plano Noite mantém até 15 quadros e orçamento desejado de até 3 s; depende da medição confiável e do que o HAL realmente entrega. Não apresentar esse orçamento como tempo comprovado de captura no Reno.
- Se o tempo proposto exigir ISO fora da faixa, conservar AE. Medida inconclusiva de bordas é registrada como desconhecida, sem aprovação fictícia. Intermediários de laboratório ficam desligados por padrão.
- A base de 50 milhões permanece combinatória, com consulta parcial e 40 regras. Os protótipos de medição de movimento e mapeamento de foco não equivalem a calibração física nem a treinamento de imagem.

Os ajustes abrem esta diretriz e `PROTOCOLO_RUIDO_E_EMPILHAMENTO.md`. A qualidade real, o desempenho e as pendências de cada versão ficam no relatório Codex; os testes de software não substituem a comparação dos JPEGs salvos.
