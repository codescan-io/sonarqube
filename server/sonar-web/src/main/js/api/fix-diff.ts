/*
 * SonarQube
 * Copyright (C) 2009-2024 SonarSource SA
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

import { IssueContextResponse } from './issues';

export interface FixDiffPayload {
  componentKey: string;
  issueContext: IssueContextResponse;
  issueKey: string;
  originalSource: string;
}

export function getFixDiffPayload(){
  return {
             "repo_full_name": "newproject:EventParticipantHandler.cls",
             "branch": "main",
             "ruleKey": "sf:OuterClassExplicitSharing",
             "issueKey": "a2cafc45-873c-48a5-962f-84c7aec15496",
             "target_file": "",
             "violation_lines": "0",
             "guard_policy": "loose",
             "sourceSnippetStartLine": 1,
             "sourceSnippetEndLine": 91,
             "snippetViolationLine": 0,
             "projectKey": "newproject",
             "ruleDescription": "Ensure all classes explicitly declare sharing settings (with, without, or inherited sharing) to prevent data exposure and maintain security compliance. This overlaps with ClassExplicitSharing; disable one rule if both report the same issue.",
             "contextSeverity": "HIGH",
             "automation_mode": false,
             "codesnippet": "line 1 - public class EventParticipantHandler {\nline 2 - \nline 3 -     // This method processes events, users, and case participants to add or remove relations\nline 4 - \nline 5 -     public void processEventParticipants(List<CaseParticipant_c> oldCaseParticipants, List<Event> events, List<User> users, Boolean add) {\nline 6 - \nline 7 -         // Loop through each case participant\nline 8 - \nline 9 -         for (CaseParticipant_c cp : oldCaseParticipants) {\nline 10 - \nline 11 -             // Loop through each event\nline 12 - \nline 13 -             for (Event e : events) {\nline 14 - \nline 15 -                 // Loop through each user\nline 16 - \nline 17 -                 for (User u : users) {\nline 18 - \nline 19 -                     // Check if the user is associated with the case participant and the event\nline 20 - \nline 21 -                     if (e.WhatId == cp.Case_c && u.Id == cp.Participant_c) {\nline 22 - \nline 23 -                         Set<String> contactIds = new Set<String>();\nline 24 - \nline 25 - \nline 26 - \nline 27 -                         // Add Contact__c instead of ContactId based on BOOST change\nline 28 - \nline 29 -                         contactIds.add(u.Contact__c);\nline 30 - \nline 31 - \nline 32 - \nline 33 -                         // Based on the flag, either add or remove the relation\nline 34 - \nline 35 -                         if (add) {\nline 36 - \nline 37 -                             addWhoRelations(e, contactIds);\nline 38 - \nline 39 -                         } else {\nline 40 - \nline 41 -                             removeWhoRelation(e, contactIds);\nline 42 - \nline 43 -                         }\nline 44 - \nline 45 -                     }\nline 46 - \nline 47 -                 }\nline 48 - \nline 49 -             }\nline 50 - \nline 51 -         }\nline 52 - \nline 53 -     }\nline 54 - \nline 55 -     // This method adds the relations between events and contacts\nline 56 - \nline 57 -     public static void addWhoRelations(Event e, Set<String> relatedContactIds) {\nline 58 - \nline 59 -         // Fix missing event attendee issue by adding Who relation\nline 60 - \nline 61 -         try {\nline 62 - \nline 63 -             EventsServiceWOSHelper.addWhoRelations(e, relatedContactIds);\nline 64 - \nline 65 -         } catch (Exception ex) {\nline 66 - \nline 67 -             System.debug('Error adding who relation: ' + ex.getMessage());\nline 68 - \nline 69 -         }\nline 70 - \nline 71 -     }\nline 72 - \nline 73 -     // This method removes the relations between events and contacts\nline 74 - \nline 75 -     public static void removeWhoRelation(Event e, Set<String> relatedContactIds) {\nline 76 - \nline 77 -         // Implement logic to remove the who relation\nline 78 - \nline 79 -         try {\nline 80 - \nline 81 -             EventsServiceWOSHelper.removeWhoRelations(e, relatedContactIds);\nline 82 - \nline 83 -         } catch (Exception ex) {\nline 84 - \nline 85 -             System.debug('Error removing who relation: ' + ex.getMessage());\nline 86 - \nline 87 -         }\nline 88 - \nline 89 -     }\nline 90 - \nline 91 - }"
         };
}

export interface FixDiffResponse {
  branch?: string;
  details?: {
    file_changes?: {
      file_changes?: Array<{
        commit_message?: string;
        content?: string;
        path?: string;
      }>;
      new_branch?: string;
      pr_body?: string;
      pr_title?: string;
      repo_full_name?: string;
      source_branch?: string;
    };
  };
  status?: string;
}

const FIX_DIFF_API_URL = 'http://localhost:8000/process';
const CONTEXT_API_URL = '/api/issues/context';

export async function requestFixDiff(payload: getFixDiffPayload): Promise<FixDiffResponse> {
  const credentials = btoa('admin:xwqjzq3T4p9V7y8');
  const response = await fetch(FIX_DIFF_API_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Basic ${credentials}`,
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error(`Fix diff API failed with status ${response.status}`);
  }

  return {
             "status": "fix_generated",
             "pr_url": null,
             "branch": "fix/lllges",
             "details": {
                 "file_changes": {
                     "repo_full_name": "dhanushterala1509/agento",
                     "source_branch": "main",
                     "new_branch": "fix/lllges",
                     "pr_title": "fix: sf:SystemDebug - apply to Longnames.cls",
                     "pr_body": "Automated fix for sf:SystemDebug on Longnames.cls at 2025-12-02 10:19:10 UTC. Policy: loose. Lines: 10.",
                     "file_changes": [
                         {
                             "path": "Longnames.cls",
                             "content": "public with sharing class EventParticipantHandler {\n\n    // This method processes events, users, and case participants to add or remove relations\n\n\n    public void processEventParticipants(List<CaseParticipant_c> oldCaseParticipants, List<User> users, Boolean add) {\n\n        // Loop through each case participant\n\n        for (CaseParticipant_c cp : oldCaseParticipants) {\n\n            // Loop through each event\n\n            for (Event e : events) {\n\n                // Loop through each user\n\n                for (User u : users) {\n\n                    // Check if the user is associated with the case participant and the event\n\n                    if (e.WhatId == cp.Case_c && u.Id == cp.Participant_c) {\n\n                        Set<String> contactIds = new Set<String>();\n\n\n\n                        // Add Contact__c instead of ContactId based on BOOST change\n\n                        contactIds.add(u.Contact__c);\n\n\n\n                        // Based on the flag, either add or remove the relation\n\n                        if (add) {\n\n                            addWhoRelations(e, contactIds);\n\n                        } else {\n\n                            removeWhoRelation(e, contactIds);\n\n                        }\n\n                    }\n\n                }\n\n            }\n\n        }\n\n    }\n\n    // This method adds the relations between events and contacts\n\n    public static void addWhoRelations(Event e, Set<String> relatedContactIds) {\n\n        // Fix missing event attendee issue by adding Who relation\n\n        try {\n\n            EventsServiceWOSHelper.addWhoRelations(e, relatedContactIds);\n\n        } catch (Exception ex) {\n\n            System.debug('Error adding who relation: ' + ex.getMessage());\n\n        }\n\n    }\n\n    // This method removes the relations between events and contacts\n\n    public static void removeWhoRelation(Event e, Set<String> relatedContactIds) {\n\n        // Implement logic to remove the who relation\n\n        try {\n\n            EventsServiceWOSHelper.removeWhoRelations(e, relatedContactIds);\n\n        } catch (Exception ex) {\n\n            System.debug('Error removing who relation: ' + ex.getMessage());\n\n        }\n\n    }\n\n}",
                             "commit_message": "fix: sf:SystemDebug Longnames.cls (loose lines 10)"
                         }
                     ]
                 }
             }
         };

}
