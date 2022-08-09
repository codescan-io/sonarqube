/*
 * SonarQube
 * Copyright (C) 2009-2020 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import * as React from 'react';
import { searchQualityProfiles } from '../../../api/quality-profiles';
import { searchRules } from '../../../api/rules';
import { getSecurityHotspots } from '../../../api/security-hotspots';
import { getProjectActivity } from '../../../api/projectActivity';
import { Location, Router, withRouter } from '../../../components/hoc/withRouter';
import IssuesByPriority from "../components/IssuesByPriority";
import LastAnalysis from "../components/LastAnalysis";
import PolicyCount from "../components/PolicyCount";
import RiskIndicator from "../components/RiskIndicator";
import '../grc-dashboard.css';
import { RawHotspot } from '../../../types/security-hotspots';

{/* <div className="row">
  <div className="col col-5"><SecurityHotSpots></SecurityHotSpots></div>
  <div className="col col-7  no-padding">
    <ViolationDetails></ViolationDetails>
    <hr className="seperator"></hr>
    <HotSpotSeries></HotSpotSeries>
  </div>
</div> */}

interface Props {
  location: Pick<Location,'query'>;
  router: Pick<Router,'replace'>;
  component: T.Component;
}


interface State {
  loadingAnalysis: boolean;
  lastAnalysisData: any;
  lodingChartData:boolean
  totalProfilesDefined:number;
  totalProfilesEnforced:number;
  hotspots:RawHotspot[]
}

export class GRCDashboard extends React.PureComponent<Props, State> {
         mounted: boolean = false;
         state: State;

         constructor(props: Props) {
           super(props);
           this.state = {
             loadingAnalysis: false,
             lastAnalysisData: undefined,
             lodingChartData: false,
             totalProfilesDefined:0,
             totalProfilesEnforced:0,
             hotspots:[]
            };
         }

         componentDidMount() {
           this.mounted = true;
           const { location, router } = this.props;
           console.log('componentDidMount :: ' + this.props);

           if (location.query.id) {
             this.loadAnalyses();
           } else {
             router.replace('/grc');
           }
         }

         componentDidUpdate(prevProps: Props) {
           if (prevProps.location.query.id !== this.props.location.query.id) {
             this.loadAnalyses();
           }
         }

         componentWillUnmount() {
           this.mounted = false;
         }

         loadAnalyses = () => {
           if (this.mounted) {
             this.setState({ loadingAnalysis: true });
           }
           return getProjectActivity({
             project: this.props.component.key
           }).then(({ analyses }) => {
             if (this.mounted) {
               this.setState({ loadingAnalysis: false });
               this.setState({ lastAnalysisData: analyses[0] });
               this.loadChartData();
             }
           });
         };

         fetchRulesConfigured=()=>{
          const data = {
            organization: undefined,
            languages:"sfmeta",
            repositories:"grc"
          };
          return searchRules(data);
         }

         //{{APP_URL}}/api/qualityprofiles/search?defaults=true&languages=sfdeta&organization=<<org_id>>
         fetchRulesEnforced=()=>{
          const data = {
            defaults: true,
            languages:"sfmeta",
            organization:this.props.component.organization
          };
          return searchQualityProfiles(data);
         }

         fetchHotspots=()=>{
          const data = {
            projectKey: this.props.component.key,
            p: 1,
            ps: 500,
          }
          return getSecurityHotspots(data);
         }

         loadChartData = () =>{
          if (this.mounted) {
            this.setState({ lodingChartData: true });
          }
          return Promise.all([this.fetchRulesConfigured(),this.fetchRulesEnforced(),this.fetchHotspots()]).then(([rConfiguredResp,
            rEnforcedResp,
            hotspotsResp])=>{
            const totalRulesConfigured = rConfiguredResp?.total;
            const totalRulesEncorced = rEnforcedResp?.profiles?.length;
            const hotspots = hotspotsResp.hotspots;
            if (this.mounted) {
              this.setState({ lodingChartData: false });
              this.setState({ totalProfilesDefined: totalRulesConfigured });
              this.setState({ totalProfilesEnforced: totalRulesEncorced });
              this.setState({ hotspots: hotspots });
            }
          }).catch((err)=>{
            console.log("Error Load Chart Data");
            console.log(err);
          })
         }

         render() {
          
           const { loadingAnalysis, lastAnalysisData,totalProfilesDefined, totalProfilesEnforced,hotspots } = this.state;
           return (
             <>
               {' '}
               {loadingAnalysis ? (
                 <div className="page page-limited">
                   <i className="spinner" />
                 </div>
               ) : (
                 <>
                   {lastAnalysisData ? (
                     <div className="dashboard-page">
                       <div className="row">
                         <div className="col col-3 no-padding">
                           <PolicyCount totalProfilesDefined={totalProfilesDefined} totalProfilesEnforced={totalProfilesEnforced}></PolicyCount>
                         </div>
                         <div className="col col-3">
                           <RiskIndicator totalProfilesDefined={totalProfilesDefined} totalProfilesEnforced={totalProfilesEnforced}></RiskIndicator>
                         </div>
                         <div className="col col-3">
                           <IssuesByPriority hotspots={hotspots}></IssuesByPriority>
                         </div>
                         <div className="col col-3">
                           <LastAnalysis event={lastAnalysisData}></LastAnalysis>
                         </div>
                       </div>
                     </div>
                   ) : (
                     <div className="dashboard-page">
                       <div className="row">
                         <div className="col col-12">
                           Project Analysis is not, yet run. Please run the analysis.
                         </div>
                       </div>
                     </div>
                   )}
                 </>
               )}
             </>
           );
         }
       }

export default withRouter(GRCDashboard);
