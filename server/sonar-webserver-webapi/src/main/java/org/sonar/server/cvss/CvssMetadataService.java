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
package org.sonar.server.cvss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads CVSS metadata from JSON resources on the classpath and exposes it
 * via an in-memory lookup by rule key.
 *
 * Supports BOTH formats:
 *
 * 1) Flat JSON (recommended):
 *    {
 *      "ruleKey": "vf:Csrf",
 *      "cvssScore": 9.0,
 *      ...
 *    }
 *
 * 2) Wrapped JSON (legacy):
 *    {
 *      "Csrf": {
 *        "ruleKey": "vf:Csrf",
 *        ...
 *      }
 *    }
 */
public class CvssMetadataService {

    private static final String CVSS_METRICS_PREFIX = "cvss-metrics/";

    private final Map<String, CvssRuleBreakdown> byRuleKey;

    public CvssMetadataService() {
        this.byRuleKey = loadMetadata();
    }

    public Optional<CvssRuleBreakdown> forRule(String ruleKey) {
        return Optional.ofNullable(byRuleKey.get(ruleKey));
    }

    // ----------------------------------------------------------------------
    // Loading entry point
    // ----------------------------------------------------------------------

    private Map<String, CvssRuleBreakdown> loadMetadata() {
        Map<String, CvssRuleBreakdown> map = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        try {
            File codeSource =
                    new File(CvssMetadataService.class.getProtectionDomain().getCodeSource().getLocation().toURI());

            if (codeSource.isFile() && codeSource.getName().endsWith(".jar")) {
                loadFromJar(map, mapper, codeSource);
            } else {
                loadFromClasspath(map, mapper);
            }

        } catch (Exception e) {
            System.err.println("Failed to load CVSS metadata: " + e.getMessage());
        }

        return Collections.unmodifiableMap(map);
    }

    // ----------------------------------------------------------------------
    // JAR loading (SonarQube runtime)
    // ----------------------------------------------------------------------

    private void loadFromJar(Map<String, CvssRuleBreakdown> map,
            ObjectMapper mapper,
            File jarFile) throws Exception {

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();

                if (!name.startsWith(CVSS_METRICS_PREFIX) || !name.endsWith(".json")) {
                    continue;
                }

                try (InputStream in = jar.getInputStream(entry)) {
                    parseJson(in, mapper, map);
                }
            }
        }
    }

    // ----------------------------------------------------------------------
    // IDE / tests
    // ----------------------------------------------------------------------

    private void loadFromClasspath(Map<String, CvssRuleBreakdown> map,
            ObjectMapper mapper) throws Exception {
        URL example =
                getClass().getClassLoader()
                        .getResource(CVSS_METRICS_PREFIX + "Csrf.json");

        if (example == null) {
            return;
        }

        File dir = new File(example.toURI()).getParentFile();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));

        if (files == null) {
            return;
        }

        for (File file : files) {
            try (InputStream in = new FileInputStream(file)) {
                parseJson(in, mapper, map);
            }
        }
    }

    // ----------------------------------------------------------------------
    // JSON parsing (supports wrapped + flat)
    // ----------------------------------------------------------------------

    private void parseJson(InputStream in,
            ObjectMapper mapper,
            Map<String, CvssRuleBreakdown> map) throws Exception {

        JsonNode root = mapper.readTree(in);

        // CASE 1: Flat JSON → directly CvssRuleBreakdown
        if (root.has("ruleKey")) {
            CvssRuleBreakdown cvss =
                    mapper.treeToValue(root, CvssRuleBreakdown.class);
            map.put(cvss.getRuleKey(), cvss);
            return;
        }

        // CASE 2: Wrapped JSON → { "Csrf": { ... } }
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();

            CvssRuleBreakdown cvss =
                    mapper.treeToValue(entry.getValue(), CvssRuleBreakdown.class);

            // Fallback: use wrapper key if ruleKey missing
            if (cvss.getRuleKey() == null) {
                cvss.setRuleKey(entry.getKey());
            }

            map.put(cvss.getRuleKey(), cvss);

        }
    }
}
