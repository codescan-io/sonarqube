import * as React from 'react';

import { withOrganizationContext } from "../../organizations/OrganizationContext";
import withCurrentUserContext from "../../../app/components/current-user/withCurrentUserContext";
import { LoggedInUser } from '../../../../js/types/users';
import { Organization } from '../../../../js/types/types';


interface Props {
    currentUser: LoggedInUser;
    organization: Organization;
}

class PolicyResults extends React.PureComponent<Props> {

    render(){
        return (
            <div>Hello World1</div>
        )
    }
}
  
  export default withCurrentUserContext(withOrganizationContext(PolicyResults));

  