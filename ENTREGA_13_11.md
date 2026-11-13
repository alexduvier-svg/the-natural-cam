# Entrega do The Natural Cam — 13/11/2026, até 12h00

Este arquivo é o roteiro da manhã. Vale junto com o CLAUDE.md. Em conflito, o prazo e a regra
"nenhuma foto pior que a versão de partida" vencem qualquer melhoria.

## Para que serve, quem usa e o que não pode falhar
O app vai ser entregue e usado no dia a dia pelo Duvier, fotógrafo mobile, sem nenhum agente
trabalhando junto e com o celular desligado do PC. O que não pode falhar: **o app se comportar
igual com e sem agente**. Hoje as fotos ficam boas enquanto um agente trabalha e saem com ruído e
foco errado quando ele para. Uma versão que só funciona no teste de bancada reprova a entrega.

## 08h00–08h20 — Prova da base (o Duvier faz; nenhum agente mexe no celular)
Mesmas 5 fotos nos dois apps instalados, "The Natural Cam" (main, 1.30.3) e "The Natural Cam
Codex" (1.27.17-codex), com o celular no suporte, abrindo o app do zero e tocando para focar com
o dedo:
1. frasco a 30 cm, 1×;
2. frasco a 30 cm, 3,5×;
3. boné a ~2 m, 3,5×;
4. boné a ~2 m, 6×;
5. cena com sombra (armário/mesa), 1×.
O app que vencer em 4 das 5 vira a **base da entrega**. Só um agente trabalha nela (o dono
daquele ramo). O outro agente só revisa, sem editar e sem instalar.

## Regras da manhã
- **Partida congelada:** antes de qualquer mudança, copie o APK atual da base para
  `entrega/APK_PARTIDA.apk` e anote versão, versionCode e SHA-256. Se algo der errado, ele volta
  para o celular.
- **Instalação:** só instale depois de compilar, passar nos testes e terminar a etapa. A cada
  etapa aprovada, copie para `entrega/APK_APROVADO_<versão>.apk`. Se a sessão parar no meio, o
  celular fica com o último APK aprovado, nunca com um experimento.
- **Teste igual ao uso real:** a validação não usa adb para focar, tocar ou disparar. O agente
  pede ao Duvier a foto, e o Duvier toca com o dedo, com o app aberto do zero (sem estado
  anterior). Foco comandado pelo PC não conta como prova.
- **Uma mudança por vez**, com a ficha (`_ficha.json`) antes e depois na mesma cena.
- **Esforço:** `/effort high` para foco e ruído; `medium` para o resto.
- **10h30:** congelamento de código. Daí em diante, só corrigir regressão.

## Itens, na ordem de prioridade
Status no relatório: VALIDADO (visto em foto real, sem PC), PARCIAL (testes do PC passaram,
falta foto real) ou BLOQUEADO (limite do aparelho ou falta de tempo).

**P1 — Item 9: consistência sem agente.** Procurar estado que muda o comportamento entre fotos
e entre sessões: `nearMode`, `teleFailUsed`, trava de AWB/AE, lente escolhida por falha anterior,
ajustes de laboratório, caminhos que só existem no build de debug (`isDebuggable`) e
configurações que um teste por adb deixou ligadas. Toda abertura do app começa do mesmo estado
limpo. Critério: 3 fotos seguidas da mesma cena, com o app fechado e aberto entre elas, saem com a
mesma lente, a mesma exposição e o mesmo foco.

**P1 — Itens 1 e 2: foco em qualquer lente, de 20 cm ao infinito.**
- Ler e registrar na ficha a distância mínima de foco de cada lente física
  (`LENS_INFO_MINIMUM_FOCUS_DISTANCE`, `CameraCaps.minFocusCm`). A tele do Reno16 Pro declara
  foco não calibrado e não foca perto (~40 cm). **De 20 a 40 cm, a foto sai da principal**, com
  recorte se houver zoom. Isso é física, não erro. Avisar "aproxime menos" só abaixo do mínimo da
  principal.
- Foco à distância como no ensaio do boné: estudar no ramo `codex/natural-cam-memory` os commits
  `ea10827` (zoom de foco mantido na câmera lógica), `4aabb86` (alvo mantido na troca de lente),
  `e8681c7` (seleção de lente estável) e `FocusScanGuard` (descarta foco atrasado e foco falso).
  **Portar só a lógica de foco**, sem trazer o resto do ramo.
- Critério: frasco a 20, 30 e 40 cm e boné a 2 m, em 1×, 3,5× e 6×, todos com o rótulo ou o
  bordado nítido, focados com o dedo e sem PC.

**P2 — Item 4: ruído nas sombras.** Medido nas fotos enviadas: sombras com ruído de brilho ~3×
maior que as áreas claras, e ruído de cor até 10× maior que na câmera da OPPO. Ver na ficha se a
fusão foi reprovada (`verificador_qualidade.decisao = quadro_unico_limpo`). Corrigir com
limpeza de cor mais forte nas sombras e sem clarear o ruído junto com a imagem (`ToneMatch`
clareia até +1 EV). Proteger pele e textura. Critério: o ruído de cor nas sombras cai pela metade,
sem perder a trama do tecido no boné.

**P2 — Item 5: troca de lente suave.** Sem tela preta e sem travar na troca 1× → 3,5× → 6×. O
alvo de foco e a exposição passam para a nova lente (ver `4aabb86`). Critério: 10 trocas
seguidas sem engasgo visível, gravadas pelo Duvier.

**P3 — Item 3: nitidez, textura, cor e WB naturais até 10×.** O limite precisa estar claro. O
Android só libera 12,6 MP para o app, sem os 50/200 MP do sensor. Acima de 3,5× é recorte: cerca
de 6 MP em 5×, 3 MP em 7× e 1,5 MP em 10×. Super-resolução não ganha detalhe com o celular no
suporte. O que fazer: menos nitidez artificial no recorte ampliado (tirar o efeito "aquarela"),
WB automático sem correções extras e legibilidade medida no rótulo. Não prometer o detalhe da
câmera da OPPO em 6–10×. No app, mostrar "zoom digital" acima de 3,5×.

**P3 — Item 6: render mais rápido, com e sem Máxima.** O ensaio do boné levou 12–18 s até o
JPEG. Ganhos baratos: não tentar super-resolução quando não há deslocamento entre quadros
(celular no suporte), o que economiza ~6 s; reaproveitar a medição da prévia. Critério: tempo
até o JPEG registrado na ficha, menor que na partida e com qualidade igual na foto real.

**P4 — Itens 7 e 8: desfoque suave e botão de profundidade de campo.** Usar o que já existe
(dial de desfoque, diretriz 1.30.2: não come bordas, na dúvida nítido ou suave). Prévia de
desfoque em tempo real só se sobrar tempo depois das 10h, senão BLOQUEADO para depois da
entrega.

## 10h30–11h15 — Teste final (o Duvier, sem PC)
As mesmas 5 fotos da prova da base, mais o boné a 6× e o frasco a 20 cm. Compara com
`APK_PARTIDA`. Qualquer foto pior que a partida volta essa mudança.

## 11h15–11h45 — Fechamento
APK final em `entrega/`, com SHA-256. Relatório começando pelo resultado: cada item como
VALIDADO, PARCIAL ou BLOQUEADO, com a foto ou a ficha que prova. O que ficou para depois da
entrega vai para o `PENDENTE.md`.
