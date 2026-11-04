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
package org.sonar.scanner.scan.filesystem;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.concurrent.ThreadSafe;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.internal.PathPattern;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.MessageException;
import org.sonar.scanner.repository.language.Language;
import org.sonar.scanner.repository.language.LanguagesRepository;

import static java.util.Collections.unmodifiableMap;

/**
 * Detect language of a source file based on its suffix and configured patterns.
 */
@ThreadSafe
public class LanguageDetection {

  private static final Logger LOG = LoggerFactory.getLogger(LanguageDetection.class);

  /**
   * Lower-case extension -> languages
   */
  private final Map<Language, PathPattern[]> patternsByLanguage;
  /**
   * Lower-case declared file suffixes, matched against the lower-cased file path so that a suffix is case-insensitive as a whole,
   * not only after its last dot (e.g. "validationRule-meta.xml")
   */
  private final Map<Language, PathPattern[]> suffixPatternsByLanguage;
  private final List<Language> languagesToConsider;
  private final Map<String, Language> languageCacheByPath;

  public LanguageDetection(Configuration settings, LanguagesRepository languages) {
    Map<Language, PathPattern[]> patternsByLanguageBuilder = new LinkedHashMap<>();
    Map<Language, PathPattern[]> suffixPatternsByLanguageBuilder = new LinkedHashMap<>();
    for (Language language : languages.all()) {
      String[] filePatterns = settings.getStringArray(getFileLangPatternPropKey(language.key()));
      PathPattern[] pathPatterns = PathPattern.create(filePatterns);
      if (pathPatterns.length > 0) {
        patternsByLanguageBuilder.put(language, pathPatterns);
        suffixPatternsByLanguageBuilder.put(language, new PathPattern[0]);
      } else {
        PathPattern[] suffixPatterns = getSuffixPatterns(language);
        PathPattern[] filenamePatterns = getFilenamePatterns(language);
        patternsByLanguageBuilder.put(language, filenamePatterns);
        suffixPatternsByLanguageBuilder.put(language, suffixPatterns);
        LOG.debug("Declared patterns of language {} were converted to {}", language, getDetails(language, suffixPatterns, filenamePatterns));
      }
    }

    languagesToConsider = List.copyOf(patternsByLanguageBuilder.keySet());
    patternsByLanguage = unmodifiableMap(patternsByLanguageBuilder);
    suffixPatternsByLanguage = unmodifiableMap(suffixPatternsByLanguageBuilder);
    languageCacheByPath = new HashMap<>();
  }

  private static PathPattern[] getSuffixPatterns(Language language) {
    return language.fileSuffixes().stream()
      .map(suffix -> "**/*" + sanitizeExtension(suffix))
      .distinct()
      .map(PathPattern::create)
      .toArray(PathPattern[]::new);
  }

  private static PathPattern[] getFilenamePatterns(Language language) {
    return language.filenamePatterns().stream()
      .map(filenamePattern -> "**/*" + filenamePattern)
      .distinct()
      .map(PathPattern::create)
      .toArray(PathPattern[]::new);
  }

  @CheckForNull
  Language language(Path absolutePath, Path relativePath) {
    Language detectedLanguage = languageCacheByPath.get(absolutePath.toString());
    if (detectedLanguage != null) {
      return detectedLanguage;
    }

    Path lowerCaseRelativePath = Paths.get(StringUtils.lowerCase(relativePath.toString()));
    for (Language language : languagesToConsider) {
      if (isCandidateForLanguage(absolutePath, relativePath, lowerCaseRelativePath, language)) {
        if (detectedLanguage == null) {
          detectedLanguage = language;
          languageCacheByPath.put(absolutePath.toString(), language);
        } else {
          // Language was already forced by another pattern
          throw MessageException.of(MessageFormat.format("Language of file ''{0}'' can not be decided as the file matches patterns of both {1} and {2}",
            relativePath, getDetails(detectedLanguage), getDetails(language)));
        }
      }
    }

    return detectedLanguage;
  }

  public Set<String> getDetectedLanguages() {
    return languageCacheByPath.values().stream().map(Language::key).collect(Collectors.toSet());
  }

  private boolean isCandidateForLanguage(Path absolutePath, Path relativePath, Path lowerCaseRelativePath, Language language) {
    PathPattern[] suffixPatterns = suffixPatternsByLanguage.get(language);
    if (Arrays.stream(suffixPatterns).anyMatch(pattern -> pattern.match(absolutePath, lowerCaseRelativePath, true))) {
      return true;
    }
    PathPattern[] patterns = patternsByLanguage.get(language);
    return patterns != null && Arrays.stream(patterns).anyMatch(pattern -> pattern.match(absolutePath, relativePath, false));
  }

  private static String getFileLangPatternPropKey(String languageKey) {
    return "sonar.lang.patterns." + languageKey;
  }

  private String getDetails(Language detectedLanguage) {
    return getDetails(detectedLanguage, suffixPatternsByLanguage.get(detectedLanguage), patternsByLanguage.get(detectedLanguage));
  }

  private static String getDetails(Language detectedLanguage, PathPattern[] suffixPatterns, PathPattern[] patterns) {
    return getFileLangPatternPropKey(detectedLanguage.key()) + " : " +
      Stream.concat(Arrays.stream(suffixPatterns), Arrays.stream(patterns)).map(PathPattern::toString).collect(Collectors.joining(","));
  }

  static String sanitizeExtension(String suffix) {
    if (!suffix.contains(".")) {
      return "." + StringUtils.lowerCase(suffix);
    }
    return StringUtils.lowerCase(suffix);
  }
}
