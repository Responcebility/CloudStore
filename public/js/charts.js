/*
 * File:    charts.js
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Chart.js defaults for the light Kasi-Crunch theme, plus the
 *          shared red/orange palette used by every chart.
 */

const CHART_COLORS = ["#cf343b", "#e8711c", "#171714", "#f2a65a", "#ad2832", "#8a857b", "#f6c9a0", "#5b5953"];

if (window.Chart) {
  Chart.defaults.color = "#6b675f";
  Chart.defaults.borderColor = "#e9e4d9";
  Chart.defaults.font.family = '"DM Sans", system-ui, sans-serif';
  Chart.defaults.plugins.legend.labels.usePointStyle = true;
  Chart.defaults.plugins.tooltip.backgroundColor = "#171714";
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

const randAxis = { ticks: { callback: (value) => "R" + Number(value).toLocaleString("en-ZA") } };
