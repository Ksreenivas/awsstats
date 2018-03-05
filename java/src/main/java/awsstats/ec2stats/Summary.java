package awsstats.ec2stats;

import java.util.List;

public class Summary{
    public String ownerId;
    public SummaryStats average;
    public SummaryStats maximum;
    public List<List<String>> instanceTypes;
    public List<List<String>> regions;
    public List<List<String>> underUtilized;
    public Threshold threshold;
    public SummaryEfficiency efficiency;
    public Integer monthlyCost;
}