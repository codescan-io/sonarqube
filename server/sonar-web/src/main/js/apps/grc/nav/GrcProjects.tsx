import React, {useEffect, useState} from 'react';
import {connect} from "react-redux";
import {Component, searchProjects} from "../../../api/components";
import {Link} from "react-router";

const GrcProjects = () => {

  const [projects, setProjects] = useState<Component[]>();
  const [showMyProjects, setShowMyProjects] = useState(false);

  useEffect(() => {
    searchProjects({filter: 'tags=grc'}).then(({components}) => {
      setProjects(components);
      if(components.length) {
        setShowMyProjects(true);
      }
    });
  }, []);

  return (
      <>
        {showMyProjects && <><li className="divider" role="separator" /><li><span>My Projects</span></li></>}
        {!!projects && projects.map(p => (
            <li key={p.key}>
              {/* TODO store the selected project key */}
              <Link to="/grc">{p.name}</Link>
            </li>
        ))}
      </>
  );
}


export default GrcProjects;