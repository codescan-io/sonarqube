import * as React from 'react';

import { withOrganizationContext } from "../../organizations/OrganizationContext";
import withCurrentUserContext from "../../../app/components/current-user/withCurrentUserContext";
import { LoggedInUser } from '../../../../js/types/users';
import { Organization } from '../../../../js/types/types';
import { searchProjects } from '../../../../js/api/components';

import DeferredSpinner from '../../../components/ui/DeferredSpinner';


interface Props {
    currentUser: LoggedInUser;
    organization: Organization;
}

interface State {
      projects:any[],
      loadingProjects:boolean,
      selectedOption: string,
      loading:true,
      branchLikes: [],
      comparisonBranchesEnabled:false,
      isPending: false,
      warnings:[],
      branchLike:undefined,
      component:undefined
}

class PolicyResults extends React.PureComponent<Props, State> {
    mounted = false;
    orgId:string = '';


    componentDidMount() {
        this.mounted = true;
        const {currentUser, organization} = {...this.props}
        this.orgId = organization.kee 
        this.setState({loadingProjects:true})
        this.setState({projects:[]})
        searchProjects({filter: 'tags=policy', organization: this.orgId}).then((res) =>{
            this.setState({projects:res.components})
            if(res.components.length>0){
                this.setState({selectedOption:res.components[0].key})
            }
            this.setState({loadingProjects:false})
        })
      }
    
      componentWillUnmount() {
        this.mounted = false;
      }
    

    handleChange = ({target} : any) => {
        console.log('option changed :: ', target)
        this.setState({selectedOption:target.value});
    }



    render(){
        const {currentUser, organization} = {...this.props}
        const {loadingProjects, projects, selectedOption} = {...this.state}
        console.log("------ Current User -------");
        console.log(currentUser);
        console.log("------ Organization -------");
        console.log(organization)
    return <> 
        <div className="page page-limited" style={{paddingBottom: "0"}}>
            <header className='page-header'>
                <h1>Policy Results</h1>
                
            </header>
            {loadingProjects ? (<><DeferredSpinner className="spacer-right" loading={true} /></>) : (<>
                <div className="display-flex-row">
                    <div className="width-25 big-spacer-right">
                            {
                                projects?.length == 0 ? (<><span> No projects found in the organization with "policy" tag </span> </>) : ( <>
                                    <span>Select Project: </span>
                                    <br/>
                                    <select style={{maxWidth:"100%"}}  
                                    value={selectedOption}
                                    onChange={this.handleChange}>
                                        {projects?.map(({ key, name }, index) => <option key={key} value={key}>{name}</option>)}
                                    </select>
                                </>)
                            }

                        </div>
                    </div>
            </>)}
        </div>
    </>
    }
}
  
  export default withCurrentUserContext(withOrganizationContext(PolicyResults));

  