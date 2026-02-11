import { postJSON } from "../helpers/request";
import { throwGlobalError } from "~sonar-aligned/helpers/error";

export function acceptEulaVersion(): Promise<any>{
    return postJSON('/_codescan/eula/accept').catch(throwGlobalError);
}