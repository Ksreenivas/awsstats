package awsstats.ec2stats;

public class Threshold {
    public Integer avg;
    public Integer max;
    public void setThreshold(Integer avg, Integer max) {
        this.avg = avg;
        this.max = max;
    }
}