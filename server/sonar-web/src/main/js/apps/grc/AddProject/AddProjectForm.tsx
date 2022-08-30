/* eslint-disable jsx-a11y/no-onchange */
import React from 'react';
import Modal from 'sonar-ui-common/components/controls/Modal';
import Salesforce from './Salesforce';

const imgOnlyStyles = {
    maxHeight: '60px',
    paddingRight: '50px',
};

const btnStyles = {
  display: 'flex',
  marginBottom: '32px',
  justifyContent: 'flex-end'
};

interface Props {
    organization: T.Organization;
    projectKey: any;
    closeForm: () => any;
}

interface State {
    open: boolean;
    disabled: boolean;
    errorMsg: string;
    showSalesforce: boolean;
    salesforceEnvironment: any;
    salesforceDomain: any;
}

export default class AddProjectForm extends React.PureComponent<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state =  {
        open: true,
        disabled: false,
        errorMsg: "",
        showSalesforce: true,
        salesforceEnvironment: "https://login.salesforce.com",
        salesforceDomain: ""
    };
  }

  closeForm = (e: any) => {
    e.preventDefault();
    this.setState({ open: false });
    this.props.closeForm();
  };

  handleSalesforceEnvironment = (event: any) => {
    this.setState({salesforceEnvironment: event.target.value, errorMsg: ""});
  }

  handleSalesforceDomain = (event: any) => {
    this.setState({salesforceDomain: event.target.value, errorMsg: ""});
    if ( event.target.value === "https://login.salesforce.com" || event.target.value === "https://test.salesforce.com" ){
      this.setState({salesforceEnvironment: event.target.value});
    }
  }

  salesforce = () => {
    let salesforceDomain = this.state.salesforceEnvironment;
    if (!salesforceDomain ){
      salesforceDomain = this.state.salesforceDomain;
    }

    if ( !salesforceDomain ){
      this.setState({errorMsg: "No domain specified"});
      return;
    }

    const regex = /(http|https):\/\/(\w+:{0,1}\w*)?(\S+)(:[0-9]+)?(\/|\/([\w#!:.?+=&%!\-\/]))?/;
    if(!regex.test(salesforceDomain)) {
      this.setState({errorMsg: "URL Not Valid"});
      return;
    }

    if(this.state.disabled ) { return; }
    const { organization, projectKey } = this.props;
    this.setState({ disabled: true });
    new Salesforce(this.props).authorize(organization.key, projectKey, salesforceDomain);
  };

  renderModal() {
    const { errorMsg } = this.state;
    return (
      <Modal
        contentLabel="modal form"
        className="modal"
        overlayClassName="modal-overlay">
        <header className="modal-head">
          <h2>
            <span>Scan your Org for Policy Violations</span>
          </h2>
        </header>

        <div className="modal-body text-align-center">
          <div className="text-danger">{ errorMsg }</div>
            <div className="modal-large-field">
              <label>Click continue to start a new analysis. You will be redirected to an authentication page to authorize us to access your source code.</label>
              <div>&nbsp;</div>
            </div>

          { this.state.showSalesforce && (
            <div>
              <table className="data" cellPadding="10">
              <thead>
                <tr>
                  <th className="thin nowrap" style={{ borderBottom: 'none' }} />
                  <th className="nowrap" style={{ borderBottom: 'none' }}>
                    Environment
                  </th>
                </tr>
                </thead>
              <tbody>
                <tr><td className="thin nowrap text-right">
                  <img src="/static/developer/salesforce.png" alt="" style={imgOnlyStyles} />
                </td>
                <td className="nowrap text-left">
                  <div>
                    <select
                      value={this.state.salesforceEnvironment}
                      onChange={this.handleSalesforceEnvironment}
                      >
                      <option value="">My own domain</option>
                      <option value="https://login.salesforce.com">Production/Developer</option>
                      <option value="https://test.salesforce.com">Sandbox</option>
                    </select>
                  </div>
                  {this.state.salesforceEnvironment === '' && (
                    <div>
                      <br/>
                      <input required={true} type="text" placeholder="https://my.salesforce.com" value={this.state.salesforceDomain} onChange={this.handleSalesforceDomain} />
                    </div>
                  )}
                  <br/>
                  <button className="button" disabled={this.state.disabled} type="submit" onClick={this.salesforce}>
                  Authorize
                  </button>
                </td></tr>
              </tbody>
              </table>

            </div>
          )}
        </div>
        <footer className="modal-foot add-proj-foot">
          <div style={btnStyles}>
            <button className="button button-link" type="button" onClick={this.closeForm}>
            Cancel
            </button>
          </div>
        </footer>
      </Modal>
    );
  }

  render () {
    return (
      <div>
        {this.state.open && this.renderModal()}
      </div>
    );
  }
}
