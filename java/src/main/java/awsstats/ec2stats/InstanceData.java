package awsstats.ec2stats;

import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.ec2.model.Tag;

import java.util.List;

public class InstanceData {
    public String region;
    public String instanceId;
    public String instanceType;
    public String state;
    public List<Tag> tags;
    public List<Datapoint> stats;

    public void setRegion(String region) {
        this.region =  region;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public void setInstanceType(String instanceType) {
        this.instanceType = instanceType;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }

    public void setStats(List<Datapoint> stats) {
        this.stats = stats;
    }

}
