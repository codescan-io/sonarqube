/*
 * SonarQube
 * Copyright (C) 2009-2024 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.security;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

/**
 * Parser for CVSS breakdown data from rule security standards or metadata.
 * CVSS breakdown data is expected to be stored as JSON in the rule's security standards
 * or in a separate JSON field.
 */
public class CvssBreakdownParser {

    private static final Gson GSON = new Gson();

    /**
     * Parse CVSS breakdown from rule's security standards JSON.
     * The JSON structure is expected to be:
     * {
     *   "cvssMetrics": {
     *     "base": { "attackVector": {"value": "N", "justification": "..."}, ... },
     *     "temporal": { ... },
     *     "environmental": { ... }
     *   },
     *   "cvssScores": {
     *     "base": 8.1,
     *     "temporal": 9.0,
     *     "environmental": 8.6,
     *     "overall": 9.0
     *   }
     * }
     */
    @CheckForNull
    public static CvssBreakdownData parseFromSecurityStandards(@Nullable String securityStandardsJson) {
        if (securityStandardsJson == null || securityStandardsJson.trim().isEmpty()) {
            return null;
        }

        try {
            JsonElement jsonElement = JsonParser.parseString(securityStandardsJson);
            if (!jsonElement.isJsonObject()) {
                return null;
            }

            JsonObject jsonObject = jsonElement.getAsJsonObject();
            JsonObject cvssMetrics = jsonObject.getAsJsonObject("cvssMetrics");
            JsonObject cvssScores = jsonObject.getAsJsonObject("cvssScores");

            if (cvssMetrics == null && cvssScores == null) {
                return null;
            }

            CvssBreakdownData breakdown = new CvssBreakdownData();

            // Parse scores
            if (cvssScores != null) {
                if (cvssScores.has("base") && cvssScores.get("base").isJsonPrimitive()) {
                    breakdown.setBaseScore(cvssScores.get("base").getAsDouble());
                }
                if (cvssScores.has("temporal") && cvssScores.get("temporal").isJsonPrimitive()) {
                    breakdown.setTemporalScore(cvssScores.get("temporal").getAsDouble());
                }
                if (cvssScores.has("environmental") && cvssScores.get("environmental").isJsonPrimitive()) {
                    breakdown.setEnvironmentalScore(cvssScores.get("environmental").getAsDouble());
                }
                if (cvssScores.has("overall") && cvssScores.get("overall").isJsonPrimitive()) {
                    breakdown.setOverallScore(cvssScores.get("overall").getAsDouble());
                }
            }

            // Parse metrics
            if (cvssMetrics != null) {
                if (cvssMetrics.has("base") && cvssMetrics.get("base").isJsonObject()) {
                    breakdown.setBaseMetrics(parseMetrics(cvssMetrics.getAsJsonObject("base")));
                }
                if (cvssMetrics.has("temporal") && cvssMetrics.get("temporal").isJsonObject()) {
                    breakdown.setTemporalMetrics(parseMetrics(cvssMetrics.getAsJsonObject("temporal")));
                }
                if (cvssMetrics.has("environmental") && cvssMetrics.get("environmental").isJsonObject()) {
                    breakdown.setEnvironmentalMetrics(parseMetrics(cvssMetrics.getAsJsonObject("environmental")));
                }
            }

            return breakdown.hasData() ? breakdown : null;
        } catch (Exception e) {
            // Log error but don't fail the request
            return null;
        }
    }

    private static List<CvssMetricData> parseMetrics(JsonObject metricsObject) {
        List<CvssMetricData> metrics = new ArrayList<>();
        for (String key : metricsObject.keySet()) {
            JsonElement element = metricsObject.get(key);
            if (element.isJsonObject()) {
                JsonObject metricObj = element.getAsJsonObject();
                CvssMetricData metric = new CvssMetricData();
                metric.setName(formatMetricName(key));
                if (metricObj.has("value") && metricObj.get("value").isJsonPrimitive()) {
                    metric.setValue(metricObj.get("value").getAsString());
                }
                if (metricObj.has("justification") && metricObj.get("justification").isJsonPrimitive()) {
                    metric.setJustification(metricObj.get("justification").getAsString());
                }
                metrics.add(metric);
            }
        }
        return metrics;
    }

    private static String formatMetricName(String key) {
        // Convert camelCase to Title Case
        return key.substring(0, 1).toUpperCase() + key.substring(1).replaceAll("([A-Z])", " $1");
    }

    public static class CvssBreakdownData {
        private Double baseScore;
        private Double temporalScore;
        private Double environmentalScore;
        private Double overallScore;
        private List<CvssMetricData> baseMetrics;
        private List<CvssMetricData> temporalMetrics;
        private List<CvssMetricData> environmentalMetrics;

        public boolean hasData() {
            return baseScore != null || temporalScore != null || environmentalScore != null || overallScore != null
                    || (baseMetrics != null && !baseMetrics.isEmpty())
                    || (temporalMetrics != null && !temporalMetrics.isEmpty())
                    || (environmentalMetrics != null && !environmentalMetrics.isEmpty());
        }

        public Double getBaseScore() {
            return baseScore;
        }

        public void setBaseScore(Double baseScore) {
            this.baseScore = baseScore;
        }

        public Double getTemporalScore() {
            return temporalScore;
        }

        public void setTemporalScore(Double temporalScore) {
            this.temporalScore = temporalScore;
        }

        public Double getEnvironmentalScore() {
            return environmentalScore;
        }

        public void setEnvironmentalScore(Double environmentalScore) {
            this.environmentalScore = environmentalScore;
        }

        public Double getOverallScore() {
            return overallScore;
        }

        public void setOverallScore(Double overallScore) {
            this.overallScore = overallScore;
        }

        public List<CvssMetricData> getBaseMetrics() {
            return baseMetrics;
        }

        public void setBaseMetrics(List<CvssMetricData> baseMetrics) {
            this.baseMetrics = baseMetrics;
        }

        public List<CvssMetricData> getTemporalMetrics() {
            return temporalMetrics;
        }

        public void setTemporalMetrics(List<CvssMetricData> temporalMetrics) {
            this.temporalMetrics = temporalMetrics;
        }

        public List<CvssMetricData> getEnvironmentalMetrics() {
            return environmentalMetrics;
        }

        public void setEnvironmentalMetrics(List<CvssMetricData> environmentalMetrics) {
            this.environmentalMetrics = environmentalMetrics;
        }
    }

    public static class CvssMetricData {
        private String name;
        private String value;
        private String justification;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getJustification() {
            return justification;
        }

        public void setJustification(String justification) {
            this.justification = justification;
        }
    }
}
 