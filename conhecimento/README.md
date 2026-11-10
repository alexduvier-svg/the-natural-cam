# Base de conhecimento fotográfica: The Natural Cam

Montada em 09/11/2026, sem alterar o código do app (`ClaraCam/`).

| Arquivo | Conteúdo |
|---|---|
| [capacidades_aparelhos.md](capacidades_aparelhos.md) | Capacidades reais (adb, sem root) do Find X8 Ultra, do Reno16 Pro e do Reno14 |
| [estilos_marcas.md](estilos_marcas.md) | Hasselblad, Leica, Fuji, Kodak, Nikon e Adobe → parâmetros novos para os 40 estilos |
| [pipeline_computacional.md](pipeline_computacional.md) | HDR+, Night Sight, super-resolução e projetos abertos (com licenças) → `BurstFusion` |
| [discussoes_comunidade.md](discussoes_comunidade.md) | GCam/AGC/LMC em OPPO, HP5/JN5/GC50F6 e queixas de nitidez e NR |
| [agentes_e_banco.md](agentes_e_banco.md) | Agentes locais (modelo do Agro Gestão), esquema SQLite e plano em etapas |
| [dados_adb/](dados_adb/) | Resumo das características e nomes de vendor tags por aparelho |

## As 5 melhorias mais promissoras para nitidez e ruído

1. **Acabar com a nitidez dupla.** Hoje o burst pede `EDGE_MODE_FAST` por quadro e o `BurstFusion` afia de novo. O certo é pedir `EDGE_MODE_OFF` mais `NR_MINIMAL` nos quadros (suportados nos 3 aparelhos) e afiar **uma vez**, no fim, com máscara de borda e limiar de 2–3σ residual. É o menor esforço e remove halos e ruído realçado.
2. **Alinhamento subpixel com blocos sobrepostos.** Trocar os blocos de 128 px e a busca inteira por blocos de 32–64 px com 50% de sobreposição, janela cosseno e subpixel por ajuste parabólico do SAD. O erro de meio pixel é o que mais apaga a textura fina na fusão atual.
3. **Modelo de ruído do sensor mais calibração por aparelho.** Usar `SENSOR_NOISE_PROFILE` como prior junto com o σ medido entre quadros, e guardar o modelo por câmera, ISO e firmware no banco local. Isso dá mistura e denoise que separam ruído de textura com mais segurança (lição do HDR+ e da comunidade GCam).
4. **Mistura em duas escalas.** Misturar a baixa frequência com limiar largo (ganho de SNR sem risco) e a alta com o limiar atual. Dá o efeito do Wiener do HDR+ sem FFT e sem a abordagem patenteada.
5. **Fusão em RAW, ou o atalho YUV_REPROCESSING.** Os 3 aparelhos têm RAW_SENSOR 4096×3072 de 10 bits e YUV_REPROCESSING na traseira. Primeiro passo barato: devolver o YUV fundido ao ISP com `REPROCESS_EFFECTIVE_EXPOSURE_FACTOR`. O ganho máximo vem do burst RAW subexposto (−0,7 a −1 EV) fundido no Bayer, que é o caminho do HDR+ e o que a comunidade relata ao preferir o DNG ao JPEG da OPPO.

Fatos de contexto confirmados no adb: o **teto padrão é 12,6 MP** nos 3 aparelhos (sem mapa MAXIMUM_RESOLUTION; o 50/200 MP fica só em vendor tags) e as **extensões Noite/HDR são “fake/dummy”**, de modo que a fusão própria é o único caminho de várias exposições. Falta confirmar com um log real do app.
- [referencias_autores.md](referencias_autores.md) — Herzog, Salgado, Hasselblad/X2D: o que cada um ensina e as receitas no editor
