/* eslint-disable object-shorthand */
/* eslint-disable jsx-a11y/no-onchange */
import React from 'react';
import Modal from 'sonar-ui-common/components/controls/Modal';
import { authorizeToken, updateIntegration } from '../../../api/codescan';
import Salesforce, {parseError, parseErrorObject} from './Salesforce';


const imgOnlyStyles = {
  height: '50px'
};

const noteStyles = {
  padding: '0 0.5rem'
};

const btnStyles = {
  padding: '1rem 0'
};

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
    const { organization, hashState } = this.props;

    if ( typeof(this.props.hashState.error) != 'undefined' ){
      /* if ( typeof(this.props.hashState.error_description) == 'string' ){
        this.setState({
          errorMsg: decodeURI(this.props.hashState.error_description).replace(/\+/g, " ")
        });
      }else if ( typeof(this.props.hashState.error) == 'string' ) {
        this.setState({
          errorMsg: decodeURI(this.props.hashState.error).replace("+", " ")
        });
      } */
      return;
    } 

    const authHandler = new Salesforce(this.props);
    //this.setState(authHandler.getDefaultState())
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
  }

  handleChange = (event: any) => {
    let {value} = event.target;
    if ( event.target.type === 'checkbox' ){
      value = event.target.checked;
    }
    const d: any = {};
    d[event.target.dataset.field] = value;
    this.setState(d);
  }

  closeForm = () => {
    this.props.closeForm();
  };

  handleSubmit = (e: any) => {
    e.preventDefault();
    const { organization } = this.props;
    this.setState({
      disabled: true
      //errorMsg: ''
    });

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
        //this.closeForm();
      }).catch(e => {
        return parseError(e).then((message: any) => {
          //this.setState({ errorMsg: message })
        });
      });

  };

  render() {
   // const authHandler = new Salesforce(this.props);
    return (
      <Modal
        contentLabel="modal form"
        className="modal"
        overlayClassName="modal-overlay">
        <header className="modal-head">
          <h2>
            {this.state?.loading && <i className="spinner" />}
            <img src="/static/developer/salesforce.png" style={imgOnlyStyles} alt="" />
          </h2>
        </header>
        <form onSubmit={this.handleSubmit}>
          <div className="modal-body">
            {(
              <div>
                <div className="modal-field">
                  <label htmlFor="project-projectVersion">Default Project Version</label>
                  <input
                      required={true}
                      type="text"
                      name="project-projectVersion"
                      maxLength={20}
                      data-field="projectVersion"
                      value={this.state?.projectVersion}
                      onChange={this.handleChange}
                      />
                  <div className="modal-field-description">The default project version to run analysis with </div>
                </div>

                <div className="modal-field">
                  <label htmlFor="project-testmode">Unit Test Mode</label>
                  <select
                      name="project-testmode"
                      data-field="testmode"
                      value={this.state?.testmode}
                      onChange={this.handleChange}
                      >
                      <option value="async">Run Unit Tests (this can take a long time)</option>
                      <option value="history">Use previous run (if another tool has already run the unit tests)</option>
                      <option value="disabled">Disabled</option>
                  </select>
                  <div className="modal-field-description">Unit Testing mode allows you to configure which unit tests are run by the analysis</div>
                </div>
              </div>
            )}

            {(
            <div className="modal-field">
              <label htmlFor="project-scheduling">Scheduling</label>
              <select
                  name="project-scheduling"
                  data-field="scheduling"
                  value={this.state?.scheduling}
                  onChange={this.handleChange}
              >
                  <option value="">Manual</option>
              </select>

              <div className="modal-field-description">Run analysis on a regular basis</div>
            </div>
            )}
           {/*  <div className="text-danger">{ errorMsg }</div> */}
          </div>

          <footer className="modal-foot">
            <div className="modal-field-description" style={noteStyles}>
              NOTE: by continuing you agree that we will store your {this.state?.auth && this.state?.auth?.authType}
              credentials on our system
            </div>
            <div style={btnStyles}>
              <button className="button" type="submit" disabled={this.state?.disabled || !this.state?.valid}>
              Add and Run Now
              </button>
              <button type="reset" className="button button-link" onClick={this.closeForm}>
              Cancel
              </button>
            </div>
          </footer>
        </form>
      </Modal>
    );
  }

}
