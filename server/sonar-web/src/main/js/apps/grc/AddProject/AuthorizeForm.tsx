import React from 'react';
//import { parseError, parseErrorObject } from '../../common/utils.js'
/* import {
  authorizeToken,
  createProject,
  renameProjectBranch,
  updateIntegration
} from '../../../api.js' */
//import Salesforce from './Salesforce';
//import AbstractType from '../../common/AbstractType.js';
//import RepoSearch from '../../common/RepoSearch.js';

const imgOnlyStyles = {
  maxHeight: '1em',
  paddingLeft: '1em',
};

interface Props {
    organization: T.Organization;
    hashState: any;
    //closeForm: () => any;
    onModified: () => any;
    originalProject: any;
}

export default class AuthorizeForm extends React.PureComponent<Props> {

  constructor(props: Props) {
    super(props);
    this.state =  {
      //open: true,
      //disabled: false,
      //valid: false
    };
  }

  render() {
    return (
        <div>Hello welcome back to GRC to run analysis</div>
    )
  }

  /* handleChange = (event) => {
    let value = event.target.value;
    if ( event.target.type == 'checkbox' ){
      value = event.target.checked;
    }

    let d = {};
    d[event.target.dataset.field] = value;
    this.setState(d);
  }

  closeForm = () => {
    this.setState({ open: false });
  };

  componentDidMount() {
    this.setState({
      open: true,
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
      repoBranch: "",
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

    let authHandler = AbstractType.create(hashState.authType);
    this.setState(authHandler.getDefaultState())
    if ( authHandler.requiresAuthorizeToken() ){
      authorizeToken({
        code: hashState.code,
        authType: hashState.authType,
        host: hashState.host,
        organization: organization.key,
      }).then((response)=>{
        window.location.hash = ''; //remove hash
        let errorMsg = parseErrorObject(response);
        if ( errorMsg != null){
          this.setState({
            errorMsg: errorMsg
          });
        }else{
          this.setState({
            projectKey: response.projectKey,
            projectName: response.projectName,
            auth: response.auth,
            authHandler: authHandler,
            branchName: hashState.branchName,
            branchType: hashState.branchType
          });
          authHandler.onAuthorizeToken(this);
        }

      }).catch(e => {
        return parseError(e).then(message => this.setState({
          errorMsg: message
        }));
      });
    }else{
      window.location.hash = ''; //remove hash
      this.setState({
        auth: {
          authType: hashState.authType
        },
        authHandler: authHandler
      });
      authHandler.onAuthorizeToken(this);
    }
  }

  handleSubmit = (e) => {
    e.preventDefault();
    const { organization } = this.props;
    this.setState({
      disabled: true,
      errorMsg: ''
    });

    //calculate scheduling
    let scheduling = this.state.scheduling;
    if ( scheduling == "-1" ){
      scheduling = this.state.scheduling_hour;
    }

    let createData = {
      organizationId: organization.key,
      testmode: this.state.testmode,
      scheduling: scheduling,
      projectVersion: this.state.projectVersion,
      repoName: this.state.repoName,
      pullRequests: this.state.pullRequests,
      repoUrl: this.state.repoUrl,
      repoUsername: this.state.repoUsername,
      repoPassword: this.state.repoPassword,

      auth: JSON.stringify(this.state.auth)
    };

    if ( this.props.originalProject == null ){
      //create a new project first.
      let projectData = {
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

    }else{
      //link to existing project...
      createData.projectKey = this.props.originalProject.key;
      createData.projectName = this.props.originalProject.name;

      updateIntegration(createData).then((response2)=>{
        this.props.onModified(createData);
        this.closeForm();
      }).catch(e => {
        return parseError(e).then(message => this.setState({ errorMsg: message }));
      });
    }

  };

  onRepoUrlBlur = (event) => {
    let url = event.target.value;

    if ( url.includes("//") ){
      url = url.substring(url.indexOf("//")+2);

      if ( url.includes("/") ){
        url = url.substring(url.indexOf("/")+1);
        if ( url.endsWith(".git") )
          url = url.substring(0, url.lastIndexOf(".git") ); //get rid of ending .git

        let projectName = url;
        let projectKey = url;
        if ( url.includes("/") ){
          projectName = url.substring(url.lastIndexOf("/")+1);
        }

        //set key if applicable
        if (this.state.projectKey == '' || typeof this.state.projectKey == 'undefined') {
          projectKey = projectKey.replace(/[^a-zA-Z0-9 ]/g, "-"); //replace - with dash
          projectKey = projectKey.trim();
          projectKey = projectKey.replace(/--+/g, '-'); //remove multiple spaces
          this.setState({
              projectKey: projectKey
          });
        }

        //set name if applicable
        if (this.state.projectName == '' || typeof this.state.projectName == 'undefined') {
          projectName = projectName.replace(/[^a-zA-Z0-9 ]/g, " "); //replace - with dash
          projectName = projectName.trim();
          projectName = projectName.replace(/\s\s+/g, ' '); //remove multiple spaces
          this.setState({
              projectName: projectName
          });
        }

      }
    }else{
      this.setState({
        repoUrl: ''
      })
    }
  }
  onRepoNameChange = (data) => {
    if ( typeof data == 'undefined' ){
      this.setState({
        valid: false
      });
    }else{
      this.setState({
        repoName: data.key,
        valid: true
      });

      //set key/name if applicable
      if (this.state.projectKey == '' || typeof this.state.projectKey == 'undefined') {
        this.setState({
            projectKey: data.key.replace(/\//g, '-')
        });
      }
      if (this.state.projectName == '' || typeof this.state.projectName == 'undefined') {
          this.setState({
              projectName: data.name.replace(/\//g, '-')
          });
      }
    }
  } */

  /* render() {
    const canAddBranch = this.props.originalProject == null && this.props.organization.actions.admin
    const { errorMsg, loading, open } = this.state;
    const authHandler = typeof(this.state.auth) == 'undefined' ? undefined : AbstractType.create(this.state.auth.authType, this);
    return (
      <Modal
        isOpen={open}
        contentLabel="modal form"
        className="modal"
        overlayClassName="modal-overlay"
        onRequestClose={this.closeForm}>
        <header className="modal-head">
          <h2>
            {this.state.loading && <i className="spinner" />}
            { this.props.originalProject == null && (<span>Add a new project</span>) }
            { this.props.originalProject !== null && (<span>Attach analysis to project</span>) }
            { typeof(authHandler) !== 'undefined' && <img src={ authHandler.imageUrl() } style={imgOnlyStyles} /> }
          </h2>
        </header>
        <form onSubmit={this.handleSubmit}>
          <div className="modal-body">
            { this.state.auth && this.state.auth.authType == Git.TYPE && (
              <div>
                <div className="modal-field">
                  <label htmlFor="project-repoUrl">Git URL</label>
                  <input
                      required
                      type="text"
                      name="project-repoUrl"
                      maxLength="250"
                      data-field="repoUrl"
                      value={this.state.repoUrl}
                      onChange={this.handleChange}
                      onBlur={this.onRepoUrlBlur}
                      />
                  <div className="modal-field-description">The git URL. The URL must start with http(s)://, not git@</div>
                </div>

                <div className="modal-field">
                  <label htmlFor="project-repoUsername">Git Username</label>
                  <input
                      type="text"
                      name="project-repoUsername"
                      maxLength="250"
                      data-field="repoUsername"
                      value={this.state.repoUsername}
                      onChange={this.handleChange}
                      />
                  <div className="modal-field-description">The git repo username.</div>
                </div>

                <div className="modal-field">
                  <label htmlFor="project-repoPassword">Git Password</label>
                  <input
                      type="password"
                      name="project-repoPassword"
                      maxLength="250"
                      data-field="repoPassword"
                      value={this.state.repoPassword}
                      onChange={this.handleChange}
                      />
                  <div className="modal-field-description">The git repo password</div>
                </div>

                <div className="modal-field">
                  <label htmlFor="project-repoBranch">Project Branch</label>
                  <input
                      type="text"
                      name="project-repoBranch"
                      maxLength="120"
                      data-field="repoBranch"
                      value={this.state.repoBranch}
                      onChange={this.handleChange}
                      disabled={!canAddBranch}
                      />
                  <div className="modal-field-description">
                    {canAddBranch && ("The branch that the project is linked to")}
                    {!canAddBranch && ("Custom branch not enabled as you are not an admin user")}
                  </div>
                </div>
              </div>
            )}
            { this.state.auth && (this.state.auth.authType == Github.TYPE || this.state.auth.authType == Gitlab.TYPE
              || this.state.auth.authType == Bitbucket.TYPE) && (
              <div>
                <RepoSearch
                  name="repoName"
                  label="Choose a repository"
                  auth={this.state.auth}
                  onChange={this.onRepoNameChange}
                />

                { this.props.originalProject == null && (
                <div className="modal-field">
                  <label htmlFor="project-repoBranch">Project Branch</label>
                  <input
                      type="text"
                      name="project-repoBranch"
                      maxLength="120"
                      data-field="repoBranch"
                      value={this.state.repoBranch}
                      onChange={this.handleChange}
                      />
                  <div className="modal-field-description">The branch that the project is linked to</div>
                </div>
                )}

                <div className="modal-field">
                  <label htmlFor="project-pullRequests">Check Pull Requests</label>
                  <input
                      type="checkbox"
                      name="project-pullRequests"
                      data-field="pullRequests"
                      checked={this.state.pullRequests}
                      value="true"
                      onChange={this.handleChange}
                      />
                  <div className="modal-field-description">Automatically start scans for new pull requests</div>
                </div>
              </div>
            )}
            { this.props.originalProject == null && (<div>
              <div className="modal-field">
                <label htmlFor="project-projectKey">Key</label>
                <input
                    required
                    type="text"
                    name="project-projectKey"
                    maxLength="20"
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
                    required
                    type="text"
                    name="project-projectName"
                    maxLength="20"
                    data-field="projectName"
                    value={this.state.projectName}
                    onChange={this.handleChange}
                    disabled={loading}
                    />
                <div className="modal-field-description">The project name.</div>
              </div>
            </div>)}

            { this.state.auth && this.state.auth.authType == Salesforce.TYPE && (
              <div>
                <div className="modal-field">
                  <label htmlFor="project-projectVersion">Default Project Version</label>
                  <input
                      required
                      type="text"
                      name="project-projectVersion"
                      maxLength="20"
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

            { this.state.auth && (this.state.auth.authType ==  Salesforce.TYPE || this.state.auth.authType == Git.TYPE ) && (
            <div className="modal-field">
              <label htmlFor="project-scheduling">Scheduling</label>
              <select
                  name="project-scheduling"
                  data-field="scheduling"
                  value={this.state.scheduling}
                  onChange={this.handleChange}
              >
                  <option value="">Manual</option>
                  <option value="-1">Daily</option>
              </select>

              <div className="modal-field-description">Run analysis on a regular basis</div>
            </div>
            )}


            { this.state.auth && this.state.scheduling == "-1" && (
              <div className="modal-field">
                <label htmlFor="project-scheduling-hour">Schedule</label>
                <select
                    name="project-scheduling-hour"
                    data-field="scheduling_hour"
                    value={this.state.scheduling_hour}
                    onChange={this.handleChange}
                >
                  <option value="-1">Any time</option>
                  { [...Array(24).keys()].map( (hour) => {
                    return (<option key={hour+1} value={hour+1}>
                      After {new Date("1970-01-01T" + (hour < 10 ? "0" : "") + hour + ":00+0000").toTimeString().replace(/:00:00.*\(/, ":00 (")}
                    </option>)
                   } ) }
                </select>
              </div>
              )}
            <div className="text-danger">{ errorMsg }</div>
          </div>

          <footer className="modal-foot">
            <div className="modal-field-description">
              NOTE: by continuing you agree that we will store your {this.state.auth && this.state.auth.authType}
              credentials on our system
            </div>
            { this.state.auth && (this.state.auth.authType == Github.TYPE || this.state.auth.authType == Gitlab.TYPE
              || this.state.auth.authType == Bitbucket.TYPE ) && (
              <div className="modal-field-description">Analysis will be automatically started whenever there is a commit to the tracked branch</div>
            )}
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
  } */

}
