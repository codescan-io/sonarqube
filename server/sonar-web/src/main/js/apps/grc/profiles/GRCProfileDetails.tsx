import * as React from 'react';
import { withRouter } from '../../../components/hoc/withRouter';

interface Props {
}

interface State {
}

export class GRCProfileDetails extends React.PureComponent<Props, State> {


    render(){
        return (
                <>
                    GRC Profile Details Coming soon!!!
                </>);
    }
}
export default withRouter(GRCProfileDetails);
