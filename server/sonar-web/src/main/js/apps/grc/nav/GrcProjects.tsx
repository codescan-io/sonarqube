import React, {useEffect, useState} from 'react';
import {connect} from "react-redux";
import {Component, searchProjects} from "../../../api/components";
import { Link } from 'react-router';
import { getGrcDashboardUrl } from '../../../helpers/urls';

const GrcProjects = () => {

  const [projects, setProjects] = useState<Component[]>();

  useEffect(() => {
    searchProjects({filter: 'tags=grc'}).then(({components}) => {
      setProjects(components);
    });
  }, []);

  return (
      <>
        <li>
          <span>My Projects</span>
        </li>
        {!!projects && projects.map(p => (
            <li key={p.key}>
              {/* TODO store the selected project key */}
              <Link to={getGrcDashboardUrl(p.key)}>{p.name}</Link>
            </li>
        ))}
      </>
  );
}

const mapStateToProps = () => {
};

const mapDispatchToProps = {};

export default connect(mapStateToProps, mapDispatchToProps)(GrcProjects);