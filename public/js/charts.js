/*
 * File:    charts.js
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Chart.js defaults that match the glass theme, plus the shared
 *          colour palette used by every chart.
 */

const CHART_COLORS = ["#22d3ee", "#a78bfa", "#f472b6", "#34d399", "#fbbf24", "#60a5fa", "#fb7185"];

if (window.Chart) {
  Chart.defaults.color = "#aab4d8";
  Chart.defaults.borderColor = "rgba(255,255,255,0.08)";
  Chart.defaults.font.family = '"Plus Jakarta Sans", system-ui, sans-serif';
  Chart.defaults.plugins.legend.labels.usePointStyle = true;
  Chart.defaults.plugins.tooltip.backgroundColor = "rgba(15,18,45,0.92)";
  Chart.defaults.plugins.tooltip.padding = 12;
  Chart.defaults.maintainAspectRatio = false;
}

/** Creates (or replaces) a chart on a canvas. */
function drawChart(canvas, config) {
  if (!window.Chart || !canvas) return null;
  const existing = Chart.getChart(canvas);
  if (existing) existing.destroy();
  return new Chart(canvas, config);
}

const randAxis = { ticks: { callback: (value) => "R " + Number(value).toLocaleString("en-ZA") } };
