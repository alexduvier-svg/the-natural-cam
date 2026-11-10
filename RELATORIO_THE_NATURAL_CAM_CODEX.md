# The Natural Cam Codex — implementação e testes no Reno 16 Pro

Registro de 10/11/2026. Versão instalada: **1.27.1-codex**, código 67. O histórico abaixo identifica checkpoints anteriores; suas medições não são resultados desta versão.

## Entrega e isolamento

- Diretriz: [DIRETRIZ_THE_NATURAL_CAM.md](DIRETRIZ_THE_NATURAL_CAM.md).
- APK atual: `The_Natural_Cam_Codex_1.27.1.apk`. SHA-256: `F81B8FF974C1166D9E79C5C82277F08BD6549BBA0C6E020A880C4F9A341B9A03`.
- Pacote exclusivo: `br.com.duvier.thenaturalcam.codex`; nome no celular: **The Natural Cam Codex**.
- Fotos: `DCIM/TheNaturalCamCodex`; fichas: `Documents/TheNaturalCamCodex`.
- Branch: `codex/natural-cam-memory`, neste worktree. A pasta do Claude é somente leitura; nenhuma alteração não commitada foi copiada. O pacote original permanece separado.
- Integração solicitada: snapshot publicado `cee696e1ec9d569fd00c4979b25583cc3806cd7c` (Claude 1.24.1), 16 commits após a base. O merge e o envio destinam-se somente à branch Codex, conforme autorização do usuário.
- [Instalador](tools/Install-RenoCodex.ps1): atualização automática por ADB, preservando dados, sem tela manual de instalação. Fotos privadas, diagnósticos, referências originais e APK permanecem fora do Git.

## Base aprovada e ajuste por área — 1.27.1

A 1.27.0 restaurou o foco, a medição, a seleção de lente e o plano de Foto/Retrato publicados em 1.24.1, após regressões experimentais de exposição. A abertura direta da principal e o tempo de Foto controlado por fluxo óptico foram retirados do padrão. O usuário relatou que o resultado ficou próximo do desejado e que o pós-processamento diminuiu bastante o ruído; confirmou o teste em **3,5×**, pediu limpeza no primeiro e segundo plano preservando claridade, textura e cores, e aprovou a transição suave de zoom.

A ficha dessa foto confirma sensor físico 2 via lógica 0, ISO 9946, 29,998 ms por quadro, 12 quadros recebidos/usados e 359,97 ms de exposição somada. Arquivo de 4096 × 3072 (12,58 MP), 2.154.571 bytes; a saída usa recorte e super-resolução computacional, sem alegar resolução nativa de 50/200 MP. A inspeção do JPEG confirmou os frisos e letras principais visíveis, com grão e manchas cromáticas residuais. As estimativas de ruído da fusão não são uma medida laboratorial da imagem final.

A **1.27.1** conserva captura, enquadramento, mapeamento tonal limitado e a transição de zoom dessa base. Ajusta somente o acabamento, além da proteção contra reaplicar automaticamente um par de exposição lógica a outro sensor físico:

- Realces por escala agora deixam o grão abaixo do limiar sem amplificação e limitam a correção a ±6 níveis. Não há nova afiação depois da limpeza residual.
- `ResidualNoiseRefiner` compara padrões 3×3 numa grade auxiliar de meia resolução. Aplica uma correção suave à luminância inteira, com mais peso em superfícies lisas/sombras e proteção para textura, letras e possível pele. Não reduz a resolução final nem gera detalhes.
- Uma escala maior de crominância trata manchas de cor; guias de luminância e cor limitam vazamento entre materiais. Não há nova estimativa de balanço de branco ou dessaturação global.
- A etapa regional preserva sua entrada; exige detalhe relativo ≥0,93 e correlação ≥0,985 quando mensuráveis, ou descarta seu candidato. Medição sem bordas suficientes fica inconclusiva. Esse controle não comprova preservação de cada microtextura real.
- O usuário definiu os frisos da tampa como referência de nitidez. Foram inspecionados recortes correspondentes do mesmo arquivo, junto com parede, superfície clara do frasco e rótulo. O ensaio no PC apresentou limpeza modesta, sem aumento de claridade por exposição nem perda visível dos sulcos; não equivale a comparação entre duas capturas controladas.
- O processamento automático é fotométrico/por padrões locais; não declara uma segmentação semântica confiável de primeiro e segundo plano. Bokeh e profundidade continuam separados.

O protocolo está em [PROTOCOLO_RUIDO_E_EMPILHAMENTO.md](PROTOCOLO_RUIDO_E_EMPILHAMENTO.md), também disponível em Ajustes → **Diretriz e protocolo de qualidade**. A pesquisa de fontes e comunidades está em [OPPO e pipeline híbrido](conhecimento/oppo_ia_e_pipeline_hibrido_2026_11_10.md).

## Verificações atuais

Instalação automática confirmada pelo Android (`Success`, versão 1.27.1/código 67), preservando dados e o pacote original. A diretriz e o protocolo dentro do APK foram comparados byte a byte aos documentos finais.

`assembleDebug` e `testDebugUnitTest` concluídos: **90 testes, 88 aprovados, zero falhas/erros e duas demonstrações visuais opcionais ignoradas por falta de entrada configurada**. As regressões adicionais cobrem ruído correlacionado em duas luminosidades, contraste dos frisos, textura fina, brilho do primeiro plano, cor média, fronteiras de cor e preservação da entrada. Os números de testes referem-se a software e não validam fisicamente todas as lentes.

No ensaio do mesmo JPEG em resolução inteira no PC, o refino residual apresentou razão de detalhe 1,0003 e correlação de bordas aproximadamente 1,0000; o recorte dos frisos manteve continuidade/contraste. Esses índices são relativos à entrada já processada, sem referência limpa de verdade. O teste novo de captura da 1.27.1 foi interrompido antes do disparo porque outro aplicativo estava em foco; nenhuma ação foi enviada fora do Codex. Portanto, o resultado da cadeia inteira desta atualização ainda precisa de uma nova foto no Reno, e a melhora observada no PC foi modesta.

Os protótipos de movimento e `NightCapturePlan` mantêm um orçamento escolhido de até 15 quadros/3 s desejados no Noite, condicionado às observações e ao que o HAL permite. OIS só é considerado ativo se observado no resultado; seu funcionamento não foi demonstrado nas capturas recentes. RAW/DNG, retratos humanos, Noite e qualidade profissional em 6×–8× continuam sem validação física completa nesta versão.

## Comparação nativa OPPO concluída

Na terceira miniatura da Galeria, foi executada a sequência solicitada **Remoção de desfoque por IA → Aprimorar nitidez → Salvar cópia**. Entrada e cópia mantiveram 3072 × 4096; o original permaneceu byte a byte idêntico por SHA-256. A entrada já continha edição anterior, portanto não é um RAW de controle. Houve sombras mais uniformes e contornos mais definidos; letras pequenas continuam limitadas, com aparência de retoque em algumas formas. Não houve clareamento global. O algoritmo proprietário da OPPO não foi incorporado nem reproduzido.

As versões intermediárias 1.25.0–1.26.1 tiveram falhas de bitmap reutilizado, exposição escura e estimativa de movimento excessiva. O bitmap foi corrigido; captura direta e fluxo óptico no tempo de Foto foram abandonados. Os arquivos de diagnóstico continuam locais para rastreabilidade. A 1.27.0 aprovada pelo usuário permanece como APK local de referência.

## Correções implementadas no checkpoint 1.20.4

1. **Enquadramento entre visor e captura:** no Reno com foco não calibrado, o burst mantém o zoom e o campo de visão usados na prévia. Foi retirado desse caminho o salto temporário para zoom nativo seguido de recorte central. Esse salto mudava a condição de foco e, quando a fusão era rejeitada, podia salvar apenas um recorte de aproximadamente 1 MP. Nos testes posteriores, o arquivo passou a manter 4096 × 3072 e o frasco inteiro quando inteiro na prévia.
2. **Foco persistente:** resultados atrasados de buscas anteriores são rejeitados; o disparo aguarda a busca atual, com prazo e tentativas limitados. Há opção de AF contínuo na área tocada. É acompanhamento da profundidade nessa área, sem rastreamento de identidade do sujeito através do quadro. Foco manual e trava explícita têm prioridade.
3. **Proteção da tele de perto:** neste firmware específico, a tele declarou AF bem-sucedido com o rótulo ainda borrado no limite próximo. A versão 1.20.4 restaura a alternativa da principal nessa condição, mesmo se o AF declarar sucesso. O perfil é restrito ao modelo/firmware observado; posição de foco não calibrada não é convertida em distância física confiável.
4. **Ruído e textura:** limites de exposição por focal; giroscópio com janela temporal para permitir exposição maior quando o aparelho está estável; proteção adicional para retratos e modo Rua. O giroscópio mede movimento do aparelho, não congela movimento do sujeito. A fusão usa nitidez moderada, redução de ruído limitada pelo plano e evita afiação dupla. A verificação compara detalhe e forma das bordas com a referência, retornando ao quadro limpo se a fusão piorar o resultado. Poucas bordas produzem medição inconclusiva, sem aprovação fictícia.
5. **Qualidade de saída:** JPEG solicitado/salvo com qualidade 100; ficha registra dimensões, pixels, bytes, lente física, exposição, foco e decisões. O tamanho em MB não mede detalhe real. Foi removida a ampliação automática para 28 MP no caminho do Reno que não tinha evidência de ganho real.
6. **Resoluções públicas:** a busca inclui listas separadas de alta resolução JPEG e YUV. Na tele, 4096 × 3072 YUV é anunciado separadamente de 3264 × 2448; há retorno ao tamanho normal se o conjunto de streams for recusado. A aceitação física dessa configuração nova ainda precisa ser testada na versão 1.20.4.
7. **Cor e sensores:** leitura auxiliar dos sensores TCS3720 CCT e OPLUS Fusion RGB, com idade e precisão Android. O AWB e as matrizes da câmera são preservados. Não se inventam ganhos RGB a partir de Kelvin sem calibração por lente. O AWB fica estável durante o burst e é liberado depois, respeitando a trava do usuário.
8. **Retratos:** bokeh simulado com opções de f/5,6 a f/16, padrão f/8. Valores antigos abaixo de f/5,6 são limitados para evitar desfoque exagerado. Não altera a abertura física da lente. A textura humana continua sendo prioridade; os exemplos fornecidos são referências locais, sem treinamento ou calibração automática comprovada.
9. **RAW+:** preserva um DNG original com o resultado Camera2 correspondente ao timestamp e salva separadamente o JPEG revelado/fundido. O DNG não é apresentado como RAW fundido. A gravação real de DNG nesta atualização ainda precisa ser validada no aparelho.
10. **Fluidez:** MAX/AUTO reutilizam a mesma sessão YUV; modos compatíveis também reutilizam a sessão. Zoom por botão usa transição curta; gesto continua imediato. Trocas que exigem outro stream ou lente física ainda dependem do tempo da câmera. Aviso repetitivo de retorno ao quadro limpo foi retirado do disparo; a decisão permanece na ficha.

## O que o próprio Reno expôs

Modelo **CPH2863**, firmware **CPH2863_17.0.0.100(EX01)**. Evidência local, não versionada: `diagnostico_codex/reno16_capacidades_e_sensores.json`.

| Item observado | Resultado |
|---|---|
| IDs | 0 lógica; 1 frontal; 2 principal; 3 ultra-angular; 4 tele |
| Maior grade pública observada | 4096 × 3072, aproximadamente 12,6 MP |
| RAW público | 4096 × 3072 |
| Mapa de resolução máxima | Ausente no firmware consultado |
| Acesso público a 50/200 MP | Não exposto nas configurações Camera2 consultadas |
| Principal | 5,56 mm, f/1,8 |
| Ultra-angular | 2,31 mm, f/2 |
| Tele | 12,19 mm, f/2,8; posição próxima nominal 2,5, calibração UNCALIBRATED |
| TCS3720 CCT | Registrado e lido; amostra de 5.195 K, precisão Android 3, idade 62 ms |
| OPLUS Fusion RGB | Registrado e lido; canais brutos preservados, sem atribuir Kelvin |

Essas são capacidades expostas ao aplicativo, não uma medição da resolução física total dos sensores. Somar câmeras ou interpolar pixels não libera detalhe de 50/200 MP. Um ID lógico não representa uma quarta óptica traseira independente.

O Android define um mapa separado de streams para o modo de resolução máxima; a ausência desse mapa foi observada no próprio telefone. Referência técnica: [CameraCharacteristics — maximum resolution](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION).

Foi investigada também a rota oficial [OPPO CameraUnit](https://github.com/oppo/CameraUnit), que apresenta recursos como SAT, retrato e noite. O exemplo tem autenticação do aplicativo e consulta capacidades por modelo/modo. A documentação pública examinada não demonstra suporte a 50/200 MP no CPH2863; a lista do exemplo é antiga e não inclui este Reno. O SDK não foi incorporado como promessa de desbloqueio. Para uma futura integração, é necessário confirmar suporte e obter credencial própria para o pacote/assinatura do app pelo fluxo oficial da OPPO. Fontes: [README oficial](https://github.com/oppo/CameraUnit/blob/main/README.md), [adaptador com autenticação e consulta de tamanhos](https://github.com/oppo/CameraUnit/blob/main/camerax/src/main/java/com/oplus/ocs/camerax/adapter/cameraunit/CameraUnitAdapter.java), [serviço oficial](https://developers-eu.oppomobile.com/newservice/capability?pagename=cameraunit).

## Fotografias efetivamente conferidas

O frasco permaneceu na posição solicitada, a aproximadamente **40–60 cm** segundo o usuário. Houve mudanças de enquadramento ao longo da sessão; os registros não constituem ensaio controlado de laboratório nem comparação de cor calibrada.

| Zoom selecionado | Óptica registrada no teste 1.20.1 | Arquivo | Leitura do resultado |
|---|---|---|---|
| 0,6× | Ultra-angular 3 | 4096 × 3072; 4,67 MB | Fusão perdeu detalhe; preservado quadro limpo. Rótulo pequeno no quadro. |
| 1× | Principal 2 | 4096 × 3072; 4,37 MB | Preservado quadro limpo; ruído de sombra permanece. |
| 2× | Principal 2 | 4096 × 3072; 1,81 MB | Marca principal legível; fusão passou na comparação relativa. |
| 3,5× | Principal 2, alternativa de proximidade | 4096 × 3072; 1,54 MB | Frasco inteiro, marca e letras principais legíveis; ruído residual. |
| 6× | Principal 2 recortada | 4096 × 3072; 1,42 MB | Poucas bordas úteis; detalhe e legibilidade insuficientes para a meta. |
| 7× | Principal 2 recortada | 4096 × 3072; 1,37 MB | Medição com pouquíssimas bordas; não prova qualidade. |
| 8× | Principal 2 recortada | 4096 × 3072; 1,32 MB | Ampliação não trouxe letras finas reais; meta ainda não atingida. |

Originais e fichas permanecem locais e fora do Git, em `diagnostico_codex/comparacao_lentes/`. Resumo da primeira sequência: `diagnostico_codex/comparacao_lentes/todas_as_focais_1.20.1.json`.

Na versão experimental 1.20.3 foi testada também a **tele física 4 em 3,5×**, com foco explícito no rótulo. Apesar de AF = 4, o rótulo ficou borrado; a fusão foi rejeitada. Ajuste manual até o limite próximo também não resolveu nessa posição. Por isso 1.20.4 restabelece a proteção de proximidade. Isso não demonstra que a tele será ruim em distâncias maiores, que não foram testadas.

O giroscópio permitiu reduzir o ISO em capturas posteriores, mas houve mudança da área tocada e do enquadramento; esses registros não isolam a causa da qualidade observada. A latência de fusão medida na sequência 1.20.1 ficou entre aproximadamente **3,5 e 6,7 segundos**; não é captura instantânea e exclui parte do tempo de aquisição/salvamento.

## Memória de 50 milhões e referências

Complementos da pesquisa solicitada: [novidades de GitHub e Reddit](conhecimento/novidades_git_reddit_2026_11_10.md) e [referências de retrato enviadas pelo usuário](conhecimento/referencias_retrato_usuario_2026_11_10.md). São registros de pesquisa e validação proposta, sem troca automática de modelo ou algoritmo no APK instalado.

Dez eixos produzem exatamente **50.000.000 de combinações endereçáveis**, com **40 regras de referência**. A codificação/decodificação de todos os IDs passou em teste. Isso valida a indexação, não 50 milhões de fotos, cenas físicas, treinamento ou resultados profissionais. Eixos desconhecidos permanecem desconhecidos; o app consulta regras parciais sem inventar um ID completo.

As sete imagens fornecidas foram incorporadas como originais locais, com índice e SHA-256 em `ClaraCam/app/src/main/assets/references/`. São referências de observação e acabamento, sem substituir a fotografia capturada por detalhes gerados. Não foram publicadas no GitHub.

## Validação do checkpoint 1.20.4 e pendências materiais

- `assembleDebug` e `testDebugUnitTest`: **56 testes, zero falhas e zero erros**. Incluem fusão, RAW, bokeh, qualidade relativa, foco atrasado, giroscópio, sensor de cor e todos os IDs da memória.
- Instalação automática da **1.20.4 concluída**, sem desinstalar o pacote ou apagar dados. Testes fotográficos anteriores estão registrados acima; os novos caminhos desta versão permanecem sujeitos às validações físicas descritas abaixo.
- Conferência em 10/11 às 14h33: Codex 1.20.4/código 44 e pacote original 1.19.2/código 39 presentes separadamente. Registro local de versão e hash: `diagnostico_codex/instalacao_1.20.4.json`. A chamada ainda estava ativa; os testes de tela continuavam suspensos.
- Durante a ligação, os comandos de tela foram interrompidos. O script passou a confirmar a janela Codex antes de cada toque e antes da captura do visor.
- Ainda precisam de validação física na versão final: aceitação do YUV de alta resolução da tele, estabilidade visual ao alternar MAX/AUTO, saída RAW/DNG, retrato humano em peles claras e escuras e comparação controlada de cor/textura.
- **A meta de qualidade profissional em 6×–8× nessa proximidade ainda não foi demonstrada.** Não há equivalência comprovada com Sony, Hasselblad, Leica, Nikon ou Phase One nem uso de seus perfis proprietários. O limite óptico, a luz e os dados efetivamente disponíveis continuam determinantes.
