> **Referência complementar:** este modelo não substitui o `AGENTS.md` vigente na raiz. Em conflitos, preservar as regras e decisões atuais do projeto, levantando divergências antes de mudar o aplicativo.

# AGENTS.md — MODELO PROPOSTO: THE NATURAL CAM

> **Importante:** conteúdo para MESCLAR ao `AGENTS.md` que já existir no repositório. Não substituir automaticamente o original, nem alterar o app sem auditoria da branch ativa. Este documento é diretriz, não laudo de testes executados.

## Missão do projeto

Aplicativo Android de fotografia computacional **The Natural Cam**, com prioridade para fotografia natural, retenção de textura real, nitidez limpa, cor fiel e desempenho do clique ao arquivo final, especialmente em retratos e zoom de 3x a 8x. O aparelho de referência é o **OPPO Reno16 Pro**; o funcionamento real das lentes/sensores/APIs deve ser descoberto e documentado com evidência, não presumido.

## Procedimento de alteração

1. Ler somente arquivos pertinentes e inspecionar `git status`, branch, commit base e histórico recente da área afetada. Respeitar instruções existentes.
2. Para correções fotográficas, **identificar o estágio**: preview -> AE/AF/AWB -> captura e metadados -> seleção de lente/crop -> RAW/YUV/JPEG -> alinhamento/fusão -> redução de ruído -> demosaicing/coloração -> sharpening -> tonemapping -> exportação. Confirmar pipeline real no código antes de propor alteração.
3. Produzir baseline reproduzível por cenário. Medir e mostrar artefatos, comportamento de ISO, sombras, retrato, baixa luz, movimento, 3x–8x. Se a função exigir aparelho físico e este não estiver disponível, registrar teste como pendente, nunca como realizado.
4. Corrigir causa raiz com patch pequeno. Manter separadas as estratégias de foto diurna, retrato e modo noturno. Proteger detalhes faciais, cabelo, pele, tecidos e bordas; evitar pele plastificada, halos e nitidez falsa.
5. Revisar seleção real de câmera física/lógica, focal length, crop, EIS/OIS quando expostos, suporte RAW, stream combinations, níveis de hardware Camera2, metadados e limites vendor/HAL. Não assumir disponibilidade da teleobjetiva ou acesso a binning, sensor de cor/WB e algoritmos privados somente porque constam na ficha comercial.
6. Preservar resposta rápida do obturador, ajuste de exposição ao vivo e transição suave do zoom. Medir latência e estabilidade com ferramenta apropriada; não inventar milissegundos.
7. Executar testes locais e no dispositivo quando disponível, com comparação visual mascarada de baseline e produto final. Considerar ruído cromático e de luminância, detalhe fino, serrilhado, ring/oversharpening, smear, ghosting, cor de pele e fidelidade de WB.
8. Revisão independente focada em corridas/concurrency, buffers, memória, estados da câmera, travamentos, rollback, mudança de lente, bateria e correção das imagens. Diferenciar inspeção estática, emulador e prova no OPPO físico.

## Invariantes

**Imagem:** preservar textura natural; não suavizar a face em retratos; não compensar ruído criando detalhe artificial; não adicionar saturação sem necessidade; manter aparência natural. Não prometer "nível Hasselblad" como verificação técnica sem métricas e julgamento fotográfico.

**Zoom:** 3x–8x é prioridade do usuário, mas a distância focal óptica real e a disponibilidade via API devem ser verificadas; não confundir crop digital com lente dedicada.

**Desempenho:** preview e controles fluidos; alteração não deve ampliar atraso de captura ou introduzir engasgos. Benchmark no hardware de destino quando possível.

**Arquitetura:** manter assinatura, identificador do app, compatibilidade e interface atual. Qualquer troca de pipeline, bibliotecas, dependências ou formato de persistência precisa de análise de impacto.

**Conformidade:** não atribuir a fabricantes características não verificadas; manter registro de fontes e comprovação no aparelho. Nunca fabricar fotos "antes/depois" como evidência.

## Critérios mínimos de aceitação por correção de nitidez/ruído

- Cenários de comparação: retrato diurno em sombra, retrato em luz uniforme, paisagem texturizada, ambiente interno, pouca luz, objeto em movimento; 3x, 5x e 8x, conforme realmente disponível no aparelho.
- Mesmo enquadramento e exposição comparável (quando possível); registrar lente/crop, resolução, ISO, duração da exposição, versão/build e condições.
- Demonstrar redução de ruído sem perda significativa de textura real e sem halos, aquarela ou suavização facial. Não afirmar sucesso por um único frame.
- Preview, foco, gesto de zoom, controle de exposição, clique e gravação de JPEG/RAW continuam operantes quando suportados.
- Exibir resultados lado a lado e registrar a avaliação humana e os números de testes, se efetivamente medidos.
- Build instalável não significa câmera validada. Marcar `PARCIAL` até testes reais do sensor e pipeline.

## Prompt prático — ruído e nitidez em zoom

```text
Investigue a perda de nitidez e o excesso de ruído do The Natural Cam,
priorizando 3x–8x no OPPO Reno16 Pro. O resultado deve preservar a textura
natural da pele e a cor mais fiel já obtida pelo app.

Primeiro identifique a implementação real do pipeline e a seleção física/lógica
da câmera no repositório. Não presuma especificações do sensor, lentes,
RAW, binning, OIS, WB ou acesso ao HAL. Compare com os resultados de teste
disponíveis e documente a evidência. Determine a causa raiz antes de editar.

Implemente a menor mudança sustentável para reduzir o ruído nas sombras
sem plastificar retratos nem perder detalhe fino. Não redesenhe a interface.
Preserve a velocidade do clique, o zoom e a exposição ao vivo.

Crie/atualize testes pertinentes, rode build e análises disponíveis. Em tarefas
relevantes, faça revisão independente do diff e dos casos de borda. Teste no
OPPO real somente se houver acesso; caso contrário declare PARCIAL.

No final apresente comparações, comandos executados, arquivos alterados,
resultados observados, riscos e o que ainda requer teste em aparelho.
Não faça commit, push ou publicação do APK sem autorização específica.
```