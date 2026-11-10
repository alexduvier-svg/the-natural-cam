# Agentes de fotografia e banco local de comportamento de câmera

Proposta inspirada no **AGRO Gestão** (`C:\Users\alexd\PROJETOS\AGRO-Gestao-Claude`). O projeto foi lido sem alteração.

## 1. Como os agentes foram feitos no AGRO Gestão

| Elemento | Onde | O que ensina |
|---|---|---|
| **Agente local determinístico**, sem LLM, API paga ou internet | `app/src/features/advisor/agent.js` | Classifica a intenção por regex (`INTENTS`) e responde com seções curtas: fato, número e “porquê”, só com dados reais do app. |
| **Diretrizes versionadas e congeladas** | `app/src/data/agent-guidelines.js` | Objeto `Object.freeze` com `version`, `source`, `authority: 'reference_only'`, `parameters` (limiares numéricos) e `entries` com `id`, `applicability`, `priority` e `rule`. A regra é **referência, nunca autorização executável**. |
| **Conhecimento em Markdown dentro do app** | `public/knowledge/*.md` (empacotado em `assets/`) | `agente-operacional.md`, `hierarquia-inteligencia.md` etc. são documentação legível que o agente cita. |
| **Hierarquia Connect → Motor → Action → Audit** | `knowledge/hierarquia-inteligencia.md` | Os papéis são do software, não chatbots. **Connect** monta o contexto mínimo local; o **Motor** aplica as diretrizes; **Action** só *prepara propostas*, que o usuário confirma; **Audit** é uma barreira obrigatória antes e depois, que Action não desliga. |
| **Revisão periódica no Android** | `AgroAgentReviewWorker.java` (WorkManager) e `AgroAgentSchedulerPlugin.java` | Trabalho diário aproximado que só **sinaliza** revisão. Nada roda continuamente. |
| **QA do agente** | `qa/agent016-*.cjs/json`, `tests/agent.test.mjs` | Benchmarks e testes de regressão das respostas. |
| Princípios | `agente-operacional.md` | Fato, hipótese e teste separados. No máximo três prioridades, com evidência, período e amostra. Feedback **não autoajusta** limites críticos. |

## 2. Agentes de fotografia para o The Natural Cam

Mesma filosofia: **regras locais, determinísticas e versionadas**, que propõem e nunca impõem. Todos rodam no aparelho, em Java, e leem e escrevem no banco local.

| Agente | Papel (equivalente no Agro) | Entrada | Saída |
|---|---|---|---|
| **Perfil** (Connect) | Monta o contexto da captura | `CameraCharacteristics`, modelo, ID da câmera, ISO, exposição, lux estimado, giroscópio, movimento medido na prévia | `ContextoCaptura` (DTO local) |
| **Exposição** (Motor) | *Motion metering* e plano do burst | ContextoCaptura e histórico de ruído por ISO | Plano: N quadros × tempo × ISO, EV de proteção das altas luzes, RAW ou YUV |
| **Ruído** (Motor) | Modelo de ruído por aparelho, câmera e ISO | σ medido pelo `BurstFusion` (`noiseBefore`/`After`), `SENSOR_NOISE_PROFILE` | Prior de σ por faixa de brilho, para a próxima fusão |
| **Estilo** (Motor) | Ajusta os parâmetros do estilo à cena | Estilo escolhido, ruído residual e tipo de cena (pele, céu, textura) | Parâmetros efetivos (`sharpen`, `texture` e `nrBias` limitados pelo ruído; veja `estilos_marcas.md`) |
| **Qualidade** (Audit) | Barreira depois da fusão | Resultado da fusão e métricas | Aprova ou recomenda refazer. Detecta fantasma (peso médio baixo), halo, desfoque (nitidez abaixo da referência) e recorte de altas luzes. **Nunca apaga foto**; só marca e sugere. |
| **Comparador** (Audit/QA) | Compara com imagens de referência | Fotos de referência do usuário (por exemplo, câmera nativa do X8U × The Natural Cam, cena do copo) | Diferenças de ruído, textura e nitidez por região, salvas no banco |
| **Revisor** (Worker) | Revisão periódica (WorkManager, só com o aparelho carregando) | Banco local | Recalcula os modelos de ruído por ISO, invalida calibrações antigas depois de atualização de firmware (`ro.build.display.id` mudou) e sinaliza “recalibrar câmera X” |

**Regras permanentes** (no formato de `agent-guidelines.js`):

```java
// PhotoGuidelines.java: referência versionada, nunca autorização executável
static final int VERSION = 1;
// id, aplicabilidade, prioridade, regra
{"origem", "todas", 100, "Toda sugestão cita aparelho, câmera, firmware e amostra usados; sem amostra suficiente, dizer que não há dado."}
{"original", "todas", 100, "O arquivo original (DNG/JPEG) nunca é sobrescrito; estilo e reprocesso geram cópias ou receitas não destrutivas."}
{"ruido", "fusao", 90, "Nitidez e textura do estilo são limitadas pelo ruído residual; nunca afiar abaixo de 2σ."}
{"iso", "exposicao", 90, "Não passar do ISO analógico máximo se for possível compensar com quadros ou tempo."}
{"calibracao", "ruido", 80, "Modelo de ruído só substitui o anterior com ≥ 5 capturas em ≥ 3 ISOs diferentes do mesmo firmware."}
{"evidencia", "qualidade", 90, "No máximo três avisos por foto, cada um com a métrica e o limiar que o disparou."}
{"autotune", "todas", 100, "Feedback do usuário ajusta só preferências de estilo; não mexe em limites de segurança (fantasma, recorte, ISO)."}
{"privacidade", "todas", 100, "Nada sai do aparelho; imagens de referência ficam na pasta do app e podem ser apagadas pelo usuário."}
```

## 3. Banco de dados local (Room/SQLite)

```sql
-- Aparelho e firmware: a calibração vale por firmware
CREATE TABLE device (
  id INTEGER PRIMARY KEY,
  model TEXT NOT NULL,              -- PKJ110, CPH2863, CPH2737
  soc TEXT, build_id TEXT NOT NULL, -- ro.build.display.id
  sdk INTEGER, first_seen INTEGER, UNIQUE(model, build_id)
);

-- Uma linha por ID de câmera (lógica ou física), com o que o adb mostrou
CREATE TABLE camera (
  id INTEGER PRIMARY KEY,
  device_id INTEGER REFERENCES device(id),
  camera_id TEXT NOT NULL, physical_of TEXT,     -- '3' físico da lógica '0'
  facing TEXT, focal_mm REAL, equiv_mm REAL, aperture REAL,
  sensor_w_mm REAL, sensor_h_mm REAL, cfa TEXT, white_level INTEGER, black_level INTEGER,
  iso_min INTEGER, iso_max INTEGER, iso_analog_max INTEGER,
  exp_min_ns INTEGER, exp_max_ns INTEGER,
  jpeg_max TEXT, yuv_max TEXT, raw_max TEXT,
  nr_modes TEXT, edge_modes TEXT, caps TEXT,      -- listas CSV
  has_yuv_reprocess INTEGER, has_ext_night INTEGER, has_ext_hdr INTEGER,
  characteristics_json TEXT,                     -- snapshot completo para auditoria
  UNIQUE(device_id, camera_id)
);

-- Cada captura feita pelo app (metadados; a imagem fica no MediaStore)
CREATE TABLE capture (
  id INTEGER PRIMARY KEY,
  camera_id INTEGER REFERENCES camera(id),
  ts INTEGER, mode TEXT,                         -- FOTO, NOITE, HDR, PRO
  frames_in INTEGER, frames_used INTEGER, ref_index INTEGER,
  iso INTEGER, exposure_ns INTEGER, ev REAL, lux_est REAL, motion_px REAL, gyro_rms REAL,
  nr_mode INTEGER, edge_mode INTEGER, raw INTEGER,
  noise_before REAL, noise_after REAL, mean_weight REAL, max_shift_px REAL,
  sharpness REAL, texture_energy REAL, clipped_pct REAL,
  align_ms INTEGER, merge_ms INTEGER, total_ms INTEGER,
  style_id INTEGER, style_version INTEGER, media_uri TEXT,
  sheet_json TEXT                                -- a "ficha" que o CameraController já monta (j.sheet)
);

-- Modelo de ruído calibrado: σ por faixa de brilho para cada câmera/ISO
CREATE TABLE noise_model (
  id INTEGER PRIMARY KEY,
  camera_id INTEGER REFERENCES camera(id),
  domain TEXT NOT NULL,                          -- 'YUV' ou 'RAW'
  iso INTEGER NOT NULL, sigma_bins TEXT NOT NULL,-- 16 valores CSV
  noise_profile_s REAL, noise_profile_o REAL,    -- SENSOR_NOISE_PROFILE
  samples INTEGER, updated INTEGER,
  UNIQUE(camera_id, domain, iso)
);

-- Estilos versionados (o StyleCatalog vira semente desta tabela)
CREATE TABLE style (
  id INTEGER PRIMARY KEY, version INTEGER NOT NULL,
  family TEXT, name TEXT,
  params_json TEXT NOT NULL,                     -- contrast, saturation, …, sharpen, texture, clarity, grain…
  inspiration_note TEXT,                         -- texto próprio, sem material proprietário
  UNIQUE(name, version)
);

-- Imagens de referência e comparações (por exemplo, câmera nativa × app, cena do copo)
CREATE TABLE reference_image (
  id INTEGER PRIMARY KEY,
  label TEXT, scene TEXT,                        -- 'copo_vidro_luz_interna'
  source TEXT,                                   -- 'nativa_X8U', 'app', 'DNG'
  device_id INTEGER REFERENCES device(id), camera_id INTEGER REFERENCES camera(id),
  media_uri TEXT, iso INTEGER, exposure_ns INTEGER, added INTEGER
);
CREATE TABLE comparison (
  id INTEGER PRIMARY KEY,
  a_ref INTEGER REFERENCES reference_image(id), b_ref INTEGER REFERENCES reference_image(id),
  region TEXT,                                   -- 'plano', 'textura', 'borda', 'pele'
  noise_a REAL, noise_b REAL, texture_a REAL, texture_b REAL, halo_a REAL, halo_b REAL,
  verdict TEXT, created INTEGER
);

-- Avisos do agente de Qualidade e feedback do usuário (sem autoajuste de limites)
CREATE TABLE advice (
  id INTEGER PRIMARY KEY, capture_id INTEGER REFERENCES capture(id),
  agent TEXT, rule_id TEXT, rule_version INTEGER,
  metric TEXT, value REAL, threshold REAL, message TEXT,
  user_feedback TEXT,                            -- 'util', 'nao_util', 'dispensado'
  created INTEGER
);
```

Tamanho esperado: bem menos de 1 MB por mil capturas, porque só metadados vão para o banco. As imagens continuam no MediaStore ou na pasta do app.

## 4. Base de conhecimento dentro do app

Como no Agro (`public/knowledge/*.md`), empacotar em `app/src/main/assets/knowledge/` versões curtas e revisadas destes documentos:
- `capacidades_<modelo>.md`: gerado a partir da tabela `camera`.
- `estilos.md`: inspiração e parâmetros de cada estilo, sem material proprietário.
- `pipeline.md`: o que cada etapa da fusão faz, para a tela “Sobre esta foto”.

## 5. Plano de implementação em etapas

| Etapa | Entrega | Critério de pronto |
|---|---|---|
| **0. Dados** | Room com `device`, `camera` e `capture`. O `CameraCaps` grava o snapshot das características (inclui o resultado real de `getSupportedExtensions()`) e o `CameraController` grava cada `j.sheet`. | Após uma captura em cada aparelho, as 3 linhas `device` e as linhas `camera` batem com `capacidades_aparelhos.md`. |
| **1. Agente Ruído** | Tabela `noise_model`, alimentada pelo `BurstFusion.Result`, e uso como prior no `noiseBetweenFrames`. | O σ medido e o prior divergem menos de 20% em cenas paradas; teste unitário no PC. |
| **2. Agente Qualidade (Audit)** | Métricas de fantasma, halo, desfoque e recorte depois da fusão; tabela `advice`; aviso discreto na revisão da foto. | No máximo 3 avisos por foto, com métrica e limiar; nenhum aviso em bursts de teste limpos. |
| **3. Estilos versionados** | `style` semeada do `StyleCatalog`, mais os parâmetros novos (`sharpen`, `texture`, `clarity`, `grain` etc.) no `StylePipeline`. | Os 40 estilos, mais o Natural, renderizam iguais aos atuais com os campos novos em 0 (teste de regressão por *hash* de imagem). |
| **4. Agente Exposição** | Motion metering simples (movimento da prévia e giroscópio) define N × tempo × ISO, com teto de ISO analógico. | Na noite em mão, menos quadros tremidos (peso médio > 0,6) que no plano fixo atual. |
| **5. Referências e Comparador** | Tela para importar fotos de referência (câmera nativa) e comparação por região. | A cena do copo gera um relatório app × nativa salvo em `comparison`. |
| **6. Revisor (WorkManager)** | Revisão só com o aparelho carregando: recalcula os modelos e detecta mudança de firmware. | Depois de atualizar o firmware, o app pede recalibração e não usa o modelo antigo. |
| **7. Base de conhecimento** | `assets/knowledge/*.md` e tela “Sobre esta foto” (o que a fusão fez, com números). | O usuário vê quadros usados, ruído antes e depois e a nitidez aplicada. |

Cada etapa é independente, testável no PC (as classes puras em Java, como o `BurstFusion`) e não muda o comportamento da captura até ser ligada.

## Fontes (projeto local)

- `AGRO-Gestao-Claude/app/src/features/advisor/agent.js`
- `AGRO-Gestao-Claude/app/src/data/agent-guidelines.js`
- `AGRO-Gestao-Claude/app/public/knowledge/agente-operacional.md`
- `AGRO-Gestao-Claude/app/public/knowledge/hierarquia-inteligencia.md`
- `AGRO-Gestao-Claude/app/android/app/src/main/java/br/com/territorio/gestao/AgroAgentReviewWorker.java`
- Android Room: https://developer.android.com/training/data-storage/room ; WorkManager: https://developer.android.com/topic/libraries/architecture/workmanager

**Licenças:** Room e WorkManager são AndroidX, sob Apache-2.0, compatível com app fechado desde que se mantenham os avisos. A proposta não usa código do Agro Gestão, só a arquitetura (projeto do próprio usuário).
