/**
 * The Natural Cam — ponte "Ajuste IA".
 *
 * O app envia a foto reduzida (JPEG base64) + os nomes dos estilos e filmes que ele conhece +
 * um pedido opcional do usuário. O Claude analisa a imagem e devolve SÓ parâmetros do editor
 * do app (estilo, sliders, granulado, desfoque). A imagem final continua sendo processada no
 * celular, em resolução cheia — a IA não gera nem inventa pixels.
 *
 * A chave da Anthropic fica só aqui (segredo ANTHROPIC_API_KEY), nunca no APK.
 * O app se identifica com APP_TOKEN (cabeçalho x-app-token).
 */
import Anthropic from "@anthropic-ai/sdk";

interface Env {
  ANTHROPIC_API_KEY: string;
  APP_TOKEN: string;
}

interface Pedido {
  image: string; // JPEG em base64, sem prefixo data:
  pedido?: string;
  estilos: string[];
  filmes: string[];
}

const MAX_IMAGE_B64 = 4_500_000; // ~3,3 MB de JPEG — o app manda ~1280 px, bem menos que isso

const SYSTEM = `Você é o editor de fotos do app The Natural Cam.
Recebe uma foto e devolve apenas os ajustes do editor do app, em JSON. Você não desenha nem gera pixels.

Princípios do app (obrigatórios):
- Resultado natural, como o olho humano vê. Nunca nitidez excessiva: textura normalmente entre 0 e 0,30;
  claridade entre -0,2 e 0,35. Só passe disso se o pedido do usuário exigir.
- Cores reais. Pele de qualquer tom deve ficar rica e verdadeira: pele negra nunca acinzentada nem clareada.
- Prefira ajustes sutis: exposição, contraste, saturação, temperatura, sombras e realces entre -0,4 e 0,4,
  salvo foto claramente errada (muito escura, muito estourada, cor muito puxada).
- Redução de ruído (0..1) só se houver ruído visível; granulado (0..1) só quando combinar com o estilo
  escolhido (filme, P&B documental) — 0 em geral.
- Escolha o estilo que melhor serve à foto entre os nomes fornecidos (use exatamente um deles;
  "Natural" quando nenhum estilo ajudar). intensidade 0..1 do estilo.
- Desfoque: ligue só se houver um sujeito claro (pessoa, objeto principal) e fundo que se beneficie.
  modo "bokeh" para retrato comum; "movimento" ou "zoom" só se o pedido ou a cena pedirem dinamismo.
  x e y = ponto do sujeito que deve ficar nítido (olho mais próximo, em retratos), normalizados 0..1
  na imagem enviada (0,0 = canto superior esquerdo). intensidade 0..1 (0,3–0,6 é natural).
- Se o usuário fizer um pedido, siga-o dentro desses princípios.
- "analise": 1–2 frases em português do Brasil dizendo o que você viu e o que ajustou.`;

function schema(estilos: string[], filmes: string[]) {
  const num = { type: "number" };
  return {
    type: "object",
    additionalProperties: false,
    required: [
      "analise", "estilo", "intensidade", "exposicao", "contraste", "saturacao", "temperatura",
      "sombras", "realces", "textura", "claridade", "reducao_ruido", "vinheta", "granulado",
      "filme", "desfoque",
    ],
    properties: {
      analise: { type: "string" },
      estilo: { type: "string", enum: estilos },
      intensidade: num,
      exposicao: num,
      contraste: num,
      saturacao: num,
      temperatura: num,
      sombras: num,
      realces: num,
      textura: num,
      claridade: num,
      reducao_ruido: num,
      vinheta: num,
      granulado: num,
      filme: { type: "string", enum: filmes },
      desfoque: {
        type: "object",
        additionalProperties: false,
        required: ["ligado", "modo", "x", "y", "intensidade"],
        properties: {
          ligado: { type: "boolean" },
          modo: { type: "string", enum: ["bokeh", "movimento", "zoom"] },
          x: num,
          y: num,
          intensidade: num,
        },
      },
    },
  };
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}

/** Comparação em tempo constante para o token do app. */
function sameToken(a: string, b: string): boolean {
  if (!a || !b || a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    const url = new URL(req.url);
    if (req.method !== "POST" || url.pathname !== "/ajuste") return json({ erro: "use POST /ajuste" }, 404);
    if (!sameToken(req.headers.get("x-app-token") ?? "", env.APP_TOKEN ?? "")) {
      return json({ erro: "token do app inválido" }, 401);
    }

    let p: Pedido;
    try {
      p = (await req.json()) as Pedido;
    } catch {
      return json({ erro: "corpo JSON inválido" }, 400);
    }
    if (!p.image || typeof p.image !== "string" || p.image.length > MAX_IMAGE_B64) {
      return json({ erro: "imagem ausente ou grande demais" }, 400);
    }
    const estilos = Array.isArray(p.estilos) && p.estilos.length ? p.estilos.slice(0, 200) : ["Natural"];
    const filmes = Array.isArray(p.filmes) && p.filmes.length ? p.filmes.slice(0, 20) : ["Tri-X 400"];
    const pedido = (p.pedido ?? "").toString().slice(0, 500).trim();

    const client = new Anthropic({ apiKey: env.ANTHROPIC_API_KEY });
    try {
      const response = await client.beta.messages.create({
        model: "claude-opus-5-5",
        max_tokens: 16000,
        // se o modelo principal recusar, a API tenta automaticamente outro modelo adequado
        betas: ["server-side-fallback-2026-07-01"],
        fallbacks: "default",
        system: SYSTEM,
        output_config: {
          effort: "medium",
          format: { type: "json_schema", schema: schema(estilos, filmes) },
        },
        messages: [
          {
            role: "user",
            content: [
              { type: "image", source: { type: "base64", media_type: "image/jpeg", data: p.image } },
              {
                type: "text",
                text:
                  `Estilos disponíveis: ${estilos.join(" | ")}\n` +
                  `Filmes para granulado: ${filmes.join(" | ")}\n` +
                  (pedido ? `Pedido do usuário: ${pedido}` : "Sem pedido específico: deixe a foto no seu melhor, natural."),
              },
            ],
          },
        ],
      });

      if (response.stop_reason === "refusal") {
        return json({ erro: "a IA recusou ajustar esta foto", detalhe: response.stop_details?.category ?? null }, 422);
      }
      if (response.stop_reason === "max_tokens") {
        return json({ erro: "resposta da IA incompleta; tente de novo" }, 502);
      }
      const text = response.content.flatMap((b) => (b.type === "text" ? [b.text] : [])).join("");
      let ajuste: unknown;
      try {
        ajuste = JSON.parse(text);
      } catch {
        return json({ erro: "resposta da IA fora do formato" }, 502);
      }
      return json({ ajuste, modelo: response.model });
    } catch (e) {
      if (e instanceof Anthropic.RateLimitError) return json({ erro: "limite de uso da IA; tente em instantes" }, 429);
      if (e instanceof Anthropic.AuthenticationError) return json({ erro: "chave da Anthropic inválida no servidor" }, 500);
      if (e instanceof Anthropic.APIConnectionError) return json({ erro: "sem conexão com a IA" }, 503);
      if (e instanceof Anthropic.APIError) return json({ erro: `erro da IA (${e.status ?? "?"})` }, 502);
      return json({ erro: "falha inesperada no servidor" }, 500);
    }
  },
};
