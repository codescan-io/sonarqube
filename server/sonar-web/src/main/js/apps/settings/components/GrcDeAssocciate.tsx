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

import * as React from 'react';
import { Router, withRouter } from '../../../components/hoc/withRouter';
import { searchProjectTags, setProjectTags } from '../../../api/components';
import '../styles.css';


interface Props {
  component?: T.Component;
  grc:boolean; 
  router: Router;
}

interface State {
  loading: boolean;
  updatingTags:boolean;
  tags:[];
}

class GrcDeAssocciate extends React.PureComponent<Props, State> {
  mounted = false;
  state: State = { loading: true,
     tags:[],
     updatingTags: false
   };

  componentDidMount() {
    this.mounted = true;
    this.fetchTags();
  }

  componentDidUpdate(prevProps: Props) {
    if (prevProps.component !== this.props.component) {
      this.fetchTags();
    }
  }

  componentWillUnmount() {
    this.mounted = false;
  }

  fetchTags = () => {
    this.setState({ loading: true });
    searchProjectTags({q:"",ps:10}).then((data:any)=>{
      this.setState({ tags: data.tags });
      this.setState({ loading: false });
    },()=>{
      this.setState({ tags: [] });
      this.setState({ loading: false });
    })
  };

  deTagGRC=()=>{
    const {component, router} = this.props;
    const {tags}=this.state;
    const uTags = tags.filter(tag => tag!='grc');
    const data = {
      project: component?.key ? component?.key : "",
      tags: uTags.join(",")
    }
    this.setState({updatingTags:true});
    setProjectTags(data).then(()=>{
      this.setState({updatingTags:false});
      router.replace("/project/settings?id="+component?.key)
    },()=>{
      console.log("Error occured while updating the grc tag");
      this.setState({updatingTags:false});
    })
  }


  render() {
    const {loading, updatingTags} = this.state;
    return (
      <>{
        loading ? (<><i className='spinner'></i></>) : (
          <>
          <header className="page-header">
          <h1 className="page-title">GRC Settings</h1>
          <div className="page-description">Edit GRC Settings</div>
        </header>
        <div>
        <span><b>De Associate GRC</b> from this project. Once you De-associate, you will be taken back to codescan settings page.</span><br/><br/>
          {
            updatingTags ? (<><i className="spinner"></i></>) : (<>
            <button className='button' onClick={this.deTagGRC}>De Associate GRC</button>
            </>)
          }
        </div>
      </>)
      }</>);
  }
}

export default withRouter(GrcDeAssocciate);