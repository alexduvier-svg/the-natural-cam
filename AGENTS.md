# Ambiente exclusivo do Codex — The Natural Cam

Diretriz do usuário em 10/11/2026: não escrever junto com o Claude, para não travar seu desenvolvimento.

- Trabalhar somente neste worktree gerenciado do Codex, em sua branch codex/natural-cam-memory.
- A pasta C:/Users/alexd/PROJETOS/App Natural Cam pertence ao desenvolvimento paralelo do Claude; tratá-la como somente leitura.
- Não alterar arquivos, índice Git, branch, builds ou configuração da pasta do Claude.
- Não copiar alterações não commitadas do Claude para este ambiente nem mesclar/publicar alterações na branch dele sem solicitação do usuário.
- Compilações, testes e artefatos do Codex ficam neste worktree. Não instalar APK nem dirigir o celular enquanto isso puder interferir no trabalho paralelo, sem instrução do usuário para a instalação.
- Preservar as preferências permanentes de qualidade e fidelidade visual fornecidas pelo usuário.
- Para a memória fotográfica, distinguir combinações enumeradas, regras de referência, execução no app e validação física. Não alegar treinamento em 50 milhões de fotos.

## Autorizações adicionais do usuário em 10/11/2026

- Instalar automaticamente as atualizações do app Codex no Reno 16 Pro por ADB, sem depender de toques manuais para instalar; preservar o pacote e o desenvolvimento do Claude.
- Operar o foco, o zoom e o disparo para testar as fotos nas diferentes focais e avaliar os arquivos efetivamente salvos.
- Dispensar a tela de bloqueio pelo comando padrão do Android somente quando não exigir senha. Não tentar descobrir, adivinhar ou contornar senha, PIN ou biometria.
- Antes de cada ação de tela no Reno, confirmar que a janela focada é do pacote br.com.duvier.thenaturalcam.codex. Interromper imediatamente se houver ligação ou outro app em foco; não executar sequências cegas de toques após desbloqueio ou mudança de atividade.
- O usuário liberou os testes durante a chamada em segundo plano, usando fone. Nessa situação explicitamente autorizada, a chamada ativa por si só não bloqueia a câmera: confirmar a janela Codex antes de cada ação e interromper se houver chamada recebida ou outra janela em foco. Nunca operar os controles da ligação.

## Encerramento de tarefas — preferência de 10/11/2026

- Ao concluir cada tarefa com alterações, criar o commit local e enviá-lo ao GitHub na branch exclusiva `codex/natural-cam-memory`, sem pedir novamente autorização para esse fluxo.
- Fazer as verificações adequadas antes do commit e informar o hash e o resultado do envio. Não criar commits vazios quando não houver alterações.
- Publicar apenas o trabalho do Codex. Não fazer merge nem enviar alterações à branch do Claude.
- Manter fotos privadas/de teste, diagnósticos locais do aparelho, SDK, credenciais e artefatos de compilação fora do commit. Referências visuais originais permanecem locais.

## Diretrizes complementares de prompting e validação (11/11/2026)

- Para mudanças complexas, consultar `docs/DIRETRIZ_MESTRA_PROMPTS_CODEX.md` e o modelo contextual `docs/AGENTS_NATURAL_CAM_MODELO.md`; usar `docs/PROMPTS_CODEX_PARA_USAR.md` apenas quando pertinente.
- Antes de mudar o pipeline ou a interface, identificar base/versionName/versionCode, reproduzir o problema, delimitar escopo, preservar invariantes, executar testes e relatar evidências reais. Validação sintética não comprova qualidade no OPPO.
- **Este AGENTS.md da branch Codex prevalece sobre modelos e recomendações genéricas dos documentos**, inclusive as autorizações de commits, push e testes no aparelho concedidas acima. Não tocar na pasta do Claude, em sua branch ou no pacote Android `br.com.duvier.thenaturalcam`.
- Não inferir que a versão instalada no celular esteja sincronizada com o Git: antes de qualquer nova build, reconciliar versão, commit, pacote, assinatura e alterações locais. A informação de versão instalada fornecida pelo usuário prevalece sobre notas desatualizadas do repositório.
- Esta inclusão documental não modifica o aplicativo nem valida qualquer APK.
