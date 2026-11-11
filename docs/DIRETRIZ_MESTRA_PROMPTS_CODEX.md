# DIRETRIZ MESTRA — PROMPTS PARA CODEX

**Aplicação:** The Natural Cam e Agro Gestão  
**Versão:** 1.0 — 11/11/2026  
**Status:** Documento de referência integrado ao repositório. Sua presença não autoriza, por si só, modificações no aplicativo, builds, releases ou deploys.

## 1. Origem e alcance

Baseada na transcrição do vídeo https://www.youtube.com/watch?v=lzYrQ9LO0WM (aprox. 23 min), que interpreta dez recomendações de prompting para Claude Fable 5.2, e adaptada para o Codex com apoio nos guias oficiais da Anthropic e OpenAI. **As técnicas relatadas para Claude não são garantias de desempenho do Codex.** Testar em tarefas representativas dos aplicativos antes de padronizar.

Fontes:
- Anthropic, Prompting Claude Fable 5.2: https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/prompting-claude-fable-5-2
- OpenAI, Rethinking skills and prompts for GPT-6 Astra: https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra
- OpenAI, Using Goals in Codex: https://developers.openai.com/cookbook/examples/codex/using_goals_in_codex
- OpenAI, Iterating Development Workflows with Codex: https://developers.openai.com/cookbook/examples/codex/iterating-development-workflows-with-codex
- OpenAI, Custom Code Review rules for Codex: https://developers.openai.com/blog/custom-code-review-rules-for-codex

## 2. Tradução das dez regras do vídeo para Codex

**R01 — Ajustar esforço à tarefa.** Usar esforço médio em correções localizadas, alto em múltiplos módulos, depuração complexa, arquitetura ou fotografia computacional. Esforço máximo somente após evidência de benefício; o nome/configuração exatos dependem da interface e modelo instalados. Não escrever "pense menos" como substituto de configuração.

**R02 — Declarar intenção e contexto.** Explicar usuário, ambiente, comportamento desejado, consequências de falha, restrições e impacto nas demais áreas. Explicar por que a função existe é mais útil do que apenas nomeá-la.

**R03 — Definir conclusão verificável.** Estabelecer uma Definition of Done antes da edição. Definir critérios observáveis, comandos de testes reais, cenários, artefatos e o que fica condicionado ao aparelho físico.

**R04 — Assumir responsabilidade técnica sem carta branca.** O agente deve corrigir a causa raiz e evitar duplicações e remendos. Pode reorganizar apenas o código diretamente afetado, preservando contratos, aparência e dados. Refatorações fora do escopo precisam de aprovação. A frase "o projeto é seu" não é recomendada literalmente: segundo a Anthropic, pode provocar alterações não solicitadas.

**R05 — Revisão independente baseada no risco.** Em trabalhos relevantes, chamar revisor/subagente com contexto de aceitação e diff, sem deixá-lo apenas confirmar a solução original. Se não houver subagentes, executar segunda revisão focada em regressões, segurança, cálculos e casos de borda. Testes automatizados continuam indispensáveis.

**R06 — Permissões e fronteiras explícitas.** Autorizar exploração de arquivos e testes locais não destrutivos. Solicitar consentimento antes de operações externas, commits/push, publicar APK, trocar assinatura/chave, editar dados de produção, tocar em credenciais, apagar dados reais, alterar contratos ou adicionar dependências relevantes. A aprovação de uma alteração não autoriza ações irreversíveis não solicitadas.

**R07 — Auditar instruções antigas.** Examinar `AGENTS.md`, documentação de projeto, prompts, logs de decisões e instruções de versões anteriores. Corrigir conflitos e retirar regras desatualizadas, sem apagar decisões importantes sem rastreabilidade. Orientações persistentes devem ser curtas, escopadas e verificáveis.

**R08 — Eliminar frases não operacionais.** Evitar "faça perfeito", "não erre", "pense pouco", "gaste no máximo X tokens" como únicas especificações. Substituir por critérios mensuráveis e configurações da ferramenta. A qualidade é avaliada pelos testes e não pela retórica.

**R09 — Perguntar somente sobre decisões de arquitetura genuinamente ausentes.** Quando faltar informação capaz de mudar o desenho do sistema, formular uma pergunta por vez, e não escrever código prematuramente. Em tarefa clara, primeiro inspecionar o código e agir sem perguntas repetitivas.

**R10 — Relatório final de evidências.** Explicar objetivo, arquivos modificados, comportamento antes/depois, comandos de compilação e testes com resultado, verificação em aparelho real (se houve), limitações, pendências, riscos e situação de commit/APK. Nunca escrever "validado" quando somente compilou.

## 3. Regras adicionais obrigatórias para os dois apps

**R11 — Diagnóstico antes da mudança.** Identificar versão, branch, hash do commit-base, arquitetura real, arquivos envolvidos, dependências, sintomas e forma de reproduzir. Não inventar caminhos, recursos do dispositivo ou testes executados.

**R12 — Mudanças pequenas e testáveis.** Preferir patch mínimo que resolve causa raiz. Para mudanças maiores, executar por marcos independentes. Não criar UI paralela, duplicar regras de negócio, copiar dados ou reescrever módulos sem justificativa e autorização.

**R13 — Redução de regressão.** Antes/depois: registrar baseline, criar teste de reprodução sempre que viável, executar testes unitários/instrumentados pertinentes, build, inspeção de diff, UX e verificação de persistência. Para Android, diferenciar testes locais de testes em dispositivo.

**R14 — Três estados de validação.** `VALIDADO` somente com evidência executada; `PARCIAL` quando testes locais passaram mas faltam ensaios físicos/serviços; `BLOQUEADO` quando faltam permissões, ambiente, dados ou dispositivos. Não substituir evidência por afirmação.

**R15 — Conservar identidade visual.** Não redesenhar telas ou criar componentes visuais desconexos para resolver um bug. Respeitar hierarquia, tipografia, espaçamento, componentes, acessibilidade e padrão já aprovado. Avaliar telas em aparelho básico e principal conforme disponibilidade.

**R16 — Dados reais e privacidade.** Testes devem usar dados sintéticos ou cópias sanitizadas. Não registrar segredos, chaves PIX, dados pessoais, identificadores nem informações confidenciais em logs, screenshots e relatórios.

**R17 — Autonomia limitada para persistir.** Diagnosticar erro, corrigir, executar novamente até concluir dentro do escopo e recursos disponíveis. Se houver bloqueio real, parar e documentar. Nunca tentar contornar mecanismos de aprovação nem executar ciclos ilimitados.

**R18 — Não alterar releases sem autorização.** VersionCode, versionName, assinatura, canal de distribuição, substituição de APK anterior, tag/release, commits e push devem seguir decisões explícitas do projeto. Preparar diffs ou artefatos locais não equivale a publicá-los.

**R19 — Revisão da revisão.** Uma revisão independente não prova ausência de falhas. Usar checks de CI, regressões, medições e avaliação humana; em falha, voltar à fase de diagnóstico e reportar limites.

## 4. Estrutura padrão de prompt de trabalho

```text
[OBJETIVO]
O que precisa passar a funcionar e por quê.

[CONTEXTO DE USO]
Quem usa, em qual aparelho/ambiente e o impacto da falha.

[ESTADO ATUAL]
Repositório, branch, versão-base, sintoma e passos reproduzíveis.
Não presumir caminhos nem classes: localizar no código.

[ESCOPO]
O que pode ser modificado e o que está fora do pedido.

[INVARIANTES]
Comportamentos, contratos, cálculos, dados, desempenho,
identidade visual e compatibilidade que precisam permanecer.

[CRITÉRIOS DE ACEITE]
Condições observáveis que demonstram sucesso.

[EXECUÇÃO]
Inspecione código e documentação pertinente; reproduza; encontre
a causa raiz; faça a menor correção sustentável; teste e revise.
Prossiga sem pedir aprovação em ações locais seguras.
Pare em decisões de arquitetura realmente abertas ou ações sensíveis.

[VALIDAÇÃO]
Execute os testes disponíveis e verifique o resultado real.
Informe quando não houver aparelho, credenciais ou serviço para testar.

[RELATÓRIO]
Arquivos alterados; causa raiz; evidência antes/depois; comandos,
resultados; pendências; riscos; artefatos; status de commit/publicação.
```

## 5. Política de planejamento e encerramento

Tarefas simples: prompt direto com critério de aceite. Tarefas complexas: usar `/plan` quando disponível para escolher abordagem antes da edição. Problemas com meta persistente e verificação repetida: `/goal` quando suportado. Nunca colocar um objetivo vago como "melhorar tudo"; usar um indicador observável e testes que protegem o comportamento existente.

Em cada marco: diagnóstico -> reprodução/linha de base -> implementação mínima -> testes relevantes -> revisão -> documentação. Ao final, cada requisito fica `Concluído`, `Parcial`, `Bloqueado` ou `Não executado`, com justificativa. **Não entregar uma versão se o requisito crítico está falhando.**

## 6. Modelo de relatório final

```text
RESULTADO: [VALIDADO | PARCIAL | BLOQUEADO]
BASE EXAMINADA: branch / hash / versão
REQUISITO: ...
CAUSA RAIZ: ...
ARQUIVOS ALTERADOS: ...
COMPORTAMENTO PRESERVADO: ...
TESTES EXECUTADOS: [comandos + saídas observadas]
TESTES NÃO EXECUTADOS: [motivo]
REGRESSÕES VERIFICADAS: ...
REVISÃO INDEPENDENTE: [sim/não; achados]
BUILD/APK: [gerado e caminho verificado | não gerado]
GIT: [sem mudanças remotas | commit local | push realizado com autorização]
PENDÊNCIAS/RISCOS: ...
```

## 7. Métricas para validar se a diretriz realmente melhora o Codex

Executar conjunto comparável de tarefas com e sem a diretriz; acompanhar taxa de critérios aceitos, regressões introduzidas, testes realmente executados, número de retrabalhos, tempo de ciclo, tokens/custo (quando disponíveis), diffs fora de escopo e precisão de relatórios. A comparação isolada de dois jogos mostrada no vídeo não é um experimento controlado que prove ganho universal.