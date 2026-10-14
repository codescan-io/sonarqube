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
package org.sonar.server.analytics.ws;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import javax.annotation.Nullable;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.WebService;
import org.sonar.server.analytics.ws.AnalyticsPeriod.Interval;
import org.sonar.server.exceptions.BadRequestException;

/** The 'from', 'to' and 'timeZone' parameters shared by the actions that read a period of CE activity. */
class AnalyticsPeriodParams {

  static final String PARAM_FROM = "from";
  static final String PARAM_TO = "to";
  static final String PARAM_TIME_ZONE = "timeZone";

  private AnalyticsPeriodParams() {
    // static only
  }

  static void define(WebService.NewAction action) {
    action.createParam(PARAM_FROM)
      .setDescription("First day of the period, included")
      .setRequired(true)
      .setExampleValue("2026-09-13");
    action.createParam(PARAM_TO)
      .setDescription("Last day of the period, included. Defaults to today.")
      .setExampleValue("2026-10-12");
    action.createParam(PARAM_TIME_ZONE)
      .setDescription("Time zone in which days are read. Defaults to the server time zone.")
      .setExampleValue("Europe/Paris");
  }

  static AnalyticsPeriod parse(Request request, Interval interval, long now) {
    ZoneId zone = parseZone(request.param(PARAM_TIME_ZONE));
    LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
    LocalDate from = parseDate(PARAM_FROM, request.mandatoryParam(PARAM_FROM));
    String toParam = request.param(PARAM_TO);
    LocalDate to = toParam == null ? today : parseDate(PARAM_TO, toParam);
    try {
      return new AnalyticsPeriod(from, to, zone, interval);
    } catch (IllegalArgumentException e) {
      throw BadRequestException.create(e.getMessage());
    }
  }

  private static ZoneId parseZone(@Nullable String timeZone) {
    if (timeZone == null) {
      return ZoneId.systemDefault();
    }
    try {
      return ZoneId.of(timeZone);
    } catch (DateTimeException e) {
      throw BadRequestException.create(String.format("'%s' is not a valid time zone", timeZone));
    }
  }

  private static LocalDate parseDate(String param, String value) {
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      throw BadRequestException.create(String.format("'%s' must be a date (yyyy-MM-dd), got '%s'", param, value));
    }
  }
}
