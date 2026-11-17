#!/usr/bin/env node
// Pacote de publicação e chamadas do job "Publicar conteúdo" (história 1.7). Sem
// dependências: só o Node (fetch nativo). O hash da versão é só da API; o script
// nunca o calcula.
//
//   node scripts/content-bundle.mjs bundle <unitKey> [--content <dir>]
//     Imprime o pacote {unit, stages, media}: o unit.json, os Estágios na ordem de
//     unit.stages e só as entradas de media.json que a Unidade referencia. É o
//     mesmo pacote do carregador local (UnitBundle.fromDirectory).
//
//   node scripts/content-bundle.mjs publish <unitKey> --api <url> [--content <dir>] [--publish false]
//     POST /api/v1/admin/content/units/<unitKey>/versions?publish=<true|false>.
//     Imprime a resposta; com GITHUB_OUTPUT, grava version-id, created e published.
//
//   node scripts/content-bundle.mjs check <unitKey> <versionId> --api <url>
//     GET .../versions/<versionId>: sai com 0 se a versão existe, 1 se não.
//
// O token vem só de FLUVENY_DEPLOY_TOKEN (nunca de argumento, para não ir ao log). Com
// FLUVENY_PROXY_SECRET, toda chamada leva também o X-Fluveny-Proxy (história 1.6).
import { appendFileSync, existsSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const KEY = /^[a-z0-9]+(-[a-z0-9]+)*$/;

function parseArgs(argv) {
  const positional = [];
  const options = {};
  for (let i = 0; i < argv.length; i++) {
    if (!argv[i].startsWith('--')) {
      positional.push(argv[i]);
      continue;
    }
    const name = argv[i].slice(2);
    if (name.includes('=')) fail(`use "--${name.split('=')[0]} <valor>", sem "=": ${argv[i]}`);
    const value = argv[i + 1];
    if (value === undefined || value.startsWith('--')) fail(`--${name} sem valor`);
    options[name] = value;
    i++;
  }
  return { positional, options };
}

function fail(message) {
  console.error(`content-bundle: ${message}`);
  process.exit(1);
}

function readJson(file) {
  try {
    return JSON.parse(readFileSync(file, 'utf8'));
  } catch (error) {
    fail(`não deu para ler ${file}: ${error.message}`);
  }
}

function collectMediaKeys(node, keys) {
  if (Array.isArray(node)) node.forEach((item) => collectMediaKeys(item, keys));
  else if (node && typeof node === 'object') {
    for (const [name, value] of Object.entries(node)) {
      if (name === 'mediaKey' && typeof value === 'string') keys.add(value);
      collectMediaKeys(value, keys);
    }
  }
  return keys;
}

export function buildBundle(unitKey, contentDir) {
  if (!KEY.test(unitKey)) fail(`unitKey fora do formato kebab-case: ${unitKey}`);
  const unitDir = join(contentDir, 'units', unitKey);
  const unitFile = join(unitDir, 'unit.json');
  if (!existsSync(unitFile)) fail(`não existe ${unitFile}`);
  const unit = readJson(unitFile);
  const stageKeys = [...new Set((unit.stages ?? []).filter((key) => typeof key === 'string'))];
  const stages = [];
  for (const key of stageKeys) {
    const file = join(unitDir, 'stages', `${key}.json`);
    // Estágio sem arquivo fica de fora; a API aponta o erro (STAGE_FILE_MISMATCH).
    if (existsSync(file)) stages.push(readJson(file));
  }
  const mediaFile = join(contentDir, 'media.json');
  const manifest = existsSync(mediaFile) ? readJson(mediaFile) : {};
  const media = {};
  for (const key of [...collectMediaKeys([unit, ...stages], new Set())].sort()) {
    // mediaKey fora do manifesto fica de fora; a API aponta (MEDIA_NOT_FOUND).
    if (Object.hasOwn(manifest, key)) media[key] = manifest[key];
  }
  return { unit, stages, media };
}

function token() {
  const value = process.env.FLUVENY_DEPLOY_TOKEN;
  if (!value) fail('FLUVENY_DEPLOY_TOKEN vazio: configure o secret no GitHub Environment do ambiente.');
  return value;
}

// Cabeçalhos de toda chamada: o token de deploy e, com FLUVENY_PROXY_SECRET, o segredo
// de proxy (história 1.6), que a API exige fora do perfil local. Também só do ambiente.
function headers(extra = {}) {
  const proxySecret = process.env.FLUVENY_PROXY_SECRET?.trim();
  return {
    Authorization: `Bearer ${token()}`,
    ...(proxySecret ? { 'X-Fluveny-Proxy': proxySecret } : {}),
    ...extra,
  };
}

function versionsUrl(api, unitKey) {
  if (!api) fail('falta --api <url da API>');
  return `${api.replace(/\/+$/, '')}/api/v1/admin/content/units/${encodeURIComponent(unitKey)}/versions`;
}

function output(values) {
  if (!process.env.GITHUB_OUTPUT) return;
  appendFileSync(
    process.env.GITHUB_OUTPUT,
    Object.entries(values)
      .map(([name, value]) => `${name}=${value}\n`)
      .join(''),
  );
}

async function publish(unitKey, options) {
  const bundle = buildBundle(unitKey, resolve(options.content ?? 'content'));
  const publishFlag = options.publish ?? 'true';
  if (publishFlag !== 'true' && publishFlag !== 'false') {
    fail(`--publish aceita só true ou false: ${publishFlag}`);
  }
  const response = await fetch(`${versionsUrl(options.api, unitKey)}?publish=${publishFlag}`, {
    method: 'POST',
    headers: headers({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(bundle),
  });
  const text = await response.text();
  if (!response.ok) {
    console.error(`A API respondeu ${response.status}:`);
    try {
      const problem = JSON.parse(text);
      console.error(`${problem.code}: ${problem.detail}`);
      for (const error of problem.errors ?? []) {
        console.error(`  ${error.file}#${error.pointer} [${error.rule}]${error.key ? ` (${error.key})` : ''} ${error.message}`);
      }
    } catch {
      console.error(text);
    }
    process.exit(1);
  }
  const { data } = JSON.parse(text);
  console.log(
    `${unitKey}: ${data.versionId} (${data.created ? 'versão nova' : 'versão já existia'}; ${data.published ? 'publicada' : 'candidata, ponteiro intacto'}).`,
  );
  output({ 'version-id': data.versionId, created: data.created, published: data.published });
}

async function check(unitKey, versionId, options) {
  if (!/^sha256:[0-9a-f]{64}$/.test(versionId ?? '')) fail(`versionId inválido: ${versionId}`);
  const response = await fetch(`${versionsUrl(options.api, unitKey)}/${versionId}`, {
    headers: headers(),
  });
  if (response.status === 200) {
    console.log(`${versionId} existe em ${options.api}.`);
    return;
  }
  if (response.status === 404) {
    console.error(`${versionId} não existe em ${options.api}: publique em homologação antes.`);
    process.exit(1);
  }
  fail(`a API respondeu ${response.status} ao consultar ${versionId}.`);
}

async function main() {
  const [command, ...rest] = process.argv.slice(2);
  const { positional, options } = parseArgs(rest);
  switch (command) {
    case 'bundle':
      process.stdout.write(JSON.stringify(buildBundle(positional[0] ?? '', resolve(options.content ?? 'content'))) + '\n');
      break;
    case 'publish':
      await publish(positional[0] ?? '', options);
      break;
    case 'check':
      await check(positional[0] ?? '', positional[1], options);
      break;
    default:
      fail('uso: content-bundle.mjs bundle|publish|check (veja o cabeçalho do arquivo)');
  }
}

main().catch((error) => fail(error.message));
