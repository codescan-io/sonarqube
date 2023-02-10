/*
 * SonarQube
 * Copyright (C) 2009-2023 SonarSource SA
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
package org.sonar.server.app;

import org.sonar.process.PluginFileWriteRule;
import org.sonar.process.PluginSecurityManager;
import org.sonar.process.ProcessProperties;
import org.sonar.process.Props;

public class WebSecurityManager {
  private final PluginSecurityManager pluginSecurityManager;
  private final Props props;

  private boolean applied;

  public WebSecurityManager(PluginSecurityManager pluginSecurityManager, Props props) {
    this.pluginSecurityManager = pluginSecurityManager;
    this.props = props;
  }

  public void apply() {
    if (applied) {
      throw new IllegalStateException("can't apply twice");
    }
    applied = true;

    PluginFileWriteRule writeRule = new PluginFileWriteRule(
      props.nonNullValueAsFile(ProcessProperties.Property.PATH_HOME.getKey()).toPath(),
      props.nonNullValueAsFile(ProcessProperties.Property.PATH_TEMP.getKey()).toPath());
    pluginSecurityManager.restrictPlugins(writeRule);
  }
}
