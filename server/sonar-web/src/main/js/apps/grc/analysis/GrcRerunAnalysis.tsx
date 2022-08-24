import React from "react";
import NotFound from "../../../app/components/NotFound";
import Extension from "../../../app/components/extensions/Extension";
import { Location, Router, withRouter } from "../../../components/hoc/withRouter";
import { BranchLike } from "../../../types/branch-like";

interface Props {
    location: Location;
    component: T.Component;
    branchLike: BranchLike,
    router: Router
}

export class GRCRerunAnalysis extends React.PureComponent<Props> {
        mounted: boolean = false;

        constructor(props: Props) {
          super(props);
        }

        componentDidMount() {
            this.mounted = true;
        }

        componentDidUpdate(prevProps: Props) {
        }

        componentWillUnmount() {
            this.mounted = false;
        }

        render(){
            const {location, branchLike, component} = this.props;
            const extension =
            component.extensions &&
            component.extensions.find(p => p.key === "developer/project");

            return extension ? (
                <Extension extension={extension} options={{ branchLike, component }} />
            ) : (
                <NotFound withContainer={false} />
            );
        }
    }
    export default withRouter(GRCRerunAnalysis);