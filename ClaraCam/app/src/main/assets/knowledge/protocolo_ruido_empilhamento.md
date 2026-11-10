# The Natural Cam — ruído, textura e empilhamento por região

Protocolo 1.1.0 · 10/11/2026 · implementação Codex 1.27.1

## Objetivo e evidência

Conservar foco, letras, textura, tons de pele e cores ao reduzir ruído, de dia ou à noite. Clarear sombras depois de reduzir o ruído, de forma moderada e sem apagar o contraste da cena. Não reconstruir letras, poros ou detalhes ausentes por geração de imagem.

Nos dois exemplos enviados, o usuário identifica a imagem mais clara como OPPO e a mais escura como Natural Cam. São PNGs de 960 × 1280, com enquadramentos diferentes. Evidenciam a diferença de brilho percebida, mas não permitem atribuir uma redução exata de ruído, ISO, exposição ou vantagem óptica. Os originais não foram alterados nem publicados.

## Três técnicas distintas

| Técnica | Como capturar | Uso no Natural Cam |
|---|---|---|
| Média robusta contra ruído | Mesmo foco, enquadramento, WB e exposição; alinhar e rejeitar discrepâncias | Implementada nas rajadas YUV/RAW; é a base deste protocolo |
| HDR de exposições | Exposições diferentes, com pelo menos uma boa leitura de cada região | Não confundir a rajada uniforme com HDR bracketado; mesclagem própria por exposição ainda não implementada |
| Empilhamento de foco | Vários planos de foco, com sobreposição de profundidade | Ainda não implementado; candidato para produto/macro parado, nunca ativado automaticamente em retrato |

No Photoshop, a Adobe orienta alinhamento e Smart Object com modo Mean para ruído, sem estabelecer um número universal de fotos. Para foco, orienta Auto-Align Layers e Auto-Blend Layers → Stack Images. [Adobe: pilhas](https://helpx.adobe.com/photoshop/desktop/create-manage-layers/create-layer-compositions/create-and-process-image-stacks.html), [Adobe: foco](https://helpx.adobe.com/photoshop/desktop/create-masks/blend-images/create-a-composite-with-extended-depth-of-field.html).

Para HDR, a Adobe indica três exposições em ±2 EV; sua tabela relaciona extensões de −1,5/+1,5, −3/+3, −4,5/+4,5 e −6/+6 EV a 2, 3, 4 e 5 exposições. Mais fotos podem aumentar erros de alinhamento e fantasmas. Isso se refere a exposições diferentes, não ao número ideal de repetições para limpar ruído. [Adobe: quantidade de exposições HDR](https://helpx.adobe.com/dk/lightroom/desktop/edit-photos/hdr-panorama.html).

## Referências de fotografia noturna confirmadas

O trabalho de Liba e colaboradores, do Google, mede movimento da câmera e do sujeito para escolher tempo e número de quadros. Demonstra um exemplo com 13 quadros de aproximadamente 0,33 s e processamento espacialmente variável. Mostra também que apenas clarear um resultado escuro expõe ruído; captura e fusão precisam melhorar junto. O limite de captura citado para aquele sistema é 6 s quando estabilizado. São dados históricos daquele sistema, não metas já medidas no Reno. [Artigo original, 2019](https://arxiv.org/html/1910.11336).

A Huawei confirma exposição prolongada e composição de vários quadros no Super Night, mas a página consultada não especifica uma quantidade universal de quadros ou duração. O SDK descrito é para aparelhos Huawei compatíveis; não libera seu ISP no Reno. [Huawei Camera Engine](https://developer.huawei.com/consumer/en/CameraKit).

A busca do YouTube fornecida não carregou diretamente. O índice encontrou o tutorial de redução de ruído por empilhamento do Lonely Speck, mas não confirmou o ranking de “mais assistido”, nem foi possível verificar o vídeo integralmente. Não é usado como fonte de números do algoritmo. [Vídeo encontrado](https://www.youtube.com/watch?v=Rydg7JGTAbw).

## Política de captura implementada

- Foto/retrato: quantidade adaptativa, em geral 4–12 quadros, limitada também pela segurança do foco e do enquadramento. MAX permite 12 no Reno. Foto/Retrato seguem o plano publicado do Claude 1.24.1, inclusive recorte nativo e super-resolução quando decididos por esse plano. Na proximidade, conservar o processamento da interface lógica; registrar qual sensor físico foi entregue. Abertura direta da principal e encurtamento de Foto por fluxo óptico foram retirados do padrão após regressões físicas.
- Noite automática: até 15 quadros, com orçamento de até 3 s de exposição somada. 15 × 200 ms é o limite escolhido para este app, não uma constante oficial do Pixel ou Huawei. Em luz melhor, o orçamento cai para 1 s ou 0,4 s.
- Um quadro de até 200 ms só é permitido com movimento medido na prévia, giroscópio estável e OIS observado ativo. Sem essa evidência, aplica-se o limite conservador pela focal. Movimento do sujeito reduz o tempo mesmo com o telefone apoiado. Rosto limita cada quadro a 8 ms.
- Só alterar o par automático ISO/tempo se o ISO equivalente couber na faixa real do sensor. Se o limite impedir manter o brilho, conservar AE do HAL e registrar o plano não aplicado. Não produzir subexposição para cumprir um tempo teórico.
- Na câmera lógica com outro sensor físico ativo, conservar AE do HAL para o ajuste automático da rajada: o ensaio encontrou uma captura quase preta ao reutilizar o par lógico de ISO/tempo. Nessa rota, o plano de tempo é uma intenção não aplicada; a ficha informa essa limitação e a exposição efetivamente recebida. OIS só é considerado ativo quando confirmado no resultado; não presumir que esteja ligado.
- O tempo total de uso inclui aquisição, alinhamento, processamento e gravação; 3 s de luz não significam uma foto pronta em 3 s. Movimento pode exigir tempos menores, somando bem menos luz.
- Exposição manual e compensação escolhida pelo usuário são preservadas. WB é mantido durante a rajada. TCS3720 e Fusion RGB continuam registrados com validade, sem fabricar ganhos RGB a partir de Kelvin não calibrado.

## Processamento por área implementado

1. **Alinhar antes de limpar.** Usar quadro de referência nítido; reduzir a contribuição de quadros/regiões incompatíveis. Comparar detalhe e forma das bordas com a referência. Fusão reprovada volta à fusão simples ou a um quadro preservado, conforme o caminho disponível.
2. **Estimar ruído restante.** Combinar estimativa espacial com limite pelo prior temporal, quando disponível. Isso reduz a chance de tratar textura fina como ruído. Não equivale a uma calibração física do sensor por ISO.
3. **Medir regiões.** Uma limpeza geral leve de luminância/cor, preservada do fluxo publicado do Claude, precede a limpeza local. Usar luminância, variância local e crominância. Sombras lisas recebem mais limpeza; superfícies claras, menos; bordas/letras/textura, menos ainda. As máscaras têm transições suaves. A detecção de possível pele pela cor é uma proteção conservadora, sem reconhecimento de raça ou clareamento por tom de pele.
4. **Proteger textura de pele.** O candidato de pele recebe menor mistura do denoise de luminância e mais limpeza de manchas cromáticas. Essa heurística ainda requer retratos reais de várias peles e iluminações. Não aplicar embelezamento, remoção de poros ou “pele de porcelana”.
5. **Conferir o denoise.** Reavaliar bordas antes do clareamento. Se a medição conclusiva reprovar, descartar essa limpeza e conservar a entrada. Em cenas sem bordas suficientes, registrar medição inconclusiva, sem alegar aprovação de nitidez.
6. **Clarear com limites.** Só no automático sem EV intencional, quando EV100 estimado e histograma indicarem pouca luz/resultado escuro. Limite escolhido: até +0,7 EV em Foto, +1,1 EV em Noite, +0,5 EV com rosto. Ganho comum em RGB linear, suave por área e limitado pelas altas luzes. Preto profundo permanece ancorado; o algoritmo não recupera sinal ausente ou altas luzes já recortadas.
7. **Refinar grão residual e manchas por área.** Após o acabamento principal, comparar pequenas regiões numa grade auxiliar de meia resolução. Corrigir mais as superfícies lisas e sombras; preservar ranhuras/letras por semelhança de padrão e pela diferença com a grade inteira. Uma escala de 8 pixels trata manchas cromáticas, guiada por luminância e cor para limitar vazamento entre materiais. Aplicar somente a correção de luminância à fotografia inteira. A estimativa multiescala e o prior temporal são aproximações, sem calibração física por ISO.
8. **Realçar só detalhe confiável.** Os realces por escala mantêm a estrutura publicada, mas agora zeram o ganho abaixo de aproximadamente 1,1× o ruído residual e sobem suavemente; limitar a correção a ±6 níveis. Não acrescentar outra afiação no acabamento final. A redução residual exige detalhe relativo ≥0,93 e correlação de bordas ≥0,985 quando a medição é conclusiva; caso contrário, conservar sua entrada. Isso não garante que cada textura de uma cena real tenha sido validada.
9. **Salvar com reserva.** Não modificar a entrada do acabamento regional. Uma falha nessa etapa conserva a captura alinhada. JPEG em qualidade 100, com dimensões reais e bytes na ficha. Intermediários extras de laboratório ficam desligados por padrão.

Nas rajadas Foto/Retrato/Noite/Rua, a limpeza espacial publicada precede realces por escala com proteção do ruído, refino residual por área e o mapeamento tonal local da 1.27.0. No JPEG revelado do RAW+, o processamento regional completo permanece ativo. O DNG original permanece intacto. Foto com empilhamento desligado, JPEG direto/Pro, Longa, Astro, extensões do sistema e filtros criativos seguem seus caminhos próprios; este protocolo não alega substituir todos os processamentos do aparelho. Bokeh continua separado e opcional na Foto; empilhamento de foco não é usado para destruir o desfoque de retrato.

## Memória, rastreabilidade e aceitação

O catálogo de 50.000.000 de combinações e suas 40 regras permanece como referência local. Este protocolo executa parte dessas regras (`FOCUS`, `MOTION`, `DETAIL`, `NOISE`, `COLOR`, `AUDIT`), com observações parciais. Não representa treinamento em 50 milhões de fotos nem identificação completa de qualquer cenário.

A ficha registra quadros pedidos/recebidos/usados, exposição realmente somada quando todos os metadados chegaram, movimento da prévia, OIS, ruído estimado, máscaras, resultado relativo de detalhe, clareamento e latência regional. Ruído previsto da fusão não deve ser apresentado como uma medida laboratorial da foto pronta; máscara média não é porcentagem de pixels comprovadamente sem ruído.

Testes automatizados verificam ruído sintético, bordas, cores, ancoragem de preto, altas luzes, preservação da entrada, movimento e orçamento noturno. A validação física deve conferir arquivos salvos em 0,6×/1×/2×/3,5×/6×/7×/8×, foco por toque e persistência, alternância MAX/AUTO, DNG e retratos humanos. Exigir texto legível e ausência de halo/plástico/fantasmas em recortes de 100%, sem confundir ampliação ou tamanho em MB com detalhe real. Resultados e pendências ficam no [relatório Codex](RELATORIO_THE_NATURAL_CAM_CODEX.md).

## Estado após comparação com o Claude

A foto de referência aprovada foi registrada na lógica 0 com sensor físico 2 e 12 quadros. Uma repetição com sensor 4 ficou desfocada; trocar controles por estimativas experimentais piorou a exposição no Codex. A 1.27.0 restaurou a estratégia publicada de captura de Foto/Retrato. O usuário informou melhora após seu pós-processamento em 3,5× e aprovou a transição de zoom. A 1.27.1 conserva essa base e ajusta o acabamento por região. O estimador da prévia é observação/protótipo, sem comandar o tempo de Foto. Não afirmar que todos os zooms estejam fisicamente validados.

A aceitação usa três recortes da mesma foto: frisos da tampa para contraste e continuidade dos sulcos; rótulo para forma das letras; superfícies lisas do frasco e fundo para ruído/clareza/cor. Comparar a 100% sem confundir ruído com nitidez, respeitando que um foco ou captura insuficiente não pode ser reparado pela invenção de detalhe.
