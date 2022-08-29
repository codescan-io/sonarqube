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
}

interface State {
  valid: boolean;
  loading: boolean;
  disabled: boolean;
  scheduling: any;
  scheduling_hour: any;
  projectVersion: any;
  testmode: any;
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
    const { hashState } = this.props;

    if ( typeof(this.props.hashState.error) != 'undefined' ){
      return;
    } 

    const authHandler = new Salesforce(this.props);
    if (authHandler.requiresAuthorizeToken()) {
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
            projectName: response.projectName
          });
          authHandler.onAuthorizeToken(this);
        }
        this.handleSubmit(response?.auth);
      }).catch(e => {
        return parseError(e).then((message: any) => {
          if(message) { 
            //this.setState({errorMsg: message});
          }
        })      
      });
    }
  }

  handleSubmit = (authT: any) => {
    const createData: any = {
      projectVersion: 1,
      testmode: "disabled",
      scheduling: "0",
      organizationId: this.props.hashState.organization,
      repoName: "",
      repoUrl: "",
      pullRequests: true,
      repoUsername: "",
      repoPassword: "",
      projectKey: '',
      projectName: '',
      auth: JSON.stringify(authT)
    };
    
      //link to existing project...
      createData.projectKey = this.props.hashState?.projectKey;
      createData.projectName = this.props.hashState?.projectName;

      updateIntegration(createData).then(()=>{
        this.props.onModified(createData);
      }).catch(e => {
        return parseError(e).then((message: any) => {});
      });

  };

  render() {
    return (
      <div />
    );
  }

}
