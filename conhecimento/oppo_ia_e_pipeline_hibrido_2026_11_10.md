# OPPO IA e combinação de técnicas para o Natural Cam

Pesquisa e ensaio de 10/11/2026. Distinguir efeito observado, explicação pública, código disponível e implementação realmente instalada. Não há acesso ao modelo proprietário da OPPO.

## Ensaio solicitado no Reno

O vídeo de 2min59s enviado foi examinado por quadros ao longo de toda a duração. Mostra o Editor de IA da OPPO, Aprimorar nitidez, Remoção de desfoque por IA e ajustes convencionais. Essa observação identifica as ferramentas, mas não revela a rede ou os parâmetros internos.

Depois, foi selecionada a terceira miniatura da Galeria — uma foto, após um vídeo e outra foto — e executada exatamente a sequência pedida: **Remoção de desfoque por IA → OK → Aprimorar nitidez → OK → Salvar cópia**. O arquivo de entrada já tinha edição anterior; o ensaio não é uma comparação com RAW original sem processamento.

Entrada e cópia mantiveram 3072 × 4096. O original de 514.893 bytes permaneceu idêntico, verificado por SHA-256 no telefone e no PC. A cópia ficou com 332.136 bytes. A redução do tamanho é compatível com menor complexidade/ruído e nova compressão; não prova ganho de resolução nem maior perda de detalhe por si só.

Na comparação dos mesmos recortes, as sombras ficaram mais uniformes e os contornos principais mais definidos. Letras pequenas continuam limitadas e algumas formas parecem retocadas. A luminosidade global não aumentou: mediana de luminância aproximada 26,0 → 24,8 em escala de 8 bits, sem valor de calibração fotométrica. Portanto, denoise/restauração e exposição/mapeamento tonal são problemas distintos. As imagens, vídeo e diagnósticos do ensaio permanecem locais, fora do Git.

## O que as fontes permitem concluir

| Fonte | Evidência e aplicação |
|---|---|
| [OPPO ColorOS](https://www.oppo.com/en/coloros15/) | Apresenta AI Clarity Enhancer e AI Unblur como restauração de clareza/textura e borrão. A documentação consultada não oferece pesos ou uma API de editor para incorporá-los ao app. Não atribuir nosso código ao ISP da OPPO. |
| [Google Research — Night Sight](https://arxiv.org/html/1910.11336) | Base para medir movimento, alinhar/fundir com confiança local e tratar tons após melhorar a captura. Não portar calibração de WB ou valores de ruído do Pixel para o Reno. |
| [Adobe — ordem das operações de IA](https://helpx.adobe.com/lightroom/web/edit-photos/manage-ai-edits.html) | Coloca HDR e Denoise/Raw Details/Super Resolution antes de várias edições locais e globais. Serve como referência para a ordem das etapas, não como SDK Android. |
| [Adobe — preservar detalhes e ruído JPEG](https://helpx.adobe.com/photoshop/desktop/effects-filters/artistic-stylize-filters/reduce-image-noise-and-jpeg-artifacts.html) | Recomenda avaliar a 100%, equilibrar ruído e detalhe e observar os canais. Isso orienta a inspeção de letras, cabelo e padrões finos. |
| [Apple — Photonic Engine](https://www.apple.com/hk/en/newsroom/2022/09/apple-debuts-iphone-14-pro-and-iphone-14-pro-max/) | A Apple descreve Deep Fusion mais cedo no fluxo para preservar informação e textura. A ideia aplicável é trabalhar antes de perdas adicionais; não há portabilidade do algoritmo proprietário para Android. |

## Comunidades pesquisadas

- **Reddit:** [relato sobre texto artificial em OPPO X9 Pro](https://www.reddit.com/r/Oppo/comments/1rb9gi2/x9_pro_how_to_take_picture_without_ai_interference/) e [diferença entre visor e resultado](https://www.reddit.com/r/Oppo/comments/1pbf65n/the_find_x9_pros_processing_is_so_bad_viewfinder/) trazem a mesma preocupação do usuário: limpeza excessiva pode reduzir naturalidade. São relatos de outros aparelhos, não prova do algoritmo ou da causa no Reno. Não usar seus valores de ISO, zoom ou focal como calibração.
- **Comunidade Adobe:** a busca encontrou relatos de Denoise que aparenta não aplicar e problemas de execução, como [este registro de 2026](https://community.adobe.com/questions-680/denoise-no-longer-working-on-compatible-photos-super-resolution-and-raw-details-work-but-denoise-does-not-work-when-clicked-1635978). Não foi encontrada ali uma implementação pronta com evidência superior para o Reno. A orientação técnica foi confirmada na documentação oficial acima.
- **Comunidade Google:** [relato de mudança/disponibilidade do Photo Unblur](https://support.google.com/photos/thread/446608695) é sobre o editor, não um algoritmo reutilizável. O fundamento de captura vem do artigo técnico original do Google.
- **Comunidade Apple:** [comparação entre prévia e processamento final](https://discussions.apple.com/thread/255098775) e [relato de suavização excessiva](https://discussions.apple.com/thread/253466608) reforçam critérios de avaliação, sem demonstrar mecanismos internos. São fóruns de usuários.
- **X:** as buscas públicas por restauração móvel/NAFNet não localizaram uma publicação técnica verificável útil. Não foi inferida uma recomendação a partir de uma postagem inacessível ou de popularidade.

## Projetos de código encontrados e limites

| Projeto | Possibilidade concreta | Decisão nesta entrega |
|---|---|---|
| [NAFNet oficial](https://github.com/megvii-research/NAFNet) | Denoise por regressão, já disponível no editor do app com modelo SIDD. A licença e os avisos locais permanecem preservados. Uma rede de restauração ainda pode remover ou alterar textura. | Mantido no editor. Não forçar automaticamente uma passada neural longa em cada foto; o histórico do projeto registra latência elevada em Noite. Não é o algoritmo da OPPO. |
| [SplitterNet / MoDeNet — CVPR 2024](https://github.com/rflepp/SplitterNet-Efficient-Mobile-Denoising-Models-CVPR2024) | Implementações eficientes e conversão TFLite, com dados reais de celular. Repositório atualizado em 24/09/2026 na consulta. | Candidato de bancada. O [LICENSE.md](https://github.com/rflepp/SplitterNet-Efficient-Mobile-Denoising-Models-CVPR2024/blob/main/LICENSE.md) é CC BY-NC-SA 4.0, portanto não foi incorporado como dependência do produto. Não há tempo medido no Reno. |
| [LiteDenoiseNet — artigo 2026](https://arxiv.org/abs/2605.03680) e [NN Dataset](https://github.com/ABrain-One/nn-dataset) | Distilação e operações compatíveis com NPU são um caminho para reduzir custo. O framework informa MIT, mas contém muitas variantes e estatísticas, inclusive modelos gerados/modificados. | Candidato a estudo. A consulta não confirmou um pacote TFLite treinado e validado para o Reno pronto para substituir nosso modelo. A licença do framework não dispensa conferir pesos/componentes. |
| [AISP / mv-lab](https://github.com/mv-lab/AISP) | Referências de RAW, ISP aprendido, restauração e bokeh. | Material de pesquisa, sem importação ampla. Conferir cada componente e medir cor, foco, memória e latência antes de converter. |

Não somar ganhos de PSNR, megapixels ou velocidade de artigos diferentes como se valessem para o mesmo sensor. Dimensity 9500, Snapdragon de competição, FHD e cenas de dataset não são uma medição de 12,6 MP no Reno CPH2863.

## Combinação escolhida e implementada

A entrega 1.27.1 conserva a estratégia publicada de captura de Foto/Retrato do Claude 1.24.1. Após o usuário aprovar melhora de ruído e suavidade do zoom na 1.27.0 em 3,5×, o ajuste concentrou-se no acabamento: alinhamento/rejeição temporal e limpeza da base publicada, realces por escala que não amplificam o grão abaixo do limiar, refinamento residual por semelhança de padrões, limpeza maior de manchas cromáticas e o mapeamento tonal limitado já aprovado. Nenhuma dessas etapas gera letras ou ranhuras novas.

A comparação física mostrou uma boa referência com sensor 2 pela lógica 0 e uma repetição desfocada com sensor 4. A tentativa de forçar a principal diretamente e de comandar Foto por um fluxo óptico ainda não confiável piorou a imagem; foi retirada do padrão. O estimador da prévia permanece observação/protótipo. A proteção de ISO equivalente e compatibilidade de sensor conserva AE quando o plano não pode ser aplicado com segurança. A ficha separa intenção de exposição, controle aplicado e exposição entregue.

A limpeza adicional usa guias de luminância/crominância, não uma segmentação semântica comprovada de primeiro e segundo plano. A comparação da tampa deve preservar contraste e continuidade dos frisos; as superfícies lisas do frasco e parede são áreas de inspeção de grão, e o rótulo controla forma/legibilidade. O ganho modesto visto no mesmo JPEG no PC não é prova de qualidade profissional de todos os zooms.

A restauração neural já existente permanece no editor. Uma futura alternativa mais leve exige comparação com a foto preservada, proteção de texto/textura, licença compatível e tempo total medido no Reno. Não foi substituída por um modelo de fórum nem incorporado um modelo proprietário OPPO/Adobe/Google/Apple.

O funcionamento detalhado e os critérios de aceitação estão no [protocolo](../PROTOCOLO_RUIDO_E_EMPILHAMENTO.md); resultados da versão instalada e pendências físicas ficam no [relatório](../RELATORIO_THE_NATURAL_CAM_CODEX.md).
