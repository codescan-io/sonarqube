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
