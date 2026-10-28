package com.argument.dashboard;

import com.argument.version.port.VersionIdentity.Actor;

/** A/B and the automation worker supply authorized aggregates, never annotation content. */
public interface DashboardData {
    Summary summary(Actor actor);
    record Summary(int guideCount, int documentCount, int taskCount, int pendingReviews, int autoQueueCount) {}
}
