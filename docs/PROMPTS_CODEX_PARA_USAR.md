# PROMPTS PRONTOS PARA CODEX — THE NATURAL CAM E AGRO GESTÃO

Use após abrir o repositório correto. Respeite o `AGENTS.md` existente e as diretrizes específicas. Não cole instruções de ambos os apps no mesmo repositório.

## A. Prompt de diagnóstico inicial, sem alterar código

```text
Faça uma auditoria SOMENTE LEITURA do repositório atual.
Identifique branch, commit, estado do git, versões, arquitetura,
componentes e instruções persistentes relevantes. Localize os pontos de
entrada da funcionalidade [DESCREVER]. Identifique duplicações, riscos de
regressão, testes existentes, problemas observáveis e fontes de verdade.
Não modifique arquivos, não atualize dependências e não crie commits.
Entregue um diagnóstico com arquivos e linhas, causa provável, evidências,
incertezas e plano mínimo verificável. Só faça pergunta se a decisão alterar
materialmente a arquitetura.
```

## B. Prompt de execução com limites

```text
Objetivo: [COMPORTAMENTO DESEJADO].
Usuário/cenário: [CONTEXTO E IMPACTO DA FALHA].
Baseline/reprodução: [PASSOS OBSERVADOS].
Preservar: [INVARIANTES DE FUNCIONALIDADE, UI, DADOS, PERFORMANCE].
Escopo permitido: [COMPONENTES/ARQUIVOS OU ÁREA A LOCALIZAR].
Fora do escopo: [ALTERAÇÕES QUE NÃO DEVE EFETUAR].
Concluído quando: [TESTES E EVIDÊNCIA OBSERVÁVEL].

Inspecione antes de editar; identifique causa raiz; faça correção mínima;
execute build, testes pertinentes e revisão independente quando justificada.
Prossiga autonomamente nas operações locais não destrutivas. Não faça
alterações externas, destrutivas, de assinatura, release ou push sem
aprovação explícita. Registre tudo que não foi possível testar.
Relate evidências reais e pendências, sem prometer conclusão não validada.
```

## C. Prompt de revisão independente

```text
Atue como revisor de código com contexto independente. Compare o diff
atual com o comportamento original e os critérios de aceite. Priorize
bugs de severidade alta, regressões, concorrência, segurança, duplicação,
inconsistência de dados, desempenho em Android e testes faltantes.
Cite arquivos/linhas, descreva como reproduzir cada risco e proponha o
menor ajuste. Não presuma que o autor da alteração esteja correto.
Se não conseguir executar o projeto, diferencie claramente hipótese de
falha reproduzida. Não modifique arquivos nesta revisão.
```

## D. Prompt de fechamento e entrega

```text
Audite a implementação frente aos critérios originais. Identifique o que
passou, falhou, não foi executado ou não pode ser verificado. Confira
`git diff`, resultados dos comandos, testes e artefatos realmente gerados.
Não afirme que houve teste em aparelho sem log ou observação comprovável.
Não afirme que existe APK se o arquivo não existir.
Produza relatório: base/versão, objetivo, causa raiz, arquivos alterados,
comandos e resultados, regressões, revisões, APK e assinatura (se aplicável),
status do Git, pendências e riscos. Informe se houve publicação autorizada.
```

## E. Prompt para alinhar AGENTS.md à nova diretriz

```text
Leia o AGENTS.md atual, a DIRETRIZ_MESTRA_PROMPTS_CODEX.md e o modelo
AGENTS específico deste aplicativo. Sem modificar código do app, proponha
um merge mínimo das instruções, preservando regras vigentes relevantes.
Aponte conflitos, duplicidades e instruções obsoletas com justificativa.
Não sobrescreva AGENTS.md sem mostrar um diff para revisão.
Mantenha orientações persistentes concisas e deixe detalhes operacionais
em documentos referenciados por assunto. Não faça commit nem push.
```

## F. Prompt para medir se esta diretriz realmente melhora o trabalho

```text
Monte avaliação controlada de cinco tarefas representativas, usando casos
equivalentes: correção simples, bug com regressão, funcionalidade transversal,
revisão independente e problema de performance. Compare prompts curtos com
esta diretriz sob as mesmas condições de repositório e ferramentas.
Meça: taxa de aceite, testes executados, bugs regressivos, alterações fora de
escopo, custo de tokens quando acessível, tempo e retrabalho. Não confunda
mais código/mais tokens com maior qualidade. Reporte incertezas.
```