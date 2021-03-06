package com.yahoo.vespa.hosted.controller;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService;
import com.yahoo.vespa.hosted.controller.api.integration.MetricsService.ApplicationMetrics;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueId;
import com.yahoo.vespa.hosted.controller.application.ApplicationRevision;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.ClusterInfo;
import com.yahoo.vespa.hosted.controller.application.ClusterUtilization;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.application.DeploymentMetrics;


import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A combination of an application instance and a lock for that application. Provides methods for updating application
 * fields.
 *
 * @author mpolden
 * @author jvenstad
 */
public class LockedApplication extends Application {

    private LockedApplication(Builder builder) {
        super(builder.applicationId, builder.deploymentSpec, builder.validationOverrides,
              builder.deployments, builder.deploymentJobs, builder.deploying,
              builder.hasOutstandingChange, builder.ownershipIssueId, builder.metrics);
    }

    /**
     * Used to create a locked application
     *
     * @param application The application to lock.
     * @param lock The lock for the application.
     */
    LockedApplication(Application application, Lock lock) {
        this(new Builder(application));
    }

    public LockedApplication withProjectId(long projectId) {
        return new LockedApplication(new Builder(this).with(deploymentJobs().withProjectId(projectId)));
    }

    public LockedApplication withDeploymentIssueId(IssueId issueId) {
        return new LockedApplication(new Builder(this).with(deploymentJobs().with(issueId)));
    }

    public LockedApplication withJobCompletion(DeploymentJobs.JobReport report, Instant notificationTime, Controller controller) {
        return new LockedApplication(new Builder(this).with(deploymentJobs().withCompletion(report, notificationTime, controller)));
    }

    public LockedApplication withJobTriggering(JobType type, Optional<Change> change, Instant triggerTime,
                                               Version version, Optional<ApplicationRevision> revision, String reason) {
        return new LockedApplication(new Builder(this).with(deploymentJobs().withTriggering(type, change, version, revision, reason, triggerTime)));
    }

    public LockedApplication withNewDeployment(Zone zone, ApplicationRevision revision, Version version, Instant instant) {
        // Use info from previous deployment if available, otherwise create a new one.
        Deployment previousDeployment = deployments().getOrDefault(zone, new Deployment(zone, revision, version, instant));
        Deployment newDeployment = new Deployment(zone, revision, version, instant,
                                                  previousDeployment.clusterUtils(),
                                                  previousDeployment.clusterInfo(),
                                                  previousDeployment.metrics());
        return with(newDeployment);
    }

    public LockedApplication withClusterUtilization(Zone zone, Map<ClusterSpec.Id, ClusterUtilization> clusterUtilization) {
        Deployment deployment = deployments().get(zone);
        if (deployment == null) return this;    // No longer deployed in this zone.
        return with(deployment.withClusterUtils(clusterUtilization));
    }

    public LockedApplication withClusterInfo(Zone zone, Map<ClusterSpec.Id, ClusterInfo> clusterInfo) {
        Deployment deployment = deployments().get(zone);
        if (deployment == null) return this;    // No longer deployed in this zone.
        return with(deployment.withClusterInfo(clusterInfo));

    }

    public LockedApplication with(Zone zone, DeploymentMetrics deploymentMetrics) {
        Deployment deployment = deployments().get(zone);
        if (deployment == null) return this;    // No longer deployed in this zone.
        return with(deployment.withMetrics(deploymentMetrics));
    }

    public LockedApplication withoutDeploymentIn(Zone zone) {
        Map<Zone, Deployment> deployments = new LinkedHashMap<>(deployments());
        deployments.remove(zone);
        return new LockedApplication(new Builder(this).with(deployments));
    }

    public LockedApplication withoutDeploymentJob(DeploymentJobs.JobType jobType) {
        return new LockedApplication(new Builder(this).with(deploymentJobs().without(jobType)));
    }

    public LockedApplication with(DeploymentSpec deploymentSpec) {
        return new LockedApplication(new Builder(this).with(deploymentSpec));
    }

    public LockedApplication with(ValidationOverrides validationOverrides) {
        return new LockedApplication(new Builder(this).with(validationOverrides));
    }

    public LockedApplication withDeploying(Optional<Change> deploying) {
        return new LockedApplication(new Builder(this).withDeploying(deploying));
    }

    public LockedApplication withOutstandingChange(boolean outstandingChange) {
        return new LockedApplication(new Builder(this).with(outstandingChange));
    }

    public LockedApplication withOwnershipIssueId(IssueId issueId) {
        return new LockedApplication(new Builder(this).withOwnershipIssueId(Optional.ofNullable(issueId)));
    }

    public LockedApplication with(MetricsService.ApplicationMetrics metrics) {
        return new LockedApplication(new Builder(this).with(metrics));
    }

    public Version deployVersionFor(DeploymentJobs.JobType jobType, Controller controller) {
        return jobType == JobType.component
               ? controller.systemVersion()
               : deployVersionIn(jobType.zone(controller.system()).get(), controller);
    }

    public Optional<ApplicationRevision> deployRevisionFor(DeploymentJobs.JobType jobType, Controller controller) {
        return jobType == JobType.component
               ? Optional.empty()
               : deployRevisionIn(jobType.zone(controller.system()).get());
    }

    /** Don't expose non-leaf sub-objects. */
    private LockedApplication with(Deployment deployment) {
        Map<Zone, Deployment> deployments = new LinkedHashMap<>(deployments());
        deployments.put(deployment.zone(), deployment);
        return new LockedApplication(new Builder(this).with(deployments));
    }


    private static class Builder {

        private final ApplicationId applicationId;
        private DeploymentSpec deploymentSpec;
        private ValidationOverrides validationOverrides;
        private Map<Zone, Deployment> deployments;
        private DeploymentJobs deploymentJobs;
        private Optional<Change> deploying;
        private boolean hasOutstandingChange;
        private Optional<IssueId> ownershipIssueId;
        private ApplicationMetrics metrics;

        private Builder(Application application) {
            this.applicationId = application.id();
            this.deploymentSpec = application.deploymentSpec();
            this.validationOverrides = application.validationOverrides();
            this.deployments = application.deployments();
            this.deploymentJobs = application.deploymentJobs();
            this.deploying = application.deploying();
            this.hasOutstandingChange = application.hasOutstandingChange();
            this.ownershipIssueId = application.ownershipIssueId();
            this.metrics = application.metrics();
        }

        private Builder with(DeploymentSpec deploymentSpec) { this.deploymentSpec = deploymentSpec; return this; }
        private Builder with(ValidationOverrides validationOverrides) { this.validationOverrides = validationOverrides; return this; }
        private Builder with(Map<Zone, Deployment> deployments) { this.deployments = deployments; return this; }
        private Builder with(DeploymentJobs deploymentJobs) { this.deploymentJobs = deploymentJobs; return this; }
        private Builder withDeploying(Optional<Change> deploying) { this.deploying = deploying; return this; }
        private Builder with(boolean hasOutstandingChange) { this.hasOutstandingChange = hasOutstandingChange; return this; }
        private Builder withOwnershipIssueId(Optional<IssueId> ownershipIssueId) { this.ownershipIssueId = ownershipIssueId; return this; }
        private Builder with(ApplicationMetrics metrics) { this.metrics = metrics; return this; }

    }

}
