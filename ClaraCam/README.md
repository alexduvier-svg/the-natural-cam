# The Natural Cam 1.0.0 (teste)

App de câmera Android próprio, feito do zero para **fotos com pouco ruído** no OPPO Reno16 Pro
(e testável no Find X8 Ultra e no Reno14). Pacote `br.com.duvier.thenaturalcam`; convive com o Natural Cam.

## Por que um app novo
Na análise do Natural Cam (APK 0.2.1 e repositório `natural-cam-android` 0.2.2):
- a 0.2.0 forçava redução de ruído e nitidez **desligadas** até em ISO 6400 — causa principal do ruído;
- a 0.2.1 voltou ao padrão da câmera; a 0.2.2 só mexe em zoom/lentes/latência;
- continua travando uma **lente física** (`setPhysicalCameraId`), que recebe menos processamento da OPPO;
- não há fusão de vários quadros nem modo Noite;
- o código está compactado em linhas de até 1.858 caracteres, difícil de manter.

## O que o The Natural Cam faz diferente
| Ponto | The Natural Cam |
|---|---|
| Resolução padrão | ~12 MP com pixels agrupados (menos ruído). 50/200 MP só se escolher, com aviso |
| Processamento da câmera | Redução de ruído, nitidez, aberração e pixel quente em **ALTA QUALIDADE** em toda foto |
| Lentes | Câmera **lógica**: o zoom troca de lente por dentro, mantendo o processamento do fabricante |
| Noite | Usa o modo Noite do sistema se o aparelho oferecer; senão, **fusão própria** de 4–8 quadros |
| Pro | ISO, obturador e exposição; aviso quando ISO ≥ 1600 |
| Ficha técnica | Cada foto gera `Documents/TheNaturalCam/<nome>_ficha.json` (ISO, exposição, RR observada, lente, quadros) |

Fotos em `DCIM/TheNaturalCam`. Botões de volume também disparam.

## Fusão Noite (`BurstFusion.java`)
Quadro de referência mais nítido → alinhamento em pirâmide e por blocos de 128 px →
mistura robusta por nível de brilho (sem fantasmas) → limpeza de ruído de cor guiada → nitidez só no brilho, acima do ruído.

Teste no PC (cena sintética, ruído σ=12, 6 quadros com tremor):
erro 12,0 → 5,9; ruído de cor 10,0 → 1,3; objeto em movimento sem fantasma.

## Compilar
```
gradlew assembleDebug testDebugUnitTest
```
APK: `app/build/outputs/apk/debug/app-debug.apk` (assinatura de depuração).

## Pendente
- Validação com fotos reais nos OPPO (cena noturna, comparar com a câmera original).
- Assinatura própria para versão final.
- Editor leve (v2).
