import React, {useEffect, useState} from "react";
import SecurityHotspotsAppRenderer from "../../security-hotspots/SecurityHotspotsAppRenderer";
import {BranchLike} from "../../../types/branch-like";
import {Location} from "history";
import {Router, withRouter} from "../../../components/hoc/withRouter";
import {
  HotspotFilters,
  HotspotResolution,
  HotspotStatus,
  HotspotStatusFilter,
  RawHotspot
} from "../../../types/security-hotspots";
import {isLoggedIn} from "../../../helpers/users";
import {getStandards} from "../../../helpers/security-standard";
import {getMeasures} from "../../../api/measures";
import {getLeakValue} from "../../../components/measure/utils";
import {getSecurityHotspotList, getSecurityHotspots} from "../../../api/security-hotspots";
import {flatMap, range} from "lodash";

const PAGE_SIZE = 500;

interface Props {
  branchLike?: BranchLike;
  currentUser: T.CurrentUser;
  component: T.Component;
  location: Location;
  router: Router;
}

/**
 * See SecurityHotspotsApp.
 */
function Violations({currentUser, component, location, router}: Props) {

  const [hotspotKeys, setHotspotKeys] = useState<string[]>();
  const [hotspots, setHotspots] = useState<RawHotspot[]>([]);
  const [hotspotsPageIndex, setHotspotsPageIndex] = useState<number>(1);
  const [hotspotsReviewedMeasure, setHotspotsReviewedMeasure] = useState<string>();
  const [hotspotsTotal, setHotspotsTotal] = useState<number>(0);
  const [loading, setLoading] = useState<boolean>(false);
  const [loadingMeasure, setLoadingMeasure] = useState<boolean>(false);
  const [loadingMore, setLoadingMore] = useState<boolean>(false);
  const [securityCategories, setSecurityCategories] = useState<T.StandardSecurityCategories>({});
  const [selectedHotspot, setSelectedHotspot] = useState<RawHotspot>();
  const [filters, setFilters] = useState<HotspotFilters>(constructFiltersFromProps());

  useEffect(() => {
    fetchInitialData();
  }, []);

  function constructFiltersFromProps(): HotspotFilters {
    return {
      assignedToMe: location.query.assignedToMe === 'true' && isLoggedIn(currentUser),
      status: HotspotStatusFilter.TO_REVIEW,
      sinceLeakPeriod: false,
    };
  }

  const handleCallFailure = () => {
    setLoading(false);
  };

  function fetchInitialData() {
    return Promise.all([
      getStandards(),
      fetchSecurityHotspots(),
      fetchSecurityHotspotsReviewed()
    ])
    .then(([{sonarsourceSecurity}, {hotspots, paging}]) => {
      const requestedCategory = location.query.category;

      let selectedHotspot;
      if (hotspots.length > 0) {
        const hotspotForCategory = requestedCategory
            ? hotspots.find(h => h.securityCategory === requestedCategory)
            : undefined;
        selectedHotspot = hotspotForCategory ?? hotspots[0];
      }

      setHotspots(hotspots);
      setHotspotsTotal(paging.total);
      setLoading(false);
      setSecurityCategories(sonarsourceSecurity);
      setSelectedHotspot(selectedHotspot);
    })
    .catch(handleCallFailure);
  }

  const fetchSecurityHotspotsReviewed = () => {
    const reviewedHotspotsMetricKey = filters.sinceLeakPeriod
        ? 'new_security_hotspots_reviewed'
        : 'security_hotspots_reviewed';

    setLoadingMeasure(true);
    return getMeasures({
      component: component.key,
      metricKeys: reviewedHotspotsMetricKey,
    })
    .then(measures => {
      const measure = measures && measures.length > 0 ? measures[0] : undefined;
      const hotspotsReviewedMeasure = filters.sinceLeakPeriod
          ? getLeakValue(measure)
          : measure?.value;

      setHotspotsReviewedMeasure(hotspotsReviewedMeasure);
      setLoadingMeasure(false);
    })
    .catch(() => {
      setLoadingMeasure(false);
    });
  };

  function fetchSecurityHotspots(page = 1) {
    const hotspotKeys = location.query.hotspots
        ? (location.query.hotspots as string).split(',')
        : undefined;

    setHotspotKeys(hotspotKeys);

    if (hotspotKeys?.length) {
      return getSecurityHotspotList(hotspotKeys, {
        projectKey: component.key,
      });
    }

    const status =
        filters.status === HotspotStatusFilter.TO_REVIEW
            ? HotspotStatus.TO_REVIEW
            : HotspotStatus.REVIEWED;

    const resolution =
        filters.status === HotspotStatusFilter.TO_REVIEW
            ? undefined
            : HotspotResolution[filters.status];

    return getSecurityHotspots({
      projectKey: component.key,
      p: page,
      ps: PAGE_SIZE,
      status,
      resolution,
      onlyMine: filters.assignedToMe,
      sinceLeakPeriod: filters.sinceLeakPeriod,
    });
  }

  const reloadSecurityHotspotList = () => {
    setLoading(true);

    return fetchSecurityHotspots()
    .then(({hotspots, paging}) => {
      setHotspots(hotspots);
      setHotspotsPageIndex(1);
      setHotspotsTotal(paging.total);
      setLoading(false);
      setSelectedHotspot(hotspots.length > 0 ? hotspots[0] : undefined);
    })
    .catch(handleCallFailure);
  };

  const handleChangeFilters = (changes: Partial<HotspotFilters>) => {
    setFilters({...filters, ...changes});

    reloadSecurityHotspotList();
    if (changes.sinceLeakPeriod !== undefined) {
      fetchSecurityHotspotsReviewed();
    }
  };

  const handleHotspotClick = (selectedHotspot: RawHotspot) => setSelectedHotspot(selectedHotspot);

  const handleHotspotUpdate = (hotspotKey: string) => {
    const index = hotspots.findIndex(h => h.key === hotspotKey);

    return Promise.all(
        range(hotspotsPageIndex).map(p => fetchSecurityHotspots(p + 1 /* pages are 1-indexed */))
    )
    .then(hotspotPages => {
      const allHotspots = flatMap(hotspotPages, 'hotspots');

      const {paging} = hotspotPages[hotspotPages.length - 1];

      const nextHotspot = allHotspots[Math.min(index, allHotspots.length - 1)];

      setHotspots(allHotspots);
      setHotspotsPageIndex(paging.pageIndex);
      setHotspotsTotal(paging.total);
      setSelectedHotspot(nextHotspot);
    })
    .then(fetchSecurityHotspotsReviewed);
  };

  const handleShowAllHotspots = () => {
    router.push({
      ...location,
      query: {...location.query, hotspots: undefined}
    });
  };

  const handleLoadMore = () => {
    setLoadingMore(true);
    return fetchSecurityHotspots(hotspotsPageIndex + 1)
    .then(({hotspots: additionalHotspots}) => {
      setHotspots([...hotspots, ...additionalHotspots]);
      setHotspotsPageIndex(hotspotsPageIndex + 1);
      setLoadingMore(false);
    })
    .catch(handleCallFailure);
  };

  return (
    <SecurityHotspotsAppRenderer
        component={component}
        filters={filters}
        hotspots={hotspots}
        hotspotsReviewedMeasure={hotspotsReviewedMeasure}
        hotspotsTotal={hotspotsTotal}
        isStaticListOfHotspots={Boolean(hotspotKeys && hotspotKeys.length > 0)}
        loading={loading}
        loadingMeasure={loadingMeasure}
        loadingMore={loadingMore}
        onChangeFilters={handleChangeFilters}
        onHotspotClick={handleHotspotClick}
        onLoadMore={handleLoadMore}
        onShowAllHotspots={handleShowAllHotspots}
        onUpdateHotspot={handleHotspotUpdate}
        securityCategories={securityCategories}
        selectedHotspot={selectedHotspot}
    />
  );
}

export default withRouter(Violations);