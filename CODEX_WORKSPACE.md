# Ambiente Codex — The Natural Cam

Criado em 10/11/2026, por solicitação do usuário, para manter o trabalho separado do Claude.

- Worktree: C:/Users/alexd/.codex/worktrees/natural-cam-codex/App Natural Cam
- Branch: codex/natural-cam-memory
- Origem: snapshot da branch main; mudanças não commitadas do Claude não foram transportadas.
- Escopo: app Android, diretriz, leitor de 50.000.000 de combinações e testes autorizados no Reno.
- Fonte principal: conhecimento/diretriz_fotografica_50_milhoes.md
- Base embarcável: ClaraCam/app/src/main/assets/knowledge/
- Leitor local: PhotoKnowledge.java; endereçamento: PhotoScenarioSpace.java.

## Evidência de validação

O endereçamento passou em três testes JUnit, incluindo a codificação e decodificação de todos os 50.000.000 de IDs. Isso comprova indexação, não qualidade fotográfica dos cenários. Catálogo: dez eixos, quarenta regras, condições válidas; cópias do guia com SHA-256 idêntico.

O bloqueio inicial de SDK foi resolvido com SDK independente em `.android-sdk/`. A compilação Android completa e os testes passaram. O app Codex foi instalado e testado no Reno por autorização do usuário. A base textual é de referência; o comportamento implementado e o que ainda exige validação física estão separados no relatório.

Este worktree separa arquivos e índice de trabalho; o repositório Git de origem e o histórico continuam compartilhados, como é próprio de worktrees. Não usar operações que alterem a branch ou os arquivos do Claude. Por autorização do usuário em 10/11/2026, cada tarefa concluída com alterações recebe commit local e envio à branch exclusiva `codex/natural-cam-memory`. Não há merge na branch do Claude. Fotos originais, diagnósticos do aparelho e compilações permanecem locais e ignorados pelo Git.

## Preservação da origem

Durante a transferência, PhotoKnowledge.java recebeu alteração concorrente na pasta original. Por isso o conjunto original foi preservado, para não remover dependências do trabalho em andamento. A entrada de README adicionada pelo Codex foi retirada. Não houve exclusão dos seis arquivos da diretriz na origem; futuras alterações do Codex ocorrerão somente neste worktree.

## Estado em 10/11/2026 — app Codex 1.20.4

O app é baseado no commit 6d6ae30, com melhorias de captura e instalação solicitada no Reno 16 Pro. A suíte atual contém 56 testes, todos aprovados, incluindo o percurso de todos os IDs da memória, regressões de ruído/textura em tons claros e escuros, foco, giroscópio e bokeh. Registro completo: [RELATORIO_THE_NATURAL_CAM_CODEX.md](RELATORIO_THE_NATURAL_CAM_CODEX.md).

Pacote exclusivo: br.com.duvier.thenaturalcam.codex. APK de entrega: The_Natural_Cam_Codex_1.20.4.apk. As sete referências fornecidas pelo usuário foram copiadas para assets/references, preservadas byte a byte e verificadas por SHA-256. Permanecem locais, sem publicação no GitHub ou treinamento de modelo.

O Android confirmou a instalação automática da 1.20.4 no Reno (`Success`, código 44), por ADB, sem apagar dados.

A implementação usa classificação parcial da cena, sem inventar eixos ausentes. Consulta da base e regras executadas são registradas separadamente. O foco contínuo acompanha a profundidade dentro da área selecionada, sem rastreamento semântico de identidade através da imagem. O bokeh permanece simulado, de f/5,6 a f/16, padrão f/8. A referência a marcas não significa uso de seus perfis proprietários nem equivalência comprovada.

Os sensores TCS3720 CCT e OPLUS Fusion RGB foram confirmados no sensorservice do próprio Reno. Os canais são registrados como observações auxiliares; corrigir ganhos RGB a partir do sensor exige calibração específica. O tamanho em MB não é o critério de resolução: cada JPEG passa a registrar bytes, largura, altura e megapixels na ficha.

As fotos de 0,6× a 8× foram salvas, extraídas e conferidas. O reenquadramento do burst e o recorte de aproximadamente 1 MP foram corrigidos no caminho do Reno. A tele confirmou foco falsamente no limite próximo; 1.20.4 restaura a alternativa de proximidade nesse modelo/firmware. O ganho profissional em 6×–8× a 40–60 cm ainda não está demonstrado. RAW/DNG e retratos humanos ainda precisam de validação física nesta atualização. Não confundir testes de software aprovados com essa validação.

## Atualização de referência — 1.27.1-codex

Integração do snapshot publicado `cee696e` (Claude 1.24.1), somente nesta branch. A 1.27.0 recuperou a estratégia de captura de referência; o usuário relatou redução importante de ruído em 3,5× e aprovou a transição suave de zoom. A 1.27.1 conserva essa base e ajusta o ruído residual/realces por área, usando os frisos da tampa, as letras e as superfícies lisas como critérios de inspeção.

O protocolo 1.1.0 está no guia local e embarcado. O relatório registra a versão instalada, verificações e limitações reais; os protótipos de movimento não comandam Foto. Diagnósticos e originais continuam privados/ignorados, e o pacote do Claude não foi atualizado pelo Codex.

A atualização 1.27.1/código 67 foi instalada por ADB com confirmação `Success`. Compilação e suíte completa passaram: 90 testes, 88 aprovados e 2 demonstrações opcionais ignoradas, sem falhas/erros. A nova captura física desta atualização ficou pendente porque a proteção encontrou outro pacote em foco e não enviou o disparo.
