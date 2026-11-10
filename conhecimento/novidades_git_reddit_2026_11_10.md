# Novidades úteis para a qualidade do The Natural Cam

Pesquisa de 10/11/2026, cruzada com o código Codex e com as medições do Reno CPH2863. Processador informado pelo próprio aparelho: **MediaTek MT6899**. Este documento registra evidências e experiências propostas; não ativa algoritmos nem substitui a validação física.

## Resultado prático

As prioridades são **preservar o foco e o enquadramento**, melhorar a seleção/alinhamento dos quadros e medir ruído sem apagar letras ou textura humana. Novos modelos de redução de ruído merecem teste de velocidade e fidelidade. Nenhuma fonte examinada demonstrou liberar 50/200 MP neste Reno.

### 1. Burst Super-Res: novidade concreta de novembro

O projeto [skittleson/burst-superres](https://github.com/skittleson/burst-superres) publicou a [versão 0.4.0 em 09/11/2026](https://github.com/skittleson/burst-superres/releases/tag/v0.4.0). É um aplicativo de desktop, com licença MIT, que apresenta alinhamento subpixel global/local, rejeição de movimento e de quadros ruins, normalização de brilho e avaliação da nitidez por um quadro separado. O suporte RAW descrito é Sony ARW, não DNG do Reno. Os ganhos divulgados são do autor, em exemplos próprios e simulações; não foram reproduzidos aqui.

**Aplicação proposta:** selecionar a referência pela região do sujeito, medir confiança do alinhamento e testar uma imagem reservada que não participou da fusão. Conferir a forma das letras e a textura, além da energia de borda. Duplicar largura/altura não comprova duplicação do detalhe real. Não foi instalado nem incorporado código desse projeto ao app.

**Diferença importante no app atual:** `BurstFusion` seleciona a referência pela nitidez global. O refinamento fracionário existe no caminho de super-resolução, mas a fusão normal ainda usa deslocamentos inteiros por blocos de 128 px. No caminho do Reno que mantém o zoom constante para preservar foco, a super-resolução está desativada. Portanto, alinhamento subpixel na fusão normal é um candidato real de melhoria, não uma função que já esteja resolvida.

### 2. Redução de ruído leve: Mobile AI 2026

O [relatório dos organizadores, submetido em 31/10/2026](https://arxiv.org/abs/2611.00974), apresenta modelos LiteRT para redução de ruído com avaliação em **1920 × 1088**. Há resultados inferiores a 100 ms em GPUs de referência, incluindo Snapdragon 8 Elite Gen 5 e Dimensity 9500. Esses números não medem 12,6 MP, o MT6899 do Reno nem o fluxo completo de captura/salvamento. [Tabela e metodologia](https://arxiv.org/html/2611.00974v1).

**Aplicação proposta:** comparar um modelo leve com o NAFNet já embarcado, em entradas reais do Reno, medindo tempo, memória, alteração de cor e perda de textura. O app usa NAFNet no **editor**, não no caminho automático do burst; trocar esse modelo sozinho não acelera a fusão Java de 3,5–6,7 s observada nos testes.

O estudo [LiteDenoiseNet / distilação para NPUs](https://arxiv.org/abs/2605.03680) aponta arquiteturas com operações mais compatíveis com aceleradores móveis e remete ao [catálogo dos autores](https://github.com/ABrain-One/nn-dataset). É candidato de pesquisa; não foi confirmado um checkpoint específico, conversão e licença de pesos adequados para substituição direta no app.

**Critério de adoção:** medir o arquivo salvo no Reno; um modelo de regressão também pode apagar ou alterar textura. Nome de rede, PSNR de outro sensor e ausência de difusão não garantem fidelidade de poros ou letras.

### 3. OPPO CameraUnit: rota oficial, ainda sem prova de alta resolução

O [CameraUnit oficial](https://github.com/oppo/CameraUnit) expõe modos/capacidades do fabricante e apresenta SAT, retrato e noite. O exemplo contém autenticação e consulta tamanhos por modo. A lista de aparelhos é antiga e não inclui este Reno. Não há demonstração pública examinada de 50/200 MP no CPH2863.

**Aplicação proposta:** confirmar suporte ao pacote/assinatura do The Natural Cam e capacidades deste firmware pelo fluxo oficial, antes de criar um backend opcional. A ausência do mapa Camera2 de resolução máxima foi medida no telefone; um SDK com autenticação não está automaticamente liberado por o telefone ter um sensor de 200 MP. [Adaptador oficial](https://github.com/oppo/CameraUnit/blob/main/camerax/src/main/java/com/oplus/ocs/camerax/adapter/cameraunit/CameraUnitAdapter.java).

### 4. PhotonCamera e HDR+: referências, com datas conferidas

- [PhotonCamera](https://github.com/eszdman/PhotonCamera): o último push consultado é de **06/10/2026**. Os três commits mais recentes tratam de build/CI; não encontrei neles uma nova melhoria de imagem para transferir. É referência de arquitetura RAW/GPU, com licença GPL-3.0; nenhum código foi copiado. [Último commit examinado](https://github.com/eszdman/PhotonCamera/commit/b2eff821a858033515eb2700e7292befecc95225).
- [timothybrooks/hdr-plus](https://github.com/timothybrooks/hdr-plus): implementação MIT mantida por Simon Gardling; último push consultado de **13/01/2026**. Serve para avaliar um protótipo RAW nativo, sem atribuir ao port a qualidade ou o desempenho de um Pixel atual.
- A [super-resolução RAW do Google](https://research.google/pubs/handheld-multi-frame-super-resolution/) é de **2019**, não uma novidade de 2026. Combina amostras CFA com deslocamentos reais, alinhamento e robustez a movimento. Ampliar uma imagem YUV já suavizada pelo ISP não reproduz esse processo.

O [NAFNet oficial](https://github.com/megvii-research/NAFNet), já usado no app, teve último push consultado em 03/07/2024. Atualização de estrelas/metadados do GitHub não significa atualização do algoritmo. A licença raiz contém MIT e avisos de componentes adicionais; não houve troca de modelo.

## O que o Reddit acrescentou

| Relato consultado | Utilidade para os testes | Limite da evidência |
|---|---|---|
| [Find X9 Pro: stock × GCam, 11/03/2026](https://www.reddit.com/r/Oppo/comments/1rqywhd/find_x9_pro_stock_vs_gcam_samples/) | Comparar letras e tecidos; acabamento agressivo pode aumentar contorno e apagar textura. | Relato do autor, configuração própria e outro hardware; não prova qual ajuste funciona no Reno. |
| [Find X9 Ultra: comparação de zoom, 07/07/2026](https://www.reddit.com/r/Oppo/comments/1uq4lex/find_x9_ultra_long_range_zoom_comparison_stock/) | Conferir simultaneamente ruído, cor, aberração cromática e forma das letras. | O autor descreve vantagens e defeitos em ambos os apps, em zoom próximo de 100×; não é ensaio controlado de 3,5×–8× no Reno. |
| [Reno14 após atualização, 30/06/2026](https://www.reddit.com/r/Oppo/comments/1ujldkk/any_reno_14_users_who_feels_that_camera_is/) | Verificar consistência de WB entre principal, ultra-angular e tele e suavidade da troca. | Comentários de usuários, sem calibração, em outro modelo/firmware. |

Não foram instalados APKs, XMLs pagos ou configurações de outros aparelhos. Não se adotou como fato a alegação genérica de que apps jamais leem sensores de cor: o próprio CPH2863 forneceu leituras TCS3720 e Fusion RGB neste trabalho. Isso comprova leitura; não comprova calibração espectral nem uso do processamento proprietário da OPPO.

## Próximos experimentos, por ganho esperado

1. **Referência e verificação na área escolhida:** impedir que balcão/fundo mais contrastado determine o melhor quadro enquanto o rótulo ou olho está mole. Registrar a região e sua transformação para a imagem salva; testar orientação, zoom e troca de lente.
2. **Fusão normal com subpixel e confiança local:** protótipo separado, comparado com a versão 1.20.4 usando o mesmo burst. Rejeitar alinhamento incerto e movimento; observar custo de CPU/memória e costuras.
3. **Quadro independente para validação:** reservar uma captura para avaliar reconstrução e afiação, com compensação de exposição. Aumento de contraste ou grão não vale como ganho de detalhe.
4. **RAW e perfis por lente/ISO/firmware:** usar ruído e metadados do sensor no domínio correto; não tratar coeficientes RAW como um modelo exato para YUV pós-ISP. Validar DNG, WB e sombras antes de levar RAW ao automático.
5. **Modelo leve no editor e, depois, captura:** comparar velocidade e textura em 12,6 MP no MT6899; manter escolha e original. Uma rede genérica não substitui alinhamento e foco óptico.

Aceitação: mesmo enquadramento entre visor e foto; olho/rótulo escolhido em foco; letras com forma original; ausência de halos, pele plástica e costuras; neutros e tons de pele coerentes; latência real registrada. Testar 0,6×, 1×, 2×, 3,5×, 6×, 7× e 8×. A posição atual do frasco, a 40–60 cm, permanece como solicitada. Ensaios adicionais de distância dependem da disponibilidade do usuário; não foram simulados como já realizados.

## Estado do Git do próprio app

A consulta à branch principal do repositório [the-natural-cam](https://github.com/alexduvier-svg/the-natural-cam), antes da publicação do trabalho Codex, continuou mostrando [6d6ae30 / 1.19.0](https://github.com/alexduvier-svg/the-natural-cam/commit/6d6ae300832d1bd66e5ba3ebdb438fc6b040c8b7) como commit mais recente, a mesma base deste worktree. Não foi encontrada nova alteração remota para incorporar. O trabalho não commitado do Claude não foi importado.
