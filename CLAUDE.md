# CLAUDE.md — The Natural Cam

## O que é e para quem
App de câmera Android feito do zero (pasta `ClaraCam/`, pacote `br.com.duvier.thenaturalcam`).
Objetivo: fotos naturais, com pouco ruído, no OPPO Reno16 Pro, testáveis também no Find X8 Ultra
e no Reno14. Convive com o app irmão Natural Cam (repositório `natural-cam-android`).

Quem usa e julga: o Duvier, fotógrafo mobile premiado, que compara cada resultado com a câmera
original da OPPO e com o Find X8 Ultra, a 100 %, em fotos reais.

O que não pode falhar:
- Qualidade real da foto: sem ruído excessivo, sem dominante de cor, sem halo, sem pele de
  plástico, sem perda silenciosa de resolução. Uma foto pior que a da câmera original reprova
  a entrega, mesmo com o código perfeito.
- Na captura, a nitidez fica no nível do olho humano. Nitidez extra só no editor.
- O desfoque (Retrato, óptica e editor) não come bordas nem coisas finas. Na dúvida, mais nítido
  ou desfoque suave, sem corte duro.
- Originais, EXIF e ficha técnica (`Documents/TheNaturalCam/<nome>_ficha.json`) preservados.
- O editor responde durante o gesto; a exportação parte da resolução original.

Referência fotográfica: `ClaraCam/app/src/main/assets/knowledge/diretriz_fotografica_50_milhoes.md`.

## Onde está o estado do trabalho
`PENDENTE.md` é o diário do projeto: versão atual, como compilar e instalar, o que foi feito e a
lista "Falta". Leia o topo e a seção "Falta" antes de começar. Ao terminar, atualize o topo
(versão, data) e a lista "Falta".

## Compilar e testar
`cd ClaraCam && ./gradlew --no-daemon assembleDebug testDebugUnitTest`
APK de debug em `ClaraCam/app/build/outputs/apk/debug/app-debug.apk`.
Telas de teste só no debug: `TesteRecorteActivity` e `TesteRuidoActivity`.

## Este projeto é seu
Você é o dono e quem vai manter este código a longo prazo, e o desenho dele é decisão sua.
Se for preciso reorganizar o que existe para fazer bem, reorganize. Prefiro o código mais simples
no final, não remendo ao lado. Os comportamentos já aprovados pelo Duvier em foto real (listados
no `PENDENTE.md`) continuam como estão, salvo pedido dele.

## Aprovações
Peça meu OK antes de:
- comandar a tela do celular por adb (eu posso estar usando o aparelho naquele momento);
- desinstalar o app, limpar dados ou apagar fotos e arquivos do aparelho;
- mudar `applicationId`, assinatura ou baixar o `versionCode`;
- adicionar, trocar ou remover um modelo `.tflite` (pesa no APK e tem licença própria);
- fazer push no `main` ou qualquer push forçado.

Não precisam de OK: compilar, rodar testes, criar testes e cenas sintéticas no PC, refatorar,
subir o `versionCode`, atualizar o `PENDENTE.md`, instalar por cima com `pm install -r`
(mantém os dados) e ler `adb logcat -s TNC`.

Quando eu aprovar, vou ser específico ("sim, pode tocar na tela agora"). "Ok" solto não vale para
ação da lista acima.

## Como trabalhar
Não pare enquanto faltar alguma parte do que pedi. Se aparecer erro, descubra a causa e resolva.
Siga com tudo o que não depende de mim; pergunte só as decisões que de fato estão em aberto.
Obstáculos comuns (build falhando, teste quebrado, timeout do adb) não são motivo para parar:
diagnostique e contorne. Bloqueios deliberados (itens da lista de aprovação) são respeitados:
diga o que encontrou e procure outro caminho seguro.

Para recurso novo ou mudança grande no pipeline de imagem, antes de escrever código, me
entreviste, uma pergunta por vez, começando pelas que mudariam a forma de construir.

## Prova antes de "pronto"
Só diga que algo funciona se viu funcionando. Separe sempre três níveis de evidência:
1. teste no PC (cena sintética, teste unitário);
2. aparelho real, com o app rodando;
3. foto real comparada com a câmera original.
Qualidade fotográfica só é aprovada no nível 3. O que não deu para conferir, escreva
"NÃO TESTADO no aparelho" e diga por quê.

Ao fim de cada etapa grande, chame um subagente com contexto novo para revisar o diff contra os
critérios acima (ruído, cor, bordas, resolução, nitidez da captura, fluidez do editor).

## Relatório final
Comece pelo resultado, em frases curtas: o que ficou pronto, o que foi conferido e em qual dos
três níveis, o que ficou de fora e por quê. Escreva para quem não viu os comandos nem os arquivos.
Sem siglas inventadas durante o trabalho.

## Esforço
Ajuste pequeno de interface ou parâmetro: `/effort medium`. Recurso novo no pipeline de imagem
(fusão, bokeh, ruído, ISP RAW): planejar em medium e construir em `/effort high`.

## Manutenção destas regras
Quando uma regra daqui perder o motivo, apague. Quando uma versão fechar, as seções "Feito na X"
antigas do `PENDENTE.md` podem ir para um arquivo de histórico, deixando o diário curto.
