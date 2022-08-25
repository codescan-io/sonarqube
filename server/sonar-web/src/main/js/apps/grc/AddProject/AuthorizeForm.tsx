/* eslint-disable object-shorthand */
/* eslint-disable jsx-a11y/no-onchange */
import React from 'react';
import { authorizeToken, updateIntegration } from '../../../api/codescan';
import Salesforce, {parseError, parseErrorObject} from './Salesforce';

interface Props {
    organization: T.Organization;
    hashState: any;
    closeForm: () => any;
    onModified: (data: any) => any;
   // originalProject: any;
}

interface State {
  disabled: boolean;
  valid: boolean;
  scheduling: any,
  scheduling_hour: any;
  loading: boolean;
 // errorMsg: any;
  projectVersion: any;
  testmode: any;
  repoName: string;
  repoUrl: string;
  repoUsername: string;
  repoPassword: string;
  pullRequests: boolean;
  projectKey: any;
  projectName: any;
  auth: any;
  open: boolean;
  authHandler: any;
  branchName: any;
  branchType: any;
}

export default class AuthorizeForm extends React.PureComponent<Props, State> {

  constructor(props: Props) {
    super(props);
  }

  componentDidMount() {
    this.setState({
      loading: true,
      disabled: false,
      valid: false,
      //errorMsg: "",
      projectVersion: 1,
      testmode: "disabled",
      scheduling: "0",
      scheduling_hour: "-1",
      repoName: "",
      repoUrl: "",
      repoUsername: "",
      repoPassword: "",
      pullRequests: true
    });
    const { hashState } = this.props;

    if ( typeof(this.props.hashState.error) != 'undefined' ){
      return;
    } 

    const authHandler = new Salesforce(this.props);

    if (authHandler.requiresAuthorizeToken()){
      authorizeToken({
        code: hashState.code,
        authType: hashState.authType,
        host: hashState.host,
        organization: hashState.organization
      }).then((response)=>{
        window.location.hash = ''; //remove hash
        const errorMsg = parseErrorObject(response);
        if ( errorMsg != null) {
          //this.setState({errorMsg: errorMsg});
        }else{
          this.setState({
            projectKey: response.projectKey,
            projectName: response.projectName,
            auth: response.auth,
            //authHandler,
            //branchName: hashState.branchName,
            //branchType: hashState.branchType
          });
          authHandler.onAuthorizeToken(this);
        }

      }).catch(e => {
        return parseError(e).then((message: any) => {
          if(message) { 
            //this.setState({errorMsg: message});
          }
        })      
      });
    }
    else {
      window.location.hash = ''; //remove hash
      if(hashState) {
      this.setState({
        auth: {
          authType: hashState?.authType
        }
       //authHandler
      });
    }
      authHandler.onAuthorizeToken(this);
    }
    this.handleSubmit(Event);
  }

  handleSubmit = (e: any) => {
    e.preventDefault();

    //calculate scheduling
    let scheduling = this.state?.scheduling;
    if ( scheduling === "-1" ){
      scheduling = this.state?.scheduling_hour;
    }
    
    const createData: any = {
      organizationId: this.props.hashState.organization,
      testmode: this.state?.testmode,
      scheduling: scheduling,
      projectVersion: this.state?.projectVersion,
      repoName: this.state?.repoName,
      pullRequests: this.state?.pullRequests,
      repoUrl: this.state?.repoUrl,
      repoUsername: this.state?.repoUsername,
      repoPassword: this.state?.repoPassword,
      projectKey: '',
      projectName: '',
      auth: JSON.stringify(this.state?.auth)
    };
    
      //link to existing project...
      createData.projectKey = this.props.hashState.projectKey;
      createData.projectName = this.props.hashState.projectName;

      updateIntegration(createData).then(()=>{
        this.props.onModified(createData);
      }).catch(e => {
        return parseError(e).then((message: any) => {});
      });

  };

  render() {
    return ({});
  }

}
