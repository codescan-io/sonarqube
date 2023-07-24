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
package org.sonar.db.audit;

public enum AuditOperation {
    ADD,
    ADD_CHARACTERISTIC,
    ADD_EDITOR,
    ADD_GROUP,
    ADD_USER,
    DEACTIVATE,
    DELETE,
    DELETE_EDITOR,
    DELETE_GROUP,
    DELETE_USER,
    COMPONENT_KEY_UPDATE,
    COMPONENT_KEY_BRANCH_UPDATE,
    SET,
    SET_LICENSE,
    UPDATE,
    UPDATE_SECRET,
    UPDATE_PASSWORD,
    UPDATE_CHARACTERISTIC,
    UPDATE_COMPONENT_VISIBILITY
}
