# O que a comunidade diz: GCam/AGC/LMC em OPPO, HP5, JN5 e GC50F6

Nenhum APK nem config (.xml/.agc) foi baixado. O que segue resume páginas públicas.

**Limitação da pesquisa:** o Reddit (r/GooglePixel, r/Android, r/oppo, r/GCamPort) **bloqueia o acesso automatizado** desta ferramenta: a busca no domínio foi recusada e a API JSON devolveu a página de bloqueio. Por isso, as opiniões abaixo vêm de **espelhos e resumos públicos de threads do r/GCamPort**, de comentários do GSMArena, de XDA e de guias dos desenvolvedores de ports (celsoazevedo.com). Vale abrir manualmente no navegador as buscas sugeridas no fim.

---

## 1. Os sensores citados

| Código | Sensor | Onde aparece nos aparelhos do projeto |
|---|---|---|
| **HP5** | Samsung ISOCELL HP5, 200 MP, 1/1,56", 0,5 µm (2,0 µm binado 4×4) | **Reno16 Pro**, principal (ID 2). O Android expõe 4096×3072 com pixel binado de 2,016 µm e vendor tags de 8192×6144. |
| **JN5** | Samsung ISOCELL JN5, 50 MP, 1/2,76", 0,64 µm | **Find X8 Ultra**, ultra-angular (ID 3); **Reno16 Pro**, tele 3,5× e frontal; provavelmente o tele e a frontal do **Reno14** (mesma óptica de 12,19 mm). |
| **GC50F6** | GalaxyCore, 50 MP, ultra-angular | **Reno15 Pro**, segundo a Gizmochina. O Reno16 Pro herda o conjunto; a UW (ID 3) tem física compatível. Não confirmado pelo fabricante. |

## 2. O que dizem sobre GCam/AGC/LMC em OPPO

1. **Os ports funcionam via Camera2, mas exigem config.** Guias de Reno15 e Reno16 (YTECHB) dizem que o Camera2 vem liberado de fábrica e que é preciso “configurar direito” para a GCam funcionar. Para o Find X8 Ultra, um guia sugere o LMC 8.4 quando o visor não abre e cita um config “OPPO_X8U_BSG_v3.xml”, que **não foi verificado**.
2. **Configs são por port e por versão.** O BigKaka usa `.agc` numa pasta de configs (AGC 8.4/9.x). O Hasli (LMC 8.4) usa `.xml` noutra pasta. Configs não se transferem entre ports; quem usa repete que “as opções da GCam não são documentadas e mudam entre versões”.
3. **MediaTek pede RAW16 em vez de RAW10** em vários relatos, por exemplo numa resposta sobre AGC 9.4.24. Relatos genéricos dizem ainda que a GCam “raramente rende bem de cara” em MediaTek. Para o Reno16 Pro e o Reno14 (MT6899/MT6897), isso combina com o que o adb mostrou: só RAW_SENSOR, sem RAW10.
4. **Modelo de ruído (Noise Model) é o ajuste mais citado** para HP5/JN5. Usuários dizem que um modelo correto “ajuda o denoise a separar ruído de sinal”. Não há modelo publicado para HP5 nem JN5; os configs são feitos de forma artesanal por quem mantém cada port.
5. **Ajustes que mexem em nitidez e ruído**, segundo os guias comunitários (os nomes variam por port):
   - *Merge method*: **Sabre** é descrito como suave, com pouca nitidez; **Spatial RGB**, como mais nítido e com bom controle de ruído, mas aparência “processada” e mais lenta.
   - *Spatial denoise*: valor menor deixa a imagem mais macia e perde estrutura. *Chroma denoise* alto borra e confunde linhas de profundidade com sombra. *Luma denoise* alto escurece junto com o mapa de exposição.
   - *Sabre sharp*: mais contraste de borda. Em excesso, contorno; em falta, foto borrada.
   - Número de quadros fundidos: configurado junto com o método de fusão.
   - Há um tópico no r/GCamPort pedindo para “desligar toda suavização e redução de ruído”: a demanda por textura natural é recorrente.

## 3. O que dizem da câmera nativa OPPO

- **Nitidez e NR fortes demais** são a queixa mais comum nos comentários do GSMArena sobre a série Find X8. Frases recorrentes: “excessivamente afiado”, “a OPPO precisa diminuir nitidez e redução de ruído”, “o tele 3× pesa na mão na NR em retratos” e “a UW do X8 Ultra é mais afiada que a do X7 Ultra”.
- A comparação X8 Ultra × X7 Ultra da GSMArena diz que o **JN5 menor (UW) sofre em luz interna** e força mais NR. Isso bate com a ultra-angular do X8U no adb: ISO até 16000, com o analógico igual.
- **Contraponto:** a DXOMARK e reviews (SlashGear, GSMArena) elogiam a retenção de detalhe e o controle de ruído da principal do X8 Ultra. Isso confirma a referência do usuário: **o principal LYT-900 é o ponto alto**.
- **Contorno relatado:** fotografar em DNG e converter sem ajustes dá resultado “bem melhor”. É indício de que o problema está no processamento, não no sensor. Isso reforça a fusão em RAW proposta em `pipeline_computacional.md`.
- Comentários dizem que celulares de 2 a 4 anos “com GCam e um bom config” fotografam melhor que topos de linha com o app de fábrica. É opinião, mas mostra o valor percebido de uma fusão natural.

## 4. O que isso significa para o The Natural Cam

| Lição da comunidade | Ação no app |
|---|---|
| O ganho real vem de **fusão com modelo de ruído correto** | Usar `SENSOR_NOISE_PROFILE` por câmera como prior e guardar no banco local o σ medido por ISO e câmera (`agentes_e_banco.md`). |
| “Sabre suave × Spatial nítido”: o usuário quer escolher o nível de processamento | Expor um controle “Natural ↔ Definido” que mova juntos `sharpen`, `texture` e `nrBias` (como o Quick Sharp da Nikon). |
| MediaTek: RAW16, sem RAW10 | No Reno16 Pro e no Reno14, burst RAW só em RAW_SENSOR (16 bits por amostra). Considerar a memória: 4096×3072×2 B ≈ 24 MB por quadro. |
| A UW com JN5 sofre em luz baixa | Na UW, preferir mais quadros e limitar o ISO ao analógico. No Reno14, a UW de 8 MP tem analógico máximo de 1550. |
| Configs não se transferem entre aparelhos | Perfis de calibração **por aparelho e por ID de câmera** no banco local, não constantes no código. |
| Ninguém publica configs “oficiais” | O app não depende de configs de terceiros e não copia valores de configs GCam: a calibração é própria. |

## Buscas sugeridas para conferir à mão (o Reddit não é acessível por automação)

- r/GCamPort: `Find X8 Ultra config`, `Reno 14 LMC`, `Reno 16 Pro AGC`, `HP5 noise model`
- r/oppo: `oversharpening`, `third party camera 50MP`, `Camera2 RAW`
- r/Android / r/GooglePixel: `HDR+ vs Hasselblad`, `natural processing`
- XDA: fórum do Find X8 Ultra (seção de câmera)

## Fontes

- Configs do BigKaka (AGC 8.4): https://www.celsoazevedo.com/files/android/google-camera/f/configs-bigkaka-01/
- Configs do Hasli (LMC 8.4): https://www.celsoazevedo.com/files/android/google-camera/f/configs-hasli-02/ ; build LMC8.4_R18: https://www.celsoazevedo.com/files/android/google-camera/dev-hasli/f/dl14/
- Ajuda do LibPatcher (BSG): https://www.celsoazevedo.com/files/android/google-camera/dev-bsg/help/2 ; https://www.celsoazevedo.com/files/android/google-camera/dev-bsg/help/1
- Guia de criação de XML (jajo): https://www.celsoazevedo.com/files/random/2023/jajo_guide_to_make_xml.pdf
- FAQ de ports GCam: https://www.celsoazevedo.com/files/android/google-camera/troubleshooting/
- r/GCamPort, “desligar toda suavização” (espelho): https://axebps-redlib.hf.space/r/GCamPort/comments/1k5z4z3/looking_to_turn_off_all_smoothing_noise_reduction
- r/GCamPort, pedido de manual de opções (espelho): https://lr.us.psf.lt/r/GCamPort/comments/1evgj7w/i_need_a_manualebookmaterial_that_explains_the
- r/AndroidQuestions, RAW16 em MediaTek (espelho): https://pol1.lr.ggtyler.dev/r/AndroidQuestions/comments/1k2575n/i_have_a_question_about_gcam
- GCam para o Find X8 Ultra (guia de terceiros, não verificado): https://droidfeats.com/oppo-find-x8-ultra-gcam-port/
- GCam para o Reno16 (YTECHB): https://www.ytechb.com/download-google-camera-for-oppo-reno-16-series/ ; Reno15: https://www.ytechb.com/download-google-camera-for-oppo-reno-15-pro-max/
- XDA, GCam no Reno 8T: https://xdaforums.com/t/gcam-for-oppo-reno-8t.4642816/
- Sensores do Reno15 Pro (HP5/JN5/GC50F6): https://www.gizmochina.com/2025/08/02/oppo-reno-15-pro-camera-specs-design-leak/ ; GSMArena: https://fo.gsmarena.com/oppo_reno15_pro_mini-review-2920p5.php
- Comentários sobre a série Find X8 (GSMArena): https://www.gsmarena.com/newscomm-65241p2.php ; https://m.gsmarena.com/newscomm-65239p2.php ; galeria de Xi'an: https://m.gsmarena.com/newscomm-67358p2.php
- GSMArena, X8 Ultra × X7 Ultra: https://m.gsmarena.com/newscomm-67356p2.php
- DXOMARK, Find X8 Ultra: https://www.dxomark.com/oppo-find-x8-ultra-camera-test/
- SlashGear, review do Find X8 Ultra: https://www.slashgear.com/1883093/oppo-find-x8-ultra-review-features-camera-photography-specs
- GSMArena, comentários do review do Find X9 Pro (GCam × app nativo): https://m.gsmarena.com/reviewcomm-2894p8.php
