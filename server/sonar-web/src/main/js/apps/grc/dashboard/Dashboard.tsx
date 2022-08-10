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
import GrcViolations from '../components/GrcViolations';
import { getStandards } from '../../../../js/helpers/security-standard';
import ViolationDetails from '../components/ViloationDetails';
import HotSpotSeries from '../components/HotSpotSeries';

{/* <div className="row">
  <div className="col col-5"><SecurityHotSpots></SecurityHotSpots></div>
  <div className="col col-7  no-padding">
    
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
  loadingChartData:boolean
  totalProfilesDefined:number;
  totalProfilesEnforced:number;
  hotspots:RawHotspot[],
  totalHotspots:number,
  securityCategories:any
}

export class GRCDashboard extends React.PureComponent<Props, State> {
         mounted: boolean = false;
         state: State;

         constructor(props: Props) {
           super(props);
           this.state = {
             loadingAnalysis: false,
             lastAnalysisData: undefined,
             loadingChartData: false,
             totalProfilesDefined:0,
             totalProfilesEnforced:0,
             hotspots:[],
             totalHotspots:0,
             securityCategories:{}
            };
         }

         componentDidMount() {
           this.mounted = true;
           const { location, router } = this.props;

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
            this.setState({ loadingChartData: true });
          }
          return Promise.all([getStandards(),this.fetchRulesConfigured(),this.fetchRulesEnforced(),this.fetchHotspots()]).then(([standardsResp,
            rConfiguredResp,
            rEnforcedResp,
            hotspotsResp])=>{
            const securityCategories = standardsResp.sonarsourceSecurity;
            const totalRulesConfigured = rConfiguredResp?.total;
            const totalRulesEncorced = rEnforcedResp?.profiles?.length;
            const hotspots = hotspotsResp.hotspots;
            const totalHotspots = hotspotsResp.paging.total;
            console.log("standardsResp");
            console.log(standardsResp);
            if (this.mounted) {
              this.setState({ securityCategories })
              this.setState({ totalProfilesDefined: totalRulesConfigured });
              this.setState({ totalProfilesEnforced: totalRulesEncorced });
              this.setState({ hotspots });
              this.setState({ totalHotspots });
              this.setState({ loadingChartData: false });
            }
          }).catch((err)=>{
            console.log("Error Load Chart Data");
            console.log(err);
          })
         }

         render() {
           const { securityCategories, loadingChartData, loadingAnalysis, lastAnalysisData,totalProfilesDefined, totalProfilesEnforced,hotspots,totalHotspots } = this.state;
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
                    <>{
                      loadingChartData ? (
                        <div className="page page-limited">
                            <i className="spinner" />
                        </div>
                      ):(
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
                       <div className="row">
                          <div className="col col-5">
                            <GrcViolations hotspots={hotspots} 
                                           totalHotspots={totalHotspots}
                                          securityCategories={securityCategories}></GrcViolations>
                          </div>
                          <div className="col col-7  no-padding">
                            <ViolationDetails></ViolationDetails>
                            <hr className="seperator"></hr>
                            <HotSpotSeries></HotSpotSeries>
                          </div>
                       </div>
                     </div>
                      )
                    }
                    </>
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
