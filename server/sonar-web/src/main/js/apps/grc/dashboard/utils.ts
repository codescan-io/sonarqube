
import { RawHotspot, RiskExposure } from "../../../types/security-hotspots";

export function getReadableDateFormat(date:string){
    let t:Date = new Date(date);
    return t.toLocaleString('en-US', { year:'numeric', month:'numeric', day:'numeric',hour: 'numeric', minute: 'numeric', hour12: true });
}

export function getQualityGateInfo(event:T.Analysis) :string|undefined
{
    const qualityGateEvent = event.events.find(event => event.category === 'QUALITY_GATE');
    return qualityGateEvent?.name;
}

export function getQualityProfileInfo(event:T.Analysis) :string|undefined
{
    const qualityProfileEvent = event.events.find(event => event.category === 'QUALITY_PROFILE');
    return qualityProfileEvent?.name;
}

export function getVersionInfo(event:T.Analysis) :string|undefined
{
    const versionEvent = event.events.find(event => event.category === 'VERSION');
    return versionEvent?.name;
}

export function getHotspotsBasedOnRiskExposure(hotspots:RawHotspot[]):{high:number,medium:number,low:number}{
    return {
        "high": hotspots.filter((hotspot:RawHotspot)=>hotspot.vulnerabilityProbability === RiskExposure.HIGH).length,
        "medium": hotspots.filter((hotspot:RawHotspot)=>hotspot.vulnerabilityProbability === RiskExposure.MEDIUM).length,
        "low": hotspots.filter((hotspot:RawHotspot)=>hotspot.vulnerabilityProbability === RiskExposure.LOW).length
    };
}