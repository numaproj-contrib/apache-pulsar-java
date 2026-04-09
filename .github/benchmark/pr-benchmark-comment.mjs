#!/usr/bin/env node
/**
 * Reads benchmark-results.json and gh-pages branch at dev/bench/data.js baseline metrics;
 * writes a Markdown PR comment to stdout for sticky-pull-request-comment.
 *
 * Supports both bigger-is-better (throughput) and lower-is-better (latency, CPU, memory).
 * Regression threshold: a metric must degrade by more than ratio× vs baseline (e.g. 2× = 200%).
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

// reads current benchmark results from benchmark-results.json
const currentBenchmarkResults = JSON.parse(fs.readFileSync(currentPath, "utf8"));

/** @type {Map<string, number>} */
const baselineMetricsMap = new Map();

// reads baseline benchmark results from gh-pages branch at dev/bench/data.js
// checks if the file exists, is not empty, and is a valid JSON file
if (baselinePath && fs.existsSync(baselinePath) && fs.statSync(baselinePath).size > 0) {
  const raw = fs.readFileSync(baselinePath, "utf8");
  const eq = raw.indexOf("="); // find the index of the equal sign
  const jsonPart = raw.slice(eq + 1).trim().replace(/;\s*$/, ""); // extract the JSON part
  try {
    const data = JSON.parse(jsonPart);
    const arr = data?.entries?.Benchmark;
    // if the array is not empty, set the baseline map with the name and value of the last entry in the array
    if (Array.isArray(arr) && arr.length > 0) {
      const last = arr[arr.length - 1];
      for (const b of last.benches || []) {
        baselineMetricsMap.set(b.name, b.value);
      }
    }
  } catch (e) {
    console.error(`warning: could not parse baseline : ${e.message}`);
  }
}

const lowerIsBetter = new Set([
  "Processing Latency (per batch)",
  "Read Latency (per batch)",
  "Ack Latency (per batch)",
  "Consumer CPU",
  "Consumer Memory",
]);

/** @returns {boolean | null} null = unknown / no baseline */
function regressed(currentVal, baselineVal, smallerIsBetter) {
  if (baselineVal == null || baselineVal === undefined) return null;
  if (baselineVal <= 0) return null;
  if (currentVal <= 0) return !smallerIsBetter;
  if (smallerIsBetter) return currentVal / baselineVal > ratioThreshold;
  return baselineVal / currentVal > ratioThreshold;
}

// formats the number to a string
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
  `Compared to the **latest result on \`gh-pages\`** (last \`main\` run that updated charts). Regression when a metric degrades by more than **${ratioThreshold}×** vs baseline.`,
);
lines.push("");
lines.push("| Metric | This run | Baseline (main) | Change | Status |");
lines.push("| --- | ---: | ---: | ---: | --- |");

for (const { name, value, unit } of currentBenchmarkResults) {
  // getting the baseline metric to compare against current metric to check regression
  const base = baselineMetricsMap.get(name);
  if (base !== undefined) anyBaseline = true;
  const smallerIsBetter = lowerIsBetter.has(name);
  const reg = regressed(value, base, smallerIsBetter);
  if (reg === true) anyRegression = true;

  let status = "—";
  if (reg === true) status = "⚠️ Regression";
  else if (reg === false) {
    const improved = smallerIsBetter ? value < base : value > base;
    status = improved ? "✅ Improved" : "OK";
  } else if (base === undefined) status = "No baseline metric to compare against";

  let change = "—";
  if (base !== undefined && base > 0 && value > 0) {
    const percentageChange = ((value - base) / base) * 100;
    change = `${percentageChange > 0 ? "+" : ""}${percentageChange.toFixed(1)}%`;
  } else if (base !== undefined && value <= 0 && base > 0) {
    change = smallerIsBetter ? "−100%" : "−∞";
  }

  lines.push(
    `| ${name} | ${fmt(value)} ${unit || ""} | ${base !== undefined ? fmt(base) : "—"} ${unit || ""} | ${change} | ${status} |`,
  );
}

lines.push("");
if (!anyBaseline) {
  lines.push(
    "**Note:** No baseline metrics found (empty or missing \`gh-pages\` \`dev/bench/data.js\`). Merge a run on \`main\` first so charts and comparisons exist.",
  );
} else if (anyRegression) {
  lines.push(
    "**Result:** At least one metric regressed beyond the threshold vs \`main\` baseline.",
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
