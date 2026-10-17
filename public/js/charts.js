/*
 * File:    charts.js
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Chart.js defaults in the red-and-orange theme, plus the shared
 *          colour palette used by every chart.
 */

const CHART_COLORS = ["#f97316", "#dc2626", "#fdba74", "#b91c1c", "#fb923c", "#7f1d1d", "#ffedd5"];

if (window.Chart) {
  Chart.defaults.color = "#fcd9bd";
  Chart.defaults.borderColor = "rgba(253,186,116,0.15)";
  Chart.defaults.font.family = '"Poppins", system-ui, sans-serif';
  Chart.defaults.plugins.legend.labels.usePointStyle = true;
  Chart.defaults.plugins.tooltip.backgroundColor = "rgba(69,10,10,0.95)";
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
