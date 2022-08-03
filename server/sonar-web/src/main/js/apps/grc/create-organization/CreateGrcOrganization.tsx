import React from "react";
import { WithRouterProps } from "react-router";
import CreateOrganization from "../../create/organization/CreateOrganization";

interface Props {
  appState: T.AppState | undefined;
  createOrganization: (
      organization: T.Organization & { installationId?: string }
  ) => Promise<string>;
  currentUser: T.LoggedInUser;
  deleteOrganization: (key: string) => Promise<void>;
  updateOrganization: (
      organization: T.Organization & { installationId?: string }
  ) => Promise<string>;
  userOrganizations: T.Organization[];
}

const CreateGrcOrganization = (props: Props & WithRouterProps) => {
    
  return (
    <CreateOrganization {...props} />
  );
}

export default CreateGrcOrganization;