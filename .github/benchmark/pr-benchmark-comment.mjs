#!/usr/bin/env node
/**
 * Reads benchmark-results.json and optional gh-pages dev/bench/data.js baseline;
 * writes a Markdown PR comment to stdout for sticky-pull-request-comment.
 *
 * Alert rule matches github-action-benchmark bigger-is-better + alert-threshold:
 * regression if (baseline / current) > ratio (e.g. 200% -> ratio 2).
 */
import fs from "fs";

const [, , currentPath, baselinePath] = process.argv;
const ratioThreshold = Number(process.env.ALERT_RATIO || "2");
const repo = process.env.GITHUB_REPOSITORY || "";
const runUrl = process.env.GITHUB_SERVER_URL && process.env.GITHUB_RUN_ID
  ? `${process.env.GITHUB_SERVER_URL}/${repo}/actions/runs/${process.env.GITHUB_RUN_ID}`
  : "";
const pagesBase = repo
  ? `https://${repo.split("/")[0]}.github.io/${repo.split("/")[1]}/`
  : "";

const current = JSON.parse(fs.readFileSync(currentPath, "utf8"));

/** @type {Map<string, number>} */
const baselineMap = new Map();

if (baselinePath && fs.existsSync(baselinePath) && fs.statSync(baselinePath).size > 0) {
  const raw = fs.readFileSync(baselinePath, "utf8");
  const eq = raw.indexOf("=");
  const jsonPart = raw.slice(eq + 1).trim().replace(/;\s*$/, "");
  try {
    const data = JSON.parse(jsonPart);
    const arr = data?.entries?.Benchmark;
    if (Array.isArray(arr) && arr.length > 0) {
      const last = arr[arr.length - 1];
      for (const b of last.benches || []) {
        baselineMap.set(b.name, b.value);
      }
    }
  } catch (e) {
    console.error(`warning: could not parse baseline: ${e.message}`);
  }
}

/** @returns {boolean | null} null = unknown / no baseline */
function regressed(currentVal, baselineVal) {
  if (baselineVal == null || baselineVal === undefined) return null;
  if (baselineVal <= 0) return null;
  if (currentVal <= 0) return true;
  return baselineVal / currentVal > ratioThreshold;
}

function fmt(n) {
  if (n == null || Number.isNaN(n)) return "—";
  if (typeof n === "number" && !Number.isInteger(n)) return String(n);
  return String(n);
}

let anyRegression = false;
let anyBaseline = false;

const lines = [];
lines.push("### Continuous benchmark");
lines.push("");
lines.push(
  `Compared to the **latest result on \`gh-pages\`** (last \`main\` run that updated charts). Regression when **baseline ÷ current** is **greater than ${ratioThreshold}×** (same idea as \`alert-threshold: 200%\`).`,
);
lines.push("");
lines.push("| Metric | This run | Baseline (main) | Ratio (base/run) | Status |");
lines.push("| --- | ---: | ---: | ---: | --- |");

for (const b of current) {
  const base = baselineMap.get(b.name);
  if (base !== undefined) anyBaseline = true;
  const reg = regressed(b.value, base);
  if (reg === true) anyRegression = true;
  let status = "—";
  if (reg === true) status = "Regression";
  else if (reg === false) {
    if (base !== undefined && b.value > base) status = "Improved";
    else status = "OK";
  } else if (base === undefined) status = "No baseline";

  const r =
    base !== undefined && b.value > 0
      ? (base / b.value).toFixed(2)
      : base !== undefined && b.value <= 0 && base > 0
        ? "∞"
        : "—";

  lines.push(
    `| ${b.name} | ${fmt(b.value)} ${b.unit || ""} | ${base !== undefined ? fmt(base) : "—"} ${b.unit || ""} | ${r} | ${status} |`,
  );
}

lines.push("");
if (!anyBaseline) {
  lines.push(
    "**Note:** No baseline found (empty or missing \`gh-pages\` \`dev/bench/data.js\`). Merge a run on \`main\` first so charts and comparisons exist.",
  );
} else if (anyRegression) {
  lines.push(
    "**Result:** At least one **bigger-is-better** metric regressed beyond the threshold vs \`main\` baseline.",
  );
} else {
  lines.push("**Result:** No regression beyond the threshold vs \`main\` baseline.");
}
lines.push("");
if (runUrl) lines.push(`[Workflow run](${runUrl})`);
if (pagesBase) {
  lines.push(
    ` · [GitHub Pages charts](${pagesBase}dev/bench/) (after \`main\` has published \`gh-pages\`).`,
  );
} else {
  lines.push("");
}

process.stdout.write(lines.join("\n"));
