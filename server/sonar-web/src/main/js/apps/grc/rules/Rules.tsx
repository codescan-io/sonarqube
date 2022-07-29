/*
 * SonarQube
 * Copyright (C) 2009-2020 SonarSource SA
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
import React, {useEffect, useState} from "react";
import {searchRules} from "../../../api/rules";
import Rule = T.Rule;
import DeferredSpinner from "sonar-ui-common/components/ui/DeferredSpinner";

export default function Rules() {

  const [rules, setRules] = useState<Rule[]>();
  const [loading, setLoading] = useState<boolean>();

  useEffect(() => {
    setLoading(true);

    // TODO: Later we will replace 'sf' repository filter, with a new one created for GRC rules.
    searchRules({
      organization: 'b21',
      ps: 15,
      repositories: 'sf',
    }).then(response => {
      setLoading(false);
      setRules(response.rules);
    });
  }, [])

  return (
      <>
        <h1>GRC Analysis Rules</h1>

        <div className="data-box">
          <h2>Top 15 Rules of Apex repo</h2>

          <DeferredSpinner
              loading={loading}
          />

          {!loading && (
              <ul>
                {rules?.map(rule => (
                    <li>
                      {rule.name}
                    </li>
                ))}
              </ul>
          )}
        </div>
      </>
  );

}
