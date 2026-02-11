import { postJSON} from "../helpers/request";
import { throwGlobalError } from "~sonar-aligned/helpers/error";
import { getJSON } from '~sonar-aligned/helpers/request';

export function acceptEulaVersion(): Promise<any>{
    return postJSON('/_codescan/eula/accept').catch(throwGlobalError);
}

export function getEulaVerification(): Promise<any>{
    return getJSON('/_codescan/eula/verify').catch(throwGlobalError);
  }