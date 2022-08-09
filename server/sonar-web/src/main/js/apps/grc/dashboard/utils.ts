
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
