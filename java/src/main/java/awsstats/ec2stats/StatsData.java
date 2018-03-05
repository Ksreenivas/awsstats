package awsstats.ec2stats;

import java.util.List;

public class StatsData {
    public String ownerId;
    public List<InstanceData> instances;
    public Threshold threshold;

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public void setInstances(List<InstanceData> instances) {
        this.instances = instances;
    }

    public void setThreshold(Integer avg, Integer max) {
        this.threshold = new Threshold();
        this.threshold.setThreshold(avg, max);
    }
}
