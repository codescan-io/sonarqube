import * as React from 'react';
import { searchQualityProfiles } from '../../../api/quality-profiles';
import { Router, withRouter, Location } from '../../../components/hoc/withRouter';
import ProfileNotFound from '../../quality-profiles/components/ProfileNotFound';
import ProfileHeader from '../../quality-profiles/details/ProfileHeader';
import ProfileInheritance from '../../quality-profiles/details/ProfileInheritance';
import ProfileRules from '../../quality-profiles/details/ProfileRules';
import { Profile } from '../../quality-profiles/types';
import { sortProfiles } from '../../quality-profiles/utils';
import '../../quality-profiles/styles.css';


interface Props {
    location: Location;
    router: Pick<Router,'replace'>;
    component: T.Component;
    updateProfiles: () => Promise<void>;
}

interface State {
    profiles: Profile[];
    loading: boolean;
    languages: Array<{ key: string; name: string }>;
}

export class GRCProfileDetails extends React.PureComponent<Props, State> {

    mounted: boolean = false;
    state: State;

    constructor(props: Props) {
      super(props);
        this.state = {
            loading: false,
            languages:[
                { key:"sfmeta", name: "Salesforce Metadata" }
            ],
            profiles:[]
        }
    }

    componentDidMount() {
        this.mounted = true;
        const { location, router } = this.props;
        const { id, name, language } = location.query;

        if (id && name && language) {
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
        
        return searchQualityProfiles({ organization }).then((data: any)=>{
            if(!this.mounted){
                return
            }
            const profiles = sortProfiles(data.profiles);

            this.setState({
                loading: false,
                profiles: profiles
            })
        })
    }


    render(){
        const {loading, profiles} = this.state
        const { name, language } = this.props.location.query;
        const {key, organization} = this.props.component;
        
        const profile = profiles.find(
            profile => profile.language === language && profile.name === name
        );
        const grc = true;

        return (<div className="page page-limited">{ 

            loading ? (
                <>
                    <i className="spinner" />
                </> ) : (<>
                {
                    profile ? (
                        <div id="quality-profile">
                            <ProfileHeader
                                grc={grc}
                                organization={organization}
                                profile={profile}
                                updateProfiles={this.props.updateProfiles}
                                componentKey={key}
                            />
                            <div>
                                <div className="quality-profile-grid">
                                    <div className="quality-profile-grid-left">
                                        <ProfileRules
                                        componentKey={key}
                                         grc={grc} organization={organization} profile={profile} />
                                    </div>
                                    <div className="quality-profile-grid-right">
                                        <ProfileInheritance grc={grc}
                                            organization={organization}
                                            profile={profile}
                                            profiles={profiles}
                                            updateProfiles={this.props.updateProfiles}
                                            />
                                    </div>
                                </div>
                            </div>
                        </div>
                    ):(
                        <div className="error"> No Profiles found </div>
                    )
                }
            </>)
        }</div>);
    }
}
export default withRouter(GRCProfileDetails);
