import React, {useEffect, useState} from "react";
import "../styles.css";
import {setGrcUi} from "../../../store/appState";
import {connect} from "react-redux";
import {getComponentData, searchProjects} from "../../../api/components";
import {WithRouterProps} from "react-router";
import {Router, withRouter} from "../../../components/hoc/withRouter";
import {ComponentContext} from "../../../app/components/ComponentContext";
import { Location } from 'history'


interface Props {
  children: React.ReactElement;
  setGrcUi: (grc: boolean) => void;
  router: Pick<Router, 'replace'>;
  location:Location
}

function GrcPage({setGrcUi, children, router, location}: Props & WithRouterProps) {

  const [component, setComponent] = useState<T.Component>();
  const [loading, setLoading] = useState<boolean>();


  useEffect(() => {
    // Set 'grc' state variable to true when component is mounted.
    setGrcUi(true);

    // Load GRC projects.
    loadGrcProjects();

    // Set 'grc' state variable to false when component is unmounted.
    return () => {
      setGrcUi(false);
    };
  }, [location.query.id]);

  const loadGrcProjects = () => {
    setLoading(true);
    searchProjects({filter: 'tags=grc'})
    .then(({components}) => {
      let {id} = location.query;

      // if components are not there, if id is not given,
      if (!components.length) {
        return Promise.reject();
      }else{
        if(!id){
          id = components[0].key;
        }
      }


      router.replace('/grc/dashboard?id='+id);
      return getComponentData({component: id });
    })
    .then(({component}) => {
      setComponent(component);
      setLoading(false);
    }, () => {
      setLoading(false);
    });
  }
  
  return (
      <div className="grc-container">
        {loading ? (
            <div className="page page-limited">
              <i className="spinner"/>
            </div>
        ) : (
            <ComponentContext.Provider value={{branchLike: undefined, component}}>
              {React.cloneElement(children, {component})}
            </ComponentContext.Provider>
        )}
      </div>
  );
}

const mapStateToProps = () => ({});

const mapDispatchToProps = {
  setGrcUi
};

export default withRouter(connect(mapStateToProps, mapDispatchToProps)(GrcPage));