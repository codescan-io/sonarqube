/* eslint-disable jsx-a11y/no-onchange */
import React from 'react';
import Modal from 'sonar-ui-common/components/controls/Modal';
import { authorizeToken, updateIntegration } from '../../../api/codescan';
import Salesforce, {parseError, parseErrorObject} from './Salesforce';


const imgOnlyStyles = {
  maxHeight: '1em',
  paddingLeft: '1em',
};

interface Props {
    organization: T.Organization;
    hashState: any;
    //closeForm: () => any;
    onModified: (data: any) => any;
    originalProject: any;
}

interface State {
  disabled: boolean;
  valid: boolean;
  scheduling: any,
  scheduling_hour: any;
  loading: boolean;
  errorMsg: any;
  projectVersion: string;
  testmode: string;
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
      disabled: true,
      valid: false,
      errorMsg: "",
      projectVersion: "",
      testmode: "",
      scheduling: "0",
      scheduling_hour: "-1",
      repoName: "",
      repoUrl: "",
      repoUsername: "",
      repoPassword: "",
      //repoBranch: "",
      pullRequests: true
    });

    const { organization, hashState } = this.props;

    if ( typeof(this.props.hashState.error) != 'undefined' ){
      if ( typeof(this.props.hashState.error_description) == 'string' ){
        this.setState({
          errorMsg: decodeURI(this.props.hashState.error_description).replace(/\+/g, " ")
        });
      }else if ( typeof(this.props.hashState.error) == 'string' ){
        this.setState({
          errorMsg: decodeURI(this.props.hashState.error).replace("+", " ")
        });
      }
      return;
    }

    const authHandler = new Salesforce(this.props);
    this.setState(authHandler.getDefaultState())
    if (authHandler.requiresAuthorizeToken()){
      authorizeToken({
        code: hashState.code,
        authType: hashState.authType,
        host: hashState.host,
        organization: organization.key,
      }).then((response)=>{
        window.location.hash = ''; //remove hash
        const errorMsg = parseErrorObject(response);
        if ( errorMsg != null) {
          this.setState({errorMsg});
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
            this.setState({errorMsg: message});
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
    this.setState({ open: false });
  };

  handleSubmit = (e: any) => {
    e.preventDefault();
    const { organization } = this.props;
    this.setState({
      disabled: true,
      errorMsg: ''
    });

    //calculate scheduling
    let {scheduling} = this.state;
    if ( scheduling === "-1" ){
      scheduling = this.state.scheduling_hour;
    }

    const createData: any = {
      organizationId: organization.key,
      testmode: this.state.testmode,
      scheduling,
      projectVersion: this.state.projectVersion,
      repoName: this.state.repoName,
      pullRequests: this.state.pullRequests,
      repoUrl: this.state.repoUrl,
      repoUsername: this.state.repoUsername,
      repoPassword: this.state.repoPassword,
      projectKey: '',
      projectName: '',
      auth: JSON.stringify(this.state.auth)
    };

    /* if ( this.props.originalProject == null ){
      //create a new project first.
      const projectData = {
        name: this.state.projectName,
        organization: this.props.organization.key,
        project: this.state.projectKey,
        visibility: "private"
      };
      createData.projectKey = this.state.projectKey;
      createData.projectName = this.state.projectName;

      createProject(projectData).then((response)=>{
        let renamePromise;
        if ( this.state.repoBranch == "" ){
          renamePromise = Promise.resolve('');
        }else{
          renamePromise = renameProjectBranch({
            project: this.state.projectKey,
            name: this.state.repoBranch
          });
        }
        renamePromise.then((response3)=>{
          updateIntegration(createData).then((response2)=>{
            this.props.onModified(createData);
            this.closeForm();
          }).catch(e2 => {
            return parseError(e2).then(message => this.setState({ errorMsg: message }));
          });
        }).catch(e3 => {
          return parseError(e3).then(message => this.setState({ errorMsg: message }));
        });
      }).catch(e => {
        return parseError(e).then(message => this.setState({
          errorMsg: message,
          disabled: false //allow retry?
        }));
      });

    } else { */
      //link to existing project...
      createData.projectKey = this.props.originalProject.key;
      createData.projectName = this.props.originalProject.name;

      updateIntegration(createData).then(()=>{
        this.props.onModified(createData);
        this.closeForm();
      }).catch(e => {
        return parseError(e).then((message: any) => this.setState({ errorMsg: message }));
      });
    //}

  };

  render() {
    const { errorMsg, loading, open } = this.state;
    const authHandler = typeof(this.state.auth) == 'undefined' ? undefined : new Salesforce(this.props);
    return (
      <Modal
        contentLabel="modal form"
        className="modal"
        overlayClassName="modal-overlay">
        <header className="modal-head">
          <h2>
            {this.state.loading && <i className="spinner" />}
            { this.props.originalProject == null && (<span>Add a new project</span>) }
            { typeof(authHandler) !== 'undefined' && <img src={authHandler.imageUrl()} style={imgOnlyStyles} alt="" /> }
          </h2>
        </header>
        <form onSubmit={this.handleSubmit}>
          <div className="modal-body">
            { this.props.originalProject == null && (<div>
              <div className="modal-field">
                <label htmlFor="project-projectKey">Key</label>
                <input
                    required={true}
                    type="text"
                    name="project-projectKey"
                    maxLength={20}
                    data-field="projectKey"
                    value={this.state.projectKey}
                    onChange={this.handleChange}
                    disabled={loading}
                    />
                <div className="modal-field-description">The project key.</div>
              </div>

              <div className="modal-field">
                <label htmlFor="project-projectName">Name</label>
                <input
                    required={true}
                    type="text"
                    name="project-projectName"
                    maxLength={20}
                    data-field="projectName"
                    value={this.state.projectName}
                    onChange={this.handleChange}
                    disabled={loading}
                    />
                <div className="modal-field-description">The project name.</div>
              </div>
            </div>)}

            { this.state.auth && this.state.auth.authType === 'metadata_api' && (
              <div>
                <div className="modal-field">
                  <label htmlFor="project-projectVersion">Default Project Version</label>
                  <input
                      required={true}
                      type="text"
                      name="project-projectVersion"
                      maxLength={20}
                      data-field="projectVersion"
                      value={this.state.projectVersion}
                      onChange={this.handleChange}
                      />
                  <div className="modal-field-description">The default project version to run analysis with </div>
                </div>

                <div className="modal-field">
                  <label htmlFor="project-testmode">Unit Test Mode</label>
                  <select
                      name="project-testmode"
                      data-field="testmode"
                      value={this.state.testmode}
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

            { this.state.auth && (this.state.auth.authType ===  'metadata_api') && (
            <div className="modal-field">
              <label htmlFor="project-scheduling">Scheduling</label>
              <select
                  name="project-scheduling"
                  data-field="scheduling"
                  value={this.state.scheduling}
                  onChange={this.handleChange}
              >
                  <option value="">Manual</option>
              </select>

              <div className="modal-field-description">Run analysis on a regular basis</div>
            </div>
            )}
            <div className="text-danger">{ errorMsg }</div>
          </div>

          <footer className="modal-foot">
            <div className="modal-field-description">
              NOTE: by continuing you agree that we will store your {this.state.auth && this.state.auth.authType}
              credentials on our system
            </div>
            <div>
              <button className="button" type="submit" disabled={this.state.disabled || !this.state.valid}>
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
