import * as React from 'react';
import { Location, Router, withRouter } from '../../../components/hoc/withRouter';
import { BranchLike } from '../../../../js/types/branch-like';
import { Actions, searchQualityProfiles } from '../../../api/quality-profiles';
import { Profile } from '../../quality-profiles/types';
import ProfilesList from '../../quality-profiles/home/ProfilesList';
import Evolution from '../../quality-profiles/home/Evolution';
import { sortProfiles } from '../../quality-profiles/utils';


interface Props {
    location: Location;
    router: Pick<Router,'replace'>;
    component: T.Component;
    branchLike: BranchLike,
    updateProfiles: () => Promise<void>;
}

interface State {
    loading: boolean;
    languages: Array<{ key: string; name: string }>;
    profiles:Profile[],
    actions: Actions;
}

export class GRCProfiles extends React.PureComponent<Props, State> {

    mounted: boolean = false;
    state: State;

    constructor(props: Props) {
      super(props);
        this.state = {
            loading: false,
            languages:[
                { key:"sfmeta", name: "Salesforce Metadata" }
            ],
            profiles:[],
            actions:{
                create:false
            }
        }
    }

    componentDidMount() {
        this.mounted = true;
        const { location, router } = this.props;

        if (location.query.id) {
          this.fetchProfiles();
        } else {
          router.replace('/grc');
        }
    }

    componentDidUpdate(prevProps: Props) {
        // if (prevProps.location.query.id !== this.props.location.query.id) {
        //     this.fetchProfiles();
        // }
    }

    componentWillUnmount() {
        this.mounted = false;
    }

    fetchProfiles(){
        this.setState({
            loading: true
        })
        const {organization} = this.props.component;
        return searchQualityProfiles({ organization }).then((data)=>{
            if(!this.mounted){
                return
            }
            this.setState({
                loading: false,
                profiles: sortProfiles(data.profiles)
            })
        })
    }

    render(){
        const {loading, languages, profiles} = this.state
        const {organization,key} = this.props.component;
        const {location, updateProfiles} = this.props;
        return (<div className="page page-limited">{ 
            loading ? (
                <>
                    <i className="spinner" />
                </> ) : (<>
                            <header className="page-header">
                                <h1 className="page-title">Profiles</h1>
                                <div className="page-description markdown">A Profile is a collection of policies and their rules that you want enforced. <br/> Deviations from policy rules are flagged during an analysis.</div>
                            </header>
                            <div className="page-with-sidebar">
                                <div className="page-main">
                                    <ProfilesList grc={true} updateProfiles={updateProfiles} languages={languages} location={location} organization={organization} profiles={profiles} />
                                    </div>
                                    <div className="page-sidebar">
                                    <Evolution componentKey={key} grc={true} updateProfiles={updateProfiles} languages={languages} location={location} organization={organization} profiles={profiles} />
                                    </div>
                            </div>
                        </>
            )
        }</div>);
    }
}
export default withRouter(GRCProfiles);
