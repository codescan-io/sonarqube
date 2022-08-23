import React from 'react';
import { updateIntegration } from '../../../api/codescan';

export default class Salesforce extends React.PureComponent {
    TYPE = 'metadata_api';
    salesforceImageUrl = "/static/developer/salesforce.png";

    requiresAuthorizeToken(){
        return true;
    }

    getDefaultState(){
        return {
            projectVersion: "1",
            testmode: "disabled",
            scheduling: "-1",
        }
    }

    attachBranch(branchName: string | number | boolean, branchType: string | number | boolean, organizationId: string | number | boolean, projectKey: string | number | boolean, HOST: string | number | boolean){
        let hostStr = "";
        if ( typeof(HOST) != 'undefined' ){
            hostStr = "&HOST=" + encodeURIComponent(HOST);
        }
        window.location.href = '/_codescan/integrations/authorize?organizationId='
            + encodeURIComponent(organizationId)
            + "&branchName=" + encodeURIComponent(branchName)
            + "&branchType=" + encodeURIComponent(branchType)
            + "&projectKey=" + (typeof(projectKey)=='undefined' ? '' : encodeURIComponent(projectKey))
            + hostStr;
    }
    
    authorize(organizationId: string | number | boolean, projectKey: string | number | boolean, HOST: string | number | boolean){
        let hostStr = "";
        if ( typeof(HOST) != 'undefined' ){
            hostStr = "&HOST=" + encodeURIComponent(HOST);
        }
        window.location.href = '/_codescan/integrations/authorize?organizationId='
            + encodeURIComponent(organizationId)
            + "&projectKey=" + (typeof(projectKey)=='undefined' ? '' : encodeURIComponent(projectKey))
            + hostStr;
    }

    onAuthorizeToken(form: any){
        if ( typeof(form.state.branchName) == 'undefined' || typeof(form.state.projectKey) == 'undefined' ){
            form.setState({
                valid: true,
                loading: false,
                disabled: false
            });
        }else{
            const { organization } = form.props;

            const createData = {
                organizationId: organization.key,
                testmode: form.state.testmode,
                scheduling: form.state.scheduling,
                projectVersion: form.state.projectVersion,
                repoName: form.state.repoName,
                pullRequests: form.state.pullRequests,
                repoUrl: form.state.repoUrl,
                repoUsername: form.state.repoUsername,
                repoPassword: form.state.repoPassword,
                projectKey: form.props.originalProject.key,
                projectName: form.props.originalProject.name,
                branchName: form.state.branchName,
                branchType: form.state.branchType,
                auth: JSON.stringify(form.state.auth)
            };

            //link to existing project...
            updateIntegration(createData).then(()=>{
                if ( createData.branchName ){
                    //pass branch through if available
                    createData.projectKey += ':BRANCH:' + createData.branchName;
                }
                form.props.onModified(createData);
                form.closeForm();
            }).catch(e => {
                return parseError(e).then((message: any) => form.setState({
                  errorMsg: message
                }));
            });
        }
    }

    imageUrl(){
        return this.salesforceImageUrl;
    }
}


export function parseError(error: any) {
    const DEFAULT_MESSAGE = 'An unknown error occurred';
    try {
      if ( typeof(error) == 'undefined' ){
        return Promise.resolve("A network error occurred. Please check your internet connection and try again.");
      }else if ( error.status >= 502 && error.status <= 504 ){
        return Promise.resolve("A temporary server error occurred. Please try again soon.");
      }
      return error.json().then((r: any) => {
          let ret = parseErrorObject(r);
          if ( ret == null ) {
            ret = DEFAULT_MESSAGE;
          }
          return ret;
        })
        .catch(() => DEFAULT_MESSAGE);
    } catch (ex) {
      return Promise.resolve(DEFAULT_MESSAGE);
    }
  }

export function parseErrorObject(r: any){
    if ( typeof(r.error_description) == 'string' ){
      return r.error_description 
    }else if ( typeof(r.error) == 'string' ){
      return r.error;
    }else if ( typeof(r.errors) == 'object' ){
      return r.errors.map((error: any) => error.msg).join('. ');
    }else{
      return null;
    }
  }