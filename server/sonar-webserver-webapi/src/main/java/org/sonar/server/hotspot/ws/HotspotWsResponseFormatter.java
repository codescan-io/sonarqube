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
package org.sonar.server.hotspot.ws;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.Paging;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.issue.IssueChangeDto;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.project.ProjectDto;
import org.sonar.db.protobuf.DbIssues;
import org.sonar.db.user.UserDto;
import org.sonar.markdown.Markdown;
import org.sonar.server.issue.TextRangeResponseFormatter;
import org.sonar.server.security.SecurityStandards;
import org.sonar.server.ws.MessageFormattingUtils;
import org.sonarqube.ws.Common;
import org.sonarqube.ws.Common.Comment;
import org.sonarqube.ws.Hotspots;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static org.sonar.api.utils.DateUtils.formatDateTime;
import static org.sonar.server.security.SecurityStandards.fromSecurityStandards;
import static org.sonarqube.ws.WsUtils.nullToEmpty;

public class HotspotWsResponseFormatter {

  private final TextRangeResponseFormatter textRangeFormatter;

  public HotspotWsResponseFormatter(TextRangeResponseFormatter textRangeFormatter) {
    this.textRangeFormatter = textRangeFormatter;
  }

  Hotspots.Component formatProject(Hotspots.Component.Builder builder, ProjectDto project, @Nullable String branch, @Nullable String pullRequest) {
    builder
      .clear()
      .setKey(project.getKey())
      .setQualifier(project.getQualifier())
      .setName(project.getName())
      .setLongName(project.getName());
    ofNullable(branch).ifPresent(builder::setBranch);
    ofNullable(pullRequest).ifPresent(builder::setPullRequest);
    return builder.build();
  }

  Hotspots.Component formatComponent(Hotspots.Component.Builder builder, ComponentDto component, @Nullable String branch, @Nullable String pullRequest) {
    builder
      .clear()
      .setKey(component.getKey())
      .setQualifier(component.qualifier())
      .setName(component.name())
      .setLongName(component.longName());
    ofNullable(branch).ifPresent(builder::setBranch);
    ofNullable(pullRequest).ifPresent(builder::setPullRequest);
    ofNullable(component.path()).ifPresent(builder::setPath);
    return builder.build();
  }

  void formatHotspots(SearchResponseData searchResponseData, Hotspots.ListWsResponse.Builder responseBuilder) {
    responseBuilder.addAllHotspots(mapHotspots(searchResponseData));
  }

  void formatHotspots(SearchResponseData searchResponseData, Hotspots.SearchWsResponse.Builder responseBuilder) {
    responseBuilder.addAllHotspots(mapHotspots(searchResponseData));
  }

  private List<Hotspots.SearchWsResponse.Hotspot> mapHotspots(SearchResponseData searchResponseData) {
    List<IssueDto> hotspots = searchResponseData.getHotspots();
    if (hotspots.isEmpty()) {
      return emptyList();
    }

    Hotspots.SearchWsResponse.Hotspot.Builder builder = Hotspots.SearchWsResponse.Hotspot.newBuilder();
    List<Hotspots.SearchWsResponse.Hotspot> hotspotsList = new ArrayList<>(hotspots.size());
    for (IssueDto hotspot : hotspots) {
      SecurityStandards.SQCategory sqCategory = fromSecurityStandards(hotspot.getSecurityStandards()).getSqCategory();
      builder
        .clear()
        .setKey(hotspot.getKey())
        .setComponent(hotspot.getComponentKey())
        .setProject(hotspot.getProjectKey())
        .setSecurityCategory(sqCategory.getKey())
        .setVulnerabilityProbability(sqCategory.getVulnerability().name())
        .setRuleKey(hotspot.getRuleKey().toString());
      ofNullable(hotspot.getSeverity()).ifPresent(builder::setSeverity);
      ofNullable(hotspot.getStatus()).ifPresent(builder::setStatus);
      builder.setStatusMarkedBy(nullToEmpty(searchResponseData.getStatusMarkedBy(hotspot.getKey())));
      ofNullable(hotspot.getResolution()).ifPresent(builder::setResolution);
      ofNullable(hotspot.getAssigneeUuid()).ifPresent(builder::setAssignee);
      ofNullable(searchResponseData.getUserByUuid(hotspot.getAssigneeUuid())).ifPresent(user -> {String assignedTo = user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getLogin();builder.setAssignedTo(assignedTo);
      ofNullable(searchResponseData.getAssignedDate(hotspot.getKey())).ifPresent(date -> builder.setAssignedDate(formatDateTime(new Date(date))));});
      boolean hasActiveException = "REVIEWED".equals(hotspot.getStatus()) && "EXCEPTION".equals(hotspot.getResolution());
      if (hasActiveException) {ofNullable(hotspot.getIssueResolutionExpiresAt()).ifPresent(expiresAt -> builder.setExceptionExpiryDate(formatDateTime(new Date(expiresAt))));
      ofNullable(searchResponseData.getExceptionReason(hotspot.getKey())).ifPresent(reason -> builder.setExceptionReason(reason));}
      ofNullable(hotspot.getLine()).ifPresent(builder::setLine);
      builder.setMessage(nullToEmpty(hotspot.getMessage()));
      builder.addAllMessageFormattings(MessageFormattingUtils.dbMessageFormattingToWs(hotspot.parseMessageFormattings()));
      builder.setAuthor(nullToEmpty(hotspot.getAuthorLogin()));
      builder.setCreationDate(formatDateTime(hotspot.getIssueCreationDate()));
      builder.setUpdateDate(formatDateTime(hotspot.getIssueUpdateDate()));
      completeHotspotLocations(hotspot, builder, searchResponseData);
      ofNullable(hotspot.getCveId()).ifPresent(builder::setCveId);
      builder.addAllComments(createIssueComments(searchResponseData, hotspot));
      addSort(hotspot, builder, searchResponseData);
      hotspotsList.add(builder.build());
    }
    return hotspotsList;
  }

  private static void addSort(IssueDto hotspot, Hotspots.SearchWsResponse.Hotspot.Builder builder, SearchResponseData searchResponseData) {
    Object[] sortValues = searchResponseData.getSortValues(hotspot.getKey());
    if (sortValues == null || sortValues.length == 0) {
      return;
    }

    Hotspots.Sort.Builder sortBuilder = Hotspots.Sort.newBuilder();
    for (Object sortValue : sortValues) {
      if (sortValue != null) {
        sortBuilder.addSort(sortValue.toString());
      }
    }
    builder.setSort(sortBuilder);
  }

  private List<Comment> createIssueComments(SearchResponseData data, IssueDto dto) {
     List<IssueChangeDto> comments = data.getCommentsForIssueKey(dto.getKey());
     if (comments == null) {
         return new ArrayList<>();
     }
     List<Comment> wsComments = new ArrayList<>(comments.size());
     Comment.Builder wsComment = Comment.newBuilder();

     for (IssueChangeDto comment : comments) {
         String markdown = comment.getChangeData();
         wsComment.clear()
                  .setKey(comment.getKey())
                  .setUpdatable(data.isUpdatableComment(comment.getKey()))
                  .setCreatedAt(DateUtils.formatDateTime(new Date(comment.getIssueChangeCreationDate())));
         ofNullable(data.getUserByUuid(comment.getUserUuid())).ifPresent(user -> wsComment.setLogin(user.getLogin()));
         if (markdown != null) {
          wsComment.setHtmlText(Markdown.convertToHtml(markdown)).setMarkdown(markdown);
         }
       wsComments.add(wsComment.build());
     }
    return wsComments;
  }

  void completeHotspotLocations(IssueDto hotspot, Hotspots.SearchWsResponse.Hotspot.Builder hotspotBuilder, SearchResponseData data) {
    DbIssues.Locations locations = hotspot.parseLocations();

    if (locations == null) {
      return;
    }

    textRangeFormatter.formatTextRange(locations, hotspotBuilder::setTextRange);
    hotspotBuilder.addAllFlows(textRangeFormatter.formatFlows(locations, hotspotBuilder.getComponent(), data.getComponentsByUuid()));
  }

  Hotspots.Component formatComponent(Hotspots.Component.Builder builder, ComponentDto component, @Nullable BranchDto branchDto) {
    if (branchDto == null || branchDto.isMain()) {
      return formatComponent(builder, component, null, null);
    }
    return formatComponent(builder, component, branchDto.getBranchKey(), branchDto.getPullRequestKey());
  }

  void formatTextRange(IssueDto dto, Consumer<Common.TextRange> rangeConsumer) {
    textRangeFormatter.formatTextRange(dto, rangeConsumer);
  }

  List<Common.Flow> formatFlows(DbIssues.Locations locations, String issueComponent, Map<String, ComponentDto> componentsByUuid) {
    return textRangeFormatter.formatFlows(locations, issueComponent, componentsByUuid);
  }

  static final class SearchResponseData {
    private final Paging paging;
    private final List<IssueDto> hotspots;
    private final Map<String, ComponentDto> componentsByUuid = new HashMap<>();
    private final Map<String, BranchDto> branchesByBranchUuid = new HashMap<>();
    private final ListMultimap < String, IssueChangeDto> commentsByIssueKey = ArrayListMultimap.create();
    private final Set<String> updatableComments = new HashSet<>();
    private final Map<String, UserDto> usersByUuid = new HashMap<>();
    private final Map<String, String> statusMarkedByByIssueKey = new HashMap<>();
    private final Map<String, Object[]> sortValuesByHotspotKey;
    private final Map<String, IssueChangeDto> exceptionReasonByIssueKey = new HashMap<>();
    private final Map<String, Long> assignedDateByIssueKey = new HashMap<>();

    SearchResponseData(Paging paging, List<IssueDto> hotspots, Map<String, Object[]> sortValuesByHotspotKey) {
      this.paging = paging;
      this.hotspots = hotspots;
      this.sortValuesByHotspotKey = sortValuesByHotspotKey;
    }

    SearchResponseData(Paging paging, List<IssueDto> hotspots) {
      this(paging, hotspots, Map.of());
    }

    boolean isPresent() {
      return !hotspots.isEmpty();
    }

    public Paging getPaging() {
      return paging;
    }

    List<IssueDto> getHotspots() {
      return hotspots;
    }

    void addComponents(Collection<ComponentDto> components) {
      for (ComponentDto component : components) {
        componentsByUuid.put(component.uuid(), component);
      }
    }

    public void addBranches(List<BranchDto> branchDtos) {
      for (BranchDto branch : branchDtos) {
        branchesByBranchUuid.put(branch.getUuid(), branch);
      }
    }

      void addExceptionReasons(@Nullable List<IssueChangeDto> changes) {
          if (changes == null) {
              return;
          }
          changes.stream()
                  .filter(c -> IssueChangeDto.TYPE_EXCEPTION_REASON.equals(c.getChangeType()))
                  .forEach(c -> exceptionReasonByIssueKey.merge(c.getIssueKey(), c,
                          (existing, current) ->
                                  current.getIssueChangeCreationDate() > existing.getIssueChangeCreationDate()
                                          ? current
                                          : existing
                  ));
      }

    @Nullable
    String getExceptionReason(String issueKey) {
          IssueChangeDto dto = exceptionReasonByIssueKey.get(issueKey);
          return dto == null ? null : dto.getChangeData();
    }

      void addAssignedDates(@Nullable List<IssueChangeDto> changes) {
          if (changes == null) {
              return;
          }
          changes.stream()
                  .filter(c -> IssueChangeDto.TYPE_FIELD_CHANGE.equals(c.getChangeType()))
                  .filter(c -> c.getChangeData() != null && c.getChangeData().contains("assignee"))
                  .forEach(c -> assignedDateByIssueKey.merge(
                          c.getIssueKey(),
                          c.getIssueChangeCreationDate(),
                          Math::max
                  ));
      }

    @Nullable
    Long getAssignedDate(String issueKey) {
        return assignedDateByIssueKey.get(issueKey);
    }

    public BranchDto getBranch(String branchUuid) {
      return branchesByBranchUuid.get(branchUuid);
    }

    Collection<ComponentDto> getComponents() {
      return componentsByUuid.values();
    }

    public Map<String, ComponentDto> getComponentsByUuid() {
      return componentsByUuid;
    }

    @CheckForNull
    Object[] getSortValues(String hotspotKey) {
      return sortValuesByHotspotKey.get(hotspotKey);
    }
    public List<UserDto> getUsers() {
      return new ArrayList<>(usersByUuid.values());
    }

    void addUsers(@Nullable List<UserDto> users) {
      if (users != null) {
        users.forEach(u -> usersByUuid.put(u.getUuid(), u));
      }
    }
    public List<IssueChangeDto> getCommentsForIssueKey(String issueKey) {
      if (commentsByIssueKey.containsKey(issueKey)) {
           return commentsByIssueKey.get(issueKey);
      }
      return new ArrayList<>();
    }

    public void setComments(@Nullable List<IssueChangeDto> comments) {
       for (IssueChangeDto comment : comments) {
         commentsByIssueKey.put(comment.getIssueKey(), comment);
       }
    }

    void addUpdatableComment(String commentKey) {
      updatableComments.add(commentKey);
    }

    boolean isUpdatableComment(String commentKey) {
      return updatableComments.contains(commentKey);
    }

      @CheckForNull
    UserDto getUserByUuid(@Nullable String userUuid) {
      return usersByUuid.get(userUuid);
    }

      void addStatusMarkedBy(Map<String, String> map) {
          statusMarkedByByIssueKey.putAll(map);
      }

      @Nullable
      String getStatusMarkedBy(String issueKey) {
          return statusMarkedByByIssueKey.get(issueKey);
      }
  }

}
