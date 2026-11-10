# The Natural Cam Codex — implementação e testes no Reno 16 Pro

Registro de 10/11/2026. Versão instalada no Reno: **1.20.4-codex**, código 44. Android confirmou `Success`, `versionCode=44` e `versionName=1.20.4-codex`.

## Entrega e isolamento

- Diretriz: [DIRETRIZ_THE_NATURAL_CAM.md](DIRETRIZ_THE_NATURAL_CAM.md).
- APK: `The_Natural_Cam_Codex_1.20.4.apk`.
- SHA-256: `0439DE67C16F73B7AD316727E52B5E13C0B81E105B36BE8C949FDDAB76D518DD`.
- Pacote exclusivo: `br.com.duvier.thenaturalcam.codex`; nome no celular: **The Natural Cam Codex**.
- Fotos: `DCIM/TheNaturalCamCodex`; fichas: `Documents/TheNaturalCamCodex`.
- Branch: `codex/natural-cam-memory`, neste worktree. A pasta e o pacote do Claude permanecem separados. O usuário autorizou commit local e envio a esta branch ao fim de cada tarefa; não há merge na branch do Claude.
- Instalador autorizado: [tools/Install-RenoCodex.ps1](tools/Install-RenoCodex.ps1). Atualiza o pacote Codex por ADB, preserva dados e não abre a câmera.

## Correções e melhorias implementadas

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

## Validação e pendências materiais

- `assembleDebug` e `testDebugUnitTest`: **56 testes, zero falhas e zero erros**. Incluem fusão, RAW, bokeh, qualidade relativa, foco atrasado, giroscópio, sensor de cor e todos os IDs da memória.
- Instalação automática da **1.20.4 concluída**, sem desinstalar o pacote ou apagar dados. Testes fotográficos anteriores estão registrados acima; os novos caminhos desta versão permanecem sujeitos às validações físicas descritas abaixo.
- Conferência em 10/11 às 14h33: Codex 1.20.4/código 44 e pacote original 1.19.2/código 39 presentes separadamente. Registro local de versão e hash: `diagnostico_codex/instalacao_1.20.4.json`. A chamada ainda estava ativa; os testes de tela continuavam suspensos.
- Durante a ligação, os comandos de tela foram interrompidos. O script passou a confirmar a janela Codex antes de cada toque e antes da captura do visor.
- Ainda precisam de validação física na versão final: aceitação do YUV de alta resolução da tele, estabilidade visual ao alternar MAX/AUTO, saída RAW/DNG, retrato humano em peles claras e escuras e comparação controlada de cor/textura.
- **A meta de qualidade profissional em 6×–8× nessa proximidade ainda não foi demonstrada.** Não há equivalência comprovada com Sony, Hasselblad, Leica, Nikon ou Phase One nem uso de seus perfis proprietários. O limite óptico, a luz e os dados efetivamente disponíveis continuam determinantes.
