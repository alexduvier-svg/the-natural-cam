# Câmera X — prioridades de desenvolvimento

Estas diretrizes valem para o desenvolvimento do Câmera X e têm prioridade sobre as notas históricas do The Natural Cam. O app Android fica em `ClaraCam/`, com identificador `br.com.duvier.camerax`. Preserve o identificador, a assinatura e a possibilidade de instalar novas versões por cima.

## Referência fotográfica aprovada

A versão **0.1.2-teste.1** é a referência aprovada em fotos do OPPO Reno16 Pro em 14/11/2026. O usuário aprovou o **1× inclusive ampliado**, o **3× com a legibilidade melhorada** e a qualidade do **3,5×**. Preserve o APK de referência. Use os JPEGs originais e suas fichas quando estiverem disponíveis. Mantenha fotos pessoais, fichas de captura e credenciais fora do Git. A troca de cena com foco preso foi reportada nessa mesma rodada e deve ser testada em versões seguintes.

## Critérios de aceitação de cada atualização

- **Qualidade da imagem.** Preserve cores reais, balanço de branco natural, contraste e realces, nitidez e legibilidade de letras pequenas. Preserve fibras, cabelo e pele. Reprove halos, textura inventada, aparência plástica, granulação realçada, fantasmas e redução silenciosa de resolução.
- **Sombras.** Controle o ruído cromático e o grão nas áreas lisas. Preserve a textura nas zonas escuras e os detalhes em pele, cabelo, tecido e rótulos. Um resultado mais liso só conta como melhora se conservar esses detalhes.
- **Fusão e desempenho.** Use a política de quadros da 0.1.2 como referência, incluindo Máxima e Noite. Confirme quadros pedidos, recebidos e usados por lente e modo. Mudanças de quantidade, exposição, alinhamento ou limpeza precisam demonstrar ganho sem aumentar o tempo de captura ou pós-processamento. Compare a mediana e as capturas mais lentas em condições equivalentes. Meça do disparo ao último quadro e até o JPEG disponível, usando a ficha e os registros da câmera ou instrumentação no OPPO.
- **Foco.** O toque se aplica ao assunto escolhido. Com o cadeado aberto, o autofoco volta a acompanhar a cena após reenquadramento. A trava explícita e o foco manual continuam disponíveis. Ao trocar do rótulo para os pés e voltar, confira o foco no visor e no JPEG, com e sem Máxima.
- **Teste de regressão no aparelho.** Repetir a mesma cena e enquadramento em 1×, 3× e 3,5× e conferir também 5×–8× quando a área afetar o zoom. Incluir um rótulo, tecido/pele e sombras em pouca luz. Leia o rótulo em três fotos consecutivas, compare recortes de igual escala, lente física e modo efetivos e registre os tempos. Preserve o layout aprovado.
- **Entrega.** Testes automatizados e compilação autorizam identificar um APK como versão de teste. A aprovação fotográfica e de tempo depende das fotos e medições reais. Se faltar acesso ao aparelho, indique o que ainda precisa ser conferido. Se houver regressão, ajuste ou reverta a mudança responsável antes de promover a versão.

## Verificação local

Em `ClaraCam/`, rode `./gradlew --no-daemon assembleRelease testDebugUnitTest`. Verifique a assinatura do APK, o pacote e o código da versão. Use a branch do Câmera X para suas alterações e mantenha a base versionada acessível para comparações.
